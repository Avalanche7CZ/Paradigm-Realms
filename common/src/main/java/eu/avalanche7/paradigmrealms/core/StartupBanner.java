package eu.avalanche7.paradigmrealms.core;

import java.util.List;

import eu.avalanche7.paradigmrealms.platform.PlatformMetadata;

public final class StartupBanner {
    private StartupBanner() {}

    public static List<String> lines(PlatformMetadata metadata) {
        return List.of(
                "============================================================",
                " ____   _    ____      _    ____  ___ ____ __  __",
                "|  _ \\ / \\  |  _ \\    / \\  |  _ \\|_ _/ ___|  \\/  |",
                "| |_) / _ \\ | |_) |  / _ \\ | | | || | |  _| |\\/| |",
                "|  __/ ___ \\|  _ <  / ___ \\| |_| || | |_| | |  | |",
                "|_| /_/   \\_\\_| \\_\\/_/   \\_\\____/|___\\____|_|  |_|",
                " ____  _____    _    _     __  __ ____",
                "|  _ \\| ____|  / \\  | |   |  \\/  / ___|",
                "| |_) |  _|   / _ \\ | |   | |\\/| \\___ \\",
                "|  _ <| |___ / ___ \\| |___| |  | |___) |",
                "|_| \\_\\_____/_/   \\_\\_____|_|  |_|____/",
                "",
                "Paradigm Realms - Version " + metadata.modVersion(),
                "Minecraft: " + metadata.minecraftVersion() + " | Loader: "
                        + metadata.loaderName() + " " + metadata.loaderVersion(),
                "Environment: Dedicated Server | Author: Avalanche7CZ",
                "Discord: https://discord.com/invite/qZDcQdEFqQ",
                "============================================================");
    }
}
