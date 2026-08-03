package com.aermini.report.managers;

import com.aermini.report.AerReport;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final AerReport plugin;
    private FileConfiguration config;
    private FileConfiguration messageConfig;
    private File configFile;
    private File messageFile;
    private long cooldown;
    private Map<String, ReasonConfig> reasons;
    private List<String> specCommands;

    public ConfigManager(AerReport plugin) {
        this.plugin = plugin;
        this.reasons = new HashMap<>();
    }

    public void loadConfigs() {
        loadConfig();
        loadMessageConfig();
        loadReasons();
        loadSpecCommands();
    }

    private void loadConfig() {
        if (configFile == null) configFile=new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) plugin.saveResource("config.yml", false);
        config=YamlConfiguration.loadConfiguration(configFile);

        Reader defConfigStream = new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8);
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            config.setDefaults(defConfig);
        }
        cooldown = config.getLong("config.cooldown", 3000);
    }

    private void loadMessageConfig() {
        if (messageFile == null) messageFile=new File(plugin.getDataFolder(), "message.yml");
        if (!messageFile.exists()) plugin.saveResource("message.yml", false);
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        
        Reader defConfigStream = new InputStreamReader(plugin.getResource("message.yml"), StandardCharsets.UTF_8);
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            messageConfig.setDefaults(defConfig);
        }
    }

    private void loadReasons() {
        reasons.clear();
        ConfigurationSection reasonsSection = config.getConfigurationSection("reasons");
        if (reasonsSection == null) return;
        for (String reasonId : reasonsSection.getKeys(false)) {
            ConfigurationSection reasonSection = reasonsSection.getConfigurationSection(reasonId);
            if (reasonSection == null) continue;
            String type = reasonSection.getString("type", "behaviour");
            String name = reasonSection.getString("name", reasonId);
            String item = reasonSection.getString("item", "STONE");
            int data = reasonSection.getInt("data", 0);
            List<String> punish = reasonSection.getStringList("punish");
            reasons.put(reasonId, new ReasonConfig(reasonId, type, name, item, data, punish));
        }
    }

    private void loadSpecCommands() {
        specCommands = config.getStringList("config.teleport_commands");
        if (specCommands == null) specCommands=new ArrayList<>();
    }

    public void reloadConfigs() {
        loadConfigs();
        plugin.getLogger().info("配置文件已重载!");
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存配置文件 -> " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessageConfig() {
        return messageConfig;
    }

    public long getCooldown() {
        return cooldown;
    }

    public int getMaxRepeat() {
        return config.getInt("settings.max-repeat", 3);
    }

    public Map<String, ReasonConfig> getReasons() {
        return reasons;
    }

    public ReasonConfig getReason(String reasonId) {
        return reasons.get(reasonId);
    }

    public List<ReasonConfig> getReasonList() {
        List<ReasonConfig> list = new ArrayList<>();
        ConfigurationSection reasonsSection = config.getConfigurationSection("reasons");
        if (reasonsSection == null) return list;
        
        for (String reasonId : reasonsSection.getKeys(false)) {
            if (reasons.containsKey(reasonId)) list.add(reasons.get(reasonId));
        }
        return list;
    }

    public List<String> getSpecCommands() {
        return specCommands;
    }

    public String getMessage(String path) {
        String message = messageConfig.getString(path, "");
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return message;
    }

    public MenuConfig getMenuConfig(String menuName) {
        ConfigurationSection section = config.getConfigurationSection("menu." + menuName);
        if (section == null) return null;
        return new MenuConfig(section);
    }

    public static class ReasonConfig {
        private final String id;
        private final String type;
        private final String name;
        private final String item;
        private final int data;
        private final List<String> punish;

        public ReasonConfig(String id, String type, String name, String item, int data, List<String> punish) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.item = item;
            this.data = data;
            this.punish = punish;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getName() { return name; }
        public String getItem() { return item; }
        public int getData() { return data; }
        public List<String> getPunish() { return punish; }
    }

    public static class MenuConfig {
        private final ConfigurationSection section;

        public MenuConfig(ConfigurationSection section) {
            this.section = section;
        }

        public int getSlot() {
            return section.getInt("slot", 54);
        }

        public String getTitle() {
            return ChatColor.translateAlternateColorCodes('&', section.getString("title", "菜单"));
        }

        public ConfigurationSection getSection() {
            return section;
        }

        public List<Integer> getSlots(String itemName) {
            List<Integer> slots = new ArrayList<>();
            
            String slotPath = itemName + ".slot";
            Object rawValue = section.get(slotPath);
            if (rawValue == null) {
                ConfigurationSection itemSection = section.getConfigurationSection(itemName);
                if (itemSection != null) {
                    rawValue = itemSection.get("slot");
                }
            }
            
            List<Integer> slotList = section.getIntegerList(slotPath);
            if (!slotList.isEmpty()) {
                slots.addAll(slotList);
                return slots;
            }
            
            ConfigurationSection itemSection = section.getConfigurationSection(itemName);
            if (itemSection != null) {
                List<Integer> itemSlotList = itemSection.getIntegerList("slot");
                if (!itemSlotList.isEmpty()) {
                    slots.addAll(itemSlotList);
                    return slots;
                }
                
                int singleSlot = itemSection.getInt("slot", -1);
                if (singleSlot >= 0) {
                    slots.add(singleSlot);
                    return slots;
                }
            }
            
            int singleSlot = section.getInt(slotPath, -1);
            if (singleSlot >= 0) {
                slots.add(singleSlot);
                return slots;
            }
            
            List<String> stringList = section.getStringList(slotPath);
            if (!stringList.isEmpty()) {
                for (String s : stringList) {
                    try {
                        slots.add(Integer.parseInt(s));
                    } catch (NumberFormatException ignored) {}
                }
                return slots;
            }
            return slots;
        }

        public String getItem(String itemName) {
            Object itemObj = section.get(itemName + ".item");
            if (itemObj == null) return null;
            if (itemObj instanceof String) {
                return (String) itemObj;
            } else if (itemObj instanceof ConfigurationSection) {
                return section.getString(itemName + ".item.normal", "DIAMOND");
            }
            return section.getString(itemName + ".item", "STONE");
        }

        public String getItem(String itemName, String state) {
            Object itemObj = section.get(itemName + ".item");
            if (itemObj == null) return null;
            if (itemObj instanceof ConfigurationSection) {
                return section.getString(itemName + ".item." + state,
                        section.getString(itemName + ".item.normal", "DIAMOND"));
            }
            return section.getString(itemName + ".item", "STONE");
        }

        public int getData(String itemName) {
            return section.getInt(itemName + ".data", 0);
        }

        public int getData(String itemName, String state) {
            Object itemObj = section.get(itemName + ".item");
            if (itemObj instanceof ConfigurationSection) {
                return section.getInt(itemName + ".data." + state, 0);
            }
            return section.getInt(itemName + ".data", 0);
        }

        public String getName(String itemName) {
            return ChatColor.translateAlternateColorCodes('&', section.getString(itemName + ".name", itemName));
        }

        public List<String> getLore(String itemName) {
            Object loreObj = section.get(itemName + ".lore");
            List<String> coloredLore = new ArrayList<>();
            
            if (loreObj instanceof String) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', (String) loreObj));
            } else if (loreObj instanceof List) {
                List<String> lore = section.getStringList(itemName + ".lore");
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
            return coloredLore;
        }
    }

    public List<String> getReturnServerWhitelist() {
        return config.getStringList("settings.return-server-whitelist");
    }

    public List<String> getExitObserverCommands() {
        return config.getStringList("settings.exit-observer-commands");
    }
}
