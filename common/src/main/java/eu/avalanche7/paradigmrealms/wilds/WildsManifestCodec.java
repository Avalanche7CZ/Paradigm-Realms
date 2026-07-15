package eu.avalanche7.paradigmrealms.wilds;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WildsManifestCodec {
    public Map<String, String> encode(WildsResetManifest value) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        out.put("manifestVersion", Integer.toString(value.version()));
        out.put("worldIdentity", value.worldIdentity());
        out.put("dimensionId", value.dimensionId());
        out.put("operationId", value.operationId().toString());
        out.put("sourceEpoch", Long.toString(value.sourceEpoch()));
        out.put("targetEpoch", Long.toString(value.targetEpoch()));
        out.put("sourceSeed", Long.toString(value.sourceSeed()));
        out.put("targetSeed", Long.toString(value.targetSeed()));
        out.put("sourceProfile", value.sourceProfile().value());
        out.put("targetProfile", value.targetProfile().value());
        out.put("expectedRelativeWildsPath", value.expectedRelativeWildsPath());
        out.put("quarantineRelativePath", value.quarantineRelativePath());
        out.put("stage", value.stage().name());
        out.put("createdAt", value.createdAt().toString());
        out.put("updatedAt", value.updatedAt().toString());
        value.failure().ifPresent(failure -> {
            out.put("failureCode", failure.code());
            out.put("failureDetail", failure.detail());
            out.put("failureAt", failure.occurredAt().toString());
        });
        return Map.copyOf(out);
    }

    public WildsResetManifest decode(Map<String, String> value) {
        Optional<WildsFailure> failure = value.containsKey("failureCode")
                ? Optional.of(new WildsFailure(required(value, "failureCode"), required(value, "failureDetail"),
                        Instant.parse(required(value, "failureAt")))) : Optional.empty();
        return new WildsResetManifest(
                Integer.parseInt(required(value, "manifestVersion")), required(value, "worldIdentity"),
                required(value, "dimensionId"), UUID.fromString(required(value, "operationId")),
                Long.parseLong(required(value, "sourceEpoch")), Long.parseLong(required(value, "targetEpoch")),
                Long.parseLong(required(value, "sourceSeed")), Long.parseLong(required(value, "targetSeed")),
                new WildsProfileId(required(value, "sourceProfile")),
                new WildsProfileId(required(value, "targetProfile")),
                required(value, "expectedRelativeWildsPath"), required(value, "quarantineRelativePath"),
                WildsManifestStage.valueOf(required(value, "stage")),
                Instant.parse(required(value, "createdAt")), Instant.parse(required(value, "updatedAt")), failure);
    }

    private static String required(Map<String, String> value, String key) {
        String result = value.get(key);
        if (result == null || result.isBlank()) throw new IllegalArgumentException("manifest missing " + key);
        return result;
    }
}
