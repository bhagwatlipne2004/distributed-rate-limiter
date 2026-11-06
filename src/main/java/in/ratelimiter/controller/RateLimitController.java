
package in.ratelimiter.controller;

import in.ratelimiter.dto.RateLimitPolicy;
import in.ratelimiter.dto.RateLimitResult;
import in.ratelimiter.redis.RedisTokenBucket;
import in.ratelimiter.service.RateLimitPolicyService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RateLimitController {

    private final RedisTokenBucket redisTokenBucket;
    private final RateLimitPolicyService policyService;

    private final Counter allowedRequests;
    private final Counter rejectedRequests;
    private final Timer requestDuration;

    public RateLimitController(
            RedisTokenBucket redisTokenBucket,
            RateLimitPolicyService policyService,
            MeterRegistry meterRegistry
    ) {
        this.redisTokenBucket = redisTokenBucket;
        this.policyService = policyService;

        this.allowedRequests = meterRegistry.counter(
                "rate_limiter.requests.allowed"
        );

        this.rejectedRequests = meterRegistry.counter(
                "rate_limiter.requests.rejected"
        );

        this.requestDuration = meterRegistry.timer(
                "rate_limiter.request.duration"
        );
    }

    @GetMapping("/resource")
    public ResponseEntity<String> accessResource(
            @RequestHeader("X-User-Id") String userId
    ) {

        Timer.Sample sample = Timer.start();

        try {

            RateLimitPolicy policy = policyService.getPolicy(userId);

            RateLimitResult result = redisTokenBucket.allowRequest(
                    "USER:" + userId,
                    policy.capacity(),
                    policy.refillRate()
            );

            HttpHeaders headers = new HttpHeaders();

            headers.add(
                    "X-RateLimit-Limit",
                    String.valueOf(policy.capacity())
            );

            headers.add(
                    "X-RateLimit-Remaining",
                    String.valueOf((long) Math.floor(result.remainingTokens()))
            );

            if (result.allowed()) {

                allowedRequests.increment();

                return ResponseEntity.ok()
                        .headers(headers)
                        .body("Request allowed!");
            }

            rejectedRequests.increment();

            headers.add(
                    "Retry-After",
                    String.valueOf(result.retryAfterSeconds())
            );

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body("Rate limit exceeded!");

        } finally {

            sample.stop(requestDuration);
        }
    }
}
