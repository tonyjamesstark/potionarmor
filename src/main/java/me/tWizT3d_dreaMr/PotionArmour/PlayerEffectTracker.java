/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.tWizT3d_dreaMr.PotionArmour.Effects.DisguiseEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.EquipmentEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.PotionEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.TrailEffect;
import org.bukkit.entity.Player;

/**
 * Tracks which effects have been applied to each player by this plugin.
 * This allows us to only remove effects that WE applied, leaving effects
 * from other sources (drunk potions, manually added trails, etc.) untouched.
 */
public class PlayerEffectTracker {

    // Player UUID -> Set of effect identifiers that this plugin has applied
    private final Map<UUID, Set<String>> activeEffects = new ConcurrentHashMap<>();

    // Player UUID -> Set of potion effect type keys (e.g., "minecraft:regeneration")
    private final Map<UUID, Set<String>> activePotionTypes = new ConcurrentHashMap<>();

    // Player UUID -> Set of trail effect identifiers (particle type + style)
    private final Map<UUID, Set<String>> activeTrails = new ConcurrentHashMap<>();

    // Player UUID -> Current disguise identifier (only one disguise at a time)
    private final Map<UUID, String> activeDisguise = new ConcurrentHashMap<>();

    // Cooldown tracking to prevent validation during active equipment changes
    private long validationCooldownMs = 5000L; // Default 5 seconds
    private final Map<UUID, Long> lastEquipmentChange = new ConcurrentHashMap<>();

    /**
     * Generate a unique identifier for an effect.
     * Format depends on effect type:
     * - POTION: "POTION:minecraft:regeneration:1"
     * - TRAIL: "TRAIL:flame:overhead:data_hash"
     * - DISGUISE: "DISGUISE:creeper"
     */
    public static String getEffectId(EquipmentEffect effect) {
        if (effect instanceof PotionEffect) {
            PotionEffect pe = (PotionEffect) effect;
            return "POTION:" + pe.toString();
        } else if (effect instanceof TrailEffect) {
            return "TRAIL:" + effect.toString();
        } else if (effect instanceof DisguiseEffect) {
            return "DISGUISE:" + effect.toString();
        }
        return "UNKNOWN:" + effect.toString();
    }

    /**
     * Get the potion type key from a PotionEffect for tracking.
     */
    public static String getPotionTypeKey(PotionEffect effect) {
        // Extract the potion effect type from the effect
        return effect.toString(); // "CE minecraft:regeneration" etc.
    }

    /**
     * Record that an effect has been applied to a player by this plugin.
     */
    public void trackEffect(Player player, EquipmentEffect effect) {
        UUID uuid = player.getUniqueId();
        String effectId = getEffectId(effect);

        // Add to general tracking set
        activeEffects.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(effectId);

        // Type-specific tracking
        if (effect instanceof PotionEffect) {
            PotionEffect pe = (PotionEffect) effect;
            activePotionTypes
                    .computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                    .add(getPotionTypeKey(pe));
        } else if (effect instanceof TrailEffect) {
            activeTrails.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(effectId);
        } else if (effect instanceof DisguiseEffect) {
            activeDisguise.put(uuid, effectId);
        }
    }

    /**
     * Set the validation cooldown period in milliseconds.
     */
    public void setValidationCooldown(long milliseconds) {
        this.validationCooldownMs = milliseconds;
    }

    /**
     * Get the current validation cooldown period in milliseconds.
     */
    public long getValidationCooldownMs() {
        return validationCooldownMs;
    }

