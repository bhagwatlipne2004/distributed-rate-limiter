
package in.ratelimiter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterPropertiesTest {

    @Test
    void shouldDefaultToFailClosedWhenStrategyIsNull() {
        RateLimiterProperties properties =
                new RateLimiterProperties(null);

        assertEquals("fail-closed", properties.failureStrategy());
        assertFalse(properties.isFailOpen());
    }

    @Test
    void shouldDefaultToFailClosedWhenStrategyIsBlank() {
        RateLimiterProperties properties =
                new RateLimiterProperties(" ");

        assertEquals("fail-closed", properties.failureStrategy());
        assertFalse(properties.isFailOpen());
    }

    @Test
    void shouldAcceptFailOpenStrategy() {
        RateLimiterProperties properties =
                new RateLimiterProperties("fail-open");

        assertTrue(properties.isFailOpen());
    }

    @Test
    void shouldAcceptFailClosedStrategy() {
        RateLimiterProperties properties =
                new RateLimiterProperties("fail-closed");

        assertFalse(properties.isFailOpen());
    }

    @Test
    void shouldHandleUppercaseStrategy() {
        RateLimiterProperties properties =
                new RateLimiterProperties("FAIL-OPEN");

        assertEquals("fail-open", properties.failureStrategy());
        assertTrue(properties.isFailOpen());
    }

    @Test
    void shouldRejectInvalidStrategy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimiterProperties("invalid")
        );
    }
}
