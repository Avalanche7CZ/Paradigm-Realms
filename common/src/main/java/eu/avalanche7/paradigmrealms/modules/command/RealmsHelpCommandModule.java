package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;

import eu.avalanche7.paradigmrealms.platform.PlatformMetadata;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.command.CommandText;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;

public final class RealmsHelpCommandModule {
    private static final int CYAN = 0x22D3EE;
    private static final int PURPLE = 0xA78BFA;
    private static final int PINK = 0xF472B6;
    private static final int WHITE = 0xF8FAFC;
    private static final int MUTED = 0x94A3B8;
    private static final int GOLD = 0xFBBF24;
    private static final int LINE = 0x475569;
    private static final List<Section> SECTIONS = List.of(
            new Section("Getting started", "/realm create [preset]", "Create your personal realm"),
            new Section("Home & identity", "/realm home | info | name | description", "Return home and shape its identity"),
            new Section("Community", "/realm public | listing | visit", "Browse and visit opt-in public realms"),
            new Section("Members & roles", "/realm invite | members | role | managers", "Invite trusted players and managers"),
            new Section("Moderation", "/realm kick | ban | unban | bans", "Control who may enter your realm"),
            new Section("Protection", "/realm settings | setting", "Manage focused realm safety settings"),
            new Section("Lifecycle", "/realm reset | delete", "Safely recreate or archive your realm"),
            new Section("Wilds", "/wilds info | spawn | rtp", "Explore the shared resettable world"),
            new Section("Administration", "/realms admin", "Inspect presets, archives and recovery operations"));

    private RealmsHelpCommandModule() {}

    public static void register(RealmsPlatformAdapter platform) {
        platform.commands().register(platform.commands().literal("realms")
                .executes(context -> show(context.source(), platform.metadata()))
                .then(platform.commands().literal("help")
                        .executes(context -> show(context.source(), platform.metadata())))
                .then(platform.commands().literal("version")
                        .executes(context -> version(context.source(), platform.metadata())))
                .then(platform.commands().literal("admin")
                        .requires(source -> RealmAdminCommandAccess.allowedAny(source, platform.permissions()))
                        .then(platform.commands().literal("help")
                                .executes(context -> adminHelp(context.source())))));
        platform.commands().register(platform.commands().literal("realm")
                .then(platform.commands().literal("help")
                        .executes(context -> realmHelp(context.source()))));
        platform.commands().register(platform.commands().literal("wilds")
                .then(platform.commands().literal("help")
                        .executes(context -> wildsHelp(context.source()))));
    }

    private static int realmHelp(CommandSource source) {
        return focused(source, "REALM HELP", List.of(
                "/realm create [preset]", "/realm home | leave | who", "/realm public | visit",
                "/realm invite | members | role", "/realm settings", "/realm reset | delete | transfer"));
    }

    private static int wildsHelp(CommandSource source) {
        return focused(source, "WILDS HELP", List.of(
                "/wilds", "/wilds info", "/wilds spawn", "/wilds rtp"));
    }

    private static int adminHelp(CommandSource source) {
        return focused(source, "REALMS ADMIN", List.of(
                "/realms admin validate", "/realms admin realm archives",
                "/realms admin realm operation", "/realms admin wilds", "/realms admin validate"));
    }

    private static int focused(CommandSource source, String title, List<String> commands) {
        source.sendFeedback(new CommandText(List.of(CommandText.Part.styled(title, PURPLE, true))), false);
        commands.forEach(command -> source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styledInteractive(command, CYAN, true, false,
                        CommandText.ClickAction.SUGGEST_COMMAND, command + " ", "Prepare " + command))), false));
        return 1;
    }

    private static int version(CommandSource source, PlatformMetadata metadata) {
        source.sendFeedback("Paradigm Realms " + metadata.modVersion());
        source.sendFeedback("Minecraft " + metadata.minecraftVersion() + " | "
                + metadata.loaderName() + " " + metadata.loaderVersion());
        source.sendFeedback("Common schema " + SchemaVersion.CURRENT.value()
                + " | Wilds reset tool compatibility " + metadata.resetToolCompatibilityVersion());
        source.sendFeedback("Optional Paradigm integration: " + metadata.optionalIntegrationState());
        return 1;
    }

    private static int show(CommandSource source, PlatformMetadata metadata) {
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                "────────────────────────────────────────", LINE))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled("PARADIGM ", CYAN, true),
                CommandText.Part.styled("REALMS", PURPLE, true),
                CommandText.Part.styled("  v" + metadata.modVersion(), MUTED, false))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled("by ", PINK, false),
                CommandText.Part.styled("Avalanche7CZ", WHITE, true),
                CommandText.Part.styled("  ♥", PINK, false))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled("Minecraft " + metadata.minecraftVersion(), MUTED, false),
                CommandText.Part.styled("  •  " + metadata.loaderName() + " " + metadata.loaderVersion(), MUTED, false))), false);
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                "────────────────────────────────────────", LINE))), false);
        for (Section section : SECTIONS) {
            source.sendFeedback(new CommandText(List.of(
                    CommandText.Part.styled("◆ ", PINK, true),
                    CommandText.Part.styled(section.title(), WHITE, true),
                    CommandText.Part.styled(" — " + section.summary() + " ", MUTED, false),
                    CommandText.Part.styledInteractive("[open]", CYAN, true, false,
                            CommandText.ClickAction.SUGGEST_COMMAND, section.command() + " ",
                            "Click to prepare " + section.command()))), false);
        }
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                "────────────────────────────────────────", LINE))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled("Tip: ", GOLD, true),
                CommandText.Part.styled("click ", WHITE, false),
                CommandText.Part.styledInteractive("[open]", CYAN, true, false,
                        CommandText.ClickAction.SUGGEST_COMMAND, "/realm ", "Start typing a realm command"),
                CommandText.Part.styled(" or use tab completion.", WHITE, false))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled("Paradigm Realms", PURPLE, true),
                CommandText.Part.styled(" — your world, safely isolated", MUTED, false))), false);
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                "────────────────────────────────────────", LINE))), false);
        return 1;
    }

    private record Section(String title, String command, String summary) {}
}
