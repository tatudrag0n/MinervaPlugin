package org.server.minerva;

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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

final class QuestService {
   private static final int DAILY_WEEKLY_SELECTION_SIZE = 5;
   private final Minerva plugin;
   private final Map<String, QuestDefinition> definitions = new LinkedHashMap<>();

   QuestService(Minerva plugin) {
      this.plugin = plugin;
   }

   void load() {
      this.definitions.clear();
      File file = new File(this.plugin.getDataFolder(), "quests.yml");
      if (!file.exists()) {
         this.plugin.saveResource("quests.yml", false);
      }

      YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
      ConfigurationSection section = config.getConfigurationSection("quests");
      if (section == null) {
         this.plugin.getLogger().warning("quests.yml does not contain quests.");
      } else {
         for (String id : section.getKeys(false)) {
            String path = id + ".";
            QuestType type = QuestType.fromLabel(section.getString(path + "type", ""));
            Material icon = Material.matchMaterial(section.getString(path + "icon", "PAPER"));
            this.definitions
               .put(
                  id,
                  new QuestDefinition(
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
                     section.getString(path + "intent", "")
                  )
               );
         }

         this.plugin.getLogger().info("Loaded " + this.definitions.size() + " quest definitions.");
      }
   }

   QuestDefinition definition(String id) {
      return this.definitions.get(id);
   }

   List<QuestDefinition> visibleQuests(Player player, QuestType type) {
      this.ensurePeriods(player);
      this.syncDerivedSpecialProgress(player);
      List<QuestDefinition> result = new ArrayList<>();
      if (type == QuestType.SPECIAL) {
         result.addAll(
            this.definitions
               .values()
               .stream()
               .filter(definition -> definition.type() == QuestType.SPECIAL)
               .sorted(Comparator.comparing(QuestDefinition::id))
               .toList()
         );
         return result;
      } else if (type == QuestType.MONTHLY) {
         result.addAll(
            this.definitions
               .values()
               .stream()
               .filter(definition -> definition.type() == QuestType.MONTHLY)
               .sorted(Comparator.comparing(QuestDefinition::id))
               .toList()
         );
         return result;
      } else {
         Set<String> selected = new HashSet<>(this.playerSection(player.getUniqueId()).getStringList(this.questBase(type) + ".available"));
         this.definitions
            .values()
            .stream()
            .filter(definition -> definition.type() == type)
            .filter(definition -> selected.contains(definition.id()) || definition.isCompletionQuest())
            .sorted(Comparator.comparing(QuestDefinition::id))
            .forEach(result::add);
         return result;
      }
   }

   boolean isVisible(Player player, QuestDefinition definition) {
      if (definition == null) {
         return false;
      } else if (definition.type() != QuestType.SPECIAL && definition.type() != QuestType.MONTHLY) {
         this.ensurePeriods(player);
         return definition.isCompletionQuest()
            || this.playerSection(player.getUniqueId()).getStringList(this.questBase(definition.type()) + ".available").contains(definition.id());
      } else {
         return true;
      }
   }

   boolean isUnlocked(Player player, QuestDefinition definition) {
      if (definition != null && definition.type() == QuestType.SPECIAL) {
         this.syncDerivedSpecialProgress(player);
         return this.progress(player, definition) > 0 || this.isClaimed(player, definition);
      } else {
         return true;
      }
   }

   int progress(Player player, QuestDefinition definition) {
      this.ensurePeriods(player);
      if (definition.isCompletionQuest()) {
         return this.completionProgress(player, definition.type());
      } else {
         return definition.type() == QuestType.SPECIAL
            ? this.playerSection(player.getUniqueId()).getInt(this.questBase(QuestType.SPECIAL) + ".progress." + definition.progressKey(), 0)
            : this.playerSection(player.getUniqueId()).getInt(this.questBase(definition.type()) + ".progress." + definition.progressKey(), 0);
      }
   }

   boolean isCompleted(Player player, QuestDefinition definition) {
      return this.progress(player, definition) >= definition.required();
   }

   boolean isClaimed(Player player, QuestDefinition definition) {
      return this.playerSection(player.getUniqueId()).getStringList(this.questBase(definition.type()) + ".claimed").contains(definition.id());
   }

   int effectiveReward(Player player, QuestDefinition definition) {
      return this.effectiveReward(player.getUniqueId(), definition.baseReward(), definition.reincarnationBonus());
   }

   int effectiveReward(UUID uuid, int baseReward, boolean allowReincarnation) {
      if (baseReward <= 0) {
         return 0;
      }

      if (allowReincarnation && this.plugin.getConfig().getBoolean("quests.reward.apply-reincarnation-bonus", true)) {
         double compression = this.plugin.getConfig().getDouble("quests.reward.reincarnation-bonus-compression-percent", 25.0);
         double cap = this.plugin.getConfig().getDouble("quests.reward.reincarnation-bonus-cap-percent", 120.0);
         double reincarnation = Math.min(Math.max(0, this.plugin.getReincarnationBonus(uuid)), Math.max(0.0, cap));
         double bonusPercent = reincarnation * Math.max(0.0, compression) / 100.0;
         double value = baseReward * (1.0 + bonusPercent / 100.0);
         String rounding = this.plugin.getConfig().getString("quests.reward.rounding", "floor").toLowerCase(Locale.ROOT);

         return switch (rounding) {
            case "ceil", "ceiling" -> (int)Math.ceil(value);
            case "round", "nearest" -> (int)Math.round(value);
            default -> (int)Math.floor(value);
         };
      } else {
         return baseReward;
      }
   }

