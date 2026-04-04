/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour;

import dev.esophose.playerparticles.api.PlayerParticlesAPI;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.libraryaddict.disguise.DisguiseAPI;
import me.tWizT3d_dreaMr.PotionArmour.Effects.EquipmentEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.EquipmentEffect.EffectType;
import me.tWizT3d_dreaMr.PotionArmour.Effects.PotionEffect;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

public class EffectManager {
    PotionArmorPlugin plugin;
    private final String LORE_DELIM = "|";

    public static Map<EffectType, Boolean> isEnabled = new HashMap<>();

    public static List<NamespacedKey> supportedEffects = new ArrayList<>();

    // Tracks which effects this plugin has applied to each player
    // This allows us to only remove OUR effects, not effects from other sources
    private final PlayerEffectTracker tracker = new PlayerEffectTracker();

    // loreline --> effects list
    private static Map<String, List<EquipmentEffect>> effectsTable =
            new HashMap<String, List<EquipmentEffect>>();

    // full item lore --> relevant lore lines
    // (cache built up as items processed)
    private static Map<String, List<String>> loreCache = new HashMap<String, List<String>>();

    /**
     * Get the effect tracker for external access (e.g., validation tasks).
     */
    public PlayerEffectTracker getTracker() {
        return tracker;
    }

    /**
     * Get detailed validation state for a player (for debugging).
     * Shows tracked vs expected effects.
     */
    public String getValidationDebugInfo(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Debug Info for ").append(player.getName()).append(":\n");

        // Effects
        Set<String> tracked = tracker.getTrackedEffects(player);
        Set<String> expected = calculateExpectedEffects(player);

        sb.append("  Effects:\n");
        sb.append("    Tracked (")
                .append(tracked.size())
                .append("): ")
                .append(tracked)
                .append("\n");
        sb.append("    Expected (")
                .append(expected.size())
                .append("): ")
                .append(expected)
                .append("\n");

        // Differences
        Set<String> orphaned = new HashSet<>(tracked);
        orphaned.removeAll(expected);
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(tracked);

        if (!orphaned.isEmpty()) {
            sb.append("    Orphaned: ").append(orphaned).append("\n");
        }
        if (!missing.isEmpty()) {
            sb.append("    Missing: ").append(missing).append("\n");
        }
        if (orphaned.isEmpty() && missing.isEmpty()) {
            sb.append("    Status: All effects match!\n");
        }

        return sb.toString();
    }

    public EffectManager(PotionArmorPlugin pluginInstance) {
        plugin = pluginInstance;

        for (EffectType t : EffectType.values()) {
            isEnabled.put(t, true);
        }
        isEnabled.put(EffectType.BASE, false); // base type abstract, cannot enable

        if (!Bukkit.getPluginManager().isPluginEnabled("PlayerParticles")) {
            plugin.logger.warning("PlayerParticles is not loaded, trail support will be disabled.");
            isEnabled.put(EffectType.TRAIL, false);
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("libsdisguises")) {
            plugin.logger.warning(
                    "libsdisguises is not loaded, disguise support will be disabled.");
            isEnabled.put(EffectType.DISGUISE, false);
        }
    }

    /**
     * Load effect configurations from the plugin's config file.
     *
     * @param cfg The configuration file to load from
     * @return The number of effects loaded
     */
    public int loadEffects(FileConfiguration cfg) {
        for (EffectType t : isEnabled.keySet()) {
            if (t != EffectType.BASE && !isEnabled.get(t)) {
                plugin.logger.info(
                        "Effect type "
                                + t.toString()
                                + " not enabled, skipping these effects in config");
            }
        }
        Map<String, List<EquipmentEffect>> loaded =
                EquipmentEffect.effectsFromConfig(cfg, plugin.logger);
        effectsTable.putAll(loaded);
        return loaded.size();
    }

