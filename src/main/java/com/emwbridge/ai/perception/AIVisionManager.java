package com.emwbridge.ai.perception;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.events.AIEventDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 视觉+听觉综合感知管理器 — 替代旧 PerceptionManager
 * 
 * 核心职责：
 * - K:AI实体UUID  V:玩家-曝光数据 的缓存管理
 * - 每 Tick 更新视觉曝光 + 听觉事件处理
 * - 闪光弹致盲 — 一键清空目标 AI 所有视觉曝光缓存
 * - 三级警戒状态判定 (null=IDLE / YELLOW / ORANGE / RED)
 * - EventDispatcher 跨模块广播 SIGHT/SOUND/HOSTILE_LOCK/FLASH_BLIND 事件
 */
public class AIVisionManager {

    private final EMWMBridge plugin;
    private final VisualPerception visual;
    private final AuditoryPerception auditory;
    private final AIEventDispatcher eventDispatcher;

    // K:AI实体UUID  V:玩家UUID-曝光数据
    private final Map<UUID, Map<UUID, ExposureData>> exposureCache = new ConcurrentHashMap<>();
    // 当前警戒阶段
    private final Map<UUID, Map<UUID, AlertStage>> alertStages = new ConcurrentHashMap<>();
    // 主目标
    private final Map<UUID, UUID> primaryTargetMap = new ConcurrentHashMap<>();

    // 配置参数
    private double baseRate = 5.0;
    private double exposureDecayRate = 0.3;
    private boolean auditoryEnabled = true;

    public AIVisionManager(EMWMBridge plugin, AuditoryPerception auditory, AIEventDispatcher eventDispatcher) {
        this.plugin = plugin;
        this.visual = new VisualPerception();
        this.auditory = auditory;
        this.eventDispatcher = eventDispatcher;
    }

    // ==================== 配置加载 ====================

    public void reload(FileConfiguration config) {
        visual.reload(config);
        auditory.reload(config);
        baseRate = config.getDouble("perception.visual.base-rate", 5.0);
        exposureDecayRate = config.getDouble("perception.decay-rate", 0.3);
        auditoryEnabled = config.getBoolean("perception.auditory.enabled", true);
    }

    // ==================== 每Tick曝光更新 ====================

    /**
     * 核心方法 — 每 AI tick 更新对一个目标的视觉曝光值
     */
    public ExposureData tickExposure(LivingEntity ai, Player target) {
        UUID aiUuid = ai.getUniqueId();
        UUID playerUuid = target.getUniqueId();

        Map<UUID, ExposureData> playerExposures = exposureCache
                .computeIfAbsent(aiUuid, k -> new ConcurrentHashMap<>());
        Map<UUID, AlertStage> aiAlerts = alertStages
                .computeIfAbsent(aiUuid, k -> new ConcurrentHashMap<>());

        ExposureData data = playerExposures.computeIfAbsent(playerUuid, k -> new ExposureData());

        // 视觉曝光增量（传入当前曝光值用于记忆追踪）
        double increment = visual.calculate(ai, target, baseRate, data.getValue());
        if (increment > 0) {
            // 玩家朝向修正：背对AI时更难被发现
            increment *= visual.getTargetFacingMultiplier(target, ai.getEyeLocation());
            data.addExposure(increment);
            data.updatePosition(target.getLocation());
            // 广播 SIGHT 事件 — 小队情报共享
            eventDispatcher.sight(aiUuid, playerUuid, data.getValue());
        } else {
            data.decayExposure(exposureDecayRate);
        }

        // 闪光弹 tick
        data.tickFlashBlind();

        AlertStage oldStage = aiAlerts.get(playerUuid);
        AlertStage newStage = AlertStage.transitionWithProtection(oldStage, data.getValue(), false, aiUuid);

        if (newStage == null) {
            aiAlerts.remove(playerUuid);
            if (oldStage == AlertStage.RED) {
                AlertStage.clearHatred(aiUuid);
            }
        } else if (newStage != oldStage) {
            aiAlerts.put(playerUuid, newStage);
            if (newStage == AlertStage.RED && oldStage != AlertStage.RED) {
                // 投影仇恨位置到AI实体地面高度，避免记录空中坐标
                Location hatredLoc = target.getLocation().clone();
                if (hatredLoc.getY() - ai.getLocation().getY() > 3) {
                    hatredLoc.setY(ai.getLocation().getY());
                }
                AlertStage.recordHatred(aiUuid, playerUuid, hatredLoc, Bukkit.getCurrentTick());
                eventDispatcher.hostileLock(aiUuid, playerUuid, hatredLoc);
            } else if (newStage != AlertStage.RED && oldStage == AlertStage.RED) {
                AlertStage.clearHatred(aiUuid);
            }
        }

        // 更新主目标
        updatePrimaryTarget(aiUuid, playerExposures);

        return data;
    }

    // ==================== 闪光弹致盲 ====================

    /**
     * 闪光弹致盲 — 一键清空目标 AI 所有视觉曝光缓存
     */
    public void flashBlind(LivingEntity ai) {
        UUID aiUuid = ai.getUniqueId();
        Map<UUID, ExposureData> exposures = exposureCache.get(aiUuid);
        if (exposures == null) return;

        for (ExposureData data : exposures.values()) {
            data.applyFlashBlind();
        }

        // 清除所有警戒状态
        Map<UUID, AlertStage> alerts = alertStages.get(aiUuid);
        if (alerts != null) alerts.clear();

        // 清除仇恨
        AlertStage.clearHatred(aiUuid);
        primaryTargetMap.remove(aiUuid);

        // 广播 FLASH_BLIND 事件
        eventDispatcher.flashBlind(aiUuid);
    }

