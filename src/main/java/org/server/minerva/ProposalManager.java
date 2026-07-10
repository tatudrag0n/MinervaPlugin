package org.server.minerva;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class ProposalManager {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Minerva plugin;
    private File file;
    private YamlConfiguration data;

    ProposalManager(Minerva plugin) {
        this.plugin = plugin;
    }

    void load() {
        ensureConfigDefaults();
        file = new File(plugin.getDataFolder(), "proposals.yml");
        if (!file.exists()) {
            data = new YamlConfiguration();
            data.set("proposals.pending", new java.util.LinkedHashMap<>());
            data.set("proposals.approved", new java.util.LinkedHashMap<>());
            data.set("proposals.rejected", new java.util.LinkedHashMap<>());
            save();
        } else {
            data = YamlConfiguration.loadConfiguration(file);
        }
    }

    boolean handleCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minerva.admin")) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/mva proposal list|review|approve|reject|reload");
            return true;
        }
        ensureLoaded();
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "review" -> review(sender, args);
            case "approve" -> approve(sender, args);
            case "reject" -> reject(sender, args);
            case "reload" -> {
                load();
                sender.sendMessage(ChatColor.GREEN + "Proposal を再読み込みしました。");
                yield true;
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "/mva proposal list|review|approve|reject|reload");
                yield true;
            }
        };
    }

    List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return List.of("list", "review", "approve", "reject", "reload");
        }
        if (args.length == 3 && List.of("review", "approve", "reject").contains(args[1].toLowerCase(Locale.ROOT))) {
            ConfigurationSection pending = data == null ? null : data.getConfigurationSection("proposals.pending");
            return pending == null ? List.of() : new ArrayList<>(pending.getKeys(false));
        }
        return List.of();
    }

    private boolean list(CommandSender sender) {
        ConfigurationSection pending = data.getConfigurationSection("proposals.pending");
        if (pending == null || pending.getKeys(false).isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "pending proposal はありません。");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "Pending proposals:");
        for (String id : pending.getKeys(false).stream().sorted().toList()) {
            sender.sendMessage(ChatColor.YELLOW + id + ChatColor.GRAY + " - "
                    + data.getString("proposals.pending." + id + ".type", "unknown")
                    + " / " + data.getString("proposals.pending." + id + ".name", "(no name)")
                    + " / votes=" + data.getInt("proposals.pending." + id + ".votes", 0));
        }
        return true;
    }

    private boolean review(CommandSender sender, String[] args) {
        ConfigurationSection section = pending(sender, args);
        if (section == null) {
            return true;
        }
        String id = args[2];
        sender.sendMessage(ChatColor.GOLD + "Proposal: " + id);
        sender.sendMessage(ChatColor.GRAY + "type: " + section.getString("type", "unknown"));
        sender.sendMessage(ChatColor.GRAY + "name: " + section.getString("name", "(no name)"));
        sender.sendMessage(ChatColor.GRAY + "description: " + section.getString("description", ""));
        sender.sendMessage(ChatColor.GRAY + "source: " + section.getString("source", ""));
        sender.sendMessage(ChatColor.GRAY + "discord-user-id: " + section.getString("discord-user-id", ""));
        sender.sendMessage(ChatColor.GRAY + "votes: " + section.getInt("votes", 0));
        return true;
    }

    private boolean approve(CommandSender sender, String[] args) {
        ConfigurationSection section = pending(sender, args);
        if (section == null) {
            return true;
        }
        String id = args[2];
        if (!applyProposal(sender, id, section)) {
            return true;
        }
        move("proposals.pending." + id, "proposals.approved." + id);
        data.set("proposals.approved." + id + ".approved-at", System.currentTimeMillis());
        save();
        plugin.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Proposal を承認し、設定へ反映しました: " + id);
        return true;
    }

    private boolean reject(CommandSender sender, String[] args) {
        ConfigurationSection section = pending(sender, args);
        if (section == null) {
            return true;
        }
        String id = args[2];
        move("proposals.pending." + id, "proposals.rejected." + id);
        data.set("proposals.rejected." + id + ".rejected-at", System.currentTimeMillis());
        save();
        sender.sendMessage(ChatColor.YELLOW + "Proposal を却下しました: " + id);
        return true;
    }

    private ConfigurationSection pending(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "/mva proposal " + args[1] + " <id>");
            return null;
        }
        ConfigurationSection section = data.getConfigurationSection("proposals.pending." + args[2]);
        if (section == null) {
            sender.sendMessage(ChatColor.RED + "pending proposal が見つかりません: " + args[2]);
        }
        return section;
    }

    private boolean applyProposal(CommandSender sender, String proposalId, ConfigurationSection section) {
        String type = section.getString("type", "").toLowerCase(Locale.ROOT).replace("-", "_");
        return switch (type) {
            case "title" -> applyTitle(sender, proposalId, section);
            case "custom_item", "customitem", "item" -> applyCustomItem(sender, proposalId, section);
            case "shop_item", "shopitem" -> applyShopItem(sender, section);
            default -> {
                sender.sendMessage(ChatColor.RED + "未対応の proposal type です: " + type);
                yield false;
            }
        };
    }

    private boolean applyTitle(CommandSender sender, String proposalId, ConfigurationSection section) {
        String id = safeId(section.getString("id", proposalId));
        String displayName = section.getString("display-name", section.getString("name", id));
        if (displayName == null || displayName.isBlank()) {
            sender.sendMessage(ChatColor.RED + "title proposal に name がありません。");
            return false;
        }
        String path = "titles." + id;
        plugin.getConfig().set(path + ".display-name", displayName);
        plugin.getConfig().set(path + ".description", section.getString("description", ""));
        plugin.getConfig().set(path + ".icon", section.getString("icon", "name_tag"));
        if (section.contains("required-advancements")) {
            plugin.getConfig().set(path + ".required-advancements", section.getStringList("required-advancements"));
        }
        ConfigurationSection condition = section.getConfigurationSection("condition");
        if (condition != null) {
            for (Map.Entry<String, Object> entry : condition.getValues(true).entrySet()) {
                plugin.getConfig().set(path + ".condition." + entry.getKey(), entry.getValue());
            }
        }
        return true;
    }

    private boolean applyCustomItem(CommandSender sender, String proposalId, ConfigurationSection section) {
        String id = safeId(section.getString("id", proposalId));
        String materialName = section.getString("material", "paper");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            sender.sendMessage(ChatColor.RED + "custom item の material が不正です: " + materialName);
            return false;
        }
        String path = "custom-items." + id;
        plugin.getConfig().set(path + ".material", material.name().toLowerCase(Locale.ROOT));
        plugin.getConfig().set(path + ".display-name", section.getString("display-name", section.getString("name", id)));
        plugin.getConfig().set(path + ".lore", section.getStringList("lore"));
        plugin.getConfig().set(path + ".glint", section.getBoolean("glint", false));
        plugin.getConfig().set(path + ".unbreakable", section.getBoolean("unbreakable", false));
        plugin.getConfig().set(path + ".source", section.getString("source", "proposal"));
        return true;
    }

    private boolean applyShopItem(CommandSender sender, ConfigurationSection section) {
        String category = section.getString("category", "").toLowerCase(Locale.ROOT);
        ShopCategory shopCategory = ShopCategory.fromKey(category);
        String materialName = section.getString("material", "");
        Material material = Material.matchMaterial(materialName);
        if (shopCategory == null || material == null || !material.isItem()) {
            sender.sendMessage(ChatColor.RED + "shop_item proposal には category と material が必要です。");
            return false;
        }
        String path = "shopwand.categories." + shopCategory.key();
        List<String> values = new ArrayList<>(plugin.getConfig().getStringList(path));
        String normalized = material.name().toLowerCase(Locale.ROOT);
        if (!values.contains(normalized)) {
            values.add(normalized);
        }
        plugin.getConfig().set(path, values);
        return true;
    }

    private void move(String source, String target) {
        ConfigurationSection section = data.getConfigurationSection(source);
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(true).entrySet()) {
                data.set(target + "." + entry.getKey(), entry.getValue());
            }
        }
        data.set(source, null);
    }

    private String safeId(String raw) {
        String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (normalized.isBlank()) {
            normalized = "proposal";
        }
        return SAFE_ID.matcher(normalized).matches() ? normalized : normalized.substring(0, Math.min(64, normalized.length()));
    }

    private void ensureLoaded() {
        if (data == null) {
            load();
        }
    }

    private void ensureConfigDefaults() {
        if (!plugin.getConfig().contains("proposals.enabled")) {
            plugin.getConfig().set("proposals.enabled", true);
        }
        if (!plugin.getConfig().contains("proposals.require-admin-approval")) {
            plugin.getConfig().set("proposals.require-admin-approval", true);
        }
        plugin.saveConfig();
    }

    private void save() {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save proposals.yml: " + e.getMessage());
        }
    }
}
