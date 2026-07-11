package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.engine.TarkovAIEngine;
import com.emwbridge.ai.squad.SquadManager;
import com.emwbridge.managers.TarkovAIManager;
import com.magmaguy.elitemobs.api.ElitePhaseSwitchEvent;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * 需求8 Boss 协同召唤（M5 P2）。
 *
 * <p>监听 EliteMobs 的 {@link ElitePhaseSwitchEvent}（Boss 阶段切换），当 Boss 血量降到配置阈值时，
 * 在 Boss 周围召唤若干 minion，并将它们经需求2 的编制 API 编入命名编制。
 *
 * <p>阵营继承（8.2）：minion 须同时是 EMWM 怪物配置名，生成时自动经 {@code EliteMobSpawnListener}
 * 按名匹配绑定，从而获得 需求1 阵营判定 / AI / 护甲 —— 本类不重复实现阵营逻辑（薄胶水）。
 *
 * <p>可单测的纯逻辑：{@link #resolveRule(String)}、{@link #shouldSummon(CoordinationRule, double, long, long)}、
 * {@link #computeSpawnLocation(Location, int, int)}。依赖 EliteMobs 的召唤与 Bukkit 调度仅在运行时调用，
 * 不在单测范围（Mockito-only 环境无法实例化 EliteMobs 类）。
 */
public class BossCoordinationManager implements Listener {

    /** Boss 配置名（小写）→ 协同规则。包级可见以便单测直接注入。 */
    final Map<String, CoordinationRule> rules = new HashMap<>();
    /** 同一 Boss 上次召唤时间（毫秒），用于冷却防刷。 */
    private final Map<UUID, Long> lastSummonMillis = new HashMap<>();

    private final EMWMBridge plugin;

    public BossCoordinationManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        rules.clear();
        lastSummonMillis.clear();
        try {
            File dataFolder = plugin.getDataFolder();
            if (dataFolder == null) return;
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File file = new File(dataFolder, "boss-coordination.yml");
            if (!file.exists()) {
                plugin.saveResource("boss-coordination.yml", false);
            }
            if (!file.exists()) return;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            ConfigurationSection coords = yaml.getConfigurationSection("coordinations");
            if (coords != null) {
                for (String key : coords.getKeys(false)) {
                    ConfigurationSection r = coords.getConfigurationSection(key);
                    if (r == null) continue;
                    String boss = r.getString("boss");
                    if (boss == null) continue;
                    CoordinationRule rule = new CoordinationRule(
                            boss,
                            r.getString("minion"),
                            r.getString("squad"),
                            r.getInt("count", 4),
                            r.getDoubleList("triggerHealthPercent"),
                            r.getInt("cooldownSeconds", 30));
                    rules.put(boss.toLowerCase(), rule);
                }
            }
            plugin.getLogger().info("[BossCoordination] 已加载 " + rules.size() + " 条 Boss 协同规则");
        } catch (Exception e) {
            // 加载失败（文件缺失/损坏，或测试环境 mock）时静默关闭协同，不影响插件启动
            rules.clear();
            try {
                plugin.getLogger().warning("[BossCoordination] 加载 boss-coordination.yml 失败，Boss 协同关闭: " + e.getMessage());
            } catch (Exception ignored) {
                // 连 logger 都不可用（极端 mock 环境）则忽略
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossPhaseSwitch(ElitePhaseSwitchEvent event) {
        CustomBossEntity boss = event.getCustomBossEntity();
        if (boss == null) return;
        LivingEntity bossEntity = boss.getLivingEntity();
        if (bossEntity == null || !bossEntity.isValid()) return;

        CoordinationRule rule = resolveRule(boss.getName());
        if (rule == null) return;

        double hpFraction = bossEntity.getMaxHealth() > 0
                ? bossEntity.getHealth() / bossEntity.getMaxHealth() : 1.0;

        long now = System.currentTimeMillis();
        long last = lastSummonMillis.getOrDefault(bossEntity.getUniqueId(), 0L);
        if (!shouldSummon(rule, hpFraction, now, last)) return;

        lastSummonMillis.put(bossEntity.getUniqueId(), now);
        spawnWave(rule, bossEntity.getLocation(), boss.getName());
    }

    /**
     * 在 Boss 周围环形召唤 minion 波次，并编入命名编制（8.1）。运行时调用。
     */
    void spawnWave(CoordinationRule rule, Location bossLoc, String bossName) {
        if (bossLoc == null) return;
        for (int i = 0; i < rule.count; i++) {
            Location loc = computeSpawnLocation(bossLoc, i, rule.count);
            try {
                CustomBossEntity minion = CustomBossEntity.createCustomBossEntity(rule.minion);
                if (minion == null) {
                    plugin.getLogger().warning("[BossCoordination] 未找到 minion 配置: " + rule.minion);
                    continue;
                }
                minion.spawn(loc, false);
                joinSquadLater(minion, rule);
            } catch (Exception e) {
                plugin.getLogger().warning("[BossCoordination] 召唤 minion 失败: " + rule.minion + " - " + e.getMessage());
            }
        }
        plugin.getLogger().info("[BossCoordination] Boss " + bossName + " 阶段切换，召唤 " + rule.count + " 名协同 (" + rule.minion + ")");
    }

    /**
     * 8.1：调编制生成 API，将协同单位编入指定命名编制。下一 tick 执行（等其完成 EMWM 绑定）。
     * 阵营继承由 EliteMobSpawnListener 名称匹配自动完成（8.2），此处仅编队。
     */
    private void joinSquadLater(CustomBossEntity minion, CoordinationRule rule) {
        if (rule.squad == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            LivingEntity me = minion.getLivingEntity();
            if (me == null || !me.isValid()) return;
            SquadManager sm = getSquadManager();
            if (sm == null) return;
            // tier/personality 传 null：由 SquadManager 按配额随机分配角色（assignRole 对 null 安全）
            sm.tryJoin(me, null, null, rule.squad);
        }, 1L);
    }

    private SquadManager getSquadManager() {
        TarkovAIManager ai = plugin.getTarkovAIManager();
        if (ai == null) return null;
        TarkovAIEngine engine = ai.getEngine();
        return engine != null ? engine.getSquadManager() : null;
    }

    // ==================== 纯逻辑（可单测）====================

    /** 按 Boss 配置名（小写）解析协同规则；不存在返回 null。 */
    CoordinationRule resolveRule(String bossName) {
        if (bossName == null) return null;
        return rules.get(bossName.toLowerCase());
    }

    /**
     * 是否应召唤：触发阈值命中 且 不在冷却期内。
     *
     * @param hpFraction  当前 Boss 血量比例 [0,1]
     * @param nowMillis   当前时间（毫秒）
     * @param lastMillis  该 Boss 上次召唤时间（毫秒，0 表示从未）
     */
    boolean shouldSummon(CoordinationRule rule, double hpFraction, long nowMillis, long lastMillis) {
        if (!triggers(rule, hpFraction)) return false;
        if (rule.cooldownSeconds > 0 && (nowMillis - lastMillis) < (long) rule.cooldownSeconds * 1000L) return false;
        return true;
    }

    /** 触发阈值判定：无阈值配置 = 任意阶段切换都触发；否则血量比例 <= 任一阈值即触发。 */
    boolean triggers(CoordinationRule rule, double hpFraction) {
        if (rule.triggerHealthPercent == null || rule.triggerHealthPercent.isEmpty()) return true;
        for (double t : rule.triggerHealthPercent) {
            if (hpFraction <= t + 1e-6) return true;
        }
        return false;
    }

    /**
     * 在 Boss 周围环形分布生成点（半径 3 格）。world 透传（可 null，便于单测）。
     */
    Location computeSpawnLocation(Location bossLoc, int index, int count) {
        double angle = (2.0 * Math.PI * index) / Math.max(1, count);
        double radius = 3.0;
        double dx = Math.cos(angle) * radius;
        double dz = Math.sin(angle) * radius;
        return new Location(bossLoc.getWorld(), bossLoc.getX() + dx, bossLoc.getY(), bossLoc.getZ() + dz);
    }

    /** Boss 协同规则（不可变）。 */
    static class CoordinationRule {
        final String boss;
        final String minion;
        final String squad;
        final int count;
        final List<Double> triggerHealthPercent;
        final int cooldownSeconds;

        CoordinationRule(String boss, String minion, String squad, int count,
                         List<Double> triggerHealthPercent, int cooldownSeconds) {
            this.boss = boss;
            this.minion = minion;
            this.squad = squad;
            this.count = count;
            this.triggerHealthPercent = triggerHealthPercent;
            this.cooldownSeconds = cooldownSeconds;
        }
    }
}
