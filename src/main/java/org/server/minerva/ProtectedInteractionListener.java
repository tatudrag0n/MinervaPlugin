package org.server.minerva;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class ProtectedInteractionListener implements Listener {
   private final Minerva plugin;
   private final ProtectionService protection;

   ProtectedInteractionListener(Minerva plugin, ProtectionService protection) {
      this.plugin = plugin;
      this.protection = protection;
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onBlockDrop(BlockDropItemEvent event) {
      if (this.plugin.isShopBlock(event.getBlockState().getBlock())) {
         event.getItems().clear();
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onEntityExplode(EntityExplodeEvent event) {
      event.blockList().removeIf(block -> this.plugin.isShopBlock(block) || this.protection.isProtected(block.getLocation()));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onBlockExplode(BlockExplodeEvent event) {
      event.blockList().removeIf(block -> this.plugin.isShopBlock(block) || this.protection.isProtected(block.getLocation()));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onBurn(BlockBurnEvent event) {
      if (this.plugin.isShopBlock(event.getBlock()) || this.protection.isProtected(event.getBlock().getLocation())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onPistonExtend(BlockPistonExtendEvent event) {
      if (event.getBlocks().stream().anyMatch(block -> this.plugin.isShopBlock(block) || this.protection.isProtected(block.getLocation()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onPistonRetract(BlockPistonRetractEvent event) {
      if (event.getBlocks().stream().anyMatch(block -> this.plugin.isShopBlock(block) || this.protection.isProtected(block.getLocation()))) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onLiquidFlow(BlockFromToEvent event) {
      Block target = event.getToBlock();
      if (this.plugin.isShopBlock(target) || this.protection.isProtected(target.getLocation())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent event) {
      Block block = event.getClickedBlock();
      if (block != null) {
         Player player = event.getPlayer();
         if (!this.plugin.isShopBlock(block)) {
            if (this.isProtectedInteractionMaterial(block.getType()) || event.getAction() == Action.PHYSICAL) {
               InteractionType type = this.interactionType(block.getType());
               if (!this.protection.canInteract(player, block.getLocation(), type)) {
                  event.setCancelled(true);
                  player.sendMessage(ChatColor.RED + "このチャンクは保護されています。");
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onEntityInteract(PlayerInteractEntityEvent event) {
      Entity entity = event.getRightClicked();
      if (entity instanceof ItemFrame || entity instanceof ArmorStand) {
         if (!this.plugin.isAuctionFrame(entity) || !this.plugin.isAuctionInteractionItem(this.handItem(event.getPlayer(), event.getHand()))) {
            if (!this.protection.canInteract(event.getPlayer(), entity.getLocation(), InteractionType.ENTITY_EDIT)) {
               event.setCancelled(true);
               event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onEntityDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof ItemFrame || event.getEntity() instanceof ArmorStand) {
         if (event.getDamager() instanceof Player player) {
            if (!this.plugin.isAuctionFrame(event.getEntity()) || !this.plugin.isAuctionInteractionItem(player.getInventory().getItemInMainHand())) {
               if (!this.protection.canInteract(player, event.getEntity().getLocation(), InteractionType.ENTITY_EDIT)) {
                  event.setCancelled(true);
                  player.sendMessage(ChatColor.RED + "このチャンクは保護されています。");
               }
            }
         } else {
            if (this.protection.isProtected(event.getEntity().getLocation())) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onHangingBreak(HangingBreakByEntityEvent event) {
      if (event.getRemover() instanceof Player player) {
         if (!this.protection.canInteract(player, event.getEntity().getLocation(), InteractionType.ENTITY_EDIT)) {
            event.setCancelled(true);
         }
      } else {
         if (this.protection.isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onSignChange(SignChangeEvent event) {
      if (!this.protection.canInteract(event.getPlayer(), event.getBlock().getLocation(), InteractionType.USE_BLOCK)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.RED + "このチャンクは保護されています。");
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onInventoryMove(InventoryMoveItemEvent event) {
      Location source = this.holderLocation(event.getSource());
      Location destination = this.holderLocation(event.getDestination());
      if (source != null && this.protection.isProtected(source) || destination != null && this.protection.isProtected(destination)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Location top = this.holderLocation(event.getView().getTopInventory());
         if (top != null && !this.plugin.isShopBlock(top.getBlock()) && !this.protection.canInteract(player, top, InteractionType.CONTAINER)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Location top = this.holderLocation(event.getView().getTopInventory());
         if (top != null && !this.plugin.isShopBlock(top.getBlock()) && !this.protection.canInteract(player, top, InteractionType.CONTAINER)) {
            event.setCancelled(true);
         }
      }
   }

   private Location holderLocation(Inventory inventory) {
      if (inventory == null) {
         return null;
      } else {
         return inventory.getHolder() instanceof BlockState blockState ? blockState.getLocation() : null;
      }
   }

   private ItemStack handItem(Player player, EquipmentSlot hand) {
      return hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
   }

   private InteractionType interactionType(Material material) {
      String name = material.name();
      if (name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE") || name.equals("LEVER")) {
         return InteractionType.REDSTONE;
      } else {
         return this.isContainerMaterial(material) ? InteractionType.CONTAINER : InteractionType.USE_BLOCK;
      }
   }

   private boolean isProtectedInteractionMaterial(Material material) {
      String name = material.name();
      return this.isContainerMaterial(material)
         || name.endsWith("_TRAPDOOR")
         || name.endsWith("_DOOR")
         || name.endsWith("_FENCE_GATE")
         || name.endsWith("_BUTTON")
         || name.endsWith("_PRESSURE_PLATE")
         || name.equals("LEVER");
   }

   private boolean isContainerMaterial(Material material) {
      String name = material.name();
      return name.equals("BARREL")
         || name.equals("CHEST")
         || name.equals("TRAPPED_CHEST")
         || name.endsWith("_SHULKER_BOX")
         || name.endsWith("_SHELF")
         || name.equals("CHISELED_BOOKSHELF")
         || name.equals("HOPPER")
         || name.equals("DISPENSER")
         || name.equals("DROPPER")
         || name.equals("FURNACE")
         || name.equals("BLAST_FURNACE")
         || name.equals("SMOKER")
         || name.equals("BREWING_STAND")
         || name.equals("LECTERN");
   }
}
