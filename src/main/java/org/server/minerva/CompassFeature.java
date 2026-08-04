package org.server.minerva;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredListener;

final class CompassFeature implements Listener {
   private final Minerva plugin;
   private final NamespacedKey athleticControlKey;
   private final Map<UUID, Long> teleportBlockUntil = new ConcurrentHashMap<>();
   private final Map<UUID, Location> clickLocations = new ConcurrentHashMap<>();
   private final Map<UUID, Long> lastDiagnosticLog = new ConcurrentHashMap<>();

   CompassFeature(Minerva plugin) {
      this.plugin = plugin;
      this.athleticControlKey = new NamespacedKey(plugin, "athletic_control");
   }

   boolean handleCompassClick(PlayerInteractEvent event) {
      if (!this.isCompassClick(event)) {
         return false;
      }

      this.blockCompassClickTeleport(event);
      return true;
   }

   void updateCompassTarget(Player player) {
      Location hub = this.plugin.readLocation("hub");
      if (hub != null) {
         player.setCompassTarget(hub);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onCompassClickLate(PlayerInteractEvent event) {
      this.handleCompassClick(event);
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onCompassTeleport(PlayerTeleportEvent event) {
      Long blockedUntil = this.teleportBlockUntil.get(event.getPlayer().getUniqueId());
      if (blockedUntil != null) {
         if (System.currentTimeMillis() <= blockedUntil) {
            event.setCancelled(true);
            this.plugin
               .getLogger()
               .warning(
                  "Blocked teleport after compass click: player="
                     + event.getPlayer().getName()
                     + ", cause="
                     + event.getCause()
                     + ", from="
                     + this.formatLocation(event.getFrom())
                     + ", to="
                     + (event.getTo() == null ? "null" : this.formatLocation(event.getTo()))
               );
         } else {
            this.teleportBlockUntil.remove(event.getPlayer().getUniqueId());
            this.clickLocations.remove(event.getPlayer().getUniqueId());
         }
      }
   }

   private boolean isCompassClick(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      return item != null
         && item.getType() == Material.COMPASS
         && (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.athleticControlKey, PersistentDataType.STRING))
         && (event.getAction().isRightClick() || event.getAction().isLeftClick());
   }

   private void blockCompassClickTeleport(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      long blockedUntil = System.currentTimeMillis() + 5000L;
      this.teleportBlockUntil.put(player.getUniqueId(), blockedUntil);
      this.clickLocations.put(player.getUniqueId(), player.getLocation().clone());
      event.setCancelled(true);
      event.setUseItemInHand(Result.DENY);
      event.setUseInteractedBlock(Result.DENY);
      this.logCompassDiagnostics(player);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.restoreIfCompassTeleportLeaked(player, blockedUntil), 1L);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.restoreIfCompassTeleportLeaked(player, blockedUntil), 3L);
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.restoreIfCompassTeleportLeaked(player, blockedUntil), 10L);
   }

   private void restoreIfCompassTeleportLeaked(Player player, long blockedUntil) {
      if (player.isOnline()) {
         Long currentBlock = this.teleportBlockUntil.get(player.getUniqueId());
         Location original = this.clickLocations.get(player.getUniqueId());
         if (currentBlock != null && currentBlock == blockedUntil && original != null) {
            if (!player.getWorld().equals(original.getWorld()) || player.getLocation().distanceSquared(original) > 1.0) {
               this.plugin
                  .getLogger()
                  .warning(
                     "Compass click teleport leaked for "
                        + player.getName()
                        + "; restoring location. Current="
                        + this.formatLocation(player.getLocation())
                        + ", original="
                        + this.formatLocation(original)
                  );
               this.teleportBlockUntil.remove(player.getUniqueId());
               this.clickLocations.remove(player.getUniqueId());
               player.teleport(original);
            }
         }
      }
   }

   private void logCompassDiagnostics(Player player) {
      long now = System.currentTimeMillis();
      long last = this.lastDiagnosticLog.getOrDefault(player.getUniqueId(), 0L);
      if (now - last >= 10000L) {
         this.lastDiagnosticLog.put(player.getUniqueId(), now);
         this.plugin
            .getLogger()
            .warning(
               "Compass click detected for "
                  + player.getName()
                  + ". Plugins listening to PlayerInteractEvent: "
                  + this.listeningPlugins(PlayerInteractEvent.getHandlerList().getRegisteredListeners())
                  + ". Plugins listening to PlayerTeleportEvent: "
                  + this.listeningPlugins(PlayerTeleportEvent.getHandlerList().getRegisteredListeners())
            );
      }
   }

   private String listeningPlugins(RegisteredListener[] listeners) {
      Set<String> plugins = new HashSet<>();

      for (RegisteredListener listener : listeners) {
         if (!listener.getPlugin().equals(this.plugin)) {
            plugins.add(listener.getPlugin().getName());
         }
      }

      return plugins.isEmpty() ? "(none)" : String.join(", ", plugins);
   }

   private String formatLocation(Location location) {
      return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
   }
}
