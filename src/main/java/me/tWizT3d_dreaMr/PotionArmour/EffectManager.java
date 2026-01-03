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
    PotionArmorPlugin p;
    // private PlayerParticlesAPI ppAPI;
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
    // TODO: schedule cache clears?
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
    public String getValidationDebugInfo(Player _p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Debug Info for ").append(_p.getName()).append(":\n");

        // Effects
        Set<String> tracked = tracker.getTrackedEffects(_p);
        Set<String> expected = calculateExpectedEffects(_p);

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

    public EffectManager(PotionArmorPlugin _p) {
        p = _p;

        for (EffectType t : EffectType.values()) {
            isEnabled.put(t, true);
        }
        isEnabled.put(EffectType.BASE, false); // base type abstract, cannot enable

        if (!Bukkit.getPluginManager().isPluginEnabled("PlayerParticles")) {
            // ppAPI = PlayerParticlesAPI.getInstance();
            // } else {
            p.logger.warning("PlayerParticles is not loaded, trail support will be disabled.");
            isEnabled.put(EffectType.TRAIL, false);
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("libsdisguises")) {
            p.logger.warning("libsdisguises is not loaded, disguise support will be disabled.");
            isEnabled.put(EffectType.DISGUISE, false);
        }
    }

    public int loadEffects(FileConfiguration cfg) {
        for (EffectType t : isEnabled.keySet()) {
            if (t != EffectType.BASE && !isEnabled.get(t)) {
                p.logger.info(
                        "Effect type "
                                + t.toString()
                                + " not enabled, skipping these effects in config");
            }
        }
        Map<String, List<EquipmentEffect>> loaded =
                EquipmentEffect.effectsFromConfig(cfg, p.logger);
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

    public void resetPlayerEffects(Player _p) {
        p.logger.info("Resetting player effects for " + _p.getName());
        PlayerInventory inv = _p.getInventory();
        List<ItemStack> equipment = new ArrayList<ItemStack>();
        equipment.addAll(
                Arrays.asList(inv.getArmorContents())); // in order, boots, legs, chest, helmet
        equipment.add(inv.getItemInMainHand());
        equipment.add(inv.getItemInOffHand());

        // Only remove effects that WE applied, preserving effects from
        // other sources (drunk potions, manually added trails, etc.)
        clearTrackedEffects(_p);

        // Re-apply equipment effects
        for (int i = 0; i < equipment.size(); i++) {
            addEquipment(_p, equipment.get(i), slots[i]);
        }
    }

    /**
     * Clear only the effects that this plugin has applied to the player.
     * This preserves effects from other sources like drunk potions or manually added trails.
     */
    private void clearTrackedEffects(Player _p) {
        // Get tracked potion types and remove only those
        if (isEnabled.get(EffectType.POTION)) {
            Set<String> trackedPotions = tracker.getTrackedPotionTypes(_p);
            for (org.bukkit.potion.PotionEffect active : _p.getActivePotionEffects()) {
                String potionKey = "CE " + active.getType().toString();
                if (trackedPotions.contains(potionKey)) {
                    _p.removePotionEffect(active.getType());
                }
            }
        }

        // Clear tracked trails
        if (isEnabled.get(EffectType.TRAIL)) {
            // PlayerParticles API doesn't have fine-grained removal, but we track what we added
            // We need to remove only the particles we added
            // Unfortunately the API removes all particles of a type, so we'll clear and re-add
            // non-plugin particles aren't tracked by us anyway
            Set<String> trackedTrails = tracker.getTrackedTrails(_p);
            if (!trackedTrails.isEmpty()) {
                // We have to use the API's reset since we can't selectively remove
                // But this only affects PlayerParticles-managed particles for this player
                PlayerParticlesAPI.getInstance().resetActivePlayerParticles(_p);
            }
        }

        // Clear tracked disguise
        if (isEnabled.get(EffectType.DISGUISE)) {
            if (tracker.hasTrackedDisguise(_p)) {
                DisguiseAPI.undisguiseToAll((Entity) _p);
            }
        }

        // Clear the tracking data
        tracker.clearPlayer(_p);
    }

    /**
     * Find the highest level of a potion effect type that remains on equipped items
     * (excluding the item being removed).
     *
     * @param _p The player
     * @param potionTypeKey The potion type key (e.g., "minecraft:speed")
     * @param excludingItem The item being removed (to exclude from check)
     * @return The highest remaining level (-1 if none found)
     */
    private int getHighestRemainingLevel(Player _p, String potionTypeKey, ItemStack excludingItem) {
        int highestLevel = -1;

        ArrayList<ItemStack> equipped = new ArrayList<>();
        equipped.addAll(Arrays.asList(_p.getEquipment().getArmorContents()));
        equipped.add(_p.getInventory().getItemInMainHand());
        equipped.add(_p.getInventory().getItemInOffHand());

        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack item = equipped.get(idx);
            if (item == null || item.getType() == Material.AIR) continue;

            // Skip the item being removed
            if (excludingItem != null && item.isSimilar(excludingItem)) continue;

            List<String> lore = getLore(item);
            if (lore == null) continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(lore)) {
                if (!effectsTable.containsKey(loreline)) continue;
                for (EquipmentEffect eff : effectsTable.get(loreline)) {
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

    private void removeEffects(Player _p, List<String> lines, ItemStack removedItem) {
        for (String loreLine : lines) {
            for (EquipmentEffect eff : effectsTable.get(loreLine)) {
                // Only remove if we tracked this effect
                if (!tracker.isTracked(_p, eff)) {
                    continue;
                }

                // For potion effects, handle level overlapping
                if (eff instanceof PotionEffect) {
                    PotionEffect pe = (PotionEffect) eff;
                    int highestRemaining =
                            getHighestRemainingLevel(_p, pe.getPotionTypeKey(), removedItem);

                    if (highestRemaining >= pe.getLevel()) {
                        // A higher or equal level will remain - skip actual removal
                        // Just untrack this specific effect
                        tracker.untrackEffect(_p, eff);
                        continue;
                    } else if (highestRemaining > 0) {
                        // Removing a higher level effect, but a lower level remains
                        // Remove the current effect and re-apply the lower level
                        eff.removeFrom(_p);
                        tracker.untrackEffect(_p, eff);

                        // Re-apply the highest remaining level from other equipment
                        // Convert the potion type key back to PotionEffectType
                        NamespacedKey key = NamespacedKey.fromString(pe.getPotionTypeKey());
                        PotionEffectType effectType = Registry.EFFECT.get(key);

                        PotionEffect lowerEffect =
                                new PotionEffect(pe.slot, effectType, highestRemaining);
                        lowerEffect.applyTo(_p);
                        tracker.trackEffect(_p, lowerEffect);
                        continue;
                    }
                }

                // Remove effect and untrack it
                eff.removeFrom(_p);
                tracker.untrackEffect(_p, eff);
            }
        }
    }

    public void resetLoreCache() {
        loreCache.clear();
    }

    public void addEquipment(Player _p, ItemStack i, EquipmentSlot slot) {
        addEquipment(_p, i, slot, true);
    }

    private void addEquipment(Player _p, ItemStack i, EquipmentSlot slot, boolean apply) {
        List<String> lore = getLore(i);
        if (lore == null || _p == null) return;

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
                if (tracker.isTracked(_p, eff)) {
                    continue;
                }

                // Apply effect and track it
                eff.applyTo(_p);
                tracker.trackEffect(_p, eff);
            }
        }
    }

    public List<String> getCached(List<String> lore) {
        List<String> linesWithEffects = new ArrayList<String>();
        String loreKey = loreKey(lore);
        if (loreCache.containsKey(loreKey)) {
            linesWithEffects = loreCache.get(loreKey);
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

    public void removeEquipment(Player _p, ItemStack i) {
        removeEquipment(_p, i, true);
    }

    private void removeEquipment(Player _p, ItemStack i, boolean apply) {
        List<String> lore = getLore(i);
        if (i == null || i.getType() == Material.AIR || lore == null || _p == null) return;
        String key = loreKey(lore);
        if (!loreCache.containsKey(key)) return;
        removeEffects(_p, loreCache.get(key), i);

        // Re-apply ALL effect types from remaining equipment
        // This fixes the issue where trails and disguises were not being re-applied
        // when removing one piece of equipment that shared effects with another
        reapplyEffectsFromEquipment(_p);
    }

    /**
     * Re-apply all effects from currently equipped items.
     * This is called after removing equipment to ensure overlapping effects are restored.
     * The tracker prevents duplicate applications.
     */
    private void reapplyEffectsFromEquipment(Player _p) {
        ArrayList<ItemStack> equipped = new ArrayList<>();
        equipped.addAll(Arrays.asList(_p.getEquipment().getArmorContents()));
        equipped.add(_p.getInventory().getItemInMainHand());
        equipped.add(_p.getInventory().getItemInOffHand());

        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack j = equipped.get(idx);
            if (j == null || j.getType() == Material.AIR) continue;

            List<String> _lore = getLore(j);
            if (_lore == null) continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(_lore)) {
                for (EquipmentEffect eff : effectsTable.get(loreline)) {
                    if (!eff.slot.test(slot)) continue;
                    if (!isEnabled.get(EquipmentEffect.getType(eff))) continue;

                    // Always apply - Bukkit's addPotionEffect() handles duplicates
                    // correctly (keeps higher/longer effects). trackEffect() uses
                    // a Set so duplicate tracking calls are safe.
                    eff.applyTo(_p);
                    tracker.trackEffect(_p, eff);
                }
            }
        }
    }

    public void refreshAppliedEquipment(Player _p, ItemStack toExclude) {
        // this only re-applies effects from equipment, it does not remove effects
        // TODO factor this (code adapted from resetPlayerEffects)
        // Delay by 1 tick (20ms) to ensure inventory is updated
        Bukkit.getScheduler()
                .runTaskLater(
                        p,
                        () -> {
                            PlayerInventory inv = _p.getInventory();
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
                                addEquipment(_p, toAdd, slots[i]);
                            }
                        },
                        1L); // 1 tick = 20ms
    }

    public void replaceEquipment(Player _p, ItemStack _new, ItemStack _old, EquipmentSlot slot) {
        // TODO: figure out if bugs when new and old have overlapping effects
        if (_old != null) {
            removeEquipment(_p, _old);
        }
        if (_new != null) {
            addEquipment(_p, _new, slot);
        }
    }

    @SuppressWarnings("deprecation")
    private static List<String> getLore(ItemStack i) {
        if (i == null) return null;
        if (!i.hasItemMeta()) return null;
        ItemMeta meta = i.getItemMeta();
        if (!meta.hasLore()) return null;
        return meta.getLore().stream()
                .map(line -> ChatColor.stripColor(line))
                .collect(Collectors.toList());
    }

    /**
     * Dump contents of cache and effectsTable to logs for debugging
     */
    public void dump() {
        p.logger.info(effectsTable.toString());
        p.logger.info(loreCache.toString());
    }

    /**
     * Calculate the set of effect IDs that SHOULD be active based on current equipment.
     * Used by the validation task to detect orphaned effects.
     */
    public Set<String> calculateExpectedEffects(Player _p) {
        Set<String> expected = new HashSet<>();

        ArrayList<ItemStack> equipped = new ArrayList<>();
        equipped.addAll(Arrays.asList(_p.getEquipment().getArmorContents()));
        equipped.add(_p.getInventory().getItemInMainHand());
        equipped.add(_p.getInventory().getItemInOffHand());

        for (int idx = 0; idx < equipped.size(); idx++) {
            ItemStack item = equipped.get(idx);
            if (item == null || item.getType() == Material.AIR) continue;

            List<String> lore = getLore(item);
            if (lore == null) continue;

            EquipmentSlot slot = slots[idx];

            for (String loreline : getCached(lore)) {
                if (!effectsTable.containsKey(loreline)) continue;
                for (EquipmentEffect eff : effectsTable.get(loreline)) {
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
    public boolean validateAndFixPlayerEffects(Player _p) {
        Set<String> expected = calculateExpectedEffects(_p);
        Set<String> tracked = tracker.getTrackedEffects(_p);

        // Find effects that are tracked but shouldn't be (orphaned)
        Set<String> orphaned = new HashSet<>(tracked);
        orphaned.removeAll(expected);

        // Find effects that should be active but aren't tracked (missing)
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(tracked);

        // If any mismatches found, reset player effects
        if (!orphaned.isEmpty() || !missing.isEmpty()) {
            if (!orphaned.isEmpty()) {
                p.logger.warning(
                        "Found "
                                + orphaned.size()
                                + " orphaned effects on "
                                + _p.getName()
                                + ": "
                                + orphaned);
            }
            if (!missing.isEmpty()) {
                p.logger.warning(
                        "Found "
                                + missing.size()
                                + " missing effects on "
                                + _p.getName()
                                + ": "
                                + missing);
            }
            p.logger.info("Resetting effects for " + _p.getName());
            resetPlayerEffects(_p);
            return true;
        }

        // Validation passed
        p.logger.fine(
                "Validation passed for "
                        + _p.getName()
                        + " - "
                        + tracked.size()
                        + " effects matched");
        return false;
    }

    /**
     * Clear tracking for a player without removing effects.
     * Used when the player quits (effects are cleared by the server anyway).
     */
    public void clearPlayerTracking(Player _p) {
        tracker.clearPlayer(_p);
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
    public void hatCommand(Player _p) {
        ItemStack oldHat = _p.getInventory().getHelmet();
        ItemStack oldMain = _p.getInventory().getItemInMainHand();

        // Delay by 2 ticks (40ms) to ensure inventory is updated after Essentials' /hat command
        Bukkit.getScheduler()
                .runTaskLater(
                        p,
                        () -> {
                            ItemStack newHat = _p.getInventory().getHelmet();
                            ItemStack newMain = _p.getInventory().getItemInMainHand();
                            if (!(newHat.isSimilar(oldHat) && newMain.isSimilar(oldMain))) {
                                removeEquipment(_p, oldHat);
                                removeEquipment(_p, oldMain);
                                addEquipment(_p, newHat, EquipmentSlot.HEAD);
                                addEquipment(_p, newMain, EquipmentSlot.HAND);
                            }
                        },
                        2L); // 2 ticks = 40ms
    }
}
