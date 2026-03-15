package com.example.mailserver.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RedisLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);

    private final StringRedisTemplate redisTemplate;

    public LockAcquireResult tryAcquire(String key, String owner, Duration ttl, boolean failOpen) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);
            if (Boolean.TRUE.equals(ok)) {
                return LockAcquireResult.acquired(owner);
            }
            return LockAcquireResult.conflict();
        } catch (DataAccessException ex) {
            log.warn("[REDIS_LOCK] acquire failed key={} error={}", key, ex.getMessage());
            if (failOpen) {
                return LockAcquireResult.acquired(owner, "REDIS_UNAVAILABLE");
            }
            return LockAcquireResult.redisUnavailable(ex.getMessage());
        }
    }

    public void release(String key, String owner) {
        try {
            if (owner == null) {
                redisTemplate.delete(key);
                return;
            }

            String currentOwner = redisTemplate.opsForValue().get(key);
            if (Objects.equals(owner, currentOwner)) {
                redisTemplate.delete(key);
            }
        } catch (DataAccessException ex) {
            log.warn("[REDIS_LOCK] release failed key={} error={}", key, ex.getMessage());
        }
    }

    public static final class LockAcquireResult {
        private final boolean acquired;
        private final String owner;
        private final String reason;

        private LockAcquireResult(boolean acquired, String owner, String reason) {
            this.acquired = acquired;
            this.owner = owner;
            this.reason = reason;
        }

        public static LockAcquireResult acquired(String owner) {
            return new LockAcquireResult(true, owner, null);
        }

        public static LockAcquireResult acquired(String owner, String reason) {
            return new LockAcquireResult(true, owner, reason);
        }

        public static LockAcquireResult conflict() {
            return new LockAcquireResult(false, null, "REPLICA_LOCK_CONFLICT");
        }

        public static LockAcquireResult redisUnavailable(String detail) {
            return new LockAcquireResult(false, null, "REDIS_UNAVAILABLE" + (detail == null ? "" : ":" + detail));
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
