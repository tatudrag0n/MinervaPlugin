package org.server.minerva;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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

final class UtilityItemsFeature implements Listener {
   private static final Set<String> INITIAL_ITEM_IDS = Set.of("emerald_bundle", "friend_book", "teleporter");
   private static final Set<String> FIXED_ITEM_IDS = Set.of(
      "emerald_bundle", "friend_book", "teleporter", "shelf_shop_wand", "shop_wand", "slot_wand", "server_wand", "jump_pad_wand", "chunk_protection_beacon"
   );
   private static final int MAX_JUMP_PAD_POWER = 100;
   private final NamespacedKey minervaItemKey;
   private final NamespacedKey shopWandTypeKey;
   private final NamespacedKey jumpPadPowerKey;
   private final NamespacedKey jumpPadVerticalPowerKey;
   private final NamespacedKey jumpPadHorizontalPowerKey;

   UtilityItemsFeature(Minerva plugin) {
      this.minervaItemKey = new NamespacedKey(plugin, "item");
      this.shopWandTypeKey = new NamespacedKey(plugin, "shop_wand_type");
      this.jumpPadPowerKey = new NamespacedKey(plugin, "jump_pad_power");
      this.jumpPadVerticalPowerKey = new NamespacedKey(plugin, "jump_pad_vertical_power");
      this.jumpPadHorizontalPowerKey = new NamespacedKey(plugin, "jump_pad_horizontal_power");
   }

   void giveInitialItems(Player player) {
      this.removeMinervaItems(player, "hub_compass");
      this.updateOrGiveMinervaItem(
         player,
         "emerald_bundle",
         this.createMinervaItem(
            Material.BUNDLE, "emerald_bundle", ChatColor.GREEN + "ウォレット", List.of(ChatColor.GRAY + "左クリック: MP残高確認", ChatColor.GRAY + "棚ショップ・スロットに右クリック: 使用")
         )
      );
      this.updateOrGiveMinervaItem(player, "friend_book", this.createStatusBook());
      this.giveMinervaItemIfMissing(
         player,
         "teleporter",
         this.createMinervaItem(Material.ENDER_EYE, "teleporter", ChatColor.LIGHT_PURPLE + "テレポーター", List.of(ChatColor.GRAY + "右クリック: サーバーショートカット"))
      );
   }

   ItemStack createShopWand() {
      return this.createMinervaItem(
         Material.BLAZE_ROD,
         "shelf_shop_wand",
         ChatColor.GOLD + "ショップワンド",
         List.of(ChatColor.GRAY + "右クリック: 棚・樽をショップ化", ChatColor.GRAY + "左クリック: ショップ化を解除", ChatColor.DARK_GRAY + "樽ショップの商品はショップ化時に生成されます。")
      );
   }

   ItemStack createShopWand(ShopWandType type) {
      if (type.isSlotWand()) {
         SlotMachineManager.Difficulty difficulty = type.getSlotDifficulty();
         String diffName = difficulty != null ? difficulty.name() : "";
         return this.createMinervaItem(
            Material.BLAZE_ROD,
            "slot_wand",
            ChatColor.GOLD + "スロットワンド [" + diffName + "]",
            List.of(
               ChatColor.GRAY + "難易度: " + this.getDifficultyDisplayName(difficulty),
               ChatColor.GRAY + "右クリック: 棚をスロットマシン化",
               ChatColor.GRAY + "ウォレットを持って棚を右クリックで回転"
            ),
            meta -> {
               PersistentDataContainer container = meta.getPersistentDataContainer();
               container.set(this.shopWandTypeKey, PersistentDataType.STRING, type.key());
            }
         );
      } else {
         return this.createMinervaItem(
            Material.BLAZE_ROD,
            "shop_wand",
            ChatColor.GOLD + "ショップワンド",
            List.of(ChatColor.GRAY + "種類: " + type.key(), ChatColor.GRAY + "右クリック: 対応ブロックをショップ化", ChatColor.GRAY + "左クリック: ショップ化を解除"),
            meta -> {
               PersistentDataContainer container = meta.getPersistentDataContainer();
               container.set(this.shopWandTypeKey, PersistentDataType.STRING, type.key());
            }
         );
      }
   }

