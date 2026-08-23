package edu.mc.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

/**
 * 边界外开箱核心代理管理器（高精度箱体紧密贴合版 + 观察模式隐形净化）：
 * 1. 严格将微型盔甲架下沉至 y-0.35 并内聚于箱体核心内部（X/Z 范围 0.22~0.78，Y 范围 -0.35~0.64）；
 * 2. 彻底消除箱子上方与四周相邻方块的空气多余判定；
 * 3. 观察者模式隐形净化（Spectator Packet Filter）：旁观者客户端自动屏蔽/销毁所有代理实体数据包，观察模式下绝对看不到任何半透明幽灵模型；
 * 4. 完美支持单箱子、大箱子（DoubleChest）、空投箱与玩家阵亡遗物盒子；
 * 5. 突破 WorldBorder 限制，支持空手与手持物品 100% 顺畅右键秒开箱；
 * 6. 实装 3D 物理开盖/关盖动画与四周告示牌自动生成。
 */
public class ChestProxyManager implements Listener {

    private final ChickenDinnerPlugin plugin;
    public static final String METADATA_KEY = "CHEST_PROXY";

    // 记录正在被玩家打开的箱子方块位置，用于关箱时播放关盖动画
    private final Map<UUID, Block> openChestMap = new HashMap<>();