    /**
     * 按目标玩家 UUID 闪光弹致盲（仅对侦测到该玩家的 AI 生效）
     */
    public void flashBlindByTarget(Player target, List<LivingEntity> nearbyAI) {
        UUID targetUuid = target.getUniqueId();
        for (LivingEntity ai : nearbyAI) {
            UUID aiUuid = ai.getUniqueId();
            Map<UUID, ExposureData> exposures = exposureCache.get(aiUuid);
            if (exposures == null) continue;
            ExposureData data = exposures.get(targetUuid);
            if (data != null && data.getValue() > 0) {
                flashBlind(ai);
            }
        }
    }

    // ==================== 听觉事件处理 ====================

    /**
     * 接收声源事件 — 委托给 AuditoryPerception 处理
     * 传入小队成员列表用于情报共享
     */
    public void receiveSoundEvent(SoundSource source, LivingEntity listener,
                                  List<LivingEntity> squadMembers) {
        if (!auditoryEnabled) return;

        // 对每个小队成员分别计算听觉暴露
        if (squadMembers == null || squadMembers.isEmpty()) {
            auditory.processSound(listener, source, exposureCache, alertStages, eventDispatcher);
        } else {
            for (LivingEntity member : squadMembers) {
                auditory.processSound(member, source, exposureCache, alertStages, eventDispatcher);
            }
        }
    }

    /**
     * 供 SoundEventManager 的广播使用：对附近所有AI传播声源
     */
    public void broadcastSound(SoundSource source, List<LivingEntity> listeners) {
        if (!auditoryEnabled) return;
        for (LivingEntity listener : listeners) {
            auditory.processSound(listener, source, exposureCache, alertStages, eventDispatcher);
        }
    }

    // ==================== 查询方法 ====================

    /** 获取指定 AI 对某玩家的警戒阶段（null = IDLE） */
    public AlertStage getAlertStage(UUID aiUuid, UUID playerUuid) {
        Map<UUID, AlertStage> alerts = alertStages.get(aiUuid);
        return alerts != null ? alerts.get(playerUuid) : null;
    }

    /** 基于 ExposureData 快速判定警戒阶段 */
    public AlertStage getAlertStage(ExposureData data) {
        if (data == null) return null;
        return AlertStage.fromExposure(data.getValue());
    }

    /** 获取曝光值 */
    public double getExposure(UUID aiUuid, UUID playerUuid) {
        Map<UUID, ExposureData> exposures = exposureCache.get(aiUuid);
        if (exposures == null) return 0;
        ExposureData data = exposures.get(playerUuid);
        return data != null ? data.getValue() : 0;
    }

    /** 获取完整曝光数据 */
    public ExposureData getExposureData(UUID aiUuid, UUID playerUuid) {
        Map<UUID, ExposureData> exposures = exposureCache.get(aiUuid);
        return exposures != null ? exposures.get(playerUuid) : null;
    }

    /** 获取AI对所有目标的最大曝光值 */
    public double getMaxExposure(UUID aiUuid) {
        Map<UUID, ExposureData> exposures = exposureCache.get(aiUuid);
        if (exposures == null || exposures.isEmpty()) return 0;
        return exposures.values().stream()
                .mapToDouble(ExposureData::getValue).max().orElse(0);
    }

    /** 获取最后已知位置 */
    public Location getLastKnownPosition(UUID aiUuid, UUID playerUuid) {
        Map<UUID, ExposureData> exposures = exposureCache.get(aiUuid);
        if (exposures == null) return null;
        ExposureData data = exposures.get(playerUuid);
        return data != null ? data.getLastKnownPosition() : null;
    }

    /** 获取主目标 UUID */
    public UUID getPrimaryTarget(UUID aiUuid) {
        return primaryTargetMap.get(aiUuid);
    }

    /** 获取AI对某玩家的活跃曝光数据Map（用于遍历） */
    public Map<UUID, ExposureData> getExposures(UUID aiUuid) {
        return exposureCache.get(aiUuid);
    }

    /** 快速衰减特定目标的暴露值（用于不可达目标，衰减速度5倍） */
    public void fastDecayExposure(UUID aiUuid, UUID playerUuid) {
        Map<UUID, ExposureData> exposures = exposureCache.get(aiUuid);
        if (exposures == null) return;
        ExposureData data = exposures.get(playerUuid);
        if (data != null) {
            data.decayExposure(exposureDecayRate * 5);
        }
    }

    // ==================== 生命周期 ====================

    public void registerMob(UUID aiUuid) {
        exposureCache.put(aiUuid, new ConcurrentHashMap<>());
        alertStages.put(aiUuid, new ConcurrentHashMap<>());
    }

    public void unregisterMob(UUID aiUuid) {
        exposureCache.remove(aiUuid);
        alertStages.remove(aiUuid);
        primaryTargetMap.remove(aiUuid);
        AlertStage.clearHatred(aiUuid);
    }

    // ==================== 内部工具 ====================

    private void updatePrimaryTarget(UUID aiUuid, Map<UUID, ExposureData> exposures) {
        UUID best = null;
        double bestExp = 0;
        for (Map.Entry<UUID, ExposureData> e : exposures.entrySet()) {
            double val = e.getValue().getValue();
            if (val > bestExp) {
                bestExp = val;
                best = e.getKey();
            }
        }
        if (best != null && bestExp >= 2) {
            primaryTargetMap.put(aiUuid, best);
        } else {
            primaryTargetMap.remove(aiUuid);
        }
    }

    // ==================== Getter ====================

    public AIEventDispatcher getEventDispatcher() { return eventDispatcher; }
    public VisualPerception getVisual() { return visual; }
    public AuditoryPerception getAuditory() { return auditory; }
    public boolean isAuditoryEnabled() { return auditoryEnabled; }
}
