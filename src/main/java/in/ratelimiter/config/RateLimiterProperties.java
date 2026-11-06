package in.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limiter.redis")
public record RateLimiterProperties(
        String failureStrategy
) {
    public RateLimiterProperties {
        if (failureStrategy == null || failureStrategy.isBlank()) {
            failureStrategy = "fail-closed";
        }

        failureStrategy = failureStrategy.toLowerCase();

        if (!failureStrategy.equals("fail-open")
                && !failureStrategy.equals("fail-closed")) {
            throw new IllegalArgumentException(
                    "Failure strategy must be fail-open or fail-closed"
            );
        }
    }

    public boolean isFailOpen() {
        return failureStrategy.equals("fail-open");
    }
}