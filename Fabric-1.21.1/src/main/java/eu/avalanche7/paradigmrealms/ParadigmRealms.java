package eu.avalanche7.paradigmrealms;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.resource.ResourceType;
import eu.avalanche7.paradigmrealms.platform.FabricRealmRuntime;
import eu.avalanche7.paradigmrealms.platform.FabricRealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.core.CommonRuntime;
import eu.avalanche7.paradigmrealms.core.StartupBanner;
import eu.avalanche7.paradigmrealms.platform.PlatformMetadata;
import eu.avalanche7.paradigmrealms.platform.command.CompatibilityTeleportCommands;
import eu.avalanche7.paradigmrealms.platform.integration.OptionalIntegrationBootstrap;
import eu.avalanche7.paradigmrealms.platform.message.MessageRouter;
import eu.avalanche7.paradigmrealms.platform.permission.FabricPermissionGate;
import eu.avalanche7.paradigmrealms.platform.protection.FabricProtectionEvents;
import eu.avalanche7.paradigmrealms.config.RealmsConfig;
import eu.avalanche7.paradigmrealms.platform.config.RealmsConfigLoader;
import eu.avalanche7.paradigmrealms.platform.generation.FabricPresetCatalogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ParadigmRealms implements DedicatedServerModInitializer {
    public static final String MOD_ID = "paradigm_realms";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static volatile FabricRealmRuntime runtime;
    private static AutoCloseable optionalIntegration = () -> {};
    private static int presenceTicks;

    @Override
    public void onInitializeServer() {
        logStartupBanner();
        RealmsConfig config = RealmsConfigLoader.load();
        FabricPresetCatalogManager presets = new FabricPresetCatalogManager(config);
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new FabricPresetCatalogManager.ReloadListener(presets));
        FabricPermissionGate permissions = new FabricPermissionGate();
        MessageRouter messages = new MessageRouter();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FabricRealmsPlatformAdapter platform = new FabricRealmsPlatformAdapter(
                    dispatcher, permissions, messages);
            CommonRuntime.registerCommands(platform, ParadigmRealms::runtime);
            if (CompatibilityTeleportCommands.enabled()) {
                CompatibilityTeleportCommands.register(dispatcher, permissions);
                LOGGER.warn("Development compatibility teleport commands are ENABLED");
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FabricRealmsPlatformAdapter.loadPlayerCache(server);
            presets.reload(server.getResourceManager());
            runtime = FabricRealmRuntime.start(server, config, presets, permissions, messages);
            FabricProtectionEvents.register(runtime.protection());
            var report = runtime.validate();
            if (report.isValid()) {
                LOGGER.info("Paradigm Realms realm state and preset catalog loaded and validated");
            } else {
                LOGGER.error("Paradigm Realms startup validation failed; administrative inspection remains available");
            }
            report.issues().forEach(issue -> LOGGER.warn("{} {}: {}", issue.code(), issue.path(), issue.message()));
            if (report.isValid()) {
                var recovered = runtime.recoverInterruptedCreations();
                if (!recovered.isEmpty()) {
                    LOGGER.info("Recovered {} interrupted realm creation operation(s)", recovered.size());
                }
                var lifecycleRecovered = runtime.recoverInterruptedLifecycle();
                if (!lifecycleRecovered.isEmpty()) {
                    LOGGER.info("Recovered {} interrupted realm lifecycle operation(s)", lifecycleRecovered.size());
                }
                int expired = runtime.membership().cleanupExpired();
                if (expired > 0) LOGGER.info("Removed {} expired realm invitation(s) during startup", expired);
            }
            optionalIntegration = OptionalIntegrationBootstrap.startIfPresent(runtime, permissions, messages);
            runtime.wilds().start();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FabricRealmRuntime current = runtime;
            if (current != null && ++presenceTicks >= current.config().presenceValidationIntervalTicks()) {
                presenceTicks = 0;
                current.presence().validateAll();
                current.wilds().validateAllPresence();
            }
            if (current != null) current.wilds().tick();
            if (current != null) current.tickLifecycle();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            FabricRealmsPlatformAdapter.loadPlayerCache(server);
            FabricRealmRuntime current = runtime;
            if (current != null) {
                current.presence().validate(handler.player);
                current.wilds().validatePresence(handler.player, true);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            FabricRealmRuntime current = runtime;
            if (current != null) {
                current.bypass().clear(handler.player.getUuid());
                current.presence().disconnect(handler.player.getUuid());
                current.wilds().disconnect(handler.player.getUuid());
            }
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            FabricRealmRuntime current = runtime;
            if (current != null) {
                current.presence().validate(player);
                current.wilds().validatePresence(player, false);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            closeOptionalIntegration();
            FabricRealmRuntime current = runtime;
            if (current != null) current.shutdown();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            runtime = null;
            presenceTicks = 0;
            messages.reset();
            permissions.reset();
            eu.avalanche7.paradigmrealms.platform.wilds.WildsBootContext.clear();
        });
        LOGGER.info("Paradigm Realms server adapter initialized");
    }

    private static void logStartupBanner() {
        FabricLoader loader = FabricLoader.getInstance();
        String modVersion = loader.getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        String loaderVersion = loader.getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        PlatformMetadata metadata = new PlatformMetadata(modVersion,
                SharedConstants.getGameVersion().getName(), "Fabric", loaderVersion);
        StartupBanner.lines(metadata).forEach(LOGGER::info);
    }

    private static FabricRealmRuntime runtime() {
        return runtime;
    }

    private static void closeOptionalIntegration() {
        try {
            optionalIntegration.close();
        } catch (Exception exception) {
            LOGGER.warn("Optional integration shutdown failed: {}", exception.getClass().getSimpleName());
        } finally {
            optionalIntegration = () -> {};
        }
    }
}
