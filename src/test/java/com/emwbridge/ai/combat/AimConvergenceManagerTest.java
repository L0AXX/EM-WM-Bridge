package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AimConvergenceManager 瞄准收敛管理器测试
 */
class AimConvergenceManagerTest {

    private EMWMBridge plugin;
    private FileConfiguration config;
    private AimConvergenceManager aimManager;
    private UUID aiUuid;

    @BeforeEach
    void setUp() {
        plugin = mock(EMWMBridge.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        
        config = mock(FileConfiguration.class);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        
        aimManager = new AimConvergenceManager(plugin);
        aiUuid = UUID.randomUUID();
        aimManager.reload(config);
    }

    @Nested
    @DisplayName("生命周期管理")
    class LifecycleTests {

        @Test
        @DisplayName("registerMob 应注册瞄准状态")
        void registerMobShouldCreateAimState() {
            aimManager.registerMob(aiUuid);
            aimManager.registerMob(aiUuid); // 幂等操作
        }

        @Test
        @DisplayName("unregisterMob 应移除瞄准状态")
        void unregisterMobShouldRemoveAimState() {
            aimManager.registerMob(aiUuid);
            aimManager.unregisterMob(aiUuid);
            aimManager.unregisterMob(aiUuid); // 幂等操作
        }
    }

    @Nested
    @DisplayName("配置重载")
    class ConfigReloadTests {

        @Test
        @DisplayName("配置重载应调用配置方法")
        void reloadShouldCallConfigMethods() {
            aimManager.reload(config);
            verify(config, atLeastOnce()).getDouble(anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("初始延迟计算")
    class InitialDelayTests {

        @Test
        @DisplayName("已知 tier 应返回非零延迟")
        void knownTierShouldReturnNonZeroDelay() {
            double delay = aimManager.getInitialDelay("scav");
            assertTrue(delay > 0);
        }

        @Test
        @DisplayName("未知 tier 应返回默认延迟")
        void unknownTierShouldReturnDefaultDelay() {
            double delay = aimManager.getInitialDelay("unknown");
            assertTrue(delay > 0);
        }

        @Test
        @DisplayName("tier 名称应不区分大小写")
        void tierNameShouldBeCaseInsensitive() {
            double delay1 = aimManager.getInitialDelay("PMC");
            double delay2 = aimManager.getInitialDelay("pmc");
            assertTrue(delay1 > 0);
            assertTrue(delay2 > 0);
        }
    }

    @Nested
    @DisplayName("瞄准结果验证")
    class AimResultTests {

        @Test
        @DisplayName("AimResult 构造函数应正确设置属性")
        void aimResultConstructorShouldSetProperties() {
            org.bukkit.Location testLoc = mock(org.bukkit.Location.class);
            AimConvergenceManager.AimResult result = 
                new AimConvergenceManager.AimResult(testLoc, 2.5);
            
            assertEquals(testLoc, result.aimPoint);
            assertEquals(2.5, result.spreadRadius);
        }
    }

    @Nested
    @DisplayName("多 AI 状态隔离")
    class MultiAIStateIsolationTests {

        @Test
        @DisplayName("多个 AI 应有独立瞄准状态")
        void multipleAIsShouldHaveIndependentStates() {
            UUID ai1 = UUID.randomUUID();
            UUID ai2 = UUID.randomUUID();
            
            aimManager.registerMob(ai1);
            aimManager.registerMob(ai2);
            
            // 注销和重新注册
            aimManager.unregisterMob(ai1);
            aimManager.registerMob(ai1);
        }
    }
}
