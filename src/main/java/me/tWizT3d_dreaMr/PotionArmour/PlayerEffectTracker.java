/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour;

import dev.esophose.playerparticles.api.PlayerParticlesAPI;
import dev.esophose.playerparticles.particles.ParticlePair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.tWizT3d_dreaMr.PotionArmour.Effects.DisguiseEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.EquipmentEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.PotionEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.TrailEffect;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Tracks which effects have been applied to each player by this plugin.
 * Uses slot-aware tracking to know which equipment slot provides which effects.
 * This allows us to only remove effects that WE applied, leaving effects
 * from other sources (drunk potions, manually added trails, etc.) untouched.
 */
public class PlayerEffectTracker {

    // Player UUID -> Equipment Slot -> Set of effect identifiers from that slot
    private final Map<UUID, Map<EquipmentSlot, Set<String>>> slotEffects = new HashMap<>();

    // Player UUID -> Set of effect identifiers that this plugin has applied (all slots combined)
    private final Map<UUID, Set<String>> activeEffects = new HashMap<>();

    // Player UUID -> Set of potion effect type keys (e.g., "minecraft:regeneration")
    private final Map<UUID, Set<String>> activePotionTypes = new HashMap<>();

    // Player UUID -> Set of trail effect identifiers (particle type + style)
    private final Map<UUID, Set<String>> activeTrails = new HashMap<>();

    // Player UUID -> Current disguise identifier (only one disguise at a time)
    private final Map<UUID, String> activeDisguise = new HashMap<>();

    // ===== Trail Snapshot Tracking (for user trail preservation) =====

    // Player UUID -> List of user-configured ParticlePairs before equipment was applied
    private final Map<UUID, List<ParticlePair>> userTrailSnapshots = new HashMap<>();

    // Player UUID -> Equipment Slot -> Set of equipment trail IDs from that slot
    private final Map<UUID, Map<EquipmentSlot, Set<Integer>>> equipmentTrailIds = new HashMap<>();

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
     * Uses the namespaced key (e.g., "minecraft:regeneration") without level.
     */
    public static String getPotionTypeKey(PotionEffect effect) {
        // Use the potion type key without level for tracking
        return effect.getPotionTypeKey(); // "minecraft:regeneration"
    }

