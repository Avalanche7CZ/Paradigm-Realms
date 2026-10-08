
# Paradigm Realms

> **Personal realms for permanent bases and a shared Wilds world for exploration.**

Paradigm Realms is a server-side mod that gives each player their own protected realm. Players can build, store items, run farms, or invite friends without relying on a separate claiming mod.

The **Wilds** provide a shared world for resources, structures, mobs, and exploration. Server administrators can regenerate it without affecting player realms.

**No client installation is required.**

---

## Features

- One permanent realm per player
- Configurable starter presets
- Public and private realms
- Members, managers, visitors, invitations, and bans
- Built-in realm protection
- Public realm directory
- Realm resets and ownership transfers
- Schematic and structure imports
- Shared Wilds world with safe random teleportation
- Optional integration with [Paradigm](https://modrinth.com/mod/paradigm)

---

## Realms

Players can create a realm from one of the presets allowed by the server.

```text
/realm presets
/realm create [preset]
/realm home
```

Realm owners can:

- Change the realm name and description
- Invite members or managers
- Allow visitors
- Make the realm public or private
- Configure PvP, explosions, mob griefing, and visitor access
- Reset, delete, or transfer the realm

Public realms may also be listed in the realm directory:

```text
/realm public
```

Realm protection covers blocks, containers, entities, fluids, explosions, pistons, teleports, and other common interactions.

---

## Presets

Servers can provide starter islands, flat plots, empty platforms, custom bases, or modpack-specific starting areas.

Administrators can also import:

- Sponge `.schem`
- WorldEdit `.schematic`
- Litematica `.litematic`
- Vanilla structure `.nbt`

Imports are checked before they become available to players.

---

## The Wilds

The Wilds are intended for temporary exploration and resource gathering.

```text
/wilds
/wilds spawn
/wilds rtp
/wilds info
```

Random teleportation checks world borders, terrain, and collisions before moving the player.

Wilds resets are prepared while the server is running and completed while the world is offline. The previous generation is kept as a backup until the replacement has been verified.

---

## Administration

```text
/realms admin help
/realms admin validate
/realms admin presets list
/realms admin wilds status
```

Administration tools cover realm inspection, validation, preset imports, archived realms, interrupted operations, Wilds resets, and support exports.

---

## Planned

- More realm presets
- More Wilds profiles
- Realm expansion
- Forge and NeoForge support
- Additional Minecraft versions

---

## Support

[![Discord](https://img.shields.io/badge/Join%20our%20Discord-5865F2?logo=discord&logoColor=white)](https://discord.gg/bbqPQTzK7b)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/L3L4Z8L38)

---

**Paradigm Realms** is developed by **Avalanche7CZ**.

