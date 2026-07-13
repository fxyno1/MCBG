package edu.mc;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * MCBG 全局游戏配置中心
 * =====================================================
 * 所有需要调整的游戏参数都集中在此文件。
 * 修改参数时只需改这一个文件，无需深入逻辑代码。
 * =====================================================
 */
public final class GameConfig {

    private GameConfig() {
    } // 禁止实例化

    // =====================================================
    // 【大厅 & 匹配阶段】
    // =====================================================

    /** 游戏开始所需的最少玩家数 */
    public static int MIN_PLAYERS = 2;

    /** 大厅等待倒计时（秒）—— 玩家人数达标后开始 */
    public static int LOBBY_COUNTDOWN = 30;

    /** 大厅倒计时触发 Title + 音效提示的关键节点（秒），超过最大值的秒数不提示 */
    public static int LOBBY_WARN_1 = 30;
    public static int LOBBY_WARN_2 = 15;
    /** 10 及以下的秒数每秒提示（在代码里用 countdownTime <= 10 判断） */
    public static int LOBBY_WARN_EVERY_SECOND_BELOW = 10;

    /** 匹配航线阶段的倒计时（秒）——倒计时结束后飞机起飞 */
    public static int STARTING_COUNTDOWN = 3;

    /** 大厅出生点坐标（X, Y, Z） */
    public static double LOBBY_X = 1387.5;
    public static double LOBBY_Y = 227;
    public static double LOBBY_Z = 21.5;

    // =====================================================
    // 【飞机飞行阶段】
    // =====================================================

    /**
     * 飞机在地图中心（0, 16）外侧生成的轨道半径（格）。
     * 飞机起点和终点以此为半径对称生成。
     */
    public static double FLIGHT_RADIUS = 220.0;

    /** 飞机飞行高度（Y 轴，格） */
    public static double FLIGHT_ALTITUDE = 150.0;

    /**
     * 决定飞机速度的除数（越小飞得越快，原版为 150.0）
     * 飞行速度 = (终点 - 起点) / FLIGHT_SPEED_DIVISOR
     */
    public static double FLIGHT_SPEED_DIVISOR = 150.0;

    /**
     * 飞机飞越全程的强制超时 Tick 数。
     * 超过此时间后强制所有人跳伞（默认 600 ticks = 30 秒）
     */
    public static int FLIGHT_DURATION_TICKS = 600;

    /**
     * 飞行任务的调度间隔（ticks）。
     * ⚠ 必须与 FlightManager 中 runTaskTimer 的 period 参数保持一致！
     * 同时 ticks 计数器每次 += FLIGHT_TASK_INTERVAL_TICKS。
     */
    public static int FLIGHT_TASK_INTERVAL_TICKS = 4;

    /** 飞行阶段结束后（全部落地）进入正式游戏前的等待时间（秒） */
    public static int FLIGHT_PHASE_COUNTDOWN = 60;

    // =====================================================
    // 【飞机与跳伞快捷栏槽位】
    // =====================================================

    /** 雷达地图在飞机上以及跳伞时的快捷栏槽位（0-8） */
    public static int FLIGHT_MAP_SLOT = 0;

    /** 跳伞羽毛在飞机上的快捷栏槽位（0-8） */
    public static int FLIGHT_PARACHUTE_SLOT = 4;

    /** 队伍匹配模式：单排、双排、四排 */
    public static int TEAM_MAX_PLAYERS_PER_TEAM = 4;

    /** 组队状态下，用来展示队伍颜色的帽子，放在快捷栏的哪个槽位（0-8） */
    public static int TEAM_HAT_INVENTORY_SLOT = 7;

    /** 组队状态下，大厅准备期间用来展示队伍颜色的帽子，放在快捷栏的哪个槽位（0-8） */
    public static int TEAM_HAT_LOBBY_SLOT = 7;

    // =====================================================
    // 【跳伞 & 滑翔阶段】
    // =====================================================

    /** 开伞后每帧向下的速度（负值 = 向下）。值越小下落越慢，滞空时间越长 */
    public static double PARACHUTE_FALL_SPEED = -0.2;

    /** 开伞后水平滑翔速度倍率（应用于玩家朝向的 X/Z 分量） */
    public static double PARACHUTE_GLIDE_MULTIPLIER = 1.5;

    // =====================================================
    // 【正式游戏阶段（缩圈）】
    // =====================================================

