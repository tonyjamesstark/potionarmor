/* (C)2024 */
package me.tWizT3d_dreaMr.PotionArmour;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType;
import java.util.logging.Level;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
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
        final Player p = e.getPlayer();

        // Delay the reset to ensure inventory is synchronized after dimension change
        // Similar to playerJoin/playerRespawn, we need to wait for the player's
        // equipment to be fully loaded in the new dimension
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(PotionArmorPlugin.plugin, () -> mgr.resetPlayerEffects(p), 5L);
    }

    // inventory click
    @EventHandler
    public void invClick(InventoryClickEvent e) {
        PotionArmorPlugin.plugin.logger.info("invClick called");
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player p = (Player) e.getWhoClicked();
        EquipmentSlot slot = null;

        // Handle armor slots (shift-clicking, direct clicking, etc.)
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) {
            // Map armor slot to EquipmentSlot
            switch (e.getSlot()) {
                case 36: // boots
                    slot = EquipmentSlot.FEET;
                    break;
                case 37: // leggings
                    slot = EquipmentSlot.LEGS;
                    break;
                case 38: // chestplate
                    slot = EquipmentSlot.CHEST;
                    break;
                case 39: // helmet
                    slot = EquipmentSlot.HEAD;
                    break;
                default:
                    return;
            }
        } else if (e.getSlotType() == InventoryType.SlotType.QUICKBAR) {
            // Handle main hand
            if (e.getSlot() == p.getInventory().getHeldItemSlot()) {
                slot = EquipmentSlot.HAND;
            } else {
                return; // other quickbar slots don't matter
            }
        } else if (e.getSlot() == UNKNOWN_SLOT_NUM) {
            // Handle offhand (slot 45) - can be accessed from various slot types in inventory GUI
            slot = EquipmentSlot.OFF_HAND;
        } else {
            return; // only act on armor, main hand, and offhand slots
        }

        // this fires AFTER the click happens, so the current item is the one taken
        // away(?) etc.
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
    // TODO: figure out a way to implement this such that duplicate effects arent
    // applied
    // i.e. only if it ends up in your main hand
    // // pickup
    // @EventHandler
    // public void pickup(EntityPickupItemEvent e) {
    // final LivingEntity le = e.getEntity();
    // if (!(le instanceof Player)) {
    // return;
    // }
    // Player p = (Player) le;
    // if(e.getItem().getItemStack().isSimilar(p.getInventory().getItemInMainHand())){

    // }

    // // TODO: if performance is suffering,
    // // can optionally check if the item ended up in main hand and act on only
    // that
    // // mgr.refreshAppliedEquipment(p, null);
    // }

    // armor stand use - player swaps item in hand with armor stand slot
    @EventHandler
    public void armorStandInteract(PlayerArmorStandManipulateEvent e) {
        PotionArmorPlugin.plugin.logger.info("armorStandInteract called");
        final Player p = e.getPlayer();

        // getArmorStandItem() = item ON the armor stand (player will receive this)
        // getPlayerItem() = item player is HOLDING (will go to armor stand)
        // The player's slot is always HAND since they interact with their held item
        ItemStack itemFromStand = e.getArmorStandItem();
        ItemStack itemFromPlayer = e.getPlayerItem();

        // Player gives their held item to stand, receives item from stand
        // The player's slot involved is their main hand
        mgr.replaceEquipment(p, itemFromStand, itemFromPlayer, EquipmentSlot.HAND);
    }

    // Handle giving items to other entities (horses, wolves, etc.)
    @EventHandler
    public void entityInteract(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        // Skip armor stands - handled by dedicated event above
        if (e.getRightClicked() instanceof org.bukkit.entity.ArmorStand) {
            return;
        }

        final Player p = e.getPlayer();
        final EquipmentSlot hand = e.getHand();

        // Get the item the player is using to interact
        ItemStack heldItem;
        if (hand == EquipmentSlot.HAND) {
            heldItem = p.getInventory().getItemInMainHand();
        } else {
            heldItem = p.getInventory().getItemInOffHand();
        }

        // If player is holding an item that might be given to the entity
        // (saddles, armor, food, etc.), schedule a check
        if (heldItem != null && heldItem.getType() != org.bukkit.Material.AIR) {
            final ItemStack originalItem = heldItem.clone();
            org.bukkit.Bukkit.getScheduler()
                    .runTaskLater(
                            PotionArmorPlugin.plugin,
                            () -> {
                                // Check if the item was consumed/given to the entity
                                ItemStack currentItem;
                                if (hand == EquipmentSlot.HAND) {
                                    currentItem = p.getInventory().getItemInMainHand();
                                } else {
                                    currentItem = p.getInventory().getItemInOffHand();
                                }

                                // If item changed (was given away or consumed), remove its effects
                                if (!originalItem.isSimilar(currentItem)) {
                                    mgr.removeEquipment(p, originalItem);
                                    // Apply effects from new item if any
                                    if (currentItem != null
                                            && currentItem.getType() != org.bukkit.Material.AIR) {
                                        mgr.addEquipment(p, currentItem, hand);
                                    }
                                }
                            },
                            1L);
        }
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

    // check swap hands commands (F key)
    @EventHandler
    public void swapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        PotionArmorPlugin.plugin.logger.info("swapHands called");
        final Player p = e.getPlayer();

        // e.getMainHandItem() = item that WILL BE in main hand after swap
        // e.getOffHandItem() = item that WILL BE in off hand after swap
        ItemStack newMain = e.getMainHandItem();
        ItemStack newOff = e.getOffHandItem();

        // Current items (before the swap completes)
        ItemStack oldMain = p.getInventory().getItemInMainHand();
        ItemStack oldOff = p.getInventory().getItemInOffHand();

        mgr.replaceEquipment(p, newMain, oldMain, EquipmentSlot.HAND);
        mgr.replaceEquipment(p, newOff, oldOff, EquipmentSlot.OFF_HAND);
    }

    // handle inventory drag events (e.g., dragging armor into armor slots)
    @EventHandler
    public void inventoryDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        PotionArmorPlugin.plugin.logger.info("inventoryDrag called");
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player p = (Player) e.getWhoClicked();

        // Check if any armor slots were affected
        for (int slot : e.getRawSlots()) {
            EquipmentSlot equipSlot = null;
            switch (slot) {
                case 36: // boots
                    equipSlot = EquipmentSlot.FEET;
                    break;
                case 37: // leggings
                    equipSlot = EquipmentSlot.LEGS;
                    break;
                case 38: // chestplate
                    equipSlot = EquipmentSlot.CHEST;
                    break;
                case 39: // helmet
                    equipSlot = EquipmentSlot.HEAD;
                    break;
            }

            if (equipSlot != null) {
                // Schedule a delayed check since drag event happens before the item is placed
                final EquipmentSlot finalSlot = equipSlot;
                org.bukkit.Bukkit.getScheduler()
                        .runTaskLater(
                                PotionArmorPlugin.plugin,
                                () -> {
                                    ItemStack newItem = null;
                                    switch (finalSlot) {
                                        case FEET:
                                            newItem = p.getInventory().getBoots();
                                            break;
                                        case LEGS:
                                            newItem = p.getInventory().getLeggings();
                                            break;
                                        case CHEST:
                                            newItem = p.getInventory().getChestplate();
                                            break;
                                        case HEAD:
                                            newItem = p.getInventory().getHelmet();
                                            break;
                                    }
                                    mgr.resetPlayerEffects(p);
                                },
                                1L);
                return; // Only reset once per drag event
            }
        }
    }

    // handle player death - clear tracked effects only
    @EventHandler
    public void playerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        PotionArmorPlugin.plugin.logger.info("playerDeath called");
        final Player p = e.getEntity();

        // Clear tracked effects when player dies (they lose their equipment)
        // Use resetPlayerEffects which will clear only tracked effects
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(
                        PotionArmorPlugin.plugin,
                        () -> {
                            // Reset clears tracked effects and re-applies from current equipment
                            // Since player died, equipment is gone, so this just clears effects
                            mgr.resetPlayerEffects(p);
                        },
                        1L);
    }

    // handle player respawn - reapply effects from equipped armor
    @EventHandler
    public void playerRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        PotionArmorPlugin.plugin.logger.info("playerRespawn called");
        final Player p = e.getPlayer();

        // Reset effects after respawn (delayed to ensure inventory is loaded)
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(PotionArmorPlugin.plugin, () -> mgr.resetPlayerEffects(p), 5L);
    }

    // handle player quit - clear tracking data
    // Note: We don't need to actively remove effects since the player is leaving
    // and the server will handle cleanup. We just need to clear our tracking.
    @EventHandler
    public void playerQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        PotionArmorPlugin.plugin.logger.info("playerQuit called");
        final Player p = e.getPlayer();

        // Clear our tracking for this player
        // The effects themselves will be cleared by the server when the player leaves
        mgr.clearPlayerTracking(p);
    }

    // handle player join - reset effects to ensure clean state
    @EventHandler
    public void playerJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        PotionArmorPlugin.plugin.logger.info("playerJoin called");
        final Player p = e.getPlayer();

        // Reset effects when player joins (delayed to ensure inventory is loaded)
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(PotionArmorPlugin.plugin, () -> mgr.resetPlayerEffects(p), 10L);
    }

    // Handle inventory close - equipment changes in containers are handled by
    // InventoryClickEvent and other specific events. The periodic validation task
    // will catch any edge cases. Calling validation directly here was causing
    // the validation to fire too frequently.

    // Handle dispenser equipping armor on players
    @EventHandler
    public void dispenserArmor(BlockDispenseArmorEvent e) {
        PotionArmorPlugin.plugin.logger.info("dispenserArmor called");

        if (!(e.getTargetEntity() instanceof Player)) return;

        final Player p = (Player) e.getTargetEntity();
        final ItemStack item = e.getItem();

        // Determine equipment slot from item type
        final EquipmentSlot slot = getSlotForItem(item);
        if (slot == null) return;

        // The item is being added to the player
        // Delayed to ensure the item is actually equipped
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(PotionArmorPlugin.plugin, () -> mgr.addEquipment(p, item, slot), 1L);
    }

    /**
     * Determine the equipment slot for an armor item based on its material.
     */
    private EquipmentSlot getSlotForItem(ItemStack item) {
        if (item == null) return null;
        String name = item.getType().name();

        if (name.endsWith("_HELMET")
                || name.equals("CARVED_PUMPKIN")
                || name.equals("PLAYER_HEAD")
                || name.equals("SKELETON_SKULL")
                || name.equals("WITHER_SKELETON_SKULL")
                || name.equals("ZOMBIE_HEAD")
                || name.equals("CREEPER_HEAD")
                || name.equals("DRAGON_HEAD")
                || name.equals("PIGLIN_HEAD")
                || name.equals("TURTLE_HELMET")) {
            return EquipmentSlot.HEAD;
        } else if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return EquipmentSlot.CHEST;
        } else if (name.endsWith("_LEGGINGS")) {
            return EquipmentSlot.LEGS;
        } else if (name.endsWith("_BOOTS")) {
            return EquipmentSlot.FEET;
        }
        return null;
    }

    // Handle item pickup - only apply effects if item ends up in main hand
    @EventHandler
    public void pickup(EntityPickupItemEvent e) {
        final LivingEntity le = e.getEntity();
        if (!(le instanceof Player)) return;

        final Player p = (Player) le;
        final ItemStack pickedUp = e.getItem().getItemStack();

        // Check after a delay if the item ended up in the main hand
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(
                        PotionArmorPlugin.plugin,
                        () -> {
                            ItemStack mainHand = p.getInventory().getItemInMainHand();
                            // If the picked up item is now in main hand, apply effects
                            if (mainHand.isSimilar(pickedUp)) {
                                mgr.addEquipment(p, mainHand, EquipmentSlot.HAND);
                            }
                        },
                        1L);
    }
}
