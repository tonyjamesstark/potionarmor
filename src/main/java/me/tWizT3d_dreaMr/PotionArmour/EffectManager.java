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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import me.libraryaddict.disguise.DisguiseAPI;
import me.tWizT3d_dreaMr.PotionArmour.Effects.EquipmentEffect;
import me.tWizT3d_dreaMr.PotionArmour.Effects.EquipmentEffect.EffectType;
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

    // Track consecutive validation failures to prevent false positives
    private final Map<UUID, Integer> validationFailureCount = new ConcurrentHashMap<>();
    private int validationStrikesRequired = 2; // Default, configurable

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
     * Set the number of consecutive validation failures required before reset.
     */
    public void setValidationStrikesRequired(int strikes) {
        if (strikes < 1) {
            p.logger.warning("Validation strikes must be >= 1, using default of 2");
            this.validationStrikesRequired = 2;
        } else {
            this.validationStrikesRequired = strikes;
        }
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
        Callable<Void> task =
                () -> {
                    p.logger.info("Running task...");
                    PlayerInventory inv = _p.getInventory();
                    p.logger.info("adding equipment...");
                    List<ItemStack> equipment = new ArrayList<ItemStack>();
                    equipment.addAll(
                            Arrays.asList(inv.getArmorContents())); // in order, boots, legs, chest,
                    // helmet
                    equipment.add(inv.getItemInMainHand());
                    equipment.add(inv.getItemInOffHand());

                    // bukkit methods must be run on main thread
                    Callable<Void> forMain =
                            () -> {
                                p.logger.info("Running forMain...");
                                // Only remove effects that WE applied, preserving effects from
                                // other sources (drunk potions, manually added trails, etc.)
                                clearTrackedEffects(_p);
                                p.logger.info("cleared tracked effects, returning...");
                                return null;
                            };
                    p.logger.info("submitting task...");
                    Future<Void> _task =
                            Bukkit.getServer()
                                    .getScheduler()
                                    .callSyncMethod(PotionArmorPlugin.plugin, forMain);

                    p.logger.info("syncing threads...");
                    // must sync here
                    try {
                        _task.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // task failed successfully
                        p.logger.severe(
                                "Failed to reset player: " + _p.getName() + " | " + e.toString());
                        return null;
                    }

                    p.logger.info("syncing equipment effects...");
                    for (int i = 0; i < equipment.size(); i++) {
                        addEquipment(_p, equipment.get(i), slots[i]);
                    }
                    return null;
                };
        FutureTask<Void> job = new FutureTask<>(task);
        p.logger.info("submitting task...");
        p.submitAsyncTask(job);
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

    private void removeEffects(Player _p, List<String> lines) {
        Callable<Void> task =
                () -> {
                    for (String loreLine : lines) {
                        for (EquipmentEffect eff : effectsTable.get(loreLine)) {
                            // Only remove if we tracked this effect
                            if (!tracker.isTracked(_p, eff)) {
                                continue;
                            }
                            // bukkit methods must be run on main thread
                            Callable<Void> mainTask =
                                    () -> {
                                        eff.removeFrom(_p);
                                        tracker.untrackEffect(_p, eff);
                                        return null;
                                    };
                            Bukkit.getServer()
                                    .getScheduler()
                                    .callSyncMethod(PotionArmorPlugin.plugin, mainTask);
                        }
                    }
                    return null;
                };
        FutureTask<Void> job = new FutureTask<Void>(task);
        p.submitAsyncTask(job);
    }

    public void resetLoreCache() {
        loreCache.clear();
    }

    public void addEquipment(Player _p, ItemStack i, EquipmentSlot slot) {
        addEquipment(_p, i, slot, true);
    }

    private void addEquipment(Player _p, ItemStack i, EquipmentSlot slot, boolean apply) {
        tracker.markEquipmentChange(_p);
        validationFailureCount.put(_p.getUniqueId(), 0);

        List<String> lore = getLore(i);
        if (lore == null || _p == null) return;
        Callable<Void> task =
                () -> {
                    for (String line : getCached(lore)) {
                        for (EquipmentEffect eff : effectsTable.get(line)) {
                            if (!eff.slot.test(slot)) { // could probably move to outer loop
                                continue;
                            }

                            if (!isEnabled.get(EquipmentEffect.getType(eff))) {
                                PotionArmorPlugin.plugin.logger.severe(
                                        "Type not enabled: " + eff.toString());
                                continue;
                            }

                            // Skip if already tracked (prevents duplicate applications)
                            if (tracker.isTracked(_p, eff)) {
                                continue;
                            }

                            // bukkit methods must be run on main thread
                            Callable<Void> mainTask =
                                    () -> {
                                        eff.applyTo(_p);
                                        // Track that we applied this effect
                                        tracker.trackEffect(_p, eff);
                                        return null;
                                    };
                            Bukkit.getServer()
                                    .getScheduler()
                                    .callSyncMethod(PotionArmorPlugin.plugin, mainTask);
                        }
                    }
                    return null;
                };
        FutureTask<Void> job = new FutureTask<Void>(task);
        p.submitAsyncTask(job);
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
        tracker.markEquipmentChange(_p);
        validationFailureCount.put(_p.getUniqueId(), 0);

        List<String> lore = getLore(i);
        if (i == null || i.getType() == Material.AIR || lore == null || _p == null) return;
        String key = loreKey(lore);
        if (!loreCache.containsKey(key)) return;
        removeEffects(_p, loreCache.get(key));

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
        Callable<Void> task =
                () -> {
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

                                // Skip if already tracked (prevents duplicates)
                                if (tracker.isTracked(_p, eff)) continue;

                                // Apply on main thread
                                Callable<Void> mainTask =
                                        () -> {
                                            eff.applyTo(_p);
                                            tracker.trackEffect(_p, eff);
                                            return null;
                                        };
                                Bukkit.getServer()
                                        .getScheduler()
                                        .callSyncMethod(PotionArmorPlugin.plugin, mainTask);
                            }
                        }
                    }
                    return null;
                };
        FutureTask<Void> job = new FutureTask<Void>(task);
        p.submitAsyncTask(job);
    }

    public void refreshAppliedEquipment(Player _p, ItemStack toExclude) {
        // this only re-applies effects from equipment, it does not remove effects
        // TODO factor this (code adapted from resetPlayerEffects)
        Callable<Void> task =
                () -> {
                    PlayerInventory inv = _p.getInventory();
                    List<ItemStack> equipment = new ArrayList<ItemStack>();
                    equipment.addAll(
                            Arrays.asList(inv.getArmorContents())); // in order, boots, legs, chest,
                    // helmet
                    equipment.add(inv.getItemInMainHand());
                    equipment.add(inv.getItemInOffHand());

                    for (int i = 0; i < equipment.size(); i++) {
                        ItemStack toAdd = equipment.get(i);
                        if ((toExclude != null) && (toAdd.isSimilar(toExclude))) {
                            continue;
                        }
                        addEquipment(_p, toAdd, slots[i]);
                    }
                    return null;
                };
        FutureTask<Void> job = new FutureTask<>(task);
        p.submitAsyncTaskLater(job, 20, TimeUnit.MILLISECONDS);
    }

    public void replaceEquipment(Player _p, ItemStack _new, ItemStack _old, EquipmentSlot slot) {
        tracker.markEquipmentChange(_p);
        validationFailureCount.put(_p.getUniqueId(), 0);

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
        // Skip validation if equipment changed recently (cooldown period)
        long timeSinceChange = tracker.getTimeSinceLastChange(_p);
        if (timeSinceChange < tracker.getValidationCooldownMs()) {
            p.logger.fine(
                    "Skipping validation for "
                            + _p.getName()
                            + " - equipment changed "
                            + timeSinceChange
                            + "ms ago (within cooldown)");
            return false;
        }

        Set<String> expected = calculateExpectedEffects(_p);
        Set<String> tracked = tracker.getTrackedEffects(_p);
        boolean corrected = false;

        UUID uuid = _p.getUniqueId();
        int currentStrikes = validationFailureCount.getOrDefault(uuid, 0);

        // Find effects that are tracked but shouldn't be (orphaned)
        Set<String> orphaned = new HashSet<>(tracked);
        orphaned.removeAll(expected);

        if (!orphaned.isEmpty()) {
            currentStrikes++;
            validationFailureCount.put(uuid, currentStrikes);

            p.logger.warning(
                    "Found "
                            + orphaned.size()
                            + " orphaned effects on "
                            + _p.getName()
                            + " (strike "
                            + currentStrikes
                            + "/"
                            + validationStrikesRequired
                            + "): "
                            + orphaned);

            if (currentStrikes >= validationStrikesRequired) {
                p.logger.warning(
                        "Validation strike threshold reached for "
                                + _p.getName()
                                + " - triggering effect reset");
                resetPlayerEffects(_p);
                validationFailureCount.put(uuid, 0); // Reset after correction
                corrected = true;
            }
            return corrected;
        }

        // Find effects that should be active but aren't tracked
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(tracked);

        if (!missing.isEmpty()) {
            currentStrikes++;
            validationFailureCount.put(uuid, currentStrikes);

            p.logger.warning(
                    "Found "
                            + missing.size()
                            + " missing effects on "
                            + _p.getName()
                            + " (strike "
                            + currentStrikes
                            + "/"
                            + validationStrikesRequired
                            + "): "
                            + missing);

            if (currentStrikes >= validationStrikesRequired) {
                p.logger.warning(
                        "Validation strike threshold reached for "
                                + _p.getName()
                                + " - triggering effect reapply");
                reapplyEffectsFromEquipment(_p);
                validationFailureCount.put(uuid, 0); // Reset after correction
                corrected = true;
            }
            return corrected;
        }

        // Validation passed - reset strike counter
        if (currentStrikes > 0) {
            p.logger.fine(
                    "Validation passed for "
                            + _p.getName()
                            + " - resetting strike counter (was "
                            + currentStrikes
                            + ")");
            validationFailureCount.put(uuid, 0);
        }

        return corrected;
    }

    /**
     * Clear tracking for a player without removing effects.
     * Used when the player quits (effects are cleared by the server anyway).
     */
    public void clearPlayerTracking(Player _p) {
        tracker.clearPlayer(_p);
        validationFailureCount.remove(_p.getUniqueId());
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
        Callable<Void> task =
                () -> {
                    ItemStack newHat = _p.getInventory().getHelmet();
                    ItemStack newMain = _p.getInventory().getItemInMainHand();
                    if (!(newHat.isSimilar(oldHat) && newMain.isSimilar(oldMain))) {
                        // WHY IS THIS ERRORING?
                        // ADDEQUIPMENT() SHOULD CALL MAIN THREAD
                        // bukkit methods must be run on main thread
                        Callable<Void> mainTask =
                                () -> {
                                    removeEquipment(_p, oldHat);
                                    removeEquipment(_p, oldMain);
                                    addEquipment(_p, newHat, EquipmentSlot.HEAD);
                                    addEquipment(_p, newMain, EquipmentSlot.HAND);
                                    return null;
                                };
                        Bukkit.getServer()
                                .getScheduler()
                                .callSyncMethod(PotionArmorPlugin.plugin, mainTask);
                    }
                    return null;
                };
        FutureTask<Void> job = new FutureTask<Void>(task);
        p.submitAsyncTaskLater(job, 40, TimeUnit.MILLISECONDS);
    }
}
