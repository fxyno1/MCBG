package edu.mc.map;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class RadarMapRenderer extends MapRenderer {

    private static final byte COLOR_GRID = MapPalette.matchColor(50, 50, 50);
    private static final byte COLOR_ISLAND = MapPalette.matchColor(0, 100, 0);
    private static final byte COLOR_BORDER = MapPalette.matchColor(0, 80, 220);
    private static final byte COLOR_TARGET = MapPalette.matchColor(240, 240, 240);
    private static final byte COLOR_PLAYER = MapPalette.matchColor(0, 255, 0);
    private static final byte COLOR_FLIGHT = MapPalette.matchColor(255, 0, 0);
    private static final byte COLOR_AIRDROP = MapPalette.matchColor(0, 255, 255);

    private final ChickenDinnerPlugin plugin;
    private final byte[] terrainCache;

    // 玩家级刷新状态控制
    private final java.util.Set<java.util.UUID> terrainFlushed = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<java.util.UUID> forceFullRefresh = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, java.util.Set<Integer>> dirtyPixels = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastRenderTime = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean flightLineRenderedInCache = false;

    public RadarMapRenderer(ChickenDinnerPlugin plugin, byte[] terrainCache) {
        super(true);
        this.plugin = plugin;
        this.terrainCache = new byte[16384];
        if (terrainCache != null) {
            System.arraycopy(terrainCache, 0, this.terrainCache, 0, 16384);
        }
        preRenderStaticElements();
    }

    /**
     * 重置单一玩家的状态：标记他需要全屏强制重绘，彻底解决中途退赛/掉线重连的残留。
     */
    public void resetPlayerState(java.util.UUID pid) {
        terrainFlushed.remove(pid);
        lastRenderTime.remove(pid);
        forceFullRefresh.add(pid); // 核心：加入强制全量刷新标签
        java.util.Set<Integer> dirty = dirtyPixels.get(pid);
        if (dirty != null) {
            dirty.clear();
        }
    }

    /**
     * 彻底擦除航线，将底图复原为最初状态。
     */
    public void resetGlobalCache() {
        synchronized (this.terrainCache) {
            flightLineRenderedInCache = false;
            terrainFlushed.clear();
            dirtyPixels.clear();
            forceFullRefresh.clear();

            // 重新拉取原汁原味的无航线底图
            byte[] globalCache = plugin.getTerrainCache();
            if (globalCache != null) {
                System.arraycopy(globalCache, 0, this.terrainCache, 0, 16384);
            }
            preRenderStaticElements();
        }
    }

    private void preRenderStaticElements() {
        // 1. 绘制战术网格虚线
        for (int wx = -GameConfig.MAP_GRID_RANGE; wx <= GameConfig.MAP_GRID_RANGE; wx += GameConfig.MAP_GRID_INTERVAL) {
            int cx = worldToCanvasX(wx);
            if (cx >= 0 && cx < 128) {
                for (int cy = 0; cy < 128; cy++) {
                    if (cy % 2 == 0) setCachePixel(cx, cy, COLOR_GRID);
                }
            }
        }
        for (int wz = -GameConfig.MAP_GRID_RANGE; wz <= GameConfig.MAP_GRID_RANGE; wz += GameConfig.MAP_GRID_INTERVAL) {
            int cy = worldToCanvasZ(wz + GameConfig.MAP_CENTER_Z);
            if (cy >= 0 && cy < 128) {
                for (int cx = 0; cx < 128; cx++) {
                    if (cx % 2 == 0) setCachePixel(cx, cy, COLOR_GRID);
                }
            }
        }
        // 2. 绘制战斗主岛轮廓线
        int ix1 = worldToCanvasX(GameConfig.ISLAND_X1), ix2 = worldToCanvasX(GameConfig.ISLAND_X2);
        int iz1 = worldToCanvasZ(GameConfig.ISLAND_Z1), iz2 = worldToCanvasZ(GameConfig.ISLAND_Z2);
        drawCacheRect(ix1, iz1, ix2, iz2, COLOR_ISLAND);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (player == null || !player.isOnline()) return;

        java.util.UUID pid = player.getUniqueId();
        GameState currentState = plugin.getCurrentState();

        // 1. 动态生成并烤入航线
        if (!flightLineRenderedInCache && (currentState == GameState.FLIGHT || currentState == GameState.STARTING || currentState == GameState.INGAME)) {
            synchronized (this.terrainCache) {
                if (!flightLineRenderedInCache) {
                    Location start = plugin.getFlightManager().getStartPoint();
                    Location end = plugin.getFlightManager().getEndPoint();
                    if (start != null && end != null) {
                        int sx = worldToCanvasX(start.getX()), sz = worldToCanvasZ(start.getZ());
                        int ex = worldToCanvasX(end.getX()), ez = worldToCanvasZ(end.getZ());
                        drawCacheLine(sx, sz, ex, ez, COLOR_FLIGHT, true);
                        flightLineRenderedInCache = true;

                        // 航线刚生成时，通知所有人下一帧必须重做底图刷新
                        terrainFlushed.clear();
                    }
                }
            }
        }

        // 大厅闲置阶段性能节流：非强制刷新情况下，一旦 flush 过就不再耗费 CPU
        if (currentState == GameState.LOBBY && terrainFlushed.contains(pid) && !forceFullRefresh.contains(pid)) {
            updatePlayerCursor(canvas, player);
            return;
        }

        // 渲染帧率节流控制
        long now = System.currentTimeMillis();
        long last = lastRenderTime.getOrDefault(pid, 0L);
        if (now - last < GameConfig.MAP_RENDER_THROTTLE_MS && !forceFullRefresh.contains(pid)) {
            return;
        }
        lastRenderTime.put(pid, now);

        java.util.Set<Integer> dirty = dirtyPixels.computeIfAbsent(pid, k -> java.util.concurrent.ConcurrentHashMap.newKeySet());

        synchronized (dirty) {
            // 【核心修复】如果带有 forceFullRefresh 标志，或者从未刷过底图：强制全屏拉底图覆盖，擦除一切历史残余
            if (!terrainFlushed.contains(pid) || forceFullRefresh.contains(pid)) {
                for (int i = 0; i < 16384; i++) {
                    canvas.setPixel(i & 127, i >> 7, terrainCache[i]);
                }
                terrainFlushed.add(pid);
                forceFullRefresh.remove(pid); // 释放强制重绘锁
                dirty.clear();
            } else {
                // 正常回滚上一帧的动态像素覆盖层
                for (int idx : dirty) {
                    byte color = terrainCache[idx];
                    int px = idx & 127;
                    int py = idx >> 7;
                    if (canvas.getPixel(px, py) != color) {
                        canvas.setPixel(px, py, color);
                    }
                }
                dirty.clear();
            }
        }

        // 2. 重新绘制当前周期的动态白圈（安全区）
        edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
        if (zm != null && zm.getCurrentPhase() < 5) {
            int nx1 = worldToCanvasX(zm.getTargetX() - zm.getTargetSize() / 2.0);
            int nx2 = worldToCanvasX(zm.getTargetX() + zm.getTargetSize() / 2.0);
            int nz1 = worldToCanvasZ(zm.getTargetZ() - zm.getTargetSize() / 2.0);
            int nz2 = worldToCanvasZ(zm.getTargetZ() + zm.getTargetSize() / 2.0);
            drawRect(canvas, nx1, nz1, nx2, nz2, COLOR_TARGET, true, dirty);
        }

        // 3. 重新绘制当前周期的动态蓝圈（毒圈边界）
        World world = player.getWorld();
        org.bukkit.WorldBorder border = world.getWorldBorder();
        int bx1 = worldToCanvasX(border.getCenter().getX() - border.getSize() / 2.0);
        int bx2 = worldToCanvasX(border.getCenter().getX() + border.getSize() / 2.0);
        int bz1 = worldToCanvasZ(border.getCenter().getZ() - border.getSize() / 2.0);
        int bz2 = worldToCanvasZ(border.getCenter().getZ() + border.getSize() / 2.0);
        drawRect(canvas, bx1, bz1, bx2, bz2, COLOR_BORDER, false, dirty);

        // 4. 重新绘制实时动态空投
        for (Location drop : plugin.getAirdropManager().getActiveAirdrops()) {
            int dx = worldToCanvasX(drop.getX());
            int dz = worldToCanvasZ(drop.getZ());
            for (int r = -1; r <= 1; r++) {
                setOverlayPixel(canvas, dx + r, dz, COLOR_AIRDROP, dirty);
                setOverlayPixel(canvas, dx, dz + r, COLOR_AIRDROP, dirty);
            }
        }

        // 5. 更新原版小指针
        updatePlayerCursor(canvas, player);
    }

    // ... 以下的 worldToCanvasX, worldToCanvasZ, setOverlayPixel, drawRect, drawLine, drawCacheLine 等绘图辅助方法完全保持原样即可 ...
    private void setCachePixel(int x, int y, byte color) { if (x >= 0 && x < 128 && y >= 0 && y < 128) terrainCache[x + y * 128] = color; }
    private void drawCacheRect(int x1, int y1, int x2, int y2, byte color) { drawCacheLine(x1, y1, x2, y1, color); drawCacheLine(x1, y2, x2, y2, color); drawCacheLine(x1, y1, x1, y2, color); drawCacheLine(x2, y1, x2, y2, color); }
    private void drawCacheLine(int x1, int y1, int x2, int y2, byte color) { drawCacheLine(x1, y1, x2, y2, color, false); }
    private void drawCacheLine(int x1, int y1, int x2, int y2, byte color, boolean dotted) { int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1); int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1; int err = dx - dy, count = 0; while (true) { if (!dotted || count % 2 == 0) setCachePixel(x1, y1, color); count++; if (x1 == x2 && y1 == y2) break; int e2 = 2 * err; if (e2 > -dy) { err -= dy; x1 += sx; } if (e2 < dx) { err += dx; y1 += sy; } } }
    private void updatePlayerCursor(MapCanvas canvas, Player player) { org.bukkit.map.MapCursorCollection cursors = canvas.getCursors(); while (cursors.size() > 0) { cursors.removeCursor(cursors.getCursor(0)); } int px = worldToCanvasX(player.getLocation().getX()); int pz = worldToCanvasZ(player.getLocation().getZ()); byte cx = (byte) Math.max(-128, Math.min(127, (px * 2) - 128)); byte cz = (byte) Math.max(-128, Math.min(127, (pz * 2) - 128)); byte dir = (byte) (Math.round(player.getLocation().getYaw() / 22.5) & 0x0F); cursors.addCursor(cx, cz, dir, (byte) 0); }
    private int worldToCanvasX(double worldX) { double relativeX = worldX - GameConfig.MAP_CENTER_X; return 64 + (int) Math.round(relativeX / GameConfig.MAP_SCALE); }
    private int worldToCanvasZ(double worldZ) { double relativeZ = worldZ - GameConfig.MAP_CENTER_Z; return 64 + (int) Math.round(relativeZ / GameConfig.MAP_SCALE); }
    private void setOverlayPixel(MapCanvas canvas, int x, int y, byte color, java.util.Set<Integer> dirty) { if (x >= 0 && x < 128 && y >= 0 && y < 128) { if (canvas.getPixel(x, y) != color) { canvas.setPixel(x, y, color); } dirty.add(x | (y << 7)); } }
    private void drawRect(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color, boolean dotted, java.util.Set<Integer> dirty) { int cx1 = Math.max(0, Math.min(127, x1)); int cy1 = Math.max(0, Math.min(127, y1)); int cx2 = Math.max(0, Math.min(127, x2)); int cy2 = Math.max(0, Math.min(127, y2)); if (cx1 == cx2 && cy1 == cy2) return; if (y1 >= 0 && y1 <= 127) drawLine(canvas, cx1, y1, cx2, y1, color, dotted, dirty); if (y2 >= 0 && y2 <= 127) drawLine(canvas, cx1, y2, cx2, y2, color, dotted, dirty); if (x1 >= 0 && x1 <= 127) drawLine(canvas, x1, cy1, x1, cy2, color, dotted, dirty); if (x2 >= 0 && x2 <= 127) drawLine(canvas, x2, cy1, x2, cy2, color, dotted, dirty); }
    private void drawLine(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color, boolean dotted, java.util.Set<Integer> dirty) { x1 = Math.max(0, Math.min(127, x1)); y1 = Math.max(0, Math.min(127, y1)); x2 = Math.max(0, Math.min(127, x2)); y2 = Math.max(0, Math.min(127, y2)); int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1); int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1; int err = dx - dy, count = 0; while (true) { if (!dotted || count % 2 == 0) { setOverlayPixel(canvas, x1, y1, color, dirty); } count++; if (x1 == x2 && y1 == y2) break; int e2 = 2 * err; if (e2 > -dy) { err -= dy; x1 += sx; } if (e2 < dx) { err += dx; y1 += sy; } } }
}