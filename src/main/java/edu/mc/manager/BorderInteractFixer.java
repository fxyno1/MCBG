package edu.mc.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;

public class BorderInteractFixer {

    /**
     * 精确判定坐标是否在 Minecraft 原生世界边界之外。
     * NMS 原生判定 isInBounds 规则：(x + 1) > minX && x < maxX && (z + 1) > minZ && z < maxZ
     * 只要不满足上述条件，服务端底层 NetHandlerPlayServer 就会静默丢弃 PacketPlayInBlockPlace 数据包。
     */
    public static boolean isOutsideBorder(WorldBorder border, int x, int z) {
        if (border == null) return false;
        double size = border.getSize();
        double half = size / 2.0D;
        double minX = border.getCenter().getX() - half;
        double maxX = border.getCenter().getX() + half;
        double minZ = border.getCenter().getZ() - half;
        double maxZ = border.getCenter().getZ() + half;

        return (x + 1) <= minX || x >= maxX || (z + 1) <= minZ || z >= maxZ;
    }

    public static void register(final ChickenDinnerPlugin cdPlugin) {
        try {
            // 监听数据包：BLOCK_PLACE（仅右键方块与右键手持物品）
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                    cdPlugin, com.comphenix.protocol.events.ListenerPriority.NORMAL,
                    PacketType.Play.Client.BLOCK_PLACE) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null || !player.isOnline()) return;

                    PacketContainer packet = event.getPacket();
                    BlockPosition pos = packet.getBlockPositionModifier().readSafely(0);

                    if (pos == null) return;

                    // 情况 A：客户端右键指向了特定方块
                    if (pos.getY() >= 0 && pos.getY() <= 255 && !(pos.getX() == -1 && pos.getY() == -1 && pos.getZ() == -1)) {
                        WorldBorder border = player.getWorld().getWorldBorder();
                        if (isOutsideBorder(border, pos.getX(), pos.getZ())) {
                            final int bx = pos.getX();
                            final int by = pos.getY();
                            final int bz = pos.getZ();

                            Bukkit.getScheduler().runTask(cdPlugin, () -> {
                                if (!player.isOnline()) return;
                                Location loc = new Location(player.getWorld(), bx, by, bz);
                                tryOpenChest(player, cdPlugin, loc.getBlock());
                            });
                        }
                    } else {
                        // 情况 B：客户端右键空气或手持物品时触发（pos 为 -1,-1,-1）
                        // 通过视线射线检测前方方块，实现右键开箱兜底
                        Bukkit.getScheduler().runTask(cdPlugin, () -> {
                            if (!player.isOnline()) return;
                            Block target = getTargetChestBlock(player, 5);
                            if (target != null) {
                                tryOpenChest(player, cdPlugin, target);
                            }
                        });
                    }
                }
            });

            Bukkit.getLogger().info("[MCBG] 成功注册右键开箱补丁！");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[MCBG] 注册开箱补丁失败: " + e.getMessage());
        }
    }

    /**
     * 视线射线检测：检测玩家准心对准的箱子或附着在箱子上的告示牌
     */
    private static Block getTargetChestBlock(Player player, int maxDistance) {
        try {
            Block block = player.getTargetBlock((Set<Material>) null, maxDistance);
            if (block == null) return null;
            Material type = block.getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
                return block;
            }
            if (type == Material.WALL_SIGN || type == Material.SIGN_POST) {
                if (block.getState() instanceof org.bukkit.block.Sign) {
                    org.bukkit.block.Sign sign = (org.bukkit.block.Sign) block.getState();
                    if (sign.getData() instanceof org.bukkit.material.Sign) {
                        org.bukkit.material.Sign signData = (org.bukkit.material.Sign) sign.getData();
                        Block attached = block.getRelative(signData.getAttachedFace());
                        if (attached.getType() == Material.CHEST || attached.getType() == Material.TRAPPED_CHEST) {
                            return attached;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 尝试为玩家打开目标箱子
     */
    private static void tryOpenChest(Player player, ChickenDinnerPlugin cdPlugin, Block block) {
        if (block == null) return;

        // 旁观者或已死亡玩家不可交互
        if (cdPlugin.getPlayerManager().isSpectator(player)
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        // 游戏状态校验（比赛进行中，或 OP 管理员用于测试）
        GameState state = cdPlugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT && !player.isOp()) {
            return;
        }

        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);

        // 距离校验（放宽至 8 格，容差网络延迟与高低差）
        if (player.getEyeLocation().distanceSquared(blockCenter) > 64.0) {
            return;
        }

        Material type = block.getType();

        // 支持附着在箱子上的告示牌
        if (type == Material.WALL_SIGN || type == Material.SIGN_POST) {
            if (block.getState() instanceof org.bukkit.block.Sign) {
                org.bukkit.block.Sign sign = (org.bukkit.block.Sign) block.getState();
                if (sign.getData() instanceof org.bukkit.material.Sign) {
                    org.bukkit.material.Sign signData = (org.bukkit.material.Sign) sign.getData();
                    Block attached = block.getRelative(signData.getAttachedFace());
                    if (attached.getType() == Material.CHEST || attached.getType() == Material.TRAPPED_CHEST) {
                        block = attached;
                        type = attached.getType();
                    }
                }
            }
        }

        // 处理普通箱子与陷阱/空投箱
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
            PlayerInteractEvent fakeEvent = new PlayerInteractEvent(
                    player, Action.RIGHT_CLICK_BLOCK, player.getItemInHand(), block,
                    org.bukkit.block.BlockFace.UP);

            Bukkit.getPluginManager().callEvent(fakeEvent);

            if (!fakeEvent.isCancelled()
                    && fakeEvent.useInteractedBlock() != org.bukkit.event.Event.Result.DENY) {
                if (block.getState() instanceof org.bukkit.block.Chest) {
                    org.bukkit.block.Chest chest = (org.bukkit.block.Chest) block.getState();
                    player.openInventory(chest.getInventory());
                    player.playSound(blockCenter, Sound.CHEST_OPEN, 0.5F, 1.0F);
                }
            }
        } else if (type == Material.ENDER_CHEST) {
            // 处理末影箱
            PlayerInteractEvent fakeEvent = new PlayerInteractEvent(
                    player, Action.RIGHT_CLICK_BLOCK, player.getItemInHand(), block,
                    org.bukkit.block.BlockFace.UP);

            Bukkit.getPluginManager().callEvent(fakeEvent);

            if (!fakeEvent.isCancelled()
                    && fakeEvent.useInteractedBlock() != org.bukkit.event.Event.Result.DENY) {
                player.openInventory(player.getEnderChest());
                player.playSound(blockCenter, Sound.CHEST_OPEN, 0.5F, 1.0F);
            }
        }
    }
}
