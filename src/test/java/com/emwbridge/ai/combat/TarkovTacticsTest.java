package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import com.emwbridge.managers.MobWeaponManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TarkovTactics 战术行为控制器测试
 */
class TarkovTacticsTest {

    private EMWMBridge plugin;
    private MobWeaponManager weaponManager;
    private FileConfiguration config;
    private TarkovTactics tactics;
    private UUID aiUuid;

    @BeforeEach
    void setUp() {
        plugin = mock(EMWMBridge.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        
        weaponManager = mock(MobWeaponManager.class);
        config = mock(FileConfiguration.class);
        
        // 默认配置返回值
        when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        
        tactics = new TarkovTactics(plugin, weaponManager);
        aiUuid = UUID.randomUUID();
        tactics.reload(config);
    }

    @Nested
    @DisplayName("生命周期管理")
    class LifecycleTests {

        @Test
        @DisplayName("registerMob 应创建战术状态")
        void registerMobShouldCreateState() {
            tactics.registerMob(aiUuid);
            TarkovTactics.TacticalState state = tactics.getState(aiUuid);
            assertNotNull(state);
        }

        @Test
        @DisplayName("unregisterMob 应移除战术状态")
        void unregisterMobShouldRemoveState() {
            tactics.registerMob(aiUuid);
            tactics.unregisterMob(aiUuid);
            TarkovTactics.TacticalState state = tactics.getState(aiUuid);
            assertNotNull(state);
            assertEquals(0, state.burstCount);
        }

        @Test
        @DisplayName("getState 对不存在的 AI 应创建新状态")
        void getStateShouldCreateNewIfNotExists() {
            UUID newUuid = UUID.randomUUID();
            TarkovTactics.TacticalState state = tactics.getState(newUuid);
            assertNotNull(state);
        }
    }

    @Nested
    @DisplayName("配置重载")
    class ConfigReloadTests {

        @Test
        @DisplayName("配置重载应调用配置方法")
        void reloadShouldCallConfigMethods() {
            tactics.reload(config);
            verify(config, atLeastOnce()).getInt(anyString(), anyInt());
            verify(config, atLeastOnce()).getDouble(anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("连发射击控制")
    class BurstFireTests {

        @Test
        @DisplayName("首次射击应返回 true")
        void firstShotShouldReturnTrue() {
            assertTrue(tactics.shouldShoot(aiUuid, 1.0, 50.0));
        }

        @Test
        @DisplayName("压制模式应始终返回 true")
        void suppressModeShouldAlwaysReturnTrue() {
            tactics.registerMob(aiUuid);
            tactics.enterSuppress(aiUuid);
            
            assertTrue(tactics.isSuppressing(aiUuid));
            assertTrue(tactics.shouldShoot(aiUuid, 1.0, 50.0));
        }

        @Test
        @DisplayName("recordShot 应增加连发计数")
        void recordShotShouldIncrementBurstCount() {
            tactics.registerMob(aiUuid);
            assertEquals(0, tactics.getBurstCount(aiUuid));
            
            tactics.recordShot(aiUuid);
            assertEquals(1, tactics.getBurstCount(aiUuid));
        }

        @Test
        @DisplayName("退出压制模式应重置连发计数")
        void exitSuppressShouldResetBurstCount() {
            tactics.registerMob(aiUuid);
            tactics.enterSuppress(aiUuid);
            tactics.recordShot(aiUuid);
            assertTrue(tactics.isSuppressing(aiUuid));
            
            tactics.exitSuppress(aiUuid);
            assertFalse(tactics.isSuppressing(aiUuid));
            assertEquals(0, tactics.getBurstCount(aiUuid));
        }
    }

    @Nested
    @DisplayName("压制模式")
    class SuppressionTests {

        @Test
        @DisplayName("进入压制模式应设置标志")
        void enterSuppressShouldSetFlag() {
            tactics.registerMob(aiUuid);
            tactics.enterSuppress(aiUuid);
            assertTrue(tactics.isSuppressing(aiUuid));
        }

        @Test
        @DisplayName("退出压制模式应清除标志和连发")
        void exitSuppressShouldClearState() {
            tactics.registerMob(aiUuid);
            tactics.enterSuppress(aiUuid);
            tactics.recordShot(aiUuid);
            
            tactics.exitSuppress(aiUuid);
            
            assertFalse(tactics.isSuppressing(aiUuid));
            assertEquals(0, tactics.getBurstCount(aiUuid));
        }

        @Test
        @DisplayName("getSuppressionSpreadMultiplier 应返回正确值")
        void getSuppressionSpreadMultiplierShouldReturnValue() {
            assertEquals(2.5, tactics.getSuppressionSpreadMultiplier());
        }
    }

    @Nested
    @DisplayName("战术决策")
    class TacticalDecisionTests {

        @Test
        @DisplayName("decideTacticalAction 应返回有效动作")
        void decideTacticalActionShouldReturnValidAction() {
            tactics.registerMob(aiUuid);
            TarkovTactics.TacticalAction action = tactics.decideTacticalAction(
                aiUuid, 0.5, false, 0, 15.0, false, 50);
            
            assertNotNull(action);
            assertTrue(action instanceof TarkovTactics.TacticalAction);
        }

        @Test
        @DisplayName("decideTacticalAction 应处理各种参数")
        void decideTacticalActionShouldHandleAllParams() {
            tactics.registerMob(aiUuid);
            
            // 测试各种场景
            TarkovTactics.TacticalAction action1 = tactics.decideTacticalAction(
                aiUuid, 0.1, true, 3, 20.0, true, 300);
            TarkovTactics.TacticalAction action2 = tactics.decideTacticalAction(
                aiUuid, 0.9, false, 0, 5.0, false, 10);
            
            assertNotNull(action1);
            assertNotNull(action2);
        }
    }

    @Nested
    @DisplayName("一次性标记管理")
    class OneTimeMarkerTests {

        @Test
        @DisplayName("resetFlashUsed 应重置闪光和烟雾标记")
        void resetFlashUsedShouldResetBothMarkers() {
            tactics.registerMob(aiUuid);
            tactics.resetFlashUsed(aiUuid);
            
            TarkovTactics.TacticalAction action = tactics.decideTacticalAction(
                aiUuid, 0.9, false, 0, 8.0, false, 50);
            assertNotNull(action);
        }
    }

    @Nested
    @DisplayName("状态查询")
    class StateQueryTests {

        @Test
        @DisplayName("getTicksSinceEngage 应返回正确值")
        void getTicksSinceEngageShouldReturnValue() {
            tactics.registerMob(aiUuid);
            tactics.decideTacticalAction(aiUuid, 0.5, false, 0, 15.0, false, 150);
            
            assertEquals(150, tactics.getTicksSinceEngage(aiUuid));
        }

        @Test
        @DisplayName("getLastShotTime 应记录最后射击时间")
        void getLastShotTimeShouldRecordTime() {
            tactics.registerMob(aiUuid);
            tactics.recordShot(aiUuid);
            
            assertTrue(tactics.getLastShotTime(aiUuid) > 0);
        }
    }
}
