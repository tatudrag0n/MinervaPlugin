package org.server.minerva;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

enum FfaKit {
    BOW("bow", "§aアーチャー", Material.BOW),
    SWORD("sword", "§cソードマン", Material.DIAMOND_SWORD),
    SHIELD("shield", "§9シールダー", Material.SHIELD),
    SPEAR("spear", "§eランサー", Material.IRON_SPEAR),
    AXE("axe", "§6ウォリアー", Material.NETHERITE_AXE),
    CROSSBOW("crossbow", "§dリボルバー", Material.CROSSBOW);

    private final String key;
    private final String defaultDisplayName;
    private final Material icon;

    FfaKit(String key, String defaultDisplayName, Material icon) {
        this.key = key;
        this.defaultDisplayName = defaultDisplayName;
        this.icon = icon;
    }

    String key() {
        return key;
    }

    String displayName(FfaConfig config) {
        return configValue(config, "display-name", defaultDisplayName);
    }

    Material icon() {
        return icon;
    }

    Material icon(FfaConfig config) {
        return material(configValue(config, "icon", icon.name()), icon);
    }

    List<Component> details(FfaConfig config, Minerva plugin) {
        List<Component> lines = loadoutComponents(config, plugin);
        lines.add(Component.text("クリックでこのキットを選択", NamedTextColor.YELLOW));
        return lines;
    }

    void applyTo(PlayerInventory inventory, FfaConfig config, Minerva plugin) {
        inventory.clear();
        inventory.setArmorContents(armor(config, this));
        inventory.setItemInOffHand(null);
        switch (this) {
            case BOW -> {
                inventory.addItem(named(material(configValue(config, "weapon", "iron_sword"), Material.IRON_SWORD), displayName(config)));
                if (plugin.getConfig().getBoolean(config.kitPath(this, "bow"), true)) {
                    inventory.addItem(named(Material.BOW, ChatColor.GREEN + "FFA Bow"));
                }
                inventory.addItem(new ItemStack(Material.ARROW, amount(config, "arrows", 64)));
            }
            case SWORD -> inventory.addItem(named(material(configValue(config, "weapon", "diamond_sword"), Material.DIAMOND_SWORD), displayName(config)));
            case SHIELD -> {
                inventory.addItem(named(material(configValue(config, "weapon", "iron_sword"), Material.IRON_SWORD), displayName(config)));
                if (plugin.getConfig().getBoolean(config.kitPath(this, "shield"), true)) {
                    inventory.setItemInOffHand(named(Material.SHIELD, ChatColor.BLUE + "FFA Shield"));
                }
            }
            case SPEAR -> {
                Material spear = spearMaterial(config, plugin, true);
                if (spear == null) {
                    return;
                }
                inventory.addItem(named(spear, displayName(config)));
                String backup = configValue(config, "backup-weapon", "stone_sword");
                if (!backup.isBlank()) {
                    inventory.addItem(named(material(backup, Material.STONE_SWORD), ChatColor.GRAY + "FFA Backup"));
                }
            }
            case AXE -> inventory.addItem(named(material(configValue(config, "weapon", "diamond_axe"), Material.DIAMOND_AXE), displayName(config)));
            case CROSSBOW -> {
                inventory.addItem(named(material(configValue(config, "weapon", "stone_sword"), Material.STONE_SWORD), ChatColor.GRAY + "FFA Sidearm"));
                int loaded = Math.max(1, Math.min(9, amount(config, "loaded-crossbows", 6)));
                for (int i = 0; i < loaded; i++) {
                    inventory.addItem(loadedCrossbow(config));
                }
                inventory.addItem(new ItemStack(Material.ARROW, amount(config, "arrows", 64)));
            }
        }
        List<ItemStack> foods = configuredFoodItems(config, this);
        if (foods.isEmpty()) {
            int legacyFood = amount(config, "food", 16);
            if (legacyFood > 0) {
                inventory.addItem(new ItemStack(Material.COOKED_BEEF, legacyFood));
            }
        } else {
            for (ItemStack food : foods) {
                inventory.addItem(food);
            }
        }
        for (ItemStack item : configuredItems(config, this)) {
            inventory.addItem(item);
        }
        for (ItemStack potion : configuredPotions(config, this)) {
            inventory.addItem(potion);
        }
    }

