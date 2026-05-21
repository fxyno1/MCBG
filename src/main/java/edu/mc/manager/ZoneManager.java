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

    // 阶段对应的尺寸（直径）—— 已调整为原始值的 3/5，增加游戏挑战性
    private final double[] phaseSizes = GameConfig.ZONE_SIZES;
    // 缩圈时间（秒）
    private final int[] phaseTimes = GameConfig.ZONE_SHRINK_TIMES;
    // 毒圈伤害（每两秒伤害量，1.0=半颗心）
    private final double[] phaseDamages = GameConfig.ZONE_DAMAGES;

    private org.bukkit.scheduler.BukkitTask shrinkTask;
    private int remainingShrinkSeconds = 0;

    public int getRemainingShrinkSeconds() {
        return remainingShrinkSeconds;
    }

    // 下一级安全区（也就是白圈）的目标参数，用于雷达地图渲染和缩圈驱动
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
        WorldBorder border = gameWorld.getWorldBorder();

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

        // 默认白圈是整个地图初始边界，代表还没有划定安全区！
        this.targetX = 0.0;
        this.targetZ = 16.0;
        this.targetSize = 600.0;

        Bukkit.broadcastMessage("§a[安全区] 战术雷达GPS已上线。落地后将公布第一波安全区！");
    }

    public void generateNextZone() {
        int nextPhase = phase + 1;
        if (nextPhase >= phaseSizes.length)
            return; // 已经到最后一圈，没有下一波了

        // 当前作为基准的圈（如果游戏还没开始，基准是 600 直径）
        double currentSize = (phase == -1) ? 600.0 : phaseSizes[phase];
        double currentX = (phase == -1) ? 0.0 : targetX;
        double currentZ = (phase == -1) ? 16.0 : targetZ;

        double nextSize = phaseSizes[nextPhase];

        double currentR = currentSize / 2.0;
        double targetR = nextSize / 2.0;

        if (targetR < currentR) {
            // 新的安全区圆心偏移范围：必须完全包含在当前安全区内部
            double maxOffset = currentR - targetR;
            double offsetX = (Math.random() * 2 * maxOffset) - maxOffset;
            double offsetZ = (Math.random() * 2 * maxOffset) - maxOffset;

            this.targetX = currentX + offsetX;
            this.targetZ = currentZ + offsetZ;
            this.targetSize = nextSize;
        }
    }

    public void applyZoneDamage() {
        if (phase < 0 || gameWorld == null)
            return;
        double damage = phaseDamages[phase];
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
                }
            }
        }
    }

    public void shrinkToNextPhase() {
        if (gameWorld == null)
            return;
        if (phase >= phaseSizes.length - 1)
            return;

        if (shrinkTask != null) {
            shrinkTask.cancel();
            shrinkTask = null;
        }

        WorldBorder border = gameWorld.getWorldBorder();
        double currentSize = border.getSize();
        double currentX = border.getCenter().getX();
        double currentZ = border.getCenter().getZ();

        // 提取已经预先计算好的下一个白圈目标参数
        final double finalTargetX = this.targetX;
        final double finalTargetZ = this.targetZ;
        final double finalTargetSize = this.targetSize;

        phase++; // 进入下一阶段

        int timeToShrink = phaseTimes[phase];
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

                        // 关键修改：当前阶段缩圈彻底完成后，才生成并公布下一阶段 of 白圈参数！
                        generateNextZone();
                        Bukkit.broadcastMessage("§a[安全区] 毒圈收缩完毕。下一波安全区（白色区域）已在GPS雷达中标出！");
                        return;
                    }
                    currentTick += 20; // 每一秒更新一次
                    double progress = (double) Math.min(currentTick, totalTicks) / totalTicks;
                    double curX = currentX + (finalTargetX - currentX) * progress;
                    double curZ = currentZ + (finalTargetZ - currentZ) * progress;
                    double curSize = currentSize + (finalTargetSize - currentSize) * progress;
                    border.setCenter(curX, curZ);
                    border.setSize(curSize);
                }
            }.runTaskTimer(plugin, 1L, 20L); // 每 20 ticks (1秒) 更新一次，杜绝客户端 desync
        } else {
            // 如果不需要缩圈（理论上不可能），则直接生成
            generateNextZone();
        }

        Bukkit.broadcastMessage("§c[安全区] 第" + (phase + 1) + "级毒圈开始向白圈位置收缩！");

        Bukkit.getOnlinePlayers().forEach(p -> {
            ((ChickenDinnerPlugin) plugin).sendTitle(p, "§c边界开始收缩", "§e请尽快前往白圈安全区！", 10, 60, 10);
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
        return phase >= phaseSizes.length - 1;
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
