package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final EMWMBridge plugin;
    private final String CONFIG_VERSION = "1.2.0";

    public ConfigManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载并迁移配置
     */
    public void loadAndMigrate() {
        FileConfiguration config = plugin.getConfig();

        // 第一次加载，保存默认配置
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        // 检查版本并迁移
        String currentVersion = config.getString("config-version", "0.0.0");

        if (!currentVersion.equals(CONFIG_VERSION)) {
            plugin.getLogger().warning("检测到旧版本配置: " + currentVersion + " -> " + CONFIG_VERSION);
            migrateConfig(currentVersion);
        } else {
            plugin.getLogger().info("配置版本: " + CONFIG_VERSION);
        }

        // 验证必要字段
        validateConfig();
    }

    private void migrateConfig(String fromVersion) {
        plugin.getLogger().info("开始迁移配置...");

        // 备份原配置
        java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        java.io.File backupFile = new java.io.File(plugin.getDataFolder(), "config.yml.backup." + fromVersion);
        try {
            java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("已备份原配置到: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("备份配置失败: " + e.getMessage());
        }

        // 合并新配置
        InputStream stream = plugin.getResource("config.yml");
        if (stream == null) {
            plugin.getLogger().warning("找不到默认配置文件！");
            return;
        }

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        FileConfiguration currentConfig = plugin.getConfig();

        // 复制所有缺失的键
        copyMissingKeys(defaultConfig, currentConfig);

        // 设置新版本号
        currentConfig.set("config-version", CONFIG_VERSION);

        try {
            currentConfig.save(configFile);
            plugin.getLogger().info("配置迁移完成！新版本: " + CONFIG_VERSION);
        } catch (IOException e) {
            plugin.getLogger().severe("保存配置失败: " + e.getMessage());
        }
    }

    private void copyMissingKeys(FileConfiguration from, FileConfiguration to) {
        for (String key : from.getKeys(true)) {
            if (!to.contains(key)) {
                Object value = from.get(key);
                to.set(key, value);
                plugin.getLogger().info("  + 添加新键: " + key + " = " + value);
            }
        }
    }

    private void validateConfig() {
        FileConfiguration config = plugin.getConfig();
        boolean fixed = false;

        // 确保必要字段存在
        if (!config.contains("settings.debug")) {
            config.set("settings.debug", false);
            fixed = true;
        }

        if (!config.contains("tier-settings")) {
            config.set("tier-settings.scav.aggressiveness", 0.3);
            config.set("tier-settings.pmc.aggressiveness", 0.6);
            config.set("tier-settings.boss.aggressiveness", 0.9);
            fixed = true;
        }

        if (!config.contains("extreme-events")) {
            config.set("extreme-events.panic-mode-chance", 0.02);
            config.set("extreme-events.luck-shot-chance", 0.05);
            config.set("extreme-events.malfunction-chance", 0.03);
            config.set("extreme-events.tactical-mistake-chance", 0.08);
            config.set("extreme-events.adrenaline-chance", 0.10);
            config.set("extreme-events.adrenaline-hp-threshold", 0.25);
            fixed = true;
        }

        if (fixed) {
            try {
                config.save(new java.io.File(plugin.getDataFolder(), "config.yml"));
                plugin.getLogger().info("已修复缺失的配置字段");
            } catch (IOException e) {
                plugin.getLogger().warning("修复配置保存失败: " + e.getMessage());
            }
        }
    }

    public String getConfigVersion() {
        return CONFIG_VERSION;
    }
}
