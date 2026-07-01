package com.emwbridge.harness;

import com.emwbridge.ai.perception.AlertStage;
import com.emwbridge.ai.perception.AIVisionManager;
import com.emwbridge.ai.perception.AuditoryPerception;
import com.emwbridge.ai.perception.ExposureData;
import com.emwbridge.ai.events.AIEventDispatcher;
import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.bukkit.World;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI感知系统测试Harness - 使用Mockito模拟Bukkit环境
 */
public class PerceptionHarness {

    @Mock
    private EMWMBridge plugin;

    private AIVisionManager visionManager;
    private AutoCloseable mocks;

    // 测试用的UUID
    private UUID aiUuid = UUID.randomUUID();
    private UUID playerUuid = UUID.randomUUID();

    // 累积的曝光值
    private double totalExposure = 0.0;

    public PerceptionHarness() {
        this.mocks = MockitoAnnotations.openMocks(this);
        this.visionManager = new AIVisionManager(plugin, new AuditoryPerception(), new AIEventDispatcher());
    }

    public AIVisionManager getVisionManager() {
        return visionManager;
    }

    public UUID getAiUuid() {
        return aiUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void cleanup() {
        try {
            if (mocks != null) {
                mocks.close();
            }
        } catch (Exception e) {
            // ignore
        }
        visionManager = null;
    }

    /**
     * 模拟曝光值累积 - 将曝光值直接注入visionManager的内部缓存
     */
    @SuppressWarnings("unchecked")
    public void addExposure(double amount) {
        totalExposure += amount;
        if (totalExposure > 100.0) totalExposure = 100.0;

        try {
            // 访问 AIVisionManager.exposureCache 私有字段
            Field exposureCacheField = AIVisionManager.class.getDeclaredField("exposureCache");
            exposureCacheField.setAccessible(true);
            Map<UUID, Map<UUID, ExposureData>> exposureCache =
                    (Map<UUID, Map<UUID, ExposureData>>) exposureCacheField.get(visionManager);

            Map<UUID, ExposureData> playerExposures = exposureCache
                    .computeIfAbsent(aiUuid, k -> new ConcurrentHashMap<>());
            ExposureData data = playerExposures.computeIfAbsent(playerUuid, k -> new ExposureData());

            // 直接设置曝光值
            data.addExposure(amount);

            // 同步更新alertStages
            AlertStage alertStage = AlertStage.fromExposure(totalExposure);

            Field alertStagesField = AIVisionManager.class.getDeclaredField("alertStages");
            alertStagesField.setAccessible(true);
            Map<UUID, Map<UUID, AlertStage>> alertStages =
                    (Map<UUID, Map<UUID, AlertStage>>) alertStagesField.get(visionManager);

            Map<UUID, AlertStage> aiAlerts = alertStages
                    .computeIfAbsent(aiUuid, k -> new ConcurrentHashMap<>());

            if (alertStage != null) {
                aiAlerts.put(playerUuid, alertStage);
                if (alertStage == AlertStage.RED) {
                    World world = mock(World.class);
                    Location mockLoc = new Location(world, 0.0, 0.0, 0.0);
                    AlertStage.recordHatred(aiUuid, playerUuid, mockLoc, 0);

                    // 更新主目标
                    Field primaryTargetMapField = AIVisionManager.class.getDeclaredField("primaryTargetMap");
                    primaryTargetMapField.setAccessible(true);
                    Map<UUID, UUID> primaryTargetMap =
                            (Map<UUID, UUID>) primaryTargetMapField.get(visionManager);
                    primaryTargetMap.put(aiUuid, playerUuid);
                }
            } else {
                aiAlerts.remove(playerUuid);
            }
        } catch (Exception e) {
            throw new RuntimeException("无法通过反射注入曝光数据", e);
        }
    }

    /**
     * 获取当前警戒状态
     */
    public AlertStage getCurrentStage() {
        return visionManager.getAlertStage(aiUuid, playerUuid);
    }

    /**
     * 检查主目标是否锁定
     */
    public boolean isPrimaryTargetLocked() {
        UUID primaryTarget = visionManager.getPrimaryTarget(aiUuid);
        return primaryTarget != null && primaryTarget.equals(playerUuid);
    }

    /**
     * 获取仇恨目标
     */
    public UUID getHatredTarget() {
        return AlertStage.getHatredTarget(aiUuid);
    }
}
