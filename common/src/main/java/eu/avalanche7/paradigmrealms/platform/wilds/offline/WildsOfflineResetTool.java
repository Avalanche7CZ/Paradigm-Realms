package eu.avalanche7.paradigmrealms.platform.wilds.offline;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import eu.avalanche7.paradigmrealms.wilds.WildsManifestStage;
import eu.avalanche7.paradigmrealms.wilds.WildsResetManifest;

public final class WildsOfflineResetTool {
    private WildsOfflineResetTool() {}

    public static void main(String[] args) {
        int result;
        try {
            result = run(args);
        } catch (Exception exception) {
            System.err.println("Wilds reset refused: " + exception.getMessage());
            result = 2;
        }
        if (result != 0) System.exit(result);
    }

    static int run(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 3) || !"--world".equals(args[0])
                || (args.length == 3 && !"--restore".equals(args[2]))) {
            System.err.println("Usage: java -jar paradigm-realms-wilds-reset-tool.jar "
                    + "--world <world-directory> [--restore]");
            return 64;
        }
        Path root = Path.of(args[1]).toRealPath();
        Path sessionLock = root.resolve("session.lock");
        try (FileChannel channel = FileChannel.open(sessionLock,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = tryLock(channel)) {
            WildsManifestFile file = new WildsManifestFile();
            WildsResetManifest manifest = file.read(root)
                    .orElseThrow(() -> new IllegalStateException("no pending Wilds reset manifest"));
            if (!manifest.worldIdentity().equals(WildsWorldIdentity.of(root))) {
                throw new IllegalStateException("manifest belongs to a different world save");
            }
            if (args.length == 3) return restore(root, file, manifest);
            if (manifest.stage() == WildsManifestStage.OLD_WORLD_QUARANTINED) {
                verifyMoved(root, manifest);
                System.out.println("Wilds reset already quarantined for operation " + manifest.operationId());
                return 0;
            }
            if (manifest.stage() != WildsManifestStage.PREPARED) {
                throw new IllegalStateException("manifest stage is " + manifest.stage() + ", expected PREPARED");
            }
            Path source = WildsPathSafety.resolve(root, manifest.expectedRelativeWildsPath(), false);
            Path quarantine = WildsPathSafety.resolve(root, manifest.quarantineRelativePath(), true);
            if (Files.exists(quarantine, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("unique quarantine target already exists");
            }
            Files.createDirectories(quarantine.getParent());
            if (Files.isSymbolicLink(quarantine.getParent())) {
                throw new IllegalStateException("backup parent is a symlink");
            }
            Files.move(source, quarantine, StandardCopyOption.ATOMIC_MOVE);
            file.write(root, manifest.withStage(WildsManifestStage.OLD_WORLD_QUARANTINED, Instant.now()));
            verifyMoved(root, manifest);
            System.out.println("Wilds epoch " + manifest.sourceEpoch() + " quarantined; target epoch "
                    + manifest.targetEpoch() + " is ready for server startup. Backup: "
                    + manifest.quarantineRelativePath());
            return 0;
        }
    }

    private static int restore(
            Path root, WildsManifestFile file, WildsResetManifest manifest) throws Exception {
        if (manifest.stage() == WildsManifestStage.RESTORED_PENDING_VERIFICATION) {
            verifyRestored(root, manifest);
            System.out.println("Wilds source epoch is already restored and waiting for server verification");
            return 0;
        }
        if (manifest.stage() != WildsManifestStage.OLD_WORLD_QUARANTINED
                && manifest.stage() != WildsManifestStage.SERVER_BOOTED
                && manifest.stage() != WildsManifestStage.FAILED) {
            throw new IllegalStateException("manifest stage " + manifest.stage() + " cannot be restored");
        }
        Path source = WildsPathSafety.resolve(root, manifest.expectedRelativeWildsPath(), true);
        Path quarantine = WildsPathSafety.resolve(root, manifest.quarantineRelativePath(), true);
        String failedRelative = "paradigm-realms-backups/wilds/failed-target-epoch-"
                + manifest.targetEpoch() + "-operation-" + manifest.operationId();
        Path failedTarget = WildsPathSafety.resolve(root, failedRelative, true);

        boolean sourceExists = Files.exists(source, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        boolean quarantineExists = Files.isDirectory(
                quarantine, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        boolean failedExists = Files.exists(failedTarget, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (sourceExists && !quarantineExists
                && (failedExists || manifest.stage() == WildsManifestStage.OLD_WORLD_QUARANTINED)) {
            file.write(root, manifest.withStage(
                    WildsManifestStage.RESTORED_PENDING_VERIFICATION, Instant.now()));
            verifyRestored(root, manifest);
            return 0;
        }
        if (!quarantineExists) throw new IllegalStateException("source quarantine is missing");
        if (sourceExists) {
            if (failedExists) throw new IllegalStateException("failed target quarantine already exists");
            Files.createDirectories(failedTarget.getParent());
            Files.move(source, failedTarget, StandardCopyOption.ATOMIC_MOVE);
        }
        if (Files.exists(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("active Wilds still exists before restore");
        }
        Files.move(quarantine, source, StandardCopyOption.ATOMIC_MOVE);
        file.write(root, manifest.withStage(
                WildsManifestStage.RESTORED_PENDING_VERIFICATION, Instant.now()));
        verifyRestored(root, manifest);
        System.out.println("Wilds source epoch " + manifest.sourceEpoch()
                + " restored for server verification; failed target retained at " + failedRelative);
        return 0;
    }

    private static FileLock tryLock(FileChannel channel) throws Exception {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IllegalStateException("world session.lock is held; server may still be running");
            }
            return lock;
        } catch (java.nio.channels.OverlappingFileLockException exception) {
            throw new IllegalStateException("world session.lock is held in this process", exception);
        }
    }

    private static void verifyMoved(Path root, WildsResetManifest manifest) throws Exception {
        Path source = WildsPathSafety.resolve(root, manifest.expectedRelativeWildsPath(), true);
        Path quarantine = WildsPathSafety.resolve(root, manifest.quarantineRelativePath(), false);
        if (Files.exists(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(quarantine)) {
            throw new IllegalStateException("post-move Wilds path invariant failed");
        }
    }

    private static void verifyRestored(Path root, WildsResetManifest manifest) throws Exception {
        Path source = WildsPathSafety.resolve(root, manifest.expectedRelativeWildsPath(), false);
        Path quarantine = WildsPathSafety.resolve(root, manifest.quarantineRelativePath(), true);
        if (!Files.isDirectory(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.exists(quarantine, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("post-restore Wilds path invariant failed");
        }
    }
}
