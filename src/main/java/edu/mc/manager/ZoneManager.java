package edu.mc.manager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import org.bukkit.Location;

public class ZoneManager {

    private final Plugin plugin;
    private final World gameWorld;
    private int phase = -1;

    // 阶段对应的尺寸（直径）—— 从 GameConfig 动态获取以支持热重载
    private double[] getPhaseSizes() { return GameConfig.ZONE_SIZES; }
    private int[] getPhaseTimes() { return GameConfig.ZONE_SHRINK_TIMES; }
    private double[] getPhaseDamages() { return GameConfig.ZONE_DAMAGES; }

    private org.bukkit.scheduler.BukkitTask shrinkTask;
    private int remainingShrinkSeconds = 0;

    public int getRemainingShrinkSeconds() {
        return remainingShrinkSeconds;
    }

    // 下一级安全区（白圈）的目标参数，用于雷达地图渲染和缩圈驱动
    private double targetX = 0.0;
    private double targetZ = 16.0;
    private double targetSize = 600.0;

    public ZoneManager(Plugin plugin, World world) {
        this.plugin = plugin;
        this.gameWorld = world;
    }

    public void initBorder() {
        if (gameWorld == null)
            return;

        // 初始化官方原版 WorldBorder 为 600 格全岛范围
        WorldBorder border = gameWorld.getWorldBorder();
        border.reset();
        border.setCenter(0, 16);
        border.setSize(600.0);
        border.setDamageAmount(0.0);
        border.setDamageBuffer(0.0);
        border.setWarningDistance(0);

        this.phase = -1;
        this.remainingShrinkSeconds = 0;
        if (shrinkTask != null) {
            shrinkTask.cancel();
            shrinkTask = null;
        }

        this.targetX = 0.0;
        this.targetZ = 16.0;
        this.targetSize = 600.0;

        Bukkit.broadcastMessage(((ChickenDinnerPlugin) plugin).getMessageManager().getMessage("zone.radar_online"));
    }

    public void generateNextZone() {
        int nextPhase = phase + 1;
        if (nextPhase >= getPhaseSizes().length)
            return; // 已经到最后一圈，没有下一波了

        // 当前作为基准的圈（如果游戏还没开始，基准是 600 直径）
        double baseSize = (phase == -1) ? 600.0 : getPhaseSizes()[phase];
        double baseX = (phase == -1) ? 0.0 : targetX;
        double baseZ = (phase == -1) ? 16.0 : targetZ;

        double nextSize = getPhaseSizes()[nextPhase];

        double currentR = baseSize / 2.0;
        double targetR = nextSize / 2.0;

        if (targetR < currentR) {
            // 新的安全区圆心偏移范围：必须完全包含在当前安全区内部
            double maxOffset = currentR - targetR;
            double offsetX = (Math.random() * 2 * maxOffset) - maxOffset;
            double offsetZ = (Math.random() * 2 * maxOffset) - maxOffset;

            this.targetX = baseX + offsetX;
            this.targetZ = baseZ + offsetZ;
            this.targetSize = nextSize;
        }
    }

    public void applyZoneDamage() {
        if (phase < 0 || gameWorld == null)
            return;
        double damage = (phase < getPhaseDamages().length) ? getPhaseDamages()[phase] : 1.0;
        if (damage <= 0)
            return;

        WorldBorder border = gameWorld.getWorldBorder();
        double size = border.getSize();
        Location center = border.getCenter();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE)
                continue;
            if (!p.getWorld().equals(gameWorld))
                continue;

            // 方形毒圈判定：X 或 Z 超出半径即扣血
            double dx = Math.abs(p.getLocation().getX() - center.getX());
            double dz = Math.abs(p.getLocation().getZ() - center.getZ());

