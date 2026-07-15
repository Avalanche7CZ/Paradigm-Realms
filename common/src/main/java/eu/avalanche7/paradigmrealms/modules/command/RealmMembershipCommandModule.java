package eu.avalanche7.paradigmrealms.modules.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.application.RealmMemberInspectionService;
import eu.avalanche7.paradigmrealms.application.RealmVisitService;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.membership.MembershipResult;
import eu.avalanche7.paradigmrealms.membership.MembershipStatus;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.command.CommandArgument;
import eu.avalanche7.paradigmrealms.platform.command.CommandBuilder;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.command.CommandText;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessageService;
import eu.avalanche7.paradigmrealms.platform.player.PlayerDirectory;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;

public final class RealmMembershipCommandModule {
    private RealmMembershipCommandModule() {}

    public static void register(
            RealmsPlatformAdapter platform,
            Supplier<? extends RealmMembershipCommandRuntime> runtime) {
        CommandPlatform commands = platform.commands();
        CommandPermissionGate permissions = platform.permissions();
        CommandMessageService messages = platform.messages();
        PlayerDirectory players = platform.players();

        CommandBuilder root = commands.literal("realm")
                .then(invite(commands, runtime, permissions, messages, players))
                .then(commands.literal("invites")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.INVITES))
                        .executes(context -> listInvites(context.source(), runtime)))
                .then(ownerArgument(commands, "accept", RealmPermissionNodes.ACCEPT, runtime, permissions, players,
                        (source, owner) -> mutateInvitation(source, runtime, messages, players, owner, true)))
                .then(ownerArgument(commands, "decline", RealmPermissionNodes.DECLINE, runtime, permissions, players,
                        (source, owner) -> mutateInvitation(source, runtime, messages, players, owner, false)))
                .then(remove(commands, runtime, permissions, messages, players))
                .then(ownerArgument(commands, "leave", RealmPermissionNodes.LEAVE, runtime, permissions, players,
                        (source, owner) -> leave(source, runtime, messages, players, owner)))
                .then(members(commands, runtime, permissions, players))
                .then(access(commands, runtime, permissions, messages))
                .then(ownerArgument(commands, "visit", RealmPermissionNodes.VISIT, runtime, permissions, players,
                        (source, owner) -> visit(source, runtime, messages, players, owner)));
        commands.register(root);
    }

    private static CommandBuilder invite(
            CommandPlatform commands, Supplier<? extends RealmMembershipCommandRuntime> runtime,
            CommandPermissionGate permissions, CommandMessageService messages, PlayerDirectory players) {
        return commands.literal("invite")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.INVITE))
                .then(commands.argument("player", CommandArgument.word())
                        .suggests((context, input) -> players.onlineNames(context.source()))
                        .executes(context -> invitePlayer(
                                context.source(), runtime, messages, players, context.string("player"))));
    }

    private static CommandBuilder remove(
            CommandPlatform commands, Supplier<? extends RealmMembershipCommandRuntime> runtime,
            CommandPermissionGate permissions, CommandMessageService messages, PlayerDirectory players) {
        return commands.literal("remove")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.REMOVE))
                .then(commands.argument("player", CommandArgument.word())
                        .executes(context -> removeMember(
                                context.source(), runtime, messages, players, context.string("player"))));
    }

    private static CommandBuilder members(
            CommandPlatform commands, Supplier<? extends RealmMembershipCommandRuntime> runtime,
            CommandPermissionGate permissions, PlayerDirectory players) {
        return commands.literal("members")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.MEMBERS))
                .executes(context -> listMembers(context.source(), runtime, players, Optional.empty()))
                .then(commands.argument("owner", CommandArgument.word())
                        .executes(context -> listMembers(
                                context.source(), runtime, players, Optional.of(context.string("owner")))));
    }

    private static CommandBuilder access(
            CommandPlatform commands, Supplier<? extends RealmMembershipCommandRuntime> runtime,
            CommandPermissionGate permissions, CommandMessageService messages) {
        return commands.literal("access")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.ACCESS))
                .then(commands.literal("private").executes(context -> setAccess(
                        context.source(), runtime, messages, RealmAccessPolicy.PRIVATE)))
                .then(commands.literal("public").executes(context -> setAccess(
                        context.source(), runtime, messages, RealmAccessPolicy.PUBLIC_VISIT)));
    }

    private static CommandBuilder ownerArgument(
            CommandPlatform commands, String name, RealmPermissionNode permission,
            Supplier<? extends RealmMembershipCommandRuntime> runtime,
            CommandPermissionGate permissions, PlayerDirectory players, OwnerHandler handler) {
        return commands.literal(name)
                .requires(source -> allowed(source, permissions, permission))
                .then(commands.argument("owner", CommandArgument.word())
                        .suggests((context, input) -> ownerSuggestions(context.source(), runtime, players))
                        .executes(context -> handler.run(context.source(), context.string("owner"))));
    }

    private static int invitePlayer(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            CommandMessageService messages, PlayerDirectory players, String targetName) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference owner = requirePlayer(source);
        if (runtime == null || owner == null) return 0;
        Optional<PlayerIdentity> target = players.onlineExact(source, targetName);
        if (target.isEmpty()) {
            source.sendError("That player must be online for an invitation.");
            return 0;
        }
        PlayerIdentity resolved = target.orElseThrow();
        MembershipResult result = runtime.invite(owner.uuid(), owner.name(), resolved.uuid(), resolved.name());
        if (!result.succeeded()) return membershipError(source, result);
        messages.send(source, "<color:aqua>Invitation {status}</color> for {player}.",
                Map.of("status", result.status().name().toLowerCase(), "player", resolved.name()),
                "Invitation " + result.status().name().toLowerCase() + " for " + resolved.name() + ".");
        players.onlineSource(source, resolved.uuid()).ifPresent(targetSource -> messages.send(targetSource,
                "<color:aqua>{owner}</color> invited you to their realm. Use /realm accept {owner}.",
                Map.of("owner", owner.name()), owner.name() + " invited you to their realm. Use /realm accept "
                        + owner.name() + "."));
        return 1;
    }

    private static int listInvites(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        var invitations = runtime.invitationsFor(player.uuid());
        if (invitations.isEmpty()) {
            source.sendFeedback("You have no pending realm invitations.");
            return 1;
        }
        source.sendFeedback("Pending realm invitations:");
        invitations.forEach(invitation -> {
            String owner = invitation.ownerNameSnapshot();
            source.sendFeedback(CommandText.literal("- " + owner + " ").append(
                    CommandText.Part.interactive("[accept]", CommandText.ClickAction.SUGGEST_COMMAND,
                            "/realm accept " + owner, "Suggest the vanilla server command")), false);
        });
        return invitations.size();
    }

    private static int mutateInvitation(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            CommandMessageService messages, PlayerDirectory players, String ownerName, boolean accept) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        Optional<PlayerIdentity> owner = resolveOwner(source, runtime, players, ownerName);
        if (owner.isEmpty()) {
            source.sendError("Unknown cached realm owner.");
            return 0;
        }
        MembershipResult result = accept
                ? runtime.accept(player.uuid(), owner.orElseThrow().uuid())
                : runtime.decline(player.uuid(), owner.orElseThrow().uuid());
        if (!result.succeeded()) return membershipError(source, result);
        String action = accept ? "accepted" : "declined";
        messages.send(source, "Realm invitation {action}.", Map.of("action", action),
                "Realm invitation " + action + ".");
        return 1;
    }

    private static int removeMember(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            CommandMessageService messages, PlayerDirectory players, String memberName) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference owner = requirePlayer(source);
        if (runtime == null || owner == null) return 0;
        Optional<Realm> realm = runtime.realms().stream()
                .filter(candidate -> candidate.owner().uuid().equals(owner.uuid())).findFirst();
        if (realm.isEmpty()) {
            source.sendError("You do not own a realm.");
            return 0;
        }
        Optional<PlayerIdentity> member = resolveMember(
                source, players, realm.orElseThrow().members(), memberName);
        if (member.isEmpty()) {
            source.sendError("No matching cached realm member.");
            return 0;
        }
        MembershipResult result = runtime.remove(owner.uuid(), member.orElseThrow().uuid());
        if (!result.succeeded()) return membershipError(source, result);
        messages.send(source, "Removed {player} from the realm.",
                Map.of("player", member.orElseThrow().name()),
                "Removed " + member.orElseThrow().name() + " from the realm.");
        return 1;
    }

    private static int leave(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            CommandMessageService messages, PlayerDirectory players, String ownerName) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference member = requirePlayer(source);
        if (runtime == null || member == null) return 0;
        Optional<PlayerIdentity> owner = resolveOwner(source, runtime, players, ownerName);
        if (owner.isEmpty()) {
            source.sendError("Unknown cached realm owner.");
            return 0;
        }
        MembershipResult result = runtime.leave(member.uuid(), owner.orElseThrow().uuid());
        if (!result.succeeded()) return membershipError(source, result);
        messages.send(source, "You left {owner}'s realm.", Map.of("owner", owner.orElseThrow().name()),
                "You left " + owner.orElseThrow().name() + "'s realm.");
        return 1;
    }

    private static int listMembers(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            PlayerDirectory players, Optional<String> requestedOwner) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        Optional<UUID> targetOwner = Optional.empty();
        if (requestedOwner.isPresent()) {
            Optional<PlayerIdentity> owner = resolveOwner(
                    source, runtime, players, requestedOwner.orElseThrow());
            if (owner.isEmpty()) {
                source.sendError("Unknown cached realm owner.");
                return 0;
            }
            targetOwner = Optional.of(owner.orElseThrow().uuid());
        }
        RealmMemberInspectionService.Decision decision = runtime.inspectMembers(player.uuid(), targetOwner);
        if (!decision.allowed()) {
            source.sendError(memberInspectionError(decision.status()));
            return 0;
        }
        Realm realm = decision.realm().orElseThrow();
        String ownerName = players.cached(source, realm.owner().uuid())
                .map(PlayerIdentity::name).orElse("Owner");
        source.sendFeedback("Owner: " + ownerName);
        if (realm.members().isEmpty()) {
            source.sendFeedback("Members: none");
        } else {
            source.sendFeedback("Members:");
            realm.members().forEach(uuid -> source.sendFeedback("- " + players.cached(source, uuid)
                    .map(PlayerIdentity::name).orElse("Unknown cached member")));
        }
        return 1;
    }

    private static int setAccess(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            CommandMessageService messages, RealmAccessPolicy policy) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference owner = requirePlayer(source);
        if (runtime == null || owner == null) return 0;
        MembershipResult result = runtime.setAccess(owner.uuid(), policy);
        if (result.status() == MembershipStatus.NO_CHANGE) {
            source.sendFeedback("Realm access is already " + displayPolicy(policy) + ".");
            return 1;
        }
        if (!result.succeeded()) return membershipError(source, result);
        messages.send(source, "Realm access changed to {access}.",
                Map.of("access", displayPolicy(policy)),
                "Realm access changed to " + displayPolicy(policy) + ".");
        return 1;
    }

    private static int visit(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            CommandMessageService messages, PlayerDirectory players, String ownerName) {
        RealmMembershipCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference visitor = requirePlayer(source);
        if (runtime == null || visitor == null) return 0;
        Optional<PlayerIdentity> owner = resolveOwner(source, runtime, players, ownerName);
        if (owner.isEmpty()) {
            source.sendError("Unknown cached realm owner.");
            return 0;
        }
        RealmVisitService.Decision decision = runtime.evaluateVisit(visitor.uuid(), owner.orElseThrow().uuid());
        if (!decision.allowed()) {
            if (decision.status() == RealmVisitService.Status.REALM_NOT_ACTIVE
                    || decision.status() == RealmVisitService.Status.TARGET_NOT_FOUND) {
                source.sendError("That realm is not active.");
            } else {
                String detail = decision.accessDecision().map(access -> access.reason().toString())
                        .orElse("ACCESS_DENIED");
                source.sendError("You cannot visit that realm: " + detail);
            }
            return 0;
        }
        TeleportResult teleport = runtime.visit(visitor.uuid(), decision.realm().orElseThrow());
        if (teleport != TeleportResult.SUCCESS) {
            source.sendError("Unable to visit realm: " + teleport);
            return 0;
        }
        messages.send(source, "Visiting {owner}'s realm.", Map.of("owner", owner.orElseThrow().name()),
                "Visiting " + owner.orElseThrow().name() + "'s realm.");
        return 1;
    }

    private static List<String> ownerSuggestions(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier,
            PlayerDirectory players) {
        RealmMembershipCommandRuntime runtime = runtimeSupplier.get();
        if (runtime == null) return List.of();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        runtime.realms().forEach(realm -> players.cached(source, realm.owner().uuid())
                .map(PlayerIdentity::name).ifPresent(names::add));
        runtime.realmInvitations().forEach(invitation -> names.add(invitation.ownerNameSnapshot()));
        return List.copyOf(names);
    }

    private static Optional<PlayerIdentity> resolveOwner(
            CommandSource source, RealmMembershipCommandRuntime runtime,
            PlayerDirectory players, String name) {
        Optional<PlayerIdentity> online = players.onlineExact(source, name);
        if (online.isPresent()) return online;
        for (Realm realm : runtime.realms()) {
            Optional<PlayerIdentity> cached = players.cached(source, realm.owner().uuid());
            if (cached.isPresent() && cached.orElseThrow().name().equalsIgnoreCase(name)) return cached;
        }
        return runtime.realmInvitations().stream()
                .filter(invitation -> invitation.ownerNameSnapshot().equalsIgnoreCase(name))
                .findFirst()
                .map(invitation -> new PlayerIdentity(
                        invitation.realmOwnerUuid(), invitation.ownerNameSnapshot()));
    }

    private static Optional<PlayerIdentity> resolveMember(
            CommandSource source, PlayerDirectory players, java.util.Set<UUID> candidates, String name) {
        Optional<PlayerIdentity> online = players.onlineExact(source, name);
        if (online.isPresent() && candidates.contains(online.orElseThrow().uuid())) return online;
        return candidates.stream().map(uuid -> players.cached(source, uuid)).flatMap(Optional::stream)
                .filter(identity -> identity.name().equalsIgnoreCase(name)).findFirst();
    }

    private static String memberInspectionError(RealmMemberInspectionService.Status status) {
        return switch (status) {
            case TARGET_NOT_FOUND -> "Unknown cached realm owner.";
            case NO_REALM -> "You do not own or belong to a realm.";
            case AMBIGUOUS_MEMBERSHIP -> "You belong to multiple realms; use the owner-specific commands.";
            case NOT_AUTHORIZED -> "Realm membership is private.";
            case ALLOWED -> throw new IllegalArgumentException("allowed member inspection is not an error");
        };
    }

    private static int membershipError(CommandSource source, MembershipResult result) {
        source.sendError("Realm membership operation failed: " + result.status());
        return 0;
    }

    private static RealmMembershipCommandRuntime requireRuntime(
            CommandSource source, Supplier<? extends RealmMembershipCommandRuntime> runtimeSupplier) {
        RealmMembershipCommandRuntime runtime = runtimeSupplier.get();
        if (runtime == null) source.sendError("Paradigm Realms has not completed startup.");
        return runtime;
    }

    private static PlayerReference requirePlayer(CommandSource source) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) source.sendError("This command can only be used by a player.");
        return player;
    }

    private static boolean allowed(
            CommandSource source, CommandPermissionGate permissions, RealmPermissionNode node) {
        return permissions.allowed(source, node);
    }

    private static String displayPolicy(RealmAccessPolicy policy) {
        return policy == RealmAccessPolicy.PUBLIC_VISIT ? "public" : "private";
    }

    @FunctionalInterface
    private interface OwnerHandler {
        int run(CommandSource source, String owner);
    }
}
