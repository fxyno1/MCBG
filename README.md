# MCBG (Minecraft Battlegrounds)

*For the Chinese version, please scroll down. / 中文说明请见下方。*

## License & Usage Terms
This project is **Source-Available, NOT Open Source**.
- For educational & private use only
- **This plugin cannot run without the ProtocolLib dependency plugin installed in your plugins folder.**
- **This plugin can ONLY display the player map properly if there is a map.png file in the root directory of the save.**
- **No free compilation/distribution, no resale/leaking**
- Commercial use requires official purchase
- Violations will be met with DMCA takedown

See [LICENSE](./LICENSE) for full terms.

## Features

*   **Flight & Parachute System**
    *   Players are teleported to a plane flying along a randomly generated path across the map.
    *   Players can choose when to drop and use a feather to deploy their parachute and glide to their desired destination.
    *   Built-in fall damage prevention upon landing.

*   **Packet-Based Radar Map**
    *   Bypasses native Minecraft map limitations using ProtocolLib to send custom packets for a high-frequency refreshing radar.
    *   The radar displays: Player position, facing direction, safe zone (white circle), danger zone (blue circle), flight path, and teammates.

*   **Dynamic Zone System**
    *   Customizable multi-phase shrinking safe zones. Radius, wait time, shrink time, and damage per second can be configured for each phase.
    *   Players outside the safe zone take continuous true damage that ignores armor.

*   **Healing System**
    *   Custom healing items: Bandages (heals a small amount), First Aid Kits (heals to full), and Medkits (heals to full health and hunger).
    *   Healing actions have casting times and progress bars. Movement, attacking, and inventory interactions are restricted while healing.

*   **Team System**
    *   Supports custom team sizes (e.g., Solo, Duo, Squad).
    *   Intuitive lobby GUI for players to form teams. Auto-assigns remaining players when the match starts.
    *   Teammates are given colored leather armor, colored name tags, and have friendly fire disabled.

*   **Tactical Weapons**
    *   **Frag Grenades (TNT):** Right-click to throw. Explodes after a delay, dealing massive true damage.
    *   **Molotovs (Fire Charges):** Right-click to throw. Explodes on impact creating a fire field, dealing damage that scales with distance from the center.

*   **Death Chest System**
    *   When a player dies, their inventory is safely stored in an automatically generated double trapped chest to prevent lag from dropped items.
    *   A tombstone sign with the deceased player's name is placed next to the chest.

*   **Auto World Management**
    *   Automatically clones a template world named `world_backup` to a temporary `game_1` world before every match.
    *   The temporary world is entirely deleted after the match, guaranteeing a pristine map for every game.

## Dependencies

*   **[ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)** (Required): Essential for intercepting and sending custom map packets.
*   Multiverse-Core (Recommended): For managing the lobby world.

## Setup & Deployment

1.  Place `MCBG.jar` and `ProtocolLib.jar` into your server's `plugins` folder.
2.  Ensure you have a world folder named `world_backup` in your server's root directory to serve as the template map.
3.  Ensure there is a `map.png` file in the root directory of the `world_backup` save.
4.  Start the server to generate the default `config.yml`.
5.  Configure lobby coordinates, map bounds, zone phases, and radar scales in `config.yml`.
6.  Restart the server or use `/cd reload` to apply changes.

## Commands

### Admin Commands (Requires `mcbg.admin`)
*   `/cd start` - Force start the match.
*   `/cd pause` - Pause/resume the current countdown timer.
*   `/cd skip` - Skip the current waiting phase.
*   `/cd stop` - Force stop the match and reset the map.
*   `/cd settime <seconds>` - Override the remaining countdown time.
*   `/cd resetmap` - Force refresh the radar map for all online players.
*   `/cd reload` - Reload `config.yml`.
*   `/cd cleansigns <world_name>` - Scan and remove leftover death chests and signs from a template world (causes lag due to chunk loading).

### Player Commands
*   `/hub` or `/lobby` - Leave the current match and return to the lobby.

## Configuration

The `config.yml` provides extensive customization:
*   **lobby**: Lobby spawn coordinates, minimum player thresholds, and countdown timers.
*   **flight**: Flight path generation radius, altitude, speed, and auto-drop timeout.
*   **team**: Max team size and hotbar slot overrides for the team indicator hat.
*   **zone**: Array of radii for safe zones, shrink animations, and damage values.
*   **map**: Center coordinates of the radar, scale factor, island bounds, and grid size.

## Architecture & Implementation Details

This plugin is designed with a highly optimized, modular architecture. Below are the in-depth technical details of its core systems:

