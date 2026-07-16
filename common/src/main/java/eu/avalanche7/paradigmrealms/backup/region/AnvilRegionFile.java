package eu.avalanche7.paradigmrealms.backup.region;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;

public final class AnvilRegionFile implements AutoCloseable {
    public static final int SECTOR_BYTES = 4096;
    public static final int HEADER_BYTES = 8192;
    public static final int SLOTS = 1024;
    private final Path path;
    private final int regionX;
    private final int regionZ;
    private final FileChannel channel;
    private final Location[] locations = new Location[SLOTS];
    private final int[] timestamps = new int[SLOTS];

    private AnvilRegionFile(Path path, int regionX, int regionZ, FileChannel channel) throws IOException {
        this.path = path;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.channel = channel;
        readAndValidate();
    }

    public static AnvilRegionFile open(Path path, int regionX, int regionZ) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("region file is a symlink: " + path.getFileName());
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("region file is missing: " + path.getFileName());
        }
        return new AnvilRegionFile(path, regionX, regionZ, FileChannel.open(path, StandardOpenOption.READ));
    }

    public Optional<Entry> entry(ChunkCoordinate coordinate) throws IOException {
        int index = index(coordinate, regionX, regionZ);
        Location location = locations[index];
        if (location == null) {
            return Optional.empty();
        }

        ByteBuffer prefix = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        readFully(channel, prefix, (long) location.sectorOffset() * SECTOR_BYTES);
        prefix.flip();

        int length = prefix.getInt();
        int compression = Byte.toUnsignedInt(prefix.get());
        if (length < 1 || length > (long) location.sectorCount() * SECTOR_BYTES - 4) {
            throw new IOException("invalid chunk length at " + coordinate);
        }

        boolean external = (compression & 0x80) != 0;
        int compressionId = compression & 0x7f;
        if (compressionId < 1 || compressionId > 4) {
            throw new IOException("bad compression header at " + coordinate);
        }
        if (external && length != 1) {
            throw new IOException("external chunk has an inline payload at " + coordinate);
        }

        Path externalPath = externalPath(path.getParent(), coordinate);
        if (external && (Files.isSymbolicLink(externalPath)
                || !Files.isRegularFile(externalPath, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("external chunk payload is missing at " + coordinate);
        }
        return Optional.of(new Entry(
                coordinate,
                location,
                timestamps[index],
                length,
                compressionId,
                external,
                external ? Optional.of(externalPath) : Optional.empty()));
    }

    public String fingerprint(Entry entry) throws IOException {
        MessageDigest digest = sha256();
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        long remaining = 4L + entry.length();
        long position = (long) entry.location().sectorOffset() * SECTOR_BYTES;

        while (remaining > 0) {
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), remaining));
            int read = channel.read(buffer, position);
            if (read < 0) {
                throw new IOException("truncated chunk record");
            }
            if (read == 0) {
                continue;
            }
            digest.update(buffer.array(), 0, read);
            position += read;
            remaining -= read;
        }

        if (entry.external()) {
            try (var input = Files.newInputStream(entry.externalPath().orElseThrow())) {
                byte[] bytes = new byte[64 * 1024];
                int read;
                while ((read = input.read(bytes)) >= 0) {
                    if (read > 0) {
                        digest.update(bytes, 0, read);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public void copyRecord(Entry entry, FileChannel destination, long destinationOffset) throws IOException {
        long remaining = 4L + entry.length();
        long sourceOffset = (long) entry.location().sectorOffset() * SECTOR_BYTES;
        while (remaining > 0) {
            destination.position(destinationOffset);
            long copied = channel.transferTo(sourceOffset, remaining, destination);
            if (copied <= 0) {
                throw new IOException("could not copy region record");
            }
            sourceOffset += copied;
            destinationOffset += copied;
            remaining -= copied;
        }
    }

    public int timestamp(ChunkCoordinate coordinate) {
        return timestamps[index(coordinate, regionX, regionZ)];
    }

    public Path path() {
        return path;
    }

    private void readAndValidate() throws IOException {
        long size = channel.size();
        if (size < HEADER_BYTES) {
            throw new IOException("truncated region header");
        }

        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        readFully(channel, header, 0);
        header.flip();

        Set<Integer> occupied = new HashSet<>();
        occupied.add(0);
        occupied.add(1);
        long sectors = (size + SECTOR_BYTES - 1) / SECTOR_BYTES;
        for (int i = 0; i < SLOTS; i++) {
            int packed = header.getInt();
            if (packed == 0) {
                continue;
            }
            int offset = packed >>> 8;
            int count = packed & 0xff;
            if (offset < 2 || count < 1 || (long) offset + count > sectors) {
                throw new IOException("invalid location table entry " + i);
            }
            for (int sector = offset; sector < offset + count; sector++) {
                if (!occupied.add(sector)) {
                    throw new IOException("overlapping sectors in region file");
                }
            }
            locations[i] = new Location(offset, count);
        }
        for (int i = 0; i < SLOTS; i++) {
            timestamps[i] = header.getInt();
        }
        for (int i = 0; i < SLOTS; i++) {
            if (locations[i] != null) {
                entry(coordinate(i, regionX, regionZ));
            }
        }
    }

    public static int index(ChunkCoordinate coordinate, int regionX, int regionZ) {
        int localX = coordinate.x() - regionX * 32;
        int localZ = coordinate.z() - regionZ * 32;
        if (localX < 0 || localX > 31 || localZ < 0 || localZ > 31) {
            throw new IllegalArgumentException("chunk is outside region");
        }
        return localX + localZ * 32;
    }

    public static ChunkCoordinate coordinate(int index, int regionX, int regionZ) {
        return new ChunkCoordinate(
                regionX * 32 + index % 32,
                regionZ * 32 + index / 32);
    }

    public static Path externalPath(Path directory, ChunkCoordinate coordinate) {
        return directory.resolve("c." + coordinate.x() + '.' + coordinate.z() + ".mcc");
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target, position);
            if (read < 0) {
                throw new IOException("unexpected end of region file");
            }
            if (read == 0) {
                continue;
            }
            position += read;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public record Location(int sectorOffset, int sectorCount) {}
    public record Entry(ChunkCoordinate coordinate, Location location, int timestamp, int length,
            int compressionId, boolean external, Optional<Path> externalPath) {}
}
