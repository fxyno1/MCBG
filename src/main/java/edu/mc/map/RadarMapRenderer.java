package edu.mc.map;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class RadarMapRenderer extends MapRenderer {

    // 所有颜色常量提升为 static final：matchColor() 内部需遍历 128 个调色板条目做颜色距离运算，
    // 每次 render() 调用 6 次将带来不必要的 CPU 开销。提升为类级别常量后只在类加载时计算一次。
    private static final byte COLOR_GRID = MapPalette.matchColor(50, 50, 50);
    private static final byte COLOR_ISLAND = MapPalette.matchColor(0, 100, 0);
    private static final byte COLOR_BORDER = MapPalette.matchColor(0, 80, 220);
    private static final byte COLOR_TARGET = MapPalette.matchColor(240, 240, 240);
    private static final byte COLOR_PLAYER = MapPalette.matchColor(0, 255, 0);
    private static final byte COLOR_FLIGHT = MapPalette.matchColor(255, 0, 0);
    private static final byte COLOR_AIRDROP = MapPalette.matchColor(0, 255, 255);

    private final ChickenDinnerPlugin plugin;
    private final byte[] terrainCache;
    private final java.util.Set<java.util.UUID> terrainFlushed = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // 每个玩家上一帧的覆盖层像素索引（必须做到玩家独立，否则会出现严重残影）
    private final java.util.Map<java.util.UUID, java.util.Set<Integer>> dirtyPixels = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastRenderTime = new java.util.concurrent.ConcurrentHashMap<>();

    // 用于跟踪游戏状态的变化，以便在重开局时刷新地图
    private GameState lastState = null;

    // 跟踪是否已经将航线绘制到了地形缓存中
    private boolean flightLineRenderedInCache = false;

    /**
     * 清除指定玩家的渲染缓存状态。
     * 在玩家死亡、成为旁观者时调用，确保他们在新一局拿到地图时能立刻得到全量刷新。
     */
    public void resetPlayerState(java.util.UUID pid) {
        terrainFlushed.remove(pid);
        lastRenderTime.remove(pid);
        dirtyPixels.remove(pid);
    }

    /**
     * 主动从外部调用，彻底重置全局地形缓存，擦除航线等所有上局痕迹
     */
    public void resetGlobalCache() {
        synchronized (terrainCache) {
            terrainFlushed.clear();
            dirtyPixels.clear();
            flightLineRenderedInCache = false;

            // 从全局重置缓存以擦除上局残留
            byte[] globalCache = plugin.getTerrainCache();
            if (globalCache != null && terrainCache != null) {
                System.arraycopy(globalCache, 0, terrainCache, 0, 16384);
            }
            preRenderStaticElements();
        }
    }

    public RadarMapRenderer(ChickenDinnerPlugin plugin, byte[] terrainCache) {
        super(true);
        this.plugin = plugin;

        // 深度拷贝一份 terrainCache，将战术网格虚线和大逃杀主岛轮廓线作为静态底图一次性绘制上去
        // 这样可以彻底免除后续帧的静态划线计算，以及恢复背景像素时造成的划线破损
        this.terrainCache = new byte[16384];
        if (terrainCache != null) {
            System.arraycopy(terrainCache, 0, this.terrainCache, 0, 16384);
        }

        preRenderStaticElements();
    }

    /**
     * 预渲染静态元素（网格虚线、岛屿边线），将其写入底图缓存
     */
    private void preRenderStaticElements() {
        // 1. 绘制战术网格虚线
        for (int wx = -GameConfig.MAP_GRID_RANGE; wx <= GameConfig.MAP_GRID_RANGE; wx += GameConfig.MAP_GRID_INTERVAL) {
            int cx = worldToCanvasX(wx);
            if (cx >= 0 && cx < 128) {
                for (int cy = 0; cy < 128; cy++) {
                    if (cy % 2 == 0) {
                        setCachePixel(cx, cy, COLOR_GRID);
                    }
                }
            }
        }
        for (int wz = -GameConfig.MAP_GRID_RANGE; wz <= GameConfig.MAP_GRID_RANGE; wz += GameConfig.MAP_GRID_INTERVAL) {
            int cy = worldToCanvasZ(wz + GameConfig.MAP_CENTER_Z);
            if (cy >= 0 && cy < 128) {
                for (int cx = 0; cx < 128; cx++) {
                    if (cx % 2 == 0) {
                        setCachePixel(cx, cy, COLOR_GRID);
                    }
                }
            }
        }

        // 2. 绘制整个大逃杀战斗主岛的轮廓线
        int ix1 = worldToCanvasX(GameConfig.ISLAND_X1), ix2 = worldToCanvasX(GameConfig.ISLAND_X2);
        int iz1 = worldToCanvasZ(GameConfig.ISLAND_Z1), iz2 = worldToCanvasZ(GameConfig.ISLAND_Z2);
        drawCacheRect(ix1, iz1, ix2, iz2, COLOR_ISLAND);
    }

    private void setCachePixel(int x, int y, byte color) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) {
            terrainCache[x + y * 128] = color;
        }
    }

    private void drawCacheRect(int x1, int y1, int x2, int y2, byte color) {
        drawCacheLine(x1, y1, x2, y1, color);
        drawCacheLine(x1, y2, x2, y2, color);
        drawCacheLine(x1, y1, x1, y2, color);
        drawCacheLine(x2, y1, x2, y2, color);
    }

    private void drawCacheLine(int x1, int y1, int x2, int y2, byte color) {
        drawCacheLine(x1, y1, x2, y2, color, false);
    }

    /**
     * Bresenham 直线缓存绘制器（支持虚线选项）
     */
    private void drawCacheLine(int x1, int y1, int x2, int y2, byte color, boolean dotted) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, count = 0;
        while (true) {
            if (!dotted || count % 2 == 0) {
                setCachePixel(x1, y1, color);
            }
            count++;
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

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (player == null || !player.isOnline())
            return;

        java.util.UUID pid = player.getUniqueId();
        GameState currentState = plugin.getCurrentState();

        // 记录状态供调试，不再依赖它来清理缓存（现在由 GameManager 显式调用 resetGlobalCache）
        lastState = currentState;

        // 游戏进入飞行/正式战斗阶段，且此时已生成航线，将其一次性烤入底图缓存中，防止在每帧里擦除重建
        if (!flightLineRenderedInCache && (currentState == GameState.FLIGHT || currentState == GameState.STARTING
                || currentState == GameState.INGAME)) {
            synchronized (terrainCache) {
                if (!flightLineRenderedInCache) {
                    Location start = plugin.getFlightManager().getStartPoint();
                    Location end = plugin.getFlightManager().getEndPoint();
                    if (start != null && end != null) {
                        int sx = worldToCanvasX(start.getX()), sz = worldToCanvasZ(start.getZ());
                        int ex = worldToCanvasX(end.getX()), ez = worldToCanvasZ(end.getZ());
                        drawCacheLine(sx, sz, ex, ez, COLOR_FLIGHT, true);
                        flightLineRenderedInCache = true;

                        // 瞬间重置所有玩家的 flushed 标记，让所有人立刻加载包含航线的新底图
                        terrainFlushed.clear();
                    }
                }
            }
        }

        // 【最核心性能优化】如果在等待时期（LOBBY），且该玩家已经初始化过一次静态地图，直接无消耗跳过像素重绘
        // 彻底杜绝等待阶段频繁的网络地图包推送与 CPU 遍历运算，解决玩家在等待期间的严重卡顿！
        if (currentState == GameState.LOBBY && terrainFlushed.contains(pid)) {
            updatePlayerCursor(canvas, player);
            return;
        }

        // 关键性能优化：非等待阶段限制更新频率（每秒 4 次 / 250毫秒一次）
        long now = System.currentTimeMillis();
        long last = lastRenderTime.getOrDefault(pid, 0L);
        if (now - last < GameConfig.MAP_RENDER_THROTTLE_MS) {
            return;
        }
        lastRenderTime.put(pid, now);

        // 1. 地形底图与静态网格：每个玩家只全量写入一次，后续帧跳过
        java.util.Set<Integer> dirty = dirtyPixels.computeIfAbsent(pid,
                k -> java.util.concurrent.ConcurrentHashMap.newKeySet());
        synchronized (dirty) {
            if (!terrainFlushed.contains(pid)) {
                if (terrainCache != null) {
                    for (int i = 0; i < 16384; i++) {
                        canvas.setPixel(i & 127, i >> 7, terrainCache[i]);
                    }
                }
                terrainFlushed.add(pid);
            } else {
                // 用包含静态网格和岛屿的 terrainCache 完美恢复上一帧的动态覆盖层像素，既省性能，又无画线破损
                if (terrainCache != null) {
                    for (int idx : dirty) {
                        byte color = terrainCache[idx];
                        int px = idx & 127;
                        int py = idx >> 7;
                        if (canvas.getPixel(px, py) != color) {
                            canvas.setPixel(px, py, color);
                        }
                    }
                }
            }

            dirty.clear();
        }

        // 3. 绘制下一级白圈安全区范围（白色虚线圈）
        edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
        if (zm != null && zm.getCurrentPhase() < 5) {
            int nx1 = worldToCanvasX(zm.getTargetX() - zm.getTargetSize() / 2.0);
            int nx2 = worldToCanvasX(zm.getTargetX() + zm.getTargetSize() / 2.0);
            int nz1 = worldToCanvasZ(zm.getTargetZ() - zm.getTargetSize() / 2.0);
            int nz2 = worldToCanvasZ(zm.getTargetZ() + zm.getTargetSize() / 2.0);
            drawRect(canvas, nx1, nz1, nx2, nz2, COLOR_TARGET, true, dirty);
        }

        // 4. 绘制实时毒圈位置（蓝色实线）
        World world = player.getWorld();
        org.bukkit.WorldBorder border = world.getWorldBorder();
        int bx1 = worldToCanvasX(border.getCenter().getX() - border.getSize() / 2.0);
        int bx2 = worldToCanvasX(border.getCenter().getX() + border.getSize() / 2.0);
        int bz1 = worldToCanvasZ(border.getCenter().getZ() - border.getSize() / 2.0);
        int bz2 = worldToCanvasZ(border.getCenter().getZ() + border.getSize() / 2.0);
        drawRect(canvas, bx1, bz1, bx2, bz2, COLOR_BORDER, false, dirty);

        // 4.5 绘制空投位置（金色十字）
        for (Location drop : plugin.getAirdropManager().getActiveAirdrops()) {
            int dx = worldToCanvasX(drop.getX());
            int dz = worldToCanvasZ(drop.getZ());
            for (int r = -1; r <= 1; r++) {
                setOverlayPixel(canvas, dx + r, dz, COLOR_AIRDROP, dirty);
                setOverlayPixel(canvas, dx, dz + r, COLOR_AIRDROP, dirty);
            }
        }

        // 5. 绘制玩家位置（使用原版指针替代残留的像素绘图）
        updatePlayerCursor(canvas, player);
    }

    private void updatePlayerCursor(MapCanvas canvas, Player player) {
        org.bukkit.map.MapCursorCollection cursors = canvas.getCursors();
        while (cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0)); // 清理所有旧指针
        }

        int px = worldToCanvasX(player.getLocation().getX());
        int pz = worldToCanvasZ(player.getLocation().getZ());

        // MapCursor 的坐标范围是 -128 到 127
        byte cx = (byte) Math.max(-128, Math.min(127, (px * 2) - 128));
        byte cz = (byte) Math.max(-128, Math.min(127, (pz * 2) - 128));

        // 计算原版指针方向 (0到15)：0=下(南), 4=左(西), 8=上(北), 12=右(东) (以地图视角)
        // MC 中 Yaw 0=南。指针 0=南。直接换算即可，不再加 180！
        byte dir = (byte) (Math.round(player.getLocation().getYaw() / 22.5) & 0x0F);

        // Type 0 是默认的白底指针
        cursors.addCursor(cx, cz, dir, (byte) 0);
    }

    /**
     * 将世界坐标 X 映射到 canvas 上的 [0-128]（NORMAL缩放：1像素 = 4格）
     */
    private int worldToCanvasX(double worldX) {
        double relativeX = worldX - GameConfig.MAP_CENTER_X;
        return 64 + (int) Math.round(relativeX / GameConfig.MAP_SCALE);
    }

    /**
     * 将世界坐标 Z 映射到 canvas 上的 [0-128]（NORMAL缩放：1像素 = 4格）
     */
    private int worldToCanvasZ(double worldZ) {
        double relativeZ = worldZ - GameConfig.MAP_CENTER_Z;
        return 64 + (int) Math.round(relativeZ / GameConfig.MAP_SCALE);
    }

    /**
     * 绘制覆盖层像素并记录到 dirty set（高性能点绘制器：过滤相同像素）
     */
    private void setOverlayPixel(MapCanvas canvas, int x, int y, byte color, java.util.Set<Integer> dirty) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) {
            if (canvas.getPixel(x, y) != color) {
                canvas.setPixel(x, y, color);
            }
            dirty.add(x | (y << 7));
        }
    }

    /**
     * 辅助绘制矩形线框（先裁剪坐标到画布范围，防止世界边界巨大时 Bresenham 死循环）
     */
    private void drawRect(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color, boolean dotted,
            java.util.Set<Integer> dirty) {
        // 将矩形裁剪到画布可见区域 [0, 127]
        int cx1 = Math.max(0, Math.min(127, x1));
        int cy1 = Math.max(0, Math.min(127, y1));
        int cx2 = Math.max(0, Math.min(127, x2));
        int cy2 = Math.max(0, Math.min(127, y2));
        // 如果整个矩形都在画布同一条边外面（坍缩为一个点），跳过绘制
        if (cx1 == cx2 && cy1 == cy2)
            return;
        // 只绘制在画布范围内可见的边
        if (y1 >= 0 && y1 <= 127)
            drawLine(canvas, cx1, y1, cx2, y1, color, dotted, dirty); // 上边
        if (y2 >= 0 && y2 <= 127)
            drawLine(canvas, cx1, y2, cx2, y2, color, dotted, dirty); // 下边
        if (x1 >= 0 && x1 <= 127)
            drawLine(canvas, x1, cy1, x1, cy2, color, dotted, dirty); // 左边
        if (x2 >= 0 && x2 <= 127)
            drawLine(canvas, x2, cy1, x2, cy2, color, dotted, dirty); // 右边
    }

    /**
     * 辅助绘制直线（Bresenham 算法），支持虚线，像素记录到 dirty set
     * 关键：先将端点裁剪到 [0, 127] 画布范围内，防止坐标跨度过大（如默认 60M 世界边界）导致数千万次循环
     */
    private void drawLine(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color, boolean dotted,
            java.util.Set<Integer> dirty) {
        // 裁剪端点到画布范围，彻底杜绝巨坐标 Bresenham 死循环
        x1 = Math.max(0, Math.min(127, x1));
        y1 = Math.max(0, Math.min(127, y1));
        x2 = Math.max(0, Math.min(127, x2));
        y2 = Math.max(0, Math.min(127, y2));
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, count = 0;
        while (true) {
            if (!dotted || count % 2 == 0) {
                setOverlayPixel(canvas, x1, y1, color, dirty);
            }
            count++;
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
}
