package org.server.genstructure;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class GenStructurePlugin extends JavaPlugin implements TabExecutor {
    private StructureManager structureManager;
    private Plugin mifronPlugin;
    private Method mifronProtectionMethod;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hookMifronProtection();
        structureManager = new StructureManager(this);
        structureManager.load();
        Bukkit.getPluginManager().registerEvents(structureManager, this);
        PluginCommand command = getCommand("genstructure");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return structureManager.handleCommand(sender, normalizeArgs(args));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return structureManager.tabComplete(normalizeArgs(args));
    }

    boolean isStructureProtectedLocation(Location location) {
        if (location == null) {
            return false;
        }
        return isConfiguredProtectedChunk(location.getChunk()) || isMinervaProtected(location);
    }

    private void hookMinervaProtection() {
        minervaPlugin = Bukkit.getPluginManager().getPlugin("minerva");
        if (minervaPlugin == null) {
            return;
        }
        try {
            minervaProtectionMethod = minervaPlugin.getClass().getDeclaredMethod("isStructureProtectedLocation", Location.class);
            minervaProtectionMethod.setAccessible(true);
            getLogger().info("Linked Minerva protected-area checks.");
        } catch (ReflectiveOperationException e) {
            getLogger().warning("Minerva was found, but protected-area checks are not available: " + e.getMessage());
            minervaProtectionMethod = null;
        }
    }

    private boolean isMinervaProtected(Location location) {
        if (!getConfig().getBoolean("structures.safety.avoid-minerva-protected", true) || minervaProtectionMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(minervaProtectionMethod.invoke(minervaPlugin, location));
        } catch (ReflectiveOperationException e) {
            getLogger().warning("Minerva protected-area check failed: " + e.getMessage());
            minervaProtectionMethod = null;
            return false;
        }
    }

    private boolean isConfiguredProtectedChunk(Chunk chunk) {
        String key = chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
        return getConfig().getStringList("structures.safety.protected-chunks").contains(key);
    }

    private String[] normalizeArgs(String[] args) {
        if (args.length > 0 && "structure".equalsIgnoreCase(args[0])) {
            return Arrays.copyOfRange(args, 1, args.length);
        }
        return args;
    }
}
