/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour.Effects;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffectType;

public class PotionEffect extends EquipmentEffect {

    public static final int MAX_DURATION = 2147000;

    org.bukkit.potion.PotionEffect effect;
    int level = 0;
    String str = "";
    EquipmentEffect.EffectType type = EffectType.POTION;

    public PotionEffect() {
        this(null, null, 0);
    }

    public PotionEffect(EquipmentSlotGroup slot, PotionEffectType eff, int _level) {
        this.slot = slot;
        effect = eff.createEffect(MAX_DURATION, _level).withDuration(MAX_DURATION);
        // for some reason saturation constructor overrides durration...this is a bit of a hack
        level = _level;
        str = "CE " + eff.toString() + ":" + _level;
    }

    @Override
    public int compareTo(EquipmentEffect o) {
        // in order, rank by: equipment effect type, potion effect type, level
        if (!(o instanceof PotionEffect)) {
            return this.type.compareTo(o.type);
        }
        PotionEffect cast = (PotionEffect) o;
        int strcmp = this.effect.getType().toString().compareTo(cast.effect.getType().toString());
        if (strcmp != 0) {
            return strcmp;
        }
        return Integer.compare(this.level, cast.level);
    }

    @Override
    public boolean applyTo(LivingEntity p) {
        return p.addPotionEffect(effect);
    }

    @Override
    public String toString() {
        return this.str;
    }

    /**
     * Get the underlying Bukkit PotionEffect.
     */
    public org.bukkit.potion.PotionEffect getEffect() {
        return this.effect;
    }

    /**
     * Get the potion effect type.
     */
    public org.bukkit.potion.PotionEffectType getEffectType() {
        return this.effect.getType();
    }

    /**
     * Get the potion type key (without level) for comparison.
     * E.g., "minecraft:speed"
     */
    public String getPotionTypeKey() {
        return this.effect.getType().getKey().toString();
    }

    /**
     * Get the amplifier level (0-indexed: 0 = Level I, 1 = Level II, etc.)
     */
    public int getLevel() {
        return this.level;
    }

    public static PotionEffect fromConfig(EquipmentSlotGroup slot, ConfigurationSection s) {
        NamespacedKey key = NamespacedKey.fromString(s.getString("effect"));
        return new PotionEffect(slot, Registry.EFFECT.get(key), s.getInt("level", 0) - 1);
        // potion "level" starts at 0 (corresponds)
    }

    @Override
    public boolean removeFrom(LivingEntity p) {
        // org.bukkit.potion.PotionEffect active =
        // p.getPotionEffect(this.effect.getType());
        // leave stronger or longer effects alone

        // TODO: fix behavior that drinking stronger potion will leave persistent long
        // duration potion effect

        // if (compareEffectsIgnoreDuration(active, this.effect)) {
        // p.removePotionEffect(this.effect.getType());
        // }

        // TODO: remove only specific potion effect, rather than all effects of same
        // class
        p.removePotionEffect(this.effect.getType());
        return true;
    }

    private boolean equalsEffectIgnoreDuration(
            org.bukkit.potion.PotionEffect one, org.bukkit.potion.PotionEffect two) {
        return (one.getAmplifier() == two.getAmplifier()) && (one.getType() == two.getType());
    }
}
