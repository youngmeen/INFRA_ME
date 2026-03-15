package com.example.mailserver.service;

import com.example.mailserver.repository.SendStateRedisRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DailyDigestExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestExecutionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime CATCH_UP_TIME = LocalTime.of(9, 0);

    private final DailyNewsDigestService dailyNewsDigestService;
    private final DigestExecutionGuard digestExecutionGuard;
    private final SendStateRedisRepository sendStateRedisRepository;

    public int sendDailyDigest() {
        return sendDailyDigest("manual");
    }

    public int sendDailyDigest(String source) {
        return sendDailyDigestForDate(LocalDate.now(KST), source);
    }

    public int sendDailyDigestForDate(LocalDate date, String source) {
        DigestExecutionGuard.GuardResult guardResult = digestExecutionGuard.tryDailyDigest(date, source);
        if (!guardResult.acquired()) {
            log.info("[DIGEST_LOCK] skipped date={} source={} reason={}", date, source, guardResult.reason());
            return -1;
        }

        try {
            return dailyNewsDigestService.sendDailyDigestAfterClaim(date, source);
        } finally {
            digestExecutionGuard.releaseDailyDigest(date, guardResult);
        }
    }

    public int runStartupCatchup() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        if (now.toLocalTime().isBefore(CATCH_UP_TIME)) {
            return -1;
        }

        String owner = buildOwner("startup-catchup", today);
        RedisLockService.LockAcquireResult startupLock = sendStateRedisRepository.tryAcquireStartupCatchupLockDetailed(owner);
        if (!startupLock.acquired()) {
            log.info("[STARTUP] catch-up skipped reason={}", startupLock.reason());
            return -1;
        }

        try {
            if (digestExecutionGuard.hasDailyDigestAlreadySucceeded(today)) {
                log.info("[STARTUP] daily digest already sent");
                return -1;
            }

            log.info("[STARTUP] daily digest missed -> sending");
            DigestExecutionGuard.GuardResult guardResult = digestExecutionGuard.tryDailyDigest(today, "startup-check");
            if (!guardResult.acquired()) {
                log.info("[STARTUP] catch-up skipped date={} reason={}", today, guardResult.reason());
                return -1;
            }
            try {
                return dailyNewsDigestService.sendDailyDigestAfterClaim(today, "startup-check");
            } finally {
                digestExecutionGuard.releaseDailyDigest(today, guardResult);
            }
        } finally {
            sendStateRedisRepository.releaseStartupCatchupLock(owner);
        }
    }

    private String buildOwner(String source, LocalDate date) {
        return source + ":" + date + ":" + UUID.randomUUID();
    }
}
