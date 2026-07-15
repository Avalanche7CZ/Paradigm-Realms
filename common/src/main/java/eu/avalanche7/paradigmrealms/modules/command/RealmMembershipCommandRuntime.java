package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.application.RealmMemberInspectionService;
import eu.avalanche7.paradigmrealms.application.RealmVisitService;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.membership.MembershipResult;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;

public interface RealmMembershipCommandRuntime {
    List<Realm> realms();
    List<RealmInvitation> realmInvitations();
    List<RealmInvitation> invitationsFor(UUID player);
    MembershipResult invite(UUID owner, String ownerName, UUID target, String targetName);
    MembershipResult accept(UUID player, UUID owner);
    MembershipResult decline(UUID player, UUID owner);
    MembershipResult remove(UUID owner, UUID target);
    MembershipResult leave(UUID member, UUID owner);
    MembershipResult setAccess(UUID owner, RealmAccessPolicy policy);
    RealmMemberInspectionService.Decision inspectMembers(UUID actor, Optional<UUID> requestedOwner);
    RealmVisitService.Decision evaluateVisit(UUID visitor, UUID owner);
    TeleportResult visit(UUID player, Realm realm);
}
