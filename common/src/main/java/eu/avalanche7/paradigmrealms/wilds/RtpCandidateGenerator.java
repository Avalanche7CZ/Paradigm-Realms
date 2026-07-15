package eu.avalanche7.paradigmrealms.wilds;

import java.util.SplittableRandom;

public final class RtpCandidateGenerator {
    public RtpCandidate candidate(WildsRtpConfig config, long requestSeed, int attempt) {
        if (attempt < 0 || attempt >= config.maximumAttempts()) throw new IllegalArgumentException("attempt out of range");
        SplittableRandom random = new SplittableRandom(requestSeed ^ mix(attempt));
        double min2 = Math.multiplyExact((long) config.minimumRadius(), config.minimumRadius());
        double max2 = Math.multiplyExact((long) config.maximumRadius(), config.maximumRadius());
        double radius = Math.sqrt(min2 + random.nextDouble() * (max2 - min2));
        double angle = random.nextDouble() * Math.PI * 2.0;
        long x = Math.round(Math.cos(angle) * radius);
        long z = Math.round(Math.sin(angle) * radius);
        return new RtpCandidate(Math.toIntExact(x), Math.toIntExact(z));
    }

    private static long mix(int attempt) {
        long value = attempt + 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
