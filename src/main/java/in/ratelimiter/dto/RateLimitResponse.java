package in.ratelimiter.dto;

public record RateLimitResponse(
        String message,
        boolean allowed
) {
}
