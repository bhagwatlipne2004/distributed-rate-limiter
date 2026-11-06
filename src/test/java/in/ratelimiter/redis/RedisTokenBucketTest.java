package in.ratelimiter.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisTokenBucketTest {

    @Autowired
    private RedisTokenBucket redisTokenBucket;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String identifier;

    @BeforeEach
    void setUp() {

        identifier = "TEST:" + UUID.randomUUID();

        redisTemplate.delete("rate_limit:" + identifier);
    }

    @Test
    void shouldAllowRequestsWithinBucketCapacity() {

        for (int i = 0; i < 5; i++) {

            assertTrue(
                    redisTokenBucket.allowRequest(identifier, 5, 1).allowed()
            );
        }
    }

    @Test
    void shouldRejectRequestsWhenBucketIsEmpty() {

        for (int i = 0; i < 5; i++) {
            assertTrue(
                    redisTokenBucket.allowRequest(identifier, 5, 1).allowed()
            );
        }

        assertFalse(
                redisTokenBucket.allowRequest(identifier, 5, 1).allowed()
        );
    }

    @Test
    void shouldAllowRequestsAfterTokenRefill()
            throws InterruptedException {

        for (int i = 0; i < 5; i++) {
            assertTrue(
                    redisTokenBucket.allowRequest(identifier, 5, 1).allowed()
            );
        }

        Thread.sleep(1100);

        assertTrue(
                redisTokenBucket.allowRequest(identifier, 5, 1).allowed()
        );
    }
}
