package org.server.minerva;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

final class SlotJackpotSync {
   private SlotJackpotSync() {
   }

   static boolean beginSpin(Minerva var0, Player var1) {
      String var2 = "players." + var1.getUniqueId();
      String var3 = var2 + ".slot-jackpot";
      FileConfiguration var4 = var0.data();
      if (!var4.getBoolean(var3, false)) {
         return false;
      }

      String var5 = var2 + ".slot-jackpot-spins";
      int var6 = var4.getInt(var5, 0) + 1;
      if (var6 >= 10) {
         var4.set(var3, Boolean.FALSE);
         var4.set(var5, 0);
         var1.sendMessage("§eジャックポットモードを10回プレイしたため、次回から通常モードに戻ります。");
      } else {
         var4.set(var5, var6);
      }

      var0.saveData();
      return true;
   }

   static boolean enableMode(Minerva var0, Player var1) {
      String var2 = "players." + var1.getUniqueId();
      FileConfiguration var3 = var0.data();
      var3.set(var2 + ".slot-jackpot", Boolean.TRUE);
      var3.set(var2 + ".slot-jackpot-spins", 0);
      var0.saveData();
      var1.sendMessage("§6§lジャックポットモード突入！ §e次の10回は当選率と報酬が上昇します。");
      return true;
   }

   static void unlockAndClear(Minerva var0, Player var1, String var2) {
      var0.unlockTitle(var1, var2);
      String var3 = "players." + var1.getUniqueId();
      FileConfiguration var4 = var0.data();
      var4.set(var3 + ".slot-jackpot", Boolean.FALSE);
      var4.set(var3 + ".slot-jackpot-spins", 0);
      var0.saveData();
   }
}
