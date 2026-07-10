package org.server.minerva;

import net.kyori.adventure.text.Component;
import org.bukkit.Axis;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        meta.lore(List.of(
                Component.text(ChatColor.GRAY + "右クリック: ブロックをサーバーポータル化"),
                Component.text(ChatColor.GRAY + "ポータルを右クリック: 移動先サーバー設定"),
                Component.text(ChatColor.GRAY + "左クリック: サーバーポータルを削除")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(minervaItemKey, PersistentDataType.STRING, "server_wand");
        item.setItemMeta(meta);
        return item;
    }

    boolean isServerWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return "server_wand".equals(item.getItemMeta().getPersistentDataContainer().get(minervaItemKey, PersistentDataType.STRING));
    }

    void handleWandClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("minerva.admin")) {
            player.sendMessage(ChatColor.RED + "権限がありません。");
            event.setCancelled(true);
            return;
        }
        Block block = resolveWandTargetBlock(event);
        if (block == null) {
            player.sendMessage(ChatColor.RED + "ブロックをクリックしてください。");
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isRightClick()) {
            if (isServerPortal(block)) {
                plugin.openServerPortalTargetUi(player, blockKey(block));
                event.setCancelled(true);
                return;
            }
            block.setType(Material.NETHER_PORTAL, false);
            applyServerPortalFacing(block, player);
            setServerPortal(block, true);
            plugin.openServerPortalTargetUi(player, blockKey(block));
            player.sendMessage(ChatColor.GREEN + "サーバーポータルを作成しました。移動先サーバーを選択してください。");
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isLeftClick()) {
            if (!isServerPortal(block)) {
                player.sendMessage(ChatColor.YELLOW + "このブロックはサーバーポータルではありません。");
                event.setCancelled(true);
                return;
            }
            setServerPortal(block, false);
            block.setType(Material.AIR, false);
            player.sendMessage(ChatColor.GREEN + "サーバーポータルを削除しました。");
            event.setCancelled(true);
        }
    }

    private Block resolveWandTargetBlock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked != null) {
            if (isServerPortal(clicked)) {
                return clicked;
            }
            if (event.getAction().isRightClick()) {
                return clicked;
            }
        }
        Block lookedPortal = lookedAtServerPortal(player);
        if (lookedPortal != null) {
            return lookedPortal;
        }
        if (clicked != null) {
            Block nearbyClickedPortal = nearbyServerPortal(clicked);
            if (nearbyClickedPortal != null) {
                return nearbyClickedPortal;
            }
        }
        Block nearbyPlayerPortal = nearestServerPortal(player.getLocation());
        if (nearbyPlayerPortal != null) {
            return nearbyPlayerPortal;
        }
        Block registeredNearby = nearestRegisteredServerPortal(player.getLocation(), 6.0D);
        if (registeredNearby != null) {
            return registeredNearby;
        }
        if (clicked != null) {
            return clicked;
        }
        return null;
    }

    boolean isServerPortal(Block block) {
        return block != null
                && block.getType() == Material.NETHER_PORTAL
                && plugin.data().getStringList("server-portals").contains(blockKey(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onServerPortalTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        if (nearestServerPortal(event.getFrom()) != null) {
            event.setCancelled(true);
            tryUseServerPortal(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onServerPortalMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        tryUseServerPortal(event.getPlayer(), true);
    }

    private boolean tryUseServerPortal(Player player, boolean notifyIfUnset) {
        long now = System.currentTimeMillis();
        long nextAllowed = portalUseCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (nextAllowed > now) {
            return false;
        }
        Block portal = nearestServerPortal(player.getLocation());
        if (portal == null) {
            return false;
        }
        String target = serverPortalTarget(portal);
        if (target == null || target.isBlank()) {
            portalUseCooldowns.put(player.getUniqueId(), now + 2000L);
            if (notifyIfUnset) {
                player.sendMessage(ChatColor.YELLOW + "このサーバーポータルの移動先が未設定です。");
            }
            return true;
        }
        portalUseCooldowns.put(player.getUniqueId(), now + 2000L);
        plugin.teleportToConfigLocation(player, target);
        return true;
    }

    private void applyServerPortalFacing(Block block, Player player) {
        if (!(block.getBlockData() instanceof Orientable orientable)) {
            return;
        }
        double dx = player.getLocation().getX() - (block.getX() + 0.5);
        double dz = player.getLocation().getZ() - (block.getZ() + 0.5);
        orientable.setAxis(Math.abs(dx) > Math.abs(dz) ? Axis.Z : Axis.X);
        block.setBlockData(orientable, false);
    }

    private boolean isNearServerPortal(Location location) {
        return nearestServerPortal(location) != null;
    }

    private Block nearestServerPortal(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return nearbyServerPortal(location.getBlock());
    }

    private Block nearbyServerPortal(Block base) {
        if (base == null) {
            return null;
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block candidate = base.getRelative(x, y, z);
                    if (isServerPortal(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Block lookedAtServerPortal(Player player) {
        Location cursor = player.getEyeLocation();
        org.bukkit.util.Vector step = cursor.getDirection().normalize().multiply(0.25D);
        for (int i = 0; i < 40; i++) {
            cursor.add(step);
            Block block = cursor.getBlock();
            if (isServerPortal(block)) {
                return block;
            }
            Block nearby = nearbyServerPortal(block);
            if (nearby != null && nearby.getLocation().add(0.5, 0.5, 0.5).distanceSquared(cursor) <= 2.25D) {
                return nearby;
            }
            for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Block relative = block.getRelative(face);
                if (isServerPortal(relative) && relative.getLocation().add(0.5, 0.5, 0.5).distanceSquared(cursor) <= 1.25D) {
                    return relative;
                }
            }
        }
        return null;
    }

    private Block nearestRegisteredServerPortal(Location origin, double radius) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        double maxDistanceSquared = radius * radius;
        Block nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (String key : plugin.data().getStringList("server-portals")) {
            Block block = blockFromKey(key);
            if (block == null || block.getWorld() == null || !block.getWorld().equals(origin.getWorld()) || block.getType() != Material.NETHER_PORTAL) {
                continue;
            }
            double distanceSquared = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(origin);
            if (distanceSquared <= maxDistanceSquared && distanceSquared < nearestDistanceSquared) {
                nearest = block;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private void setServerPortal(Block block, boolean enabled) {
        List<String> portals = new ArrayList<>(plugin.data().getStringList("server-portals"));
        Set<String> keys = portalClusterKeys(block);
        if (enabled) {
            for (String key : keys) {
                if (!portals.contains(key)) {
                    portals.add(key);
                }
            }
        } else {
            portals.removeAll(keys);
            for (String key : keys) {
                plugin.data().set(serverPortalTargetPath(key), null);
            }
        }
        plugin.data().set("server-portals", portals);
        plugin.saveData();
    }

    void setServerPortalTarget(String portalKey, String targetPath) {
        if (portalKey == null || portalKey.isBlank() || targetPath == null || targetPath.isBlank()) {
            return;
        }
        Block block = blockFromKey(portalKey);
        if (block == null) {
            plugin.data().set(serverPortalTargetPath(portalKey), targetPath);
            plugin.saveData();
            return;
        }
        for (String key : portalClusterKeys(block)) {
            plugin.data().set(serverPortalTargetPath(key), targetPath);
        }
        plugin.saveData();
    }

    private String serverPortalTarget(Block block) {
        return plugin.data().getString(serverPortalTargetPath(blockKey(block)), "");
    }

    private String serverPortalTargetPath(String key) {
        return "server-portal-targets." + key;
    }

    private Set<String> portalClusterKeys(Block origin) {
        Set<String> keys = new HashSet<>();
        collectPortalCluster(origin, keys, new HashSet<>());
        return keys;
    }

    private void collectPortalCluster(Block block, Set<String> keys, Set<String> visited) {
        if (block == null || block.getType() != Material.NETHER_PORTAL || !visited.add(blockKey(block))) {
            return;
        }
        keys.add(blockKey(block));
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            collectPortalCluster(block.getRelative(face), keys, visited);
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
            java.util.UUID worldId = java.util.UUID.fromString(parts[0]);
            org.bukkit.World world = plugin.getServer().getWorld(worldId);
            if (world == null) {
                return null;
            }
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
