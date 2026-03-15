package com.example.mailserver.service;

import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.NewsDigestView;
import com.example.mailserver.news.StoredNewsItem;
import com.example.mailserver.repository.SendStateRedisRepository;
import com.example.mailserver.service.RedisLockService;
import com.example.mailserver.support.JdbcIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DailyDigestExecutionServiceIntegrationTest extends JdbcIntegrationTestSupport {

    @Autowired
    private DailyDigestExecutionService dailyDigestExecutionService;

    @MockBean
    private NewsService newsService;

    @MockBean
    private TelegramService telegramService;

    @MockBean
    private TelegramFormatService telegramFormatService;

    @MockBean
    private SendStateRedisRepository sendStateRedisRepository;

    @MockBean
    private RedisLockService redisLockService;

    private final ConcurrentMap<String, String> digestLocks = new ConcurrentHashMap<>();

    @BeforeEach
    void setUpRedisLockMock() {
        digestLocks.clear();
        when(sendStateRedisRepository.hasDailySent(any())).thenReturn(false);
        when(sendStateRedisRepository.tryAcquireStartupCatchupLock(any())).thenReturn(true);
        when(sendStateRedisRepository.tryAcquireStartupCatchupLockDetailed(any()))
                .thenAnswer(invocation -> RedisLockService.LockAcquireResult.acquired(invocation.getArgument(0)));
        doNothing().when(sendStateRedisRepository).releaseStartupCatchupLock(any());
        when(sendStateRedisRepository.tryClaimDaily(any(), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            String owner = invocation.getArgument(1);
            return digestLocks.putIfAbsent(date.toString(), owner) == null;
        });
        when(sendStateRedisRepository.tryClaimDailyDetailed(any(), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            String owner = invocation.getArgument(1);
            if (digestLocks.putIfAbsent(date.toString(), owner) == null) {
                return RedisLockService.LockAcquireResult.acquired(owner);
            }
            return RedisLockService.LockAcquireResult.conflict();
        });
        doAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            String owner = invocation.getArgument(1);
            digestLocks.computeIfPresent(date.toString(), (key, currentOwner) ->
                    currentOwner.equals(owner) ? null : currentOwner);
            return null;
        }).when(sendStateRedisRepository).releaseDailyClaim(any(), any());
        doNothing().when(sendStateRedisRepository).markDailySent(any(), any());
    }

    @Test
    void shouldSkipWhenDateAlreadySucceeded() {
        LocalDate date = LocalDate.of(2026, 3, 18);
        jdbcTemplate.update(
                "INSERT INTO send_history (send_type, send_date, digest_type, digest_date, sent_at, sent_count, status, message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "DAILY_NEWS",
                date,
                "DAILY_NEWS",
                date,
                Instant.now().toEpochMilli(),
                2,
                "SUCCESS",
                "seed"
        );

        int result = dailyDigestExecutionService.sendDailyDigestForDate(date, "manual-api");

        assertThat(result).isEqualTo(-1);
        verify(telegramService, times(0)).sendHtmlMessage(any());
        verify(telegramService, times(0)).sendMessage(any());
    }

    @Test
    void shouldAllowOnlyOneSuccessDuringConcurrentExecution() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 19);
        NewsDigestView view = sampleView();

        when(newsService.getDailyDigestCandidates()).thenReturn(view);
        when(telegramFormatService.formatDaily(eq(view))).thenReturn("<b>digest</b>");
        doNothing().when(telegramService).sendHtmlMessage("<b>digest</b>");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() ->
                    dailyDigestExecutionService.sendDailyDigestForDate(date, "replica-a"));
            Future<Integer> second = executor.submit(() ->
                    dailyDigestExecutionService.sendDailyDigestForDate(date, "replica-b"));

            List<Integer> results = List.of(first.get(), second.get());
            assertThat(results.stream().filter(value -> value > 0).count()).isEqualTo(1);
            assertThat(results.stream().filter(value -> value < 0).count()).isEqualTo(1);
            verify(telegramService, times(1)).sendHtmlMessage("<b>digest</b>");

            Integer successRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM send_history WHERE send_date = ? AND status = 'SUCCESS'",
                    Integer.class,
                    date
            );
            assertThat(successRows).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldSkipStartupCatchupWhenStartupLockAlreadyHeld() {
        when(sendStateRedisRepository.tryAcquireStartupCatchupLockDetailed(any()))
                .thenReturn(RedisLockService.LockAcquireResult.conflict());

        int result = dailyDigestExecutionService.runStartupCatchup();

        assertThat(result).isEqualTo(-1);
        verify(sendStateRedisRepository, times(1)).tryAcquireStartupCatchupLockDetailed(any());
        verify(sendStateRedisRepository, times(0)).releaseStartupCatchupLock(any());
        verify(telegramService, times(0)).sendHtmlMessage(any());
    }

    private NewsDigestView sampleView() {
        StoredNewsItem item = new StoredNewsItem(
                1L,
                "Digest title",
                "Digest summary",
                NewsCategory.DEV,
                "test-source",
                "https://example.com/news/1",
                Instant.parse("2026-03-19T00:00:00Z"),
                9.2,
                Instant.parse("2026-03-19T00:00:00Z")
        );
        return new NewsDigestView(
                Instant.parse("2026-03-19T00:00:00Z"),
                List.of("briefing line"),
                List.of(item),
                Map.of(NewsCategory.DEV, List.of(item)),
                1
        );
    }
}
