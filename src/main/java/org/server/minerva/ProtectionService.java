package org.server.minerva;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

final class ProtectionService {
   private final Minerva plugin;
   private final ChunkProtectionFeature chunkProtection;

   ProtectionService(Minerva plugin, ChunkProtectionFeature chunkProtection) {
      this.plugin = plugin;
      this.chunkProtection = chunkProtection;
   }

   boolean isProtected(Location location) {
      return location != null && this.chunkProtection.isProtectedLocation(location);
   }

   boolean canBuild(Player player, Location location) {
      if (player == null || location == null) {
         return false;
      } else {
         return this.hasBypass(player) ? true : this.chunkProtection.canBuild(player, location);
      }
   }

   boolean canInteract(Player player, Location location, InteractionType type) {
      if (player == null || location == null) {
         return false;
      } else if (this.hasBypass(player)) {
         return true;
      } else {
         return (type == InteractionType.SHOP || type == InteractionType.AUCTION) && this.isShopInteractionAllowed(player, location)
            ? true
            : this.chunkProtection.canBuild(player, location);
      }
   }

   boolean isTrusted(Player player, Location location) {
      return player != null && location != null && this.chunkProtection.isTrusted(player, location);
   }

   boolean isSpawnProtected(Location location) {
      return location != null && this.chunkProtection.isSpawnProtected(location);
   }

   boolean isShopInteractionAllowed(Player player, Location location) {
      if (location != null && location.getWorld() != null) {
         Block block = location.getBlock();
         return this.plugin.isShopBlock(block);
      } else {
         return false;
      }
   }

   private boolean hasBypass(Player player) {
      return player.hasPermission("minerva.protect.bypass") || player.hasPermission("minerva.admin");
   }
}