   private String getDifficultyDisplayName(SlotMachineManager.Difficulty difficulty) {
      if (difficulty == null) {
         return "不明";
      }

      switch (difficulty) {
         case EASY:
            return ChatColor.GREEN + "イージー";
         case NORMAL:
            return ChatColor.YELLOW + "ノーマル";
         case HARD:
            return ChatColor.RED + "ハード";
         case EXPERT:
            return "" + ChatColor.DARK_RED + ChatColor.BOLD + "エキスパート";
         default:
            return "不明";
      }
   }

   ItemStack createJumpPadWand(int verticalPower, int horizontalPower) {
      int safeVerticalPower = this.clampJumpPadPower(verticalPower);
      int safeHorizontalPower = this.clampJumpPadPower(horizontalPower);
      return this.createMinervaItem(
         Material.FEATHER,
         "jump_pad_wand",
         ChatColor.AQUA + "ジャンプパッドワンド",
         List.of(
            ChatColor.GRAY + "縦の強さ: " + safeVerticalPower,
            ChatColor.GRAY + "横の強さ: " + safeHorizontalPower,
            ChatColor.GRAY + "右クリック: ブロックをジャンプパッド化",
            ChatColor.GRAY + "左クリック: ジャンプパッドを解除"
         ),
         meta -> {
            meta.getPersistentDataContainer().set(this.jumpPadVerticalPowerKey, PersistentDataType.INTEGER, safeVerticalPower);
            meta.getPersistentDataContainer().set(this.jumpPadHorizontalPowerKey, PersistentDataType.INTEGER, safeHorizontalPower);
         }
      );
   }

   ItemStack createChunkProtectionBeacon() {
      return this.createMinervaItem(
         Material.BEACON,
         "chunk_protection_beacon",
         ChatColor.AQUA + "チャンク保護ビーコン",
         List.of(ChatColor.GRAY + "設置したチャンクを保護します。", ChatColor.GRAY + "通常ビーコンとは別の保護用ビーコンです。")
      );
   }

   int getJumpPadVerticalPower(ItemStack item) {
      if (!this.isMinervaItem(item, "jump_pad_wand")) {
         return 5;
      }

      PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
      Integer power = (Integer)container.get(this.jumpPadVerticalPowerKey, PersistentDataType.INTEGER);
      if (power != null) {
         return this.clampJumpPadPower(power);
      }

      Integer oldPower = (Integer)container.get(this.jumpPadPowerKey, PersistentDataType.INTEGER);
      return oldPower == null ? 5 : this.oldPowerToNewPower(oldPower);
   }

   int getJumpPadHorizontalPower(ItemStack item) {
      if (!this.isMinervaItem(item, "jump_pad_wand")) {
         return 5;
      }

      PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
      Integer power = (Integer)container.get(this.jumpPadHorizontalPowerKey, PersistentDataType.INTEGER);
      if (power != null) {
         return this.clampJumpPadPower(power);
      }

      Integer oldPower = (Integer)container.get(this.jumpPadPowerKey, PersistentDataType.INTEGER);
      return oldPower == null ? 5 : this.oldPowerToNewPower(oldPower);
   }