            if (dx > size / 2.0 || dz > size / 2.0) {
                double newHealth = p.getHealth() - damage;
                if (newHealth <= 0) {
                    p.setHealth(0);
                } else {
                    p.setHealth(newHealth);
                    p.playEffect(org.bukkit.EntityEffect.HURT);
                    ((ChickenDinnerPlugin) plugin).sendActionBar(p, "§c§l⚠ 你正处于毒圈中！生命值持续下降中！");
                }
            }
        }
    }

    public void shrinkToNextPhase() {
        if (gameWorld == null)
            return;
        if (phase >= getPhaseSizes().length - 1)
            return;

        if (shrinkTask != null) {
            shrinkTask.cancel();
            shrinkTask = null;
        }

        WorldBorder border = gameWorld.getWorldBorder();
        double currentSize = border.getSize();
        double currentX = border.getCenter().getX();
        double currentZ = border.getCenter().getZ();

        final double finalTargetX = this.targetX;
        final double finalTargetZ = this.targetZ;
        final double finalTargetSize = this.targetSize;

        phase++; // 进入下一阶段

        int timeToShrink = getPhaseTimes()[phase];
        final int totalTicks = timeToShrink * 20;

        if (finalTargetSize < currentSize) {
            shrinkTask = new org.bukkit.scheduler.BukkitRunnable() {
                int currentTick = 0;

                @Override
                public void run() {
                    remainingShrinkSeconds = Math.max(0, (totalTicks - currentTick) / 20);
                    if (currentTick >= totalTicks) {
                        border.setCenter(finalTargetX, finalTargetZ);
                        border.setSize(finalTargetSize);
                        this.cancel();
                        shrinkTask = null;
                        remainingShrinkSeconds = 0;

                        // 当前阶段缩圈彻底完成后，生成并公布下一阶段白圈参数
                        generateNextZone();
                        Bukkit.broadcastMessage(((ChickenDinnerPlugin) plugin).getMessageManager().getMessage("zone.shrink_done"));
                        return;
                    }
                    currentTick += 20; // 每一秒更新一次
                    double progress = (double) Math.min(currentTick, totalTicks) / totalTicks;
                    double curX = currentX + (finalTargetX - currentX) * progress;
                    double curZ = currentZ + (finalTargetZ - currentZ) * progress;

                    double nextProgress = (double) Math.min(currentTick + 20, totalTicks) / totalTicks;
                    double nextSize = currentSize + (finalTargetSize - currentSize) * nextProgress;

                    border.setCenter(curX, curZ);
                    // 触发原版 2 秒平滑收缩动画，呈现官方红色收缩光幕
                    border.setSize(nextSize, 2L);
                }
            }.runTaskTimer(plugin, 1L, 20L);
        } else {
            generateNextZone();
        }

        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("phase", String.valueOf(phase + 1));
        Bukkit.broadcastMessage(((ChickenDinnerPlugin) plugin).getMessageManager().getMessage("zone.shrink_start", map));

        Bukkit.getOnlinePlayers().forEach(p -> {
            ((ChickenDinnerPlugin) plugin).sendTitle(p, ((ChickenDinnerPlugin) plugin).getMessageManager().getMessage("zone.title_shrink_start"), ((ChickenDinnerPlugin) plugin).getMessageManager().getMessage("zone.title_shrink_start_sub"), 10, 60, 10);
        });

        // 每次开始缩圈时，在目标白圈内生成一个空投
        ((ChickenDinnerPlugin) plugin).getAirdropManager().spawnAirdrop(finalTargetX, finalTargetZ, finalTargetSize);
    }

    /**
     * 当前阶段是否仍在收缩动画中（用于 GameManager 判断是否暂停下一波收缩倒数）
     */
    public boolean isShrinking() {
        return shrinkTask != null;
    }

    public double getCurrentX() {
        return gameWorld != null ? gameWorld.getWorldBorder().getCenter().getX() : 0.0;
    }

    public double getCurrentZ() {
        return gameWorld != null ? gameWorld.getWorldBorder().getCenter().getZ() : 16.0;
    }

    public double getCurrentSize() {
        return gameWorld != null ? gameWorld.getWorldBorder().getSize() : 600.0;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetZ() {
        return targetZ;
    }

    public double getTargetSize() {
        return targetSize;
    }

    public int getCurrentPhase() {
        return phase;
    }

    public boolean isMaxPhase() {
        return phase >= getPhaseSizes().length - 1;
    }

    public void reset() {
        if (shrinkTask != null) {
            shrinkTask.cancel();
            shrinkTask = null;
        }
        this.phase = -1;
        this.targetX = 0.0;
        this.targetZ = 16.0;
        this.targetSize = 600.0;
        this.remainingShrinkSeconds = 0;
    }
}
