package com.emwbridge.ai.faction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionManager {

    // ===== 旧版 Tarkov 枚举阵营（无 emwm_factions.yml 时回退使用）=====
    private final Map<UUID, TarkovFaction> entityFactions = new ConcurrentHashMap<>();
    private final HostilityMatrix matrix;

    // ===== 新版 GreyZone 可配置字符串阵营系统 =====
    private final Map<String, FactionProfile> factionsById = new ConcurrentHashMap<>();
    private final Map<UUID, String> entityFactionId = new ConcurrentHashMap<>();

    public FactionManager() {
        this.matrix = new HostilityMatrix();
    }

    // ===================== 加载 emwm_factions.yml =====================

    /**
     * 加载 plugins/EM-WM-Bridge/emwm_factions.yml 中的阵营定义。
     * 文件不存在时仅拷贝默认资源，不做任何解析（factionsById 为空 → 回退枚举）。
     */
    public void load(JavaPlugin plugin) {
        try {
            factionsById.clear();
            File dataFolder = plugin.getDataFolder();
            if (dataFolder == null) {
                return;
            }
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File file = new File(dataFolder, "emwm_factions.yml");
            if (!file.exists()) {
                plugin.saveResource("emwm_factions.yml", false);
            }
            if (!file.exists()) {
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yaml.getConfigurationSection("factions");
            if (root == null) {
                return;
            }
            for (String fid : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(fid);
                if (sec == null) {
                    continue;
                }
                FactionProfile profile = new FactionProfile(fid, sec.getString("display", fid));
                List<String> hostile = sec.getStringList("relations.hostile");
                List<String> ally = sec.getStringList("relations.ally");
                List<String> neutral = sec.getStringList("relations.neutral");
                if (hostile != null) hostile.forEach(profile::addHostile);
                if (ally != null) ally.forEach(profile::addAlly);
                if (neutral != null) neutral.forEach(profile::addNeutral);
                factionsById.put(fid, profile);
            }
            plugin.getLogger().info("[FactionManager] 已加载 " + factionsById.size() + " 个阵营定义（GreyZone 字符串阵营系统已启用）");
        } catch (Exception e) {
            // 加载失败（配置缺失/损坏，或测试环境 mock）时静默回退到内置 Tarkov 枚举，不影响插件启动
            factionsById.clear();
            try {
                plugin.getLogger().warning("[FactionManager] 加载 emwm_factions.yml 失败，回退内置枚举: " + e.getMessage());
            } catch (Exception ignored) {
                // 连 logger 都不可用（极端 mock 环境）则忽略
            }
        }
    }

    /** 供单元测试注入阵营定义，无需加载文件。 */
    public void addFaction(FactionProfile profile) {
        factionsById.put(profile.getId(), profile);
    }

    public boolean isConfigured() {
        return !factionsById.isEmpty();
    }

    public FactionProfile getProfile(String id) {
        return factionsById.get(id);
    }

    // ===================== 字符串阵营：赋值 / 查询 =====================

    public void assignFactionId(UUID entityUuid, String factionId) {
        if (factionId != null) {
            entityFactionId.put(entityUuid, factionId);
        }
    }

    public String getFactionId(UUID entityUuid) {
        return entityFactionId.get(entityUuid);
    }

    public String getFactionDisplay(String id) {
        FactionProfile p = factionsById.get(id);
        return p != null ? p.getDisplay() : id;
    }

    // ===================== 旧版枚举阵营（回退）=====================

    public void assignFaction(UUID entityUuid, TarkovFaction faction) {
        entityFactions.put(entityUuid, faction);
    }

    public TarkovFaction getFaction(UUID entityUuid) {
        return entityFactions.getOrDefault(entityUuid, TarkovFaction.AI_SCAV);
    }

    public void assignByTier(UUID entityUuid, String tier) {
        entityFactions.put(entityUuid, TarkovFaction.fromTier(tier));
    }

    // ===================== 关系判定（统一入口）=====================

    /**
     * 返回 self 对 target 的关系。
     * 当字符串阵营系统已配置、且双方都能解析到阵营ID时，使用可配置矩阵；
     * 否则回退到内置 Tarkov 枚举矩阵（保证非 GreyZone 服务器行为不变）。
     */
    public HostilityMatrix.Relation getRelation(LivingEntity self, LivingEntity target) {
        if (isConfigured()) {
            String selfId = getFactionId(self.getUniqueId());
            String targetId = resolveTargetFactionId(target);
            if (selfId != null && targetId != null) {
                FactionProfile selfProfile = factionsById.get(selfId);
                if (selfProfile != null) {
                    return selfProfile.getRelationTo(targetId);
                }
            }
        }
        // 回退：内置 Tarkov 枚举
        TarkovFaction selfFaction = getFaction(self.getUniqueId());
        TarkovFaction targetFaction = resolveTargetFaction(target);
        return matrix.getRelation(selfFaction, targetFaction);
    }

    private String resolveTargetFactionId(LivingEntity target) {
        if (target instanceof Player) {
            return "player";
        }
        return getFactionId(target.getUniqueId());
    }

    private TarkovFaction resolveTargetFaction(LivingEntity target) {
        if (target instanceof Player) {
            if (target.hasPermission("emwm.scav")) {
                return TarkovFaction.PLAYER_SCAV;
            }
            return TarkovFaction.PLAYER_PMC;
        }
        return getFaction(target.getUniqueId());
    }

    public boolean shouldTurnHostile(LivingEntity self, LivingEntity target) {
        // P0-8 修复：跨世界安全距离检查
        if (self.getLocation().getWorld() != null && target.getLocation().getWorld() != null
                && self.getLocation().getWorld().equals(target.getLocation().getWorld())
                && self.getLocation().distance(target.getLocation()) < 3.0) return true;
        if (self.getLastDamageCause() != null
                && self.getLastDamageCause().getEntity() == target) return true;
        return false;
    }

    public void removeEntity(UUID uuid) {
        entityFactions.remove(uuid);
        entityFactionId.remove(uuid);
    }
}
