# Paradigm Realms

> **A permanent little home for every player, and a Wilds world that can begin again.**

Paradigm Realms is a fully server-side mod that gives players their own protected personal realms, created from configurable presets inside one shared void dimension.

Alongside them lives the **Wilds**: a shared exploration world made for resources, structures, mobs, adventures, and safe periodic regeneration.

**Players do not need Paradigm Realms installed on their client.**

---

## ✨ Features

- 🌍 Permanent isolated personal realms
- 🏡 Configurable starter islands and realm presets
- 👑 Owners, managers, members, visitors, and bans
- 💌 Persistent invitations, including cached offline players
- 🌐 Public realm directory with clickable visits
- ✏️ Realm names and descriptions
- ⚙️ Per-realm gameplay and visitor settings
- ♻️ Safe realm reset and logical deletion
- 🔁 Two-player ownership transfers
- 📦 Schematic and structure imports
- 🛡️ Built-in server-authoritative protection
- 🌲 Shared exploration-focused Wilds world
- 🎲 Bounded safe random teleportation
- 🔄 Recoverable offline Wilds reset workflow
- 💾 Persistent lifecycle and recovery data
- 🧰 Validation, repair, audit, and support tools
- 🔌 Optional Paradigm integration
- 🖥️ Fully server-side

---

## 🏡 Your own little realm

Every player can create one permanent realm — their own tiny corner of the void for building, storage, farms, machines, or anything else they would rather not lose.

Realms can be created from built-in or server-defined presets and include:

- A persistent home and custom spawn
- Custom name and description
- Public or private access
- An optional public directory listing
- Owners, managers, members, and visitors
- Persistent invitations
- Offline player identity support
- Kicks and persistent realm bans
- Safe ownership transfers
- Protection from unauthorized interaction

Keep it private, build together with friends, or open it for visitors.

**Your realm, your rules :3**

### Region-aligned allocation

Every newly created realm uses the persisted allocation profile `region-aligned-32-v1`:

- Allocation cell: **32×32 chunks** (**512×512 blocks**)
- Buildable area: **16×16 chunks**, centered in the cell
- Guard area: **8 chunks on every side**
- Storage ownership: **one complete Minecraft Anvil region per realm**

The square spiral assigns a different region coordinate to every realm, so neighboring realms never share terrain,
entity, or POI region files. Protection, preset placement, spawn validation, and teleports use each realm's persisted
profile and bounds.

This release candidate intentionally does not migrate the former 16×16 test allocation. For a pre-release server that does not
need its test realms, the recommended upgrade is a clean reset:

```text
stop server
remove the Realms dimension and Realms persistent state
start server
create realms again
```

Paradigm Realms never performs this deletion automatically.

---

## 👥 Members and managers

Realm owners can invite trusted players and assign them roles.

### Members

Members can enter and build inside the realm according to its configured settings.

### Managers

Managers can help operate a community realm by managing members, visitors, access, bans, and other permitted owner controls.

The owner always remains the final authority and controls destructive operations such as realm resets, deletion, and ownership transfer.

---

## 🌐 Public realm directory

Public realms can optionally appear in a server-wide directory.

Players can browse listed realms and visit them through clickable server messages:

```text
/realm public
/realm public <page>
```

A realm only appears when it is both:

- Publicly accessible
- Explicitly listed by its owner or manager

Private realms are never exposed through the directory.

---

## ⚙️ Realm settings

Owners can control a focused set of gameplay rules for their realm:

- PvP
- Explosions
- Mob griefing
- Visitor interaction
- Visitor container access

Servers may define defaults, lock individual settings, or prevent players from changing settings that should remain globally enforced.

Realm settings never weaken boundary protection or allow writes into another player's realm.

---

## ♻️ Reset, archive, and begin again

Made a mess? Picked the wrong island? Changed your mind?

A realm can be safely recreated from another preset:

```text
/realm reset [preset]
```

Resetting does **not** destructively wipe the active cell in place.

Instead, Paradigm Realms:

1. Creates a replacement realm in a fresh protected allocation
2. Generates and verifies the requested preset
3. Keeps the old realm active if generation fails
4. Switches ownership only after the replacement is ready
5. Archives the old realm safely

Realm metadata such as its name, members, managers, bans, access policy, and settings can remain attached to the replacement.

Players may also logically delete their realm:

```text
/realm delete
```

Deletion archives the realm and frees the owner to create another one.

Archived cells are **not physically erased or reused**. Their blocks and allocation remain protected, and administrators can inspect or restore them when safe.

No scary live chunk deletion nonsense here.

---

## 🎨 Realm presets

New realms are generated from configurable server-defined presets.

Servers can provide:

- Starter islands
- Flat building plots
- Empty platforms
- Custom bases
- Modpack-specific starting areas
- Imported structures

Players may either receive the server's default preset or select from an allowed list.

Existing realms keep their original preset identity and remain usable even when the source preset is later removed.

---

## 📦 Schematic imports

Administrators can import existing builds directly as realm presets.

Supported formats include:

- Sponge `.schem` v1, v2, and v3
- Legacy WorldEdit/MCEdit `.schematic`
- Litematica `.litematic`
- Vanilla structure `.nbt`

Imports are parsed through a loader-neutral format system and validated before publication.

Validation includes:

- Realm bounds
- Region overlaps
- Spawn placement
- Registry and mod namespace checks
- Block-state validation
- Block entity sanitation
- Entity and scheduled-tick handling
- Forbidden or unsafe block detection
- Content fingerprints

Unknown blocks are rejected instead of silently being replaced with air.

Imported templates are compiled before realm creation, so `/realm create` never parses arbitrary schematic files in the middle of gameplay.

---

## 🛡️ Built-in protection

Realm boundaries and access rules are enforced directly by the server.

Protection covers common server-authoritative interactions such as:

- Breaking and placing blocks
- Item-based placement
- Containers
- Buckets and fluids
- Doors, buttons, and other interactions
- Entity interaction and damage
- Item and experience pickup
- Farmland trampling
- PvP
- Mob griefing
- Explosions
- Pistons crossing realm boundaries
- Unauthorized entry
- Banned-player entry
- Foreign teleports and reconnects

Guard space separates neighboring build regions and prevents players from interacting across realm boundaries.

Administrators may enable an explicit session-only bypass for maintenance. Lifecycle safety still takes priority during resets and archival operations.

---

## 🌲 The Wilds

The Wilds are a shared world made for exploration, resources, structures, mobs, and temporary adventures.

Players can enter normally or use bounded safe random teleportation:

```text
/wilds
/wilds spawn
/wilds rtp
/wilds info
```

The Wilds support:

- Safe spawn validation
- Random teleport cooldowns
- Bounded chunk generation
- Surface and collision checks
- World-border awareness
- Generation epochs
- Scheduled reset warnings
- Player evacuation
- Offline-player generation tracking
- Quarantined backups
- Restart and failure recovery
- Administrative validation

Unlike personal realms, the Wilds can be regenerated without touching permanent player bases.

Build forever at home, then go cause temporary problems in the Wilds responsibly :3

---

## 🔄 Safe Wilds resets

Paradigm Realms intentionally does **not** try to delete a loaded world while the server is running.

A Wilds reset follows a controlled workflow:

```text
Reset scheduled
→ warnings are sent
→ new entry is blocked
→ players are evacuated
→ the world is saved
→ a durable reset manifest is written
→ the server is stopped
→ the old Wilds is moved into quarantine
→ a new generation is created
→ the new world is verified
→ entry opens again
```

The actual filesystem replacement is performed while the old world is offline using the bundled Wilds reset tool.

This protects:

- Personal realms
- The Overworld
- The Nether
- The End
- Player inventories
- Realm ownership and membership data
- Imported preset bindings

The previous Wilds generation is retained as a quarantined backup until the new generation has been verified.

---

## 🧰 Administration tools

Paradigm Realms includes tools for operating and recovering a real server.

Administrators can:

- Inspect realms and owners
- Validate persistent state
- Preview and rebuild derived indexes
- Clean stale sessions and expired operations
- Inspect and retry realm lifecycle operations
- List and restore archived realms
- Validate and reload configuration
- Import, inspect, reimport, and remove presets
- Inspect Wilds lifecycle and backups
- Schedule and recover Wilds resets
- Export a bounded support bundle
- Review structured operational audit logs

Useful starting commands:

```text
/realms
/realms help
/realms version
/realms admin help
/realms admin validate
/realms admin support export
```

Validation never silently changes authoritative data. Repair operations remain explicit and separate.

Realm backups select their strategy from the persisted allocation. `region-aligned-32-v1` uses `REGION_COPY` and
stores the realm's dedicated terrain, entity, and POI `.mca` files plus referenced external `.mcc` payloads.
`custom-v1` allocations retain the selective `CHUNK_EXTRACT` path. Exact offline region restore quarantines and
replaces only that realm's dedicated files while preserving every other region.

---

## 🔌 Optional Paradigm integration

When [Paradigm](https://modrinth.com/mod/paradigm) is installed, Paradigm Realms can integrate with its:

- Permission API
- Message formatting
- Placeholder API

Paradigm permissions preserve explicit deny decisions, while native operator-level fallback remains available when the permission API returns no decision.

Paradigm is completely optional.

Paradigm Realms also works as a standalone mod.

---

## 📋 Quick start

```text
/realm help
/realm presets
/realm create [preset]
/realm home
/realm public
/wilds
/wilds rtp
```

Administrators can begin with:

```text
/realms admin help
/realms admin validate
/realms admin presets list
/realms admin wilds status
```

---

## 🚧 Known limitations

- One active realm per owner
- Archived realm cells are not physically deleted or reused
- No realm expansion yet
- No built-in economy, quests, or challenge system
- No client GUI
- No cross-server synchronization
- Arbitrary modded fake players and custom machine behavior cannot be universally guaranteed
- External mod databases are not automatically cleaned during a Wilds reset
- Fabric 1.21.1 is currently the supported release target

---

## 🌸 Planned

- More built-in realm presets
- More Wilds generation profiles
- Realm expansion
- Additional Minecraft versions
- Forge and NeoForge support
- Further modded compatibility integrations

---

## 💬 Support & Community

Need help, found a bug, or have a cute little feature idea?

Come say hi ♡

[![Discord](https://img.shields.io/badge/Join%20our%20Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/bbqPQTzK7b)

---

## ❤️ Support Development

Enjoying Paradigm and want to support its development?

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/L3L4Z8L38)

Every bit of support helps me spend more time making Paradigm better and adding more silly server things. ♡

---

# Credits

**Paradigm Realms** is developed by **Avalanche7CZ**.
```
