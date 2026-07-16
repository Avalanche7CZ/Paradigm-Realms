package eu.avalanche7.paradigmrealms.backup;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;

public final class BackupFilenamePolicy {
    public static final String DEFAULT_TEMPLATE = "{owner}-Realm-{realm_id}-{dd-MM-yyyy_HH-mm}-Backup.zip";
    private static final String LOCAL_DATE_PLACEHOLDER = "{dd-MM-yyyy_HH-mm}";
    private static final String LEGACY_DATE_PLACEHOLDER = "{yyyy-MM-dd_HH-mm}";
    private static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm");
    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private final String template;
    private final ZoneId zone;
    private final int maximumLength;

    public BackupFilenamePolicy(String template, ZoneId zone, int maximumLength) {
        this.template = Objects.requireNonNull(template, "template");
        this.zone = Objects.requireNonNull(zone, "zone");
        if (maximumLength < 64 || maximumLength > 240) throw new IllegalArgumentException("filename length must be 64..240");
        this.maximumLength = maximumLength;
        validateTemplate(template);
    }

    public String create(String owner, long realmId, Instant createdAt, BackupId backupId, Set<String> existing) {
        String safeOwner = sanitize(owner == null || owner.isBlank() ? "UnknownOwner" : owner, 64);
        String value = template.replace("{owner}", safeOwner).replace("{realm_id}", Long.toString(realmId))
                .replace(LOCAL_DATE_PLACEHOLDER, LOCAL_DATE.withZone(zone).format(createdAt))
                .replace(LEGACY_DATE_PLACEHOLDER, LEGACY_DATE.withZone(zone).format(createdAt));
        value = sanitize(value, maximumLength);
        if (!value.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) value = fit(value, ".zip");
        if (!existing.contains(value)) return value;
        String suffix = "-" + backupId.value().substring(0, 8) + ".zip";
        String stem = value.substring(0, value.length() - 4);
        return fit(stem, suffix);
    }

    public String sanitize(String input, int limit) {
        String normalized = Normalizer.normalize(Objects.requireNonNull(input, "input"), Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset); offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint) || codePoint == '/' || codePoint == '\\' || codePoint == ':') {
                result.append('_');
            } else result.appendCodePoint(codePoint);
        }
        String value = result.toString().replace("..", "_").strip();
        while (value.startsWith(".")) value = value.substring(1);
        if (value.isBlank()) value = "UnknownOwner";
        if (value.length() > limit) value = value.substring(0, limit).strip();
        return value;
    }

    private String fit(String stem, String suffix) {
        int keep = Math.max(1, maximumLength - suffix.length());
        if (stem.length() > keep) stem = stem.substring(0, keep).strip();
        return stem + suffix;
    }

    private static void validateTemplate(String template) {
        if (!template.contains("{owner}") || !template.contains("{realm_id}")
                || (!template.contains(LOCAL_DATE_PLACEHOLDER) && !template.contains(LEGACY_DATE_PLACEHOLDER))) {
            throw new IllegalArgumentException("backup filename template is missing a required placeholder");
        }
        if (template.contains("/") || template.contains("\\") || template.contains(":") || template.contains("..")) {
            throw new IllegalArgumentException("backup filename template contains an unsafe path sequence");
        }
    }
}
