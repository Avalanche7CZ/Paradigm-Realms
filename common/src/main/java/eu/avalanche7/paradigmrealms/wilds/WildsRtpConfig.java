package eu.avalanche7.paradigmrealms.wilds;

import java.time.Duration;

public record WildsRtpConfig(
        int minimumRadius, int maximumRadius, int maximumAttempts,
        int maximumChunksGeneratedPerRequest, Duration cooldown, Duration timeout,
        boolean avoidFluids, boolean avoidLeaves, boolean avoidPowderSnow) {
    public WildsRtpConfig {
        if (minimumRadius < 0 || maximumRadius <= minimumRadius || maximumRadius > 29_000_000) {
            throw new IllegalArgumentException("invalid RTP radius range");
        }
        if (maximumAttempts < 1 || maximumAttempts > 256) throw new IllegalArgumentException("invalid RTP attempts");
        if (maximumChunksGeneratedPerRequest < 0 || maximumChunksGeneratedPerRequest > maximumAttempts) {
            throw new IllegalArgumentException("invalid RTP chunk budget");
        }
        if (cooldown.isNegative() || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("invalid RTP timing");
        }
    }
}