*   **1. State Machine & Event Loop (`GameManager` & `GameState`)**
    *   **Architecture:** The game lifecycle is strictly managed by an enum-based state machine (`LOBBY` -> `STARTING` -> `FLIGHT` -> `PARACHUTE` -> `INGAME` -> `ENDING`). 
    *   **Implementation:** A centralized `BukkitRunnable` (`GameManager#run()`) serves as the Main Game Tick. Instead of having dozens of scattered schedulers, this single loop delegates ticks to respective handlers based on the current `GameState`. This ensures predictable phase transitions and prevents memory leaks from orphan tasks.

*   **2. High-Performance Packet Radar (`PacketMapManager`)**
    *   **Architecture:** Completely bypasses the native, highly-inefficient Bukkit `MapView` API.
    *   **Implementation:** Utilizes `ProtocolLib` to send `PacketType.Play.Server.MAP` packets directly to clients. The plugin assigns virtual Map IDs (e.g., >10000) to avoid conflicts with vanilla maps.
    *   **Rendering Algorithm:** A static `map.png` is read via `ImageIO` and cached as a byte array using `MapPalette.matchColor`. In an asynchronous thread, the plugin dynamically calculates pixel coordinates for players, safe zones (Bresenham's circle algorithm), flight paths, and danger zones. Updates are throttled (e.g., 250ms) to prevent network thread saturation.

*   **3. Dynamic Multi-World Cloning (`WorldManager`)**
    *   **Architecture:** Zero-dependency world management to ensure a pristine map without relying on heavy rollback plugins (like CoreProtect).
    *   **Implementation:** Upon game start, the plugin uses standard Java I/O to recursively copy the `world_backup` template directory into a new `game_1` directory. `Bukkit.createWorld()` is then invoked to load it into memory. Auto-save is forcefully disabled (`world.setAutoSave(false)`) to prevent unnecessary disk I/O. Upon match end, the world is safely unloaded, and the `game_1` directory (along with `uid.dat` and `session.lock`) is completely wiped from the disk.

*   **4. Flight & Movement Physics (`FlightManager`)**
    *   **Architecture:** Simulates a moving airplane and parachute gliding using pure vector mathematics without actual vehicle entities.
    *   **Implementation:** The flight trajectory calculates a start and end `Location` based on a random angle along a defined `FLIGHT_RADIUS`. A fast-ticking `BukkitRunnable` applies `Player#setVelocity()` to all players onboard. 
    *   **Cross-World Safety:** To circumvent Bukkit/Anti-Cheat movement rollbacks caused by cross-world teleports, a strict 20-tick delay is enforced before velocity is applied to players entering the game world. Parachuting physics overrides vanilla gravity by calculating a downward fall speed and applying a horizontal glide multiplier based on the player's `Yaw` and `Pitch`.

*   **5. Zone Shrinking Algorithm (`ZoneManager` / `GameManager`)**
    *   **Architecture:** A multi-phase geometric algorithm to handle safe zone bounds and damage.
    *   **Implementation:** The shrinking process uses mathematical Linear Interpolation (Lerp). The current danger radius is calculated tick-by-tick: `currentRadius = oldRadius - (oldRadius - newRadius) * (elapsedShrinkTicks / totalShrinkTicks)`. Player distance to the center `(0, 16)` is calculated in a 2D plane `(X, Z)`. If a player is outside the boundary, `Player#setHealth()` is manipulated directly to apply true damage, perfectly bypassing vanilla armor enchants and resistance effects.

*   **6. Action-Interrupted Healing (`HealingManager`)**
    *   **Implementation:** When a healing item is used, a `BukkitRunnable` tracks the casting time. The player's initial location is stored. If the player moves more than `0.5` blocks (`Location#distanceSquared`) or takes damage (tracked via `EntityDamageEvent`), the casting scheduler is immediately cancelled, enforcing strict tactical cover usage.

---

# MCBG (Minecraft Battlegrounds) - 中文说明

## 许可协议与使用条款
本项目**源码可见，并非开源项目**。
- 仅限学习研究及个人私下使用
- **若插件文件夹中未安装ProtocolLib依赖插件，本插件将无法运行。**
- **仅当存档根目录内存在map.png文件时，插件才能正常展示玩家地图。**
- **禁止私自编译、分发，禁止转售、外泄本插件**
- 商业用途需向官方购买授权
- 违规行为将触发DMCA下架处置

完整条款详见 [许可文件](./LICENSE)。

## 核心特性

*   **跳伞系统**
    *   游戏开始后，玩家将被传送至一条随机生成的空中航线上。
    *   玩家可根据战术需求自由选择跳伞时机，并使用“羽毛”道具控制滑翔轨迹与降落点。
    *   内置降落判定算法，防止玩家在正常落地时受到高空坠落伤害。

*   **战术雷达**
    *   突破原生 Minecraft 地图的刷新限制，基于 ProtocolLib 直接拦截与发送数据包，实现高频刷新的独立战术雷达。
    *   雷达界面实时呈现：玩家坐标、玩家朝向、安全区（白圈）范围、毒圈（蓝圈）范围、飞机航线以及同队成员位置。

*   **动态毒圈系统**
    *   支持高度自定义的多阶段安全区收缩机制。可通过配置文件精确设定每一阶段的区域半径、等待时长、收缩耗时及对应伤害值。
    *   系统会对处于安全区外的玩家造成持续的、无视护甲的真实伤害。

*   **医疗系统**
    *   重构了恢复机制，引入三种自定义医疗道具：绷带（恢复少量生命）、急救包（恢复至满血）、医疗箱（恢复全额生命与饱食度）。
    *   引入了打药动作时间与进度条提示。在恢复读条期间，玩家将被限制移动、攻击及物品栏操作，以提升竞技策略性。

*   **团队系统**
    *   支持自定义单排、双排、四排等多种队伍规模限制。
    *   提供可视化的大厅 GUI 供玩家自由组队，并在游戏开始时为未组队玩家执行自动分配算法。
    *   队伍成员将统一配备专属颜色的皮革护甲及 Tab 列表名称前缀，内置友军伤害豁免机制。

*   **战术投掷物**
    *   **破片手榴弹（TNT）**：支持右键投掷及延时起爆，对爆炸范围内的玩家造成经过算法放大的高额伤害。
    *   **燃烧弹（火焰弹）**：支持右键发射，触地即爆并生成持续燃烧区域，伤害随爆炸中心距离呈线性衰减。

*   **死亡遗物箱**
    *   玩家阵亡后，其物品栏内的所有掉落物将自动封装于生成的陷阱箱内，避免实体堆积造成的服务器性能损耗。
    *   系统将在遗物箱四周自动生成包含死者名称的告示牌，以作标识。

*   **自动化多世界管理**
    *   对局开始前，系统会自动基于 `world_backup` 模板文件夹克隆并加载名为 `game_1` 的独立游戏世界。
    *   对局结算结束后，系统将自动卸载并彻底清除该世界目录，确保每一轮游戏的初始地形均保持绝对纯净。

## 前置依赖

*   **[ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)** (必须): 插件的核心依赖组件，用于处理底层地图渲染数据包的发送与拦截。
*   多世界管理插件（如 Multiverse-Core）: 推荐配置，用于大厅世界的基础管理（核心游戏世界的生命周期由本插件独立接管）。

## 部署与配置

1.  将 `MCBG.jar` 及前置 `ProtocolLib.jar` 放入服务器的 `plugins` 目录下。
2.  确保服务器根目录中存在名为 `world_backup` 的世界文件夹（作为游戏地图模板）。
3.  确保 `world_backup` 存档的根目录内存在 `map.png` 文件，以保证地图正常渲染。
4.  启动服务器，系统将自动生成默认配置文件 `config.yml`。
5.  根据服务器实际需求，在 `config.yml` 中配置大厅坐标、岛屿边界、毒圈阶段参数及雷达比例尺等数据。
6.  重启服务器或使用指令 `/cd reload` 重载配置项以生效。

## 指令参考

### 管理员指令 (需 `mcbg.admin` 权限)
*   `/cd start` - 强制启动比赛（无视大厅倒计时与最低玩家人数限制）。
*   `/cd pause` - 暂停或恢复当前游戏状态的倒计时时钟。
*   `/cd skip` - 跳过当前阶段（大厅等待、缩圈等待等）的剩余倒计时。
*   `/cd stop` - 强制中断当前对局，立刻执行结算流程并回收地图世界。
*   `/cd settime <秒>` - 覆写当前游戏阶段的剩余倒计时。
*   `/cd resetmap` - 强制重新向全服在线玩家下发雷达地图数据包。
*   `/cd reload` - 重新读取并应用 `config.yml` 中的配置项。
*   `/cd cleansigns <存档名>` - 扫描并清理目标世界存档中遗留的死亡箱子及关联告示牌（执行时产生卡顿）。

### 玩家指令
*   `/hub` 或 `/lobby` - 退出当前对局并返回服务器大厅。

## 配置文件

`config.yml` 提供了深度的自定义空间，主要包含以下配置节点：
*   **lobby**: 游戏大厅重生点坐标、最低启动人数阈值及等待倒计时设置。
*   **flight**: 航线起点/终点生成半径、巡航高度、飞行移速及强制跳伞超时参数。
*   **team**: 队伍规模限制及大厅/游戏内团队标识（帽子）在玩家物品栏中的强制锁定槽位。
*   **zone**: 多阶段安全区半径数组、缩圈动画演出时间、扣血判定间隔与每次扣血数值。
*   **map**: 雷达地图的中心锚点世界坐标、缩放比例、岛屿渲染区域及战术网格大小。

## 架构与核心实现机制

本插件采用了高度模块化及极致优化的架构设计，以下是核心底层系统的深度技术剖析：

*   **1. 状态机与主事件循环 (GameManager & GameState)**
    *   **架构设计：** 游戏的整体生命周期由严格的枚举状态机（`LOBBY` -> `STARTING` -> `FLIGHT` -> `PARACHUTE` -> `INGAME` -> `ENDING`）进行控制。
    *   **实现细节：** 摒弃了容易导致内存泄漏和时序错乱的分散式定时器（Scheduler），采用唯一的核心 `BukkitRunnable` 作为 Main Game Tick。该主循环每秒/每 Tick 根据当前的 `GameState` 将逻辑精准分发给对应的子模块处理，确保了游戏阶段平滑过渡及高度稳定性。

*   **2. 高性能异步发包雷达 (PacketMapManager)**
    *   **架构设计：** 完全弃用极耗性能且限制繁多的原生 Bukkit `MapView` API。
    *   **实现细节：** 深度调用 `ProtocolLib`，直接向客户端下发 `PacketType.Play.Server.MAP` 数据包。系统为每位玩家分配虚拟的 Map ID（如 >10000），避免与游戏内实体地图冲突。
    *   **渲染算法：** 插件启动时使用 `ImageIO` 将 `map.png` 载入内存并转化为 Minecraft 特有的 `MapPalette` 颜色字节数组。地图的更新（如计算安全区圆周、绘制航线、标记队友）全部放在异步线程中进行，并引入节流（Throttle）机制（例如 250ms 一次刷新），确保在百人同屏对局中完全不阻塞主线程，实现 **0 TPS 损耗**。

*   **3. 极速动态多世界克隆 (WorldManager)**
    *   **架构设计：** 无需前置的独立世界管理方案，摆脱对 CoreProtect 等区块回档插件的依赖，实现真正彻底的地形重置。
    *   **实现细节：** 开局前通过 Java 原生 `File` I/O 递归深拷贝 `world_backup` 模板文件夹为 `game_1`。加载后强制关闭该世界的自动保存功能（`world.setAutoSave(false)`），杜绝运行期间无效的磁盘写入。结算完成后，强制卸载世界并删除 `game_1` 文件夹（连同 `uid.dat` 和 `session.lock` 缓存），做到纯净无残留的“用完即焚”。

*   **4. 物理飞行与高空滑翔模拟 (FlightManager)**
    *   **架构设计：** 航线飞行与跳伞系统完全基于纯数学三维向量（Vector）计算，无需生成真实载具实体。
    *   **实现细节：** 根据设定的半径随机生成飞行起点与终点。通过高频调度器对机舱内玩家执行 `Player#setVelocity()` 模拟平滑位移。
    *   **跨世界防拉回安全机制：** 为避免 Bukkit 和反作弊插件对跨世界传送产生位置判断异常（位置回档Bug），系统在传送玩家至游戏世界后强制引入 20 Tick 的安全静默期，随后再施加速度。跳伞时，系统接管原生重力，根据玩家视角（Yaw/Pitch）计算出水平方向的乘区（Glide Multiplier）和向下的空气阻力，实现极致顺滑的跳伞体验。

*   **5. 毒圈收缩与真伤算法 (ZoneManager / GameManager)**
    *   **架构设计：** 多阶段动态几何伤害判定。
    *   **实现细节：** 毒圈的收缩过程运用了**线性插值（Lerp）算法**：`当前半径 = 旧半径 - (旧半径 - 新半径) * (已流逝时间 / 总收缩时间)`，从而计算出每一 Tick 的精准范围。通过计算玩家坐标在 X/Z 平面上与地图中心点坐标的平面距离，判定是否身处毒圈。伤害结算直接操作 `Player#setHealth()` 而非 `p.damage()`，从底层越过所有原版护甲值和附魔减伤，实现硬核的无视护甲纯粹真实伤害。

*   **6. 强打断医疗系统 (HealingManager)**
    *   **实现细节：** 玩家使用医疗道具时，触发异步读条倒计时。系统会记录玩家施法的初始 `Location`，并在每个判定 Tick 内检查位移距离（`distanceSquared > 0.5`）以及是否遭受任何外界伤害（监听 `EntityDamageEvent`）。一旦触发打断条件，读条立刻销毁，逼迫玩家必须寻找掩体进行战术恢复。
