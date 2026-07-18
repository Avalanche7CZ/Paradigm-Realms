package eu.avalanche7.paradigmrealms.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLLoader;

/** Small Forge-only facade for configuration paths and loaded-mod metadata. */
public final class ForgeLoaderServices {
    private static final ForgeLoaderServices INSTANCE = new ForgeLoaderServices();

    private ForgeLoaderServices() {}

    public static ForgeLoaderServices getInstance() { return INSTANCE; }

    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }

    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }

    public Optional<Container> getModContainer(String modId) {
        if ("forge".equals(modId)) {
            return Optional.of(new Container(new Metadata("forge", FMLLoader.versionInfo().forgeVersion())));
        }
        return ModList.get().getModContainerById(modId)
                .map(container -> new Container(new Metadata(
                        container.getModInfo().getModId(),
                        container.getModInfo().getVersion().toString())));
    }

    public List<Container> getAllMods() {
        return ModList.get().getMods().stream()
                .map(info -> new Container(new Metadata(info.getModId(), info.getVersion().toString())))
                .toList();
    }

    public record Container(Metadata metadata) {
        public Metadata getMetadata() { return metadata; }
    }

    public record Metadata(String id, String versionValue) {
        public String getId() { return id; }
        public Version getVersion() { return new Version(versionValue); }
    }

    public record Version(String value) {
        public String getFriendlyString() { return value; }
    }
}
