# API Reference: PlayerParticles & LibsDisguises

This document provides a comprehensive reference for the PlayerParticles and LibsDisguises APIs used in the PotionArmor plugin.

## PlayerParticles API (v8.8)

### Dependency Information

**Maven coordinates:**
```xml
<dependency>
    <groupId>dev.esophose</groupId>
    <artifactId>playerparticles</artifactId>
    <version>8.8</version>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```

**Repository:**
```xml
<repository>
    <id>rwd-repo</id>
    <url>https://repo.rosewooddev.io/repository/public/</url>
</repository>
```

**Links:**
- GitHub: https://github.com/Rosewood-Development/PlayerParticles
- JavaDocs: https://javadoc.rosewooddev.io/PlayerParticles/

### Core Classes Used

#### PlayerParticlesAPI

**Singleton access:**
```java
import dev.esophose.playerparticles.api.PlayerParticlesAPI;

PlayerParticlesAPI api = PlayerParticlesAPI.getInstance();
```

**Key methods:**

```java
// Add a trail (returns ParticlePair with unique ID)
ParticlePair addActivePlayerParticle(Player player, ParticleEffect effect, ParticleStyle style);
ParticlePair addActivePlayerParticle(Player player, ParticleEffect effect, ParticleStyle style, OrdinaryColor data);
ParticlePair addActivePlayerParticle(Player player, ParticleEffect effect, ParticleStyle style, ColorTransition data);
ParticlePair addActivePlayerParticle(Player player, ParticleEffect effect, ParticleStyle style, NoteColor data);
ParticlePair addActivePlayerParticle(Player player, ParticleEffect effect, ParticleStyle style, Vibration data);
ParticlePair addActivePlayerParticle(Player player, ParticleEffect effect, ParticleStyle style, Material data);

// Remove trails (selective by ID - PREFERRED)
void removeActivePlayerParticle(Player player, int particlePairId);

// Remove trails (bulk by type - AVOID, removes ALL including user trails)
void removeActivePlayerParticles(Player player, ParticleEffect effect);

// Nuclear option - removes ALL trails for a player
void resetActivePlayerParticles(Player player);

// Query active trails
List<ParticlePair> getActivePlayerParticles(Player player);
```

#### ParticlePair

Represents an active trail instance.

```java
// Get unique identifier
int getId();

// Get the effect type
ParticleEffect getEffect();

// Get the style
ParticleStyle getStyle();

// Clone for snapshots
ParticlePair clone();
```

#### ParticleEffect

Enum of particle types. Examples:
- `FLAME`, `SMALL_FLAME`
- `DUST`, `DUST_COLOR_TRANSITION`
- `NOTE`
- `ENTITY_EFFECT`
- `VIBRATION`
- `BLOCK`, `ITEM`

**Usage:**
```java
ParticleEffect effect = ParticleEffect.fromName("flame");
```

#### ParticleStyle

Defines trail patterns. Examples:
- `OVERHEAD` - Particles above player
- `GROUND` - Particles at ground level
- `HALO` - Circle around player
- `ORBIT` - Rotating effect

**Usage:**
```java
ParticleStyle style = ParticleStyle.fromName("overhead");
```

### Particle Data Types

Used to modify particle appearance:

#### OrdinaryColor
```java
import dev.esophose.playerparticles.particles.data.OrdinaryColor;

OrdinaryColor color = new OrdinaryColor(255, 0, 0); // RGB (red)
```

#### ColorTransition
```java
import dev.esophose.playerparticles.particles.data.ColorTransition;

ColorTransition gradient = new ColorTransition(
    255, 0, 0,    // Start RGB (red)
    0, 0, 255     // End RGB (blue)
);
```

#### NoteColor
```java
import dev.esophose.playerparticles.particles.data.NoteColor;

NoteColor note = new NoteColor(12); // Note value 0-24
```

#### Vibration
```java
import dev.esophose.playerparticles.particles.data.Vibration;

Vibration vib = new Vibration(
    x, y, z,           // Origin location
    x2, y2, z2,        // Destination location
    100                // Duration in ticks
);
```

#### Material-based
```java
import org.bukkit.Material;

// Use Material directly for BLOCK/ITEM particles
api.addActivePlayerParticle(player, ParticleEffect.BLOCK, style, Material.STONE);
```

### Usage Examples

```java
// Example 1: Simple flame trail
PlayerParticlesAPI api = PlayerParticlesAPI.getInstance();
ParticlePair trail = api.addActivePlayerParticle(
    player,
    ParticleEffect.fromName("flame"),
    ParticleStyle.fromName("overhead")
);
int trailId = trail.getId(); // Store for later removal

// Example 2: Colored dust trail
OrdinaryColor purple = new OrdinaryColor(128, 0, 255);
ParticlePair coloredTrail = api.addActivePlayerParticle(
    player,
    ParticleEffect.fromName("dust"),
    ParticleStyle.fromName("halo"),
    purple
);

// Example 3: Color transition trail
ColorTransition rainbow = new ColorTransition(255, 0, 0, 0, 255, 0);
ParticlePair gradientTrail = api.addActivePlayerParticle(
    player,
    ParticleEffect.fromName("dust_color_transition"),
    ParticleStyle.fromName("orbit"),
    rainbow
);

// Example 4: Selective removal (by ID)
api.removeActivePlayerParticle(player, trailId);

// Example 5: Query active trails
List<ParticlePair> active = api.getActivePlayerParticles(player);
for (ParticlePair pair : active) {
    System.out.println("Trail ID: " + pair.getId() + ", Effect: " + pair.getEffect());
}
```

