package eu.avalanche7.paradigmrealms.generation.importing;

public final class ImportLimits {
    public static final long MAX_COMPRESSED_BYTES = 16L * 1024 * 1024;
    public static final int MAX_DECOMPRESSED_BYTES = 64 * 1024 * 1024;
    public static final long MAX_VOLUME = 2_000_000;
    public static final int MAX_PLACED_BLOCKS = 250_000;
    public static final int MAX_NBT_DEPTH = 64;
    public static final long MAX_NBT_ELEMENTS = 2_500_000;
    private ImportLimits() {}
}
