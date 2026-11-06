package in.ratelimiter.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    @Test
    void shouldAllowRequestsWithinCapacity() {

        TokenBucket bucket = new TokenBucket(5, 1);

        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.allowRequest());
        }
    }

    @Test
    void shouldRejectRequestsWhenBucketIsEmpty() {

        TokenBucket bucket = new TokenBucket(2, 1);

        assertTrue(bucket.allowRequest());
        assertTrue(bucket.allowRequest());

        assertFalse(bucket.allowRequest());
    }

    @Test
    void shouldRefillTokensOverTime() throws InterruptedException {

        TokenBucket bucket = new TokenBucket(5, 2);

        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.allowRequest());
        }

        Thread.sleep(1100);

        assertTrue(bucket.allowRequest());
    }

    @Test
    void shouldNotExceedBucketCapacity() throws InterruptedException {

        TokenBucket bucket = new TokenBucket(5, 10);

        Thread.sleep(600);

        assertEquals(5, bucket.getAvailableTokens(), 0.001);
    }
}
