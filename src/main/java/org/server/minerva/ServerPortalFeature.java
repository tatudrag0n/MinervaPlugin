package org.server.minerva;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Axis;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

final class ServerPortalFeature implements Listener {
   private final Minerva plugin;
   private final NamespacedKey minervaItemKey;
   private final Map<UUID, Long> portalUseCooldowns = new ConcurrentHashMap<>();

   ServerPortalFeature(Minerva plugin) {
      this.plugin = plugin;
      this.minervaItemKey = new NamespacedKey(plugin, "item");
   }

   ItemStack createServerWand() {
      ItemStack item = new ItemStack(Material.BLAZE_ROD);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(ChatColor.LIGHT_PURPLE + "サーバーワンド"));
      meta.lore(
         List.of(
            Component.text(ChatColor.GRAY + "右クリック: ブロックをサーバーポータル化"),
            Component.text(ChatColor.GRAY + "ポータルを右クリック: 移動先サーバー設定"),
            Component.text(ChatColor.GRAY + "左クリック: サーバーポータルを削除")
         )
      );
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      meta.getPersistentDataContainer().set(this.minervaItemKey, PersistentDataType.STRING, "server_wand");
      item.setItemMeta(meta);
      return item;
   }

   boolean isServerWand(ItemStack item) {
      return item != null && item.hasItemMeta()
         ? "server_wand".equals(item.getItemMeta().getPersistentDataContainer().get(this.minervaItemKey, PersistentDataType.STRING))
         : false;
   }

   void handleWandClick(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("minerva.admin")) {
         player.sendMessage(ChatColor.RED + "権限がありません。");
         event.setCancelled(true);
      } else {
         Block block = this.resolveWandTargetBlock(event);
         if (block == null) {
            player.sendMessage(ChatColor.RED + "ブロックをクリックしてください。");
            event.setCancelled(true);
         } else if (block.getType() == Material.END_PORTAL_FRAME) {
            if (event.getAction().isRightClick()) {
               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));
               player.sendMessage(ChatColor.LIGHT_PURPLE + "このエンドポータルフレームの移動先を選択してください。");
            } else if (event.getAction().isLeftClick()) {
               this.clearPortalTarget(block);
               player.sendMessage(ChatColor.GREEN + "テレポーターフレームの移動先設定を解除しました。");
            }
            event.setCancelled(true);
         } else if (event.getAction().isRightClick()) {
            if (this.isServerPortal(block)) {
               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));
               event.setCancelled(true);
            } else {
               block.setType(Material.NETHER_PORTAL, false);
               this.applyServerPortalFacing(block, player);
               this.setServerPortal(block, true);
               this.plugin.openServerPortalTargetUi(player, this.blockKey(block));
               player.sendMessage(ChatColor.GREEN + "サーバーポータルを作成しました。移動先サーバーを選択してください。");
               event.setCancelled(true);
            }
         } else {
            if (event.getAction().isLeftClick()) {
               if (!this.isServerPortal(block)) {
                  player.sendMessage(ChatColor.YELLOW + "このブロックはサーバーポータルではありません。");
                  event.setCancelled(true);
                  return;
               }

               this.setServerPortal(block, false);
               block.setType(Material.AIR, false);
               player.sendMessage(ChatColor.GREEN + "サーバーポータルを削除しました。");
               event.setCancelled(true);
            }
         }
      }
   }

   private Block resolveWandTargetBlock(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      Block clicked = event.getClickedBlock();
      if (clicked != null) {
         if (this.isServerPortal(clicked)) {
            return clicked;
         }

         if (event.getAction().isRightClick()) {
            return clicked;
         }
      }

      Block lookedPortal = this.lookedAtServerPortal(player);
      if (lookedPortal != null) {
         return lookedPortal;
      }

      if (clicked != null) {
         Block nearbyClickedPortal = this.nearbyServerPortal(clicked);
         if (nearbyClickedPortal != null) {
            return nearbyClickedPortal;
         }
      }

      Block nearbyPlayerPortal = this.nearestServerPortal(player.getLocation());
      if (nearbyPlayerPortal != null) {
         return nearbyPlayerPortal;
      } else {
         Block registeredNearby = this.nearestRegisteredServerPortal(player.getLocation(), 6.0);
         if (registeredNearby != null) {
            return registeredNearby;
         } else {
            return clicked != null ? clicked : null;
         }
      }
   }

   boolean isServerPortal(Block block) {
      return block != null && block.getType() == Material.NETHER_PORTAL && this.plugin.data().getStringList("server-portals").contains(this.blockKey(block));
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onTeleporterUse(PlayerInteractEvent event) {
      if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
         return;
      }

      ItemStack item = event.getItem();
      if (!this.isTeleporter(item)) {
         return;
      }

      // Always cancel vanilla Ender Eye behaviour: the Minerva teleporter can never be thrown.
      event.setCancelled(true);
      Block frame = event.getClickedBlock();
      if (frame == null || frame.getType() != Material.END_PORTAL_FRAME) {
         return;
      }

      String target = this.serverPortalTarget(frame);
      if (target == null || target.isBlank()) {
         event.getPlayer().sendMessage(ChatColor.YELLOW + "このエンドポータルフレームには移動先が設定されていません。");
         return;
      }

      this.setFrameEye(frame, true);
      event.getPlayer().playSound(event.getPlayer().getLocation(), org.bukkit.Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8F, 1.1F);
      this.plugin.teleportToConfigLocation(event.getPlayer(), target);

      // The inserted eye is only a short visual cue. It must never remain in the frame.
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.setFrameEye(frame, false));
   }

   private boolean isTeleporter(ItemStack item) {
      return item != null
         && item.hasItemMeta()
         && "teleporter".equals(item.getItemMeta().getPersistentDataContainer().get(this.minervaItemKey, PersistentDataType.STRING));
   }

   private void setFrameEye(Block frame, boolean eye) {
      if (frame != null && frame.getType() == Material.END_PORTAL_FRAME && frame.getBlockData() instanceof EndPortalFrame data) {
         data.setEye(eye);
         frame.setBlockData(data, false);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onServerPortalTeleport(PlayerTeleportEvent event) {
      if (event.getCause() == TeleportCause.NETHER_PORTAL) {
         if (this.nearestServerPortal(event.getFrom()) != null) {
            event.setCancelled(true);
            this.tryUseServerPortal(event.getPlayer(), true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onServerPortalMove(PlayerMoveEvent event) {
      if (event.getTo() != null && !event.getFrom().getBlock().equals(event.getTo().getBlock())) {
         this.tryUseServerPortal(event.getPlayer(), true);
      }
   }

   private boolean tryUseServerPortal(Player player, boolean notifyIfUnset) {
      long now = System.currentTimeMillis();
      long nextAllowed = this.portalUseCooldowns.getOrDefault(player.getUniqueId(), 0L);
      if (nextAllowed > now) {
         return false;
      }

      Block portal = this.nearestServerPortal(player.getLocation());
      if (portal == null) {
         return false;
      }

      String target = this.serverPortalTarget(portal);
      if (target != null && !target.isBlank()) {
         this.portalUseCooldowns.put(player.getUniqueId(), now + 2000L);
         this.plugin.teleportToConfigLocation(player, target);
         return true;
      }

      this.portalUseCooldowns.put(player.getUniqueId(), now + 2000L);
      if (notifyIfUnset) {
         player.sendMessage(ChatColor.YELLOW + "このサーバーポータルの移動先が未設定です。");
      }

      return true;
   }

   private void applyServerPortalFacing(Block block, Player player) {
      if (block.getBlockData() instanceof Orientable orientable) {
         double var8 = player.getLocation().getX() - (block.getX() + 0.5);
         double dz = player.getLocation().getZ() - (block.getZ() + 0.5);
         orientable.setAxis(Math.abs(var8) > Math.abs(dz) ? Axis.Z : Axis.X);
         block.setBlockData(orientable, false);
      }
   }

   private boolean isNearServerPortal(Location location) {
      return this.nearestServerPortal(location) != null;
   }

   private Block nearestServerPortal(Location location) {
      return location != null && location.getWorld() != null ? this.nearbyServerPortal(location.getBlock()) : null;
   }

   private Block nearbyServerPortal(Block base) {
      if (base == null) {
         return null;
      }

      for (int x = -1; x <= 1; x++) {
         for (int y = -2; y <= 2; y++) {
            for (int z = -1; z <= 1; z++) {
               Block candidate = base.getRelative(x, y, z);
               if (this.isServerPortal(candidate)) {
                  return candidate;
               }
            }
         }
      }

      return null;
   }

   private Block lookedAtServerPortal(Player player) {
      Location cursor = player.getEyeLocation();
      Vector step = cursor.getDirection().normalize().multiply(0.25);

      for (int i = 0; i < 40; i++) {
         cursor.add(step);
         Block block = cursor.getBlock();
         if (this.isServerPortal(block)) {
            return block;
         }

         Block nearby = this.nearbyServerPortal(block);
         if (nearby != null && nearby.getLocation().add(0.5, 0.5, 0.5).distanceSquared(cursor) <= 2.25) {
            return nearby;
         }

         for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block relative = block.getRelative(face);
            if (this.isServerPortal(relative) && relative.getLocation().add(0.5, 0.5, 0.5).distanceSquared(cursor) <= 1.25) {
               return relative;
            }
         }
      }

      return null;
   }

   private Block nearestRegisteredServerPortal(Location origin, double radius) {
      if (origin != null && origin.getWorld() != null) {
         double maxDistanceSquared = radius * radius;
         Block nearest = null;
         double nearestDistanceSquared = Double.MAX_VALUE;

         for (String key : this.plugin.data().getStringList("server-portals")) {
            Block block = this.blockFromKey(key);
            if (block != null && block.getWorld() != null && block.getWorld().equals(origin.getWorld()) && block.getType() == Material.NETHER_PORTAL) {
               double distanceSquared = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(origin);
               if (distanceSquared <= maxDistanceSquared && distanceSquared < nearestDistanceSquared) {
                  nearest = block;
                  nearestDistanceSquared = distanceSquared;
               }
            }
         }

         return nearest;
      } else {
         return null;
      }
   }

   private void setServerPortal(Block block, boolean enabled) {
      List<String> portals = new ArrayList<>(this.plugin.data().getStringList("server-portals"));
      Set<String> keys = this.portalClusterKeys(block);
      if (enabled) {
         for (String key : keys) {
            if (!portals.contains(key)) {
               portals.add(key);
            }
         }
      } else {
         portals.removeAll(keys);

         for (String key : keys) {
            this.plugin.data().set(this.serverPortalTargetPath(key), null);
         }
      }

      this.plugin.data().set("server-portals", portals);
      this.plugin.saveData();
   }

   void setServerPortalTarget(String portalKey, String targetPath) {
      if (portalKey != null && !portalKey.isBlank() && targetPath != null && !targetPath.isBlank()) {
         Block block = this.blockFromKey(portalKey);
         if (block == null) {
            this.plugin.data().set(this.serverPortalTargetPath(portalKey), targetPath);
            this.plugin.saveData();
         } else if (block.getType() == Material.END_PORTAL_FRAME) {
            this.plugin.data().set(this.serverPortalTargetPath(portalKey), targetPath);
            this.plugin.saveData();
         } else {
            for (String key : this.portalClusterKeys(block)) {
               this.plugin.data().set(this.serverPortalTargetPath(key), targetPath);
            }

            this.plugin.saveData();
         }
      }
   }

   private void clearPortalTarget(Block block) {
      if (block != null) {
         this.plugin.data().set(this.serverPortalTargetPath(this.blockKey(block)), null);
         this.plugin.saveData();
         this.setFrameEye(block, false);
      }
   }

   private String serverPortalTarget(Block block) {
      return this.plugin.data().getString(this.serverPortalTargetPath(this.blockKey(block)), "");
   }

   private String serverPortalTargetPath(String key) {
      return "server-portal-targets." + key;
   }

   private Set<String> portalClusterKeys(Block origin) {
      Set<String> keys = new HashSet<>();
      this.collectPortalCluster(origin, keys, new HashSet<>());
      return keys;
   }

   private void collectPortalCluster(Block block, Set<String> keys, Set<String> visited) {
      if (block != null && block.getType() == Material.NETHER_PORTAL && visited.add(this.blockKey(block))) {
         keys.add(this.blockKey(block));

         for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            this.collectPortalCluster(block.getRelative(face), keys, visited);
         }
      }
   }

   private String blockKey(Block block) {
      return block.getWorld().getUID() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
   }

   private Block blockFromKey(String key) {
      String[] parts = key.split(",");
      if (parts.length != 4) {
         return null;
      }

      try {
         UUID worldId = UUID.fromString(parts[0]);
         World world = this.plugin.getServer().getWorld(worldId);
         return world == null ? null : world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
