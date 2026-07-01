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
 * ThrowableManager 投掷物管理器测试
 */
class ThrowableManagerTest {

    private EMWMBridge plugin;
    private FileConfiguration config;
    private ThrowableManager throwableManager;
    private UUID aiUuid;

    @BeforeEach
    void setUp() {
        plugin = mock(EMWMBridge.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        
        config = mock(FileConfiguration.class);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(config.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        
        throwableManager = new ThrowableManager(plugin);
        aiUuid = UUID.randomUUID();
        throwableManager.reload(config);
    }

    @Nested
    @DisplayName("生命周期管理")
    class LifecycleTests {

        @Test
        @DisplayName("registerMob 应注册冷却状态")
        void registerMobShouldRegisterCooldownState() {
            throwableManager.registerMob(aiUuid);
            throwableManager.registerMob(aiUuid); // 幂等操作
        }

        @Test
        @DisplayName("unregisterMob 应移除冷却状态")
        void unregisterMobShouldRemoveCooldownState() {
            throwableManager.registerMob(aiUuid);
            throwableManager.unregisterMob(aiUuid);
            throwableManager.unregisterMob(aiUuid); // 幂等操作
        }

        @Test
        @DisplayName("未注册的 AI 获取冷却应返回 0")
        void unregisteredAIGetCooldownShouldReturnZero() {
            UUID unregistered = UUID.randomUUID();
            long cooldown = throwableManager.getCooldownRemaining(unregistered, 
                ThrowableManager.ThrowableType.FRAG);
            assertEquals(0, cooldown);
        }
    }

    @Nested
    @DisplayName("冷却管理")
    class CooldownTests {

        @Test
        @DisplayName("新注册 AI 所有投掷物冷却应为 0")
        void newAIRegisteredAllCooldownsShouldBeZero() {
            throwableManager.registerMob(aiUuid);
            
            assertEquals(0, throwableManager.getCooldownRemaining(aiUuid, 
                ThrowableManager.ThrowableType.FRAG));
            assertEquals(0, throwableManager.getCooldownRemaining(aiUuid, 
                ThrowableManager.ThrowableType.FLASH));
            assertEquals(0, throwableManager.getCooldownRemaining(aiUuid, 
                ThrowableManager.ThrowableType.SMOKE));
        }
    }

    @Nested
    @DisplayName("配置重载")
    class ConfigReloadTests {

        @Test
        @DisplayName("配置重载应加载破片参数")
        void reloadShouldLoadFragParams() {
            verify(config).getDouble(eq("throwables.frag.fuse-seconds"), eq(3.0));
            verify(config).getDouble(eq("throwables.frag.radius"), eq(5.0));
            verify(config).getDouble(eq("throwables.frag.max-damage"), eq(16.0));
            verify(config).getDouble(eq("throwables.frag.min-damage"), eq(4.0));
        }

        @Test
        @DisplayName("配置重载应加载闪光弹参数")
        void reloadShouldLoadFlashParams() {
            verify(config).getDouble(eq("throwables.flash.fuse-seconds"), eq(2.0));
            verify(config).getDouble(eq("throwables.flash.radius"), eq(8.0));
            verify(config).getInt(eq("throwables.flash.blindness-ticks"), eq(80));
            verify(config).getInt(eq("throwables.flash.slowness-ticks"), eq(60));
            verify(config).getInt(eq("throwables.flash.nausea-ticks"), eq(40));
        }

        @Test
        @DisplayName("配置重载应加载烟雾弹参数")
        void reloadShouldLoadSmokeParams() {
            verify(config).getDouble(eq("throwables.smoke.fuse-seconds"), eq(1.0));
            verify(config).getDouble(eq("throwables.smoke.radius"), eq(6.0));
            verify(config).getInt(eq("throwables.smoke.duration-ticks"), eq(160));
        }

        @Test
        @DisplayName("配置重载应加载冷却参数")
        void reloadShouldLoadCooldownParams() {
            verify(config).getLong(eq("throwables.cooldowns.frag-ms"), eq(15000L));
            verify(config).getLong(eq("throwables.cooldowns.flash-ms"), eq(20000L));
            verify(config).getLong(eq("throwables.cooldowns.smoke-ms"), eq(25000L));
        }

        @Test
        @DisplayName("配置重载应记录日志")
        void reloadShouldLogInfo() {
            verify(plugin).debug(contains("[投掷物]"));
        }
    }

    @Nested
    @DisplayName("ThrowableType 枚举")
    class ThrowableTypeTests {

        @Test
        @DisplayName("ThrowableType 应包含三种类型")
        void throwableTypeShouldContainThreeTypes() {
            ThrowableManager.ThrowableType[] types = 
                ThrowableManager.ThrowableType.values();
            assertEquals(3, types.length);
            assertNotNull(ThrowableManager.ThrowableType.FRAG);
            assertNotNull(ThrowableManager.ThrowableType.FLASH);
            assertNotNull(ThrowableManager.ThrowableType.SMOKE);
        }

        @Test
        @DisplayName("ThrowableType ordinal 应正确")
        void throwableTypeOrdinalShouldBeCorrect() {
            assertEquals(0, ThrowableManager.ThrowableType.FRAG.ordinal());
            assertEquals(1, ThrowableManager.ThrowableType.FLASH.ordinal());
            assertEquals(2, ThrowableManager.ThrowableType.SMOKE.ordinal());
        }
    }

    @Nested
    @DisplayName("内部状态验证")
    class InternalStateTests {

        @Test
        @DisplayName("多个 AI 冷却应独立")
        void multipleAICooldownsShouldBeIndependent() {
            UUID ai1 = UUID.randomUUID();
            UUID ai2 = UUID.randomUUID();
            
            throwableManager.registerMob(ai1);
            throwableManager.registerMob(ai2);
            
            assertEquals(0, throwableManager.getCooldownRemaining(ai1, 
                ThrowableManager.ThrowableType.FRAG));
            assertEquals(0, throwableManager.getCooldownRemaining(ai2, 
                ThrowableManager.ThrowableType.FRAG));
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("冷却值应为非负数")
        void cooldownValueShouldBeNonNegative() {
            throwableManager.registerMob(aiUuid);
            
            long cooldown = throwableManager.getCooldownRemaining(aiUuid, 
                ThrowableManager.ThrowableType.FRAG);
            assertTrue(cooldown >= 0);
        }

        @Test
        @DisplayName("getCooldownRemaining 对 null UUID 应返回 0")
        void getCooldownRemainingNullUUIDShouldReturnZero() {
            long cooldown = throwableManager.getCooldownRemaining(null, 
                ThrowableManager.ThrowableType.FRAG);
            assertEquals(0, cooldown);
        }
    }
}