    public ChestProxyManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        registerSpectatorPacketFilter();
    }

    /**
     * 注册 ProtocolLib 发包过滤器：对于旁观者（Spectator），完全拦截所有微型代理实体的生成包
     */
    private void registerSpectatorPacketFilter() {
        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                    plugin, ListenerPriority.NORMAL, PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null || !player.isOnline()) return;

                    // 如果玩家是旁观者模式或死亡观战状态，则绝不发送代理盔甲架生成包
                    if (player.getGameMode() == GameMode.SPECTATOR || ChestProxyManager.this.plugin.getPlayerManager().isSpectator(player)) {
                        int entityId = event.getPacket().getIntegers().read(0);
                        Entity entity = ProtocolLibrary.getProtocolManager().getEntityFromID(player.getWorld(), entityId);
                        if (entity != null && entity.hasMetadata(METADATA_KEY)) {
                            event.setCancelled(true); // 拦截！旁观者客户端完全接收不到该实体
                        }
                    }
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("[ChestProxyManager] 注册旁观者发包过滤器失败: " + e.getMessage());
        }
    }

    /**
     * 向指定旁观者发送 EntityDestroy 数据包，清除其客户端视野中残留的所有微型代理实体
     */
    public void hideProxiesFromSpectator(Player player) {
        if (player == null || !player.isOnline()) return;
        World world = player.getWorld();
        if (world == null) return;

        List<Integer> ids = new ArrayList<>();
        for (Entity e : world.getEntitiesByClass(ArmorStand.class)) {
            if (e.hasMetadata(METADATA_KEY)) {
                ids.add(e.getEntityId());
            }
        }

        if (!ids.isEmpty()) {
            int[] idArray = new int[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                idArray[i] = ids.get(i);
            }
            try {
                PacketContainer destroy = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroy.getIntegerArrays().write(0, idArray);
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, destroy);
            } catch (Exception ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                hideProxiesFromSpectator(event.getPlayer());
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || plugin.getPlayerManager().isSpectator(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                hideProxiesFromSpectator(player);
            }, 2L);
        }
    }

    /**
     * 为指定的箱子方块创建高精度内嵌微型代理网格
     */
    public void spawnProxyForChest(Block block) {
        if (block == null || block.getWorld() == null) return;

        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.ENDER_CHEST) {
            return;
        }

        // 处理大箱子（双箱子）：分别在左半边和右半边两个方块各布设一套高精度内嵌代理
        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            Inventory inv = chest.getInventory();
            if (inv instanceof DoubleChestInventory) {
                DoubleChest holder = ((DoubleChestInventory) inv).getHolder();
                if (holder != null) {
                    if (holder.getLeftSide() instanceof Chest) {
                        spawnPreciseProxiesForBlock(((Chest) holder.getLeftSide()).getBlock());
                    }
                    if (holder.getRightSide() instanceof Chest) {
                        spawnPreciseProxiesForBlock(((Chest) holder.getRightSide()).getBlock());
                    }
                    return;
                }
            }
        }

        spawnPreciseProxiesForBlock(block);
    }

    /**
     * 为单个箱子方块布设 5 点紧密内嵌微型 ArmorStand（内聚坐标，上方与四周 0 溢出）
     */
    private void spawnPreciseProxiesForBlock(Block block) {
        if (block == null || block.getWorld() == null) return;

        // 强力清理历史遗留的旧版实体与周边残留
        for (Entity nearby : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 1.5, 2.5, 1.5)) {
            if (nearby.hasMetadata(METADATA_KEY) || (nearby instanceof Slime) || (nearby instanceof ArmorStand && !((ArmorStand) nearby).isVisible())) {
                nearby.remove();
            }
        }

        // 高精度内聚坐标偏移（下沉至 y-0.35，实体顶端仅达 y+0.64，完全收敛在箱子 0.875 箱盖之下；X/Z 严格收敛在 0.22~0.78 内部）
        double[][] offsets = {
                {0.35, -0.35, 0.35},
                {0.65, -0.35, 0.35},
                {0.35, -0.35, 0.65},
                {0.65, -0.35, 0.65},
                {0.50, -0.35, 0.50}
        };

        for (double[] off : offsets) {
            Location standLoc = block.getLocation().add(off[0], off[1], off[2]);
            spawnSingleArmorStand(standLoc);
        }
    }

    private void spawnSingleArmorStand(Location loc) {
        try {
            ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCanPickupItems(false);
            stand.setCustomNameVisible(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(true); // 微型实体：高度 0.98 格
            stand.setMarker(false); // 允许准心交互
            stand.setRemoveWhenFarAway(false);
            stand.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, true));
        } catch (Exception e) {
            plugin.getLogger().warning("[ChestProxyManager] 生成箱子交互代理失败: " + e.getMessage());
        }
    }

    /**
     * 扫描指定世界的所有加载区块并为箱子挂载交互代理（先全局清理旧实体）
     */
    public void scanAndRegisterWorldChests(World world) {
        if (world == null) return;
        clearAllProxies(world);

        int count = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Chest || state.getType() == Material.ENDER_CHEST) {
                    spawnProxyForChest(state.getBlock());
                    count++;
                }
            }
        }
        plugin.getLogger().info("[ChestProxyManager] 世界 " + world.getName() + " 成功挂载 " + count + " 个箱子高精度交互代理！");
    }

    /**
     * 清理指定世界中的所有代理实体
     */
    public void clearAllProxies(World world) {
        if (world == null) return;
        for (Entity e : world.getEntities()) {
            if (e.hasMetadata(METADATA_KEY) || (e instanceof Slime) || (e instanceof ArmorStand && !((ArmorStand) e).isVisible() && ((ArmorStand) e).getCustomName() == null)) {
                e.remove();
            }
        }
        openChestMap.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityInteract(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleEntityInteract(Player player, Entity entity, org.bukkit.event.Cancellable event) {
        if (entity == null || !entity.hasMetadata(METADATA_KEY)) return;

        // 拦截实体默认交互
        event.setCancelled(true);

        if (!player.isOnline()) return;
        if (plugin.getPlayerManager().isSpectator(player) || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT && !player.isOp()) {
            return;
        }

        Location loc = entity.getLocation();
        Block block = loc.getBlock();

        // 寻找实体所在方块或周围相邻的箱子
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST && block.getType() != Material.ENDER_CHEST) {
            boolean found = false;
            for (int ox = -1; ox <= 1 && !found; ox++) {
                for (int oy = -1; oy <= 1 && !found; oy++) {
                    for (int oz = -1; oz <= 1 && !found; oz++) {
                        Block candidate = loc.clone().add(ox, oy, oz).getBlock();
                        if (candidate.getType() == Material.CHEST || candidate.getType() == Material.TRAPPED_CHEST || candidate.getType() == Material.ENDER_CHEST) {
                            block = candidate;
                            found = true;
                        }
                    }
                }
            }
        }

        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
            if (block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
                Inventory inv = chest.getInventory();
                Location chestLoc = chest.getLocation();

                if (inv instanceof DoubleChestInventory) {
                    DoubleChest holder = ((DoubleChestInventory) inv).getHolder();
                    if (holder != null) {
                        chestLoc = holder.getLocation();
                    }
                }

                // 首次开启普通物资箱：填充物资并生成四周“已被打开”告示牌
                if (type != Material.TRAPPED_CHEST && !plugin.getLootManager().isChestOpened(chestLoc)) {
                    plugin.getLootManager().populateChest(inv);
                    plugin.getLootManager().markChestOpened(chestLoc);
                    placeSignsAroundChest(block, inv);
                }

                player.openInventory(inv);
                player.playSound(loc, Sound.CHEST_OPEN, 0.5F, 1.0F);

                // 触发箱子物理开盖动画
                playChestAnimation(block, inv, true);
                openChestMap.put(player.getUniqueId(), block);
            }
        } else if (type == Material.ENDER_CHEST) {
            player.openInventory(player.getEnderChest());
            player.playSound(loc, Sound.CHEST_OPEN, 0.5F, 1.0F);
            playChestAnimation(block, null, true);
            openChestMap.put(player.getUniqueId(), block);
        }
    }

    /**
     * 严防实体造成伤害或受到伤害
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().hasMetadata(METADATA_KEY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager().hasMetadata(METADATA_KEY) || event.getEntity().hasMetadata(METADATA_KEY)) {
            event.setCancelled(true);
        }
    }

    /**
     * 监听背包关闭事件：播放箱子关盖动画与关箱音效
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Block block = openChestMap.remove(player.getUniqueId());
        if (block != null && block.getWorld() != null) {
            playChestAnimation(block, event.getInventory(), false);
            player.playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.CHEST_CLOSE, 0.5F, 1.0F);
        }
    }

    /**
     * 通过 ProtocolLib 发送物理方块动作包，触发箱子开盖 / 关盖 3D 旋转动画
     */
    public void playChestAnimation(Block block, Inventory inv, boolean open) {
        if (block == null || block.getWorld() == null) return;
        try {
            if (inv instanceof DoubleChestInventory) {
                DoubleChest holder = ((DoubleChestInventory) inv).getHolder();
                if (holder != null) {
                    if (holder.getLeftSide() instanceof Chest) {
                        sendBlockAction(((Chest) holder.getLeftSide()).getBlock(), open);
                    }
                    if (holder.getRightSide() instanceof Chest) {
                        sendBlockAction(((Chest) holder.getRightSide()).getBlock(), open);
                    }
                    return;
                }
            }
            sendBlockAction(block, open);
        } catch (Throwable ignored) {}
    }

    private void sendBlockAction(Block block, boolean open) {
        try {
            Location loc = block.getLocation();
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_ACTION);
            packet.getBlockPositionModifier().write(0, new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            packet.getIntegers().write(0, 1);
            packet.getIntegers().write(1, open ? 1 : 0);
            packet.getBlocks().write(0, block.getType());
            ProtocolLibrary.getProtocolManager().broadcastServerPacket(packet, loc, 64);
        } catch (Throwable ignored) {}
    }

    /**
     * 在箱子四周放置“已被打开”告示牌
     */
    public void placeSignsAroundChest(Block block, Inventory inv) {
        org.bukkit.block.BlockFace[] faces = { org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.EAST };
        if (inv instanceof DoubleChestInventory) {
            DoubleChest holder = ((DoubleChestInventory) inv).getHolder();
            if (holder != null) {
                if (holder.getLeftSide() instanceof Chest) {
                    Block left = ((Chest) holder.getLeftSide()).getBlock();
                    for (org.bukkit.block.BlockFace face : faces) {
                        placeOpenedChestSign(left.getRelative(face), face);
                    }
                }
                if (holder.getRightSide() instanceof Chest) {
                    Block right = ((Chest) holder.getRightSide()).getBlock();
                    for (org.bukkit.block.BlockFace face : faces) {
                        placeOpenedChestSign(right.getRelative(face), face);
                    }
                }
                return;
            }
        }
        for (org.bukkit.block.BlockFace face : faces) {
            placeOpenedChestSign(block.getRelative(face), face);
        }
    }

    private void placeOpenedChestSign(Block block, org.bukkit.block.BlockFace face) {
        if (block.getType() == Material.AIR || block.getType() == Material.WATER
                || block.getType() == Material.STATIONARY_WATER
                || block.getType() == Material.LONG_GRASS || block.getType() == Material.SNOW
                || block.getType() == Material.DEAD_BUSH
                || block.getType() == Material.YELLOW_FLOWER || block.getType() == Material.RED_ROSE) {
            block.setType(Material.WALL_SIGN);
            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof org.bukkit.block.Sign) {
                org.bukkit.block.Sign sign = (org.bukkit.block.Sign) state;
                org.bukkit.material.Sign signData = (org.bukkit.material.Sign) sign.getData();
                signData.setFacingDirection(face);
                sign.setData(signData);
                sign.setLine(0, "§e[系统]");
                sign.setLine(1, "§f该箱子");
                sign.setLine(2, "§c已被打开");
                sign.update(true, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals("game_1")) return;

        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state instanceof Chest || state.getType() == Material.ENDER_CHEST) {
                spawnProxyForChest(state.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
            spawnProxyForChest(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            for (Entity e : block.getWorld().getNearbyEntities(loc, 1.5, 2.5, 1.5)) {
                if (e.hasMetadata(METADATA_KEY)) {
                    e.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            Material type = b.getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
                Location loc = b.getLocation().add(0.5, 0.5, 0.5);
                for (Entity e : b.getWorld().getNearbyEntities(loc, 1.5, 2.5, 1.5)) {
                    if (e.hasMetadata(METADATA_KEY)) {
                        e.remove();
                    }
                }
            }
        }
    }
}
