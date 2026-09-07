package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;
import java.util.Map;

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
            new Section("getting_started", "/realm create [preset]"),
            new Section("home_identity", "/realm home | info | name | description"),
            new Section("community", "/realm public | listing | visit"),
            new Section("members_roles", "/realm invite | members | role | managers"),
            new Section("moderation", "/realm kick | ban | unban | bans"),
            new Section("protection", "/realm settings | setting"),
            new Section("lifecycle", "/realm reset | delete"),
            new Section("wilds", "/wilds info | spawn | rtp"),
            new Section("administration", "/realms admin"));

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
        return focused(source, "help.focused.realm", List.of(
                "/realm create [preset]", "/realm home | leave | who", "/realm public | visit",
                "/realm invite | members | role", "/realm settings", "/realm reset | delete | transfer"));
    }

    private static int wildsHelp(CommandSource source) {
        return focused(source, "help.focused.wilds", List.of(
                "/wilds", "/wilds info", "/wilds spawn", "/wilds rtp"));
    }

    private static int adminHelp(CommandSource source) {
        return focused(source, "help.focused.admin", List.of(
                "/realms admin validate", "/realms admin realm archives",
                "/realms admin realm operation", "/realms admin wilds", "/realms admin validate"));
    }

    private static int focused(CommandSource source, String titleKey, List<String> commands) {
        source.sendFeedback(new CommandText(List.of(CommandText.Part.styled(
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text(titleKey), PURPLE, true))), false);
        commands.forEach(command -> source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styledInteractive(command, CYAN, true, false,
                        CommandText.ClickAction.SUGGEST_COMMAND, command + " ",
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                                "help.prepare", Map.of("command", command))))), false));
        return 1;
    }

    private static int version(CommandSource source, PlatformMetadata metadata) {
        source.sendFeedbackKey("help.version.mod", Map.of("version", metadata.modVersion()));
        source.sendFeedbackKey("help.version.platform", Map.of(
                "minecraft", metadata.minecraftVersion(), "loader", metadata.loaderName(),
                "loader_version", metadata.loaderVersion()));
        source.sendFeedbackKey("help.version.schema", Map.of(
                "schema", Integer.toString(SchemaVersion.CURRENT.value()),
                "reset_tool", metadata.resetToolCompatibilityVersion()));
        source.sendFeedbackKey("help.version.integration", Map.of("state", metadata.optionalIntegrationState()));
        return 1;
    }

    private static int show(CommandSource source, PlatformMetadata metadata) {
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("help.separator"), LINE))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.brand_first"), CYAN, true),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.brand_second"), PURPLE, true),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.version_suffix", Map.of("version", metadata.modVersion())), MUTED, false))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.by"), PINK, false),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.author"), WHITE, true),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.heart"), PINK, false))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.minecraft", Map.of("version", metadata.minecraftVersion())), MUTED, false),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.loader", Map.of("loader", metadata.loaderName(),
                                "version", metadata.loaderVersion())), MUTED, false))), false);
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("help.separator"), LINE))), false);
        for (Section section : SECTIONS) {
            source.sendFeedback(new CommandText(List.of(
                    CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "help.bullet"), PINK, true),
                    CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "help.sections." + section.key() + ".title"), WHITE, true),
                    CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "help.section_summary", Map.of("summary",
                                    eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                                            "help.sections." + section.key() + ".summary"))), MUTED, false),
                    CommandText.Part.styledInteractive(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "help.open_button"), CYAN, true, false,
                            CommandText.ClickAction.SUGGEST_COMMAND, section.command() + " ",
                            eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                                    "help.click_prepare", Map.of("command", section.command()))))), false);
        }
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("help.separator"), LINE))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.tip"), GOLD, true),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.click"), WHITE, false),
                CommandText.Part.styledInteractive(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.open_button"), CYAN, true, false,
                        CommandText.ClickAction.SUGGEST_COMMAND, "/realm ",
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("help.start_typing")),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.tab_completion"), WHITE, false))), false);
        source.sendFeedback(new CommandText(List.of(
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.brand"), PURPLE, true),
                CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "help.tagline"), MUTED, false))), false);
        source.sendFeedback(new CommandText(List.of(CommandText.Part.separator(
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("help.separator"), LINE))), false);
        return 1;
    }

    private record Section(String key, String command) {}
}
