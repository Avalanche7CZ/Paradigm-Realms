package eu.avalanche7.paradigmrealms.backup;

public record ChunkCoordinate(int x, int z) implements Comparable<ChunkCoordinate> {
    public String archiveName(BackupStorageKind kind) {
        return "chunks/" + kind.directory() + '/' + x + '_' + z + ".chunk";
    }

    public static ChunkCoordinate parseArchiveName(String name, BackupStorageKind kind) {
        String prefix = "chunks/" + kind.directory() + '/';
        if (!name.startsWith(prefix) || !name.endsWith(".chunk")) {
            throw new IllegalArgumentException("unexpected chunk entry " + name);
        }
        String value = name.substring(prefix.length(), name.length() - 6);
        int split = value.indexOf('_', 1);
        if (split < 0 || value.indexOf('_', split + 1) >= 0) {
            throw new IllegalArgumentException("malformed chunk entry " + name);
        }
        return new ChunkCoordinate(Integer.parseInt(value.substring(0, split)),
                Integer.parseInt(value.substring(split + 1)));
    }

    @Override public int compareTo(ChunkCoordinate other) {
        int xOrder = Integer.compare(x, other.x);
        return xOrder == 0 ? Integer.compare(z, other.z) : xOrder;
    }
}
