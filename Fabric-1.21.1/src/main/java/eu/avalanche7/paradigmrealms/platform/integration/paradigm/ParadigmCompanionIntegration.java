package eu.avalanche7.paradigmrealms.platform.integration.paradigm;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import eu.avalanche7.paradigm.api.ApiCapability;
import eu.avalanche7.paradigm.api.ExternalPlaceholderResolver;
import eu.avalanche7.paradigm.api.MessageResult;
import eu.avalanche7.paradigm.api.ParadigmAPI;
import eu.avalanche7.paradigm.api.PermissionContext;
import eu.avalanche7.paradigm.api.PermissionDecision;
import eu.avalanche7.paradigm.api.PermissionNodeDefinition;
import eu.avalanche7.paradigm.api.PlaceholderContext;
import eu.avalanche7.paradigm.api.Registration;
import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.application.RealmInspectionService;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.platform.FabricRealmRuntime;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessenger;
import eu.avalanche7.paradigmrealms.platform.message.MessageRouter;
import eu.avalanche7.paradigmrealms.platform.permission.FabricPermissionGate;
import net.minecraft.server.command.ServerCommandSource;

public final class ParadigmCompanionIntegration implements AutoCloseable {
    private static final String OWNER = "paradigm_realms";
    private static final int REQUIRED_API_VERSION = 1;
    private static final Set<ApiCapability> REQUIRED_CAPABILITIES = EnumSet.of(
            ApiCapability.PERMISSION_CHECKS,
            ApiCapability.PERMISSION_NODE_REGISTRATION,
            ApiCapability.MESSAGE_DELIVERY_AND_FORMATTING,
            ApiCapability.EXTERNAL_PLACEHOLDERS);

    private final FabricPermissionGate permissions;
    private final MessageRouter messages;
    private final List<Registration> registrations = new ArrayList<>();
    private boolean active;

    public ParadigmCompanionIntegration(
            FabricRealmRuntime runtime, FabricPermissionGate permissions, MessageRouter messages) {
        this.permissions = permissions;
        this.messages = messages;
        initialize(runtime.inspection(), Optional.of(runtime));
    }

    ParadigmCompanionIntegration(
            RealmInspectionService inspection, FabricPermissionGate permissions, MessageRouter messages) {
        this.permissions = permissions;
        this.messages = messages;
        initialize(inspection, Optional.empty());
    }

    private void initialize(RealmInspectionService inspection, Optional<FabricRealmRuntime> runtime) {
        if (!ParadigmAPI.isAvailable()) {
            ParadigmRealms.LOGGER.info("Paradigm Companion integration inactive: API is not available");
            return;
        }
        if (ParadigmAPI.apiVersion() < REQUIRED_API_VERSION) {
            ParadigmRealms.LOGGER.info("Paradigm Companion integration inactive: API version {} is too old",
                    ParadigmAPI.apiVersion());
            return;
        }
        if (!ParadigmAPI.capabilities().containsAll(REQUIRED_CAPABILITIES)) {
            ParadigmRealms.LOGGER.info("Paradigm Companion integration inactive: required capabilities are missing");
            return;
        }

        try {
            RealmPermissionNodes.ALL.forEach(node -> registrations.add(
                    ParadigmAPI.permissions().registerPermissionNode(
                            OWNER,
                            new PermissionNodeDefinition(
                                    node.node(), node.description(), node.fallbackOpLevel(),
                                    Optional.of(node.category()), Optional.of(node.featureIdentifier())))));
            registerPlaceholder(inspection, "paradigm_realms_realm_id", realm -> Long.toString(realm.id().value()));
            registerPlaceholder(inspection, "paradigm_realms_realm_owner", realm -> realm.owner().uuid().toString());
            registerPlaceholder(inspection, "paradigm_realms_realm_preset", realm -> realm.preset().value());
            registerPlaceholder(inspection, "paradigm_realms_realm_state", realm -> realm.state().name());
            runtime.ifPresent(value -> {
                registerWildsPlaceholder("paradigm_realms_wilds_state",
                        () -> value.wilds().state().lifecycle().name());
                registerWildsPlaceholder("paradigm_realms_wilds_epoch",
                        () -> Long.toString(value.wilds().state().activeEpoch()));
                registerWildsPlaceholder("paradigm_realms_wilds_entry_open",
                        () -> Boolean.toString(value.wilds().state().lifecycle().entryOpen()));
                registerWildsPlaceholder("paradigm_realms_wilds_profile",
                        () -> value.wilds().state().activeProfile().map(Object::toString).orElse(""));
                registerWildsPlaceholder("paradigm_realms_wilds_next_reset",
                        () -> value.wilds().state().nextScheduledReset().map(Object::toString).orElse(""));
                registerWildsPlaceholder("paradigm_realms_wilds_time_until_reset", () ->
                        value.wilds().state().nextScheduledReset()
                                .map(time -> Long.toString(Math.max(0,
                                        java.time.Duration.between(java.time.Instant.now(), time).toSeconds())))
                                .orElse(""));
            });
        } catch (RuntimeException exception) {
            closeRegistrations();
            ParadigmRealms.LOGGER.info("Paradigm Companion integration inactive: registration failed");
            return;
        }

        if (registrations.stream().anyMatch(registration -> !registration.active())) {
            ParadigmRealms.LOGGER.info("Paradigm Companion integration inactive: registration conflict or rejection");
            closeRegistrations();
            return;
        }

        permissions.install(new ParadigmPermissionService());
        messages.install(new ParadigmMessenger());
        active = true;
        ParadigmRealms.LOGGER.info("Paradigm Companion integration active (Paradigm {}, API {})",
                ParadigmAPI.modVersion(), ParadigmAPI.apiVersion());
    }

