package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import eu.avalanche7.paradigmrealms.backup.io.BackupPathSafety;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

final class FabricBackupPaths {
    private final Path worldRoot;
    private final Path backupRoot;

    FabricBackupPaths(MinecraftServer server) throws IOException {
        worldRoot = server.getSavePath(WorldSavePath.ROOT)
                .toAbsolutePath()
                .normalize();
        backupRoot = worldRoot.resolve("backups/paradigm-realms");

        Files.createDirectories(backupRoot);
        BackupPathSafety.rejectSymlinks(worldRoot, backupRoot);
    }

    Path backupRoot() {
        return backupRoot;
    }

    Path realmDirectory(long realmId) throws IOException {
        Path directory = backupRoot.resolve("realms").resolve(Long.toString(realmId));
        Files.createDirectories(directory);
        BackupPathSafety.rejectSymlinks(backupRoot, directory);
        return directory;
    }

    Path operationDirectory() throws IOException {
        Path directory = backupRoot.resolve("operations");
        Files.createDirectories(directory);
        BackupPathSafety.rejectSymlinks(backupRoot, directory);
        return directory;
    }

    Path stagingDirectory(String relativePath) throws IOException {
        Path directory = BackupPathSafety.resolveInside(
                backupRoot,
                relativePath,
                true);
        Files.createDirectories(directory);
        BackupPathSafety.rejectSymlinks(backupRoot, directory);
        return directory;
    }

    Path worldIdentityFile() throws IOException {
        Path stateRoot = worldRoot.resolve("paradigm-realms");
        Files.createDirectories(stateRoot);
        BackupPathSafety.rejectSymlinks(worldRoot, stateRoot);
        return stateRoot.resolve("world-identity.txt");
    }

    Path restoreManifestDirectory() throws IOException {
        Path directory = worldRoot.resolve("paradigm-realms/restore-manifests");
        Files.createDirectories(directory);
        BackupPathSafety.rejectSymlinks(worldRoot, directory);
        return directory;
    }

    Path scheduleStateFile() throws IOException {
        Path directory = backupRoot.resolve("catalog");
        Files.createDirectories(directory);
        BackupPathSafety.rejectSymlinks(backupRoot, directory);
        return directory.resolve("schedule-enabled-at.txt");
    }

    Path worldRoot() {
        return worldRoot;
    }

    boolean isTemporaryArchive(Path path) {
        return path.getFileName().toString().contains(".tmp-")
                && path.startsWith(backupRoot);
    }
}