   boolean hasMinervaItem(Player player, String id) {
      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isMinervaItem(item, id)) {
            return true;
         }
      }

      return false;
   }

   boolean isMinervaItem(ItemStack item, String id) {
      return id.equals(this.getMinervaItemId(item));
   }

   boolean isShopWand(ItemStack item) {
      String id = this.getMinervaItemId(item);
      return "shop_wand".equals(id) || "shelf_shop_wand".equals(id) || "slot_wand".equals(id);
   }

   boolean isLegacyShopWand(ItemStack item) {
      return this.isMinervaItem(item, "shelf_shop_wand");
   }

   ShopWandType getShopWandType(ItemStack item) {
      if (this.isShopWand(item) && item != null && item.hasItemMeta()) {
         String raw = (String)item.getItemMeta().getPersistentDataContainer().get(this.shopWandTypeKey, PersistentDataType.STRING);
         return ShopWandType.fromKey(raw);
      } else {
         return null;
      }
   }

   String getMinervaItemId(ItemStack item) {
      if (item != null && item.hasItemMeta()) {
         PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
         return (String)container.get(this.minervaItemKey, PersistentDataType.STRING);
      } else {
         return null;
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onDropItem(PlayerDropItemEvent event) {
      ItemStack stack = event.getItemDrop().getItemStack();
      if (this.isInitialMinervaItem(stack) || this.isFixedMinervaUtilityItem(stack)) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(ChatColor.YELLOW + "Minervaの固定アイテムは捨てられません。");
      }
   }

   private void giveMinervaItemIfMissing(Player player, String id, ItemStack item) {
      if (!this.hasMinervaItem(player, id)) {
         Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack[]{item});
         if (!leftovers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "インベントリに空きがないため、初期アイテムを配布できませんでした: " + id);
         }
      }
   }

   private void updateOrGiveMinervaItem(Player player, String id, ItemStack template) {
      boolean found = false;

      for (ItemStack item : player.getInventory().getContents()) {
         if (this.isMinervaItem(item, id)) {
            ItemMeta meta = item.getItemMeta();
            ItemMeta templateMeta = template.getItemMeta();
            meta.displayName(templateMeta.displayName());
            meta.lore(templateMeta.lore());
            if (meta instanceof BookMeta bookMeta && templateMeta instanceof BookMeta templateBookMeta) {
               bookMeta.setTitle(templateBookMeta.getTitle());
               bookMeta.setAuthor(templateBookMeta.getAuthor());
               bookMeta.pages(templateBookMeta.pages());
            }

            item.setItemMeta(meta);
            found = true;
         }
      }

      if (!found) {
         this.giveMinervaItemIfMissing(player, id, template);
      }
   }

   private void removeMinervaItems(Player player, String id) {
      ItemStack[] contents = player.getInventory().getContents();

      for (int slot = 0; slot < contents.length; slot++) {
         if (this.isMinervaItem(contents[slot], id)) {
            player.getInventory().setItem(slot, null);
         }
      }
   }

   private ItemStack createStatusBook() {
      ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta)item.getItemMeta();
      meta.setTitle("ステータス");
      meta.setAuthor("Minerva");
      meta.addPages(new Component[]{Component.text("Minerva Status UI\n右クリックで開きます。")});
      meta.displayName(Component.text(ChatColor.GOLD + "ステータス"));
      meta.lore(List.of(Component.text(ChatColor.GRAY + "右クリック: ステータス UI")));
      meta.getPersistentDataContainer().set(this.minervaItemKey, PersistentDataType.STRING, "friend_book");
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createMinervaItem(Material material, String id, String name, List<String> lore) {
      return this.createMinervaItem(material, id, name, lore, null);
   }

   private ItemStack createMinervaItem(Material material, String id, String name, List<String> lore, Consumer<ItemMeta> customizer) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      meta.displayName(Component.text(name));
      meta.lore(lore.stream().map(Component::text).toList());
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
      meta.getPersistentDataContainer().set(this.minervaItemKey, PersistentDataType.STRING, id);
      if (customizer != null) {
         customizer.accept(meta);
      }

      item.setItemMeta(meta);
      return item;
   }

   private boolean isFixedMinervaUtilityItem(ItemStack item) {
      String id = this.getMinervaItemId(item);
      return id != null && FIXED_ITEM_IDS.contains(id);
   }

   private boolean isInitialMinervaItem(ItemStack item) {
      String id = this.getMinervaItemId(item);
      return id != null && INITIAL_ITEM_IDS.contains(id);
   }

   private int clampJumpPadPower(int power) {
      return Math.max(1, Math.min(100, power));
   }

   private int oldPowerToNewPower(int power) {
      return this.clampJumpPadPower(Math.max(1, Math.min(5, power)) * 2);
   }
}
