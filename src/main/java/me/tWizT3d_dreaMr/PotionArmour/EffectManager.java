/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour;

import dev.esophose.playerparticles.api.PlayerParticlesAPI;
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
     * Find the highest level of a potion effect type that remains on equipped items
     * (excluding the item being removed).
     *
     * @param player The player
     * @param potionTypeKey The potion type key (e.g., "minecraft:speed")
     * @param excludingItem The item being removed (to exclude from check)
     * @return The highest remaining level (-1 if none found)
     */
    private int getHighestRemainingLevel(
            Player player, String potionTypeKey, ItemStack excludingItem) {
        int highestLevel = -1;
        List<ItemStack> equipped = getEquippedItems(player);

        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack item = equipped.get(idx);
            if (item == null || item.getType() == Material.AIR) continue;

            // Skip the item being removed
            if (excludingItem != null && item.isSimilar(excludingItem)) continue;

            List<String> lore = getLore(item);
            if (lore == null) continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(lore)) {
                List<EquipmentEffect> effects = effectsTable.get(loreline);
                if (effects == null) continue;
                for (EquipmentEffect eff : effects) {
                    if (!eff.slot.test(slot)) continue;
                    if (!(eff instanceof PotionEffect)) continue;

                    PotionEffect pe = (PotionEffect) eff;
                    if (pe.getPotionTypeKey().equals(potionTypeKey)) {
                        highestLevel = Math.max(highestLevel, pe.getLevel());
                    }
                }
            }
        }
        return highestLevel;
    }

    /**
     * Apply a potion effect at a specific level.
     *
     * @param player The player to apply the effect to
     * @param baseEffect The base potion effect (for slot and type information)
     * @param level The level to apply
     */
    private void applyPotionEffectAtLevel(Player player, PotionEffect baseEffect, int level) {
        NamespacedKey key = NamespacedKey.fromString(baseEffect.getPotionTypeKey());
        PotionEffectType effectType = Registry.EFFECT.get(key);
        PotionEffect lowerEffect = new PotionEffect(baseEffect.slot, effectType, level);
        lowerEffect.applyTo(player);
        tracker.trackEffect(player, lowerEffect);
    }

    /**
     * Handle removal of a potion effect, considering level overlaps from other equipment.
     *
     * @param player The player
     * @param effect The effect being removed
     * @param removedItem The item being removed (excluded from overlap check)
     * @return true if effect was handled (removed or downgraded), false if should fall through
     */
    private boolean handlePotionEffectRemoval(
            Player player, PotionEffect effect, ItemStack removedItem) {
        int highestRemaining =
                getHighestRemainingLevel(player, effect.getPotionTypeKey(), removedItem);

        if (highestRemaining >= effect.getLevel()) {
            // Higher/equal level remains - just untrack
            tracker.untrackEffect(player, effect);
            return true;
        } else if (highestRemaining > 0) {
            // Lower level remains - remove current and re-apply lower
            effect.removeFrom(player);
            tracker.untrackEffect(player, effect);
            applyPotionEffectAtLevel(player, effect, highestRemaining);
            return true;
        }

        return false; // No overlap, fall through to normal removal
    }

    private void removeEffects(Player player, List<String> lines, ItemStack removedItem) {
        for (String loreLine : lines) {
            for (EquipmentEffect eff : effectsTable.get(loreLine)) {
                // Only remove if we tracked this effect
                if (!tracker.isTracked(player, eff)) {
                    continue;
                }

                // For potion effects, handle level overlapping
                if (eff instanceof PotionEffect) {
                    if (handlePotionEffectRemoval(player, (PotionEffect) eff, removedItem)) {
                        continue;
                    }
                }

                // Remove effect and untrack it
                eff.removeFrom(player);
                tracker.untrackEffect(player, eff);
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
        if (lore == null || player == null) return;

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

                // Apply effect and track it
                eff.applyTo(player);
                tracker.trackEffect(player, eff);
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
                if (!effectsTable.containsKey(line_strip)) continue;
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
     */
    public void removeEquipment(Player player, ItemStack item) {
        List<String> lore = getLore(item);
        if (item == null || item.getType() == Material.AIR || lore == null || player == null)
            return;
        List<String> cachedLines = getCached(lore);
        removeEffects(player, cachedLines, item);

        // Re-apply ALL effect types from remaining equipment
        // This fixes the issue where trails and disguises were not being re-applied
        // when removing one piece of equipment that shared effects with another
        // Pass the removed item to exclude it (important for events that fire before inventory
        // updates)
        reapplyEffectsFromEquipment(player, item);
    }

    /**
     * Re-apply all effects from currently equipped items.
     * This is called after removing equipment to ensure overlapping effects are restored.
     * The tracker prevents duplicate applications.
     *
     * @param player The player
     * @param excludeItem Item to exclude (the one being removed, may still be in inventory during event)
     */
    private void reapplyEffectsFromEquipment(Player player, ItemStack excludeItem) {
        List<ItemStack> equipped = getEquippedItems(player);

        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack currentItem = equipped.get(idx);
            if (currentItem == null || currentItem.getType() == Material.AIR) continue;
            // Skip the item being removed (it may still be in the slot during event processing)
            if (excludeItem != null && currentItem.isSimilar(excludeItem)) continue;

            List<String> _lore = getLore(currentItem);
            if (_lore == null) continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(_lore)) {
                for (EquipmentEffect eff : effectsTable.get(loreline)) {
                    if (!eff.slot.test(slot)) continue;
                    if (!isEnabled.get(EquipmentEffect.getType(eff))) continue;

                    // Always apply - Bukkit's addPotionEffect() handles duplicates
                    // correctly (keeps higher/longer effects). trackEffect() uses
                    // a Set so duplicate tracking calls are safe.
                    eff.applyTo(player);
                    tracker.trackEffect(player, eff);
                }
            }
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
            removeEquipment(player, _old);
        }
        if (_new != null) {
            addEquipment(player, _new, slot);
        }
    }

    @SuppressWarnings("deprecation")
    private static List<String> getLore(ItemStack item) {
        if (item == null) return null;
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return null;
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
            if (item == null || item.getType() == Material.AIR) continue;

            List<String> lore = getLore(item);
            if (lore == null) continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(lore)) {
                List<EquipmentEffect> effects = effectsTable.get(loreline);
                if (effects == null) continue;
                for (EquipmentEffect eff : effects) {
                    if (!eff.slot.test(slot)) continue;
                    if (!isEnabled.get(EquipmentEffect.getType(eff))) continue;
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
                                removeEquipment(player, oldHat);
                                removeEquipment(player, oldMain);
                                addEquipment(player, newHat, EquipmentSlot.HEAD);
                                addEquipment(player, newMain, EquipmentSlot.HAND);
                            }
                        },
                        2L); // 2 ticks = 40ms
    }
}
