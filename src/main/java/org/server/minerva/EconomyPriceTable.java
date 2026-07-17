package org.server.minerva;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class EconomyPriceTable {
    private final Minerva plugin;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    EconomyPriceTable(Minerva plugin) {
        this.plugin = plugin;
    }

    void load() {
        entries.clear();
        YamlConfiguration config = loadConfig();
        ConfigurationSection section = config.getConfigurationSection("price-table");
        if (section == null) {
            plugin.getLogger().warning("economy-price-table.yml does not contain price-table.");
            return;
        }
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                continue;
            }
            String path = materialName + ".";
            int price = section.contains(path + "Price_MP")
                    ? section.getInt(path + "Price_MP", 0)
                    : section.getInt(path + "Price_EM", 0);
            int sell = section.contains(path + "Sell_MP")
                    ? section.getInt(path + "Sell_MP", 0)
                    : section.getInt(path + "Sell_EM", 0);
            Entry entry = new Entry(
                    material,
                    section.getString(path + "Item_ID", materialName.toLowerCase(Locale.ROOT)),
                    section.getString(path + "Display_Name", materialName),
                    Math.max(0, price),
                    Math.max(0, sell),
                    section.getString(path + "Resource_Class", ""),
                    section.getString(path + "Economy_Impact", ""),
                    section.getBoolean(path + "Merchant_Buy_Pool", false),
                    section.getBoolean(path + "Merchant_Sell_Pool", false),
                    Math.max(0, section.getInt(path + "Merchant_Buy_Weight", 0)),
                    Math.max(0, section.getInt(path + "Merchant_Sell_Weight", 0)),
                    section.getBoolean(path + "Barrel_Shop_Pool", false),
                    section.getString(path + "Barrel_Shop_Tier", ""),
                    Math.max(0, section.getInt(path + "Barrel_Shop_Weight", 0)),
                    section.getBoolean(path + "Auction_Allowed", false));
            entries.put(material.name(), entry);
        }
        plugin.getLogger().info("Loaded " + entries.size() + " entries from economy-price-table.yml.");
    }

    private YamlConfiguration loadConfig() {
        File file = new File(plugin.getDataFolder(), "economy-price-table.yml");
        if (!file.exists()) {
            plugin.saveResource("economy-price-table.yml", false);
        }
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        try (InputStream input = plugin.getResource("economy-price-table.yml")) {
            if (input == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load bundled economy-price-table.yml: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    Entry entry(Material material) {
        return material == null ? null : entries.get(material.name());
    }

    int price(Material material) {
        Entry entry = entry(material);
        return entry == null ? 0 : entry.priceEm();
    }

    int sell(Material material) {
        Entry entry = entry(material);
        return entry == null ? 0 : entry.sellEm();
    }

    boolean isAuctionAllowed(Material material) {
        Entry entry = entry(material);
        return entry != null && entry.auctionAllowed();
    }

    record Entry(
            Material material,
            String itemId,
            String displayName,
            int priceEm,
            int sellEm,
            String resourceClass,
            String economyImpact,
            boolean merchantBuyPool,
            boolean merchantSellPool,
            int merchantBuyWeight,
            int merchantSellWeight,
            boolean barrelShopPool,
            String barrelShopTier,
            int barrelShopWeight,
            boolean auctionAllowed) {

        String barrelTierKey() {
            String normalized = barrelShopTier == null ? "" : barrelShopTier.toLowerCase(Locale.ROOT);
            return normalized.contains("掘り出し") || normalized.contains("bargain") ? "bargain" : "junk";
        }
    }
}
