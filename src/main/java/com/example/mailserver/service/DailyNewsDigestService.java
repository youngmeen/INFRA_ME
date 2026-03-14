package com.example.mailserver.service;

import com.example.mailserver.news.NewsDigestView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyNewsDigestService {

    private final NewsService newsService;
    private final TelegramService telegramService;
    private final TelegramFormatService telegramFormatService;

    public int sendDailyDigest() {
        NewsDigestView view = newsService.getDailyDigestCandidates();
        if (view.totalCount() == 0) {
            telegramService.sendMessage("📊 오늘의 Dev 뉴스\n지난 24시간 내 조건에 맞는 뉴스가 없습니다.");
            return 0;
        }

        String html = telegramFormatService.formatDaily(view);
        telegramService.sendHtmlMessage(html);
        return view.totalCount();
    }
}
