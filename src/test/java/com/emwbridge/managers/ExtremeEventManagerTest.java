package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtremeEventManagerTest {

    @Mock(lenient = true)
    private EMWMBridge plugin;

    @Mock(lenient = true)
    private LivingEntity entity;

    @Mock(lenient = true)
    private Player player;

    private ExtremeEventManager eventManager;
    private UUID entityUuid;

    @BeforeEach
    void setUp() {
        entityUuid = UUID.randomUUID();
        when(entity.getUniqueId()).thenReturn(entityUuid);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));

        var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));

        eventManager = new ExtremeEventManager(plugin);
        eventManager.reload();
    }

    @Nested
    @DisplayName("配置重载")
    class ReloadTests {

        @Test
        @DisplayName("reload 应正确加载配置")
        void reloadShouldLoadConfig() {
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.getDouble("extreme-events.panic-mode-chance", 0.02)).thenReturn(0.1);
            when(config.getDouble("extreme-events.luck-shot-chance", 0.05)).thenReturn(0.2);
            when(config.getDouble("extreme-events.malfunction-chance", 0.03)).thenReturn(0.01);
            when(config.getDouble("extreme-events.tactical-mistake-chance", 0.08)).thenReturn(0.15);
            when(config.getDouble("extreme-events.adrenaline-chance", 0.10)).thenReturn(0.20);
            when(config.getDouble("extreme-events.adrenaline-hp-threshold", 0.25)).thenReturn(0.30);

            eventManager.reload();
        }

        @Test
        @DisplayName("shutdown 应清空状态")
        void shutdownShouldClearState() {
            when(entity.getLastDamage()).thenReturn(100.0);
            when(entity.getHealth()).thenReturn(10.0);
            when(entity.getMaxHealth()).thenReturn(100.0);

            try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
                var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
                bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pluginManager);

                eventManager.checkExtremeEvents(entity, player, "scav");
            }

            eventManager.shutdown();

            assertFalse(eventManager.isInPanicMode(entity));
            assertFalse(eventManager.isAdrenalineActive(entity));
        }
    }

    @Nested
    @DisplayName("极限事件检查")
    class EventCheckTests {

        @Test
        @DisplayName("checkExtremeEvents 正常状态应返回 false")
        void checkExtremeEventsNormalStateShouldReturnFalse() {
            when(entity.getLastDamage()).thenReturn(0.0);
            when(entity.getHealth()).thenReturn(50.0);
            when(entity.getMaxHealth()).thenReturn(100.0);

            try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
                var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
                bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pluginManager);

                assertFalse(eventManager.checkExtremeEvents(entity, player, "scav"));
            }
        }

        @Test
        @DisplayName("getLuckShotBonus 应返回正确值")
        void getLuckShotBonusShouldReturnCorrectValue() {
            double bonus = eventManager.getLuckShotBonus();
            assertTrue(bonus == 0 || bonus == 0.1);
        }

        @Test
        @DisplayName("getPanicModeBonus 应返回正确值")
        void getPanicModeBonusShouldReturnCorrectValue() {
            assertEquals(0.2, eventManager.getPanicModeBonus(), 0.001);
        }

        @Test
        @DisplayName("getTacticalMistakePenalty 应返回正确值")
        void getTacticalMistakePenaltyShouldReturnCorrectValue() {
            assertEquals(0.4, eventManager.getTacticalMistakePenalty(), 0.001);
        }
    }

    @Nested
    @DisplayName("状态查询")
    class StateQueryTests {

        @Test
        @DisplayName("isInPanicMode 未注册应返回 false")
        void isInPanicModeUnregisteredShouldReturnFalse() {
            assertFalse(eventManager.isInPanicMode(entity));
        }

        @Test
        @DisplayName("isAdrenalineActive 未注册应返回 false")
        void isAdrenalineActiveUnregisteredShouldReturnFalse() {
            assertFalse(eventManager.isAdrenalineActive(entity));
        }

        @Test
        @DisplayName("isSlowedByMistake 未注册应返回 false")
        void isSlowedByMistakeUnregisteredShouldReturnFalse() {
            assertFalse(eventManager.isSlowedByMistake(entity));
        }

        @Test
        @DisplayName("getSpeedModifier 未注册应返回 1.0")
        void getSpeedModifierUnregisteredShouldReturnOne() {
            assertEquals(1.0, eventManager.getSpeedModifier(entity), 0.001);
        }

        @Test
        @DisplayName("getFireRateModifier 未注册应返回 1.0")
        void getFireRateModifierUnregisteredShouldReturnOne() {
            assertEquals(1.0, eventManager.getFireRateModifier(entity), 0.001);
        }
    }

    @Nested
    @DisplayName("实体死亡")
    class EntityDeathTests {

        @Test
        @DisplayName("onEntityDeath 应移除状态")
        void onEntityDeathShouldRemoveState() {
            when(entity.getLastDamage()).thenReturn(0.0);

            try (var bukkitMock = mockStatic(org.bukkit.Bukkit.class)) {
                var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
                bukkitMock.when(org.bukkit.Bukkit::getPluginManager).thenReturn(pluginManager);

                eventManager.checkExtremeEvents(entity, player, "scav");
            }

            eventManager.onEntityDeath(entityUuid);

            assertFalse(eventManager.isInPanicMode(entity));
        }
    }

    @Nested
    @DisplayName("ExtremeState 构造")
    class ExtremeStateTests {

        @Test
        @DisplayName("ExtremeState 构造函数应初始化所有字段")
        void extremeStateConstructorShouldInitialize() {
            ExtremeEventManager.ExtremeState state = new ExtremeEventManager.ExtremeState();

            assertEquals(0, state.lastDamageTimestamp);
            assertEquals(0, state.lastDamageValue, 0.001);
            assertEquals(0, state.damageCount);
            assertEquals(0, state.consecutiveDamage);
            assertEquals(0, state.panicLevel, 0.001);
            assertFalse(state.inPanicMode);
            assertEquals(0, state.panicEndTime);
            assertFalse(state.adrenalineActive);
            assertEquals(0, state.adrenalineEndTime);
            assertEquals(0, state.lastMoveTime);
            assertFalse(state.movingTooMuch);
            assertEquals(0, state.exposedTime);
            assertEquals(0, state.mistakeCooldown);
        }
    }
}