    EquipmentSlot[] slots = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD,
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND
    };

    /**
     * Reset all effects for a player by removing tracked effects and reapplying from current equipment.
     *
     * @param player The player to reset effects for
     */
    public void resetPlayerEffects(Player player) {
        plugin.logger.info("Resetting player effects for " + player.getName());
        Set<String> beforeTracked = tracker.getTrackedPotionTypes(player);
        plugin.logger.info("Before reset - tracked potions: " + beforeTracked);
        List<ItemStack> equipment = getEquippedItems(player);
        plugin.logger.info("Equipment slots: " + equipment.size());

        // Only remove effects that WE applied, preserving effects from
        // other sources (drunk potions, manually added trails, etc.)
        clearTrackedEffects(player);

        // Re-apply equipment effects
        for (int i = 0; i < equipment.size(); i++) {
            addEquipment(player, equipment.get(i), slots[i]);
        }
    }

    /**
     * Clear only the effects that this plugin has applied to the player.
     * This preserves effects from other sources like drunk potions or manually added trails.
     */
    private void clearTrackedEffects(Player player) {
        // Get tracked potion types and remove only those
        if (isEnabled.get(EffectType.POTION)) {
            Set<String> trackedPotions = tracker.getTrackedPotionTypes(player);
            for (org.bukkit.potion.PotionEffect active : player.getActivePotionEffects()) {
                // Use namespaced key to match tracking format (e.g., "minecraft:regeneration")
                String potionKey = active.getType().getKey().toString();
                if (trackedPotions.contains(potionKey)) {
                    player.removePotionEffect(active.getType());
                }
            }
        }

        // Clear tracked trails
        if (isEnabled.get(EffectType.TRAIL)) {
            // PlayerParticles API doesn't have fine-grained removal, but we track what we added
            // We need to remove only the particles we added
            // Unfortunately the API removes all particles of a type, so we'll clear and re-add
            // non-plugin particles aren't tracked by us anyway
            Set<String> trackedTrails = tracker.getTrackedTrails(player);
            if (!trackedTrails.isEmpty()) {
                // We have to use the API's reset since we can't selectively remove
                // But this only affects PlayerParticles-managed particles for this player
                PlayerParticlesAPI.getInstance().resetActivePlayerParticles(player);
            }
        }

        // Clear tracked disguise
        if (isEnabled.get(EffectType.DISGUISE)) {
            if (tracker.hasTrackedDisguise(player)) {
                DisguiseAPI.undisguiseToAll((Entity) player);
            }
        }

        // Clear the tracking data
        tracker.clearPlayer(player);
    }

    /**
     * Get all currently equipped items for a player.
     * 
     * @param player The player
     * @return List containing armor (boots to helmet), main hand, and off hand items
     */
    private List<ItemStack> getEquippedItems(Player player) {
        List<ItemStack> equipped = new ArrayList<>();
        equipped.addAll(Arrays.asList(player.getEquipment().getArmorContents()));
        equipped.add(player.getInventory().getItemInMainHand());
        equipped.add(player.getInventory().getItemInOffHand());
        return equipped;
    }

    /**
     * Container for potion effect level and the slot it comes from.
     */
    private static class PotionLevelInfo {
        final int level;
        final EquipmentSlot slot;

        PotionLevelInfo(int level, EquipmentSlot slot) {
            this.level = level;
            this.slot = slot;
        }
    }

    /**
     * Find the highest level of a potion effect type that remains on equipped items
     * (excluding the item being removed).
     *
     * @param player The player
     * @param potionTypeKey The potion type key (e.g., "minecraft:speed")
     * @param excludingItem The item being removed (to exclude from check)
     * @return PotionLevelInfo with level and slot, or null if none found
     */
    private PotionLevelInfo getHighestRemainingLevel(
            Player player, String potionTypeKey, ItemStack excludingItem) {
        int highestLevel = -1;
        EquipmentSlot highestSlot = null;
        List<ItemStack> equipped = getEquippedItems(player);

        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack item = equipped.get(idx);
            if (item == null || item.getType() == Material.AIR)
                continue;

            // Skip the item being removed
            if (excludingItem != null && item.isSimilar(excludingItem))
                continue;

            List<String> lore = getLore(item);
            if (lore == null)
                continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(lore)) {
                List<EquipmentEffect> effects = effectsTable.get(loreline);
                if (effects == null)
                    continue;
                for (EquipmentEffect eff : effects) {
                    if (!eff.slot.test(slot))
                        continue;
                    if (!(eff instanceof PotionEffect))
                        continue;

                    PotionEffect pe = (PotionEffect) eff;
                    if (pe.getPotionTypeKey().equals(potionTypeKey)) {
                        if (pe.getLevel() > highestLevel) {
                            highestLevel = pe.getLevel();
                            highestSlot = slot;
                        }
                    }
                }
            }
        }
        return highestLevel >= 0 ? new PotionLevelInfo(highestLevel, highestSlot) : null;
    }

    /**
     * Apply a potion effect at a specific level from a specific slot.
     *
     * @param player The player to apply the effect to
     * @param baseEffect The base potion effect (for slot and type information)
     * @param level The level to apply
     * @param slot The equipment slot this effect comes from
     */
    private void applyPotionEffectAtLevel(
            Player player, PotionEffect baseEffect, int level, EquipmentSlot slot) {
        NamespacedKey key = NamespacedKey.fromString(baseEffect.getPotionTypeKey());
        PotionEffectType effectType = Registry.EFFECT.get(key);
        PotionEffect lowerEffect = new PotionEffect(baseEffect.slot, effectType, level);
        lowerEffect.applyTo(player, slot);
        tracker.trackEffect(player, lowerEffect, slot);
    }

    /**
     * Handle removal of a potion effect, considering level overlaps from other equipment.
     *
     * @param player The player
     * @param effect The effect being removed
     * @param removedItem The item being removed (excluded from overlap check)
     * @param sameEffectOnOtherSlot Whether this exact effect exists on another slot
     * @return true if effect was handled (don't remove visual), false if should remove visual
     */
    private boolean handlePotionEffectRemoval(
            Player player,
            PotionEffect effect,
            ItemStack removedItem,
            boolean sameEffectOnOtherSlot,
            EquipmentSlot slot) {

        // If the exact same effect (same level) is on another slot, don't remove
        if (sameEffectOnOtherSlot) {
            return true;
        }

        // Check if a different level of this potion type exists on other equipment
        PotionLevelInfo remaining =
                getHighestRemainingLevel(player, effect.getPotionTypeKey(), removedItem);

        if (remaining != null) {
            // A different level remains - remove current and re-apply the highest remaining
            effect.removeFrom(player, slot);
            // Re-apply immediately with slot info - no delay needed!
            applyPotionEffectAtLevel(player, effect, remaining.level, remaining.slot);
            return true;
        }

        return false; // No overlap, remove the visual effect
    }

    private void removeEffects(
            Player player, List<String> lines, ItemStack removedItem, EquipmentSlot slot) {
        for (String loreLine : lines) {
            for (EquipmentEffect eff : effectsTable.get(loreLine)) {
                // Only remove if we tracked this effect
                if (!tracker.isTracked(player, eff)) {
                    continue;
                }

                String effectId = PlayerEffectTracker.getEffectId(eff);

                // Untrack from this slot
                tracker.untrackEffect(player, eff, slot);

                // Check if this effect exists on another slot
                boolean onOtherSlot = tracker.isEffectOnOtherSlot(player, effectId, slot);

                if (eff instanceof PotionEffect) {
                    // For potion effects, removeFrom() removes ALL levels of this type
                    // So we need to check if another slot has ANY level and re-apply it
                    if (handlePotionEffectRemoval(
                            player, (PotionEffect) eff, removedItem, onOtherSlot, slot)) {
                        continue;
                    }
                } else {
                    // For trail/disguise effects, only call removeFrom if not on another slot
                    if (onOtherSlot) {
                        // Effect still exists on another slot - don't remove visual
                        continue;
                    }
                }

                // Remove the visual effect
                eff.removeFrom(player, slot);
            }
        }
    }

    public void resetLoreCache() {
        loreCache.clear();
    }

    /**
     * Apply effects from an equipped item to a player.
     *
     * @param player The player equipping the item
     * @param item The item being equipped
     * @param slot The equipment slot
     */
    public void addEquipment(Player player, ItemStack item, EquipmentSlot slot) {
        List<String> lore = getLore(item);
        if (lore == null || player == null)
            return;

        for (String line : getCached(lore)) {
            for (EquipmentEffect eff : effectsTable.get(line)) {
                if (!eff.slot.test(slot)) {
                    continue;
                }

                if (!isEnabled.get(EquipmentEffect.getType(eff))) {
                    PotionArmorPlugin.plugin.logger.severe("Type not enabled: " + eff.toString());
                    continue;
                }

                // Skip if already tracked (prevents duplicate applications)
                if (tracker.isTracked(player, eff)) {
                    continue;
                }

                // Apply effect and track it with slot information
                eff.applyTo(player, slot);
                tracker.trackEffect(player, eff, slot);
            }
        }
    }

    public List<String> getCached(List<String> lore) {
        List<String> linesWithEffects = new ArrayList<String>();
        String loreKey = loreKey(lore);
        List<String> cached = loreCache.get(loreKey);
        if (cached != null) {
            linesWithEffects = cached;
        } else {
            for (String line : lore) {
                String line_strip = line.strip();
                if (!effectsTable.containsKey(line_strip))
                    continue;
                linesWithEffects.add(line_strip);
            }
            loreCache.put(loreKey, linesWithEffects);
        }
        return linesWithEffects;
    }

    private String loreKey(List<String> lore) {
        // hashing done by cache object
        return String.join(LORE_DELIM, lore);
    }

    /**
     * Remove effects from an unequipped item.
     *
     * @param player The player unequipping the item
     * @param item The item being unequipped
     * @param slot The slot the item was removed from
     */
    public void removeEquipment(Player player, ItemStack item, EquipmentSlot slot) {
        List<String> lore = getLore(item);
        if (item == null || item.getType() == Material.AIR || lore == null || player == null)
            return;
        List<String> cachedLines = getCached(lore);
        removeEffects(player, cachedLines, item, slot);
        // No need to reapply! Slot-aware tracking handles overlapping effects automatically
    }

    /**
     * Remove effects from an unequipped item when slot is unknown.
     * This searches through all equipped slots to find and remove the effects.
     * Used by events like drop where we don't know which slot the item came from.
     *
     * @param player The player unequipping the item
     * @param item The item being unequipped
     */
    public void removeEquipment(Player player, ItemStack item) {
        List<String> lore = getLore(item);
        if (item == null || item.getType() == Material.AIR || lore == null || player == null)
            return;

        // Search through all slots to remove this item's effects
        List<ItemStack> equipped = getEquippedItems(player);
        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack slotItem = equipped.get(idx);
            if (slotItem != null && slotItem.isSimilar(item)) {
                // Found the slot - remove effects from it
                List<String> cachedLines = getCached(lore);
                removeEffects(player, cachedLines, item, slots[idx]);
                return;
            }
        }

        // Item not found in any slot - it may have already been removed
        // In this case, remove from all slots (defensive)
        for (EquipmentSlot slot : slots) {
            List<String> cachedLines = getCached(lore);
            removeEffects(player, cachedLines, item, slot);
        }
    }

    public void refreshAppliedEquipment(Player player, ItemStack toExclude) {
        // this only re-applies effects from equipment, it does not remove effects
        // TODO factor this (code adapted from resetPlayerEffects)
        // Delay by 1 tick (20ms) to ensure inventory is updated
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            PlayerInventory inv = player.getInventory();
                            List<ItemStack> equipment = new ArrayList<ItemStack>();
                            equipment.addAll(
                                    Arrays.asList(
                                            inv.getArmorContents())); // in order, boots, legs,
                            // chest, helmet
                            equipment.add(inv.getItemInMainHand());
                            equipment.add(inv.getItemInOffHand());

                            for (int i = 0; i < equipment.size(); i++) {
                                ItemStack toAdd = equipment.get(i);
                                if ((toExclude != null) && (toAdd.isSimilar(toExclude))) {
                                    continue;
                                }
                                addEquipment(player, toAdd, slots[i]);
                            }
                        },
                        1L); // 1 tick = 20ms
    }

    /**
     * Replace equipment by removing old item effects and adding new item effects.
     *
     * @param player The player
     * @param _new The new item being equipped
     * @param _old The old item being unequipped
     * @param slot The equipment slot
     */
    public void replaceEquipment(
            Player player, ItemStack _new, ItemStack _old, EquipmentSlot slot) {
        if (_old != null) {
            removeEquipment(player, _old, slot);
        }
        if (_new != null) {
            addEquipment(player, _new, slot);
        }
    }

    @SuppressWarnings("deprecation")
    private static List<String> getLore(ItemStack item) {
        if (item == null)
            return null;
        if (!item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore())
            return null;
        return meta.getLore().stream()
                .map(line -> ChatColor.stripColor(line))
                .collect(Collectors.toList());
    }

    /**
     * Dump contents of cache and effectsTable to logs for debugging
     */
    public void dump() {
        plugin.logger.info(effectsTable.toString());
        plugin.logger.info(loreCache.toString());
    }

    /**
     * Calculate the set of effect IDs that SHOULD be active based on current equipment.
     * Used by the validation task to detect orphaned effects.
     */
    public Set<String> calculateExpectedEffects(Player player) {
        Set<String> expected = new HashSet<>();
        List<ItemStack> equipped = getEquippedItems(player);

        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack item = equipped.get(idx);
            if (item == null || item.getType() == Material.AIR)
                continue;

            List<String> lore = getLore(item);
            if (lore == null)
                continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(lore)) {
                List<EquipmentEffect> effects = effectsTable.get(loreline);
                if (effects == null)
                    continue;
                for (EquipmentEffect eff : effects) {
                    if (!eff.slot.test(slot))
                        continue;
                    if (!isEnabled.get(EquipmentEffect.getType(eff)))
                        continue;
                    expected.add(PlayerEffectTracker.getEffectId(eff));
                }
            }
        }
        return expected;
    }

    /**
     * Validate and fix a player's effects.
     * Removes any effects that shouldn't be active and applies missing effects.
     * Returns true if any corrections were made.
     */
    public boolean validateAndFixPlayerEffects(Player player) {
        Set<String> expected = calculateExpectedEffects(player);
        Set<String> tracked = tracker.getTrackedEffects(player);

        // Find effects that are tracked but shouldn't be (orphaned)
        Set<String> orphaned = new HashSet<>(tracked);
        orphaned.removeAll(expected);

        // Find effects that should be active but aren't tracked (missing)
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(tracked);

        // If any mismatches found, reset player effects
        if (!orphaned.isEmpty() || !missing.isEmpty()) {
            if (!orphaned.isEmpty()) {
                plugin.logger.warning(
                        "Found "
                                + orphaned.size()
                                + " orphaned effects on "
                                + player.getName()
                                + ": "
                                + orphaned);
            }
            if (!missing.isEmpty()) {
                plugin.logger.warning(
                        "Found "
                                + missing.size()
                                + " missing effects on "
                                + player.getName()
                                + ": "
                                + missing);
            }
            plugin.logger.info("Resetting effects for " + player.getName());
            resetPlayerEffects(player);
            return true;
        }

        // Validation passed
        plugin.logger.fine(
                "Validation passed for "
                        + player.getName()
                        + " - "
                        + tracked.size()
                        + " effects matched");
        return false;
    }

    /**
     * Clear tracking for a player without removing effects.
     * Used when the player quits since effects are cleared by the server anyway.
     *
     * @param player The player to clear tracking for
     */
    public void clearPlayerTracking(Player player) {
        tracker.clearPlayer(player);
    }

    public static void setSupportedEffects() {
        setSupportedEffects(new ArrayList<NamespacedKey>());
    }

    public static void setSupportedEffects(List<NamespacedKey> exclude) {
        NamespacedKey tag;
        for (PotionEffectType pe : Registry.EFFECT) {
            tag = pe.getKey();
            if (!exclude.contains(tag)) {
                supportedEffects.add(tag);
            }
        }
    }

    // THIS IS A KLUDGE BECAUSE ESSENTIALS DOES NOT THROW EVENTS
    // AND YOU CAN'T LISTEN TO PROGRAMATIC INVENTORY CHANGES :(
    public void hatCommand(Player player) {
        ItemStack oldHat = player.getInventory().getHelmet();
        ItemStack oldMain = player.getInventory().getItemInMainHand();

        // Delay by 2 ticks (40ms) to ensure inventory is updated after Essentials' /hat command
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            ItemStack newHat = player.getInventory().getHelmet();
                            ItemStack newMain = player.getInventory().getItemInMainHand();
                            if (!(newHat.isSimilar(oldHat) && newMain.isSimilar(oldMain))) {
                                removeEquipment(player, oldHat, EquipmentSlot.HEAD);
                                removeEquipment(player, oldMain, EquipmentSlot.HAND);
                                addEquipment(player, newHat, EquipmentSlot.HEAD);
                                addEquipment(player, newMain, EquipmentSlot.HAND);
                            }
                        },
                        2L); // 2 ticks = 40ms
    }
}
