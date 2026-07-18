package eu.avalanche7.paradigmrealms.platform.operations;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.operations.OperationalAuditEvent;
import eu.avalanche7.paradigmrealms.operations.OperationalAuditSink;

public final class ForgeOperationalAuditLog implements OperationalAuditSink {
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private final Path root;
    private final int retentionDays;
    private final Gson gson = new Gson();
    private final ArrayDeque<OperationalAuditEvent> recent = new ArrayDeque<>();
    private BufferedWriter writer;
    private LocalDate writerDate;
    private int segment;

    public ForgeOperationalAuditLog(Path runDirectory, int retentionDays) throws IOException {
        this.retentionDays = retentionDays;
        Path runRoot = runDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        this.root = runRoot.resolve("logs").resolve("paradigm-realms");
        rejectSymlink(runRoot.resolve("logs"));
        rejectSymlink(root);
        Files.createDirectories(root);
        if (!root.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(runRoot)) {
            throw new IOException("audit directory escapes server root");
        }
        prune();
    }

    @Override public synchronized void append(OperationalAuditEvent event, boolean durable) {
        try {
            rotateIfRequired();
            writer.write(gson.toJson(json(event)));
            writer.newLine();
            if (durable) writer.flush();
            recent.addLast(event);
            while (recent.size() > 200) recent.removeFirst();
        } catch (IOException exception) {
            ParadigmRealms.LOGGER.error("Operational audit write failed: {}", exception.getMessage());
        }
    }

    public synchronized List<OperationalAuditEvent> recent() {
        return List.copyOf(recent);
    }

    @Override public synchronized void close() {
        if (writer == null) return;
        try {
            writer.flush();
            writer.close();
        } catch (IOException exception) {
            ParadigmRealms.LOGGER.error("Operational audit close failed: {}", exception.getMessage());
        } finally {
            writer = null;
        }
    }

    private void rotateIfRequired() throws IOException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (writer != null && writerDate.equals(today)
                && Files.size(path(today, segment)) < MAX_FILE_BYTES) return;
        close();
        writerDate = today;
        segment = 0;
        while (Files.exists(path(today, segment), LinkOption.NOFOLLOW_LINKS)
                && Files.size(path(today, segment)) >= MAX_FILE_BYTES) segment++;
        Path file = path(today, segment);
        rejectSymlink(file);
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    private Path path(LocalDate date, int value) {
        String suffix = value == 0 ? "" : "-" + value;
        return root.resolve("audit-" + date + suffix + ".jsonl");
    }

    private static JsonObject json(OperationalAuditEvent event) {
        JsonObject value = new JsonObject();
        value.addProperty("eventVersion", event.eventVersion());
        value.addProperty("timestamp", event.timestamp().toString());
        value.addProperty("eventType", event.eventType());
        value.addProperty("outcome", event.outcome());
        event.operationId().ifPresent(id -> value.addProperty("operationId", id.toString()));
        event.actor().ifPresent(id -> value.addProperty("actorUuid", id.toString()));
        event.actorName().ifPresent(name -> value.addProperty("actorName", name));
        event.target().ifPresent(id -> value.addProperty("targetUuid", id.toString()));
        event.realmId().ifPresent(id -> value.addProperty("realmId", id.value()));
        event.wildsEpoch().ifPresent(epoch -> value.addProperty("wildsEpoch", epoch));
        JsonObject details = new JsonObject();
        event.details().forEach(details::addProperty);
        value.add("details", details);
        return value;
    }

    private void prune() throws IOException {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(retentionDays);
        try (var files = Files.list(root)) {
            files.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().matches("audit-\\d{4}-\\d{2}-\\d{2}(-\\d+)?\\.jsonl"))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return LocalDate.parse(name.substring(6, 16)).isBefore(cutoff);
                    }).forEach(path -> {
                        try { Files.deleteIfExists(path); }
                        catch (IOException exception) {
                            ParadigmRealms.LOGGER.warn("Could not prune audit file {}", path.getFileName());
                        }
                    });
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("audit path must not be a symlink");
        }
    }
}
