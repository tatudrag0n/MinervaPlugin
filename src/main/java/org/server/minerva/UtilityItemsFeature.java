package org.server.minerva;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class UtilityItemsFeature implements Listener {
    private static final Set<String> INITIAL_ITEM_IDS = Set.of("emerald_bundle", "hub_compass", "friend_book", "teleporter");
    private static final Set<String> FIXED_ITEM_IDS = Set.of(
            "emerald_bundle", "hub_compass", "friend_book", "teleporter", "shelf_shop_wand", "server_wand");

    private final NamespacedKey minervaItemKey;

    UtilityItemsFeature(Minerva plugin) {
        this.minervaItemKey = new NamespacedKey(plugin, "item");
    }

    void giveInitialItems(Player player) {
        giveMinervaItemIfMissing(player, "emerald_bundle", createMinervaItem(Material.BUNDLE, "emerald_bundle", ChatColor.GREEN + "エメラルドバンドル",
                List.of(ChatColor.GRAY + "左クリック: 残高確認", ChatColor.GRAY + "棚ショップに右クリック: 購入")));
        giveMinervaItemIfMissing(player, "hub_compass", createMinervaItem(Material.COMPASS, "hub_compass", ChatColor.AQUA + "中央広場コンパス",
                List.of(ChatColor.GRAY + "中央広場の方向を示します")));
        giveMinervaItemIfMissing(player, "friend_book", createFriendBook());
        giveMinervaItemIfMissing(player, "teleporter", createMinervaItem(Material.ENDER_EYE, "teleporter", ChatColor.LIGHT_PURPLE + "テレポーター",
                List.of(ChatColor.GRAY + "右クリック: サーバーショートカット")));
    }

    ItemStack createShopWand() {
        return createMinervaItem(Material.BLAZE_ROD, "shelf_shop_wand", ChatColor.GOLD + "ショップワンド",
                List.of(ChatColor.GRAY + "右クリック: 棚・樽をショップ化",
                        ChatColor.GRAY + "左クリック: ショップ化を解除",
                        ChatColor.DARK_GRAY + "樽ショップの商品はショップ化時に生成されます。"));
    }

    boolean hasMinervaItem(Player player, String id) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isMinervaItem(item, id)) {
                return true;
            }
        }
        return false;
    }

    boolean isMinervaItem(ItemStack item, String id) {
        return id.equals(getMinervaItemId(item));
    }

    String getMinervaItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.get(minervaItemKey, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!isInitialMinervaItem(stack) && !isFixedMinervaUtilityItem(stack)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.YELLOW + "Minervaの固定アイテムは捨てられません。");
    }

    private void giveMinervaItemIfMissing(Player player, String id, ItemStack item) {
        if (hasMinervaItem(player, id)) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "インベントリに空きがないため、初期アイテムを配布できませんでした: " + id);
        }
    }

    private ItemStack createFriendBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        meta.setTitle("Minerva Friend");
        meta.setAuthor("Minerva");
        meta.addPages(Component.text("Minerva Friend UI\n右クリックで開きます。"));
        meta.displayName(Component.text(ChatColor.GOLD + "フレンド"));
        meta.lore(List.of(Component.text(ChatColor.GRAY + "右クリック: フレンド UI")));
        meta.getPersistentDataContainer().set(minervaItemKey, PersistentDataType.STRING, "friend_book");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMinervaItem(Material material, String id, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(lore.stream().map(Component::text).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(minervaItemKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isFixedMinervaUtilityItem(ItemStack item) {
        String id = getMinervaItemId(item);
        return FIXED_ITEM_IDS.contains(id);
    }

    private boolean isInitialMinervaItem(ItemStack item) {
        String id = getMinervaItemId(item);
        return INITIAL_ITEM_IDS.contains(id);
    }
}
