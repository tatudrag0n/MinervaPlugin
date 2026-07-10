package org.server.minerva;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class DiscordAuthManager {
    private static final String DEFAULT_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Minerva plugin;
    private final SecureRandom random = new SecureRandom();
    private File authFile;
    private YamlConfiguration auth;

    DiscordAuthManager(Minerva plugin) {
        this.plugin = plugin;
    }

    synchronized void load() {
        ensureConfigDefaults();
        authFile = new File(plugin.getDataFolder(), "auth.yml");
        if (!authFile.exists()) {
            auth = new YamlConfiguration();
            auth.set("discord-auth.verified", new java.util.LinkedHashMap<>());
            auth.set("discord-auth.pending", new java.util.LinkedHashMap<>());
            saveAuth();
            return;
        }
        auth = YamlConfiguration.loadConfiguration(authFile);
        purgeExpiredPending();
        saveAuth();
    }

    synchronized void reload() {
        load();
    }

    void tickExternalUpdates() {
        if (!enabled() || authFile == null || !authFile.exists()) {
            return;
        }
        synchronized (this) {
            auth = YamlConfiguration.loadConfiguration(authFile);
            purgeExpiredPending();
        }
    }

    void handlePreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled()) {
            return;
        }
        try {
            PreLoginDecision decision = checkPreLogin(event.getUniqueId(), event.getName(), event.getAddress());
            if (decision.allowed()) {
                return;
            }
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, decision.message());
        } catch (Throwable e) {
            plugin.getLogger().severe("Discord auth pre-login check failed for " + event.getName() + " (" + event.getUniqueId() + "): " + e.getMessage());
            e.printStackTrace();
            if (allowJoinWhenAuthSystemError()) {
                plugin.getLogger().warning("Allowing login because discord-auth.allow-join-when-auth-system-error is true.");
                return;
            }
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§c認証システムエラー\n\n§f時間をおいてもう一度接続してください。\n§7解決しない場合は管理者へ連絡してください。");
        }
    }

    boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length >= 2 && "reload".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("minerva.admin")) {
                sender.sendMessage(ChatColor.RED + "権限がありません。");
                return true;
            }
            plugin.reloadConfig();
            reload();
            sender.sendMessage(ChatColor.GREEN + "Discord認証設定を再読み込みしました。");
            return true;
        }
        if (args.length >= 2 && "lookup".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("minerva.admin")) {
                sender.sendMessage(ChatColor.RED + "権限がありません。");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "/mva auth lookup <player|uuid>");
                return true;
            }
            sendLookup(sender, args[2]);
            return true;
        }
        if (args.length >= 2 && "unlink".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("minerva.admin")) {
                sender.sendMessage(ChatColor.RED + "権限がありません。");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "/mva auth unlink <player|uuid>");
                return true;
            }
            unlink(sender, args[2]);
            return true;
        }
        if (args.length >= 2 && "verified".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("minerva.admin")) {
                sender.sendMessage(ChatColor.RED + "権限がありません。");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "/mva auth verified <player|uuid>");
                return true;
            }
            sendVerified(sender, args[2]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        if (!enabled()) {
            player.sendMessage(ChatColor.YELLOW + "Discord認証は現在無効です。");
            return true;
        }
        if (args.length >= 2 && "status".equalsIgnoreCase(args[1])) {
            player.sendMessage(isVerified(player.getUniqueId())
                    ? ChatColor.GREEN + "Discord認証済みです。"
                    : ChatColor.YELLOW + "Discord未認証です。再接続するとキック画面に認証コードが表示されます。");
            return true;
        }
        if (isVerified(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "Discord認証済みです。");
            return true;
        }
        boolean forceNew = args.length >= 2 && "code".equalsIgnoreCase(args[1]);
        kickWithCode(player, forceNew);
        return true;
    }

    List<String> tabComplete(String[] args, CommandSender sender) {
        if (args.length == 2) {
            List<String> values = new ArrayList<>(List.of("status", "code"));
            if (sender.hasPermission("minerva.admin")) {
                values.add("reload");
                values.add("lookup");
                values.add("unlink");
                values.add("verified");
            }
            return values;
        }
        return List.of();
    }

    void handleJoin(Player player) {
        // Discord auth codes are intentionally shown only on the kick screen.
    }

    boolean shouldRestrict(Player player, String key) {
        return enabled()
                && restrictUnverified()
                && player != null
                && !isVerified(player.getUniqueId())
                && plugin.getConfig().getBoolean("discord-auth.restrictions." + key, false);
    }

    boolean commandAllowed(String commandLine) {
        String normalized = commandLine == null ? "" : commandLine.toLowerCase(Locale.ROOT).trim();
        for (String allowed : plugin.getConfig().getStringList("discord-auth.allowed-commands")) {
            String value = allowed.toLowerCase(Locale.ROOT).trim();
            if (!value.isBlank() && (normalized.equals(value) || normalized.startsWith(value + " "))) {
                return true;
            }
        }
        return normalized.equals("/minerva auth")
                || normalized.startsWith("/minerva auth ")
                || normalized.equals("/mva auth")
                || normalized.startsWith("/mva auth ");
    }

    synchronized boolean isVerified(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        ensureLoaded();
        String path = "discord-auth.verified." + uuid;
        return auth.contains(path) && !auth.getString(path + ".discord-id", "").isBlank();
    }

    private void kickWithCode(Player player, boolean forceNew) {
        String code = issueCode(player.getUniqueId(), player.getName(), null, forceNew);
        player.kickPlayer(buildKickMessage(code));
    }

    private synchronized PreLoginDecision checkPreLogin(UUID uuid, String name, InetAddress address) throws IOException {
        ensureLoaded();
        loadAuthFromDisk();
        purgeExpiredPending();
        if (isVerifiedLoaded(uuid)) {
            return PreLoginDecision.allow();
        }
        String code = issueCodeLoaded(uuid, name, address, false);
        saveAuthChecked();
        return PreLoginDecision.deny(buildKickMessage(code));
    }

    private synchronized String issueCode(UUID uuid, String name, InetAddress address, boolean forceNew) {
        ensureLoaded();
        loadAuthFromDisk();
        purgeExpiredPending();
        String code = issueCodeLoaded(uuid, name, address, forceNew);
        saveAuth();
        return code;
    }

    private String issueCodeLoaded(UUID uuid, String name, InetAddress address, boolean forceNew) {
        if (!forceNew) {
            String existing = pendingCodeFor(uuid);
            if (existing != null) {
                return existing;
            }
        }
        removePendingFor(uuid);
        String code;
        do {
            code = randomCode();
        } while (auth.contains("discord-auth.pending." + code));
        String path = "discord-auth.pending." + code;
        long now = nowSeconds();
        auth.set(path + ".uuid", uuid.toString());
        auth.set(path + ".name", name);
        auth.set(path + ".expires-at", now + expireSeconds());
        auth.set(path + ".created-at", now);
        String addressHash = addressHash(address);
        if (addressHash != null) {
            auth.set(path + ".address-hash", addressHash);
        }
        return code;
    }

    private boolean isVerifiedLoaded(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        String path = "discord-auth.verified." + uuid;
        return auth.contains(path) && !auth.getString(path + ".discord-id", "").isBlank();
    }

    private String pendingCodeFor(UUID uuid) {
        ConfigurationSection pending = auth.getConfigurationSection("discord-auth.pending");
        if (pending == null) {
            return null;
        }
        long now = nowSeconds();
        for (String code : pending.getKeys(false)) {
            String path = "discord-auth.pending." + code;
            if (uuid.toString().equals(auth.getString(path + ".uuid"))
                    && auth.getLong(path + ".expires-at", 0L) > now) {
                return code;
            }
        }
        return null;
    }

    private void removePendingFor(UUID uuid) {
        ConfigurationSection pending = auth.getConfigurationSection("discord-auth.pending");
        if (pending == null) {
            return;
        }
        for (String code : new ArrayList<>(pending.getKeys(false))) {
            if (uuid.toString().equals(auth.getString("discord-auth.pending." + code + ".uuid"))) {
                auth.set("discord-auth.pending." + code, null);
            }
        }
    }

    private void purgeExpiredPending() {
        ensureLoaded();
        ConfigurationSection pending = auth.getConfigurationSection("discord-auth.pending");
        if (pending == null) {
            return;
        }
        long now = nowSeconds();
        for (String code : new ArrayList<>(pending.getKeys(false))) {
            if (auth.getLong("discord-auth.pending." + code + ".expires-at", 0L) <= now) {
                auth.set("discord-auth.pending." + code, null);
            }
        }
    }

    private String randomCode() {
        int length = Math.max(4, Math.min(12, plugin.getConfig().getInt("discord-auth.code-length", 6)));
        String characters = plugin.getConfig().getString("discord-auth.code-characters", DEFAULT_CODE_CHARS);
        if (characters == null || characters.isBlank()) {
            characters = DEFAULT_CODE_CHARS;
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(characters.charAt(random.nextInt(characters.length())));
        }
        return builder.toString();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("discord-auth.enabled", true);
    }

    private boolean allowJoinWhenAuthSystemError() {
        return plugin.getConfig().getBoolean("discord-auth.allow-join-when-auth-system-error", false);
    }

    private boolean restrictUnverified() {
        return plugin.getConfig().getBoolean("discord-auth.restrict-unverified", true);
    }

    private long expireSeconds() {
        return Math.max(1L, plugin.getConfig().getLong("discord-auth.expire-minutes", 10L)) * 60L;
    }

    private long nowSeconds() {
        return Instant.now().getEpochSecond();
    }

    private int expireMinutes() {
        return Math.max(1, plugin.getConfig().getInt("discord-auth.expire-minutes", 10));
    }

    private String discordCommand(String code) {
        return plugin.getConfig().getString("discord-auth.discord-command", "/verify code:{code}").replace("{code}", code);
    }

    private String buildKickMessage(String code) {
        String invite = plugin.getConfig().getString("discord-auth.discord-invite", "");
        StringBuilder message = new StringBuilder();
        message.append("§eMinerVa Discord認証が必要です\n\n")
                .append("§fDiscordサーバーで以下のコードを入力してください。\n\n")
                .append("§b認証コード: §f").append(code).append("\n\n");
        if (invite != null && !invite.isBlank()) {
            message.append("§7Discord: §f").append(invite).append("\n");
        }
        message.append("§7Discordで実行:\n")
                .append("§a").append(discordCommand(code)).append("\n\n")
                .append("§f認証後、もう一度サーバーに接続してください。\n")
                .append("§7コードの有効期限: ").append(expireMinutes()).append("分");
        return message.toString();
    }

    private void ensureLoaded() {
        if (auth == null) {
            load();
        }
    }

    private void ensureConfigDefaults() {
        FileConfiguration config = plugin.getConfig();
        setIfMissing(config, "discord-auth.enabled", true);
        setIfMissing(config, "discord-auth.require-before-join", true);
        setIfMissing(config, "discord-auth.code-length", 6);
        setIfMissing(config, "discord-auth.expire-minutes", 10);
        setIfMissing(config, "discord-auth.code-characters", DEFAULT_CODE_CHARS);
        setIfMissing(config, "discord-auth.discord-command", "/verify code:{code}");
        setIfMissing(config, "discord-auth.discord-invite", "https://discord.gg/ここに招待URL");
        setIfMissing(config, "discord-auth.allow-join-when-auth-system-error", false);
        setIfMissing(config, "discord-auth.restrict-unverified", true);
        setIfMissing(config, "discord-auth.restrictions.block-break", true);
        setIfMissing(config, "discord-auth.restrictions.block-place", true);
        setIfMissing(config, "discord-auth.restrictions.chat", false);
        setIfMissing(config, "discord-auth.restrictions.command", true);
        setIfMissing(config, "discord-auth.restrictions.movement", false);
        setIfMissing(config, "discord-auth.allowed-commands", List.of("/mva auth", "/mva auth code", "/minerva auth", "/minerva auth code"));
        plugin.saveConfig();
    }

    private void setIfMissing(FileConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    private synchronized void sendLookup(CommandSender sender, String query) {
        ensureLoaded();
        loadAuthFromDisk();
        UUID uuid = findUuid(query);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "認証データが見つかりません: " + query);
            return;
        }
        String path = "discord-auth.verified." + uuid;
        if (auth.contains(path)) {
            sender.sendMessage(ChatColor.GREEN + "UUID: " + uuid);
            sender.sendMessage(ChatColor.GREEN + "Minecraft: " + auth.getString(path + ".name", "(unknown)"));
            sender.sendMessage(ChatColor.GREEN + "Discord ID: " + auth.getString(path + ".discord-id", "(none)"));
            sender.sendMessage(ChatColor.GREEN + "Discord Name: " + auth.getString(path + ".discord-name", "(none)"));
            return;
        }
        String code = pendingCodeFor(uuid);
        sender.sendMessage(ChatColor.YELLOW + "UUID: " + uuid);
        sender.sendMessage(ChatColor.YELLOW + "未認証 pending code: " + (code == null ? "(none)" : code));
    }

    private synchronized void sendVerified(CommandSender sender, String query) {
        ensureLoaded();
        loadAuthFromDisk();
        UUID uuid = findUuid(query);
        sender.sendMessage(uuid != null && isVerifiedLoaded(uuid)
                ? ChatColor.GREEN + query + " はDiscord認証済みです。"
                : ChatColor.YELLOW + query + " はDiscord未認証です。");
    }

    private synchronized void unlink(CommandSender sender, String query) {
        ensureLoaded();
        loadAuthFromDisk();
        UUID uuid = findUuid(query);
        if (uuid == null) {
            sender.sendMessage(ChatColor.RED + "認証データが見つかりません: " + query);
            return;
        }
        auth.set("discord-auth.verified." + uuid, null);
        removePendingFor(uuid);
        saveAuth();
        sender.sendMessage(ChatColor.GREEN + "Discord認証を解除しました: " + uuid);
    }

    private UUID findUuid(String query) {
        try {
            return UUID.fromString(query);
        } catch (IllegalArgumentException ignored) {
            // Continue with name lookup.
        }
        ConfigurationSection verified = auth.getConfigurationSection("discord-auth.verified");
        if (verified != null) {
            for (String uuidText : verified.getKeys(false)) {
                if (query.equalsIgnoreCase(auth.getString("discord-auth.verified." + uuidText + ".name", ""))) {
                    try {
                        return UUID.fromString(uuidText);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }
        }
        ConfigurationSection pending = auth.getConfigurationSection("discord-auth.pending");
        if (pending != null) {
            for (String code : pending.getKeys(false)) {
                if (query.equalsIgnoreCase(auth.getString("discord-auth.pending." + code + ".name", ""))) {
                    try {
                        return UUID.fromString(auth.getString("discord-auth.pending." + code + ".uuid"));
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private void loadAuthFromDisk() {
        if (authFile != null && authFile.exists()) {
            auth = YamlConfiguration.loadConfiguration(authFile);
        }
        if (auth == null) {
            auth = new YamlConfiguration();
        }
        if (!auth.contains("discord-auth.verified")) {
            auth.set("discord-auth.verified", new java.util.LinkedHashMap<>());
        }
        if (!auth.contains("discord-auth.pending")) {
            auth.set("discord-auth.pending", new java.util.LinkedHashMap<>());
        }
    }

    private String addressHash(InetAddress address) {
        if (address == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(address.getHostAddress().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private void saveAuth() {
        try {
            saveAuthChecked();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save auth.yml: " + e.getMessage());
        }
    }

    private void saveAuthChecked() throws IOException {
        File tempFile = null;
        try {
            if (authFile.getParentFile() != null && !authFile.getParentFile().exists()) {
                authFile.getParentFile().mkdirs();
            }
            tempFile = File.createTempFile("auth", ".yml.tmp", authFile.getParentFile());
            auth.save(tempFile);
            try {
                Files.move(tempFile.toPath(), authFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), authFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                plugin.getLogger().warning("Could not delete temporary auth file: " + tempFile.getAbsolutePath());
            }
        }
    }

    private record PreLoginDecision(boolean allowed, String message) {
        static PreLoginDecision allow() {
            return new PreLoginDecision(true, "");
        }

        static PreLoginDecision deny(String message) {
            return new PreLoginDecision(false, message);
        }
    }
}
