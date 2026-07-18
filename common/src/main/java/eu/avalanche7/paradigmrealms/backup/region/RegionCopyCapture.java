package eu.avalanche7.paradigmrealms.backup.region;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;

public final class RegionCopyCapture {
    public Map<String, Path> capture(
            Path dimensionDirectory, BackupCellBounds bounds, Path stagingDirectory) throws IOException {
        int regionX = regionCoordinate(bounds.minimumChunkX(), bounds.maximumChunkX());
        int regionZ = regionCoordinate(bounds.minimumChunkZ(), bounds.maximumChunkZ());
        LinkedHashMap<String, Path> captured = new LinkedHashMap<>();

        for (BackupStorageKind kind : BackupStorageKind.values()) {
            String directoryName = storageDirectory(kind);
            Path directory = dimensionDirectory.resolve(directoryName);
            Path region = directory.resolve("r." + regionX + '.' + regionZ + ".mca");
            if (!Files.exists(region, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            requireRegular(region);

            LinkedHashMap<String, Path> sources = new LinkedHashMap<>();
            sources.put(directoryName + '/' + region.getFileName(), region);
            try (AnvilRegionFile file = AnvilRegionFile.open(region, regionX, regionZ)) {
                for (ChunkCoordinate coordinate : bounds.coordinates()) {
                    var entry = file.entry(coordinate);
                    if (entry.isPresent() && entry.orElseThrow().external()) {
                        Path external = entry.orElseThrow().externalPath().orElseThrow();
                        requireRegular(external);
                        sources.put(directoryName + '/' + external.getFileName(), external);
                    }
                }
            }

            for (Map.Entry<String, Path> source : sources.entrySet()) {
                Path destination = stagingDirectory.resolve("region-copy").resolve(source.getKey());
                Files.createDirectories(destination.getParent());
                Files.copy(source.getValue(), destination, StandardCopyOption.COPY_ATTRIBUTES);
                captured.put(source.getKey(), destination);
            }
        }
        return Map.copyOf(captured);
    }

    private static int regionCoordinate(int minimum, int maximum) {
        if (maximum - minimum + 1 != 32 || Math.floorMod(minimum, 32) != 0) {
            throw new IllegalArgumentException("REGION_COPY bounds must cover exactly one aligned region");
        }
        return Math.floorDiv(minimum, 32);
    }

    private static void requireRegular(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("region-copy source is missing or unsafe: " + path.getFileName());
        }
    }

    public static String storageDirectory(BackupStorageKind kind) {
        return switch (kind) {
            case TERRAIN -> "region";
            case ENTITIES -> "entities";
            case POI -> "poi";
        };
    }
}
