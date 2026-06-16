package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CompassFeature implements Listener {
    private final Minerva plugin;
    private final Map<UUID, Long> teleportBlockUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Location> clickLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDiagnosticLog = new ConcurrentHashMap<>();

    CompassFeature(Minerva plugin) {
        this.plugin = plugin;
    }

    boolean handleCompassClick(PlayerInteractEvent event) {
        if (!isCompassClick(event)) {
            return false;
        }
        blockCompassClickTeleport(event);
        return true;
    }

    void updateCompassTarget(Player player) {
        Location hub = plugin.readLocation("hub");
        if (hub != null) {
            player.setCompassTarget(hub);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCompassClickLate(PlayerInteractEvent event) {
        handleCompassClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCompassTeleport(PlayerTeleportEvent event) {
        Long blockedUntil = teleportBlockUntil.get(event.getPlayer().getUniqueId());
        if (blockedUntil == null) {
            return;
        }
        if (System.currentTimeMillis() <= blockedUntil) {
            event.setCancelled(true);
            plugin.getLogger().warning("Blocked teleport after compass click: player=" + event.getPlayer().getName()
                    + ", cause=" + event.getCause()
                    + ", from=" + formatLocation(event.getFrom())
                    + ", to=" + (event.getTo() == null ? "null" : formatLocation(event.getTo())));
            return;
        }
        teleportBlockUntil.remove(event.getPlayer().getUniqueId());
        clickLocations.remove(event.getPlayer().getUniqueId());
    }

    private boolean isCompassClick(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        return item != null
                && item.getType() == Material.COMPASS
                && (event.getAction().isRightClick() || event.getAction().isLeftClick());
    }

    private void blockCompassClickTeleport(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        long blockedUntil = System.currentTimeMillis() + 5000L;
        teleportBlockUntil.put(player.getUniqueId(), blockedUntil);
        clickLocations.put(player.getUniqueId(), player.getLocation().clone());
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        logCompassDiagnostics(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreIfCompassTeleportLeaked(player, blockedUntil), 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreIfCompassTeleportLeaked(player, blockedUntil), 3L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreIfCompassTeleportLeaked(player, blockedUntil), 10L);
    }

    private void restoreIfCompassTeleportLeaked(Player player, long blockedUntil) {
        if (!player.isOnline()) {
            return;
        }
        Long currentBlock = teleportBlockUntil.get(player.getUniqueId());
        Location original = clickLocations.get(player.getUniqueId());
        if (currentBlock == null || currentBlock != blockedUntil || original == null) {
            return;
        }
        if (!player.getWorld().equals(original.getWorld()) || player.getLocation().distanceSquared(original) > 1.0) {
            plugin.getLogger().warning("Compass click teleport leaked for " + player.getName()
                    + "; restoring location. Current=" + formatLocation(player.getLocation())
                    + ", original=" + formatLocation(original));
            teleportBlockUntil.remove(player.getUniqueId());
            clickLocations.remove(player.getUniqueId());
            player.teleport(original);
        }
    }

    private void logCompassDiagnostics(Player player) {
        long now = System.currentTimeMillis();
        long last = lastDiagnosticLog.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 10000L) {
            return;
        }
        lastDiagnosticLog.put(player.getUniqueId(), now);
        plugin.getLogger().warning("Compass click detected for " + player.getName()
                + ". Plugins listening to PlayerInteractEvent: " + listeningPlugins(PlayerInteractEvent.getHandlerList().getRegisteredListeners())
                + ". Plugins listening to PlayerTeleportEvent: " + listeningPlugins(PlayerTeleportEvent.getHandlerList().getRegisteredListeners()));
    }

    private String listeningPlugins(RegisteredListener[] listeners) {
        Set<String> plugins = new HashSet<>();
        for (RegisteredListener listener : listeners) {
            if (!listener.getPlugin().equals(plugin)) {
                plugins.add(listener.getPlugin().getName());
            }
        }
        return plugins.isEmpty() ? "(none)" : String.join(", ", plugins);
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
