package eu.avalanche7.paradigmrealms.platform.operations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.platform.FabricRealmRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public final class FabricSupportExport {
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss")
            .withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);

    private FabricSupportExport() {}

    public static String export(
            MinecraftServer server, FabricRealmRuntime runtime) throws IOException {
        Path runRoot = server.getRunDirectory().toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path supportRoot = runRoot.resolve("support").resolve("paradigm-realms");
        rejectSymlink(runRoot.resolve("support"));
        rejectSymlink(supportRoot);
        Files.createDirectories(supportRoot);
        Path canonicalSupport = supportRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!canonicalSupport.startsWith(runRoot)) throw new IOException("support directory escapes server root");
        String fileName = "paradigm-realms-support-" + NAME.format(Instant.now()) + ".zip";
        Path target = canonicalSupport.resolve(fileName);
        Path temporary = canonicalSupport.resolve(fileName + ".tmp");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("support export collision");
        byte[] report = report(runtime).getBytes(StandardCharsets.UTF_8);
        if (report.length > 2_000_000) throw new IOException("support report exceeds bounded size");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
            zip.putNextEntry(new ZipEntry("report.json"));
            zip.write(report);
            zip.closeEntry();
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target);
        }
        return runRoot.relativize(target).toString().replace('\\', '/');
    }

    private static String report(FabricRealmRuntime runtime) {
        JsonObject root = new JsonObject();
        root.addProperty("generatedAt", Instant.now().toString());
        root.addProperty("schemaVersion", runtime.repository().schemaMetadata().schemaVersion().value());
        root.addProperty("realmRevision", runtime.repository().schemaMetadata().revision());
        JsonArray checks = new JsonArray();
        runtime.validate().issues().forEach(check -> {
            JsonObject value = new JsonObject();
            value.addProperty("severity", check.severity().name());
            value.addProperty("code", check.code());
            value.addProperty("path", check.path());
            value.addProperty("detail", check.message());
            checks.add(value);
        });
        root.add("validation", checks);

        EnumMap<RealmLifecycleState, Integer> counts = new EnumMap<>(RealmLifecycleState.class);
        runtime.repository().list().forEach(realm -> counts.merge(realm.state(), 1, Integer::sum));
        JsonObject realmCounts = new JsonObject();
        counts.forEach((state, count) -> realmCounts.addProperty(state.name(), count));
        root.add("realmCounts", realmCounts);

        JsonObject wilds = new JsonObject();
        wilds.addProperty("state", runtime.wilds().state().lifecycle().name());
        wilds.addProperty("epoch", runtime.wilds().state().activeEpoch());
        wilds.addProperty("verified", runtime.wilds().state().generationVerified());
        wilds.addProperty("operation", runtime.wilds().state().operation()
                .map(operation -> operation.operationId().toString()).orElse("none"));
        root.add("wilds", wilds);

        JsonArray presets = new JsonArray();
        runtime.presetCatalogSnapshot().catalog().all().stream()
                .sorted(Comparator.comparing(preset -> preset.id().value())).forEach(preset -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", preset.id().value());
                    value.addProperty("source", preset.sourceType().name());
                    value.addProperty("enabled", preset.enabled());
                    value.addProperty("fingerprintPresent", preset.fingerprint().isPresent());
                    value.addProperty("compiled", runtime.presetSnapshot().compiledImports().containsKey(preset.id()));
                    presets.add(value);
                });
        root.add("presets", presets);

        JsonObject config = new JsonObject();
        Properties properties = runtime.config().toProperties();
        properties.stringPropertyNames().stream().sorted().forEach(key -> {
            String lower = key.toLowerCase(Locale.ROOT);
            if (!lower.contains("password") && !lower.contains("token") && !lower.contains("secret")) {
                config.addProperty(key, properties.getProperty(key));
            }
        });
        root.add("config", config);

        JsonArray mods = new JsonArray();
        FabricLoader.getInstance().getAllMods().stream()
                .sorted(Comparator.comparing(mod -> mod.getMetadata().getId())).forEach(mod -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", mod.getMetadata().getId());
                    value.addProperty("version", mod.getMetadata().getVersion().getFriendlyString());
                    mods.add(value);
                });
        root.add("mods", mods);
        JsonArray audit = new JsonArray();
        runtime.recentAuditEvents().forEach(event -> {
            JsonObject value = new JsonObject();
            value.addProperty("timestamp", event.timestamp().toString());
            value.addProperty("type", event.eventType());
            value.addProperty("outcome", event.outcome());
            event.realmId().ifPresent(id -> value.addProperty("realmId", id.value()));
            event.wildsEpoch().ifPresent(epoch -> value.addProperty("wildsEpoch", epoch));
            audit.add(value);
        });
        root.add("recentAuditEvents", audit);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("support export directory must not be a symlink");
        }
    }
}
