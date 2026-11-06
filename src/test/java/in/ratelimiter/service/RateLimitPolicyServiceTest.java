
package in.ratelimiter.service;

import in.ratelimiter.config.RateLimiterProperties;
import in.ratelimiter.dto.RateLimitPolicy;
import in.ratelimiter.exception.RedisUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitPolicyServiceTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private RateLimitPolicyService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        service = new RateLimitPolicyService(
                redisTemplate,
                new RateLimiterProperties("fail-closed"),
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldReturnDefaultFreePolicyWhenPolicyDoesNotExist() {
        when(hashOperations.entries("rate_policy:USER:101"))
                .thenReturn(Map.of());

        RateLimitPolicy policy = service.getPolicy("101");

        assertEquals("FREE", policy.plan());
        assertEquals(5, policy.capacity());
        assertEquals(1.0, policy.refillRate());
    }

    @Test
    void shouldReturnExistingUserPolicy() {
        Map<Object, Object> data = new HashMap<>();
        data.put("plan", "PREMIUM");
        data.put("capacity", "20");
        data.put("refillRate", "5");

        when(hashOperations.entries("rate_policy:USER:101"))
                .thenReturn(data);

        RateLimitPolicy policy = service.getPolicy("101");

        assertEquals("PREMIUM", policy.plan());
        assertEquals(20, policy.capacity());
        assertEquals(5.0, policy.refillRate());
    }

    @Test
    void shouldUpdateUserPolicy() {
        RateLimitPolicy policy =
                new RateLimitPolicy("PREMIUM", 20, 5);

        service.updatePolicy("101", policy);

        verify(hashOperations).putAll(
                eq("rate_policy:USER:101"),
                anyMap()
        );
    }

    @Test
    void shouldRejectEmptyUserId() {
        RateLimitPolicy policy =
                new RateLimitPolicy("FREE", 5, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePolicy(" ", policy)
        );
    }

    @Test
    void shouldRejectInvalidPolicy() {
        RateLimitPolicy policy =
                new RateLimitPolicy("FREE", 0, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updatePolicy("101", policy)
        );
    }

    @Test
    void shouldReturnDefaultPolicyWhenRedisFailsInFailOpenMode() {
        RateLimitPolicyService failOpenService =
                new RateLimitPolicyService(
                        redisTemplate,
                        new RateLimiterProperties("fail-open"),
                        new SimpleMeterRegistry()
                );

        when(hashOperations.entries("rate_policy:USER:101"))
                .thenThrow(new DataAccessResourceFailureException(
                        "Redis unavailable"
                ));

        RateLimitPolicy policy = failOpenService.getPolicy("101");

        assertEquals("FREE", policy.plan());
        assertEquals(5, policy.capacity());
    }

    @Test
    void shouldThrowExceptionWhenRedisFailsInFailClosedMode() {
        when(hashOperations.entries("rate_policy:USER:101"))
                .thenThrow(new DataAccessResourceFailureException(
                        "Redis unavailable"
                ));

        assertThrows(
                RedisUnavailableException.class,
                () -> service.getPolicy("101")
        );
    }
}
