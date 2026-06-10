package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DataManager {

    private final ChickenDinnerPlugin plugin;
    private FileConfiguration config;

    // Loot Config
    public int lootChestMinItems;
    public int lootChestMaxItems;
    public static class LootEntry {
        public Material material;
        public int weight;
        public int minAmount = 1;
        public int maxAmount = 1;
        public java.util.Map<org.bukkit.enchantments.Enchantment, java.util.Map<Integer, Integer>> enchants = new java.util.HashMap<>();
        public boolean randomProtection;
        public java.util.Map<Integer, Integer> protectionLevels = new java.util.HashMap<>();
    }

    public List<LootEntry> lootEntries = new ArrayList<>();
    public int totalLootWeight = 0;

    // Healing Config
    public Material bandageMaterial;
    public String bandageName;
    public int bandageUseTicks;
    public double bandageHealRatio;

    public Material medkitMaterial;
    public String medkitName;
    public int medkitUseTicks;
    public double medkitHealRatio;

    public Material medicalBoxMaterial;
    public String medicalBoxName;
    public int medicalBoxUseTicks;
    public List<String> medicalBoxLore;

    // Airdrop Config
    public int airdropChanceMedicalBox;
    public int airdropChanceBow;
    public List<Material> airdropGuaranteed;
    public Material airdropBowMaterial;
    public int airdropBowEnchantDamage;
    public int airdropBowEnchantInfinite;
    public Material airdropSwordMaterial;
    public int airdropSwordEnchantDamage;

    public DataManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) {
            plugin.saveResource("items.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Loot
        lootChestMinItems = config.getInt("loot.chest.min-items", 3);
        lootChestMaxItems = config.getInt("loot.chest.max-items", 7);
        lootEntries.clear();
        totalLootWeight = 0;
        if (config.contains("loot.entries")) {
            for (String key : config.getConfigurationSection("loot.entries").getKeys(false)) {
                String path = "loot.entries." + key;
                try {
                    LootEntry entry = new LootEntry();
                    entry.material = Material.valueOf(config.getString(path + ".material", "DIRT").toUpperCase());
                    entry.weight = config.getInt(path + ".weight", 10);
                    entry.minAmount = config.getInt(path + ".min-amount", 1);
                    entry.maxAmount = config.getInt(path + ".max-amount", 1);
                    
                    if (config.contains(path + ".enchants")) {
                        for (String enchKey : config.getConfigurationSection(path + ".enchants").getKeys(false)) {
                            org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByName(enchKey.toUpperCase());
                            if (ench != null) {
                                java.util.Map<Integer, Integer> levelWeights = new java.util.HashMap<>();
                                for (String levelStr : config.getConfigurationSection(path + ".enchants." + enchKey).getKeys(false)) {
                                    levelWeights.put(Integer.parseInt(levelStr), config.getInt(path + ".enchants." + enchKey + "." + levelStr));
                                }
                                entry.enchants.put(ench, levelWeights);
                            }
                        }
                    }
                    
                    entry.randomProtection = config.getBoolean(path + ".random_protection", false);
                    if (entry.randomProtection && config.contains(path + ".protection_levels")) {
                        for (String levelStr : config.getConfigurationSection(path + ".protection_levels").getKeys(false)) {
                            entry.protectionLevels.put(Integer.parseInt(levelStr), config.getInt(path + ".protection_levels." + levelStr));
                        }
                    }
                    
                    lootEntries.add(entry);
                    totalLootWeight += entry.weight;
                } catch (Exception e) {
                    plugin.getLogger().warning("Error parsing loot entry: " + key);
                }
            }
        }

        // Healing
        bandageMaterial = Material.valueOf(config.getString("healing.bandage.material", "PAPER").toUpperCase());
        bandageName = ChatColor.translateAlternateColorCodes('&', config.getString("healing.bandage.name", "&a绷带"));
        bandageUseTicks = config.getInt("healing.bandage.use-time-ticks", 60);
        bandageHealRatio = config.getDouble("healing.bandage.heal-ratio", 0.125);

        medkitMaterial = Material.valueOf(config.getString("healing.medkit.material", "COOKED_CHICKEN").toUpperCase());
        medkitName = ChatColor.translateAlternateColorCodes('&', config.getString("healing.medkit.name", "&c急救鸡"));
        medkitUseTicks = config.getInt("healing.medkit.use-time-ticks", 100);
        medkitHealRatio = config.getDouble("healing.medkit.heal-ratio", 0.5);

        medicalBoxMaterial = Material.valueOf(config.getString("healing.medical-box.material", "CHEST").toUpperCase());
        medicalBoxName = ChatColor.translateAlternateColorCodes('&', config.getString("healing.medical-box.name", "&d&l医疗箱"));
        medicalBoxUseTicks = config.getInt("healing.medical-box.use-time-ticks", 60);
        medicalBoxLore = new ArrayList<>();
        for (String loreLine : config.getStringList("healing.medical-box.lore")) {
            medicalBoxLore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
        }

        // Airdrop
        airdropChanceMedicalBox = config.getInt("airdrop.chance.medical-box", 50);
        airdropChanceBow = config.getInt("airdrop.chance.bow", 50);
        
        airdropGuaranteed = new ArrayList<>();
        for (String matStr : config.getStringList("airdrop.guaranteed")) {
            try {
                airdropGuaranteed.add(Material.valueOf(matStr.toUpperCase()));
            } catch (Exception e) {
                plugin.getLogger().warning("Unknown material in airdrop.guaranteed: " + matStr);
            }
        }

        airdropBowMaterial = Material.valueOf(config.getString("airdrop.weapons.bow.material", "BOW").toUpperCase());
        airdropBowEnchantDamage = config.getInt("airdrop.weapons.bow.enchants.ARROW_DAMAGE", 5);
        airdropBowEnchantInfinite = config.getInt("airdrop.weapons.bow.enchants.ARROW_INFINITE", 1);
        
        airdropSwordMaterial = Material.valueOf(config.getString("airdrop.weapons.sword.material", "DIAMOND_SWORD").toUpperCase());
        airdropSwordEnchantDamage = config.getInt("airdrop.weapons.sword.enchants.DAMAGE_ALL", 5);
    }
}
