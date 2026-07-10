package org.server.minerva;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;

final class WorldEditHook {
    private final Minerva plugin;

    WorldEditHook(Minerva plugin) {
        this.plugin = plugin;
    }

    boolean available() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null && classExists("com.sk89q.worldedit.WorldEdit");
    }

    boolean savePlayerClipboard(Player player, File file) {
        if (!available()) {
            return false;
        }
        try {
            Object actor = callStatic("com.sk89q.worldedit.bukkit.BukkitAdapter", "adapt", new Class<?>[]{Player.class}, player);
            Object worldEdit = callStatic("com.sk89q.worldedit.WorldEdit", "getInstance", new Class<?>[0]);
            Object sessionManager = call(worldEdit, "getSessionManager", new Class<?>[0]);
            Object session = call(sessionManager, "get", new Class<?>[]{Class.forName("com.sk89q.worldedit.extension.platform.Actor")}, actor);
            Object holder = call(session, "getClipboard", new Class<?>[0]);
            Object clipboard = call(holder, "getClipboard", new Class<?>[0]);
            Object format = enumConstant("com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat", "SPONGE_SCHEMATIC");
            file.getParentFile().mkdirs();
            try (OutputStream output = new FileOutputStream(file)) {
                Object writer = call(format, "getWriter", new Class<?>[]{OutputStream.class}, output);
                try {
                    call(writer, "write", new Class<?>[]{Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard")}, clipboard);
                } finally {
                    call(writer, "close", new Class<?>[0]);
                }
            }
            return true;
        } catch (Throwable e) {
            plugin.getLogger().warning("WorldEdit clipboard registration failed: " + e.getMessage());
            return false;
        }
    }

    boolean pasteSchematic(File file, Location location) {
        if (!available() || file == null || !file.exists()) {
            return false;
        }
        try (var input = Files.newInputStream(file.toPath())) {
            Object format = callStatic("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats", "findByFile", new Class<?>[]{File.class}, file);
            if (format == null) {
                return false;
            }
            Object reader = call(format, "getReader", new Class<?>[]{java.io.InputStream.class}, input);
            Object clipboard;
            try {
                clipboard = call(reader, "read", new Class<?>[0]);
            } finally {
                call(reader, "close", new Class<?>[0]);
            }
            Object adaptedWorld = callStatic("com.sk89q.worldedit.bukkit.BukkitAdapter", "adapt", new Class<?>[]{org.bukkit.World.class}, location.getWorld());
            Object worldEdit = callStatic("com.sk89q.worldedit.WorldEdit", "getInstance", new Class<?>[0]);
            Object editSession = call(worldEdit, "newEditSession", new Class<?>[]{Class.forName("com.sk89q.worldedit.world.World")}, adaptedWorld);
            Constructor<?> holderConstructor = Class.forName("com.sk89q.worldedit.session.ClipboardHolder")
                    .getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"));
            Object holder = holderConstructor.newInstance(clipboard);
            Object pasteBuilder = call(holder, "createPaste", new Class<?>[]{Class.forName("com.sk89q.worldedit.EditSession")}, editSession);
            Object vector = callStatic("com.sk89q.worldedit.math.BlockVector3", "at",
                    new Class<?>[]{int.class, int.class, int.class},
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
            call(pasteBuilder, "to", new Class<?>[]{Class.forName("com.sk89q.worldedit.math.BlockVector3")}, vector);
            call(pasteBuilder, "ignoreAirBlocks", new Class<?>[]{boolean.class}, false);
            Object operation = call(pasteBuilder, "build", new Class<?>[0]);
            callStatic("com.sk89q.worldedit.function.operation.Operations", "complete",
                    new Class<?>[]{Class.forName("com.sk89q.worldedit.function.operation.Operation")}, operation);
            call(editSession, "close", new Class<?>[0]);
            return true;
        } catch (Throwable e) {
            plugin.getLogger().warning("WorldEdit paste failed: " + e.getMessage());
            return false;
        }
    }

    private boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private Object enumConstant(String className, String fieldName) throws Exception {
        Field field = Class.forName(className).getField(fieldName);
        return field.get(null);
    }

    private Object callStatic(String className, String method, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method target = Class.forName(className).getMethod(method, parameterTypes);
        return target.invoke(null, args);
    }

    private Object call(Object receiver, String method, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method target = receiver.getClass().getMethod(method, parameterTypes);
        return target.invoke(receiver, args);
    }
}
