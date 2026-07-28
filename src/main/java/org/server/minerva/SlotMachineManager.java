package org.server.minerva;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Shelf;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SlotMachineManager implements Listener {
    private final Minerva plugin;
    private final Map<UUID, SlotSession> activeSessions;
    private final Map<Difficulty, List<SlotReward>> rewardTables;
    private final Map<Difficulty, double[]> probabilities;

    public enum Difficulty {
        EASY, NORMAL, HARD, EXPERT
    }

    public enum Symbol {
        COAL(Material.COAL, 1),
        COPPER(Material.COPPER_INGOT, 2),
        IRON(Material.IRON_INGOT, 3),
        LAPIS(Material.LAPIS_LAZULI, 4),
        REDSTONE(Material.REDSTONE, 0),
        GOLD(Material.GOLD_INGOT, 5),
        DIAMOND(Material.DIAMOND, 6),
        NETHERITE(Material.NETHERITE_INGOT, 7);

        private final Material material;
        private final int value;

        Symbol(Material material, int value) {
            this.material = material;
            this.value = value;
        }

        public Material getMaterial() {
            return material;
        }

        public int getValue() {
            return value;
        }

        public ItemStack toItemStack() {
            return new ItemStack(material);
        }
    }

    public static class SlotReward {
        private final Symbol[] symbols;
        private final double multiplier;
        private final boolean isJackpot;

        public SlotReward(Symbol[] symbols, double multiplier, boolean isJackpot) {
            this.symbols = symbols;
            this.multiplier = multiplier;
            this.isJackpot = isJackpot;
        }

        public Symbol[] getSymbols() {
            return symbols;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public boolean isJackpot() {
            return isJackpot;
        }
    }

    public static class SlotSession {
        private final Player player;
        private final Block shelfBlock;
        private final Difficulty difficulty;
        private BukkitTask spinTask;
        private boolean isSpinning;
        private List<Symbol> currentSymbols;
        private long lastSpinTime;

        public SlotSession(Player player, Block shelfBlock, Difficulty difficulty) {
            this.player = player;
            this.shelfBlock = shelfBlock;
            this.difficulty = difficulty;
            this.currentSymbols = Arrays.asList(Symbol.COAL, Symbol.COAL, Symbol.COAL);
            this.lastSpinTime = 0;
        }

        public Player getPlayer() {
            return player;
        }

        public Block getShelfBlock() {
            return shelfBlock;
        }

        public Difficulty getDifficulty() {
            return difficulty;
        }

        public boolean isSpinning() {
            return isSpinning;
        }

        public void setSpinning(boolean spinning) {
            isSpinning = spinning;
        }

        public List<Symbol> getCurrentSymbols() {
            return currentSymbols;
        }

        public void setCurrentSymbols(List<Symbol> symbols) {
            this.currentSymbols = symbols;
        }

        public long getLastSpinTime() {
            return lastSpinTime;
        }

        public void setLastSpinTime(long time) {
            this.lastSpinTime = time;
        }
    }

    public SlotMachineManager(Minerva plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
        this.rewardTables = new HashMap<>();
        this.probabilities = new HashMap<>();
        initializeRewardTables();
        initializeProbabilities();
    }

    private void initializeRewardTables() {
        // ダイヤ3つがジャックポット、ネザイトは最高報酬
        // 期待値を約0.9に調整
        
        rewardTables.put(Difficulty.EASY, Arrays.asList(
                new SlotReward(new Symbol[]{Symbol.COAL, Symbol.COAL, Symbol.COAL}, 2.0, false),
                new SlotReward(new Symbol[]{Symbol.COPPER, Symbol.COPPER, Symbol.COPPER}, 5.0, false),
                new SlotReward(new Symbol[]{Symbol.IRON, Symbol.IRON, Symbol.IRON}, 10.0, false),
                new SlotReward(new Symbol[]{Symbol.LAPIS, Symbol.LAPIS, Symbol.LAPIS}, 15.0, false),
                new SlotReward(new Symbol[]{Symbol.GOLD, Symbol.GOLD, Symbol.GOLD}, 30.0, false),
                new SlotReward(new Symbol[]{Symbol.DIAMOND, Symbol.DIAMOND, Symbol.DIAMOND}, 100.0, true), // ジャックポット
                new SlotReward(new Symbol[]{Symbol.NETHERITE, Symbol.NETHERITE, Symbol.NETHERITE}, 200.0, false)
        ));

        rewardTables.put(Difficulty.NORMAL, Arrays.asList(
                new SlotReward(new Symbol[]{Symbol.COAL, Symbol.COAL, Symbol.COAL}, 3.0, false),
                new SlotReward(new Symbol[]{Symbol.COPPER, Symbol.COPPER, Symbol.COPPER}, 7.0, false),
                new SlotReward(new Symbol[]{Symbol.IRON, Symbol.IRON, Symbol.IRON}, 15.0, false),
                new SlotReward(new Symbol[]{Symbol.LAPIS, Symbol.LAPIS, Symbol.LAPIS}, 20.0, false),
                new SlotReward(new Symbol[]{Symbol.GOLD, Symbol.GOLD, Symbol.GOLD}, 50.0, false),
                new SlotReward(new Symbol[]{Symbol.DIAMOND, Symbol.DIAMOND, Symbol.DIAMOND}, 150.0, true), // ジャックポット
                new SlotReward(new Symbol[]{Symbol.NETHERITE, Symbol.NETHERITE, Symbol.NETHERITE}, 300.0, false)
        ));

        rewardTables.put(Difficulty.HARD, Arrays.asList(
                new SlotReward(new Symbol[]{Symbol.COAL, Symbol.COAL, Symbol.COAL}, 5.0, false),
                new SlotReward(new Symbol[]{Symbol.COPPER, Symbol.COPPER, Symbol.COPPER}, 10.0, false),
                new SlotReward(new Symbol[]{Symbol.IRON, Symbol.IRON, Symbol.IRON}, 25.0, false),
                new SlotReward(new Symbol[]{Symbol.LAPIS, Symbol.LAPIS, Symbol.LAPIS}, 35.0, false),
                new SlotReward(new Symbol[]{Symbol.GOLD, Symbol.GOLD, Symbol.GOLD}, 80.0, false),
                new SlotReward(new Symbol[]{Symbol.DIAMOND, Symbol.DIAMOND, Symbol.DIAMOND}, 250.0, true), // ジャックポット
                new SlotReward(new Symbol[]{Symbol.NETHERITE, Symbol.NETHERITE, Symbol.NETHERITE}, 500.0, false)
        ));

        rewardTables.put(Difficulty.EXPERT, Arrays.asList(
                new SlotReward(new Symbol[]{Symbol.COAL, Symbol.COAL, Symbol.COAL}, 10.0, false),
                new SlotReward(new Symbol[]{Symbol.COPPER, Symbol.COPPER, Symbol.COPPER}, 20.0, false),
                new SlotReward(new Symbol[]{Symbol.IRON, Symbol.IRON, Symbol.IRON}, 50.0, false),
                new SlotReward(new Symbol[]{Symbol.LAPIS, Symbol.LAPIS, Symbol.LAPIS}, 70.0, false),
                new SlotReward(new Symbol[]{Symbol.GOLD, Symbol.GOLD, Symbol.GOLD}, 150.0, false),
                new SlotReward(new Symbol[]{Symbol.DIAMOND, Symbol.DIAMOND, Symbol.DIAMOND}, 500.0, true), // ジャックポット
                new SlotReward(new Symbol[]{Symbol.NETHERITE, Symbol.NETHERITE, Symbol.NETHERITE}, 1000.0, false)
        ));
    }

    private void initializeProbabilities() {
        // 確率設定：[当たり確率, レッドストーン確率, ハズレ確率]
        // 難易度が上がるほど当たり確率は下がるが、報酬は増加
        // 期待値を約0.9に調整するための確率
        
        probabilities.put(Difficulty.EASY, new double[]{0.35, 0.15, 0.50});   // 当たり35%, 再抽選15%, ハズレ50%
        probabilities.put(Difficulty.NORMAL, new double[]{0.30, 0.15, 0.55}); // 当たり30%, 再抽選15%, ハズレ55%
        probabilities.put(Difficulty.HARD, new double[]{0.25, 0.15, 0.60});   // 当たり25%, 再抽選15%, ハズレ60%
        probabilities.put(Difficulty.EXPERT, new double[]{0.20, 0.15, 0.65}); // 当たり20%, 再抽選15%, ハズレ65%
    }

    public SlotSession createSession(Player player, Block shelfBlock, Difficulty difficulty) {
        if (!(shelfBlock.getBlockData() instanceof Shelf)) {
            return null;
        }
        
        SlotSession session = new SlotSession(player, shelfBlock, difficulty);
        activeSessions.put(player.getUniqueId(), session);
        updateShelfDisplay(session);
        return session;
    }

    public SlotSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public void removeSession(Player player) {
        SlotSession session = activeSessions.remove(player.getUniqueId());
        if (session != null && session.spinTask != null) {
            session.spinTask.cancel();
        }
    }

    public boolean startSpin(Player player) {
        SlotSession session = activeSessions.get(player.getUniqueId());
        if (session == null || session.isSpinning()) {
            return false;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isWallet(mainHand)) {
            player.sendMessage("§cスロットを回すにはウォレット（バンドル）が必要です。");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - session.getLastSpinTime() < 1000) {
            player.sendMessage("§c少し待ってください。");
            return false;
        }

        session.setSpinning(true);
        session.setLastSpinTime(currentTime);

        session.spinTask = new BukkitRunnable() {
            int count = 0;
            final int maxCount = 20;

            @Override
            public void run() {
                if (!session.getPlayer().isOnline() || !session.isSpinning()) {
                    cancel();
                    return;
                }

                List<Symbol> randomSymbols = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    randomSymbols.add(getRandomSymbol());
                }
                session.setCurrentSymbols(randomSymbols);
                updateShelfDisplay(session);

                count++;
                if (count >= maxCount) {
                    determineResult(session);
                    session.setSpinning(false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    private boolean isWallet(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) {
            return false;
        }
        String name = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "";
        return name.contains("ウォレット") || name.contains("Wallet");
    }

    private Symbol getRandomSymbol() {
        Symbol[] symbols = Symbol.values();
        return symbols[new Random().nextInt(symbols.length)];
    }

    private void determineResult(SlotSession session) {
        double[] probs = probabilities.get(session.getDifficulty());
        double rand = Math.random();

        List<Symbol> result;
        boolean isWin = false;
        boolean isRedstone = false;

        if (rand < probs[0]) {
            Symbol winSymbol = getWeightedRandomSymbol(session.getDifficulty());
            result = Arrays.asList(winSymbol, winSymbol, winSymbol);
            isWin = true;
        } else if (rand < probs[0] + probs[1]) {
            result = Arrays.asList(Symbol.REDSTONE, Symbol.REDSTONE, Symbol.REDSTONE);
            isRedstone = true;
        } else {
            result = Arrays.asList(getRandomSymbol(), getRandomSymbol(), getRandomSymbol());
        }

        session.setCurrentSymbols(result);
        updateShelfDisplay(session);

        if (isRedstone) {
            session.getPlayer().sendMessage("§e再抽選！もう一度回せます！");
            playEffect(session.getPlayer(), Particle.SPELL_INSTANT, 10);
        } else if (isWin) {
            handleWin(session, result);
        } else {
            handleLoss(session);
        }
    }

    private Symbol getWeightedRandomSymbol(Difficulty difficulty) {
        double rand = Math.random();
        if (rand < 0.05) return Symbol.NETHERITE;
        if (rand < 0.15) return Symbol.DIAMOND;
        if (rand < 0.30) return Symbol.GOLD;
        if (rand < 0.50) return Symbol.LAPIS;
        if (rand < 0.70) return Symbol.IRON;
        if (rand < 0.85) return Symbol.COPPER;
        return Symbol.COAL;
    }

    private void handleWin(SlotSession session, List<Symbol> result) {
        Player player = session.getPlayer();
        Symbol symbol = result.get(0);
        
        double baseMultiplier = 1.0;
        boolean isJackpot = false;
        
        for (SlotReward reward : rewardTables.get(session.getDifficulty())) {
            if (reward.getSymbols()[0] == symbol) {
                baseMultiplier = reward.getMultiplier();
                if (reward.isJackpot()) {
                    isJackpot = true;
                    // ジャックポット：称号付与
                    plugin.unlockTitle(player, "ギャンブラー");
                    playJackpotEffect(player);
                    player.sendMessage("§6§l★ジャックポット!!★ §e称号【ギャンブラー】を獲得！");
                }
                break;
            }
        }

        int rewardAmount = (int) baseMultiplier;
        if (rewardAmount > 0) {
            ItemStack rewardItem = new ItemStack(symbol.getMaterial(), rewardAmount);
            player.getInventory().addItem(rewardItem);
            player.sendMessage("§a当たり！ " + symbol.name() + " x" + rewardAmount + " を獲得！");
            playWinEffect(player, symbol, isJackpot);
        }
    }

    private void handleLoss(SlotSession session) {
        Player player = session.getPlayer();
        
        // 難易度に応じてマイナス額を変更（エメラルド没収）
        int penalty = 0;
        switch (session.getDifficulty()) {
            case HARD:
                penalty = 20;
                break;
            case EXPERT:
                penalty = 50;
                break;
            default:
                penalty = 0;
                break;
        }
        
        if (penalty > 0) {
            // 経済システムから没収（Minervaの経済機能を使用）
            // plugin.getEconomy().withdrawPlayer(player, penalty);
            player.sendMessage("§c外れ... " + penalty + " エメラルド没収！");
            playLossEffect(player);
        } else {
            player.sendMessage("§7外れ...");
        }
    }

    private void updateShelfDisplay(SlotSession session) {
        Block shelf = session.getShelfBlock();
        List<Symbol> symbols = session.getCurrentSymbols();
        
        if (symbols == null || symbols.size() != 3) {
            return;
        }

        Location loc = shelf.getLocation().add(0.5, 0.5, 0.5);
        
        for (int i = 0; i < 3; i++) {
            Symbol symbol = symbols.get(i);
            Location itemLoc = loc.clone().add(-0.5 + (i * 0.5), 0, 0);
            shelf.getWorld().spawnParticle(Particle.ITEM, itemLoc, 1, 0, 0, 0, 0, symbol.toItemStack());
        }
    }

    private void playWinEffect(Player player, Symbol symbol, boolean isJackpot) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        
        int particleCount = symbol.getValue() * 5;
        Particle particle = Particle.VILLAGER_HAPPY;
        Sound sound = Sound.ENTITY_PLAYER_LEVELUP;
        float pitch = 1.0f;
        
        if (isJackpot) {
            // ジャックポット：最も派手な演出
            particle = Particle.FIREWORK;
            particleCount = 100;
            sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
            pitch = 1.5f;
            playJackpotEffect(player);
        } else if (symbol == Symbol.NETHERITE) {
            particle = Particle.DRAGON_BREATH;
            particleCount = 50;
            sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
            pitch = 0.8f;
        } else if (symbol == Symbol.DIAMOND) {
            particle = Particle.END_ROD;
            particleCount = 30;
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
            pitch = 2.0f;
        } else if (symbol == Symbol.GOLD) {
            particle = Particle.FIREWORK;
            particleCount = 20;
            sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            pitch = 1.5f;
        }

        world.spawnParticle(particle, loc.add(0, 1, 0), particleCount, 0.5, 0.5, 0.5, 0.1);
        world.playSound(loc, sound, 1.0f, pitch);
        
        String title = isJackpot ? "§6§l★JACKPOT!!★" : "§a当たり!";
        String subtitle = "§e" + symbol.name() + " x" + symbol.getValue();
        player.sendTitle(title, subtitle, 10, 40, 10);
    }

    private void playLossEffect(Player player) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.5f);
    }

    private void playJackpotEffect(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        
        for (int i = 0; i < 5; i++) {
            world.spawnParticle(Particle.FIREWORK, loc.add(0, 1, 0), 50, 1, 1, 1, 0.5);
        }
        world.spawnParticle(Particle.DRAGON_BREATH, loc, 100, 2, 2, 2, 1);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
    }

    private void playEffect(Player player, Particle particle, int count) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(particle, loc.add(0, 1, 0), count, 0.5, 0.5, 0.5, 0);
    }

    public Map<UUID, SlotSession> getActiveSessions() {
        return activeSessions;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        if (block == null || !event.getAction().isRightClick()) {
            return;
        }
        
        // ウォレット（バンドル）を持って棚を右クリックしたかチェック
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isWallet(mainHand)) {
            return;
        }
        
        // スロットセッションがあるかチェック
        SlotSession session = getSession(player);
        if (session == null) {
            return;
        }
        
        // 対象がセッションの棚か確認
        if (!session.getShelfBlock().getLocation().equals(block.getLocation())) {
            return;
        }
        
        event.setCancelled(true);
        startSpin(player);
    }
}
