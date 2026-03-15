package com.example.mailserver.service;

import com.example.mailserver.repository.SendHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class StartupDailyDigestService {

    private static final Logger log = LoggerFactory.getLogger(StartupDailyDigestService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime CATCH_UP_TIME = LocalTime.of(9, 0);

    private final DailyNewsDigestService dailyNewsDigestService;
    private final SendHistoryRepository sendHistoryRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void checkAndSendDailyDigestOnStartup() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        if (now.toLocalTime().isBefore(CATCH_UP_TIME)) {
            return;
        }

        if (!sendHistoryRepository.claimDailyIfAbsent(today, "startup-check")) {
            log.info("[STARTUP] daily digest already sent");
            return;
        }

        log.info("[STARTUP] daily digest missed -> sending");
        try {
            dailyNewsDigestService.sendDailyDigestAfterClaim(today, "startup-check");
        } catch (Exception e) {
            log.warn("[STARTUP] daily digest send failed: {}", safeMessage(e));
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
