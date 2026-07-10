package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

final class QuestService {
    private static final int DAILY_WEEKLY_SELECTION_SIZE = 5;

    private final Minerva plugin;
    private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();

    QuestService(Minerva plugin) {
        this.plugin = plugin;
    }

    void load() {
        definitions.clear();
        File file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("quests");
        if (section == null) {
            plugin.getLogger().warning("quests.yml does not contain quests.");
            return;
        }
        for (String id : section.getKeys(false)) {
            String path = id + ".";
            QuestType type = QuestType.fromLabel(section.getString(path + "type", ""));
            Material icon = Material.matchMaterial(section.getString(path + "icon", "PAPER"));
            definitions.put(id, new QuestDefinition(
                    id,
                    type,
                    section.getString(path + "name", id),
                    section.getString(path + "reset", ""),
                    section.getString(path + "condition", ""),
                    Math.max(0, section.getInt(path + "base-reward-em", 0)),
                    section.getString(path + "display", ""),
                    section.getBoolean(path + "reincarnation-bonus", true),
                    section.getString(path + "repeat-limit", ""),
                    icon == null ? Material.PAPER : icon,
                    section.getString(path + "progress-key", "manual"),
                    Math.max(1, section.getInt(path + "required", 1)),
                    section.getString(path + "intent", "")));
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " quest definitions.");
    }

    QuestDefinition definition(String id) {
        return definitions.get(id);
    }

    List<QuestDefinition> visibleQuests(Player player, QuestType type) {
        ensurePeriods(player);
        syncDerivedSpecialProgress(player);
        List<QuestDefinition> result = new ArrayList<>();
        if (type == QuestType.SPECIAL) {
            result.addAll(definitions.values().stream()
                    .filter(definition -> definition.type() == QuestType.SPECIAL)
                    .sorted(Comparator.comparing(QuestDefinition::id))
                    .toList());
            return result;
        }
        if (type == QuestType.MONTHLY) {
            result.addAll(definitions.values().stream()
                    .filter(definition -> definition.type() == QuestType.MONTHLY)
                    .sorted(Comparator.comparing(QuestDefinition::id))
                    .toList());
            return result;
        }
        Set<String> selected = new HashSet<>(playerSection(player.getUniqueId()).getStringList(questBase(type) + ".available"));
        definitions.values().stream()
                .filter(definition -> definition.type() == type)
                .filter(definition -> selected.contains(definition.id()) || definition.isCompletionQuest())
                .sorted(Comparator.comparing(QuestDefinition::id))
                .forEach(result::add);
        return result;
    }

