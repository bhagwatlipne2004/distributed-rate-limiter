package in.ratelimiter.dto;

public record RateLimitResult(
        boolean allowed,
        double remainingTokens,
        long retryAfterSeconds
) {}