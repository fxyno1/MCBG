package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageManager {

    private final ChickenDinnerPlugin plugin;
    private FileConfiguration config;

    public MessageManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        try {
            config = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            config = new YamlConfiguration();
        }
    }

    public String getMessage(String path) {
        String msg = config.getString(path);
        if (msg == null) {
            return "§cMessage not found: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String msg = config.getString(path);
        if (msg == null) {
            return "§cMessage not found: " + path;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public List<String> getStringList(String path) {
        List<String> list = config.getStringList(path);
        List<String> coloredList = new ArrayList<>();
        for (String str : list) {
            coloredList.add(ChatColor.translateAlternateColorCodes('&', str));
        }
        return coloredList;
    }
}
