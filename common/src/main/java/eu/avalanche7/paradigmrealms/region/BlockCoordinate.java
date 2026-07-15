package eu.avalanche7.paradigmrealms.region;

public record BlockCoordinate(int x, int y, int z) {
    public ChunkCoordinate chunk() {
        return new ChunkCoordinate(CoordinateMath.blockToChunk(x), CoordinateMath.blockToChunk(z));
    }
}
