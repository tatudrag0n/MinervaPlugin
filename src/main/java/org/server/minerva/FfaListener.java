package org.server.minerva;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
        Player attacker = attackingPlayer(event.getDamager());
        boolean victimInFfa = ffa.isPlaying(victim);
        boolean attackerInFfa = attacker != null && ffa.isPlaying(attacker);
        if (!victimInFfa && !attackerInFfa) {
            if (attacker != null && ffa.isFfaWorld(victim.getWorld())) {
                event.setCancelled(true);
                attacker.sendMessage("§cFFA参加中のプレイヤー同士だけ攻撃できます。");
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
            return;
        }
        event.setCancelled(true);
        if (attacker != null) {
            attacker.sendMessage("§cFFA参加者と非参加者は攻撃できません。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onKitSelectorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (ffa.handleKitSelectorClick(player, event)) {
            event.setCancelled(true);
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
        if (ffa.isPlaying(event.getPlayer()) && !ffa.restrictionAllows("drop-items")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cFFA中はアイテムを捨てられません。");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
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
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
