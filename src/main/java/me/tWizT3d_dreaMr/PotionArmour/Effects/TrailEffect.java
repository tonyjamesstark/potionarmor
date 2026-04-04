/* (C)2025 */
package me.tWizT3d_dreaMr.PotionArmour.Effects;

import dev.esophose.playerparticles.api.PlayerParticlesAPI;
import dev.esophose.playerparticles.particles.ParticleEffect;
import dev.esophose.playerparticles.particles.ParticlePair;
import dev.esophose.playerparticles.particles.data.ColorTransition;
import dev.esophose.playerparticles.particles.data.NoteColor;
import dev.esophose.playerparticles.particles.data.OrdinaryColor;
import dev.esophose.playerparticles.particles.data.Vibration;
import dev.esophose.playerparticles.styles.ParticleStyle;
import java.util.Set;
import me.tWizT3d_dreaMr.PotionArmour.PlayerEffectTracker;
import me.tWizT3d_dreaMr.PotionArmour.PotionArmorPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;

public class TrailEffect extends EquipmentEffect {

    ParticleEffect effect = null; // particle used
    ParticleStyle style = null; // pattern of appearance
    TrailData data = null; // modifier, optional (e.g. color, material, item, transition)
    PlayerParticlesAPI api = null;
    String str = "";
    EquipmentEffect.EffectType type = EquipmentEffect.EffectType.TRAIL;

    public TrailEffect() {
        this(EquipmentSlotGroup.ANY, "small_flame", "overhead", null);
    }

    public TrailEffect(
            EquipmentSlotGroup _slot, String _effect, String _style, ConfigurationSection _data) {
        api = PlayerParticlesAPI.getInstance();
        this.slot = _slot;
        this.effect = ParticleEffect.fromName(_effect);
        this.style = ParticleStyle.fromName(_style);
        this.data = TrailData.fromConfig(_data);
        this.str = "TrailEffect: {" + _effect + " " + _style + " " + this.data.toString() + "}";
    }

    @Override
    public int compareTo(EquipmentEffect o) {
        // in order, rank by: equipment effect type, particle, style, data obj
        if (!(o instanceof TrailEffect)) {
            return this.type.compareTo(o.type);
        }
        TrailEffect cast = (TrailEffect) o;
        return this.str.compareTo(cast.str);
    }

    @Override
    public boolean applyTo(LivingEntity p, EquipmentSlot slot) {
        if (!(p instanceof Player)) {
            return false;
        }

        Player player = (Player) p;
        PlayerEffectTracker tracker = PotionArmorPlugin.plugin.manager.getTracker();

        // 1. Capture snapshot and remove user trails before first equipment trail
        if (!tracker.hasEquipmentTrails(player)) {
            tracker.captureUserTrailSnapshot(player);
            // Remove ALL current trails so equipment trails replace them
            // This ensures user trails don't interfere with equipment trails
            api.resetActivePlayerParticles(player);
        }

        // 2. Apply equipment trail (existing switch logic)
        ParticlePair applied = null;

        switch (this.data.type) {
            case COLOR_TRANSITION:
                applied =
                        api.addActivePlayerParticle(
                                player,
                                this.effect,
                                this.style,
                                (ColorTransition) this.data.dataObject);
                break;
            case NOTE_COLOR:
                applied =
                        api.addActivePlayerParticle(
                                player, this.effect, this.style, (NoteColor) this.data.dataObject);
                break;
            case ORDINARY_COLOR:
                applied =
                        api.addActivePlayerParticle(
                                player,
                                this.effect,
                                this.style,
                                (OrdinaryColor) this.data.dataObject);
                break;
            case VIBRATION:
                applied =
                        api.addActivePlayerParticle(
                                player, this.effect, this.style, (Vibration) this.data.dataObject);
                break;
            case MATERIAL:
                applied =
                        api.addActivePlayerParticle(
                                player, this.effect, this.style, (Material) this.data.dataObject);
                break;
            default: // no data
                applied = api.addActivePlayerParticle(player, this.effect, this.style);
        }

        // 3. Store the trail ID for later selective removal
        if (applied != null) {
            tracker.trackEquipmentTrailId(player, applied.getId(), slot);
            return true;
        }

        return false;
    }

    @Override
    public boolean removeFrom(LivingEntity p, EquipmentSlot slot) {
        if (!(p instanceof Player)) {
            return false;
        }

        Player player = (Player) p;
        PlayerEffectTracker tracker = PotionArmorPlugin.plugin.manager.getTracker();

        // 1. Get equipment trail IDs for this slot
        Set<Integer> trailIds = tracker.getEquipmentTrailIds(player, slot);

        // 2. Remove ONLY the trails from this slot (selective removal by ID)
        for (Integer id : trailIds) {
            api.removeActivePlayerParticle(player, id); // ID-based removal!
            tracker.untrackEquipmentTrailId(player, id, slot);
        }

        // 3. If no more equipment trails, restore user trails
        if (!tracker.hasEquipmentTrails(player)) {
            tracker.restoreUserTrailSnapshot(player);
        }

        return true;
    }

    @Override
    public String toString() {
        String out = "TrailEffect: {";
        if (this.effect != null){
            out += this.effect.toString() + " ";
        }
        if (this.style != null){
            out += this.style.toString() + " ";
        }
        if (this.data != null){
            out += this.data.toString() + " ";
        }
        out+= "}";
        return out;
    }

    public static TrailEffect fromConfig(EquipmentSlotGroup slot, ConfigurationSection s) {
        return new TrailEffect(
                slot,
                s.getString("effect"),
                s.getString("style"),
                s.getConfigurationSection("data"));
    }
}
