package org.server.minerva;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

final class DiscordAuthListener implements Listener {
    private final DiscordAuthManager auth;

    DiscordAuthListener(DiscordAuthManager auth) {
        this.auth = auth;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        auth.handlePreLogin(event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        auth.handleJoin(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (auth.shouldRestrict(event.getPlayer(), "block-break")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Discord認証が完了するまでブロックを破壊できません。");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (auth.shouldRestrict(event.getPlayer(), "block-place")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Discord認証が完了するまでブロックを設置できません。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (auth.shouldRestrict(event.getPlayer(), "chat")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Discord認証が完了するまでチャットできません。");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!auth.shouldRestrict(player, "command") || auth.commandAllowed(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Discord認証が完了するまでこのコマンドは使用できません。");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!auth.shouldRestrict(event.getPlayer(), "movement") || sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean sameBlock(Location from, Location to) {
        return to != null
                && from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
