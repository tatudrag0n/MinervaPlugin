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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

final class ServerPortalFeature implements Listener {
    private final Minerva plugin;
    private final NamespacedKey minervaItemKey;

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
                Component.text(ChatColor.GRAY + "ポータルを右クリック: サーバー選択"),
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
        Block block = event.getClickedBlock();
        if (block == null) {
            player.sendMessage(ChatColor.RED + "ブロックをクリックしてください。");
            event.setCancelled(true);
            return;
        }
        if (event.getAction().isRightClick()) {
            if (isServerPortal(block)) {
                plugin.openTeleportUi(player);
                event.setCancelled(true);
                return;
            }
            block.setType(Material.NETHER_PORTAL, false);
            applyServerPortalFacing(block, player);
            setServerPortal(block, true);
            player.sendMessage(ChatColor.GREEN + "サーバーポータルを作成しました。右クリックでサーバーを選択できます。");
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

    boolean isServerPortal(Block block) {
        return block != null
                && block.getType() == Material.NETHER_PORTAL
                && plugin.data().getStringList("server-portals").contains(blockKey(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onServerPortalTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || !isNearServerPortal(event.getFrom())) {
            return;
        }
        event.setCancelled(true);
        plugin.openTeleportUi(event.getPlayer());
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
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block base = location.getBlock();
        return isServerPortal(base)
                || isServerPortal(base.getRelative(BlockFace.UP))
                || isServerPortal(base.getRelative(BlockFace.DOWN));
    }

    private void setServerPortal(Block block, boolean enabled) {
        List<String> portals = new ArrayList<>(plugin.data().getStringList("server-portals"));
        String key = blockKey(block);
        if (enabled) {
            if (!portals.contains(key)) {
                portals.add(key);
            }
        } else {
            portals.remove(key);
        }
        plugin.data().set("server-portals", portals);
        plugin.saveData();
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
