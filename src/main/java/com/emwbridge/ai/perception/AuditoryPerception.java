package com.emwbridge.ai.perception;

import com.emwbridge.ai.events.AIEventDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

import java.util.*;

/**
 * 听觉感知引擎 — 基于 SoundType 分级传播 + 方块材质衰减
 * 
 * 功能：
 * - 9种SoundType分级：不同枪声/脚步/开门/投掷物/爆炸
 * - 固体方块穿透衰减：每墙 pow(0.7, count)，不可穿透声源遇墙直接归零
 * - 耳机属性（emwm_headset metadata）×1.4 全局听觉范围
 * - 耳鸣状态（emwm_tinnitus metadata）听觉检测倍率 0.2 持续 5s
 * - 同小队听觉情报共享通过 EventDispatcher
 */
public class AuditoryPerception {

    // 墙体穿透衰减系数
    private double wallAttenuation = 0.7;

    // 耳机
    private double headphoneMultiplier = 1.4;

    // 耳鸣
    private double tinnitusMultiplier = 0.2;
    private int tinnitusDurationTicks = 100; // 5 秒 = 100 tick

    // 方向一致性加成（同一方向连续听到声音）
    private double sameDirectionBonus = 5.0;

    // 脚步冷却 (ms)
    private long footstepCooldownMs = 800;
    private final Map<UUID, Long> lastFootstepTimes = new HashMap<>();

    public void reload(FileConfiguration config) {
        wallAttenuation = config.getDouble("perception.auditory.wall-attenuation", 0.7);
        headphoneMultiplier = config.getDouble("perception.auditory.headphone-multiplier", 1.4);
        tinnitusMultiplier = config.getDouble("perception.auditory.tinnitus-multiplier", 0.2);
        tinnitusDurationTicks = config.getInt("perception.auditory.tinnitus-duration-ticks", 100);
        sameDirectionBonus = config.getDouble("perception.auditory.same-direction-bonus", 5.0);
        footstepCooldownMs = config.getLong("perception.auditory.footstep-cooldown-ms", 800L);
    }

    // ==================== 核心处理 ====================

    /**
     * 对单个 AI 实体处理声源 — 计算听觉暴露值 + 更新曝光缓存 + 广播事件
     */
    public void processSound(LivingEntity listener, SoundSource source,
                             Map<UUID, Map<UUID, ExposureData>> exposureCache,
                             Map<UUID, Map<UUID, AlertStage>> alertStages,
                             AIEventDispatcher eventDispatcher) {

        UUID aiUuid = listener.getUniqueId();

        // 耳鸣检查 — 若有耳鸣且未过期，听觉极度衰减
        double tinnitusM = 1.0;
        if (listener.hasMetadata("emwm_tinnitus")) {
            Long tinnitusUntilTick = null;
            try {
                tinnitusUntilTick = (Long) listener.getMetadata("emwm_tinnitus").get(0).value();
            } catch (Exception ignored) {}
            if (tinnitusUntilTick != null && Bukkit.getCurrentTick() < tinnitusUntilTick) {
                tinnitusM = tinnitusMultiplier;
            } else {
                listener.removeMetadata("emwm_tinnitus", null);
            }
        }

        // 耳机检查
        double headphoneM = listener.hasMetadata("emwm_headset") ? headphoneMultiplier : 1.0;

        // 计算距离
        Location listenerLoc = listener.getEyeLocation();
        double distance = listenerLoc.distance(source.getSourceLoc());

        // 超出基础范围直接跳过（耳机不扩展基础范围判定，留空让距离衰减处理）
        double effectiveRange = source.getBaseRange() * headphoneM;
        if (distance > effectiveRange * 1.2) return;

        // 计算墙体穿透数
        int solidBlocks = countSolidBlocksBetween(listenerLoc, source.getSourceLoc());
        source.setPenetrationCount(solidBlocks);

        // 计算听觉暴露值
        double auditoryExposure = source.calculateAuditoryExposure(distance, headphoneM, tinnitusM);
        if (auditoryExposure <= 0) return;

        // 锁定声源对应的最近玩家
        UUID closestPlayerUuid = findClosestPlayer(listenerLoc, source.getSourceLoc(), exposureCache.get(aiUuid));

        Map<UUID, ExposureData> playerExposures = exposureCache
                .computeIfAbsent(aiUuid, k -> new java.util.concurrent.ConcurrentHashMap<>());
        Map<UUID, AlertStage> aiAlerts = alertStages
                .computeIfAbsent(aiUuid, k -> new java.util.concurrent.ConcurrentHashMap<>());

        if (closestPlayerUuid != null) {
            ExposureData data = playerExposures.computeIfAbsent(closestPlayerUuid, k -> new ExposureData());

            // 方向一致性加成
            if (isSameDirection(listener, source, closestPlayerUuid, playerExposures, auditoryExposure)) {
                auditoryExposure += sameDirectionBonus;
            }

            data.addExposure(auditoryExposure);
            data.updatePosition(source.getSourceLoc());

            // 听觉触发状态流转（声音可直接从 YELLOW 跳到 ORANGE）
            AlertStage oldStage = aiAlerts.get(closestPlayerUuid);
            AlertStage newStage = AlertStage.transition(oldStage, data.getValue(), true);
            if (newStage != oldStage) {
                aiAlerts.put(closestPlayerUuid, newStage);
                if (newStage == AlertStage.RED && oldStage != AlertStage.RED) {
                    AlertStage.recordHatred(aiUuid, closestPlayerUuid,
                            source.getSourceLoc(), Bukkit.getCurrentTick());
                    eventDispatcher.hostileLock(aiUuid, closestPlayerUuid, data.getLastKnownPosition());
                } else if (newStage != AlertStage.RED && oldStage == AlertStage.RED) {
                    AlertStage.clearHatred(aiUuid);
                }
            }
            if (newStage == null) {
                aiAlerts.remove(closestPlayerUuid);
            }

            // 广播 SOUND 事件 — 小队情报共享
            eventDispatcher.sound(aiUuid, closestPlayerUuid, source.getSourceLoc(), auditoryExposure);
        }
    }

