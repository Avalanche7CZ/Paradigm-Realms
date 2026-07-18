package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupFilenamePolicy;
import eu.avalanche7.paradigmrealms.backup.BackupId;
import eu.avalanche7.paradigmrealms.backup.BackupIntegrityStatus;
import eu.avalanche7.paradigmrealms.backup.BackupManifest;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.backup.RealmBackupConfig;
import eu.avalanche7.paradigmrealms.backup.io.BackupArchiveWriter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;

final class FabricBackupPackager {
    private final FabricBackupPaths paths;
    private final RealmBackupConfig config;
    private final String worldIdentity;
    private final BackupArchiveWriter archiveWriter = new BackupArchiveWriter();

    FabricBackupPackager(
            FabricBackupPaths paths,
            RealmBackupConfig config,
            String worldIdentity) {
        this.paths = paths;
        this.config = config;
        this.worldIdentity = worldIdentity;
    }

    PackagedBackup packageBackup(
            FabricBackupCaptureContext context,
            FabricBackupStorageAccess.CapturedChunks captured) throws IOException {
        BackupManifest manifest = manifest(context, captured);
        Path realmDirectory = paths.realmDirectory(context.realm().id().value());
        Path archive = realmDirectory.resolve(filename(context, realmDirectory, manifest.backupId()));
        BackupArchiveWriter.Result result = archiveWriter.write(
                archive,
                manifest,
                context.metadata(),
                captured.chunks(),
                captured.regionFiles(),
                config.compression().level());
        return new PackagedBackup(manifest, result);
    }

    BackupCatalogEntry catalogEntry(PackagedBackup packaged) {
        BackupManifest manifest = packaged.manifest();
        EnumMap<BackupStorageKind, Integer> counts = new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            counts.put(kind, manifest.chunkCount(kind));
        }

        return new BackupCatalogEntry(
                manifest.backupId(),
                manifest.realmId(),
                manifest.ownerUuid(),
                manifest.ownerNameSnapshot(),
                manifest.createdAt(),
                manifest.reason(),
                packaged.result().sizeBytes(),
                relativeArchivePath(packaged.result().archive()),
                BackupIntegrityStatus.VERIFIED,
                manifest.pinned(),
                false,
                manifest.formatVersion(),
                manifest.minecraftVersion(),
                manifest.realmsVersion(),
                counts,
                manifest.allocationProfile(),
                manifest.strategy(),
                manifest.strategy() == eu.avalanche7.paradigmrealms.backup.BackupStrategy.REGION_COPY
                        ? manifest.regionFiles().size() : manifest.totalChunkCount());
    }

    private BackupManifest manifest(
            FabricBackupCaptureContext context,
            FabricBackupStorageAccess.CapturedChunks captured) {
        Map<BackupStorageKind, Map<ChunkCoordinate, Path>> chunks = captured.chunks();
        EnumMap<BackupStorageKind, List<ChunkCoordinate>> coordinates =
                new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            coordinates.put(
                    kind,
                    chunks.getOrDefault(kind, Map.of()).keySet().stream().sorted().toList());
        }

        var realm = context.realm();
        return new BackupManifest(
                BackupManifest.CURRENT_FORMAT_VERSION,
                context.operation().backupId(),
                context.operation().createdAt(),
                context.operation().actor(),
                context.operation().reason(),
                SharedConstants.getGameVersion().getName(),
                modVersion(),
                realm.dimension().toString(),
                worldIdentity,
                realm.id().value(),
                realm.owner().uuid(),
                context.ownerName(),
                realm.preset().value(),
                realm.state().name(),
                realm.allocation().profile().value(),
                context.bounds(),
                captured.strategy(),
                coordinates,
                captured.regionFiles().keySet().stream().sorted().toList(),
                "SNAPSHOT_INCLUDED",
                false);
    }

    private String filename(
            FabricBackupCaptureContext context,
            Path directory,
            BackupId backupId) throws IOException {
        Set<String> existing = new HashSet<>();
        try (var files = Files.list(directory)) {
            files.map(path -> path.getFileName().toString()).forEach(existing::add);
        }
        return new BackupFilenamePolicy(
                config.filenameTemplate(),
                config.filenameZone(),
                180).create(
                        context.ownerName(),
                        context.realm().id().value(),
                        context.operation().createdAt(),
                        backupId,
                        existing);
    }

    private String relativeArchivePath(Path archive) {
        return paths.backupRoot()
                .relativize(archive)
                .toString()
                .replace('\\', '/');
    }

    private static String modVersion() {
        return FabricLoader.getInstance()
                .getModContainer(ParadigmRealms.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    record PackagedBackup(
            BackupManifest manifest,
            BackupArchiveWriter.Result result) {}
}
