package com.example.mailserver.scheduler;

import com.example.mailserver.repository.SendHistoryRepository;
import com.example.mailserver.service.DailyNewsDigestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class DailyNewsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyNewsScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyNewsDigestService digestService;
    private final SendHistoryRepository sendHistoryRepository;

    @Scheduled(
            cron = "${app.news.cron:0 0 9 * * *}",
            zone = "${app.news.zone:Asia/Seoul}"
    )
    public void runDaily() {
        LocalDate today = LocalDate.now(KST);
        if (!sendHistoryRepository.claimDailyIfAbsent(today, "scheduler")) {
            logger.info("[SCHEDULER] daily news skipped because already sent today");
            return;
        }

        int count = digestService.sendDailyDigestAfterClaim(today, "scheduler");
        logger.info("[SCHEDULER] daily news sent count={}", count);
    }
}