    // ==================== 穿墙计数 ====================

    private int countSolidBlocksBetween(Location from, Location to) {
        int count = 0;
        double dist = from.distance(to);
        int steps = (int) (dist * 2);
        if (steps == 0) return 0;

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(from.getX() + (to.getX() - from.getX()) * t);
            int y = (int) Math.floor(from.getY() + (to.getY() - from.getY()) * t);
            int z = (int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * t);
            Material type = from.getWorld().getBlockAt(x, y, z).getType();
            if (type.isSolid() && !type.name().contains("GLASS") && !type.name().contains("PANE")) {
                count++;
            }
        }
        return count;
    }

    // ==================== 方向一致性 ====================

    private final Map<UUID, Map<UUID, SoundMemory>> lastSoundDirection = new HashMap<>();

    private boolean isSameDirection(LivingEntity listener, SoundSource source, UUID playerUuid,
                                     Map<UUID, ExposureData> exposuresInfo, double auditoryExposure) {
        // 基础听觉太低不触发方向加成
        if (auditoryExposure < 5) return false;

        Location listenerLoc = listener.getLocation();
        org.bukkit.util.Vector dirToSource = source.getSourceLoc().toVector()
                .subtract(listenerLoc.toVector()).setY(0).normalize();

        Map<UUID, SoundMemory> sounds = lastSoundDirection.computeIfAbsent(
                listener.getUniqueId(), k -> new HashMap<>());
        SoundMemory memory = sounds.get(playerUuid);
        long now = Bukkit.getCurrentTick();

        if (memory != null && (now - memory.tick) < 60   // 3秒内
                && memory.direction.dot(dirToSource) > 0.7) {
            sounds.put(playerUuid, new SoundMemory(dirToSource, now));
            return true;
        }
        sounds.put(playerUuid, new SoundMemory(dirToSource, now));
        return false;
    }

    // ==================== 最近玩家查找 ====================

    private UUID findClosestPlayer(Location listenerLoc, Location soundLoc,
                                    Map<UUID, ExposureData> existingExposures) {
        UUID closest = null;
        double closestDist = Double.MAX_VALUE;

        if (existingExposures != null && !existingExposures.isEmpty()) {
            for (UUID playerUuid : existingExposures.keySet()) {
                var player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    double d = player.getLocation().distance(soundLoc);
                    if (d < closestDist) {
                        closestDist = d;
                        closest = playerUuid;
                    }
                }
            }
            if (closest != null) return closest;
        }

        if (listenerLoc.getWorld() != null) {
            for (var player : listenerLoc.getWorld().getPlayers()) {
                if (!player.isOnline() || player.isDead()) continue;
                double d = player.getLocation().distance(soundLoc);
                if (d < closestDist && d < 100) {
                    closestDist = d;
                    closest = player.getUniqueId();
                }
            }
        }

        return closest;
    }

    // ==================== 外部事件触发 ====================

    /**
     * 给实体添加耳鸣状态（爆炸触发）
     */
    public void applyTinnitus(LivingEntity entity) {
        long untilTick = Bukkit.getCurrentTick() + tinnitusDurationTicks;
        entity.setMetadata("emwm_tinnitus",
                new org.bukkit.metadata.FixedMetadataValue(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("EM-WM-Bridge"), untilTick));
    }

    /**
     * 记录脚步时间（用于脚步冷却去重）
     */
    public void recordFootstep(UUID playerUuid, long currentTick) {
        lastFootstepTimes.put(playerUuid, currentTick);
    }

    /**
     * 检查脚步冷却是否已过
     */
    public boolean canHearFootstep(UUID playerUuid, long currentTick) {
        Long last = lastFootstepTimes.get(playerUuid);
        return last == null || (currentTick - last) >= (footstepCooldownMs / 50);
    }

    // ==================== 内部记录 ====================

    private record SoundMemory(org.bukkit.util.Vector direction, long tick) {}

    // Getter
    public double getHeadphoneMultiplier() { return headphoneMultiplier; }
    public double getTinnitusMultiplier() { return tinnitusMultiplier; }
    public int getTinnitusDurationTicks() { return tinnitusDurationTicks; }
}