    /**
     * Record that an effect has been applied to a player by this plugin from a specific slot.
     */
    public void trackEffect(Player player, EquipmentEffect effect, EquipmentSlot slot) {
        UUID uuid = player.getUniqueId();
        String effectId = getEffectId(effect);

        // Add to slot-specific tracking
        slotEffects
                .computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(slot, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(effectId);

        // Add to general tracking set
        activeEffects
                .computeIfAbsent(uuid, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(effectId);

        // Type-specific tracking
        if (effect instanceof PotionEffect) {
            PotionEffect pe = (PotionEffect) effect;
            activePotionTypes
                    .computeIfAbsent(uuid, k -> Collections.synchronizedSet(new HashSet<>()))
                    .add(getPotionTypeKey(pe));
        } else if (effect instanceof TrailEffect) {
            activeTrails
                    .computeIfAbsent(uuid, k -> Collections.synchronizedSet(new HashSet<>()))
                    .add(effectId);
        } else if (effect instanceof DisguiseEffect) {
            activeDisguise.put(uuid, effectId);
        }
    }

    /**
     * Record that an effect has been removed from a player from a specific slot.
     */
    public void untrackEffect(Player player, EquipmentEffect effect, EquipmentSlot slot) {
        UUID uuid = player.getUniqueId();
        String effectId = getEffectId(effect);

        // Remove from slot-specific tracking
        Map<EquipmentSlot, Set<String>> playerSlots = slotEffects.get(uuid);
        if (playerSlots != null) {
            Set<String> slotSet = playerSlots.get(slot);
            if (slotSet != null) {
                slotSet.remove(effectId);
                if (slotSet.isEmpty()) {
                    playerSlots.remove(slot);
                }
            }
        }

        // Only remove from general tracking if not present on any other slot
        if (!isEffectOnAnySlot(player, effectId)) {
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
     * Check if an effect is present on any slot for a player.
     */
    private boolean isEffectOnAnySlot(Player player, String effectId) {
        Map<EquipmentSlot, Set<String>> playerSlots = slotEffects.get(player.getUniqueId());
        if (playerSlots == null) return false;

        for (Set<String> slotSet : playerSlots.values()) {
            if (slotSet.contains(effectId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an effect is present on any slot OTHER than the specified slot.
     * This is used to determine if removing an effect from one slot should
     * actually remove the visual effect (it shouldn't if another slot has it).
     */
    public boolean isEffectOnOtherSlot(Player player, String effectId, EquipmentSlot excludeSlot) {
        Map<EquipmentSlot, Set<String>> playerSlots = slotEffects.get(player.getUniqueId());
        if (playerSlots == null) return false;

        for (Map.Entry<EquipmentSlot, Set<String>> entry : playerSlots.entrySet()) {
            if (entry.getKey() == excludeSlot) continue;
            if (entry.getValue().contains(effectId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clear all tracking for a player (used on quit/death).
     */
    public void clearPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        slotEffects.remove(uuid);
        activeEffects.remove(uuid);
        activePotionTypes.remove(uuid);
        activeTrails.remove(uuid);
        activeDisguise.remove(uuid);
        userTrailSnapshots.remove(uuid);
        equipmentTrailIds.remove(uuid);
    }

    /**
     * Clear only potion effect tracking for a player.
     */
    public void clearPotions(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> potions = activePotionTypes.get(uuid);
        if (potions != null) {
            // Remove potion entries from general tracking and slot tracking
            Set<String> effects = activeEffects.get(uuid);
            if (effects != null) {
                effects.removeIf(e -> e.startsWith("POTION:"));
            }
            Map<EquipmentSlot, Set<String>> playerSlots = slotEffects.get(uuid);
            if (playerSlots != null) {
                for (Set<String> slotSet : playerSlots.values()) {
                    slotSet.removeIf(e -> e.startsWith("POTION:"));
                }
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
            Map<EquipmentSlot, Set<String>> playerSlots = slotEffects.get(uuid);
            if (playerSlots != null) {
                for (Set<String> slotSet : playerSlots.values()) {
                    slotSet.removeIf(e -> e.startsWith("TRAIL:"));
                }
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
        Map<EquipmentSlot, Set<String>> playerSlots = slotEffects.get(uuid);
        if (playerSlots != null) {
            for (Set<String> slotSet : playerSlots.values()) {
                slotSet.removeIf(e -> e.startsWith("DISGUISE:"));
            }
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

    // ===== Trail Snapshot Methods (for user trail preservation) =====

    /**
     * Capture a snapshot of the player's current trails before applying equipment trails.
     * This preserves user-configured trails so they can be restored later.
     */
    public void captureUserTrailSnapshot(Player player) {
        UUID uuid = player.getUniqueId();

        // Don't overwrite an existing snapshot
        if (userTrailSnapshots.containsKey(uuid)) {
            return;
        }

        try {
            PlayerParticlesAPI api = PlayerParticlesAPI.getInstance();
            Collection<ParticlePair> currentTrails = api.getActivePlayerParticles(player);

            // Clone the list to avoid reference issues
            List<ParticlePair> snapshot = new ArrayList<>();
            for (ParticlePair pair : currentTrails) {
                snapshot.add(pair.clone());
            }

            userTrailSnapshots.put(uuid, snapshot);
        } catch (Exception e) {
            // PlayerParticles not loaded or error - just don't snapshot
            PotionArmorPlugin.plugin.logger.warning(
                    "Failed to capture trail snapshot for "
                            + player.getName()
                            + ": "
                            + e.getMessage());
        }
    }

    /**
     * Restore the player's user-configured trails from the snapshot.
     * This is called when all equipment trails have been removed.
     */
    public void restoreUserTrailSnapshot(Player player) {
        UUID uuid = player.getUniqueId();
        List<ParticlePair> snapshot = userTrailSnapshots.remove(uuid);

        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        try {
            PlayerParticlesAPI api = PlayerParticlesAPI.getInstance();

            // Re-apply each snapshotted trail
            for (ParticlePair pair : snapshot) {
                api.addActivePlayerParticle(player, pair.getEffect(), pair.getStyle());
            }
        } catch (Exception e) {
            PotionArmorPlugin.plugin.logger.warning(
                    "Failed to restore trail snapshot for "
                            + player.getName()
                            + ": "
                            + e.getMessage());
        }
    }

    /**
     * Track an equipment trail ID for a specific slot.
     */
    public void trackEquipmentTrailId(Player player, int trailId, EquipmentSlot slot) {
        UUID uuid = player.getUniqueId();
        equipmentTrailIds
                .computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(slot, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(trailId);
    }

    /**
     * Untrack an equipment trail ID from a specific slot.
     */
    public void untrackEquipmentTrailId(Player player, int trailId, EquipmentSlot slot) {
        UUID uuid = player.getUniqueId();
        Map<EquipmentSlot, Set<Integer>> playerTrails = equipmentTrailIds.get(uuid);
        if (playerTrails != null) {
            Set<Integer> slotTrails = playerTrails.get(slot);
            if (slotTrails != null) {
                slotTrails.remove(trailId);
                if (slotTrails.isEmpty()) {
                    playerTrails.remove(slot);
                }
            }
            if (playerTrails.isEmpty()) {
                equipmentTrailIds.remove(uuid);
            }
        }
    }

    /**
     * Check if the player has ANY equipment trails active across all slots.
     */
    public boolean hasEquipmentTrails(Player player) {
        Map<EquipmentSlot, Set<Integer>> playerTrails = equipmentTrailIds.get(player.getUniqueId());
        if (playerTrails == null || playerTrails.isEmpty()) {
            return false;
        }

        for (Set<Integer> slotTrails : playerTrails.values()) {
            if (slotTrails != null && !slotTrails.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all equipment trail IDs for a specific slot.
     * Returns an empty set if no trails are tracked for that slot.
     */
    public Set<Integer> getEquipmentTrailIds(Player player, EquipmentSlot slot) {
        UUID uuid = player.getUniqueId();
        Map<EquipmentSlot, Set<Integer>> playerTrails = equipmentTrailIds.get(uuid);
        if (playerTrails == null) {
            return Collections.emptySet();
        }

        Set<Integer> slotTrails = playerTrails.get(slot);
        return slotTrails != null ? new HashSet<>(slotTrails) : Collections.emptySet();
    }

    /**
     * Clear the user trail snapshot for a player (used on death/quit).
     */
    public void clearUserTrailSnapshot(Player player) {
        userTrailSnapshots.remove(player.getUniqueId());
    }

    /**
     * Clear all equipment trail ID tracking for a player.
     */
    public void clearEquipmentTrailIds(Player player) {
        equipmentTrailIds.remove(player.getUniqueId());
    }
}