   boolean claim(Player player, String questId) {
      QuestDefinition definition = this.definitions.get(questId);
      if (definition == null) {
         player.sendMessage(ChatColor.RED + "クエストが見つかりません: " + questId);
         return false;
      } else {
         this.ensurePeriods(player);
         this.syncDerivedSpecialProgress(player);
         if (!this.isVisible(player, definition) || !this.isUnlocked(player, definition)) {
            player.sendMessage(ChatColor.RED + "このクエストはまだ解放されていません。");
            return false;
         } else if (this.isClaimed(player, definition)) {
            player.sendMessage(ChatColor.YELLOW + "このクエスト報酬は受取済みです。");
            return false;
         } else if (!this.isCompleted(player, definition)) {
            player.sendMessage(ChatColor.RED + "クエスト条件を満たしていません。");
            return false;
         } else {
            ConfigurationSection section = this.playerSection(player.getUniqueId());
            String claimPath = this.questBase(definition.type()) + ".claimed";
            List<String> claimed = new ArrayList<>(section.getStringList(claimPath));
            claimed.add(definition.id());
            section.set(claimPath, claimed);
            this.plugin.saveData();
            int reward = this.effectiveReward(player, definition);
            this.plugin.depositEmeralds(player.getUniqueId(), reward);
            player.sendMessage(ChatColor.GREEN + "クエスト報酬: " + definition.name() + " +" + this.formatNumber(reward) + "EM");
            return true;
         }
      }
   }

   void addProgress(Player player, String progressKey, int amount) {
      if (player != null && amount > 0 && progressKey != null && !progressKey.isBlank()) {
         this.ensurePeriods(player);
         this.syncDerivedSpecialProgress(player);
         boolean changed = false;

         for (QuestType type : List.of(QuestType.DAILY, QuestType.WEEKLY, QuestType.MONTHLY)) {
            if (this.hasVisibleQuestWithProgressKey(player, type, progressKey)) {
               changed |= this.addProgressAt(player.getUniqueId(), this.questBase(type) + ".progress." + progressKey, amount);
            }
         }

         if (this.hasQuestWithProgressKey(QuestType.SPECIAL, progressKey)) {
            changed |= this.addProgressAt(player.getUniqueId(), this.questBase(QuestType.SPECIAL) + ".progress." + progressKey, amount);
         }

         if (changed) {
            this.plugin.saveData();
         }
      }
   }

   void setQuestProgress(Player player, String questId, int amount) {
      QuestDefinition definition = this.definitions.get(questId);
      if (definition == null) {
         player.sendMessage(ChatColor.RED + "クエストが見つかりません: " + questId);
      } else {
         this.ensurePeriods(player);
         String path = definition.type() == QuestType.SPECIAL
            ? this.questBase(QuestType.SPECIAL) + ".progress." + definition.progressKey()
            : this.questBase(definition.type()) + ".progress." + definition.progressKey();
         this.playerSection(player.getUniqueId()).set(path, Math.max(0, amount));
         this.plugin.saveData();
         player.sendMessage(ChatColor.GREEN + definition.id() + " の進捗を " + Math.max(0, amount) + " にしました。");
      }
   }

   void recordStat(UUID uuid, String statKey, int amount) {
      Player player = Bukkit.getPlayer(uuid);
      if (player != null && amount > 0) {
         switch (statKey) {
            case "total-trades":
               this.addProgress(player, "trades", amount);
               this.addProgress(player, "trades_total", amount);
               break;
            case "athletic-clears":
               this.addProgress(player, "athletic_clears", amount);
               this.addProgress(player, "challenge_activity", amount);
               break;
            case "minigame-plays":
            case "minigame-wins":
               this.addProgress(player, "minigame_activity", amount);
               this.addProgress(player, "challenge_activity", amount);
         }
      }
   }

   String remainingTime(QuestType type) {
      ZoneId zone = ZoneId.systemDefault();
      ZonedDateTime now = ZonedDateTime.now(zone);

      ZonedDateTime end = switch (type) {
         case DAILY -> now.toLocalDate().plusDays(1L).atStartOfDay(zone);
         case WEEKLY -> now.plusDays(8 - now.getDayOfWeek().getValue()).toLocalDate().atStartOfDay(zone);
         case MONTHLY -> YearMonth.now(zone).plusMonths(1L).atDay(1).atStartOfDay(zone);
         case SPECIAL -> null;
      };
      if (end == null) {
         return "期限なし";
      } else {
         Duration duration = Duration.between(now, end);
         long days = duration.toDays();
         long hours = duration.minusDays(days).toHours();
         long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
         if (days > 0L) {
            return days + "日" + hours + "時間";
         } else {
            return hours > 0L ? hours + "時間" + minutes + "分" : Math.max(0L, minutes) + "分";
         }
      }
   }

