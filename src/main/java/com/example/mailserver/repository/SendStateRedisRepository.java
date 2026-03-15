package com.example.mailserver.repository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import com.example.mailserver.service.RedisLockService;

import java.time.Duration;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class SendStateRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(SendStateRedisRepository.class);
    private static final Duration SENT_TTL = Duration.ofDays(2);
    private static final Duration CLAIM_TTL = Duration.ofMinutes(30);
    private static final Duration STARTUP_LOCK_TTL = Duration.ofMinutes(10);
    private static final String PREFIX_SENT = "mail-server:daily-sent:";
    private static final String PREFIX_LOCK = "mail-server:digest:lock:";
    private static final String STARTUP_CATCHUP_LOCK = "mail-server:startup-catchup-lock";

    private final StringRedisTemplate redisTemplate;
    private final RedisLockService redisLockService;

    public boolean hasDailySent(LocalDate date) {
        try {
            return Boolean.TRUE.toString().equals(redisTemplate.opsForValue().get(sentKey(date)));
        } catch (DataAccessException ex) {
            log.warn("[REDIS] hasDailySent check failed: {}", ex.getMessage());
            return false;
        }
    }

    public void markDailySent(LocalDate date, String status) {
        try {
            redisTemplate.opsForValue().set(sentKey(date), Boolean.TRUE.toString(), SENT_TTL);
            log.info("[REDIS] daily sent cached date={} status={}", date, status);
        } catch (DataAccessException ex) {
            log.warn("[REDIS] markDailySent failed: {}", ex.getMessage());
        }
    }

    public boolean tryClaimDaily(LocalDate date, String source) {
        return tryClaimDailyDetailed(date, source).acquired();
    }

    public RedisLockService.LockAcquireResult tryClaimDailyDetailed(LocalDate date, String owner) {
        return redisLockService.tryAcquire(dailyLockKey(date), owner, CLAIM_TTL, true);
    }

    public void releaseDailyClaim(LocalDate date) {
        releaseDailyClaim(date, null);
    }

    public void releaseDailyClaim(LocalDate date, String owner) {
        releaseLock(dailyLockKey(date), owner);
    }

    public boolean tryAcquireStartupCatchupLock(String owner) {
        return tryAcquireStartupCatchupLockDetailed(owner).acquired();
    }

    public RedisLockService.LockAcquireResult tryAcquireStartupCatchupLockDetailed(String owner) {
        return redisLockService.tryAcquire(STARTUP_CATCHUP_LOCK, owner, STARTUP_LOCK_TTL, false);
    }

    public void releaseStartupCatchupLock(String owner) {
        redisLockService.release(STARTUP_CATCHUP_LOCK, owner);
    }

    public String startupCatchupLockKey() {
        return STARTUP_CATCHUP_LOCK;
    }

    private String sentKey(LocalDate date) {
        return PREFIX_SENT + date;
    }

    public String dailyLockKey(LocalDate date) {
        return PREFIX_LOCK + date;
    }

    private void releaseLock(String key, String owner) {
        redisLockService.release(key, owner);
    }
}
