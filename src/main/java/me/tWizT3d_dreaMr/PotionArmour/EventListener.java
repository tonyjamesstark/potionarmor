/* (C)2024 */
package me.tWizT3d_dreaMr.PotionArmour;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

@SuppressWarnings("deprecation")
public class EventListener implements Listener {

    public final int UNKNOWN_SLOT_NUM = 45; // TODO: explain hardcoded value from prior...?

    public EffectManager mgr;

    public EventListener(EffectManager p) {
        mgr = p;
    }

    @EventHandler
    public void changeArmor(PlayerArmorChangeEvent e) {
        PotionArmorPlugin.plugin.logger.info("changeArmor called");
        ItemStack n = e.getNewItem();
        ItemStack o = e.getOldItem();
        Player p = (Player) e.getPlayer();
        SlotType slotType = e.getSlotType();
        EquipmentSlot slot;
        switch (slotType) {
            case HEAD:
                slot = EquipmentSlot.HEAD;
                break;
            case CHEST:
                slot = EquipmentSlot.CHEST;
                break;
            case LEGS:
                slot = EquipmentSlot.LEGS;
                break;
            case FEET:
                slot = EquipmentSlot.FEET;
                break;
            default:
                slot = EquipmentSlot.HAND;
        }
        this.mgr.replaceEquipment(p, n, o, slot);
        // mgr.resetPlayerEffects(e.getPlayer());
    }

    @EventHandler
    public void changeWorld(PlayerChangedWorldEvent e) {
        PotionArmorPlugin.plugin.logger.info("changeWorld called");
        mgr.resetPlayerEffects(e.getPlayer());
    }

    // inventory click
    @EventHandler
    public void invClick(InventoryClickEvent e) {
        PotionArmorPlugin.plugin.logger.info("invClick called");
        EquipmentSlot slot = null;
        if (e.getSlotType() != InventoryType.SlotType.ARMOR
                || e.getSlotType() != InventoryType.SlotType.QUICKBAR) {
            return; // necessary because inventory slot numbers overlap
        }
        if (e.getSlot() == e.getWhoClicked().getInventory().getHeldItemSlot()) {
            slot = EquipmentSlot.HAND;
        } else if (e.getSlot()
                == UNKNOWN_SLOT_NUM) { // TODO: see above, figure out magic number, likely the
            // offhand
            // slot?
            slot = EquipmentSlot.OFF_HAND;
        } else {
            return; // only act on specific slots
        }
        final Player p = (Player) e.getWhoClicked();

        // this fires AFTER the click happens, so the current item is the one taken away(?) etc.
        ItemStack n = e.getCursor();
        ItemStack o = e.getCurrentItem();
        mgr.replaceEquipment(p, n, o, slot);
    }

    // hotbar
    @EventHandler
    public void newItemHeld(PlayerItemHeldEvent e) {
        PotionArmorPlugin.plugin.logger.info("newItemHeld called");
        final Player p = e.getPlayer();
        final PlayerInventory inv = p.getInventory();
        ItemStack n = inv.getItem(e.getNewSlot());
        ItemStack o = inv.getItem(e.getPreviousSlot());
        mgr.replaceEquipment(p, n, o, EquipmentSlot.HAND);
    }

    // drop
    @EventHandler
    public void drop(PlayerDropItemEvent e) {
        PotionArmorPlugin.plugin.logger.info("drop called");
        final Player p = e.getPlayer();
        ItemStack o = e.getItemDrop().getItemStack();
        mgr.removeEquipment(p, o); // TODO: this might remove more effects than it should
    }

    // THIS IS ANNOYING
    // TODO: figure out a way to implement this such that duplicate effects arent applied
    // i.e. only if it ends up in your main hand
    // // pickup
    // @EventHandler
    // public void pickup(EntityPickupItemEvent e) {
    //     final LivingEntity le = e.getEntity();
    //     if (!(le instanceof Player)) {
    //         return;
    //     }
    //     Player p = (Player) le;
    //     if(e.getItem().getItemStack().isSimilar(p.getInventory().getItemInMainHand())){

    //     }

    //     // TODO: if performance is suffering,
    //     // can optionally check if the item ended up in main hand and act on only that
    //     // mgr.refreshAppliedEquipment(p, null);
    // }

    // armor stand use
    @EventHandler
    public void armorStandInteract(PlayerArmorStandManipulateEvent e) { // PlayerInteractEntityEvent
        PotionArmorPlugin.plugin.logger.info("armorStandInteract called");
        final Player p = e.getPlayer();
        ItemStack n = e.getArmorStandItem();
        ItemStack o = e.getPlayerItem();
        EquipmentSlot slot = e.getSlot();
        mgr.replaceEquipment(p, n, o, slot);
    }

    @EventHandler
    public void gamemode(PlayerGameModeChangeEvent e) {
        PotionArmorPlugin.plugin.logger.info("gamemode called");
        PotionArmorPlugin.plugin.logger.log(Level.FINE, "gamemode called");
        mgr.resetPlayerEffects(e.getPlayer());
    }

    // TODO: check /hat command
    // this is stupid because essentials does not throw any events for this action
    // and there's no way to listen for programatic changes to inventory
    // so its a kludge...
    @EventHandler
    public void hatPostCheck(PlayerCommandPreprocessEvent e) {
        PotionArmorPlugin.plugin.logger.info("hatPostCheck called");
        if (e.getMessage().contains("/hat") && e.getPlayer().hasPermission("essentials.hat")) {
            mgr.hatCommand(e.getPlayer());
        }
    }

    // check swap hands commands

}
