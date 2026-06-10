package edu.mc;

import edu.mc.manager.GameManager;
import edu.mc.state.GameState;
import edu.mc.map.PacketMapManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Constructor;

public final class ChickenDinnerPlugin extends JavaPlugin {

    private GameState currentState = GameState.LOBBY;
    private GameManager gameManager;
    private edu.mc.manager.PlayerManager playerManager;
    private edu.mc.manager.DataManager dataManager;
    private edu.mc.manager.LootManager lootManager;
    private edu.mc.manager.HealingManager healingManager;
    private edu.mc.manager.AirdropManager airdropManager;
    private edu.mc.manager.ZoneManager zoneManager;
    private edu.mc.manager.ScatterManager scatterManager;
    private edu.mc.manager.FlightManager flightManager;
    private edu.mc.manager.ScoreboardManager scoreboardManager;
    private PacketMapManager packetMapManager;
    private edu.mc.manager.TeamManager teamManager;
    private edu.mc.manager.WorldManager worldManager;

    private static String NMS_PACKAGE = null;

    public static String getNmsPackage() {
        if (NMS_PACKAGE == null) {
            NMS_PACKAGE = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        }
        return NMS_PACKAGE;
    }

    // Removed old onLoad logic that overwritten default world

    @Override
    public void onEnable() {
        saveDefaultConfig();
        GameConfig.load(getConfig());

        org.bukkit.World mainWorld = Bukkit.getWorlds().get(0);
        mainWorld.setSpawnLocation((int) GameConfig.LOBBY_X, (int) GameConfig.LOBBY_Y, (int) GameConfig.LOBBY_Z);
        mainWorld.getWorldBorder().reset();
        mainWorld.setGameRuleValue("doMobSpawning", "false");
        mainWorld.setGameRuleValue("mobGriefing", "false");
        mainWorld.setGameRuleValue("doDaylightCycle", "false");
        mainWorld.setGameRuleValue("doFireTick", "false");
        mainWorld.setTime(6000);
        mainWorld.setStorm(false);
        mainWorld.setDifficulty(org.bukkit.Difficulty.NORMAL);

        this.playerManager = new edu.mc.manager.PlayerManager();
        this.dataManager = new edu.mc.manager.DataManager(this);
        this.lootManager = new edu.mc.manager.LootManager(this);
        this.healingManager = new edu.mc.manager.HealingManager(this);
        this.airdropManager = new edu.mc.manager.AirdropManager(this);
        this.scatterManager = new edu.mc.manager.ScatterManager();
        this.flightManager = new edu.mc.manager.FlightManager(this);
        this.scoreboardManager = new edu.mc.manager.ScoreboardManager(this);
        this.scoreboardManager.start();
        this.packetMapManager = new PacketMapManager(this);
        // 【修改】服务器启动加载时，立即清理并重置残留的地图数据文件，实现重启重新绘制
        this.packetMapManager.clearAndResetMapFiles();

        this.worldManager = new edu.mc.manager.WorldManager(this);
        this.gameManager = new GameManager(this);
        this.teamManager = new edu.mc.manager.TeamManager();

        Bukkit.getPluginManager().registerEvents(new edu.mc.listener.GameListener(this), this);
        Bukkit.getPluginManager().registerEvents(new edu.mc.listener.TeamListener(this), this);
        getCommand("chickendinner").setExecutor(new edu.mc.command.GameCommand(this)); // 注册游戏核心命令
        getCommand("hub").setExecutor(new edu.mc.command.HubCommand(this));
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getLogger().info("MCBG 1.8.9 \u6838\u5fc3\u5df2\u542f\u52a8\uff01");
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null)
            scoreboardManager.stop();
        if (gameManager != null)
            gameManager.stopTimer();
        if (airdropManager != null)
            airdropManager.reset();
        if (packetMapManager != null)
            packetMapManager.stop();
        getLogger().info("MCBG \u6838\u5fc3\u5df2\u5b89\u5168\u5378\u8f7d\u3002");
    }

    // ==================== Managers ====================
    public edu.mc.manager.PlayerManager getPlayerManager() {
        return playerManager;
    }

    public edu.mc.manager.DataManager getDataManager() {
        return dataManager;
    }

    public edu.mc.manager.LootManager getLootManager() {
        return lootManager;
    }

    public edu.mc.manager.HealingManager getHealingManager() {
        return healingManager;
    }

    public edu.mc.manager.AirdropManager getAirdropManager() {
        return airdropManager;
    }

    public edu.mc.manager.ZoneManager getZoneManager() {
        return zoneManager;
    }

    public edu.mc.manager.ScatterManager getScatterManager() {
        return scatterManager;
    }

    public edu.mc.manager.FlightManager getFlightManager() {
        return flightManager;
    }

    public edu.mc.manager.ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public edu.mc.manager.TeamManager getTeamManager() {
        return teamManager;
    }

    public PacketMapManager getPacketMapManager() {
        return packetMapManager;
    }

    public edu.mc.manager.WorldManager getWorldManager() {
        return worldManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public void initZoneManager() {
        this.zoneManager = new edu.mc.manager.ZoneManager(this, Bukkit.getWorld("game_1"));
        this.zoneManager.initBorder();
    }

    public void clearZoneManager() {
        if (this.zoneManager != null) {
            this.zoneManager.reset();
            this.zoneManager = null;
        }
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(GameState newState) {
        this.currentState = newState;
        getLogger().info("[\u72b6\u6001\u673a] \u6e38\u620f\u72b6\u6001\u53d8\u66f4\u81f3: " + newState.name());
    }

    // ==================== NMS 反射 ====================
    private Class<?> packetTitleCls;
    private Class<?> chatSerializerCls;
    private Class<?> iChatBaseComponentCls;
    private Class<?> enumTitleActionCls;
    private Class<?> packetCls;
    private Constructor<?> timeConstructor;
    private Constructor<?> titleConstructor;
    private java.lang.reflect.Method chatSerializerMethod;
    private java.lang.reflect.Method getHandleMethod;
    private java.lang.reflect.Field playerConnectionField;
    private java.lang.reflect.Method sendPacketMethod;
    private Object titleActionEnum;
    private Object subtitleActionEnum;
    private boolean reflectionInitialized = false;

    private void initReflection() {
        if (reflectionInitialized)
            return;
        try {
            String nmsPackage = getNmsPackage();
            packetTitleCls = Class.forName("net.minecraft.server." + nmsPackage + ".PacketPlayOutTitle");
            // 【修复】1.8.8 中 ChatSerializer 是 IChatBaseComponent 的内部类，使用 $ 进行查找以避免 ClassNotFoundException
            chatSerializerCls = Class.forName("net.minecraft.server." + nmsPackage + ".IChatBaseComponent$ChatSerializer");
            iChatBaseComponentCls = Class.forName("net.minecraft.server." + nmsPackage + ".IChatBaseComponent");
            // 【修复】1.8.8 中 EnumTitleAction 是 PacketPlayOutTitle 的内部类，使用 $ 进行查找以避免 NoSuchFieldException
            enumTitleActionCls = Class.forName("net.minecraft.server." + nmsPackage + ".PacketPlayOutTitle$EnumTitleAction");
            packetCls = Class.forName("net.minecraft.server." + nmsPackage + ".Packet");

            timeConstructor = packetTitleCls.getConstructor(int.class, int.class, int.class);
            chatSerializerMethod = chatSerializerCls.getMethod("a", String.class);
            titleActionEnum = enumTitleActionCls.getField("TITLE").get(null);
            subtitleActionEnum = enumTitleActionCls.getField("SUBTITLE").get(null);
            titleConstructor = packetTitleCls.getConstructor(enumTitleActionCls, iChatBaseComponentCls);

            Class<?> craftPlayerCls = Class.forName("org.bukkit.craftbukkit." + nmsPackage + ".entity.CraftPlayer");
            getHandleMethod = craftPlayerCls.getMethod("getHandle");
            Class<?> entityPlayerCls = Class.forName("net.minecraft.server." + nmsPackage + ".EntityPlayer");
            playerConnectionField = entityPlayerCls.getField("playerConnection");
            Class<?> playerConnectionCls = Class.forName("net.minecraft.server." + nmsPackage + ".PlayerConnection");
            sendPacketMethod = playerConnectionCls.getMethod("sendPacket", packetCls);

            reflectionInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            initReflection();
            if (!reflectionInitialized)
                return;
            Object timePacket = timeConstructor.newInstance(fadeIn, stay, fadeOut);
            sendPacketToPlayer(player, timePacket);
            if (title != null) {
                Object titleComponent = chatSerializerMethod.invoke(null, "{\"text\":\"" + title + "\"}");
                Object titlePacket = titleConstructor.newInstance(titleActionEnum, titleComponent);
                sendPacketToPlayer(player, titlePacket);
            }
            if (subtitle != null) {
                Object subtitleComponent = chatSerializerMethod.invoke(null, "{\"text\":\"" + subtitle + "\"}");
                Object subtitlePacket = titleConstructor.newInstance(subtitleActionEnum, subtitleComponent);
                sendPacketToPlayer(player, subtitlePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendActionBar(Player player, String message) {
        initReflection();
        if (!reflectionInitialized)
            return;
        try {
            Object handle = getHandleMethod.invoke(player);
            Object connection = playerConnectionField.get(handle);
            Object chatComponent = chatSerializerMethod.invoke(null, "{\"text\": \"" + message + "\"}");
            Class<?> packetChatCls = Class.forName("net.minecraft.server." + getNmsPackage() + ".PacketPlayOutChat");
            Object packet = packetChatCls.getConstructor(iChatBaseComponentCls, byte.class).newInstance(chatComponent,
                    (byte) 2);
            sendPacketMethod.invoke(connection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * \u5bf9\u5916\u5f00\u653e\u7684\u53d1\u5305\u65b9\u6cd5\uff0c\u4f9b
     * PacketMapManager \u7b49\u5176\u4ed6\u7c7b\u8c03\u7528
     */
    public void sendPacketToPlayer(Player player, Object packet) {
        try {
            initReflection();
            if (!reflectionInitialized)
                return;
            Object handle = getHandleMethod.invoke(player);
            Object playerConnection = playerConnectionField.get(handle);
            sendPacketMethod.invoke(playerConnection, packet);
        } catch (Exception e) {
            // silent
        }
    }
}
