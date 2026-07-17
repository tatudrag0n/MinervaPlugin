package org.server.minerva;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

final class FfaKitStandManager {
    private static final String TAG = "minerva_ffa_kit_stand";
    private static final String SELECTOR_TAG = "minerva_ffa_kit_selector";

    private final Minerva plugin;
    private final FfaConfig config;
    private final NamespacedKey kitKey;
    private final NamespacedKey selectorKey;

    FfaKitStandManager(Minerva plugin, FfaConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.kitKey = new NamespacedKey(plugin, "ffa_kit");
        this.selectorKey = new NamespacedKey(plugin, "ffa_kit_selector");
    }

    int createKitStands() {
        Location base = config.kitSelection();
        if (base == null) {
            base = config.center();
        }
        if (base == null) {
            return -1;
        }
        removeKitStands();
        Location location = base.clone();
        location.setYaw(base.getYaw());
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.customName(Component.text("§aFFAキット選択"));
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setPersistent(true);
        stand.addScoreboardTag(TAG);
        stand.addScoreboardTag(SELECTOR_TAG);
        stand.getPersistentDataContainer().set(selectorKey, PersistentDataType.BOOLEAN, true);
        FfaKit selected = selectedKit();
        selected.applyStandEquipment(stand.getEquipment(), config, plugin);
        saveStandLocation(location, selected);
        plugin.saveConfig();
        return 1;
    }

    int removeKitStands() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isKitSelector(entity) || legacyKitFromEntity(entity) != null) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    boolean isKitSelector(Entity entity) {
        if (!(entity instanceof ArmorStand)) {
            return false;
        }
        if (Boolean.TRUE.equals(entity.getPersistentDataContainer().get(selectorKey, PersistentDataType.BOOLEAN))) {
            return true;
        }
        return entity.getScoreboardTags().contains(SELECTOR_TAG)
                || entity.getScoreboardTags().contains(TAG)
                || legacyKitFromEntity(entity) != null;
    }

    void applySelectedKit(FfaKit kit) {
        plugin.getConfig().set("ffa.stands.selected-kit", kit.key());
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isKitSelector(entity) && entity instanceof ArmorStand stand) {
                    stand.customName(Component.text("§aFFAキット選択: " + kit.displayName(config)));
                    kit.applyStandEquipment(stand.getEquipment(), config, plugin);
                }
            }
        }
        plugin.saveConfig();
    }

    FfaKit selectedKit() {
        FfaKit kit = FfaKit.fromKey(plugin.getConfig().getString("ffa.stands.selected-kit", FfaKit.SWORD.key()));
        return kit == null || !kit.isActive(config) ? FfaKit.SWORD : kit;
    }

    private FfaKit legacyKitFromEntity(Entity entity) {
        if (!(entity instanceof ArmorStand)) {
            return null;
        }
        String kit = entity.getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (kit == null && entity.getScoreboardTags().contains(TAG)) {
            kit = entity.getScoreboardTags().stream()
                    .filter(tag -> tag.startsWith("minerva_ffa_kit="))
                    .map(tag -> tag.substring("minerva_ffa_kit=".length()))
                    .findFirst()
                    .orElse(null);
        }
        return FfaKit.fromKey(kit);
    }

    void playClick(Location location) {
        if (location != null && location.getWorld() != null) {
            location.getWorld().playSound(location, Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
        }
    }

    private void saveStandLocation(Location location, FfaKit selected) {
        FileConfiguration file = plugin.getConfig();
        String path = "ffa.stands.selector.";
        file.set(path + "world", location.getWorld().getName());
        file.set(path + "x", location.getX());
        file.set(path + "y", location.getY());
        file.set(path + "z", location.getZ());
        file.set(path + "yaw", location.getYaw());
        file.set(path + "pitch", location.getPitch());
        file.set(path + "selected-kit", selected.key());
    }
}
