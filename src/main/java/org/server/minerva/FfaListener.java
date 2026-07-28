package org.server.minerva;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

final class FfaListener implements Listener {
    private final Minerva plugin;
    private final FfaManager ffa;

    FfaListener(Minerva plugin, FfaManager ffa) {
        this.plugin = plugin;
        this.ffa = ffa;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onKitStandClick(PlayerInteractAtEntityEvent event) {
        if (!ffa.stands().isKitSelector(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        ffa.stands().playClick(event.getRightClicked().getLocation());
        ffa.openKitSelector(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onKitStandDamage(EntityDamageEvent event) {
        if (ffa.stands().isKitSelector(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (ffa.handleFieldItemDamage(event)) {
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (ffa.stands().isKitSelector(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        
        // バグマニアのシルバーフィッシュの特別処理
        Entity damager = event.getDamager();
        boolean isBugSilverfish = false;
        Player bugOwner = null;
        if (damager != null && "bug_silverfish".equals(damager.getPersistentDataContainer().get(ffa.entityKindKey(), org.bukkit.persistence.PersistentDataType.STRING))) {
            isBugSilverfish = true;
            UUID ownerId = ffa.bugOwnerOf(damager);
            if (ownerId != null) {
                bugOwner = plugin.getServer().getPlayer(ownerId);
            }
        }
        
        Player attacker = attackingPlayer(event.getDamager());
        boolean victimInFfa = ffa.isPlaying(victim);
        boolean attackerInFfa = attacker != null && ffa.isPlaying(attacker);
        
        // バグマニアのシルバーフィッシュの場合、特別に処理
        if (isBugSilverfish && bugOwner != null) {
            if (!victimInFfa) {
                event.setCancelled(true);
                return;
            }
            // バグマニア本人にはダメージを与えない
            if (victim.getUniqueId().equals(bugOwner.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            // フレンドにはダメージを与えない
            if (plugin.areFriends(bugOwner.getUniqueId(), victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(false);
            return;
        }
        
        if (!victimInFfa && !attackerInFfa) {
            if (attacker != null && ffa.isFfaWorld(victim.getWorld())) {
                event.setCancelled(true);
                attacker.sendMessage("§cFFA 参加中のプレイヤー同士だけ攻撃できます。");
            }
            return;
        }
        if (victimInFfa && attackerInFfa) {
            if (plugin.areFriends(victim.getUniqueId(), attacker.getUniqueId())) {
                event.setCancelled(true);
                attacker.sendMessage("§cフレンドには攻撃できません。");
                return;
            }
            event.setCancelled(false);
            ffa.adjustFfaDamage(event, attacker, victim);
            return;
        }
        event.setCancelled(true);
        if (attacker != null) {
            attacker.sendMessage("§cFFA 参加者と非参加者は攻撃できません。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onKitSelectorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (ffa.handleKitSelectorClick(player, event)) {
            event.setCancelled(true);
            return;
        }
        if (containsFfaItem(event.getCurrentItem(), event.getCursor())
                && event.getView().getTopInventory() != player.getInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (containsFfaItem(event.getOldCursor()) && event.getView().getTopInventory() != event.getWhoClicked().getInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (ffa.isFfaItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        ffa.handleFieldItemInventoryPickup(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (ffa.isFfaItem(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!ffa.isPlaying(victim)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        ffa.handleDeath(victim, victim.getKiller());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location deathLeaveLocation = ffa.handleDeathLeaveRespawn(event.getPlayer());
        if (deathLeaveLocation != null) {
            event.setRespawnLocation(deathLeaveLocation);
            return;
        }
        if (!ffa.isPlaying(event.getPlayer())) {
            return;
        }
        Location respawn = ffa.respawnLocation();
        if (respawn != null) {
            event.setRespawnLocation(respawn);
        }
        ffa.respawn(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (ffa.isFfaItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            if (!ffa.isPlaying(event.getPlayer())) {
                event.getItemDrop().remove();
            }
            return;
        }
        if (ffa.isPlaying(event.getPlayer()) && !ffa.restrictionAllows("drop-items")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFA中はアイテムを捨てられません。");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (ffa.handleFieldItemPickup(event)) {
            return;
        }
        if (ffa.isFfaItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }
        if (event.getEntity() instanceof Player player
                && ffa.isPlaying(player)
                && !ffa.restrictionAllows("pickup-items")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (ffa.isPlaying(event.getPlayer()) && !ffa.restrictionAllows("block-break")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFA中はブロックを破壊できません。");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (ffa.isPlaying(event.getPlayer()) && !ffa.restrictionAllows("block-place")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFA中はブロックを設置できません。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (ffa.handlePotionUse(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemFrameUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) {
            return;
        }
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (ffa.isFfaItem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFAアイテムは額縁に設置できません。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShoot(EntityShootBowEvent event) {
        ffa.handleBowShoot(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ffa.handleProjectileLaunch(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileHit(ProjectileHitEvent event) {
        ffa.handleProjectileHit(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPotionSplash(PotionSplashEvent event) {
        ffa.handlePotionSplash(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDeath(EntityDeathEvent event) {
        ffa.handleEntityDeath(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        ffa.handleEntityTarget(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (ffa.handleArrowPickup(event)) {
            return;
        }
        if (ffa.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!ffa.isPlaying(event.getPlayer())) {
            return;
        }
        if (!ffa.commandAllowed(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFA中はこのコマンドを使用できません。");
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!ffa.isPlaying(event.getPlayer()) || !ffa.leaveOnWorldExit()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            Location center = ffa.center();
            if (player.isOnline() && ffa.isPlaying(player) && center != null && !player.getWorld().equals(center.getWorld())) {
                ffa.leave(player, true);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (ffa.isPlaying(event.getPlayer())) {
            ffa.leave(event.getPlayer(), false);
        }
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        Player owner = ffa.ownerOfFfaEntity(damager);
        if (owner != null) {
            return owner;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
            if (source instanceof Entity entity) {
                return ffa.ownerOfFfaEntity(entity);
            }
        }
        return null;
    }

    private boolean containsFfaItem(ItemStack... items) {
        for (ItemStack item : items) {
            if (ffa.isFfaItem(item)) {
                return true;
            }
        }
        return false;
    }
}
