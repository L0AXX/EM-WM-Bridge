package com.emwbridge.utils;

import com.emwbridge.EMWMBridge;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DebugManager {

    private final EMWMBridge plugin;
    private DebugLevel globalLevel;
    private final Map<UUID, DebugLevel> entityLevels;
    private final Map<String, Long> spamCooldowns;
    private final Map<String, Integer> counters;

    public enum DebugLevel {
        OFF(0, "关闭"),
        BASIC(1, "基础"),
        DETAILED(2, "详细"),
        TRACE(3, "追踪");

        private final int level;
        private final String name;

        DebugLevel(int level, String name) {
            this.level = level;
            this.name = name;
        }

        public int getLevel() { return level; }
        public String getName() { return name; }
    }

    public DebugManager(EMWMBridge plugin) {
        this.plugin = plugin;
        this.entityLevels = new HashMap<>();
        this.spamCooldowns = new HashMap<>();
        this.counters = new HashMap<>();
        reload();
    }

    public void reload() {
        String levelName = plugin.getConfig().getString("settings.debug-level", "BASIC");
        try {
            this.globalLevel = DebugLevel.valueOf(levelName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.globalLevel = DebugLevel.BASIC;
        }

        boolean enabled = plugin.getConfig().getBoolean("settings.debug", true);
        if (!enabled) {
            this.globalLevel = DebugLevel.OFF;
        }

        plugin.getLogger().info("DEBUG等级: " + globalLevel.name + " (Level " + globalLevel.level + ")");
    }

    /**
     * 基础调试日志
     */
    public void debug(String message) {
        debug(message, DebugLevel.BASIC);
    }

    /**
     * 分级调试日志
     */
    public void debug(String message, DebugLevel minLevel) {
        if (globalLevel.level < minLevel.level) return;

        long now = System.currentTimeMillis();
        String key = message + "_" + (now / 1000);

        // 防刷屏
        Long lastTime = spamCooldowns.get(message);
        if (lastTime != null && now - lastTime < 500) {
            return;
        }
        spamCooldowns.put(message, now);

        plugin.getLogger().info("[DEBUG:" + minLevel.name + "] " + message);
    }

    /**
     * 针对特定实体的调试日志
     */
    public void debugEntity(LivingEntity entity, String message) {
        debugEntity(entity, message, DebugLevel.BASIC);
    }

    public void debugEntity(LivingEntity entity, String message, DebugLevel minLevel) {
        DebugLevel entityLevel = entityLevels.get(entity.getUniqueId());
        DebugLevel effectiveLevel = entityLevel != null ? entityLevel : globalLevel;

        if (effectiveLevel.level < minLevel.level) return;

        String entityName = entity.getCustomName() != null ? entity.getCustomName() : entity.getType().name();
        debug("[" + entityName + "] " + message, minLevel);
    }

    /**
     * 战术调试 - 显示AI决策
     */
    public void debugTactic(String entityName, String tactic, String reason) {
        if (globalLevel.level < DebugLevel.DETAILED.level) return;
        plugin.getLogger().info("[TACTIC] " + entityName + " -> " + tactic + " (" + reason + ")");
    }

    /**
     * 事件调试 - 显示极限事件
     */
    public void debugEvent(String entityName, String eventType, double intensity) {
        if (globalLevel.level < DebugLevel.TRACE.level) return;
        plugin.getLogger().info("[EVENT:" + eventType + "] " + entityName + " intensity=" + String.format("%.2f", intensity));
    }

    /**
     * 性能调试 - 显示计时信息
     */
    public void debugPerformance(String operation, long startTime) {
        if (globalLevel.level < DebugLevel.TRACE.level) return;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 10) { // 只记录超过10ms的操作
            plugin.getLogger().info("[PERF] " + operation + " took " + elapsed + "ms");
        }
    }

    /**
     * 计数器 - 用于统计某个事件发生次数
     */
    public int incrementCounter(String name) {
        int count = counters.getOrDefault(name, 0) + 1;
        counters.put(name, count);
        return count;
    }

    public int getCounter(String name) {
        return counters.getOrDefault(name, 0);
    }

    public void resetCounter(String name) {
        counters.remove(name);
    }

    public void resetAllCounters() {
        counters.clear();
    }

    public Map<String, Integer> getAllCounters() {
        return new HashMap<>(counters);
    }

    /**
     * 设置特定实体的调试等级
     */
    public void setEntityDebugLevel(UUID uuid, DebugLevel level) {
        if (level == null) {
            entityLevels.remove(uuid);
        } else {
            entityLevels.put(uuid, level);
        }
    }

    /**
     * 获取实体调试等级
     */
    public DebugLevel getEntityDebugLevel(UUID uuid) {
        return entityLevels.getOrDefault(uuid, globalLevel);
    }

    /**
     * 获取全局调试等级
     */
    public DebugLevel getGlobalDebugLevel() {
        return globalLevel;
    }

    /**
     * 生成调试报告
     */
    public String generateDebugReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EM-WM-Bridge Debug Report ===\n");
        sb.append("Global Level: ").append(globalLevel.name).append("\n");
        sb.append("Tracked Entities: ").append(entityLevels.size()).append("\n");
        sb.append("\nCounters:\n");
        for (Map.Entry<String, Integer> entry : counters.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}