    /** 进入正式游戏后，首波缩圈前的等待时间（秒） */
    public static int INGAME_FIRST_WAIT = 150;

    /** 每次缩圈结束后，下一波缩圈开始前的等待时间（秒） */
    public static int INGAME_BETWEEN_SHRINK_WAIT = 150;

    /** 缩圈倒计时提示节点（秒）—— 剩余这些秒时广播提示 */
    public static int INGAME_WARN_1MIN = 60;
    public static int INGAME_WARN_30S = 30;
    public static int INGAME_WARN_10S = 10;

    // =====================================================
    // 【毒圈参数】
    // =====================================================

    /**
     * 各阶段毒圈直径（格）—— 索引 0 为第一波
     * 当前为原始值的 3/5，增加挑战性
     */
    public static double[] ZONE_SIZES = { 240.0, 150.0, 90.0, 30.0, 12.0, 3.0 };

    /** 各阶段缩圈动画持续时间（秒） */
    public static int[] ZONE_SHRINK_TIMES = { 120, 100, 80, 60, 45, 30 };

    /** 各阶段毒圈每 2 秒扣除的血量（1.0 = 半颗心 = 1点生命值） */
    public static double[] ZONE_DAMAGES = { 1.0, 2.0, 4.0, 8.0, 16.0, 32.0 };

    /** 毒圈扣血的间隔（每 N 个 GameManager tick 扣一次，1 tick = 1 秒） */
    public static int ZONE_DAMAGE_INTERVAL_TICKS = 2;

    // =====================================================
    // 【结算阶段】
    // =====================================================

    /** 游戏结束后结算显示时间（秒） */
    public static int ENDING_COUNTDOWN = 10;

    // =====================================================
    // 【地图 & 雷达渲染】
    // =====================================================

    /** 雷达地图中心世界坐标（X） */
    public static double MAP_CENTER_X = 0.0;

    /** 雷达地图中心世界坐标（Z） */
    public static double MAP_CENTER_Z = 16.0;

    /** 雷达地图缩放比例（1 像素 = N 格），对应 MapView.Scale.NORMAL */
    public static double MAP_SCALE = 4.0;

    /** 雷达渲染帧率节流：两帧之间的最短间隔（毫秒） */
    public static long MAP_RENDER_THROTTLE_MS = 250L;

    /** 玩家朝向指示线的长度（像素） */
    public static int MAP_DIRECTION_LINE_LENGTH = 5;

    /** 战术网格虚线的世界坐标间隔（格） */
    public static int MAP_GRID_INTERVAL = 100;

    /** 战术网格的世界坐标覆盖范围（从 -MAP_GRID_RANGE 到 +MAP_GRID_RANGE） */
    public static int MAP_GRID_RANGE = 300;

    /** 吃鸡主岛在世界坐标中的边界 */
    public static int ISLAND_X1 = -256;
    public static int ISLAND_X2 = 249;
    public static int ISLAND_Z1 = -237;
    public static int ISLAND_Z2 = 270;