    void applyStandEquipment(EntityEquipment equipment, FfaConfig config, Minerva plugin) {
        if (equipment == null) {
            return;
        }
        equipment.setArmorContents(armor(config, this));
        equipment.setItemInOffHand(null);
        Material hand = switch (this) {
            case BOW -> Material.BOW;
            case SWORD -> material(configValue(config, "weapon", "diamond_sword"), Material.DIAMOND_SWORD);
            case SHIELD -> material(configValue(config, "weapon", "iron_sword"), Material.IRON_SWORD);
            case SPEAR -> {
                Material spear = spearMaterial(config, plugin, false);
                yield spear == null ? Material.BARRIER : spear;
            }
            case AXE -> material(configValue(config, "weapon", "diamond_axe"), Material.DIAMOND_AXE);
            case CROSSBOW -> Material.CROSSBOW;
        };
        equipment.setItemInMainHand(named(hand, displayName(config)));
        if (this == SHIELD) {
            equipment.setItemInOffHand(new ItemStack(Material.SHIELD));
        }
    }

    Material spearMaterial(FfaConfig config, Minerva plugin, boolean notify) {
        Material configured = Material.matchMaterial(configValue(config, "weapon", "iron_spear").toUpperCase(Locale.ROOT));
        if (configured != null && configured.name().endsWith("_SPEAR")) {
            return configured;
        }
        boolean allowFallback = plugin.getConfig().getBoolean(config.kitPath(this, "allow-fallback"), false);
        if (allowFallback) {
            Material fallback = Material.matchMaterial(configValue(config, "fallback-weapon", "").toUpperCase(Locale.ROOT));
            if (fallback != null) {
                plugin.getLogger().warning("FFA spear fallback is enabled. Using " + fallback.name() + " instead of a spear.");
                return fallback;
            }
        }
        if (notify) {
            plugin.getLogger().warning("FFA spear material was not found: " + configValue(config, "weapon", "iron_spear"));
        }
        return null;
    }

