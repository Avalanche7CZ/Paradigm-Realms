package eu.avalanche7.paradigmrealms.region;

public final class CoordinateMath {
    public static final int BLOCKS_PER_CHUNK = 16;

    private CoordinateMath() {
    }

    public static int blockToChunk(int block) {
        return Math.floorDiv(block, BLOCKS_PER_CHUNK);
    }

    public static int blockToChunk(long block) {
        return Math.toIntExact(Math.floorDiv(block, BLOCKS_PER_CHUNK));
    }

    public static int chunkToMinBlock(int chunk) {
        return Math.toIntExact(Math.multiplyExact((long) chunk, BLOCKS_PER_CHUNK));
    }

    public static int chunkToMaxBlock(int chunk) {
        return Math.toIntExact(Math.addExact(
                Math.multiplyExact((long) chunk, BLOCKS_PER_CHUNK), BLOCKS_PER_CHUNK - 1L));
    }

    public static ChunkCoordinate chunkContaining(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
        long blockX = checkedFloorToLong(x);
        long blockZ = checkedFloorToLong(z);
        return new ChunkCoordinate(blockToChunk(blockX), blockToChunk(blockZ));
    }

    public static BlockBounds toBlockBounds(ChunkBounds chunks) {
        return new BlockBounds(
                chunkToMinBlock(chunks.minX()),
                chunkToMinBlock(chunks.minZ()),
                chunkToMaxBlock(chunks.maxX()),
                chunkToMaxBlock(chunks.maxZ()));
    }

    private static long checkedFloorToLong(double value) {
        double floor = Math.floor(value);
        if (floor < Long.MIN_VALUE || floor > Long.MAX_VALUE) {
            throw new ArithmeticException("block coordinate does not fit in a long");
        }
        return (long) floor;
    }
}
