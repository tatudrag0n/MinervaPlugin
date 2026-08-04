package org.server.minerva;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class WorldEditHook {
   private final Minerva plugin;

   WorldEditHook(Minerva plugin) {
      this.plugin = plugin;
   }

   boolean available() {
      return Bukkit.getPluginManager().getPlugin("WorldEdit") != null && this.classExists("com.sk89q.worldedit.WorldEdit");
   }

   boolean savePlayerClipboard(Player player, File file) {
      if (!this.available()) {
         return false;
      }

      try {
         Object actor = this.callStatic("com.sk89q.worldedit.bukkit.BukkitAdapter", "adapt", new Class[]{Player.class}, player);
         Object worldEdit = this.callStatic("com.sk89q.worldedit.WorldEdit", "getInstance", new Class[0]);
         Object sessionManager = this.call(worldEdit, "getSessionManager", new Class[0]);
         Object session = this.call(sessionManager, "get", new Class[]{Class.forName("com.sk89q.worldedit.extension.platform.Actor")}, actor);
         Object holder = this.call(session, "getClipboard", new Class[0]);
         Object clipboard = this.call(holder, "getClipboard", new Class[0]);
         Object format = this.enumConstant("com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat", "SPONGE_SCHEMATIC");
         file.getParentFile().mkdirs();

         try (OutputStream output = new FileOutputStream(file)) {
            Object writer = this.call(format, "getWriter", new Class[]{OutputStream.class}, output);

            try {
               this.call(writer, "write", new Class[]{Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard")}, clipboard);
            } finally {
               this.call(writer, "close", new Class[0]);
            }
         }

         return true;
      } catch (Throwable e) {
         this.plugin.getLogger().warning("WorldEdit clipboard registration failed: " + e.getMessage());
         return false;
      }
   }

   boolean pasteSchematic(File file, Location location) {
      if (this.available() && file != null && file.exists()) {
         try (InputStream input = Files.newInputStream(file.toPath())) {
            Object format = this.callStatic("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats", "findByFile", new Class[]{File.class}, file);
            if (format == null) {
               return false;
            }

            Object reader = this.call(format, "getReader", new Class[]{InputStream.class}, input);

            Object clipboard;
            try {
               clipboard = this.call(reader, "read", new Class[0]);
            } finally {
               this.call(reader, "close", new Class[0]);
            }

            Object adaptedWorld = this.callStatic("com.sk89q.worldedit.bukkit.BukkitAdapter", "adapt", new Class[]{World.class}, location.getWorld());
            Object worldEdit = this.callStatic("com.sk89q.worldedit.WorldEdit", "getInstance", new Class[0]);
            Object editSession = this.call(worldEdit, "newEditSession", new Class[]{Class.forName("com.sk89q.worldedit.world.World")}, adaptedWorld);
            Constructor<?> holderConstructor = Class.forName("com.sk89q.worldedit.session.ClipboardHolder")
               .getConstructor(Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard"));
            Object holder = holderConstructor.newInstance(clipboard);
            Object pasteBuilder = this.call(holder, "createPaste", new Class[]{Class.forName("com.sk89q.worldedit.EditSession")}, editSession);
            Object vector = this.callStatic(
               "com.sk89q.worldedit.math.BlockVector3",
               "at",
               new Class[]{int.class, int.class, int.class},
               location.getBlockX(),
               location.getBlockY(),
               location.getBlockZ()
            );
            this.call(pasteBuilder, "to", new Class[]{Class.forName("com.sk89q.worldedit.math.BlockVector3")}, vector);
            this.call(pasteBuilder, "ignoreAirBlocks", new Class[]{boolean.class}, false);
            Object operation = this.call(pasteBuilder, "build", new Class[0]);
            this.callStatic(
               "com.sk89q.worldedit.function.operation.Operations",
               "complete",
               new Class[]{Class.forName("com.sk89q.worldedit.function.operation.Operation")},
               operation
            );
            this.call(editSession, "close", new Class[0]);
            return true;
         } catch (Throwable e) {
            this.plugin.getLogger().warning("WorldEdit paste failed: " + e.getMessage());
            return false;
         }
      } else {
         return false;
      }
   }

   int[] readSchematicSize(File file) {
      if (this.available() && file != null && file.exists()) {
         try (InputStream input = Files.newInputStream(file.toPath())) {
            Object format = this.callStatic("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats", "findByFile", new Class[]{File.class}, file);
            if (format == null) {
               return null;
            }

            Object reader = this.call(format, "getReader", new Class[]{InputStream.class}, input);

            Object clipboard;
            try {
               clipboard = this.call(reader, "read", new Class[0]);
            } finally {
               this.call(reader, "close", new Class[0]);
            }

            Object dimensions = this.call(clipboard, "getDimensions", new Class[0]);
            return new int[]{
               this.coordinate(dimensions, "x", "getBlockX"), this.coordinate(dimensions, "y", "getBlockY"), this.coordinate(dimensions, "z", "getBlockZ")
            };
         } catch (Throwable e) {
            this.plugin.getLogger().warning("WorldEdit schematic size read failed: " + e.getMessage());
            return null;
         }
      } else {
         return null;
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

   private int coordinate(Object vector, String primaryMethod, String fallbackMethod) throws Exception {
      try {
         return ((Number)this.call(vector, primaryMethod, new Class[0])).intValue();
      } catch (NoSuchMethodException ignored) {
         return ((Number)this.call(vector, fallbackMethod, new Class[0])).intValue();
      }
   }
}
