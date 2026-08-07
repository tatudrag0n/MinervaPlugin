package org.server.minerva;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryGroupFeature implements Listener {
   private static InventoryGroupFeature instance;
   private final JavaPlugin plugin;
   private final Path storageDir;
   private final Map<UUID, Boolean> switching = new ConcurrentHashMap<>();

   private InventoryGroupFeature(JavaPlugin var1) {
      this.plugin = var1;
      this.storageDir = var1.getDataFolder().toPath().resolve("inventory-groups");
   }

   public static void install(JavaPlugin var0) {
      if (instance == null) {
         InventoryGroupFeature var1 = new InventoryGroupFeature(var0);
         instance = var1;

         try {
            Files.createDirectories(var1.storageDir);
         } catch (IOException var4) {
            var0.getLogger().severe("Failed to create inventory group storage: " + var4.getMessage());
         }

         var0.getServer().getPluginManager().registerEvents(var1, var0);

         for (Player var3 : Bukkit.getOnlinePlayers()) {
            var1.restoreAfterJoin(var3);
         }

         var0.getLogger().info("Inventory groups enabled: survival / normal / ephemeral FFA.");
      }
   }

   public static void shutdown(JavaPlugin var0) {
      InventoryGroupFeature var1 = instance;
      if (var1 != null) {
         for (Player var3 : Bukkit.getOnlinePlayers()) {
            InventoryGroupFeature.Group var4 = var1.groupOf(var3.getWorld());
            if (var4 != InventoryGroupFeature.Group.FFA) {
               var1.save(var3, var4);
            }
         }

         instance = null;
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent var1) {
      this.restoreAfterJoin(var1.getPlayer());
   }

   private void restoreAfterJoin(Player var1) {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (var1.isOnline()) {
            InventoryGroupFeature.Group var2 = this.groupOf(var1.getWorld());
            if (var2 != InventoryGroupFeature.Group.FFA) {
               if (this.hasSave(var1.getUniqueId(), var2)) {
                  this.load(var1, var2);
               } else if (var2 == InventoryGroupFeature.Group.SURVIVAL) {
                  this.clearPlayerState(var1);
               }

               this.ensureInitialItems(var1, var2);
            }
         }
      });
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onWorldChange(PlayerChangedWorldEvent var1) {
      Player var2 = var1.getPlayer();
      if (this.switching.putIfAbsent(var2.getUniqueId(), Boolean.TRUE) == null) {
         InventoryGroupFeature.Group var3 = this.groupOf(var1.getFrom());
         InventoryGroupFeature.Group var4 = this.groupOf(var2.getWorld());

         try {
            if (var3 == var4) {
               return;
            }

            if (var3 != InventoryGroupFeature.Group.FFA) {
               this.save(var2, var3);
            }

            if (var4 != InventoryGroupFeature.Group.FFA) {
               long var5 = var3 == InventoryGroupFeature.Group.FFA ? 1L : 0L;
               Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                  try {
                     if (!var2.isOnline() || this.groupOf(var2.getWorld()) != var4) {
                        return;
                     }

                     if (this.hasSave(var2.getUniqueId(), var4)) {
                        this.load(var2, var4);
                     } else {
                        this.clearPlayerState(var2);
                     }

                     this.ensureInitialItems(var2, var4);
                  } finally {
                     this.switching.remove(var2.getUniqueId());
                  }
               }, var5);
               return;
            }
         } finally {
            if (var4 == InventoryGroupFeature.Group.FFA || var3 == var4) {
               this.switching.remove(var2.getUniqueId());
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onQuit(PlayerQuitEvent var1) {
      Player var2 = var1.getPlayer();
      InventoryGroupFeature.Group var3 = this.groupOf(var2.getWorld());
      if (var3 != InventoryGroupFeature.Group.FFA) {
         this.save(var2, var3);
      }

      this.switching.remove(var2.getUniqueId());
   }

   private void ensureInitialItems(Player player, InventoryGroupFeature.Group group) {
      if (group == InventoryGroupFeature.Group.SURVIVAL && this.plugin instanceof Minerva minerva) {
         minerva.giveInitialItemsAfterInventoryRestore(player);
      }
   }

   private InventoryGroupFeature.Group groupOf(World var1) {
      String var2 = var1 == null ? "" : var1.getName().toLowerCase(Locale.ROOT);
      if ("survival".equals(var2)) {
         return InventoryGroupFeature.Group.SURVIVAL;
      } else {
         return "ffa".equals(var2) ? InventoryGroupFeature.Group.FFA : InventoryGroupFeature.Group.NORMAL;
      }
   }

   private boolean hasSave(UUID var1, InventoryGroupFeature.Group var2) {
      return Files.isRegularFile(this.file(var1, var2));
   }

   private Path file(UUID var1, InventoryGroupFeature.Group var2) {
      return this.storageDir.resolve(var1 + "-" + var2.name().toLowerCase(Locale.ROOT) + ".dat");
   }

   private void save(Player var1, InventoryGroupFeature.Group var2) {
      if (var2 != InventoryGroupFeature.Group.FFA) {
         Path var3 = this.file(var1.getUniqueId(), var2);
         Path var4 = var3.resolveSibling(var3.getFileName() + ".tmp");

         try {
            Files.createDirectories(this.storageDir);

            try (DataOutputStream var5 = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(var4)))) {
               var5.writeInt(1);
               writeItems(var5, var1.getInventory().getContents());
               writeItems(var5, var1.getInventory().getArmorContents());
               writeItem(var5, var1.getInventory().getItemInOffHand());
               var5.writeInt(var1.getInventory().getHeldItemSlot());
               var5.writeInt(var1.getLevel());
               var5.writeFloat(var1.getExp());
               var5.writeInt(var1.getFoodLevel());
               var5.writeFloat(var1.getSaturation());
            }

            try {
               Files.move(var4, var3, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException var10) {
               Files.move(var4, var3, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var12) {
            this.plugin.getLogger().severe("Failed to save inventory group for " + var1.getName() + ": " + var12.getMessage());

            try {
               Files.deleteIfExists(var4);
            } catch (IOException var8) {
            }
         }
      }
   }

   private void load(Player var1, InventoryGroupFeature.Group var2) {
      Path var3 = this.file(var1.getUniqueId(), var2);
      if (Files.isRegularFile(var3)) {
         try (DataInputStream var4 = new DataInputStream(new BufferedInputStream(Files.newInputStream(var3)))) {
            int var5 = var4.readInt();
            if (var5 != 1) {
               throw new IOException("Unsupported inventory data version: " + var5);
            }

            ItemStack[] var6 = readItems(var4);
            ItemStack[] var7 = readItems(var4);
            ItemStack var8 = readItem(var4);
            int var9 = var4.readInt();
            int var10 = var4.readInt();
            float var11 = var4.readFloat();
            int var12 = var4.readInt();
            float var13 = var4.readFloat();
            this.clearPlayerState(var1);
            var1.getInventory().setContents(var6);
            var1.getInventory().setArmorContents(var7);
            var1.getInventory().setItemInOffHand(var8);
            var1.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, var9)));
            var1.setLevel(Math.max(0, var10));
            var1.setExp(Math.max(0.0F, Math.min(0.999999F, var11)));
            var1.setFoodLevel(Math.max(0, Math.min(20, var12)));
            var1.setSaturation(Math.max(0.0F, var13));
            var1.setHealth(Math.min(var1.getMaxHealth(), 20.0));
            var1.updateInventory();
         } catch (Exception var16) {
            this.plugin.getLogger().severe("Failed to load inventory group for " + var1.getName() + ": " + var16.getMessage());
         }
      }
   }

   private void clearPlayerState(Player var1) {
      var1.getInventory().clear();
      var1.getInventory().setArmorContents(new ItemStack[4]);
      var1.getInventory().setItemInOffHand(null);
      var1.setLevel(0);
      var1.setExp(0.0F);
      var1.setFoodLevel(20);
      var1.setSaturation(5.0F);
      var1.setHealth(Math.min(var1.getMaxHealth(), 20.0));
      var1.updateInventory();
   }

   private static void writeItems(DataOutputStream var0, ItemStack[] var1) throws IOException {
      var0.writeInt(var1 == null ? 0 : var1.length);
      if (var1 != null) {
         for (ItemStack var5 : var1) {
            writeItem(var0, var5);
         }
      }
   }

   private static ItemStack[] readItems(DataInputStream var0) throws IOException {
      int var1 = var0.readInt();
      if (var1 >= 0 && var1 <= 1000) {
         ItemStack[] var2 = new ItemStack[var1];

         for (int var3 = 0; var3 < var1; var3++) {
            var2[var3] = readItem(var0);
         }

         return var2;
      } else {
         throw new IOException("Invalid inventory length: " + var1);
      }
   }

   private static void writeItem(DataOutputStream var0, ItemStack var1) throws IOException {
      if (var1 != null && !var1.getType().isAir()) {
         byte[] var2 = var1.serializeAsBytes();
         var0.writeInt(var2.length);
         var0.write(var2);
      } else {
         var0.writeInt(-1);
      }
   }

   private static ItemStack readItem(DataInputStream var0) throws IOException {
      int var1 = var0.readInt();
      if (var1 < 0) {
         return null;
      } else if (var1 > 16777216) {
         throw new IOException("Item data is too large");
      } else {
         byte[] var2 = var0.readNBytes(var1);
         if (var2.length != var1) {
            throw new EOFException("Truncated item data");
         } else {
            return ItemStack.deserializeBytes(var2);
         }
      }
   }

   private enum Group {
      SURVIVAL,
      NORMAL,
      FFA;
   }
}