    static FfaKit fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (FfaKit kit : values()) {
            if (kit.key.equalsIgnoreCase(key)) {
                return kit;
            }
        }
        return null;
    }

    private String defaultArmorTier() {
        return switch (this) {
            case BOW, SPEAR, CROSSBOW -> "chainmail";
            default -> "iron";
        };
    }

    private String defaultWeapon() {
        return switch (this) {
            case AXE -> "diamond_axe";
            case BOW, SHIELD -> "iron_sword";
            case CROSSBOW -> "stone_sword";
            case SPEAR -> "iron_spear";
            case SWORD -> "diamond_sword";
        };
    }

    private String configValue(FfaConfig config, String field, String fallback) {
        String value = config.plugin().getConfig().getString(config.kitPath(this, field), fallback);
        return value == null ? fallback : value;
    }

    private int amount(FfaConfig config, String field, int fallback) {
        return Math.max(0, config.plugin().getConfig().getInt(config.kitPath(this, field), fallback));
    }

    private static ItemStack loadedCrossbow(FfaConfig config) {
        ItemStack item = named(Material.CROSSBOW, ChatColor.LIGHT_PURPLE + "Loaded FFA Crossbow");
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof CrossbowMeta crossbowMeta) {
            crossbowMeta.addChargedProjectile(new ItemStack(Material.ARROW));
            int quickCharge = Math.max(0, Math.min(3, config.plugin().getConfig().getInt(config.kitPath(CROSSBOW, "quick-charge-level"), 1)));
            if (quickCharge > 0) {
                crossbowMeta.addEnchant(Enchantment.QUICK_CHARGE, quickCharge, true);
            }
            if (config.plugin().getConfig().getBoolean(config.kitPath(CROSSBOW, "multishot"), false)) {
                crossbowMeta.addEnchant(Enchantment.MULTISHOT, 1, true);
            }
            int piercing = Math.max(0, Math.min(4, config.plugin().getConfig().getInt(config.kitPath(CROSSBOW, "piercing-level"), 0)));
            if (piercing > 0) {
                crossbowMeta.addEnchant(Enchantment.PIERCING, piercing, true);
            }
            item.setItemMeta(crossbowMeta);
        }
        return item;
    }

    private static ItemStack[] armor(FfaConfig config, FfaKit kit) {
        ItemStack[] tierArmor = armorByTier(kit.configValue(config, "armor-tier", kit.defaultArmorTier()));
        ItemStack[] byPiece = armorPieces(config, kit, tierArmor);
        if (byPiece != null) {
            return byPiece;
        }
        return tierArmor;
    }

    private static ItemStack[] armorByTier(String tier) {
        String normalized = tier == null ? "" : tier.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "leather" -> new ItemStack[]{
                    new ItemStack(Material.LEATHER_BOOTS),
                    new ItemStack(Material.LEATHER_LEGGINGS),
                    new ItemStack(Material.LEATHER_CHESTPLATE),
                    new ItemStack(Material.LEATHER_HELMET)};
            case "chain", "chainmail" -> new ItemStack[]{
                    new ItemStack(Material.CHAINMAIL_BOOTS),
                    new ItemStack(Material.CHAINMAIL_LEGGINGS),
                    new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                    new ItemStack(Material.CHAINMAIL_HELMET)};
            case "gold", "golden" -> new ItemStack[]{
                    new ItemStack(Material.GOLDEN_BOOTS),
                    new ItemStack(Material.GOLDEN_LEGGINGS),
                    new ItemStack(Material.GOLDEN_CHESTPLATE),
                    new ItemStack(Material.GOLDEN_HELMET)};
            case "diamond" -> new ItemStack[]{
                    new ItemStack(Material.DIAMOND_BOOTS),
                    new ItemStack(Material.DIAMOND_LEGGINGS),
                    new ItemStack(Material.DIAMOND_CHESTPLATE),
                    new ItemStack(Material.DIAMOND_HELMET)};
            case "netherite" -> new ItemStack[]{
                    new ItemStack(Material.NETHERITE_BOOTS),
                    new ItemStack(Material.NETHERITE_LEGGINGS),
                    new ItemStack(Material.NETHERITE_CHESTPLATE),
                    new ItemStack(Material.NETHERITE_HELMET)};
            default -> new ItemStack[]{
                    new ItemStack(Material.IRON_BOOTS),
                    new ItemStack(Material.IRON_LEGGINGS),
                    new ItemStack(Material.IRON_CHESTPLATE),
                    new ItemStack(Material.IRON_HELMET)};
        };
    }

    private static ItemStack[] armorPieces(FfaConfig config, FfaKit kit, ItemStack[] fallback) {
        ConfigurationSection section = config.plugin().getConfig().getConfigurationSection("ffa.kits." + kit.key() + ".armor");
        if (section == null) {
            return null;
        }
        return new ItemStack[]{
                armorPiece(section, "boots", fallback[0]),
                armorPiece(section, "leggings", fallback[1]),
                armorPiece(section, "chestplate", fallback[2]),
                armorPiece(section, "helmet", fallback[3])};
    }

    private static ItemStack armorPiece(ConfigurationSection section, String key, ItemStack fallback) {
        String raw = section.getString(key, "");
        if (raw == null || raw.isBlank()) {
            return fallback == null ? null : fallback.clone();
        }
        Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null || material == Material.AIR ? fallback == null ? null : fallback.clone() : new ItemStack(material);
    }

    private List<Component> loadoutComponents(FfaConfig config, Minerva plugin) {
        List<Component> lines = new ArrayList<>();
        for (ItemStack item : armor(config, this)) {
            addSummary(lines, item);
        }
        switch (this) {
            case BOW -> {
                addSummary(lines, material(configValue(config, "weapon", "iron_sword"), Material.IRON_SWORD), 1);
                if (plugin.getConfig().getBoolean(config.kitPath(this, "bow"), true)) {
                    addSummary(lines, Material.BOW, 1);
                }
                addSummary(lines, Material.ARROW, amount(config, "arrows", 64));
            }
            case SWORD -> addSummary(lines, material(configValue(config, "weapon", "diamond_sword"), Material.DIAMOND_SWORD), 1);
            case SHIELD -> {
                addSummary(lines, material(configValue(config, "weapon", "iron_sword"), Material.IRON_SWORD), 1);
                if (plugin.getConfig().getBoolean(config.kitPath(this, "shield"), true)) {
                    addSummary(lines, Material.SHIELD, 1);
                }
            }
            case SPEAR -> {
                Material spear = spearMaterial(config, plugin, false);
                addSummary(lines, spear == null ? Material.BARRIER : spear, 1);
                Material backup = material(configValue(config, "backup-weapon", "stone_sword"), null);
                if (backup != null) {
                    addSummary(lines, backup, 1);
                }
            }
            case AXE -> addSummary(lines, material(configValue(config, "weapon", "diamond_axe"), Material.DIAMOND_AXE), 1);
            case CROSSBOW -> {
                addSummary(lines, material(configValue(config, "weapon", "stone_sword"), Material.STONE_SWORD), 1);
                addSummary(lines, Material.CROSSBOW, Math.max(1, Math.min(9, amount(config, "loaded-crossbows", 6))));
                addSummary(lines, Material.ARROW, amount(config, "arrows", 64));
            }
        }
        for (ItemStack item : configuredFoodItems(config, this)) {
            addSummary(lines, item);
        }
        if (configuredFoodItems(config, this).isEmpty()) {
            int legacyFood = amount(config, "food", 16);
            if (legacyFood > 0) {
                addSummary(lines, Material.COOKED_BEEF, legacyFood);
            }
        }
        for (ItemStack item : configuredItems(config, this)) {
            addSummary(lines, item);
        }
        lines.addAll(potionComponents(config, this));
        return lines;
    }

    private static void addSummary(List<Component> lines, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            addSummary(lines, item.getType(), item.getAmount());
        }
    }

    private static void addSummary(List<Component> lines, Material material, int amount) {
        if (material != null && material != Material.AIR && amount > 0) {
            Component line = Component.translatable(material.translationKey()).color(NamedTextColor.GRAY);
            if (amount > 1) {
                line = line.append(Component.text(" x" + amount, NamedTextColor.GRAY));
            }
            lines.add(line);
        }
    }

    private static List<ItemStack> configuredItems(FfaConfig config, FfaKit kit) {
        List<ItemStack> items = new ArrayList<>();
        List<?> rawItems = config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".items", List.of());
        for (Object raw : rawItems) {
            ItemStack item = parseItem(raw);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static List<ItemStack> configuredFoodItems(FfaConfig config, FfaKit kit) {
        List<ItemStack> items = new ArrayList<>();
        List<?> rawItems = config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".food-items", List.of());
        for (Object raw : rawItems) {
            ItemStack item = parseItem(raw);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static List<String> foodSummaries(FfaConfig config, FfaKit kit) {
        List<String> summaries = new ArrayList<>();
        List<?> rawItems = config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".food-items", List.of());
        if (rawItems.isEmpty()) {
            int legacyFood = kit.amount(config, "food", 16);
            if (legacyFood > 0) {
                summaries.add("COOKED_BEEF x" + legacyFood);
            }
            return summaries;
        }
        for (Object raw : rawItems) {
            ItemStack item = parseItem(raw);
            if (item != null) {
                summaries.add(item.getType().name() + " x" + item.getAmount());
            }
        }
        return summaries;
    }

    private static List<String> itemSummaries(FfaConfig config, FfaKit kit) {
        List<String> summaries = new ArrayList<>();
        List<?> rawItems = config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".items", List.of());
        for (Object raw : rawItems) {
            ItemStack item = parseItem(raw);
            if (item != null) {
                summaries.add(item.getType().name() + " x" + item.getAmount());
            }
        }
        return summaries;
    }

    private static ItemStack parseItem(Object raw) {
        if (raw instanceof String text) {
            String[] parts = text.split(":");
            Material material = material(parts[0], null);
            if (material == null) {
                return null;
            }
            int amount = parts.length > 1 ? parsePositiveInt(parts[1], 1) : 1;
            return new ItemStack(material, Math.min(64, amount));
        }
        if (raw instanceof Map<?, ?> map) {
            Material material = material(String.valueOf(map.get("material")), null);
            if (material == null) {
                return null;
            }
            int amount = parsePositiveInt(mapValue(map, "amount", "1"), 1);
            String name = map.containsKey("name") ? String.valueOf(map.get("name")) : "";
            ItemStack item = new ItemStack(material, Math.min(64, amount));
            if (!name.isBlank()) {
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(name));
                item.setItemMeta(meta);
            }
            return item;
        }
        return null;
    }

    private static List<ItemStack> configuredPotions(FfaConfig config, FfaKit kit) {
        List<ItemStack> potions = new ArrayList<>();
        List<?> rawPotions = config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".potions", List.of());
        for (Object raw : rawPotions) {
            ItemStack potion = parsePotion(raw);
            if (potion != null) {
                potions.add(potion);
            }
        }
        return potions;
    }

    private static List<Component> potionComponents(FfaConfig config, FfaKit kit) {
        List<Component> summaries = new ArrayList<>();
        List<?> rawPotions = config.plugin().getConfig().getList("ffa.kits." + kit.key() + ".potions", List.of());
        for (Object raw : rawPotions) {
            Component summary = potionSummary(raw);
            if (summary != null) {
                summaries.add(summary);
            }
        }
        return summaries;
    }

    private static Component potionSummary(Object raw) {
        if (raw instanceof String text) {
            String[] parts = text.split(":");
            if (parts.length == 0 || parts[0].isBlank()) {
                return null;
            }
            boolean splash = parts[0].startsWith("splash_");
            String effectName = parts[0].replaceFirst("^splash_", "").toUpperCase(Locale.ROOT);
            String level = parts.length > 1 ? parts[1] : "1";
            String seconds = parts.length > 2 ? parts[2] : "30";
            Material potion = splash ? Material.SPLASH_POTION : Material.POTION;
            return Component.translatable(potion.translationKey()).color(NamedTextColor.GRAY)
                    .append(Component.text("(" + potionEffectName(effectName) + " Lv" + level + " " + seconds + "秒)", NamedTextColor.GRAY));
        }
        if (raw instanceof Map<?, ?> map) {
            String effect = mapValue(map, "effect", "");
            if (effect.isBlank()) {
                return null;
            }
            Material material = material(mapValue(map, "material", "potion"), Material.POTION);
            return Component.translatable(material.translationKey()).color(NamedTextColor.GRAY)
                    .append(Component.text("(" + potionEffectName(effect) + " Lv" + mapValue(map, "level", "1")
                            + " " + mapValue(map, "seconds", "30") + "秒)", NamedTextColor.GRAY));
        }
        return null;
    }

    private static String potionEffectName(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "speed" -> "移動速度上昇";
            case "instant_health", "heal" -> "即時回復";
            case "poison" -> "毒";
            case "slowness", "slow" -> "移動速度低下";
            case "strength", "increase_damage" -> "攻撃力上昇";
            case "regeneration" -> "再生能力";
            case "resistance", "damage_resistance" -> "耐性";
            default -> raw.toUpperCase(Locale.ROOT);
        };
    }

    private static ItemStack parsePotion(Object raw) {
        if (raw instanceof String text) {
            String[] parts = text.split(":");
            String effectName = parts.length > 0 ? parts[0] : "";
            int level = parts.length > 1 ? parsePositiveInt(parts[1], 1) : 1;
            int seconds = parts.length > 2 ? parsePositiveInt(parts[2], 30) : 30;
            Material material = effectName.startsWith("splash_") ? Material.SPLASH_POTION : Material.POTION;
            return potion(material, effectName.replaceFirst("^splash_", ""), level, seconds, 1);
        }
        if (raw instanceof Map<?, ?> map) {
            String effect = mapValue(map, "effect", "");
            Material material = material(mapValue(map, "material", "potion"), Material.POTION);
            int level = parsePositiveInt(mapValue(map, "level", "1"), 1);
            int seconds = parsePositiveInt(mapValue(map, "seconds", "30"), 30);
            int amount = parsePositiveInt(mapValue(map, "amount", "1"), 1);
            return potion(material, effect, level, seconds, amount);
        }
        return null;
    }

    private static String mapValue(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static ItemStack potion(Material material, String effectName, int level, int seconds, int amount) {
        PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase(Locale.ROOT));
        if (type == null) {
            return null;
        }
        ItemStack item = new ItemStack(material == null ? Material.POTION : material, Math.min(64, amount));
        if (item.getItemMeta() instanceof PotionMeta meta) {
            meta.addCustomEffect(new PotionEffect(type, Math.max(1, seconds) * 20, Math.max(0, level - 1)), true);
            meta.displayName(Component.text(ChatColor.LIGHT_PURPLE + effectName.toUpperCase(Locale.ROOT) + " " + level));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return fallback;
        }
        Material material = raw == null ? null : Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
