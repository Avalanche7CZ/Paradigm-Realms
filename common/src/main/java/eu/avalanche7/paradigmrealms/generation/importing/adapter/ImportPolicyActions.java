package eu.avalanche7.paradigmrealms.generation.importing.adapter;

import java.util.List;

import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;

final class ImportPolicyActions {
    private ImportPolicyActions() {}

    static void entities(ImportPolicy policy, int count, String source, List<String> warnings) {
        if (count < 1) return;
        if (policy == ImportPolicy.STRICT) throw new IllegalArgumentException("entities are not allowed");
        warnings.add("SANITIZE: stripped " + count + " entit" + (count == 1 ? "y" : "ies") + " from " + source);
    }

    static void outOfBoundsBlockEntities(ImportPolicy policy, int count, String source, List<String> warnings) {
        if (count < 1) return;
        if (policy == ImportPolicy.STRICT) throw new IllegalArgumentException("block entity lies outside " + source);
        warnings.add("SANITIZE: discarded " + count + " block entit" + (count == 1 ? "y" : "ies")
                + " outside " + source);
    }
}
