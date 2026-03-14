package com.example.mailserver.service;

import com.example.mailserver.news.NewsCategory;
import com.example.mailserver.news.StoredNewsItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private final NewsService newsService;
    private final TelegramService telegramService;
    private final TelegramFormatService telegramFormatService;

    public int sendWeeklyReport() {
        newsService.refreshLatestNews();
        List<StoredNewsItem> top10 = newsService.getWeeklyTopNews();
        if (top10.isEmpty()) {
            telegramService.sendMessage("🗓️ Weekly Dev Report\n지난 7일 집계 가능한 뉴스가 없습니다.");
            return 0;
        }
        Map<NewsCategory, Long> trend = newsService.getWeeklyTrend();
        String html = telegramFormatService.formatWeekly(top10, trend);
        telegramService.sendHtmlMessage(html);
        return top10.size();
    }
}
