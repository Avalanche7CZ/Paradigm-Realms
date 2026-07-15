package eu.avalanche7.paradigmrealms.core;

import java.util.Objects;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.modules.command.RealmPlayerCommandModule;
import eu.avalanche7.paradigmrealms.modules.command.RealmMembershipCommandModule;
import eu.avalanche7.paradigmrealms.modules.command.RealmAdminCommandModule;
import eu.avalanche7.paradigmrealms.modules.command.WildsCommandModule;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;

public final class CommonRuntime {
    private CommonRuntime() {}

    public static void registerCommands(
            RealmsPlatformAdapter platform,
            Supplier<? extends RealmsCommandRuntime> runtime) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(runtime, "runtime");
        RealmPlayerCommandModule.register(platform, runtime);
        RealmMembershipCommandModule.register(platform, runtime);
        WildsCommandModule.register(platform, runtime);
        RealmAdminCommandModule.register(platform, runtime);
    }
}
