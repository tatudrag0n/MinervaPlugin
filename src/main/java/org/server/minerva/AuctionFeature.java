package org.server.minerva;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;


final class AuctionFeature implements Listener {
    private final Minerva plugin;
    private final EconomyPriceTable priceTable;

    AuctionFeature(Minerva plugin, EconomyPriceTable priceTable) {
        this.plugin = plugin;
        this.priceTable = priceTable;
    }

    boolean isAuctionFrame(Entity entity) {
        return entity instanceof ItemFrame && plugin.data().contains(auctionPath(entity));
    }

    boolean isAuctionInteractionItem(ItemStack item) {
        return plugin.isShopWand(item) || plugin.isMinervaItem(item, "emerald_bundle");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (plugin.isShopWand(item)) {
            if (plugin.isLegacyShopWand(item)) {
                createAuction(player, frame);
            } else {
                player.sendMessage(ChatColor.RED + "額縁ショップは未実装です。額縁は既存のオークション専用です。");
            }
            event.setCancelled(true);
            return;
        }
        if (!isAuctionFrame(frame)) {
            return;
        }
        event.setCancelled(true);
        if (plugin.isMinervaItem(item, "emerald_bundle")) {
            bid(player, frame, player.isSneaking());
            return;
        }
        showAuctionInfo(player, frame);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFrameDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame) || !(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!isAuctionFrame(frame)) {
            return;
        }
        event.setCancelled(true);
        if (plugin.isLegacyShopWand(player.getInventory().getItemInMainHand())) {
            removeAuction(player, frame);
        } else {
            showAuctionInfo(player, frame);
        }
    }

    private void createAuction(Player player, ItemFrame frame) {
        if (isAuctionFrame(frame)) {
            showAuctionInfo(player, frame);
            return;
        }
        ItemStack item = frame.getItem();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "額縁に出品アイテムを入れてください。");
            return;
        }
        if (!priceTable.isAuctionAllowed(item.getType())) {
            player.sendMessage(ChatColor.RED + "このアイテムはオークション出品できません。");
            return;
        }
        String path = auctionPath(frame);
        plugin.data().set(path + ".owner", player.getUniqueId().toString());
        plugin.data().set(path + ".item", item.getType().name());
        plugin.data().set(path + ".created-at", System.currentTimeMillis());
        plugin.saveData();
        plugin.recordQuestProgress(player, "auctions", 1);
        player.sendMessage(ChatColor.GREEN + "額縁をオークション化しました。EMバンドルで右クリックすると入札できます。");
    }

    private void removeAuction(Player player, ItemFrame frame) {
        String path = auctionPath(frame);
        String owner = plugin.data().getString(path + ".owner", "");
        boolean allowed = player.hasPermission("minerva.auction.admin")
                || player.hasPermission("minerva.admin")
                || owner.equals(player.getUniqueId().toString());
        if (!allowed) {
            player.sendMessage(ChatColor.RED + "このオークションを解除できるのは出品者または管理者のみです。");
            return;
        }
        plugin.data().set(path, null);
        plugin.saveData();
        player.sendMessage(ChatColor.YELLOW + "オークションを解除しました。入札は取消扱いです。");
    }

    private void bid(Player player, ItemFrame frame, boolean largeStep) {
        int step = plugin.getConfig().getInt(largeStep ? "auction.sneak-bid-step" : "auction.bid-step", largeStep ? 1000 : 100);
        step = Math.max(1, step);
        String path = auctionPath(frame);
        int ownBid = plugin.data().getInt(path + ".bids." + player.getUniqueId(), 0);
        int nextBid = ownBid + step;
        if (plugin.getEmeralds(player.getUniqueId()) < nextBid) {
            player.sendMessage(ChatColor.RED + "所持EMを超える入札はできません。必要: " + formatNumber(nextBid) + "EM");
            return;
        }
        plugin.data().set(path + ".bids." + player.getUniqueId(), nextBid);
        updateHighestBid(path);
        plugin.saveData();
        player.sendMessage(ChatColor.GREEN + "入札しました: " + formatNumber(nextBid) + "EM");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        showAuctionInfo(player, frame);
    }

    private void updateHighestBid(String path) {
        var bids = plugin.data().getConfigurationSection(path + ".bids");
        if (bids == null) {
            plugin.data().set(path + ".highest-bidder", null);
            plugin.data().set(path + ".highest-amount", 0);
            return;
        }
        List<String> keys = bids.getKeys(false).stream().toList();
        keys.stream()
                .max(Comparator.comparingInt(key -> bids.getInt(key, 0)))
                .ifPresentOrElse(key -> {
                    plugin.data().set(path + ".highest-bidder", key);
                    plugin.data().set(path + ".highest-amount", bids.getInt(key, 0));
                }, () -> {
                    plugin.data().set(path + ".highest-bidder", null);
                    plugin.data().set(path + ".highest-amount", 0);
                });
    }

    private void showAuctionInfo(Player player, ItemFrame frame) {
        String path = auctionPath(frame);
        int highest = plugin.data().getInt(path + ".highest-amount", 0);
        int own = plugin.data().getInt(path + ".bids." + player.getUniqueId(), 0);
        ItemStack item = frame.getItem();
        String itemName = item == null || item.getType() == Material.AIR ? "なし" : item.getType().name();
        player.sendMessage(ChatColor.GOLD + "オークション: " + itemName
                + ChatColor.GRAY + " / 最高入札: " + formatNumber(highest) + "EM"
                + " / 自分: " + formatNumber(own) + "EM");
    }

    private String auctionPath(Entity entity) {
        return "auctions.frames." + entity.getUniqueId();
    }

    private String formatNumber(int value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }
}
