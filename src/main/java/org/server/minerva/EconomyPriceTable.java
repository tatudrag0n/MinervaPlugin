package org.server.minerva;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class EconomyPriceTable {
   private final Minerva plugin;
   private final Map<String, EconomyPriceTable.Entry> entries = new LinkedHashMap<>();

   EconomyPriceTable(Minerva plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.entries.clear();
      YamlConfiguration config = this.loadConfig();
      ConfigurationSection section = config.getConfigurationSection("price-table");
      if (section == null) {
         this.plugin.getLogger().warning("economy-price-table.yml does not contain price-table.");
      } else {
         for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
               String path = materialName + ".";
               int price = section.contains(path + "Price_MP") ? section.getInt(path + "Price_MP", 0) : section.getInt(path + "Price_EM", 0);
               int sell = section.contains(path + "Sell_MP") ? section.getInt(path + "Sell_MP", 0) : section.getInt(path + "Sell_EM", 0);
               EconomyPriceTable.Entry entry = new EconomyPriceTable.Entry(
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
                  section.getBoolean(path + "Auction_Allowed", false)
               );
               this.entries.put(material.name(), entry);
            }
         }

         this.plugin.getLogger().info("Loaded " + this.entries.size() + " entries from economy-price-table.yml.");
      }
   }

   private YamlConfiguration loadConfig() {
      File file = new File(this.plugin.getDataFolder(), "economy-price-table.yml");
      if (!file.exists()) {
         this.plugin.saveResource("economy-price-table.yml", false);
      }

      if (file.exists()) {
         return YamlConfiguration.loadConfiguration(file);
      }

      try (InputStream input = this.plugin.getResource("economy-price-table.yml")) {
         return input == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
      } catch (Exception e) {
         this.plugin.getLogger().warning("Could not load bundled economy-price-table.yml: " + e.getMessage());
         return new YamlConfiguration();
      }
   }

   Collection<EconomyPriceTable.Entry> entries() {
      return Collections.unmodifiableCollection(this.entries.values());
   }

   EconomyPriceTable.Entry entry(Material material) {
      return material == null ? null : this.entries.get(material.name());
   }

   int price(Material material) {
      EconomyPriceTable.Entry entry = this.entry(material);
      return entry == null ? 0 : entry.priceEm();
   }

   int sell(Material material) {
      EconomyPriceTable.Entry entry = this.entry(material);
      return entry == null ? 0 : entry.sellEm();
   }

   boolean isAuctionAllowed(Material material) {
      EconomyPriceTable.Entry entry = this.entry(material);
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
      boolean auctionAllowed
   ) {
      String barrelTierKey() {
         String normalized = this.barrelShopTier == null ? "" : this.barrelShopTier.toLowerCase(Locale.ROOT);
         return !normalized.contains("掘り出し") && !normalized.contains("bargain") ? "junk" : "bargain";
      }
   }
}
