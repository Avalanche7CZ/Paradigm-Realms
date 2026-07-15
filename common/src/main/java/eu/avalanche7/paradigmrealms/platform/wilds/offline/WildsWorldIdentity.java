package eu.avalanche7.paradigmrealms.platform.wilds.offline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class WildsWorldIdentity {
    private WildsWorldIdentity() {}

    public static String of(Path canonicalWorldRoot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    canonicalWorldRoot.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
