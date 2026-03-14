package com.example.mailserver.service;

import com.example.mailserver.news.NewsDigestView;
import com.example.mailserver.repository.SendHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class DailyNewsDigestService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NewsService newsService;
    private final TelegramService telegramService;
    private final TelegramFormatService telegramFormatService;
    private final SendHistoryRepository sendHistoryRepository;

    public int sendDailyDigest() {
        LocalDate today = LocalDate.now(KST);
        try {
            NewsDigestView view = newsService.getDailyDigestCandidates();
            if (view.totalCount() == 0) {
                telegramService.sendMessage("📊 오늘의 Dev 뉴스\n지난 24시간 내 조건에 맞는 뉴스가 없습니다.");
                sendHistoryRepository.upsertDailyResult(today, 0, "EMPTY", "no-candidates");
                return 0;
            }

            String html = telegramFormatService.formatDaily(view);
            telegramService.sendHtmlMessage(html);
            sendHistoryRepository.upsertDailyResult(today, view.totalCount(), "SENT", "daily-digest");
            return view.totalCount();
        } catch (Exception e) {
            sendHistoryRepository.upsertDailyResult(today, 0, "FAILED", safeMessage(e));
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
