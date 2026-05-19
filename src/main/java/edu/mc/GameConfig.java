package edu.mc;

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
    public static final int MIN_PLAYERS = 2;

    /** 大厅等待倒计时（秒）—— 玩家人数达标后开始 */
    public static final int LOBBY_COUNTDOWN = 30;

    /** 大厅倒计时触发 Title + 音效提示的关键节点（秒），超过最大值的秒数不提示 */
    public static final int LOBBY_WARN_1 = 30;
    public static final int LOBBY_WARN_2 = 15;
    /** 10 及以下的秒数每秒提示（在代码里用 countdownTime <= 10 判断） */
    public static final int LOBBY_WARN_EVERY_SECOND_BELOW = 10;

    /** 匹配航线阶段的倒计时（秒）——倒计时结束后飞机起飞 */
    public static final int STARTING_COUNTDOWN = 3;

    /** 大厅出生点坐标（X, Y, Z） */
    public static final double LOBBY_X = 1387.5;
    public static final double LOBBY_Y = 227;
    public static final double LOBBY_Z = 21.5;

    // =====================================================
    // 【飞机飞行阶段】
    // =====================================================

    /**
     * 飞机在地图中心（0, 16）外侧生成的轨道半径（格）。
     * 飞机起点和终点以此为半径对称生成。
     */
    public static final double FLIGHT_RADIUS = 220.0;

    /** 飞机飞行高度（Y 轴，格） */
    public static final double FLIGHT_ALTITUDE = 150.0;

    /**
     * 飞机飞越全程所需的 Tick 数。
     * 飞行速度 = (终点 - 起点) / FLIGHT_DURATION_TICKS
     * 默认 600 ticks = 30 秒（20 ticks/秒）
     */
    public static final int FLIGHT_DURATION_TICKS = 600;

    /**
     * 飞行任务的调度间隔（ticks）。
     * ⚠ 必须与 FlightManager 中 runTaskTimer 的 period 参数保持一致！
     * 同时 ticks 计数器每次 += FLIGHT_TASK_INTERVAL_TICKS。
     */
    public static final int FLIGHT_TASK_INTERVAL_TICKS = 2;

    /** 飞行阶段结束后（全部落地）进入正式游戏前的等待时间（秒） */
    public static final int FLIGHT_PHASE_COUNTDOWN = 60;

    // =====================================================
    // 【跳伞 & 滑翔阶段】
    // =====================================================

    /** 开伞后每帧向下的速度（负值 = 向下）。值越小下落越慢，滞空时间越长 */
    public static final double PARACHUTE_FALL_SPEED = -0.15;

    /** 开伞后水平滑翔速度倍率（应用于玩家朝向的 X/Z 分量） */
    public static final double PARACHUTE_GLIDE_MULTIPLIER = 0.6;

    // =====================================================
    // 【正式游戏阶段（缩圈）】
    // =====================================================

    /** 进入正式游戏后，首波缩圈前的等待时间（秒） */
    public static final int INGAME_FIRST_WAIT = 150;

    /** 每次缩圈结束后，下一波缩圈开始前的等待时间（秒） */
    public static final int INGAME_BETWEEN_SHRINK_WAIT = 150;

    /** 缩圈倒计时提示节点（秒）—— 剩余这些秒时广播提示 */
    public static final int INGAME_WARN_1MIN = 60;
    public static final int INGAME_WARN_30S = 30;
    public static final int INGAME_WARN_10S = 10;

    // =====================================================
    // 【毒圈参数】
    // =====================================================

    /**
     * 各阶段毒圈直径（格）—— 索引 0 为第一波
     * 当前为原始值的 3/5，增加挑战性
     */
    public static final double[] ZONE_SIZES = { 240.0, 150.0, 90.0, 30.0, 12.0, 3.0 };

    /** 各阶段缩圈动画持续时间（秒） */
    public static final int[] ZONE_SHRINK_TIMES = { 120, 100, 80, 60, 45, 30 };

    /** 各阶段毒圈每 2 秒扣除的血量（1.0 = 半颗心 = 1点生命值） */
    public static final double[] ZONE_DAMAGES = { 1.0, 2.0, 4.0, 8.0, 16.0, 32.0 };

    /** 毒圈扣血的间隔（每 N 个 GameManager tick 扣一次，1 tick = 1 秒） */
    public static final int ZONE_DAMAGE_INTERVAL_TICKS = 2;

    // =====================================================
    // 【结算阶段】
    // =====================================================

    /** 游戏结束后结算显示时间（秒） */
    public static final int ENDING_COUNTDOWN = 10;

    // =====================================================
    // 【地图 & 雷达渲染】
    // =====================================================

    /** 雷达地图中心世界坐标（X） */
    public static final double MAP_CENTER_X = 0.0;

    /** 雷达地图中心世界坐标（Z） */
    public static final double MAP_CENTER_Z = 16.0;

    /** 雷达地图缩放比例（1 像素 = N 格），对应 MapView.Scale.NORMAL */
    public static final double MAP_SCALE = 4.0;

    /** 雷达渲染帧率节流：两帧之间的最短间隔（毫秒） */
    public static final long MAP_RENDER_THROTTLE_MS = 250L;

    /** 玩家朝向指示线的长度（像素） */
    public static final int MAP_DIRECTION_LINE_LENGTH = 5;

    /** 战术网格虚线的世界坐标间隔（格） */
    public static final int MAP_GRID_INTERVAL = 100;

    /** 战术网格的世界坐标覆盖范围（从 -MAP_GRID_RANGE 到 +MAP_GRID_RANGE） */
    public static final int MAP_GRID_RANGE = 300;

    /** 吃鸡主岛在世界坐标中的边界 */
    public static final int ISLAND_X1 = -256;
    public static final int ISLAND_X2 = 249;
    public static final int ISLAND_Z1 = -237;
    public static final int ISLAND_Z2 = 270;
}