    /**
     * Mark that a player's equipment has changed.
     * This starts a cooldown period during which validation will be skipped.
     */
    public void markEquipmentChange(Player player) {
        lastEquipmentChange.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Get time in milliseconds since the player's equipment last changed.
     * Returns Long.MAX_VALUE if equipment has never changed.
     */
    public long getTimeSinceLastChange(Player player) {
        Long lastChange = lastEquipmentChange.get(player.getUniqueId());
        if (lastChange == null) {
            return Long.MAX_VALUE; // Never changed, no cooldown
        }
        return System.currentTimeMillis() - lastChange;
    }

    /**
     * Record that an effect has been removed from a player.
     */
    public void untrackEffect(Player player, EquipmentEffect effect) {
        UUID uuid = player.getUniqueId();
        String effectId = getEffectId(effect);

        // Remove from general tracking
        Set<String> effects = activeEffects.get(uuid);
        if (effects != null) {
            effects.remove(effectId);
        }

        // Type-specific untracking
        if (effect instanceof PotionEffect) {
            PotionEffect pe = (PotionEffect) effect;
            Set<String> potions = activePotionTypes.get(uuid);
            if (potions != null) {
                potions.remove(getPotionTypeKey(pe));
            }
        } else if (effect instanceof TrailEffect) {
            Set<String> trails = activeTrails.get(uuid);
            if (trails != null) {
                trails.remove(effectId);
            }
        } else if (effect instanceof DisguiseEffect) {
            activeDisguise.remove(uuid);
        }
    }

    /**
     * Check if a specific effect was applied by this plugin.
     */
    public boolean isTracked(Player player, EquipmentEffect effect) {
        UUID uuid = player.getUniqueId();
        String effectId = getEffectId(effect);
        Set<String> effects = activeEffects.get(uuid);
        return effects != null && effects.contains(effectId);
    }

    /**
     * Check if a potion effect type was applied by this plugin.
     */
    public boolean isPotionTypeTracked(Player player, String potionTypeKey) {
        UUID uuid = player.getUniqueId();
        Set<String> potions = activePotionTypes.get(uuid);
        return potions != null && potions.contains(potionTypeKey);
    }

    /**
     * Check if the player has a disguise applied by this plugin.
     */
    public boolean hasTrackedDisguise(Player player) {
        return activeDisguise.containsKey(player.getUniqueId());
    }

    /**
     * Get all effect IDs currently tracked for a player.
     */
    public Set<String> getTrackedEffects(Player player) {
        Set<String> effects = activeEffects.get(player.getUniqueId());
        return effects != null ? Collections.unmodifiableSet(effects) : Collections.emptySet();
    }

    /**
     * Get all tracked potion type keys for a player.
     */
    public Set<String> getTrackedPotionTypes(Player player) {
        Set<String> potions = activePotionTypes.get(player.getUniqueId());
        return potions != null ? Collections.unmodifiableSet(potions) : Collections.emptySet();
    }

    /**
     * Get all tracked trail effect IDs for a player.
     */
    public Set<String> getTrackedTrails(Player player) {
        Set<String> trails = activeTrails.get(player.getUniqueId());
        return trails != null ? Collections.unmodifiableSet(trails) : Collections.emptySet();
    }

    /**
     * Clear all tracking for a player (used on quit/death).
     */
    public void clearPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        activeEffects.remove(uuid);
        activePotionTypes.remove(uuid);
        activeTrails.remove(uuid);
        activeDisguise.remove(uuid);
        lastEquipmentChange.remove(uuid);
    }

    /**
     * Clear only potion effect tracking for a player.
     */
    public void clearPotions(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> potions = activePotionTypes.get(uuid);
        if (potions != null) {
            // Remove potion entries from general tracking too
            Set<String> effects = activeEffects.get(uuid);
            if (effects != null) {
                effects.removeIf(e -> e.startsWith("POTION:"));
            }
            potions.clear();
        }
    }

    /**
     * Clear only trail effect tracking for a player.
     */
    public void clearTrails(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> trails = activeTrails.get(uuid);
        if (trails != null) {
            Set<String> effects = activeEffects.get(uuid);
            if (effects != null) {
                effects.removeIf(e -> e.startsWith("TRAIL:"));
            }
            trails.clear();
        }
    }

    /**
     * Clear only disguise tracking for a player.
     */
    public void clearDisguise(Player player) {
        UUID uuid = player.getUniqueId();
        activeDisguise.remove(uuid);
        Set<String> effects = activeEffects.get(uuid);
        if (effects != null) {
            effects.removeIf(e -> e.startsWith("DISGUISE:"));
        }
    }

    /**
     * Check if any effects are tracked for a player.
     */
    public boolean hasAnyTrackedEffects(Player player) {
        Set<String> effects = activeEffects.get(player.getUniqueId());
        return effects != null && !effects.isEmpty();
    }

    /**
     * Get count of tracked effects for a player (useful for debugging).
     */
    public int getTrackedEffectCount(Player player) {
        Set<String> effects = activeEffects.get(player.getUniqueId());
        return effects != null ? effects.size() : 0;
    }
}
