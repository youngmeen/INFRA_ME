package com.example.mailserver.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.example.mailserver.service.RedisLockService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SendStateRedisRepositoryTest {

    private final Map<String, String> storage = new HashMap<>();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private SendStateRedisRepository sendStateRedisRepository;
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
        doAnswer(invocation -> {
            storage.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        doAnswer(invocation -> storage.remove(invocation.getArgument(0)) != null).when(redisTemplate).delete(anyString());

        redisLockService = new RedisLockService(redisTemplate);
        sendStateRedisRepository = new SendStateRedisRepository(redisTemplate, redisLockService);
    }

    @Test
    void shouldAcquireRedisLockOnlyOncePerDigestDate() {
        LocalDate date = LocalDate.of(2026, 3, 17);

        assertThat(sendStateRedisRepository.tryClaimDaily(date, "owner-1")).isTrue();
        assertThat(sendStateRedisRepository.tryClaimDaily(date, "owner-2")).isFalse();

        sendStateRedisRepository.releaseDailyClaim(date, "owner-1");

        assertThat(sendStateRedisRepository.tryClaimDaily(date, "owner-3")).isTrue();
    }

    @Test
    void shouldAcquireStartupCatchupLockOnlyOnce() {
        assertThat(sendStateRedisRepository.tryAcquireStartupCatchupLock("owner-1")).isTrue();
        assertThat(sendStateRedisRepository.tryAcquireStartupCatchupLock("owner-2")).isFalse();

        sendStateRedisRepository.releaseStartupCatchupLock("owner-1");

        assertThat(sendStateRedisRepository.tryAcquireStartupCatchupLock("owner-3")).isTrue();
    }
}
