package org.server.minerva;

import java.util.UUID;
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
import org.bukkit.event.entity.FoodLevelChangeEvent;
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
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
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
      if (this.ffa.stands().isKitSelector(event.getRightClicked())) {
         event.setCancelled(true);
         this.ffa.stands().playClick(event.getRightClicked().getLocation());
         this.ffa.openKitSelector(event.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onKitStandDamage(EntityDamageEvent event) {
      if (this.ffa.stands().isKitSelector(event.getEntity())) {
         event.setCancelled(true);
      } else if (!this.ffa.handleFieldItemDamage(event)) {
         ;
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFallDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof Player player
         && this.ffa.isPlaying(player)
         && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
         event.setCancelled(true);
         player.setFallDistance(0.0F);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onDamage(EntityDamageByEntityEvent event) {
      if (this.ffa.stands().isKitSelector(event.getEntity())) {
         event.setCancelled(true);
      } else if (event.getEntity() instanceof Player victim) {
         Entity var9 = event.getDamager();
         boolean isBugSilverfish = false;
         Player bugOwner = null;
         if (var9 != null && "bug_silverfish".equals(var9.getPersistentDataContainer().get(this.ffa.entityKindKey(), PersistentDataType.STRING))) {
            isBugSilverfish = true;
            UUID ownerId = this.ffa.bugOwnerOf(var9);
            if (ownerId != null) {
               bugOwner = this.plugin.getServer().getPlayer(ownerId);
            }
         }

         Player attacker = this.attackingPlayer(event.getDamager());
         boolean victimInFfa = this.ffa.isPlaying(victim);
         boolean attackerInFfa = attacker != null && this.ffa.isPlaying(attacker);
         if (isBugSilverfish && bugOwner != null) {
            if (!victimInFfa) {
               event.setCancelled(true);
            } else if (victim.getUniqueId().equals(bugOwner.getUniqueId())) {
               event.setCancelled(true);
            } else {
               event.setCancelled(false);
            }
         } else if (victimInFfa && attacker == null) {
            event.setCancelled(false);
         } else if (!victimInFfa && !attackerInFfa) {
            if (attacker != null && this.ffa.isFfaWorld(victim.getWorld())) {
               event.setCancelled(true);
               attacker.sendMessage("§cFFA 参加中のプレイヤー同士だけ攻撃できます。");
            }
         } else if (victimInFfa && attackerInFfa) {
            event.setCancelled(false);
            this.ffa.adjustFfaDamage(event, attacker, victim);
         } else {
            event.setCancelled(true);
            if (attacker != null) {
               attacker.sendMessage("§cFFA 参加者と非参加者は攻撃できません。");
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onKitSelectorClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (this.ffa.handleKitSelectorClick(player, event)) {
            event.setCancelled(true);
         } else {
            if (this.containsFfaItem(event.getCurrentItem(), event.getCursor()) && event.getView().getTopInventory() != player.getInventory()) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onInventoryDrag(InventoryDragEvent event) {
      if (this.containsFfaItem(event.getOldCursor()) && event.getView().getTopInventory() != event.getWhoClicked().getInventory()) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onInventoryMove(InventoryMoveItemEvent event) {
      if (this.ffa.isFfaItem(event.getItem())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onInventoryPickup(InventoryPickupItemEvent event) {
      this.ffa.handleFieldItemInventoryPickup(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onCraft(CraftItemEvent event) {
      for (ItemStack item : event.getInventory().getMatrix()) {
         if (this.ffa.isFfaItem(item)) {
            event.setCancelled(true);
            return;
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onRespawn(PlayerRespawnEvent event) {
      Location deathLeaveLocation = this.ffa.handleDeathLeaveRespawn(event.getPlayer());
      if (deathLeaveLocation != null) {
         event.setRespawnLocation(deathLeaveLocation);
      } else if (this.ffa.isPlaying(event.getPlayer())) {
         Location respawn = this.ffa.respawnLocation();
         if (respawn != null) {
            event.setRespawnLocation(respawn);
         }

         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.ffa.respawn(event.getPlayer()));
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFoodLevelChange(FoodLevelChangeEvent event) {
      if (!(event.getEntity() instanceof Player player)) {
         return;
      }

      if (this.ffa.isPlaying(player)) {
         event.setCancelled(false);
         return;
      }

      if (!"survival".equalsIgnoreCase(player.getWorld().getName())
         && event.getFoodLevel() < player.getFoodLevel()) {
         event.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDrop(PlayerDropItemEvent event) {
      if (this.ffa.isFfaItem(event.getItemDrop().getItemStack())) {
         event.setCancelled(true);
         if (!this.ffa.isPlaying(event.getPlayer())) {
            event.getItemDrop().remove();
         }
      } else {
         if (this.ffa.isPlaying(event.getPlayer()) && !this.ffa.restrictionAllows("drop-items")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFA中はアイテムを捨てられません。");
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPickup(EntityPickupItemEvent event) {
      if (!this.ffa.handleFieldItemPickup(event)) {
         if (this.ffa.isFfaItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
         } else {
            if (event.getEntity() instanceof Player player && this.ffa.isPlaying(player) && !this.ffa.restrictionAllows("pickup-items")) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      if (this.ffa.isPlaying(event.getPlayer()) && !this.ffa.restrictionAllows("block-break")) {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§cFFA中はブロックを破壊できません。");
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onBlockPlace(BlockPlaceEvent event) {
      if (this.ffa.isPlaying(event.getPlayer()) && !this.ffa.restrictionAllows("block-place")) {
         event.setCancelled(true);
         event.getPlayer().sendMessage("§cFFA中はブロックを設置できません。");
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onItemFrameUse(PlayerInteractEntityEvent event) {
      if (event.getRightClicked() instanceof ItemFrame) {
         ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
         if (this.ffa.isFfaItem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFAアイテムは額縁に設置できません。");
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onShoot(EntityShootBowEvent event) {
      this.ffa.handleBowShoot(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProjectileLaunch(ProjectileLaunchEvent event) {
      this.ffa.handleProjectileLaunch(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onProjectileHit(ProjectileHitEvent event) {
      this.ffa.handleProjectileHit(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onPotionSplash(PotionSplashEvent event) {
      this.ffa.handlePotionSplash(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onEntityDeath(EntityDeathEvent event) {
      this.ffa.handleEntityDeath(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onEntityTarget(EntityTargetLivingEntityEvent event) {
      this.ffa.handleEntityTarget(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onPickupArrow(PlayerPickupArrowEvent event) {
      if (!this.ffa.handleArrowPickup(event)) {
         if (this.ffa.isPlaying(event.getPlayer())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.ffa.isPlaying(event.getPlayer())) {
         if (!event.getPlayer().isOp()) {
            if (!this.ffa.commandAllowed(event.getMessage())) {
               event.setCancelled(true);
               event.getPlayer().sendMessage("§cFFA中はこのコマンドを使用できません。");
            }
         }
      }
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent event) {
      if (this.ffa.isPlaying(event.getPlayer()) && this.ffa.leaveOnWorldExit()) {
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            Player player = event.getPlayer();
            Location center = this.ffa.center();
            if (player.isOnline() && this.ffa.isPlaying(player) && center != null && !player.getWorld().equals(center.getWorld())) {
               this.ffa.leave(player, true);
            }
         });
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      if (this.ffa.isPlaying(event.getPlayer())) {
         this.ffa.leave(event.getPlayer(), false);
      }
   }

   private Player attackingPlayer(Entity damager) {
      if (damager instanceof Player player) {
         return player;
      } else {
         Player owner = this.ffa.ownerOfFfaEntity(damager);
         if (owner != null) {
            return owner;
         }

         if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
               return player;
            }

            if (source instanceof Entity entity) {
               return this.ffa.ownerOfFfaEntity(entity);
            }
         }

         return null;
      }
   }

   private boolean containsFfaItem(ItemStack... items) {
      for (ItemStack item : items) {
         if (this.ffa.isFfaItem(item)) {
            return true;
         }
      }

      return false;
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
   public void onLethalFfaDamage(EntityDamageEvent event) {
      if (!(event.getEntity() instanceof Player player) || !this.ffa.isPlaying(player) || event.isCancelled()) {
         return;
      }

      double remainingHealth = player.getHealth() + player.getAbsorptionAmount();
      if (event.getFinalDamage() < remainingHealth) {
         return;
      }

      event.setCancelled(true);
      player.setHealth(Math.max(1.0, player.getHealth()));
      player.setAbsorptionAmount(0.0);
      this.ffa.handleDeath(player, player.getKiller());
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline() && this.ffa.isPlaying(player)) {
            this.ffa.leave(player, true);
         }
      });
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onDeath(PlayerDeathEvent var1) {
      Player var2 = var1.getEntity();
      if (this.ffa.isPlaying(var2)) {
         var1.getDrops().clear();
         var1.setDroppedExp(0);
         var1.setKeepInventory(true);
         var1.setKeepLevel(true);
         this.ffa.handleDeath(var2, var2.getKiller());
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.forceRespawn(var2));
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.forceRespawn(var2), 20L);
      }
   }

   private void forceRespawn(Player var1) {
      if (var1.isOnline() && var1.isDead() && this.ffa.isPlaying(var1)) {
         try {
            var1.spigot().respawn();
         } catch (RuntimeException var3) {
            this.plugin.getLogger().warning("FFA player respawn failed for " + var1.getName() + ": " + var3.getMessage());
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent var1) {
      if (var1.getTo() != null
         && (
            var1.getFrom().getBlockX() != var1.getTo().getBlockX()
               || var1.getFrom().getBlockY() != var1.getTo().getBlockY()
               || var1.getFrom().getBlockZ() != var1.getTo().getBlockZ()
         )) {
         this.ffa.handleTrapStep(var1.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onInteract(PlayerInteractEvent var1) {
      if (!this.ffa.handleEmptyCrossbowInteract(var1)) {
         if (this.ffa.handlePotionUse(var1)) {
            var1.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onTrainingVillagerDamage(EntityDamageByEntityEvent event) {
      if (!(event.getEntity() instanceof org.bukkit.entity.Villager villager)) {
         return;
      }

      Player attacker = null;
      if (event.getDamager() instanceof Player player) {
         attacker = player;
      } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
         attacker = player;
      }

      if (attacker != null && this.ffa.isPlaying(attacker)) {
         this.ffa.adjustTrainingVillagerDamage(event, attacker, villager);
      }
   }

}
