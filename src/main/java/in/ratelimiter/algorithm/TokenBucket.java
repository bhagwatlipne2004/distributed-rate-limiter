package in.ratelimiter.algorithm;

public class TokenBucket {

    private final long capacity;
    private final double refillRate;

    private double currentTokens;

    private long lastRefillTime;

    public TokenBucket(long capacity, double refillRate) {

        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be greater than zero"
            );
        }

        if (refillRate <= 0) {
            throw new IllegalArgumentException(
                    "Refill rate must be greater than zero"
            );
        }

        this.capacity = capacity;
        this.refillRate = refillRate;

        this.currentTokens = capacity;
        this.lastRefillTime = System.nanoTime();
    }

    private void refill() {

        long currentTime = System.nanoTime();

        long elapsedTime = currentTime - lastRefillTime;

        double elapsedSeconds = elapsedTime / 1_000_000_000.0;

        double tokensToAdd = elapsedSeconds * refillRate;

        currentTokens = Math.min(
                capacity,
                currentTokens + tokensToAdd
        );

        lastRefillTime = currentTime;
    }

    public synchronized boolean allowRequest() {

        refill();

        if (currentTokens >= 1) {

            currentTokens--;

            return true;
        }

        return false;
    }

    public synchronized double getAvailableTokens() {

        refill();

        return currentTokens;
    }
}