   void ensurePeriods(Player player) {
      ConfigurationSection section = this.playerSection(player.getUniqueId());
      this.ensurePeriod(section, player.getUniqueId(), QuestType.DAILY, this.dailyKey());
      this.ensurePeriod(section, player.getUniqueId(), QuestType.WEEKLY, this.weeklyKey());
      this.ensurePeriod(section, player.getUniqueId(), QuestType.MONTHLY, this.monthlyKey());
   }

   private void ensurePeriod(ConfigurationSection section, UUID uuid, QuestType type, String currentKey) {
      String base = this.questBase(type);
      if (!currentKey.equals(section.getString(base + ".period"))) {
         section.set(base + ".period", currentKey);
         section.set(base + ".progress", null);
         section.set(base + ".claimed", null);
         if (type == QuestType.DAILY || type == QuestType.WEEKLY) {
            section.set(base + ".available", this.selectPeriodicQuestIds(uuid, type, currentKey));
         } else if (type == QuestType.MONTHLY) {
            section.set(
               base + ".available",
               this.definitions.values().stream().filter(definition -> definition.type() == QuestType.MONTHLY).map(QuestDefinition::id).sorted().toList()
            );
         }

         this.plugin.saveData();
      }
   }

   private List<String> selectPeriodicQuestIds(UUID uuid, QuestType type, String period) {
      List<String> ids = this.definitions
         .values()
         .stream()
         .filter(definition -> definition.type() == type)
         .filter(definition -> !definition.isCompletionQuest())
         .map(QuestDefinition::id)
         .sorted()
         .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
      Collections.shuffle(ids, new Random(Objects.hash(uuid, type.key(), period)));
      return ids.stream().limit(5L).sorted().toList();
   }

   private boolean hasVisibleQuestWithProgressKey(Player player, QuestType type, String progressKey) {
      return this.visibleQuests(player, type).stream().anyMatch(definition -> !definition.isCompletionQuest() && definition.progressKey().equals(progressKey));
   }

   private boolean hasQuestWithProgressKey(QuestType type, String progressKey) {
      return this.definitions.values().stream().anyMatch(definition -> definition.type() == type && definition.progressKey().equals(progressKey));
   }

   private boolean addProgressAt(UUID uuid, String path, int amount) {
      ConfigurationSection section = this.playerSection(uuid);
      int current = section.getInt(path, 0);
      int next = this.safeAdd(current, amount);
      if (next == current) {
         return false;
      }

      section.set(path, next);
      return true;
   }

   private int completionProgress(Player player, QuestType type) {
      List<QuestDefinition> targets = this.visibleQuests(player, type).stream().filter(definition -> !definition.isCompletionQuest()).toList();
      int completed = 0;

      for (QuestDefinition target : targets) {
         if (this.isCompleted(player, target)) {
            completed++;
         }
      }

      return completed;
   }

   private void syncDerivedSpecialProgress(Player player) {
      ConfigurationSection section = this.playerSection(player.getUniqueId());
      List<String> completedAdvancements = section.getStringList("completed-advancements");
      this.setSpecialProgressAtLeast(section, "advancements_total", completedAdvancements.size());
      this.setSpecialProgressAtLeast(section, "reincarnations", section.getInt("reincarnations", 0));
      this.setSpecialProgressAtLeast(section, "trades_total", section.getInt("total-trades", 0));
      this.setSpecialProgressAtLeast(section, "mob_catalog", section.getStringList("killed-mobs").size());
      if (completedAdvancements.contains("minecraft:end/elytra") || completedAdvancements.contains("minecraft:end/find_end_city")) {
         this.setSpecialProgressAtLeast(section, "elytra_obtained", 1);
      }

      if (section.getInt("reincarnations", 0) >= 2
         && completedAdvancements.size() >= 75
         && section.getInt("total-mob-kills", 0) >= 2000
         && section.getInt("total-blocks-placed", 0) >= 20000
         && section.getInt("total-trades", 0) >= 500) {
         this.setSpecialProgressAtLeast(section, "legend_score", 1);
      }
   }

   private void setSpecialProgressAtLeast(ConfigurationSection section, String key, int value) {
      String path = this.questBase(QuestType.SPECIAL) + ".progress." + key;
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
      ConfigurationSection section = this.plugin.data().getConfigurationSection(path);
      return section != null ? section : this.plugin.data().createSection(path);
   }

   private int safeAdd(int current, int amount) {
      long result = (long)Math.max(0, current) + Math.max(0, amount);
      return (int)Math.min(2147483647L, result);
   }

   private String formatNumber(int value) {
      return String.format(Locale.ROOT, "%,d", value);
   }
}
