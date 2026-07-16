package eu.avalanche7.paradigmrealms.backup.region;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;

public final class RegionFileRewriter {
    public Result rewrite(Path regionDirectory, int regionX, int regionZ, Set<ChunkCoordinate> targetCoordinates,
            Map<ChunkCoordinate, Path> restoredNbt, Path quarantineDirectory, String storageName) throws IOException {
        Files.createDirectories(regionDirectory);
        rejectSymlinkChain(regionDirectory);
        Path region = regionDirectory.resolve("r." + regionX + '.' + regionZ + ".mca");
        if (!Files.exists(region, LinkOption.NOFOLLOW_LINKS)) {
            createEmptyRegion(region);
        }
        if (Files.isSymbolicLink(region)) {
            throw new IOException("region file is a symlink");
        }
        Path temporary = region.resolveSibling(region.getFileName() + ".restore.tmp");
        if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(temporary);
        }
        Map<ChunkCoordinate, String> neighborFingerprints = new HashMap<>();
        List<Path> temporaryExternal = new ArrayList<>();
        Set<Path> removeExternal = new HashSet<>();
        Files.createDirectories(quarantineDirectory.resolve(storageName));
        try (AnvilRegionFile source = AnvilRegionFile.open(region, regionX, regionZ);
                FileChannel output = FileChannel.open(
                        temporary,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE)) {
            output.position(AnvilRegionFile.HEADER_BYTES);
            ByteBuffer locations = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN);
            ByteBuffer timestamps = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN);
            int nextSector = 2;
            for (int slot = 0; slot < AnvilRegionFile.SLOTS; slot++) {
                ChunkCoordinate coordinate = AnvilRegionFile.coordinate(slot, regionX, regionZ);
                Optional<AnvilRegionFile.Entry> current = source.entry(coordinate);
                if (targetCoordinates.contains(coordinate)) {
                    quarantine(current, source, coordinate, quarantineDirectory.resolve(storageName));
                    Path nbt = restoredNbt.get(coordinate);
                    if (nbt == null) {
                        locations.putInt(0);
                        timestamps.putInt(0);
                        removeExternal.add(AnvilRegionFile.externalPath(regionDirectory, coordinate));
                        continue;
                    }
                    Compressed compressed = compress(nbt, regionDirectory, coordinate);
                    temporaryExternal.add(compressed.temporary());
                    int inlineBytes = 5 + Math.toIntExact(compressed.size());
                    int sectors = (inlineBytes + AnvilRegionFile.SECTOR_BYTES - 1)
                            / AnvilRegionFile.SECTOR_BYTES;
                    if (sectors >= 256) {
                        writeExternalMarker(output, nextSector, compressed.compressionId());
                        locations.putInt(nextSector << 8 | 1);
                        timestamps.putInt((int) Instant.now().getEpochSecond());
                        nextSector++;
                    } else {
                        writeInline(output, nextSector, compressed);
                        locations.putInt(nextSector << 8 | sectors);
                        timestamps.putInt((int) Instant.now().getEpochSecond());
                        nextSector += sectors;
                        removeExternal.add(AnvilRegionFile.externalPath(regionDirectory, coordinate));
                    }
                } else if (current.isPresent()) {
                    AnvilRegionFile.Entry entry = current.orElseThrow();
                    neighborFingerprints.put(coordinate, source.fingerprint(entry));
                    int bytes = 4 + entry.length();
                    int sectors = (bytes + AnvilRegionFile.SECTOR_BYTES - 1)
                            / AnvilRegionFile.SECTOR_BYTES;
                    source.copyRecord(entry, output, (long) nextSector * AnvilRegionFile.SECTOR_BYTES);
                    pad(
                            output,
                            (long) nextSector * AnvilRegionFile.SECTOR_BYTES + bytes,
                            sectors * AnvilRegionFile.SECTOR_BYTES - bytes);
                    locations.putInt(nextSector << 8 | sectors);
                    timestamps.putInt(entry.timestamp());
                    nextSector += sectors;
                } else {
                    locations.putInt(0);
                    timestamps.putInt(0);
                }
            }
            locations.flip();
            timestamps.flip();
            output.write(locations, 0);
            output.write(timestamps, 4096);
            output.truncate((long) nextSector * AnvilRegionFile.SECTOR_BYTES);
            output.force(true);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            cleanupTemporaryExternal(temporaryExternal);
            throw exception;
        }

        // Anvil validates external payloads while opening the region. Publish the
        // quarantined target payloads first, then validate and atomically switch
        // the location table that points at them.
        for (Path compressed : temporaryExternal) {
            if (Files.exists(compressed)) {
                String name = compressed.getFileName().toString();
                Path finalPath = compressed.resolveSibling(name.substring(0, name.length() - 4));
                atomicReplace(compressed, finalPath);
            }
        }
        verifyNeighbors(temporary, regionX, regionZ, neighborFingerprints);
        atomicReplace(temporary, region);
        for (Path external : removeExternal) {
            Files.deleteIfExists(external);
        }
        try (AnvilRegionFile result = AnvilRegionFile.open(region, regionX, regionZ)) {
            for (ChunkCoordinate target : targetCoordinates) {
                boolean expected = restoredNbt.containsKey(target);
                if (result.entry(target).isPresent() != expected) {
                    throw new IOException("target verification failed at " + target);
                }
            }
        }
        verifyNeighbors(region, regionX, regionZ, neighborFingerprints);
        return new Result(region, targetCoordinates.size(), neighborFingerprints.size());
    }

    private static void quarantine(Optional<AnvilRegionFile.Entry> entry, AnvilRegionFile source,
            ChunkCoordinate coordinate, Path directory) throws IOException {
        Path target = directory.resolve(coordinate.x() + "_" + coordinate.z() + ".record");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (entry.isEmpty()) {
            Files.write(target, new byte[0], StandardOpenOption.CREATE_NEW);
            return;
        }

        AnvilRegionFile.Entry value = entry.orElseThrow();
        try (FileChannel output = FileChannel.open(
                target,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            source.copyRecord(value, output, 0);
            output.force(true);
        }
        if (value.external()) {
            Files.copy(
                    value.externalPath().orElseThrow(),
                    target.resolveSibling(target.getFileName() + ".mcc"));
        }
    }

    private static Compressed compress(Path source, Path directory, ChunkCoordinate coordinate) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source)) {
            throw new IOException("restored chunk staging file is invalid");
        }
        Path temporary = AnvilRegionFile.externalPath(directory, coordinate)
                .resolveSibling("c." + coordinate.x() + '.' + coordinate.z() + ".mcc.tmp");
        Files.deleteIfExists(temporary);

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (InputStream input = Files.newInputStream(source);
                OutputStream raw = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW);
                DeflaterOutputStream output = new DeflaterOutputStream(raw, deflater, 64 * 1024)) {
            input.transferTo(output);
        }
        return new Compressed(temporary, Files.size(temporary), 2);
    }

    private static void writeExternalMarker(FileChannel output, int sector, int compression) throws IOException {
        ByteBuffer marker = ByteBuffer.allocate(5)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(1)
                .put((byte) (compression | 0x80));
        marker.flip();

        long position = (long) sector * AnvilRegionFile.SECTOR_BYTES;
        output.write(marker, position);
        pad(output, position + 5, AnvilRegionFile.SECTOR_BYTES - 5);
    }

    private static void writeInline(FileChannel output, int sector, Compressed compressed) throws IOException {
        ByteBuffer prefix = ByteBuffer.allocate(5)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(Math.toIntExact(compressed.size() + 1))
                .put((byte) compressed.compressionId());
        prefix.flip();

        long position = (long) sector * AnvilRegionFile.SECTOR_BYTES;
        output.write(prefix, position);
        position += 5;

        try (FileChannel input = FileChannel.open(compressed.temporary(), StandardOpenOption.READ)) {
            copyCompressedChunk(input, output, position, compressed.size());
        }

        int sectors = (int) ((compressed.size() + 5 + AnvilRegionFile.SECTOR_BYTES - 1) / AnvilRegionFile.SECTOR_BYTES);
        long padding = (long) sectors * AnvilRegionFile.SECTOR_BYTES - 5 - compressed.size();
        pad(output, position + compressed.size(), padding);
        Files.delete(compressed.temporary());
    }

    private static void copyCompressedChunk(
            FileChannel input,
            FileChannel output,
            long outputPosition,
            long size) throws IOException {
        long offset = 0;
        while (offset < size) {
            output.position(outputPosition + offset);
            long copied = input.transferTo(offset, size - offset, output);
            if (copied <= 0) {
                throw new IOException("could not copy compressed target chunk");
            }
            offset += copied;
        }
    }

    private static void pad(FileChannel output, long position, long bytes) throws IOException {
        ByteBuffer zero = ByteBuffer.allocate(AnvilRegionFile.SECTOR_BYTES);
        while (bytes > 0) {
            zero.clear();
            zero.limit((int) Math.min(zero.capacity(), bytes));
            int written = output.write(zero, position);
            position += written;
            bytes -= written;
        }
    }

    private static void verifyNeighbors(
            Path region,
            int regionX,
            int regionZ,
            Map<ChunkCoordinate, String> expected) throws IOException {
        try (AnvilRegionFile file = AnvilRegionFile.open(region, regionX, regionZ)) {
            for (Map.Entry<ChunkCoordinate, String> value : expected.entrySet()) {
                AnvilRegionFile.Entry entry = file.entry(value.getKey())
                        .orElseThrow(() -> new IOException("neighbor disappeared at " + value.getKey()));
                if (!value.getValue().equals(file.fingerprint(entry))) {
                    throw new IOException("neighbor changed at " + value.getKey());
                }
            }
        }
    }

    private static void createEmptyRegion(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.allocate(AnvilRegionFile.HEADER_BYTES));
            channel.force(true);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("filesystem does not support required atomic region replacement", exception);
        }
    }

    private static void rejectSymlinkChain(Path path) throws IOException {
        Path cursor = path.toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new IOException("symlink rejected in region path");
            }
            cursor = cursor.getParent();
        }
    }

    private static void cleanupTemporaryExternal(List<Path> values) {
        for (Path value : values) {
            try {
                Files.deleteIfExists(value);
            } catch (IOException ignored) {
                // A later recovery pass removes stale staging files.
            }
        }
    }

    private record Compressed(Path temporary, long size, int compressionId) {}

    public record Result(Path regionFile, int targetSlots, int preservedNeighborSlots) {}
}
