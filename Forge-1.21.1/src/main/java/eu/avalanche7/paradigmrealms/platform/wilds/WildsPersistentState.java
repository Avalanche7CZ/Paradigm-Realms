package eu.avalanche7.paradigmrealms.platform.wilds;

import java.util.Optional;

import eu.avalanche7.paradigmrealms.wilds.WildsState;
import eu.avalanche7.paradigmrealms.wilds.WildsStateNbtCodec;
import eu.avalanche7.paradigmrealms.platform.persistence.MinecraftNbtAdapter;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

public final class WildsPersistentState extends PersistentState {
    public static final Type<WildsPersistentState> TYPE = new Type<>(
            WildsPersistentState::new, WildsPersistentState::fromNbt,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private WildsState state;
    private final WildsStateNbtCodec codec = new WildsStateNbtCodec();
    private final MinecraftNbtAdapter nbt = new MinecraftNbtAdapter();
    private boolean writable;
    private String loadError;
    private NbtCompound preservedMalformed;

    public WildsPersistentState() {
        this(WildsState.disabled(), true, null, null);
    }

    private WildsPersistentState(WildsState state, boolean writable, String loadError, NbtCompound malformed) {
        this.state = state;
        this.writable = writable;
        this.loadError = loadError;
        this.preservedMalformed = malformed;
    }

    public static WildsPersistentState fromNbt(NbtCompound root, RegistryWrapper.WrapperLookup registries) {
        try {
            MinecraftNbtAdapter nbt = new MinecraftNbtAdapter();
            return new WildsPersistentState(new WildsStateNbtCodec().decode(nbt.fromMinecraft(root)),
                    true, null, null);
        } catch (RuntimeException exception) {
            return new WildsPersistentState(WildsState.disabled(), false,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage(), root.copy());
        }
    }

    public WildsState state() { return state; }
    public boolean writable() { return writable; }
    public Optional<String> loadError() { return Optional.ofNullable(loadError); }

    public boolean replace(WildsState replacement) {
        if (!writable) throw new IllegalStateException("malformed Wilds state is read-only: " + loadError);
        if (state.equals(replacement)) return false;
        state = replacement;
        markDirty();
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound root, RegistryWrapper.WrapperLookup registries) {
        if (preservedMalformed != null) return root.copyFrom(preservedMalformed);
        return root.copyFrom(nbt.toMinecraft(codec.encode(state)));
    }
}
