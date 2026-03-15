package com.example.mailserver.service;

import com.example.mailserver.repository.SendHistoryRepository;
import com.example.mailserver.repository.SendStateRedisRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DigestExecutionGuard {

    private static final Logger log = LoggerFactory.getLogger(DigestExecutionGuard.class);

    private final SendHistoryRepository sendHistoryRepository;
    private final SendStateRedisRepository sendStateRedisRepository;

    public GuardResult tryDailyDigest(LocalDate date, String source) {
        String owner = buildOwner(source, date);
        RedisLockService.LockAcquireResult lockResult = sendStateRedisRepository.tryClaimDailyDetailed(date, owner);
        if (!lockResult.acquired()) {
            log.info("[DIGEST_GUARD] skipped date={} source={} reason={}", date, source, lockResult.reason());
            return GuardResult.skipped(lockResult.reason());
        }

        try {
            boolean claimed = sendHistoryRepository.claimDailyIfAbsent(date, source);
            if (!claimed) {
                log.info("[DIGEST_GUARD] skipped date={} source={} reason=DB_DUPLICATE_GUARD", date, source);
                sendStateRedisRepository.releaseDailyClaim(date, owner);
                return GuardResult.skipped("DB_DUPLICATE_GUARD");
            }
            return GuardResult.acquired(owner);
        } catch (RuntimeException ex) {
            sendStateRedisRepository.releaseDailyClaim(date, owner);
            throw ex;
        }
    }

    public boolean hasDailyDigestAlreadySucceeded(LocalDate date) {
        return sendHistoryRepository.hasDailySentToday(date);
    }

    public void releaseDailyDigest(LocalDate date, GuardResult guardResult) {
        if (guardResult == null || !guardResult.acquired()) {
            return;
        }
        sendStateRedisRepository.releaseDailyClaim(date, guardResult.owner());
    }

    private String buildOwner(String source, LocalDate date) {
        return source + ":" + date + ":" + UUID.randomUUID();
    }

    public static final class GuardResult {
        private final boolean acquired;
        private final String owner;
        private final String reason;

        private GuardResult(boolean acquired, String owner, String reason) {
            this.acquired = acquired;
            this.owner = owner;
            this.reason = reason;
        }

        public static GuardResult acquired(String owner) {
            return new GuardResult(true, owner, null);
        }

        public static GuardResult skipped(String reason) {
            return new GuardResult(false, null, reason);
        }

        public boolean acquired() {
            return acquired;
        }

        public String owner() {
            return owner;
        }

        public String reason() {
            return reason;
        }
    }
}
