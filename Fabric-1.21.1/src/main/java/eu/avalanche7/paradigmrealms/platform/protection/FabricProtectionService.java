package eu.avalanche7.paradigmrealms.platform.protection;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import eu.avalanche7.paradigmrealms.protection.ProtectionDecision;
import eu.avalanche7.paradigmrealms.protection.ProtectionPolicyService;
import eu.avalanche7.paradigmrealms.protection.ProtectionRequest;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.RealmRegionIndex;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import eu.avalanche7.paradigmrealms.platform.wilds.FabricWildsService;

public final class FabricProtectionService {
    private final AtomicReference<RealmRegionIndex> index = new AtomicReference<>(RealmRegionIndex.empty());
    private final ProtectionPolicyService policy;
    private final RealmSessionBypass bypass;
    private final long denialCooldownMillis;
    private final Map<UUID, Long> lastDenial = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBypassLog = new ConcurrentHashMap<>();
    private volatile FabricWildsService wilds;

    public FabricProtectionService(
            RealmSessionBypass bypass,
            long denialCooldownMillis,
            eu.avalanche7.paradigmrealms.config.RealmSettingsPolicy settingsPolicy) {
        this.bypass = bypass;
        this.denialCooldownMillis = denialCooldownMillis;
        this.policy = new ProtectionPolicyService(settingsPolicy);
    }

    public void replaceIndex(RealmRegionIndex replacement) {
        index.set(replacement);
    }

    public RealmRegionIndex index() {
        return index.get();
    }

    public boolean bypassEnabled(UUID player) {
        return bypass.enabled(player);
    }

    public void installWilds(FabricWildsService service) {
        this.wilds = java.util.Objects.requireNonNull(service, "service");
    }

    public ProtectionDecision evaluate(
            ServerPlayerEntity player, World world, BlockPos target, ProtectionAction action) {
        return evaluate(Optional.of(player.getUuid()), Optional.empty(), world, target, action,
                bypass.enabled(player.getUuid()));
    }

    public ProtectionDecision evaluate(
            Optional<UUID> actor,
            Optional<UUID> responsible,
            World world,
            BlockPos target,
            ProtectionAction action,
            boolean bypassActive) {
        ProtectionDecision decision = policy.evaluate(index.get(), new ProtectionRequest(
                actor,
                DimensionId.parse(world.getRegistryKey().getValue().toString()),
                coordinate(target),
                action,
                bypassActive,
                responsible));
        if (decision.adminBypassUsed()) {
            actor.ifPresent(uuid -> logBypass(uuid, action, target));
        }
        return decision;
    }

    public boolean allowOrNotify(
            ServerPlayerEntity player, World world, BlockPos target, ProtectionAction action) {
        FabricWildsService wildsService = wilds;
        if (wildsService != null && !wildsService.mutationAllowed(player, world, target)) {
            notifyWildsDenied(player);
            return false;
        }
        ProtectionDecision decision = evaluate(player, world, target, action);
        if (!decision.allowed()) {
            notifyDenied(player, decision);
        }
        return decision.allowed();
    }

    private void notifyWildsDenied(ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        Long previous = lastDenial.put(player.getUuid(), now);
        if (previous == null || now - previous >= denialCooldownMillis) {
            player.sendMessage(Text.literal("That action is blocked by Wilds lifecycle or spawn protection."), true);
        }
    }

    public boolean allowsEnvironmental(BlockPos source, BlockPos target) {
        return policy.allowsEnvironmentalMutation(index.get(), coordinate(source), coordinate(target));
    }

    public boolean allowsExplosion(BlockPos origin, BlockPos target) {
        return policy.explosionsAllowed(index.get(), coordinate(origin))
                && policy.allowsEnvironmentalMutation(index.get(), coordinate(origin), coordinate(target));
    }

    public boolean allowDamage(Entity target, DamageSource source) {
        Optional<UUID> responsible = responsiblePlayer(source);
        if (responsible.isEmpty()) {
            return true;
        }
        ServerPlayerEntity online = target.getServer() == null
                ? null : target.getServer().getPlayerManager().getPlayer(responsible.orElseThrow());
        boolean bypassActive = online != null && bypass.enabled(online.getUuid());
        if (target instanceof ServerPlayerEntity && !bypassActive
                && !policy.pvpAllowed(index.get(), coordinate(target.getBlockPos()))) {
            if (online != null) online.sendMessage(Text.literal("PvP is disabled in this realm."), true);
            return false;
        }
        FabricWildsService wildsService = wilds;
        if (online != null && wildsService != null
                && !wildsService.mutationAllowed(online, target.getWorld(), target.getBlockPos())) {
            notifyWildsDenied(online);
            return false;
        }
        ProtectionDecision decision = evaluate(
                Optional.empty(), responsible, target.getWorld(), target.getBlockPos(),
                ProtectionAction.ENTITY_DAMAGE, bypassActive);
        if (!decision.allowed() && online != null) notifyDenied(online, decision);
        return decision.allowed();
    }

    public boolean environmentalMutationAllowed(World world, BlockPos target) {
        FabricWildsService wildsService = wilds;
        return wildsService == null || wildsService.environmentalMutationAllowed(world, target);
    }

    public boolean mobGriefingAllowed(World world, BlockPos target) {
        if (!world.getRegistryKey().getValue().toString().equals(DimensionId.REALMS.toString())) return true;
        return policy.mobGriefingAllowed(index.get(), coordinate(target));
    }

    public static Optional<UUID> responsiblePlayer(DamageSource source) {
        return responsiblePlayer(source.getAttacker()).or(() -> responsiblePlayer(source.getSource()));
    }

    public static Optional<UUID> responsiblePlayer(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) return Optional.of(player.getUuid());
        if (entity instanceof ProjectileEntity projectile) return responsiblePlayer(projectile.getOwner());
        if (entity instanceof TntEntity tnt) return responsiblePlayer(tnt.getOwner());
        if (entity instanceof TameableEntity tameable) return Optional.ofNullable(tameable.getOwnerUuid());
        return Optional.empty();
    }

    private void notifyDenied(ServerPlayerEntity player, ProtectionDecision decision) {
        long now = System.currentTimeMillis();
        Long previous = lastDenial.put(player.getUuid(), now);
        if (previous == null || now - previous >= denialCooldownMillis) {
            player.sendMessage(Text.literal("That action is protected: " + decision.reason()), true);
        }
    }

    private void logBypass(UUID player, ProtectionAction action, BlockPos target) {
        long now = System.currentTimeMillis();
        Long previous = lastBypassLog.put(player, now);
        if (previous == null || now - previous >= 5_000L) {
            ParadigmRealms.LOGGER.debug("Realm admin bypass used by {} for {} at {}", player, action, target);
        }
    }

    private static BlockCoordinate coordinate(BlockPos position) {
        return new BlockCoordinate(position.getX(), position.getY(), position.getZ());
    }
}
