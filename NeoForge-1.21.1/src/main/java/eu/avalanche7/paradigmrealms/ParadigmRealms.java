package eu.avalanche7.paradigmrealms;

import eu.avalanche7.paradigmrealms.config.RealmsConfig;
import eu.avalanche7.paradigmrealms.core.CommonRuntime;
import eu.avalanche7.paradigmrealms.core.StartupBanner;
import eu.avalanche7.paradigmrealms.platform.NeoForgeLoaderServices;
import eu.avalanche7.paradigmrealms.platform.NeoForgeRealmRuntime;
import eu.avalanche7.paradigmrealms.platform.NeoForgeRealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.PlatformMetadata;
import eu.avalanche7.paradigmrealms.platform.command.CompatibilityTeleportCommands;
import eu.avalanche7.paradigmrealms.platform.config.RealmsConfigLoader;
import eu.avalanche7.paradigmrealms.platform.generation.NeoForgePresetCatalogManager;
import eu.avalanche7.paradigmrealms.platform.integration.OptionalIntegrationBootstrap;
import eu.avalanche7.paradigmrealms.platform.message.MessageRouter;
import eu.avalanche7.paradigmrealms.platform.permission.NeoForgePermissionGate;
import eu.avalanche7.paradigmrealms.platform.protection.NeoForgeProtectionEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ParadigmRealms.MOD_ID)
public final class ParadigmRealms {
    public static final String MOD_ID = "paradigm_realms";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final RealmsConfig config;
    private final NeoForgePresetCatalogManager presets;
    private final NeoForgePermissionGate permissions = new NeoForgePermissionGate();
    private final MessageRouter messages = new MessageRouter();
    private volatile NeoForgeRealmRuntime runtime;
    private AutoCloseable optionalIntegration = () -> {};
    private int presenceTicks;

    public ParadigmRealms() {
        logStartupBanner();
        config = RealmsConfigLoader.load();
        presets = new NeoForgePresetCatalogManager(config);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Paradigm Realms NeoForge server adapter initialized");
    }

    @SubscribeEvent
    public void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new NeoForgePresetCatalogManager.ReloadListener(presets));
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        NeoForgeRealmsPlatformAdapter platform = new NeoForgeRealmsPlatformAdapter(
                event.getDispatcher(), permissions, messages);
        CommonRuntime.registerCommands(platform, this::runtime);
        if (CompatibilityTeleportCommands.enabled()) {
            CompatibilityTeleportCommands.register(event.getDispatcher(), permissions);
            LOGGER.warn("Development compatibility teleport commands are ENABLED");
        }
    }

    @SubscribeEvent
    public void serverStarted(ServerStartedEvent event) {
        var server = event.getServer();
        NeoForgeRealmsPlatformAdapter.loadPlayerCache(server);
        presets.reload(server.getResourceManager());
        runtime = NeoForgeRealmRuntime.start(server, config, presets, permissions, messages);
        NeoForgeProtectionEvents.register(runtime.protection());
        var report = runtime.validate();
        if (report.isValid()) {
            LOGGER.info("Paradigm Realms realm state and preset catalog loaded and validated");
        } else {
            LOGGER.error("Paradigm Realms startup validation failed; administrative inspection remains available");
        }
        report.issues().forEach(issue -> LOGGER.warn("{} {}: {}", issue.code(), issue.path(), issue.message()));
        if (report.isValid()) {
            var recovered = runtime.recoverInterruptedCreations();
            if (!recovered.isEmpty()) LOGGER.info("Recovered {} interrupted realm creation operation(s)", recovered.size());
            var lifecycleRecovered = runtime.recoverInterruptedLifecycle();
            if (!lifecycleRecovered.isEmpty()) {
                LOGGER.info("Recovered {} interrupted realm lifecycle operation(s)", lifecycleRecovered.size());
            }
            int expired = runtime.membership().cleanupExpired();
            if (expired > 0) LOGGER.info("Removed {} expired realm invitation(s) during startup", expired);
        }
        optionalIntegration = OptionalIntegrationBootstrap.startIfPresent(runtime, permissions, messages);
        runtime.wilds().start();
    }

    @SubscribeEvent
    public void serverTick(ServerTickEvent.Post event) {
        NeoForgeRealmRuntime current = runtime;
        if (current != null && ++presenceTicks >= current.config().presenceValidationIntervalTicks()) {
            presenceTicks = 0;
            current.presence().validateAll();
            current.wilds().validateAllPresence();
        }
        if (current != null) current.wilds().tick();
        if (current != null) current.tickBackups();
        if (current != null) current.tickLifecycle();
    }

    @SubscribeEvent
    public void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        NeoForgeRealmsPlatformAdapter.loadPlayerCache(player.getServer());
        NeoForgeRealmRuntime current = runtime;
        if (current != null) {
            current.presence().validate(player);
            current.wilds().validatePresence(player, true);
        }
    }

    @SubscribeEvent
    public void playerDisconnected(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        NeoForgeRealmRuntime current = runtime;
        if (current != null) {
            current.bypass().clear(player.getUuid());
            current.presence().disconnect(player.getUuid());
            current.wilds().disconnect(player.getUuid());
        }
    }

    @SubscribeEvent
    public void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        NeoForgeRealmRuntime current = runtime;
        if (current != null) {
            current.presence().validate(player);
            current.wilds().validatePresence(player, false);
        }
    }

    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent event) {
        closeOptionalIntegration();
        NeoForgeRealmRuntime current = runtime;
        if (current != null) current.shutdown();
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppedEvent event) {
        runtime = null;
        presenceTicks = 0;
        messages.reset();
        permissions.reset();
        eu.avalanche7.paradigmrealms.platform.wilds.WildsBootContext.clear();
    }

    private void logStartupBanner() {
        NeoForgeLoaderServices loader = NeoForgeLoaderServices.getInstance();
        PlatformMetadata metadata = new PlatformMetadata(
                loader.getModContainer(MOD_ID)
                        .map(container -> container.getMetadata().getVersion().getFriendlyString())
                        .orElse(rootVersion()),
                SharedConstants.getGameVersion().getName(),
                "NeoForge",
                loader.getModContainer("neoforge")
                        .map(container -> container.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"));
        StartupBanner.lines(metadata).forEach(LOGGER::info);
    }

    private NeoForgeRealmRuntime runtime() { return runtime; }

    private static String rootVersion() {
        Package source = ParadigmRealms.class.getPackage();
        return source == null || source.getImplementationVersion() == null
                ? "unknown" : source.getImplementationVersion();
    }

    private void closeOptionalIntegration() {
        try {
            optionalIntegration.close();
        } catch (Exception exception) {
            LOGGER.warn("Optional integration shutdown failed: {}", exception.getClass().getSimpleName());
        } finally {
            optionalIntegration = () -> {};
        }
    }
}
