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
        return location != null && chunkProtection.isProtectedLocation(location);
    }

    boolean canBuild(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }
        if (hasBypass(player)) {
            return true;
        }
        return chunkProtection.canBuild(player, location);
    }

    boolean canInteract(Player player, Location location, InteractionType type) {
        if (player == null || location == null) {
            return false;
        }
        if (hasBypass(player)) {
            return true;
        }
        if ((type == InteractionType.SHOP || type == InteractionType.AUCTION) && isShopInteractionAllowed(player, location)) {
            return true;
        }
        return chunkProtection.canBuild(player, location);
    }

    boolean isTrusted(Player player, Location location) {
        return player != null && location != null && chunkProtection.isTrusted(player, location);
    }

    boolean isSpawnProtected(Location location) {
        return location != null && chunkProtection.isSpawnProtected(location);
    }

    boolean isShopInteractionAllowed(Player player, Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block block = location.getBlock();
        return plugin.isShopBlock(block);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("minerva.protect.bypass") || player.hasPermission("minerva.admin");
    }
}
