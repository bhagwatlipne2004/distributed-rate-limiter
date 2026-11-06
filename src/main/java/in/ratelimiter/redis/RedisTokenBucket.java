
package in.ratelimiter.redis;

import in.ratelimiter.config.RateLimiterProperties;
import in.ratelimiter.dto.RateLimitResult;
import in.ratelimiter.exception.RedisUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisTokenBucket {

    private final RateLimiterProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final TokenBucketScript tokenBucketScript;
    private final Counter redisFailures;

    public RedisTokenBucket(
            RateLimiterProperties properties,
            StringRedisTemplate redisTemplate,
            TokenBucketScript tokenBucketScript, MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
        this.redisFailures = meterRegistry.counter(
                "rate_limiter.redis.failures"
        );
    }

    public RateLimitResult allowRequest(
            String identifier,
            long capacity,
            double refillRate
    ) {
        return allowRequest(identifier, capacity, refillRate, 1);
    }

    public RateLimitResult allowRequest(
            String identifier,
            long capacity,
            double refillRate,
            long requestCost
    ) {

        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(
                    "Identifier cannot be empty"
            );
        }

        if (capacity <= 0 || refillRate <= 0) {
            throw new IllegalArgumentException(
                    "Capacity and refill rate must be positive"
            );
        }

        if (requestCost <= 0 || requestCost > capacity) {
            throw new IllegalArgumentException(
                    "Invalid request cost"
            );
        }

        String key = "rate_limit:" + identifier;

        try {

            List<?> result = redisTemplate.execute(
                    tokenBucketScript.getScript(),
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(requestCost)
            );

            if (result == null || result.size() < 3) {
                throw new IllegalStateException(
                        "Invalid response from Redis"
                );
            }

            Number allowed = (Number) result.get(0);

            Number remainingTokens =
                    (Number) result.get(1);

            Number retryAfter =
                    (Number) result.get(2);

            return new RateLimitResult(
                    allowed.longValue() == 1,
                    remainingTokens.doubleValue(),
                    retryAfter.longValue()
            );

        } catch (DataAccessException ex) {
            redisFailures.increment();
            if (properties.isFailOpen()) {
                return new RateLimitResult(
                        true,
                        capacity,
                        0
                );
            }

            throw new RedisUnavailableException(
                    "Rate limiting service is temporarily unavailable",
                    ex
            );
        }
    }
}
