package eu.avalanche7.paradigmrealms.wilds;

public interface WildsStateStore {
    WildsState load();
    void save(WildsState state);
    default void flush() {}
}