    /**
     * 从 FileConfiguration 载入变量数据
     */
    public static void load(FileConfiguration config) {
        MIN_PLAYERS = config.getInt("lobby.min-players", MIN_PLAYERS);
        LOBBY_COUNTDOWN = config.getInt("lobby.countdown", LOBBY_COUNTDOWN);
        LOBBY_WARN_1 = config.getInt("lobby.warn-1", LOBBY_WARN_1);
        LOBBY_WARN_2 = config.getInt("lobby.warn-2", LOBBY_WARN_2);
        LOBBY_WARN_EVERY_SECOND_BELOW = config.getInt("lobby.warn-every-second-below", LOBBY_WARN_EVERY_SECOND_BELOW);
        STARTING_COUNTDOWN = config.getInt("lobby.starting-countdown", STARTING_COUNTDOWN);
        LOBBY_X = config.getDouble("lobby.spawn-x", LOBBY_X);
        LOBBY_Y = config.getDouble("lobby.spawn-y", LOBBY_Y);
        LOBBY_Z = config.getDouble("lobby.spawn-z", LOBBY_Z);

        FLIGHT_RADIUS = config.getDouble("flight.radius", FLIGHT_RADIUS);
        FLIGHT_ALTITUDE = config.getDouble("flight.altitude", FLIGHT_ALTITUDE);
        FLIGHT_SPEED_DIVISOR = config.getDouble("flight.speed-divisor", FLIGHT_SPEED_DIVISOR);
        FLIGHT_DURATION_TICKS = config.getInt("flight.duration-ticks", FLIGHT_DURATION_TICKS);
        FLIGHT_TASK_INTERVAL_TICKS = config.getInt("flight.task-interval-ticks", FLIGHT_TASK_INTERVAL_TICKS);
        FLIGHT_PHASE_COUNTDOWN = config.getInt("flight.phase-countdown", FLIGHT_PHASE_COUNTDOWN);
        
        FLIGHT_MAP_SLOT = config.getInt("flight.map-slot", FLIGHT_MAP_SLOT);
        FLIGHT_PARACHUTE_SLOT = config.getInt("flight.parachute-slot", FLIGHT_PARACHUTE_SLOT);
        
        TEAM_MAX_PLAYERS_PER_TEAM = config.getInt("team.max-players-per-team", TEAM_MAX_PLAYERS_PER_TEAM);
        TEAM_HAT_INVENTORY_SLOT = config.getInt("team.hat-inventory-slot", TEAM_HAT_INVENTORY_SLOT);
        TEAM_HAT_LOBBY_SLOT = config.getInt("team.hat-lobby-slot", TEAM_HAT_LOBBY_SLOT);

        PARACHUTE_FALL_SPEED = config.getDouble("parachute.fall-speed", PARACHUTE_FALL_SPEED);
        PARACHUTE_GLIDE_MULTIPLIER = config.getDouble("parachute.glide-multiplier", PARACHUTE_GLIDE_MULTIPLIER);

        INGAME_FIRST_WAIT = config.getInt("game.first-wait", INGAME_FIRST_WAIT);
        INGAME_BETWEEN_SHRINK_WAIT = config.getInt("game.between-shrink-wait", INGAME_BETWEEN_SHRINK_WAIT);
        INGAME_WARN_1MIN = config.getInt("game.warn-1min", INGAME_WARN_1MIN);
        INGAME_WARN_30S = config.getInt("game.warn-30s", INGAME_WARN_30S);
        INGAME_WARN_10S = config.getInt("game.warn-10s", INGAME_WARN_10S);

        if (config.contains("zone.sizes")) {
            java.util.List<Double> list = config.getDoubleList("zone.sizes");
            ZONE_SIZES = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                ZONE_SIZES[i] = list.get(i);
            }
        }
        if (config.contains("zone.shrink-times")) {
            java.util.List<Integer> list = config.getIntegerList("zone.shrink-times");
            ZONE_SHRINK_TIMES = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                ZONE_SHRINK_TIMES[i] = list.get(i);
            }
        }
        if (config.contains("zone.damages")) {
            java.util.List<Double> list = config.getDoubleList("zone.damages");
            ZONE_DAMAGES = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                ZONE_DAMAGES[i] = list.get(i);
            }
        }
        ZONE_DAMAGE_INTERVAL_TICKS = config.getInt("zone.damage-interval-ticks", ZONE_DAMAGE_INTERVAL_TICKS);

        ENDING_COUNTDOWN = config.getInt("ending.countdown", ENDING_COUNTDOWN);

        MAP_CENTER_X = config.getDouble("map.center-x", MAP_CENTER_X);
        MAP_CENTER_Z = config.getDouble("map.center-z", MAP_CENTER_Z);
        MAP_SCALE = config.getDouble("map.scale", MAP_SCALE);
        MAP_RENDER_THROTTLE_MS = config.getLong("map.render-throttle-ms", MAP_RENDER_THROTTLE_MS);
        MAP_DIRECTION_LINE_LENGTH = config.getInt("map.direction-line-length", MAP_DIRECTION_LINE_LENGTH);
        MAP_GRID_INTERVAL = config.getInt("map.grid-interval", MAP_GRID_INTERVAL);
        MAP_GRID_RANGE = config.getInt("map.grid-range", MAP_GRID_RANGE);
        ISLAND_X1 = config.getInt("map.island-x1", ISLAND_X1);
        ISLAND_X2 = config.getInt("map.island-x2", ISLAND_X2);
        ISLAND_Z1 = config.getInt("map.island-z1", ISLAND_Z1);
        ISLAND_Z2 = config.getInt("map.island-z2", ISLAND_Z2);
    }
}
