package eu.avalanche7.paradigmrealms.platform.integration;

import java.lang.reflect.InvocationTargetException;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.platform.FabricRealmRuntime;
import eu.avalanche7.paradigmrealms.platform.message.MessageRouter;
import eu.avalanche7.paradigmrealms.platform.permission.FabricPermissionGate;
import net.fabricmc.loader.api.FabricLoader;

public final class OptionalIntegrationBootstrap {
    private static final String ADAPTER =
            "eu.avalanche7.paradigmrealms.platform.integration.paradigm.ParadigmCompanionIntegration";
    private static volatile String status = "not started";

    private OptionalIntegrationBootstrap() {}

    public static AutoCloseable startIfPresent(
            FabricRealmRuntime runtime, FabricPermissionGate permissions, MessageRouter messages) {
        if (!FabricLoader.getInstance().isModLoaded("paradigm")) {
            status = "absent";
            ParadigmRealms.LOGGER.info("Paradigm Companion integration inactive: Paradigm is not installed");
            return () -> status = "stopped";
        }
        try {
            Class<?> adapter = Class.forName(ADAPTER, true, OptionalIntegrationBootstrap.class.getClassLoader());
            Object instance = adapter
                    .getConstructor(FabricRealmRuntime.class, FabricPermissionGate.class, MessageRouter.class)
                    .newInstance(runtime, permissions, messages);
            status = "active";
            AutoCloseable integration = (AutoCloseable) instance;
            return () -> {
                integration.close();
                status = "stopped";
            };
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                | IllegalAccessException | InvocationTargetException | ClassCastException exception) {
            ParadigmRealms.LOGGER.warn(
                    "Paradigm Companion integration unavailable; using native fallbacks: {}",
                    exception.getClass().getSimpleName());
            status = "unavailable";
            return () -> status = "stopped";
        }
    }

    public static String status() {
        return status;
    }
}
