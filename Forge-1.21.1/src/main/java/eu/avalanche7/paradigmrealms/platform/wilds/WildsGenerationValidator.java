package eu.avalanche7.paradigmrealms.platform.wilds;

import java.util.ArrayList;
import java.util.List;

import eu.avalanche7.paradigmrealms.wilds.WildsProfileId;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

public final class WildsGenerationValidator {
    public List<String> validateProfileResource(MinecraftServer server, WildsProfileId profile) {
        ArrayList<String> issues = new ArrayList<>();
        load(server, profile, issues);
        return List.copyOf(issues);
    }

    public List<String> validate(MinecraftServer server, ServerWorld world, WildsProfileId profile, long expectedSeed) {
        ArrayList<String> issues = new ArrayList<>();
        if (world == null) return List.of("Wilds dimension is not loaded");
        if (!"paradigm_realms:wilds".equals(world.getRegistryKey().getValue().toString())) {
            issues.add("loaded world key is not paradigm_realms:wilds");
        }
        ProfileExpectation expectation = load(server, profile, issues);
        if (!(world.getChunkManager().getChunkGenerator() instanceof NoiseChunkGenerator generator)) {
            issues.add("Wilds generator is not a vanilla-compatible noise generator");
        } else if (expectation != null) {
            String settings = generator.getSettings().getKey().map(RegistryKey::getValue)
                    .map(Object::toString).orElse("unregistered");
            if (!expectation.noiseSettings().equals(settings)) {
                issues.add(profile + " requires " + expectation.noiseSettings() + " noise settings, found " + settings);
            }
        }
        if (world.getSeed() != expectedSeed) {
            issues.add("active Wilds seed does not match the persisted generation seed");
        }
        return List.copyOf(issues);
    }

    private ProfileExpectation load(MinecraftServer server, WildsProfileId profile, List<String> issues) {
        int priorIssueCount = issues.size();
        String[] split = profile.value().split(":", 2);
        Identifier resourceId = Identifier.of(split[0], "wilds_profile/" + split[1] + ".json");
        var resource = server.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            issues.add("generation profile resource is missing: data/" + split[0]
                    + "/wilds_profile/" + split[1] + ".json");
            return null;
        }
        try (var reader = resource.orElseThrow().getReader()) {
            var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            String dimension = required(root, "dimension");
            String generator = required(root, "generator");
            String noiseSettings = required(root, "noiseSettings");
            if (!"paradigm_realms:wilds".equals(dimension)) issues.add("profile targets another dimension: " + dimension);
            if (!"minecraft:noise".equals(generator)) issues.add("profile generator must be minecraft:noise");
            new WildsProfileId(profile.value());
            Identifier.of(noiseSettings);
            return issues.size() == priorIssueCount ? new ProfileExpectation(noiseSettings) : null;
        } catch (Exception exception) {
            issues.add("generation profile " + profile + " is malformed: " + exception.getMessage());
            return null;
        }
    }

    private static String required(com.google.gson.JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing " + key);
        return root.get(key).getAsString();
    }

    private record ProfileExpectation(String noiseSettings) {}
}
