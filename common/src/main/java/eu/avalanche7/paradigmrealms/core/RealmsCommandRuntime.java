package eu.avalanche7.paradigmrealms.core;

import eu.avalanche7.paradigmrealms.modules.command.RealmAdminCommandRuntime;
import eu.avalanche7.paradigmrealms.modules.command.RealmMembershipCommandRuntime;
import eu.avalanche7.paradigmrealms.modules.command.RealmPlayerCommandRuntime;
import eu.avalanche7.paradigmrealms.modules.command.WildsCommandRuntime;
import eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime;
import eu.avalanche7.paradigmrealms.modules.command.RealmBackupCommandRuntime;

public interface RealmsCommandRuntime extends RealmPlayerCommandRuntime, RealmMembershipCommandRuntime,
        RealmAdminCommandRuntime, RealmOwnerCommandRuntime, RealmBackupCommandRuntime, WildsCommandRuntime {
}
