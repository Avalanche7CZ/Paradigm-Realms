package eu.avalanche7.paradigmrealms.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.generation.BuiltinPresetDefinitions;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionConfig;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.wilds.WildsConfig;
import eu.avalanche7.paradigmrealms.wilds.WildsEntryMode;
import eu.avalanche7.paradigmrealms.wilds.WildsProfileId;
import eu.avalanche7.paradigmrealms.wilds.WildsRtpConfig;

public record RealmsConfig(
        int membershipInviteExpiryMinutes,
        int maximumMembersPerRealm,
        int maximumPendingInvitesPerRealm,
        int presenceValidationIntervalTicks,
        long denialMessageCooldownMillis,
        PresetSelectionConfig presetSelection,
        boolean allowExternalPresets,
        ImportPolicy schematicImportPolicy,
        WildsConfig wilds) {

    public static final RealmsConfig DEFAULTS = new RealmsConfig(1440, 16, 16, 5, 1500,
            new PresetSelectionConfig(BuiltinPresetDefinitions.STARTER_ISLAND_ID, false,
                    Set.of(BuiltinPresetDefinitions.STARTER_ISLAND_ID)), true, ImportPolicy.SANITIZE,
            defaultWilds());

    public RealmsConfig {
        range(membershipInviteExpiryMinutes, 1, 43_200, "membershipInviteExpiryMinutes");
        range(maximumMembersPerRealm, 1, 1_000, "maximumMembersPerRealm");
        range(maximumPendingInvitesPerRealm, 1, 1_000, "maximumPendingInvitesPerRealm");
        range(presenceValidationIntervalTicks, 1, 200, "presenceValidationIntervalTicks");
        if (denialMessageCooldownMillis < 250 || denialMessageCooldownMillis > 60_000) {
            throw new IllegalArgumentException("denialMessageCooldownMillis must be between 250 and 60000");
        }
        java.util.Objects.requireNonNull(presetSelection, "presetSelection");
        java.util.Objects.requireNonNull(schematicImportPolicy, "schematicImportPolicy");
        java.util.Objects.requireNonNull(wilds, "wilds");
    }

    public static RealmsConfig fromProperties(Properties values) {
        java.util.Objects.requireNonNull(values, "values");
        return new RealmsConfig(
                integer(values, "membershipInviteExpiryMinutes"),
                integer(values, "maximumMembersPerRealm"),
                integer(values, "maximumPendingInvitesPerRealm"),
                integer(values, "presenceValidationIntervalTicks"),
                longValue(values, "denialMessageCooldownMillis"),
                new PresetSelectionConfig(
                        new RealmPresetId(required(values, "defaultPreset")),
                        bool(values, "allowPlayerPresetSelection"),
                        parsePresetIds(required(values, "allowedPresets"))),
                bool(values, "allowExternalPresets"),
                ImportPolicy.valueOf(required(values, "schematicImportPolicy").trim().toUpperCase(Locale.ROOT)),
                new WildsConfig(
                        bool(values, "wilds.enabled"),
                        new WildsProfileId(required(values, "wilds.generationProfile")),
                        bool(values, "wilds.rotateSeedOnReset"),
                        WildsEntryMode.valueOf(required(values, "wilds.entryMode").trim().toUpperCase(Locale.ROOT)),
                        integer(values, "wilds.spawnProtectionRadius"),
                        new WildsRtpConfig(
                                integer(values, "wilds.rtp.minimumRadius"),
                                integer(values, "wilds.rtp.maximumRadius"),
                                integer(values, "wilds.rtp.maximumAttempts"),
                                integer(values, "wilds.rtp.maximumChunksGeneratedPerRequest"),
                                Duration.ofSeconds(longValue(values, "wilds.rtp.cooldownSeconds")),
                                Duration.ofSeconds(longValue(values, "wilds.rtp.timeoutSeconds")),
                                bool(values, "wilds.rtp.avoidFluids"),
                                bool(values, "wilds.rtp.avoidLeaves"),
                                bool(values, "wilds.rtp.avoidPowderSnow")),
                        bool(values, "wilds.schedule.enabled"),
                        Duration.ofHours(longValue(values, "wilds.schedule.intervalHours")),
                        parseLongs(required(values, "wilds.schedule.warningTimesSeconds")),
                        bool(values, "wilds.reset.shutdownWhenPrepared"),
                        integer(values, "wilds.reset.backupRetentionCount"),
                        bool(values, "wilds.reset.deleteOldBackupsAfterVerification")));
    }

    public static Properties defaultProperties() {
        return DEFAULTS.toProperties();
    }

    public Properties toProperties() {
        Properties values = new Properties();
        values.setProperty("membershipInviteExpiryMinutes", Integer.toString(membershipInviteExpiryMinutes));
        values.setProperty("maximumMembersPerRealm", Integer.toString(maximumMembersPerRealm));
        values.setProperty("maximumPendingInvitesPerRealm", Integer.toString(maximumPendingInvitesPerRealm));
        values.setProperty("presenceValidationIntervalTicks", Integer.toString(presenceValidationIntervalTicks));
        values.setProperty("denialMessageCooldownMillis", Long.toString(denialMessageCooldownMillis));
        values.setProperty("defaultPreset", presetSelection.defaultPreset().value());
        values.setProperty("allowPlayerPresetSelection", Boolean.toString(presetSelection.allowPlayerSelection()));
        values.setProperty("allowedPresets", presetSelection.allowedPresets().stream()
                .map(RealmPresetId::value).sorted().collect(Collectors.joining(",")));
        values.setProperty("allowExternalPresets", Boolean.toString(allowExternalPresets));
        values.setProperty("schematicImportPolicy", schematicImportPolicy.name());
        values.setProperty("wilds.enabled", Boolean.toString(wilds.enabled()));
        values.setProperty("wilds.generationProfile", wilds.generationProfile().value());
        values.setProperty("wilds.rotateSeedOnReset", Boolean.toString(wilds.rotateSeedOnReset()));
        values.setProperty("wilds.entryMode", wilds.entryMode().name());
        values.setProperty("wilds.spawnProtectionRadius", Integer.toString(wilds.spawnProtectionRadius()));
        values.setProperty("wilds.rtp.minimumRadius", Integer.toString(wilds.rtp().minimumRadius()));
        values.setProperty("wilds.rtp.maximumRadius", Integer.toString(wilds.rtp().maximumRadius()));
        values.setProperty("wilds.rtp.maximumAttempts", Integer.toString(wilds.rtp().maximumAttempts()));
        values.setProperty("wilds.rtp.maximumChunksGeneratedPerRequest",
                Integer.toString(wilds.rtp().maximumChunksGeneratedPerRequest()));
        values.setProperty("wilds.rtp.cooldownSeconds", Long.toString(wilds.rtp().cooldown().toSeconds()));
        values.setProperty("wilds.rtp.timeoutSeconds", Long.toString(wilds.rtp().timeout().toSeconds()));
        values.setProperty("wilds.rtp.avoidFluids", Boolean.toString(wilds.rtp().avoidFluids()));
        values.setProperty("wilds.rtp.avoidLeaves", Boolean.toString(wilds.rtp().avoidLeaves()));
        values.setProperty("wilds.rtp.avoidPowderSnow", Boolean.toString(wilds.rtp().avoidPowderSnow()));
        values.setProperty("wilds.schedule.enabled", Boolean.toString(wilds.scheduleEnabled()));
        values.setProperty("wilds.schedule.intervalHours", Long.toString(wilds.scheduleInterval().toHours()));
        values.setProperty("wilds.schedule.warningTimesSeconds", wilds.warningTimesSeconds().stream()
                .map(String::valueOf).collect(Collectors.joining(",")));
        values.setProperty("wilds.reset.shutdownWhenPrepared", Boolean.toString(wilds.shutdownWhenPrepared()));
        values.setProperty("wilds.reset.backupRetentionCount", Integer.toString(wilds.backupRetentionCount()));
        values.setProperty("wilds.reset.deleteOldBackupsAfterVerification",
                Boolean.toString(wilds.deleteOldBackupsAfterVerification()));
        return values;
    }

    private static Set<RealmPresetId> parsePresetIds(String value) {
        if (value.isBlank()) return Set.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(RealmPresetId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static int integer(Properties values, String key) {
        return Integer.parseInt(required(values, key));
    }

    private static long longValue(Properties values, String key) {
        return Long.parseLong(required(values, key));
    }

    private static boolean bool(Properties values, String key) {
        String value = required(values, key);
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static List<Long> parseLongs(String value) {
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty())
                .map(Long::parseLong).toList();
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null) throw new IllegalArgumentException("missing configuration key " + key);
        return value;
    }

    private static WildsConfig defaultWilds() {
        return new WildsConfig(true, new WildsProfileId("paradigm_realms:overworld_like"), true,
                WildsEntryMode.RANDOM, 16,
                new WildsRtpConfig(1000, 10000, 32, 8, Duration.ofSeconds(300), Duration.ofSeconds(15),
                        true, true, true),
                false, Duration.ofHours(336), List.of(86400L, 3600L, 600L, 300L, 60L, 30L, 10L),
                false, 2, true);
    }

    private static void range(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
