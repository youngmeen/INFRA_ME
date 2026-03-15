package com.example.mailserver.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StartupDailyDigestService {

    private static final Logger log = LoggerFactory.getLogger(StartupDailyDigestService.class);

    private final DailyDigestExecutionService digestExecutionService;

    @Value("${app.startup.catchup.enabled:true}")
    private boolean startupCatchupEnabled;

    @EventListener(ApplicationReadyEvent.class)
    public void checkAndSendDailyDigestOnStartup() {
        if (!startupCatchupEnabled) {
            log.info("[STARTUP] catch-up disabled");
            return;
        }

        try {
            digestExecutionService.runStartupCatchup();
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
