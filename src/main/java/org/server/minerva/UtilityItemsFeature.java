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
            "emerald_bundle", "hub_compass", "friend_book", "teleporter", "shelf_shop_wand", "shop_wand", "server_wand", "jump_pad_wand", "chunk_protection_beacon");
    private static final int MAX_JUMP_PAD_POWER = 100;

    private final NamespacedKey minervaItemKey;
    private final NamespacedKey shopWandTypeKey;
    private final NamespacedKey shopWandCategoryKey;
    private final NamespacedKey jumpPadPowerKey;
    private final NamespacedKey jumpPadVerticalPowerKey;
    private final NamespacedKey jumpPadHorizontalPowerKey;

    UtilityItemsFeature(Minerva plugin) {
        this.minervaItemKey = new NamespacedKey(plugin, "item");
        this.shopWandTypeKey = new NamespacedKey(plugin, "shop_wand_type");
        this.shopWandCategoryKey = new NamespacedKey(plugin, "shop_wand_category");
        this.jumpPadPowerKey = new NamespacedKey(plugin, "jump_pad_power");
        this.jumpPadVerticalPowerKey = new NamespacedKey(plugin, "jump_pad_vertical_power");
        this.jumpPadHorizontalPowerKey = new NamespacedKey(plugin, "jump_pad_horizontal_power");
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

    ItemStack createShopWand(ShopWandType type, ShopCategory category) {
        return createMinervaItem(Material.BLAZE_ROD, "shop_wand", ChatColor.GOLD + "ショップワンド",
                List.of(ChatColor.GRAY + "種類: " + type.key(),
                        ChatColor.GRAY + "カテゴリ: " + category.key(),
                        ChatColor.GRAY + "右クリック: 対応ブロックをカテゴリショップ化",
                        ChatColor.GRAY + "左クリック: ショップ化を解除"), meta -> {
                    PersistentDataContainer container = meta.getPersistentDataContainer();
                    container.set(shopWandTypeKey, PersistentDataType.STRING, type.key());
                    container.set(shopWandCategoryKey, PersistentDataType.STRING, category.key());
                });
    }

    ItemStack createJumpPadWand(int verticalPower, int horizontalPower) {
        int safeVerticalPower = clampJumpPadPower(verticalPower);
        int safeHorizontalPower = clampJumpPadPower(horizontalPower);
        return createMinervaItem(Material.FEATHER, "jump_pad_wand", ChatColor.AQUA + "ジャンプパッドワンド",
                List.of(ChatColor.GRAY + "縦の強さ: " + safeVerticalPower,
                        ChatColor.GRAY + "横の強さ: " + safeHorizontalPower,
                        ChatColor.GRAY + "右クリック: ブロックをジャンプパッド化",
                        ChatColor.GRAY + "左クリック: ジャンプパッドを解除"), meta ->
                        {
                            meta.getPersistentDataContainer().set(jumpPadVerticalPowerKey, PersistentDataType.INTEGER, safeVerticalPower);
                            meta.getPersistentDataContainer().set(jumpPadHorizontalPowerKey, PersistentDataType.INTEGER, safeHorizontalPower);
                        });
    }

    ItemStack createChunkProtectionBeacon() {
        return createMinervaItem(Material.BEACON, "chunk_protection_beacon", ChatColor.AQUA + "チャンク保護ビーコン",
                List.of(ChatColor.GRAY + "設置したチャンクを保護します。",
                        ChatColor.GRAY + "通常ビーコンとは別の保護用ビーコンです。"));
    }

    int getJumpPadVerticalPower(ItemStack item) {
        if (!isMinervaItem(item, "jump_pad_wand")) {
            return 5;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        Integer power = container.get(jumpPadVerticalPowerKey, PersistentDataType.INTEGER);
        if (power != null) {
            return clampJumpPadPower(power);
        }
        Integer oldPower = container.get(jumpPadPowerKey, PersistentDataType.INTEGER);
        return oldPower == null ? 5 : oldPowerToNewPower(oldPower);
    }

    int getJumpPadHorizontalPower(ItemStack item) {
        if (!isMinervaItem(item, "jump_pad_wand")) {
            return 5;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        Integer power = container.get(jumpPadHorizontalPowerKey, PersistentDataType.INTEGER);
        if (power != null) {
            return clampJumpPadPower(power);
        }
        Integer oldPower = container.get(jumpPadPowerKey, PersistentDataType.INTEGER);
        return oldPower == null ? 5 : oldPowerToNewPower(oldPower);
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

    boolean isShopWand(ItemStack item) {
        String id = getMinervaItemId(item);
        return "shop_wand".equals(id) || "shelf_shop_wand".equals(id);
    }

    boolean isLegacyShopWand(ItemStack item) {
        return isMinervaItem(item, "shelf_shop_wand");
    }

    ShopWandType getShopWandType(ItemStack item) {
        if (!isShopWand(item) || item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(shopWandTypeKey, PersistentDataType.STRING);
        return ShopWandType.fromKey(raw);
    }

    ShopCategory getShopWandCategory(ItemStack item) {
        if (!isShopWand(item) || item == null || !item.hasItemMeta()) {
            return ShopCategory.OTHERS;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(shopWandCategoryKey, PersistentDataType.STRING);
        ShopCategory category = ShopCategory.fromKey(raw);
        return category == null ? ShopCategory.OTHERS : category;
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
        return createMinervaItem(material, id, name, lore, null);
    }

    private ItemStack createMinervaItem(Material material, String id, String name, List<String> lore, java.util.function.Consumer<ItemMeta> customizer) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(lore.stream().map(Component::text).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(minervaItemKey, PersistentDataType.STRING, id);
        if (customizer != null) {
            customizer.accept(meta);
        }
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

    private int clampJumpPadPower(int power) {
        return Math.max(1, Math.min(MAX_JUMP_PAD_POWER, power));
    }

    private int oldPowerToNewPower(int power) {
        return clampJumpPadPower(Math.max(1, Math.min(5, power)) * 2);
    }
}
