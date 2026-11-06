package in.ratelimiter.dto;

public record RateLimitPolicy(
        String plan,
        long capacity,
        double refillRate
) {
}