package com.example.mailserver.scheduler;

import com.example.mailserver.service.DailyDigestExecutionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyNewsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyNewsScheduler.class);

    private final DailyDigestExecutionService digestExecutionService;

    @Scheduled(
            cron = "${app.news.cron:0 0 9 * * *}",
            zone = "${app.news.zone:Asia/Seoul}"
    )
    public void runDaily() {
        int count = digestExecutionService.sendDailyDigest("scheduler");
        if (count < 0) {
            logger.info("[SCHEDULER] daily news skipped because another instance already handled today");
            return;
        }
        logger.info("[SCHEDULER] daily news sent count={}", count);
    }
}
