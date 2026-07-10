package org.server.minerva;

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
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EquipmentSlot;

final class ProtectedInteractionListener implements Listener {
    private final Minerva plugin;
    private final ProtectionService protection;

    ProtectedInteractionListener(Minerva plugin, ProtectionService protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (plugin.isShopBlock(event.getBlockState().getBlock())) {
            event.getItems().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.isShopBlock(block) || protection.isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.isShopBlock(block) || protection.isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.isShopBlock(event.getBlock()) || protection.isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> plugin.isShopBlock(block) || protection.isProtected(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> plugin.isShopBlock(block) || protection.isProtected(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLiquidFlow(BlockFromToEvent event) {
        Block target = event.getToBlock();
        if (plugin.isShopBlock(target) || protection.isProtected(target.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.isShopBlock(block)) {
            return;
        }
        if (!isProtectedInteractionMaterial(block.getType()) && event.getAction() != Action.PHYSICAL) {
            return;
        }
        InteractionType type = interactionType(block.getType());
        if (!protection.canInteract(player, block.getLocation(), type)) {
            event.setCancelled(true);
            player.sendMessage(org.bukkit.ChatColor.RED + "このチャンクは保護されています。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand)) {
            return;
        }
        if (plugin.isAuctionFrame(entity) && plugin.isAuctionInteractionItem(handItem(event.getPlayer(), event.getHand()))) {
            return;
        }
        if (!protection.canInteract(event.getPlayer(), entity.getLocation(), InteractionType.ENTITY_EDIT)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "このチャンクは保護されています。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame) && !(event.getEntity() instanceof ArmorStand)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            if (protection.isProtected(event.getEntity().getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (plugin.isAuctionFrame(event.getEntity()) && plugin.isAuctionInteractionItem(player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!protection.canInteract(player, event.getEntity().getLocation(), InteractionType.ENTITY_EDIT)) {
            event.setCancelled(true);
            player.sendMessage(org.bukkit.ChatColor.RED + "このチャンクは保護されています。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            if (!protection.canInteract(player, event.getEntity().getLocation(), InteractionType.ENTITY_EDIT)) {
                event.setCancelled(true);
            }
            return;
        }
        if (protection.isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!protection.canInteract(event.getPlayer(), event.getBlock().getLocation(), InteractionType.USE_BLOCK)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "このチャンクは保護されています。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Location source = holderLocation(event.getSource());
        Location destination = holderLocation(event.getDestination());
        if ((source != null && protection.isProtected(source)) || (destination != null && protection.isProtected(destination))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Location top = holderLocation(event.getView().getTopInventory());
        if (top != null && !plugin.isShopBlock(top.getBlock()) && !protection.canInteract(player, top, InteractionType.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Location top = holderLocation(event.getView().getTopInventory());
        if (top != null && !plugin.isShopBlock(top.getBlock()) && !protection.canInteract(player, top, InteractionType.CONTAINER)) {
            event.setCancelled(true);
        }
    }

    private Location holderLocation(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof BlockState blockState) {
            return blockState.getLocation();
        }
        return null;
    }

    private org.bukkit.inventory.ItemStack handItem(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private InteractionType interactionType(Material material) {
        String name = material.name();
        if (name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE") || name.equals("LEVER")) {
            return InteractionType.REDSTONE;
        }
        if (isContainerMaterial(material)) {
            return InteractionType.CONTAINER;
        }
        return InteractionType.USE_BLOCK;
    }

    private boolean isProtectedInteractionMaterial(Material material) {
        String name = material.name();
        return isContainerMaterial(material)
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
