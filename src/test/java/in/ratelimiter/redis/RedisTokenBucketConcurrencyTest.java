package in.ratelimiter.redis;

import in.ratelimiter.dto.RateLimitResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RedisTokenBucketConcurrencyTest {

    @Autowired
    private RedisTokenBucket redisTokenBucket;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldAllowOnlyCapacityRequestsUnderConcurrency()
            throws InterruptedException, ExecutionException {

        int totalRequests = 100;
        int capacity = 10;

        double refillRate = 0.000001;

        String identifier = "CONCURRENT:" + UUID.randomUUID();
        String redisKey = "rate_limit:" + identifier;

        ExecutorService executor =
                Executors.newFixedThreadPool(totalRequests);

        CountDownLatch readyLatch =
                new CountDownLatch(totalRequests);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<RateLimitResult>> futures = new ArrayList<>();

        try {

            // Submit all requests.
            for (int i = 0; i < totalRequests; i++) {

                Future<RateLimitResult> future = executor.submit(() -> {

                    readyLatch.countDown();

                    // Wait until all workers are ready.
                    startLatch.await();

                    return redisTokenBucket.allowRequest(
                            identifier,
                            capacity,
                            refillRate
                    );
                });

                futures.add(future);
            }

            // Wait until all workers are ready.
            assertTrue(
                    readyLatch.await(10, TimeUnit.SECONDS),
                    "Workers did not become ready in time"
            );

            // Release all workers simultaneously.
            startLatch.countDown();

            int allowedRequests = 0;
            int rejectedRequests = 0;

            for (Future<RateLimitResult> future : futures) {

                RateLimitResult result = future.get();

                if (result.allowed()) {
                    allowedRequests++;
                } else {
                    rejectedRequests++;
                }
            }

            System.out.println("Total requests: " + totalRequests);
            System.out.println("Allowed requests: " + allowedRequests);
            System.out.println("Rejected requests: " + rejectedRequests);

            assertEquals(
                    capacity,
                    allowedRequests,
                    "Only bucket capacity requests should be allowed"
            );

            assertEquals(
                    totalRequests - capacity,
                    rejectedRequests,
                    "Remaining requests should be rejected"
            );

        } finally {

            startLatch.countDown();

            executor.shutdownNow();

            redisTemplate.delete(redisKey);
        }
    }
}