    private void registerWildsPlaceholder(String key, java.util.function.Supplier<String> value) {
        registrations.add(ParadigmAPI.placeholders().register(
                OWNER, key, new WildsPlaceholderResolver(value)));
    }

    private void registerPlaceholder(
            RealmInspectionService inspection,
            String key,
            java.util.function.Function<Realm, String> value) {
        registrations.add(ParadigmAPI.placeholders().register(
                OWNER, key, new RealmPlaceholderResolver(inspection::findByOwner, value)));
    }

    @Override
    public void close() {
        if (active) {
            messages.reset();
            permissions.reset();
            active = false;
        }
        closeRegistrations();
    }

    private void closeRegistrations() {
        for (int index = registrations.size() - 1; index >= 0; index--) {
            registrations.get(index).close();
        }
        registrations.clear();
    }

    private static final class ParadigmMessenger implements CommandMessenger {
        private final ParadigmMessageGateway gateway = new ParadigmMessageGateway();

        @Override
        public void send(
                ServerCommandSource source,
                String template,
                Map<String, String> values,
                String nativeFallback) {
            if (source.getPlayer() == null) {
                source.sendFeedback(() -> net.minecraft.text.Text.literal(nativeFallback), false);
                return;
            }
            MessageResult result = gateway.send(
                    source.getPlayer().getUuid(), template, values);
            if (result != MessageResult.SENT) {
                source.sendFeedback(() -> net.minecraft.text.Text.literal(nativeFallback), false);
            }
        }
    }

    public static final class ParadigmPermissionService
            implements eu.avalanche7.paradigmrealms.integration.permission.PermissionService {
        @Override public boolean hasPermission(
                PlayerReference player, String permission, int fallbackOpLevel) {
            PermissionDecision decision = ParadigmAPI.permissions().check(
                    player.uuid(), permission, PermissionContext.GLOBAL);
            return switch (decision) {
                case ALLOW -> true;
                case DENY -> false;
                case UNDEFINED -> player.vanillaPermissionLevel() >= fallbackOpLevel;
            };
        }
    }

    public static final class ParadigmMessageGateway {
        public MessageResult send(UUID playerUuid, String template, Map<String, String> values) {
            return ParadigmAPI.messages().sendPlayerMessage(
                    playerUuid, template, Map.copyOf(values));
        }
    }

    public static final class RealmPlaceholderResolver implements ExternalPlaceholderResolver {
        private final Function<UUID, Optional<Realm>> realmLookup;
        private final Function<Realm, String> value;

        public RealmPlaceholderResolver(
                Function<UUID, Optional<Realm>> realmLookup, Function<Realm, String> value) {
            this.realmLookup = realmLookup;
            this.value = value;
        }

        @Override public String resolve(PlaceholderContext context) {
            try { return context.playerUuid().flatMap(realmLookup).map(value).orElse(""); }
            catch (RuntimeException exception) { return ""; }
        }
    }

    public static final class WildsPlaceholderResolver implements ExternalPlaceholderResolver {
        private final Supplier<String> value;
        public WildsPlaceholderResolver(Supplier<String> value) { this.value = value; }

        @Override public String resolve(PlaceholderContext context) {
            try { return java.util.Objects.requireNonNullElse(value.get(), ""); }
            catch (RuntimeException exception) { return ""; }
        }
    }
}
