package in.ratelimiter.service;

import in.ratelimiter.dto.RateLimitPolicy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import in.ratelimiter.config.RateLimiterProperties;
import in.ratelimiter.exception.RedisUnavailableException;
import org.springframework.dao.DataAccessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;

@Service
public class RateLimitPolicyService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimiterProperties properties;
    private final Counter redisFailures;

    private static final long DEFAULT_CAPACITY = 5;
    private static final double DEFAULT_REFILL_RATE = 1;

    public RateLimitPolicyService(
            StringRedisTemplate redisTemplate,
            RateLimiterProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;

        this.redisFailures = meterRegistry.counter(
                "rate_limiter.redis.failures"
        );
    }

    public RateLimitPolicy getPolicy(String userId) {

        String key = "rate_policy:USER:" + userId;

        try {

            Map<Object, Object> policyData =
                    redisTemplate.opsForHash().entries(key);

            if (policyData.isEmpty()) {
                return new RateLimitPolicy(
                        "FREE",
                        DEFAULT_CAPACITY,
                        DEFAULT_REFILL_RATE
                );
            }

            String plan = String.valueOf(policyData.get("plan"));

            long capacity = Long.parseLong(
                    String.valueOf(policyData.get("capacity"))
            );

            double refillRate = Double.parseDouble(
                    String.valueOf(policyData.get("refillRate"))
            );

            return new RateLimitPolicy(plan, capacity, refillRate);

        } catch (DataAccessException ex) {

            redisFailures.increment();

            if (properties.isFailOpen()) {
                return new RateLimitPolicy(
                        "FREE",
                        DEFAULT_CAPACITY,
                        DEFAULT_REFILL_RATE
                );
            }

            throw new RedisUnavailableException(
                    "Rate limiting service is temporarily unavailable",
                    ex
            );
        }
    }

    public void updatePolicy(String userId, RateLimitPolicy policy) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }

        if (policy == null
                || policy.capacity() <= 0
                || policy.refillRate() <= 0) {
            throw new IllegalArgumentException("Invalid rate limit policy");
        }

        String key = "rate_policy:USER:" + userId;

        Map<String, String> policyData = Map.of(
                "plan", policy.plan(),
                "capacity", String.valueOf(policy.capacity()),
                "refillRate", String.valueOf(policy.refillRate())
        );

        redisTemplate.opsForHash().putAll(key, policyData);
    }
}