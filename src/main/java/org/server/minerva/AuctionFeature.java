package org.server.minerva;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

final class AuctionFeature implements Listener {
   private final Minerva plugin;
   private final EconomyPriceTable priceTable;

   AuctionFeature(Minerva plugin, EconomyPriceTable priceTable) {
      this.plugin = plugin;
      this.priceTable = priceTable;
   }

   boolean isAuctionFrame(Entity entity) {
      return entity instanceof ItemFrame && this.plugin.data().contains(this.auctionPath(entity));
   }

   boolean isAuctionInteractionItem(ItemStack item) {
      return this.plugin.isShopWand(item) || this.plugin.isMinervaItem(item, "emerald_bundle");
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFrameInteract(PlayerInteractEntityEvent event) {
      if (event.getHand() == EquipmentSlot.HAND && event.getRightClicked() instanceof ItemFrame frame) {
         Player player = event.getPlayer();
         ItemStack item = player.getInventory().getItemInMainHand();
         if (this.plugin.isShopWand(item)) {
            if (this.plugin.isLegacyShopWand(item)) {
               this.createAuction(player, frame);
            } else {
               player.sendMessage(ChatColor.RED + "額縁ショップは未実装です。額縁は既存のオークション専用です。");
            }

            event.setCancelled(true);
         } else if (this.isAuctionFrame(frame)) {
            event.setCancelled(true);
            if (this.plugin.isMinervaItem(item, "emerald_bundle")) {
               this.bid(player, frame, player.isSneaking());
            } else {
               this.showAuctionInfo(player, frame);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onFrameDamage(EntityDamageByEntityEvent event) {
      if (event.getEntity() instanceof ItemFrame frame && event.getDamager() instanceof Player player) {
         if (this.isAuctionFrame(frame)) {
            event.setCancelled(true);
            if (this.plugin.isLegacyShopWand(player.getInventory().getItemInMainHand())) {
               this.removeAuction(player, frame);
            } else {
               this.showAuctionInfo(player, frame);
            }
         }
      }
   }

   private void createAuction(Player player, ItemFrame frame) {
      if (this.isAuctionFrame(frame)) {
         this.showAuctionInfo(player, frame);
      } else {
         ItemStack item = frame.getItem();
         if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "額縁に出品アイテムを入れてください。");
         } else if (!this.priceTable.isAuctionAllowed(item.getType())) {
            player.sendMessage(ChatColor.RED + "このアイテムはオークション出品できません。");
         } else {
            String path = this.auctionPath(frame);
            this.plugin.data().set(path + ".owner", player.getUniqueId().toString());
            this.plugin.data().set(path + ".item", item.getType().name());
            this.plugin.data().set(path + ".created-at", System.currentTimeMillis());
            this.plugin.saveData();
            this.plugin.recordQuestProgress(player, "auctions", 1);
            player.sendMessage(ChatColor.GREEN + "額縁をオークション化しました。EMバンドルで右クリックすると入札できます。");
         }
      }
   }

   private void removeAuction(Player player, ItemFrame frame) {
      String path = this.auctionPath(frame);
      String owner = this.plugin.data().getString(path + ".owner", "");
      boolean allowed = player.hasPermission("minerva.auction.admin") || player.hasPermission("minerva.admin") || owner.equals(player.getUniqueId().toString());
      if (!allowed) {
         player.sendMessage(ChatColor.RED + "このオークションを解除できるのは出品者または管理者のみです。");
      } else {
         this.plugin.data().set(path, null);
         this.plugin.saveData();
         player.sendMessage(ChatColor.YELLOW + "オークションを解除しました。入札は取消扱いです。");
      }
   }

   private void bid(Player player, ItemFrame frame, boolean largeStep) {
      int step = this.plugin.getConfig().getInt(largeStep ? "auction.sneak-bid-step" : "auction.bid-step", largeStep ? 1000 : 100);
      step = Math.max(1, step);
      String path = this.auctionPath(frame);
      int ownBid = this.plugin.data().getInt(path + ".bids." + player.getUniqueId(), 0);
      int nextBid = ownBid + step;
      if (this.plugin.getEmeralds(player.getUniqueId()) < nextBid) {
         player.sendMessage(ChatColor.RED + "所持EMを超える入札はできません。必要: " + this.formatNumber(nextBid) + "EM");
      } else {
         this.plugin.data().set(path + ".bids." + player.getUniqueId(), nextBid);
         this.updateHighestBid(path);
         this.plugin.saveData();
         player.sendMessage(ChatColor.GREEN + "入札しました: " + this.formatNumber(nextBid) + "EM");
         player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.4F);
         this.showAuctionInfo(player, frame);
      }
   }

   private void updateHighestBid(String path) {
      ConfigurationSection bids = this.plugin.data().getConfigurationSection(path + ".bids");
      if (bids == null) {
         this.plugin.data().set(path + ".highest-bidder", null);
         this.plugin.data().set(path + ".highest-amount", 0);
      } else {
         List<String> keys = bids.getKeys(false).stream().toList();
         keys.stream().max(Comparator.comparingInt(key -> bids.getInt(key, 0))).ifPresentOrElse(key -> {
            this.plugin.data().set(path + ".highest-bidder", key);
            this.plugin.data().set(path + ".highest-amount", bids.getInt(key, 0));
         }, () -> {
            this.plugin.data().set(path + ".highest-bidder", null);
            this.plugin.data().set(path + ".highest-amount", 0);
         });
      }
   }

   private void showAuctionInfo(Player player, ItemFrame frame) {
      String path = this.auctionPath(frame);
      int highest = this.plugin.data().getInt(path + ".highest-amount", 0);
      int own = this.plugin.data().getInt(path + ".bids." + player.getUniqueId(), 0);
      ItemStack item = frame.getItem();
      String itemName = item != null && item.getType() != Material.AIR ? item.getType().name() : "なし";
      player.sendMessage(
         ChatColor.GOLD + "オークション: " + itemName + ChatColor.GRAY + " / 最高入札: " + this.formatNumber(highest) + "EM / 自分: " + this.formatNumber(own) + "EM"
      );
   }

   private String auctionPath(Entity entity) {
      return "auctions.frames." + entity.getUniqueId();
   }

   private String formatNumber(int value) {
      return String.format(Locale.ROOT, "%,d", value);
   }
}
