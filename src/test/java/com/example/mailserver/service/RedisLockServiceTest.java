package com.example.mailserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisLockServiceTest {

    private final Map<String, String> storage = new HashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private RedisLockService redisLockService;

    @BeforeEach
    void setUp() {
        storage.clear();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> storage.get(invocation.getArgument(0)));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            if (storage.containsKey(key)) {
                return false;
            }
            storage.put(key, value);
            return true;
        });
        doAnswer(invocation -> storage.remove(invocation.getArgument(0)) != null).when(redisTemplate).delete(anyString());
        redisLockService = new RedisLockService(redisTemplate);
    }

    @Test
    void shouldAcquireLockWhenKeyIsFree() {
        RedisLockService.LockAcquireResult result =
                redisLockService.tryAcquire("daily-digest-lock:2026-03-20", "owner-1", Duration.ofMinutes(10), false);

        assertThat(result.acquired()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void shouldRejectLockWhenAlreadyHeld() {
        storage.put("daily-digest-lock:2026-03-20", "owner-1");

        RedisLockService.LockAcquireResult result =
                redisLockService.tryAcquire("daily-digest-lock:2026-03-20", "owner-2", Duration.ofMinutes(10), false);

        assertThat(result.acquired()).isFalse();
        assertThat(result.reason()).isEqualTo("REPLICA_LOCK_CONFLICT");
    }

    @Test
    void shouldReleaseOnlyMatchingOwner() {
        storage.put("startup-catchup-lock", "owner-1");

        redisLockService.release("startup-catchup-lock", "owner-2");
        assertThat(storage).containsEntry("startup-catchup-lock", "owner-1");

        redisLockService.release("startup-catchup-lock", "owner-1");
        assertThat(storage).doesNotContainKey("startup-catchup-lock");
    }
}
