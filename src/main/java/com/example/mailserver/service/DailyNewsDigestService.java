package com.example.mailserver.service;

import com.example.mailserver.news.NewsDigestView;
import com.example.mailserver.repository.SendHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyNewsDigestService {

    private static final Logger log = LoggerFactory.getLogger(DailyNewsDigestService.class);

    private final NewsService newsService;
    private final TelegramService telegramService;
    private final TelegramFormatService telegramFormatService;
    private final SendHistoryRepository sendHistoryRepository;

    public int sendDailyDigestAfterClaim(LocalDate today, String source) {
        try {
            NewsDigestView view = newsService.getDailyDigestCandidates();
            if (view.totalCount() == 0) {
                telegramService.sendMessage("📊 오늘의 Dev 뉴스\n지난 24시간 내 조건에 맞는 뉴스가 없습니다.");
                sendHistoryRepository.saveDailyResult(today, 0, "EMPTY", source + ":no-candidates");
                log.info("[SEND_HISTORY] DAILY_NEWS saved status=EMPTY date={} source={} count=0", today, source);
                return 0;
            }

            String html = telegramFormatService.formatDaily(view);
            telegramService.sendHtmlMessage(html);
            sendHistoryRepository.saveDailyResult(today, view.totalCount(), "SUCCESS", source + ":daily-digest");
            log.info("[SEND_HISTORY] DAILY_NEWS saved status=SUCCESS date={} source={} count={}", today, source, view.totalCount());
            return view.totalCount();
        } catch (Exception e) {
            sendHistoryRepository.saveDailyResult(today, 0, "FAIL", source + ":" + safeMessage(e));
            log.info("[SEND_HISTORY] DAILY_NEWS saved status=FAIL date={} source={} count=0", today, source);
            throw e;
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
