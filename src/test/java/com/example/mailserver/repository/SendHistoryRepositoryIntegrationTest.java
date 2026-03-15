package com.example.mailserver.repository;

import com.example.mailserver.support.JdbcIntegrationTestSupport;
import com.example.mailserver.service.RedisLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SendHistoryRepositoryIntegrationTest extends JdbcIntegrationTestSupport {

    @Autowired
    private SendHistoryRepository sendHistoryRepository;

    @MockBean
    private SendStateRedisRepository sendStateRedisRepository;

    @MockBean
    private RedisLockService redisLockService;

    @BeforeEach
    void setRedisDefaults() {
        when(sendStateRedisRepository.hasDailySent(any())).thenReturn(false);
        doNothing().when(sendStateRedisRepository).markDailySent(any(), any());
    }

    @Test
    void shouldPersistDailyResultAndBlockResendForSuccessfulDate() {
        LocalDate date = LocalDate.of(2026, 3, 15);

        assertThat(sendHistoryRepository.claimDailyIfAbsent(date, "test")).isTrue();
        sendHistoryRepository.saveDailyResult(date, 3, "SUCCESS", "test:daily-digest");

        assertThat(sendHistoryRepository.hasDailySentToday(date)).isTrue();
        assertThat(sendHistoryRepository.claimDailyIfAbsent(date, "retry")).isFalse();

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM send_history WHERE send_date = ? AND send_type = 'DAILY_NEWS'",
                String.class,
                date
        );
        assertThat(statuses).containsExactly("SUCCESS");
    }

    @Test
    void shouldAllowOnlyOneConcurrentDbClaim() throws Exception {
        LocalDate date = LocalDate.of(2026, 3, 16);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Boolean>> futures = executor.invokeAll(List.of(
                    claimTask(date, "worker-a"),
                    claimTask(date, "worker-b")
            ));

            long successCount = futures.stream()
                    .map(this::getUnchecked)
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(successCount).isEqualTo(1);
            Integer rowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM send_history WHERE send_date = ? AND send_type = 'DAILY_NEWS'",
                    Integer.class,
                    date
            );
            assertThat(rowCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> claimTask(LocalDate date, String source) {
        return () -> sendHistoryRepository.claimDailyIfAbsent(date, source);
    }

    private boolean getUnchecked(Future<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
