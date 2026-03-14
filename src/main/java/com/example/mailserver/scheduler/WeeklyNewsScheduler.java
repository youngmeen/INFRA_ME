package com.example.mailserver.scheduler;

import com.example.mailserver.config.NewsProperties;
import com.example.mailserver.service.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeeklyNewsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyNewsScheduler.class);
    private final WeeklyReportService weeklyReportService;
    private final NewsProperties properties;

    @Scheduled(cron = "${app.news.weekly-cron:0 0 9 * * MON}", zone = "${app.news.weekly-zone:Asia/Seoul}")
    public void runWeekly() {
        int count = weeklyReportService.sendWeeklyReport();
        logger.info("Weekly news report sent. count={}, cron={}, zone={}", count, properties.getWeeklyCron(), properties.getWeeklyZone());
    }
}
