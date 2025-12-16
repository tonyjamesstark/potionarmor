/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour;

import dev.esophose.playerparticles.api.PlayerParticlesAPI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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

    // loreline --> effects list
    private static Map<String, List<EquipmentEffect>> effectsTable =
            new HashMap<String, List<EquipmentEffect>>();

    // full item lore --> relevant lore lines
    // (cache built up as items processed)
    // TODO: schedule cache clears?
    private static Map<String, List<String>> loreCache = new HashMap<String, List<String>>();

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
                                if (isEnabled.get(EffectType.POTION))
                                    _p.clearActivePotionEffects(); // TODO: fix - clears other
                                // (drunk) potion effects
                                p.logger.info("checked potion effects...");
                                if (isEnabled.get(EffectType.TRAIL))
                                    PlayerParticlesAPI.getInstance().resetActivePlayerParticles(_p);
                                p.logger.info("checked particle effects...");
                                if (isEnabled.get(EffectType.DISGUISE))
                                    DisguiseAPI.undisguiseToAll((Entity) _p);
                                p.logger.info("checked disguise effects, returning...");
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

    private void removeEffects(Player _p, List<String> lines) {
        Callable<Void> task =
                () -> {
                    for (String loreLine : lines) {
                        for (EquipmentEffect eff : effectsTable.get(loreLine)) {
                            // bukkit methods must be run on main thread
                            Callable<Void> mainTask =
                                    () -> {
                                        eff.removeFrom(_p);
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

                            // bukkit methods must be run on main thread
                            Callable<Void> mainTask =
                                    () -> {
                                        eff.applyTo(_p);
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
        List<String> lore = getLore(i);
        if (i == null || i.getType() == Material.AIR || lore == null || _p == null) return;
        String key = loreKey(lore);
        if (!loreCache.containsKey(key)) return;
        removeEffects(_p, loreCache.get(key));

        // reapply any overlapping effects
        // TODO: properly find appropriate effects to apply. currently just reapplying all potion
        // effects
        ArrayList<ItemStack> equipped = new ArrayList<>();
        equipped.addAll(Arrays.asList(_p.getEquipment().getArmorContents()));
        equipped.add(_p.getInventory().getItemInMainHand());
        equipped.add(_p.getInventory().getItemInOffHand());
        for (ItemStack j : equipped) {
            List<String> _lore = getLore(j);
            if (_lore == null) {
                continue;
            }
            for (String loreline : getCached(_lore)) {
                for (EquipmentEffect eff : effectsTable.get(loreline)) {
                    if (EquipmentEffect.getType(eff) == EquipmentEffect.EffectType.POTION) {
                        eff.applyTo(_p);
                    }
                }
            }
        }
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