    boolean isVisible(Player player, QuestDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.type() == QuestType.SPECIAL || definition.type() == QuestType.MONTHLY) {
            return true;
        }
        ensurePeriods(player);
        return definition.isCompletionQuest()
                || playerSection(player.getUniqueId()).getStringList(questBase(definition.type()) + ".available").contains(definition.id());
    }

    boolean isUnlocked(Player player, QuestDefinition definition) {
        if (definition == null || definition.type() != QuestType.SPECIAL) {
            return true;
        }
        syncDerivedSpecialProgress(player);
        return progress(player, definition) > 0 || isClaimed(player, definition);
    }

    int progress(Player player, QuestDefinition definition) {
        ensurePeriods(player);
        if (definition.isCompletionQuest()) {
            return completionProgress(player, definition.type());
        }
        if (definition.type() == QuestType.SPECIAL) {
            return playerSection(player.getUniqueId()).getInt(questBase(QuestType.SPECIAL) + ".progress." + definition.progressKey(), 0);
        }
        return playerSection(player.getUniqueId()).getInt(questBase(definition.type()) + ".progress." + definition.progressKey(), 0);
    }

    boolean isCompleted(Player player, QuestDefinition definition) {
        return progress(player, definition) >= definition.required();
    }

    boolean isClaimed(Player player, QuestDefinition definition) {
        return playerSection(player.getUniqueId()).getStringList(questBase(definition.type()) + ".claimed").contains(definition.id());
    }

    int effectiveReward(Player player, QuestDefinition definition) {
        return effectiveReward(player.getUniqueId(), definition.baseReward(), definition.reincarnationBonus());
    }

    int effectiveReward(UUID uuid, int baseReward, boolean allowReincarnation) {
        if (baseReward <= 0) {
            return 0;
        }
        if (!allowReincarnation || !plugin.getConfig().getBoolean("quests.reward.apply-reincarnation-bonus", true)) {
            return baseReward;
        }
        double compression = plugin.getConfig().getDouble("quests.reward.reincarnation-bonus-compression-percent", 25.0D);
        double cap = plugin.getConfig().getDouble("quests.reward.reincarnation-bonus-cap-percent", 120.0D);
        double reincarnation = Math.min(Math.max(0, plugin.getReincarnationBonus(uuid)), Math.max(0.0D, cap));
        double bonusPercent = reincarnation * Math.max(0.0D, compression) / 100.0D;
        double value = baseReward * (1.0D + bonusPercent / 100.0D);
        String rounding = plugin.getConfig().getString("quests.reward.rounding", "floor").toLowerCase(Locale.ROOT);
        return switch (rounding) {
            case "ceil", "ceiling" -> (int) Math.ceil(value);
            case "round", "nearest" -> (int) Math.round(value);
            default -> (int) Math.floor(value);
        };
    }

    boolean claim(Player player, String questId) {
        QuestDefinition definition = definitions.get(questId);
        if (definition == null) {
            player.sendMessage(ChatColor.RED + "クエストが見つかりません: " + questId);
            return false;
        }
        ensurePeriods(player);
        syncDerivedSpecialProgress(player);
        if (!isVisible(player, definition) || !isUnlocked(player, definition)) {
            player.sendMessage(ChatColor.RED + "このクエストはまだ解放されていません。");
            return false;
        }
        if (isClaimed(player, definition)) {
            player.sendMessage(ChatColor.YELLOW + "このクエスト報酬は受取済みです。");
            return false;
        }
        if (!isCompleted(player, definition)) {
            player.sendMessage(ChatColor.RED + "クエスト条件を満たしていません。");
            return false;
        }
        ConfigurationSection section = playerSection(player.getUniqueId());
        String claimPath = questBase(definition.type()) + ".claimed";
        List<String> claimed = new ArrayList<>(section.getStringList(claimPath));
        claimed.add(definition.id());
        section.set(claimPath, claimed);
        plugin.saveData();

        int reward = effectiveReward(player, definition);
        plugin.depositEmeralds(player.getUniqueId(), reward);
        player.sendMessage(ChatColor.GREEN + "クエスト報酬: " + definition.name() + " +" + formatNumber(reward) + "EM");
        return true;
    }

    void addProgress(Player player, String progressKey, int amount) {
        if (player == null || amount <= 0 || progressKey == null || progressKey.isBlank()) {
            return;
        }
        ensurePeriods(player);
        syncDerivedSpecialProgress(player);
        boolean changed = false;
        for (QuestType type : List.of(QuestType.DAILY, QuestType.WEEKLY, QuestType.MONTHLY)) {
            if (hasVisibleQuestWithProgressKey(player, type, progressKey)) {
                changed |= addProgressAt(player.getUniqueId(), questBase(type) + ".progress." + progressKey, amount);
            }
        }
        if (hasQuestWithProgressKey(QuestType.SPECIAL, progressKey)) {
            changed |= addProgressAt(player.getUniqueId(), questBase(QuestType.SPECIAL) + ".progress." + progressKey, amount);
        }
        if (changed) {
            plugin.saveData();
        }
    }

    void setQuestProgress(Player player, String questId, int amount) {
        QuestDefinition definition = definitions.get(questId);
        if (definition == null) {
            player.sendMessage(ChatColor.RED + "クエストが見つかりません: " + questId);
            return;
        }
        ensurePeriods(player);
        String path = definition.type() == QuestType.SPECIAL
                ? questBase(QuestType.SPECIAL) + ".progress." + definition.progressKey()
                : questBase(definition.type()) + ".progress." + definition.progressKey();
        playerSection(player.getUniqueId()).set(path, Math.max(0, amount));
        plugin.saveData();
        player.sendMessage(ChatColor.GREEN + definition.id() + " の進捗を " + Math.max(0, amount) + " にしました。");
    }

    void recordStat(UUID uuid, String statKey, int amount) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || amount <= 0) {
            return;
        }
        switch (statKey) {
            case "total-trades" -> {
                addProgress(player, "trades", amount);
                addProgress(player, "trades_total", amount);
            }
            case "athletic-clears" -> {
                addProgress(player, "athletic_clears", amount);
                addProgress(player, "challenge_activity", amount);
            }
            case "minigame-plays", "minigame-wins" -> {
                addProgress(player, "minigame_activity", amount);
                addProgress(player, "challenge_activity", amount);
            }
            default -> {
            }
        }
    }

    String remainingTime(QuestType type) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime end = switch (type) {
            case DAILY -> now.toLocalDate().plusDays(1).atStartOfDay(zone);
            case WEEKLY -> now.plusDays(8 - now.getDayOfWeek().getValue()).toLocalDate().atStartOfDay(zone);
            case MONTHLY -> YearMonth.now(zone).plusMonths(1).atDay(1).atStartOfDay(zone);
            case SPECIAL -> null;
        };
        if (end == null) {
            return "期限なし";
        }
        Duration duration = Duration.between(now, end);
        long days = duration.toDays();
        long hours = duration.minusDays(days).toHours();
        long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        if (days > 0) {
            return days + "日" + hours + "時間";
        }
        if (hours > 0) {
            return hours + "時間" + minutes + "分";
        }
        return Math.max(0, minutes) + "分";
    }

    void ensurePeriods(Player player) {
        ConfigurationSection section = playerSection(player.getUniqueId());
        ensurePeriod(section, player.getUniqueId(), QuestType.DAILY, dailyKey());
        ensurePeriod(section, player.getUniqueId(), QuestType.WEEKLY, weeklyKey());
        ensurePeriod(section, player.getUniqueId(), QuestType.MONTHLY, monthlyKey());
    }

    private void ensurePeriod(ConfigurationSection section, UUID uuid, QuestType type, String currentKey) {
        String base = questBase(type);
        if (currentKey.equals(section.getString(base + ".period"))) {
            return;
        }
        section.set(base + ".period", currentKey);
        section.set(base + ".progress", null);
        section.set(base + ".claimed", null);
        if (type == QuestType.DAILY || type == QuestType.WEEKLY) {
            section.set(base + ".available", selectPeriodicQuestIds(uuid, type, currentKey));
        } else if (type == QuestType.MONTHLY) {
            section.set(base + ".available", definitions.values().stream()
                    .filter(definition -> definition.type() == QuestType.MONTHLY)
                    .map(QuestDefinition::id)
                    .sorted()
                    .toList());
        }
        plugin.saveData();
    }

    private List<String> selectPeriodicQuestIds(UUID uuid, QuestType type, String period) {
        List<String> ids = definitions.values().stream()
                .filter(definition -> definition.type() == type)
                .filter(definition -> !definition.isCompletionQuest())
                .map(QuestDefinition::id)
                .sorted()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.shuffle(ids, new Random(Objects.hash(uuid, type.key(), period)));
        return ids.stream().limit(DAILY_WEEKLY_SELECTION_SIZE).sorted().toList();
    }

    private boolean hasVisibleQuestWithProgressKey(Player player, QuestType type, String progressKey) {
        return visibleQuests(player, type).stream()
                .anyMatch(definition -> !definition.isCompletionQuest() && definition.progressKey().equals(progressKey));
    }

    private boolean hasQuestWithProgressKey(QuestType type, String progressKey) {
        return definitions.values().stream()
                .anyMatch(definition -> definition.type() == type && definition.progressKey().equals(progressKey));
    }

    private boolean addProgressAt(UUID uuid, String path, int amount) {
        ConfigurationSection section = playerSection(uuid);
        int current = section.getInt(path, 0);
        int next = safeAdd(current, amount);
        if (next == current) {
            return false;
        }
        section.set(path, next);
        return true;
    }

    private int completionProgress(Player player, QuestType type) {
        List<QuestDefinition> targets = visibleQuests(player, type).stream()
                .filter(definition -> !definition.isCompletionQuest())
                .toList();
        int completed = 0;
        for (QuestDefinition target : targets) {
            if (isCompleted(player, target)) {
                completed++;
            }
        }
        return completed;
    }

    private void syncDerivedSpecialProgress(Player player) {
        ConfigurationSection section = playerSection(player.getUniqueId());
        List<String> completedAdvancements = section.getStringList("completed-advancements");
        setSpecialProgressAtLeast(section, "advancements_total", completedAdvancements.size());
        setSpecialProgressAtLeast(section, "reincarnations", section.getInt("reincarnations", 0));
        setSpecialProgressAtLeast(section, "trades_total", section.getInt("total-trades", 0));
        setSpecialProgressAtLeast(section, "mob_catalog", section.getStringList("killed-mobs").size());
        if (completedAdvancements.contains("minecraft:end/elytra") || completedAdvancements.contains("minecraft:end/find_end_city")) {
            setSpecialProgressAtLeast(section, "elytra_obtained", 1);
        }
        if (section.getInt("reincarnations", 0) >= 2
                && completedAdvancements.size() >= 75
                && section.getInt("total-mob-kills", 0) >= 2000
                && section.getInt("total-blocks-placed", 0) >= 20000
                && section.getInt("total-trades", 0) >= 500) {
            setSpecialProgressAtLeast(section, "legend_score", 1);
        }
    }

    private void setSpecialProgressAtLeast(ConfigurationSection section, String key, int value) {
        String path = questBase(QuestType.SPECIAL) + ".progress." + key;
        if (value > section.getInt(path, 0)) {
            section.set(path, value);
        }
    }

    private String dailyKey() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private String weeklyKey() {
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        WeekFields weekFields = WeekFields.ISO;
        return now.get(weekFields.weekBasedYear()) + "-W" + String.format(Locale.ROOT, "%02d", now.get(weekFields.weekOfWeekBasedYear()));
    }

    private String monthlyKey() {
        return YearMonth.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String questBase(QuestType type) {
        return "quests." + type.key();
    }

    private ConfigurationSection playerSection(UUID uuid) {
        String path = "players." + uuid;
        ConfigurationSection section = plugin.data().getConfigurationSection(path);
        return section != null ? section : plugin.data().createSection(path);
    }

    private int safeAdd(int current, int amount) {
        long result = (long) Math.max(0, current) + Math.max(0, amount);
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private String formatNumber(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
