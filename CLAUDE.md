# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**PotionArmor** is a Paper/Spigot Minecraft plugin (v3.0.8) that applies potion effects, particle trails, and disguises to players based on custom lore text in their equipped items. When a player equips armor or holds items with specific lore lines, the plugin automatically applies configured effects and removes them when the item is unequipped.

## Build Commands

```bash
# Build the plugin (runs Spotless formatter check + package)
mvn package

# Clean build
mvn clean package

# Build and deploy to test server (uses Makefile)
make test

# Format code with Spotless
mvn spotless:apply

# Check code formatting
mvn spotless:check
```

note: for `mvn` commands, the `-Dmaven.repo.local=/workspace/input/.m2/repository` argument is often necessary to use the local cache

The default Maven goal is `spotless:check package`, which enforces Google Java Format (AOSP style) before building.

## Architecture

### Core Components

**PotionArmorPlugin** (main class)
- Entry point, manages plugin lifecycle
- Owns the `EffectManager` and `EventListener` instances
- Handles async task scheduling via `ScheduledThreadPoolExecutor` (configurable in config.yml)
- Provides commands: `/pareset`, `/reload`, `/effects`, `/debugDump`

**EffectManager** (src/main/java/.../EffectManager.java)
- Central effects manager that processes item lore and applies/removes effects
- Maintains two critical caches:
  - `effectsTable`: Map of lore lines to their configured effects
  - `loreCache`: Cache mapping full item lore to relevant effect lines (prevents repeated config lookups)
- Key methods:
  - `replaceEquipment()`: Called by event handlers, removes old item effects and adds new ones
  - `addEquipment()`: Applies effects from an item's lore
  - `removeEquipment()`: Removes effects and reapplies overlapping potion effects from other equipped items
  - `resetPlayerEffects()`: Clears all effects and reapplies from current equipment

**EventListener** (src/main/java/.../EventListener.java)
- Listens for equipment changes and delegates to EffectManager
- Handles multiple equip/unequip scenarios:
  - `PlayerArmorChangeEvent`: Main armor equip detection (Paper API)
  - `InventoryClickEvent`: Armor slot clicks, main/offhand changes
  - `PlayerSwapHandItemsEvent`: F key swap
  - `InventoryDragEvent`: Dragging items into armor slots
  - `PlayerItemHeldEvent`: Hotbar slot changes
  - `PlayerDropItemEvent`: Dropping equipped items
  - `PlayerArmorStandManipulateEvent`: Armor stand interactions
  - Player lifecycle: death, respawn, join, quit events
- **CRITICAL BUG FIXED**: The `invClick` handler previously had `||` instead of `&&` logic, causing it to never execute

### Effect System

**EquipmentEffect** (abstract base class in Effects/EquipmentEffect.java)
- Defines the effect interface: `applyTo()`, `removeFrom()`
- Parses effect configurations from config.yml's "Effects" section
- Three concrete implementations:
  1. **PotionEffect**: Standard Minecraft potion effects
  2. **TrailEffect**: Particle trails via PlayerParticles API (optional dependency)
  3. **DisguiseEffect**: Entity disguises via LibsDisguises API (optional dependency)

Effects are configured in config.yml under `Effects.<item_id>` sections. Each item specifies:
- `loreline`: The exact lore text to match (color codes stripped)
- `slot`: EquipmentSlotGroup constraint (any, armor, hand, head, etc.)
- `effect1`, `effect2`, etc.: Individual effect configurations with type, parameters

### Threading Model

The plugin supports async effect processing (configured via `meta.async` in config.yml):
- When enabled, uses a `ScheduledThreadPoolExecutor` with configurable thread count
- Effect apply/remove operations are wrapped in Callables and submitted to the pool
- Bukkit API calls (which must run on main thread) are wrapped in `callSyncMethod()`
- `acceptNewJobs` flag prevents task submission during shutdown

### Event Handler Edge Cases

- **Hat command**: Special handling for Essentials' `/hat` via command preprocessing (Essentials doesn't fire inventory events)
- **Armor slot numbers**: Boots=36, Leggings=37, Chestplate=38, Helmet=39, Offhand=45
- **Timing delays**: Join/respawn handlers delay effect resets (5-10 ticks) to ensure inventory is loaded

## Dependencies

- **Paper API 1.21.4** (required, provided scope)
- **PlayerParticles 8.8** (optional, for trail effects)
- **LibsDisguises 11.0.0** (optional, for disguise effects)

If optional dependencies are missing, those effect types are automatically disabled.

## Config Structure

**config.yml**: Effect definitions, async settings, supported effects list
**language.yml**: User-facing messages
**plugin.yml**: Commands, permissions (auto-populated from pom.xml properties)

## Known Issues & TODOs

- Magic number `UNKNOWN_SLOT_NUM = 45` for offhand slot (needs verification)
- `clearActivePotionEffects()` in EffectManager clears ALL potion effects, not just plugin-applied ones
- Hat command handler is a workaround since Essentials doesn't fire events for programmatic inventory changes
- Lore cache never clears during runtime (potential memory growth, though likely negligible)

## Testing

Development workflow uses a local test server:
```bash
# Build, deploy to test server, and start Purpur server
./runTest.sh
# or
make test
```

Test server location is configured in Makefile as `SRV_LOC=../../_test_server/srv`.
