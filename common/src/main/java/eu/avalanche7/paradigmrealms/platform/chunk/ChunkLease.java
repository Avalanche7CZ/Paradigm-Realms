package eu.avalanche7.paradigmrealms.platform.chunk;

@FunctionalInterface
public interface ChunkLease extends AutoCloseable {
    @Override
    void close();
}
