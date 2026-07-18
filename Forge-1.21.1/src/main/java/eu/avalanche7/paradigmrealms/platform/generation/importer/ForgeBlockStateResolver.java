package eu.avalanche7.paradigmrealms.platform.generation.importer;

import java.util.Map;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockStateResolver;
import eu.avalanche7.paradigmrealms.platform.ForgeLoaderServices;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

public final class ForgeBlockStateResolver implements SymbolicBlockStateResolver<BlockState> {
    @Override public Resolution<BlockState> resolve(SymbolicBlockState symbolic) {
        Identifier id = Identifier.tryParse(symbolic.blockId());
        if (id == null) return Resolution.failure("invalid block identifier " + symbolic.blockId());
        if (!Registries.BLOCK.containsId(id)) {
            if (!id.getNamespace().equals("minecraft") && !ForgeLoaderServices.getInstance().isModLoaded(id.getNamespace())) {
                return Resolution.failure("missing mod namespace " + id.getNamespace() + " for block " + id);
            }
            return Resolution.failure("unknown registry block " + id);
        }
        BlockState state = Registries.BLOCK.get(id).getDefaultState();
        try {
            for (Map.Entry<String, String> entry : symbolic.properties().entrySet()) {
                Property<?> property = state.getBlock().getStateManager().getProperty(entry.getKey());
                if (property == null) return Resolution.failure("unknown property " + id + "[" + entry.getKey() + "]");
                state = apply(state, property, entry.getValue());
            }
        } catch (IllegalArgumentException exception) { return Resolution.failure(exception.getMessage()); }
        return Resolution.success(state, id.getNamespace().equals("minecraft") ? Optional.empty() : Optional.of(id.getNamespace()));
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
        T parsed = property.parse(value).orElseThrow(() -> new IllegalArgumentException(
                "invalid value " + value + " for property " + property.getName()));
        return state.with(property, parsed);
    }
}
