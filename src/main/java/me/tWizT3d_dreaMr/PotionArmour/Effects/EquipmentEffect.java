/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour.Effects;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import me.tWizT3d_dreaMr.PotionArmour.EffectManager;
import me.tWizT3d_dreaMr.PotionArmour.PotionArmorPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;

public abstract class EquipmentEffect implements Comparable<EquipmentEffect>, Cloneable {

    EquipmentEffect.EffectType type = EffectType.BASE;

    public EquipmentSlotGroup slot = EquipmentSlotGroup.ANY;

    public abstract boolean applyTo(LivingEntity p, EquipmentSlot slot);

    public abstract boolean removeFrom(LivingEntity p, EquipmentSlot slot);

    public abstract String toString();

    public static EquipmentEffect parseEffect(ConfigurationSection s, EquipmentSlotGroup slot)
            throws DisabledEffectTypeException {
        if (!s.getBoolean("enable")) {
            return null; // this check should be redundant
        }
        switch (s.getString("type")) {
            case "trail":
                if (!EffectManager.isEnabled.get(EffectType.TRAIL))
                    throw new DisabledEffectTypeException();
                return TrailEffect.fromConfig(slot, s);
            case "effect":
                if (!EffectManager.isEnabled.get(EffectType.POTION)
                        || !EffectManager.supportedEffects.contains(
                                NamespacedKey.fromString(s.getString("effect"))))
                    throw new DisabledEffectTypeException();
                return PotionEffect.fromConfig(slot, s);
            case "disguise":
                if (!EffectManager.isEnabled.get(EffectType.DISGUISE))
                    throw new DisabledEffectTypeException();
                return DisguiseEffect.fromConfig(slot, s);
            default:
                return null;
        }
    }

    public static List<EquipmentEffect> parseEffectList(ConfigurationSection s, Logger logger) {
        List<EquipmentEffect> effects = new ArrayList<EquipmentEffect>();
        EquipmentSlotGroup slot = EquipmentSlotGroup.getByName(s.getString("slot").toUpperCase());
        Map<String, Object> effectsMap = s.getValues(false); // shallow lookup of section keys
        for (String effectName : effectsMap.keySet()) {
            if (effectName.equals("loreline") || effectName.equals("slot")) {
                continue; // ignore non-effect list entries
            }
            ConfigurationSection effectSection =
                    ((ConfigurationSection) effectsMap.get(effectName));
            if (!effectSection.getBoolean("enable")) {
                continue;
            }
            EquipmentEffect e = null;
            try {
                e = EquipmentEffect.parseEffect(effectSection, slot);
            } catch (DisabledEffectTypeException err) {
                PotionArmorPlugin.plugin.logger.finest(
                        "Skipped loading effect, type disabled. " + effectSection.toString());
                continue;
            }
            if (e == null) {
                logger.severe("Malformed effect in config: " + effectName);
                continue;
            }
            effects.add(e);
        }
        return effects;
    }

    // populate effectTable from config file
    // Supports nested sections for organization (e.g., Effects.weapons.sword1)
    public static Map<String, List<EquipmentEffect>> effectsFromConfig(
            FileConfiguration file, Logger logger) {
        Map<String, List<EquipmentEffect>> effectsTable =
                new HashMap<String, List<EquipmentEffect>>();
        ConfigurationSection effectsSection = file.getConfigurationSection("Effects");
        if (effectsSection == null) {
            return effectsTable;
        }

        // Use a stack for iterative traversal (avoid recursion)
        Deque<ConfigurationSection> stack = new ArrayDeque<>();
        stack.push(effectsSection);

        while (!stack.isEmpty()) {
            ConfigurationSection current = stack.pop();

            for (String key : current.getKeys(false)) {
                ConfigurationSection entry = current.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }

                // Check if this is an item (has loreline) or a section (contains sub-items)
                if (entry.contains("loreline")) {
                    // This is an item definition - process it
                    String loreline = entry.getString("loreline");
                    List<EquipmentEffect> effects = EquipmentEffect.parseEffectList(entry, logger);
                    if (effectsTable.containsKey(loreline)) {
                        effectsTable.get(loreline).addAll(effects);
                    } else {
                        effectsTable.put(loreline, effects);
                    }
                } else {
                    // This is a section - add to stack for processing
                    stack.push(entry);
                }
            }
        }

        return effectsTable;
    }

    public static EffectType getType(EquipmentEffect e) {
        if (e instanceof PotionEffect) {
            return EffectType.POTION;
        } else if (e instanceof TrailEffect) {
            return EffectType.TRAIL;
        } else if (e instanceof DisguiseEffect) {
            return EffectType.DISGUISE;
        } else {
            return EffectType.BASE;
        }
    }

    public static enum EffectType { // used for sorting subclasses
        BASE,
        POTION,
        TRAIL,
        DISGUISE
    }

    public static class DisabledEffectTypeException extends Exception {
        public DisabledEffectTypeException() {
            this("Disabled effect type");
        }

        public DisabledEffectTypeException(String message) {
            super(message);
        }
    }
}
