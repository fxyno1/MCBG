package edu.mc.map;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import edu.mc.manager.ZoneManager;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;

import org.bukkit.map.MapView;
import org.bukkit.map.MapRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public class PacketMapManager {

    private final ChickenDinnerPlugin plugin;
    private static final int MAP_SIZE = 128;

    private final Map<UUID, Integer> playerMapIds = new ConcurrentHashMap<>();
    private byte[] cachedTerrainBytes;
    
    // 【修改】核心修复：原版客户端不允许物品耐久度为负数，会自动归零！
    // 因此我们必须使用正数。因为原版地图 ID 都是从 0 慢慢递增的，
    // 所以只要我们使用一个远超正常游玩范围的正数（10000 ~ 30000 之间），就能 100% 避免缓存冲突且安全通过客户端检查。
    private final int virtualMapOffset = 10000 + new Random().nextInt(20000);

    // 预匹配缓存颜色字节
    private byte colorBorder;
    private byte colorTarget;
    private byte colorFlight;
    private byte colorAirdrop;
    private byte colorPlayer;
    private byte colorGround;
    private byte colorGrid;
    private byte colorIsland;

    // 【修改】添加自定义地图渲染器，绕过并覆盖 NMS 原生地图探索渲染，使玩家无论走到哪里都能直接看到整张完整的自定义战术雷达地图
    private static class GPSMapRenderer extends org.bukkit.map.MapRenderer {
        private final PacketMapManager manager;

        public GPSMapRenderer(PacketMapManager manager) {
            super(true); // contextual = true，使每个玩家独立渲染各自的指针和状态
            this.manager = manager;
        }

        @Override
        public void render(org.bukkit.map.MapView map, org.bukkit.map.MapCanvas canvas, Player player) {
            // 渲染当前玩家的帧画面
            byte[] pixels = manager.renderFrame(player);
            for (int x = 0; x < MAP_SIZE; x++) {
                for (int y = 0; y < MAP_SIZE; y++) {
                    canvas.setPixel(x, y, pixels[x + y * MAP_SIZE]);
                }
            }
        }
    }

    private org.bukkit.scheduler.BukkitTask updateTask;

    public PacketMapManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        initColors();
        loadTerrain();
        // 【修改】注册 ProtocolLib 拦截器，重写所有发送给客户端的 GPS 地图数据包，消除 vanilla 地形渲染
        registerPacketListener();
        // 【修改】启动后台定时任务，定期强刷雷达数据，实现平滑更新
        startUpdateTask();
    }

    private void initColors() {
        colorBorder = org.bukkit.map.MapPalette.matchColor(0, 80, 220);
        colorTarget = org.bukkit.map.MapPalette.matchColor(240, 240, 240);
        colorFlight = org.bukkit.map.MapPalette.matchColor(255, 0, 0);
        colorAirdrop = org.bukkit.map.MapPalette.matchColor(0, 255, 255);
        colorPlayer = org.bukkit.map.MapPalette.matchColor(255, 255, 0);
        colorGround = org.bukkit.map.MapPalette.matchColor(100, 160, 90);
        colorGrid = org.bukkit.map.MapPalette.matchColor(50, 50, 50);
        colorIsland = org.bukkit.map.MapPalette.matchColor(0, 100, 0);
    }

    private void loadTerrain() {
        BufferedImage terrainImage = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = terrainImage.createGraphics();

        // 尝试加载自定义的地图缩略图作为背景
        boolean loadedCustom = false;
        try {
            // 优先检查插件配置文件夹 plugins/ChickenDinner/map.png
            java.io.File imgFile = new java.io.File(plugin.getDataFolder(), "map.png");
            if (!imgFile.exists()) {
                // 备选世界存档目录下的 map.png
                org.bukkit.World mainWorld = Bukkit.getWorld("game_1");
                if (mainWorld == null) mainWorld = Bukkit.getWorlds().get(0);
                imgFile = new java.io.File(mainWorld.getWorldFolder(), "map.png");
            }

            if (imgFile.exists()) {
                BufferedImage customImg = javax.imageio.ImageIO.read(imgFile);
                if (customImg != null) {
                    // 将自定义图片缩放到 128x128 并绘制到背景上
                    g.drawImage(customImg, 0, 0, MAP_SIZE, MAP_SIZE, null);
                    loadedCustom = true;
                    Bukkit.getLogger().info("[PacketMap] 成功从 " + imgFile.getAbsolutePath() + " 加载自定义地图背景图片！");
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PacketMap] 加载自定义地图背景图片失败: " + e.getMessage());
        }

        // 如果没有成功加载自定义图片，则使用默认的绿色草地背景和网格线
        if (!loadedCustom) {
            // 绘制绿色草地背景
            g.setColor(new Color(100, 160, 90));
            g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);

            // 绘制网格线
            drawGridLines(g, terrainImage);

            // 绘制岛屿区域
            drawIslandArea(g, terrainImage);
        }

        g.dispose();

        // 一次性预先转换静态底图的全部像素，缓存为 Minecraft 地图颜色字节
        cachedTerrainBytes = new byte[MAP_SIZE * MAP_SIZE];
        for (int i = 0; i < MAP_SIZE * MAP_SIZE; i++) {
            int rgb = terrainImage.getRGB(i % MAP_SIZE, i / MAP_SIZE);
            int r = (rgb >> 16) & 0xFF;
            int g8 = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            cachedTerrainBytes[i] = org.bukkit.map.MapPalette.matchColor(r, g8, b);
        }

        Bukkit.getLogger().info("[PacketMap] 成功载入高清战术地图底图并预缓存颜色！");
    }

    public void reload() {
        initColors();
        loadTerrain();
    }

    // 动态计算并构建包含玩家当前位置与朝向的 MapIcon 数组 (原版三角指针)
    private Object getPlayerIconArray(Player player) {
        try {
            Class<?> mapIconClass = com.comphenix.protocol.utility.MinecraftReflection.getMinecraftClass("MapIcon");
            java.lang.reflect.Constructor<?> constr = mapIconClass.getConstructor(byte.class, byte.class, byte.class,
                    byte.class);

            List<Object> icons = new ArrayList<>();

            // 添加玩家自己
            Object selfIcon = createMapIcon(constr, player, (byte) 0);
            if (selfIcon != null) {
                icons.add(selfIcon);
            }

            // 队友和所有其他存活玩家不再使用原生指针，改为在 renderFrame 中直接手绘队伍颜色的像素方块！

            Object array = java.lang.reflect.Array.newInstance(mapIconClass, icons.size());
            for (int i = 0; i < icons.size(); i++) {
                java.lang.reflect.Array.set(array, i, icons.get(i));
            }
            return array;
        } catch (Exception e) {
            try {
                Class<?> mapIconClass = com.comphenix.protocol.utility.MinecraftReflection.getMinecraftClass("MapIcon");
                return java.lang.reflect.Array.newInstance(mapIconClass, 0);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private Object createMapIcon(java.lang.reflect.Constructor<?> constr, Player p, byte type) {
        try {
            int px = worldToPixelX(p.getLocation().getX());
            int pz = worldToPixelZ(p.getLocation().getZ());

            int cx = (px - 64) * 2;
            int cz = (pz - 64) * 2;

            byte cursorX = (byte) Math.max(-128, Math.min(127, cx));
            byte cursorZ = (byte) Math.max(-128, Math.min(127, cz));

            float yaw = p.getLocation().getYaw();
            int dir = (int) Math.round((yaw) * 16.0 / 360.0);
            byte cursorDir = (byte) (dir & 15);

            return constr.newInstance(type, cursorX, cursorZ, cursorDir);
        } catch (Exception e) {
            return null;
        }
    }

    private void drawGridLines(Graphics2D g, BufferedImage image) {
        g.setColor(new Color(50, 50, 50));
        int gridColorRgb = g.getColor().getRGB();
        for (int wx = -GameConfig.MAP_GRID_RANGE; wx <= GameConfig.MAP_GRID_RANGE; wx += GameConfig.MAP_GRID_INTERVAL) {
            int cx = worldToPixelX(wx);
            if (cx >= 0 && cx < MAP_SIZE) {
                for (int cy = 0; cy < MAP_SIZE; cy++) {
                    if (cy % 2 == 0) {
                        image.setRGB(cx, cy, gridColorRgb);
                    }
                }
            }
        }
        for (int wz = -GameConfig.MAP_GRID_RANGE; wz <= GameConfig.MAP_GRID_RANGE; wz += GameConfig.MAP_GRID_INTERVAL) {
            int cy = worldToPixelZ(wz + GameConfig.MAP_CENTER_Z);
            if (cy >= 0 && cy < MAP_SIZE) {
                for (int cx = 0; cx < MAP_SIZE; cx++) {
                    if (cx % 2 == 0) {
                        image.setRGB(cx, cy, gridColorRgb);
                    }
                }
            }
        }
    }

    private void drawIslandArea(Graphics2D g, BufferedImage image) {
        g.setColor(new Color(0, 100, 0));
        int ix1 = worldToPixelX(GameConfig.ISLAND_X1);
        int ix2 = worldToPixelX(GameConfig.ISLAND_X2);
        int iz1 = worldToPixelZ(GameConfig.ISLAND_Z1);
        int iz2 = worldToPixelZ(GameConfig.ISLAND_Z2);
        g.drawRect(Math.min(ix1, ix2), Math.min(iz1, iz2),
                Math.abs(ix2 - ix1), Math.abs(iz2 - iz1));
    }

    public void giveMap(Player player) {
        UUID pid = player.getUniqueId();
        // 【修复】为 mapId 初始化默认值，防止编译器误判“可能未初始化”而报错
        int mapId = 0;
        MapView view = null;

        // 如果玩家已有注册的地图，尝试获取原有的 MapView 进行复用
        if (playerMapIds.containsKey(pid)) {
            mapId = playerMapIds.get(pid);
            view = Bukkit.getMap((short) mapId);
        }

        // 如果没有已注册的地图或 MapView 丢失，则创建新的 MapView
        if (view == null) {
            World world = player.getWorld();
            view = Bukkit.createMap(world);
            mapId = view.getId();
            playerMapIds.put(pid, mapId);
        }

        // 强制配置地图 of 中心点到游玩区域，并设置正确的缩放比例，这样客户端绘制玩家指标时会处于正确的位置
        view.setCenterX((int) GameConfig.MAP_CENTER_X);
        view.setCenterZ((int) GameConfig.MAP_CENTER_Z);
        view.setScale(MapView.Scale.NORMAL);

        // 【修改】彻底清除原版的地图渲染器（包括原生地形扫描器），防止 vanilla 逻辑继续在周围探索时修改地图像素
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(r);
        }

        // 【修改】加入我们自定义的战术雷达渲染器，直接全屏覆盖像素，摆脱原版探索限制
        view.addRenderer(new GPSMapRenderer(this));

        // 创建地图物品
        org.bukkit.inventory.ItemStack mapItem = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.MAP, 1, (short) mapId);
        org.bukkit.inventory.meta.ItemMeta meta = mapItem.getItemMeta();
        meta.setDisplayName("§a§l[战术 GPS 雷达]");
        meta.setLore(Arrays.asList(
                "§7拿在手上即可生效",
                "§7- §c飞行航线（红色虚线）",
                "§7- §f下级安全圈范围（白色虚线）",
                "§7- §9实时毒圈边缘（蓝色实线）",
                "§7- §a你的实时坐标与视角朝向",
                "§7- §2队友实时位置（绿色指针）"));
        mapItem.setItemMeta(meta);

        int existingSlot = -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == org.bukkit.Material.MAP) {
                existingSlot = i;
                break;
            }
        }

        if (existingSlot != -1) {
            player.getInventory().setItem(existingSlot, mapItem);
        } else {
            if (player.getInventory().getItem(4) == null) {
                player.getInventory().setItem(4, mapItem);
            } else {
                player.getInventory().addItem(mapItem);
            }
        }
        player.updateInventory();

        // 【修改】由于我们在发包层面欺骗了客户端（加入了 virtualMapOffset），
        // 客户端永远不会去加载它本地磁盘里的旧缓存，只会显示一张没有任何残影的新地图。
        // 所以我们只需要发一次更新包即可瞬间覆盖，无需再用高频狂刷了！
        final int finalMapId = mapId;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    byte[] pixels = renderFrame(player);
                    sendMapPacket(player, finalMapId, pixels);
                }
            }
        }, 5L);
    }

    public void removeMap(Player player) {
        UUID pid = player.getUniqueId();
        playerMapIds.remove(pid);
    }

    public void clearAll() {
        playerMapIds.clear();
        Bukkit.getLogger().info("[PacketMap] 已清空所有地图缓存");
    }

    // 【修改】提供清空并彻底删除服务器 world/data/map_*.dat 和 id-counts.dat
    // 文件的功能，保证重新生成地图时不读取残留缓存，从 0 开始重新编号
    public void clearAndResetMapFiles() {
        clearAll();
        try {
            org.bukkit.World mainWorld = Bukkit.getWorld("game_1");
            if (mainWorld == null) mainWorld = Bukkit.getWorlds().get(0);
            java.io.File dataDir = new java.io.File(mainWorld.getWorldFolder(), "data");
            if (dataDir.exists() && dataDir.isDirectory()) {
                java.io.File[] files = dataDir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        if ((f.getName().startsWith("map_") && f.getName().endsWith(".dat"))
                                || f.getName().equals("id-counts.dat")) {
                            f.delete();
                        }
                    }
                }
            }
            Bukkit.getLogger().info("[PacketMap] 已彻底删除服务器上的全部 map_*.dat 缓存及 id-counts.dat 计数器");
        } catch (Exception e) {
            // 静默处理，防止报错
        }
    }

    // 【修改】注册 ProtocolLib 拦截器，重写所有发送给客户端的物品栏数据和地图数据，实现缓存欺骗
    private void registerPacketListener() {
        try {
            com.comphenix.protocol.ProtocolManager pm = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
            
            // 1. 拦截 MAP 数据包
            pm.addPacketListener(new com.comphenix.protocol.events.PacketAdapter(
                    plugin, com.comphenix.protocol.events.ListenerPriority.HIGHEST,
                    com.comphenix.protocol.PacketType.Play.Server.MAP) {
                @Override
                public void onPacketSending(com.comphenix.protocol.events.PacketEvent event) {
                    PacketContainer packet = event.getPacket();
                    int originalMapId = packet.getIntegers().read(0);
                    // 检查原始 ID 或者是加上偏移量后的虚拟 ID（因为 sendMapPacket 里我们主动加了偏移量）
                    if (isGPSMap(originalMapId) || isGPSMap(originalMapId - virtualMapOffset)) {
                        Player player = event.getPlayer();
                        if (player != null) {
                            byte[] pixels = renderFrame(player);
                            // 强制修改为 128x128 像素的完整雷达地图更新
                            packet.getIntegers().write(1, 0); // minX
                            packet.getIntegers().write(2, 0); // minZ
                            packet.getIntegers().write(3, 128); // width
                            packet.getIntegers().write(4, 128); // height
                            packet.getByteArrays().write(0, pixels);

                            Object array = getPlayerIconArray(player);
                            if (array != null) {
                                ((com.comphenix.protocol.reflect.StructureModifier) packet
                                        .getSpecificModifier(array.getClass())).write(0, array);
                            }
                        }
                    }
                }
            });

            // 2. 拦截物品栏单个物品更新包 (SET_SLOT)
            pm.addPacketListener(new com.comphenix.protocol.events.PacketAdapter(
                    plugin, com.comphenix.protocol.events.ListenerPriority.HIGHEST,
                    com.comphenix.protocol.PacketType.Play.Server.SET_SLOT) {
                @Override
                public void onPacketSending(com.comphenix.protocol.events.PacketEvent event) {
                    PacketContainer packet = event.getPacket();
                    org.bukkit.inventory.ItemStack item = packet.getItemModifier().read(0);
                    if (item != null && item.getType() == org.bukkit.Material.MAP && isGPSMap(item.getDurability())) {
                        org.bukkit.inventory.ItemStack cloned = item.clone();
                        cloned.setDurability((short) (item.getDurability() + virtualMapOffset));
                        packet.getItemModifier().write(0, cloned);
                    }
                }
            });

            // 3. 拦截物品栏全量更新包 (WINDOW_ITEMS)
            pm.addPacketListener(new com.comphenix.protocol.events.PacketAdapter(
                    plugin, com.comphenix.protocol.events.ListenerPriority.HIGHEST,
                    com.comphenix.protocol.PacketType.Play.Server.WINDOW_ITEMS) {
                @Override
                public void onPacketSending(com.comphenix.protocol.events.PacketEvent event) {
                    PacketContainer packet = event.getPacket();
                    org.bukkit.inventory.ItemStack[] items = packet.getItemArrayModifier().read(0);
                    boolean changed = false;
                    for (int i = 0; i < items.length; i++) {
                        org.bukkit.inventory.ItemStack item = items[i];
                        if (item != null && item.getType() == org.bukkit.Material.MAP && isGPSMap(item.getDurability())) {
                            org.bukkit.inventory.ItemStack cloned = item.clone();
                            cloned.setDurability((short) (item.getDurability() + virtualMapOffset));
                            items[i] = cloned;
                            changed = true;
                        }
                    }
                    if (changed) {
                        packet.getItemArrayModifier().write(0, items);
                    }
                }
            });

            Bukkit.getLogger().info("[PacketMap] 成功注册 ProtocolLib 战术雷达缓存欺骗及发包拦截器！");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[PacketMap] 注册 ProtocolLib 拦截器发生异常: " + e.getMessage());
        }
    }

    // 【修改】提供方法判断某个地图 ID 是否为我们插件分配的战术雷达地图
    public boolean isGPSMap(int mapId) {
        return playerMapIds.containsValue(mapId);
    }

    // 【修改】启动周期性地图强刷任务，使得毒圈移动和飞行轨迹平滑刷新，即使玩家原地站立不动
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID pid = player.getUniqueId();
                    if (playerMapIds.containsKey(pid)) {
                        int mapId = playerMapIds.get(pid);
                        org.bukkit.inventory.ItemStack hand = player.getItemInHand();
                        // 仅当玩家手持该雷达地图时，才主动发送完整的渲染帧，节省网络带宽
                        if (hand != null && hand.getType() == org.bukkit.Material.MAP
                                && hand.getDurability() == mapId) {
                            byte[] pixels = renderFrame(player);
                            sendMapPacket(player, mapId, pixels);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // 每 10 ticks (0.5秒) 强制更新一次
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        clearAll();
    }

    private byte[] renderFrame(Player player) {
        // 创建帧像素缓冲区，并直接复制预缓存的地形底图
        byte[] frame = new byte[MAP_SIZE * MAP_SIZE];
        System.arraycopy(cachedTerrainBytes, 0, frame, 0, MAP_SIZE * MAP_SIZE);

        GameState state = plugin.getCurrentState();
        World world = player.getWorld();

        // 绘制飞行航线
        if (state == GameState.FLIGHT || state == GameState.STARTING || state == GameState.INGAME) {
            Location start = plugin.getFlightManager().getStartPoint();
            Location end = plugin.getFlightManager().getEndPoint();
            if (start != null && end != null) {
                int sx = worldToPixelX(start.getX());
                int sz = worldToPixelZ(start.getZ());
                int ex = worldToPixelX(end.getX());
                int ez = worldToPixelZ(end.getZ());
                drawLineOnBytes(frame, sx, sz, ex, ez, colorFlight, true);
            }
        }

        // 绘制毒圈和安全区
        ZoneManager zm = plugin.getZoneManager();
        if (zm != null && state != GameState.LOBBY) {
            org.bukkit.WorldBorder border = world.getWorldBorder();
            double bx = border.getCenter().getX();
            double bz = border.getCenter().getZ();
            double bs = border.getSize();

            // 绘制当前毒圈（蓝色）
            drawRectOnBytes(frame,
                    worldToPixelX(bx - bs / 2),
                    worldToPixelZ(bz - bs / 2),
                    worldToPixelX(bx + bs / 2),
                    worldToPixelZ(bz + bs / 2),
                    colorBorder);

            // 绘制下一个安全区（白色虚线框）
            if (zm.getCurrentPhase() < 5) {
                drawRectOnBytes(frame,
                        worldToPixelX(zm.getTargetX() - zm.getTargetSize() / 2),
                        worldToPixelZ(zm.getTargetZ() - zm.getTargetSize() / 2),
                        worldToPixelX(zm.getTargetX() + zm.getTargetSize() / 2),
                        worldToPixelZ(zm.getTargetZ() + zm.getTargetSize() / 2),
                        colorTarget);
            }
        }

        // 绘制空投位置
        if (state == GameState.INGAME || state == GameState.FLIGHT) {
            for (Location drop : plugin.getAirdropManager().getActiveAirdrops()) {
                int dx = worldToPixelX(drop.getX());
                int dz = worldToPixelZ(drop.getZ());
                for (int r = -1; r <= 1; r++) {
                    setPixel(frame, dx + r, dz, colorAirdrop);
                    setPixel(frame, dx, dz + r, colorAirdrop);
                }
            }
        }

        // 绘制彩色队友/全场存活玩家标识
        if (state == GameState.INGAME || state == GameState.FLIGHT || state == GameState.STARTING || state == GameState.ENDING) {
            boolean isSpectator = plugin.getPlayerManager().isSpectator(player);
            Integer myTeam = plugin.getTeamManager().getTeam(player.getUniqueId());

            for (UUID aliveId : plugin.getPlayerManager().getAlivePlayers()) {
                Player alive = Bukkit.getPlayer(aliveId);
                if (alive != null && alive.isOnline() && alive.getWorld().equals(world)) {
                    if (alive.getUniqueId().equals(player.getUniqueId())) continue;

                    boolean shouldDraw = false;
                    if (isSpectator) {
                        shouldDraw = true;
                    } else if (myTeam != null) {
                        Integer aliveTeam = plugin.getTeamManager().getTeam(aliveId);
                        if (myTeam.equals(aliveTeam)) {
                            shouldDraw = true;
                        }
                    }

                    if (shouldDraw) {
                        int px = worldToPixelX(alive.getLocation().getX());
                        int pz = worldToPixelZ(alive.getLocation().getZ());
                        
                        byte iconColor = colorPlayer; // 默认黄色
                        Integer teamId = plugin.getTeamManager().getTeam(aliveId);
                        if (teamId != null) {
                            edu.mc.manager.TeamManager.TeamInfo info = plugin.getTeamManager().getTeamInfo(teamId);
                            if (info != null && info.armorColor != null) {
                                org.bukkit.Color tc = info.armorColor;
                                iconColor = org.bukkit.map.MapPalette.matchColor(tc.getRed(), tc.getGreen(), tc.getBlue());
                            }
                        }

                        // 绘制 3x3 方块，更加醒目
                        for (int r = -1; r <= 1; r++) {
                            for (int c = -1; c <= 1; c++) {
                                setPixel(frame, px + r, pz + c, iconColor);
                            }
                        }
                    }
                }
            }
        }

        return frame;
    }

    private void sendMapPacket(Player player, int mapId, byte[] pixels) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.MAP);

            // 【修改】这里发包给客户端时，强制附加上偏移量！
            packet.getIntegers().write(0, mapId + virtualMapOffset); // 地图虚拟 ID
            packet.getBytes().write(0, (byte) 3); // 缩放比例
            // 写入玩家位置指针，避免包序列化时产生空指针异常
            Object array = getPlayerIconArray(player);
            if (array != null) {
                ((com.comphenix.protocol.reflect.StructureModifier) packet.getSpecificModifier(array.getClass()))
                        .write(0, array);
            }
            packet.getByteArrays().write(0, pixels); // 像素数据

            packet.getIntegers().write(1, 0); // minX
            packet.getIntegers().write(2, 0); // minZ
            packet.getIntegers().write(3, 128); // 宽度 (Columns)
            packet.getIntegers().write(4, 128); // 高度 (Rows)

            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            // 静默处理，防止刷屏
        }
    }

    private int worldToPixelX(double wx) {
        return 64 + (int) Math.round((wx - GameConfig.MAP_CENTER_X) / GameConfig.MAP_SCALE);
    }

    private int worldToPixelZ(double wz) {
        return 64 + (int) Math.round((wz - GameConfig.MAP_CENTER_Z) / GameConfig.MAP_SCALE);
    }

    private void setPixel(byte[] f, int x, int y, byte c) {
        if (x >= 0 && x < MAP_SIZE && y >= 0 && y < MAP_SIZE) {
            f[x + y * MAP_SIZE] = c;
        }
    }

    private void drawRectOnBytes(byte[] f, int x1, int y1, int x2, int y2, byte c) {
        drawLineOnBytes(f, x1, y1, x2, y1, c, false);
        drawLineOnBytes(f, x1, y2, x2, y2, c, false);
        drawLineOnBytes(f, x1, y1, x1, y2, c, false);
        drawLineOnBytes(f, x2, y1, x2, y2, c, false);
    }

    private void drawLineOnBytes(byte[] f, int x1, int y1, int x2, int y2, byte c, boolean dashed) {
        x1 = clamp(x1);
        y1 = clamp(y1);
        x2 = clamp(x2);
        y2 = clamp(y2);

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int cnt = 0;

        while (true) {
            if (!dashed || cnt % 2 == 0) {
                setPixel(f, x1, y1, c);
            }
            cnt++;
            if (x1 == x2 && y1 == y2)
                break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(MAP_SIZE - 1, v));
    }
}