---

## LibsDisguises API (v11.0.0)

### Dependency Information

**Maven coordinates:**
```xml
<dependency>
    <groupId>me.libraryaddict.disguises</groupId>
    <artifactId>libsdisguises</artifactId>
    <version>11.0.0</version>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```

**Repository:**
```xml
<repository>
    <id>md_5-public</id>
    <url>https://repo.md-5.net/content/groups/public/</url>
</repository>
```

**Links:**
- SpigotMC: https://www.spigotmc.org/resources/libs-disguises.81/
- Wiki: https://github.com/libraryaddict/LibsDisguises/wiki

### Core Classes Used

#### DisguiseAPI

Main entry point for disguise operations.

```java
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.entity.Entity;

// Apply disguise
DisguiseAPI.disguiseEntity(Entity entity, Disguise disguise);

// Remove disguise (visible to all players)
DisguiseAPI.undisguiseToAll(Entity entity);

// Check if entity is disguised
boolean isDisguised = DisguiseAPI.isDisguised(Entity entity);

// Get current disguise
Disguise current = DisguiseAPI.getDisguise(Entity entity);
```

#### DisguiseParser

Utility for parsing disguise strings from configuration.

```java
import me.libraryaddict.disguise.utilities.parser.DisguiseParser;
import me.libraryaddict.disguise.disguisetypes.Disguise;

// Parse disguise from string (e.g., "cow", "zombie baby", "creeper powered")
Disguise disguise = DisguiseParser.parseDisguise(String disguiseDescription);
```

**Configuration examples:**
- `"cow"` - Simple cow disguise
- `"zombie baby"` - Baby zombie
- `"creeper powered"` - Charged creeper
- `"player Notch"` - Player disguise as Notch
- `"sheep setSheep setColor white"` - White sheep

### Usage in PotionArmor

**Location**: `DisguiseEffect.java:29-39`

```java
public DisguiseEffect(EquipmentSlotGroup slot, String disguiseDesc) {
    this.slot = slot;

    try {
        disguise = DisguiseParser.parseDisguise(disguiseDesc);
    } catch (Throwable e) {
        PotionArmorPlugin.plugin.logger.severe(
            "Error creating disguise: " + e.toString() + " " + e.getMessage() +
            "\n with parameter: " + disguiseDesc
        );
    }
}
```

**Application** (line 52-54):
```java
public boolean applyTo(LivingEntity p) {
    DisguiseAPI.disguiseEntity((Entity) p, disguise);
    return true;
}
```

**Removal** (line 67-69):
```java
public boolean removeFrom(LivingEntity p) {
    DisguiseAPI.undisguiseToAll((Entity) p);
    return true;
}
```

### Configuration Format

In `config.yml`:

```yaml
Effects:
  dragon_helmet:
    loreline: "§5Dragon Power"
    slot: head
    effect1:
      enable: true
      type: disguise
      entity: "ender_dragon"  # Disguise string parsed by DisguiseParser
```

### Common Disguise Strings

| Type | Example String | Description |
|------|---------------|-------------|
| Mobs | `"zombie"`, `"skeleton"`, `"creeper"` | Basic mob disguises |
| Variants | `"zombie baby"`, `"creeper powered"` | Mob with modifiers |
| Animals | `"cow"`, `"sheep"`, `"chicken"` | Passive mobs |
| Players | `"player Notch"`, `"player Steve"` | Player skins |
| Boss | `"ender_dragon"`, `"wither"` | Boss mobs |

---

## Integration Notes

### Graceful Degradation

Both dependencies are **optional** in PotionArmor. The plugin detects if they're loaded:

**PlayerParticles check** (`EffectManager.java:109-112`):
```java
if (!Bukkit.getPluginManager().isPluginEnabled("PlayerParticles")) {
    Bukkit.getLogger().warning("PlayerParticles is not loaded, trail support will be disabled.");
    isEnabled.put(EffectType.TRAIL, false);
}
```

**LibsDisguises check** (`EffectManager.java:105-108`):
```java
if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
    Bukkit.getLogger().warning("LibsDisguises is not loaded, disguise support will be disabled.");
    isEnabled.put(EffectType.DISGUISE, false);
}
```

### Performance Considerations

- **PlayerParticles**: Lightweight, runs particle updates async
- **LibsDisguises**: Moderate overhead, sends fake entity packets to clients
- Both integrate seamlessly with PotionArmor's async effect processing (configurable via `meta.async` in config.yml)
