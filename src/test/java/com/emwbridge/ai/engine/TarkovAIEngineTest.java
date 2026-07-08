package com.emwbridge.ai.engine;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.faction.FactionManager;
import com.emwbridge.ai.perception.AIVisionManager;
import com.emwbridge.ai.squad.SquadManager;
import com.emwbridge.managers.ExtremeEventManager;
import com.emwbridge.managers.MobWeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TarkovAIEngineTest {

    @Mock(lenient = true)
    private EMWMBridge plugin;

    @Mock(lenient = true)
    private MobWeaponManager weaponManager;

    @Mock(lenient = true)
    private ExtremeEventManager extremeEventManager;

    @Mock(lenient = true)
    private LivingEntity entity;

    private TarkovAIEngine engine;
    private UUID entityUuid;

    @BeforeEach
    void setUp() {
        entityUuid = UUID.randomUUID();
        when(entity.getUniqueId()).thenReturn(entityUuid);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        when(plugin.getName()).thenReturn("EMWMBridge");
        // P0-7: registerMob/unregisterMob 使用 PersistentDataContainer
        var pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        // P0-7: registerMob 设置 metadata
        doNothing().when(entity).setMetadata(anyString(), any());
        doNothing().when(entity).removeMetadata(anyString(), any());
        when(entity.hasMetadata(anyString())).thenReturn(false);
        engine = new TarkovAIEngine(plugin, weaponManager, extremeEventManager);
    }

    @Nested
    @DisplayName("生命周期管理")
    class LifecycleTests {

        @Test
        @DisplayName("start 应启动调度器和事件注册")
        void startShouldStartSchedulerAndRegisterEvents() {
            var testPlugin = mock(EMWMBridge.class);
            var testWeaponManager = mock(MobWeaponManager.class);
            var testExtremeManager = mock(ExtremeEventManager.class);
            when(testPlugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
            when(testPlugin.getName()).thenReturn("EMWMBridge");
            
            var testEngine = new TarkovAIEngine(testPlugin, testWeaponManager, testExtremeManager);
            
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(testPlugin.getConfig()).thenReturn(config);
            when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
            
            try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
                var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
                bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                
                testEngine.start();
                
                verify(testPlugin.getLogger()).info(contains("TarkovAIEngine"));
            }
        }

        @Test
        @DisplayName("stop 应停止调度器")
        void stopShouldStopScheduler() {
            var testPlugin = mock(EMWMBridge.class);
            var testWeaponManager = mock(MobWeaponManager.class);
            var testExtremeManager = mock(ExtremeEventManager.class);
            when(testPlugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
            when(testPlugin.getName()).thenReturn("EMWMBridge");
            
            var testEngine = new TarkovAIEngine(testPlugin, testWeaponManager, testExtremeManager);
            
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(testPlugin.getConfig()).thenReturn(config);
            when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
            
            try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
                var task = mock(org.bukkit.scheduler.BukkitTask.class);
                var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
                when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
                bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                
                testEngine.start();
                testEngine.stop();
                
                verify(task).cancel();
            }
        }

        @Test
        @DisplayName("shutdown 应停止并清空所有状态")
        void shutdownShouldStopAndClearState() {
            var testPlugin = mock(EMWMBridge.class);
            var testWeaponManager = mock(MobWeaponManager.class);
            var testExtremeManager = mock(ExtremeEventManager.class);
            when(testPlugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
            when(testPlugin.getName()).thenReturn("EMWMBridge");
            
            var testEngine = new TarkovAIEngine(testPlugin, testWeaponManager, testExtremeManager);
            
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(testPlugin.getConfig()).thenReturn(config);
            when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
            when(config.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
            
            try (MockedStatic<Bukkit> bukkitMock = mockStatic(Bukkit.class)) {
                var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
                var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
                bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                
                testEngine.start();
                testEngine.shutdown();
                
                assertEquals(0, testEngine.getActiveCount());
            }
        }

        @Test
        @DisplayName("isActive 注册后应返回 true")
        void isActiveAfterRegisterShouldReturnTrue() {
            engine.registerMob(entity, "scav");
            
            assertTrue(engine.isActive(entity));
        }

        @Test
        @DisplayName("isActive 未注册应返回 false")
        void isActiveWithoutRegisterShouldReturnFalse() {
            assertFalse(engine.isActive(entity));
        }

        @Test
        @DisplayName("getActiveCount 注册后应返回计数")
        void getActiveCountAfterRegisterShouldReturnCount() {
            assertEquals(0, engine.getActiveCount());
            
            engine.registerMob(entity, "scav");
            
            assertEquals(1, engine.getActiveCount());
        }

        @Test
        @DisplayName("getActiveCount 注销后应返回 0")
        void getActiveCountAfterUnregisterShouldReturnZero() {
            engine.registerMob(entity, "scav");
            
            assertEquals(1, engine.getActiveCount());
            
            engine.unregisterMob(entity);
            
            assertEquals(0, engine.getActiveCount());
        }
    }

    @Nested
    @DisplayName("AI 注册/注销")
    class RegisterUnregisterTests {

        @Test
        @DisplayName("registerMob 应注册所有子系统")
        void registerMobShouldRegisterAllSubsystems() {
            engine.registerMob(entity, "scav");
            
            assertTrue(engine.isActive(entity));
            assertEquals(1, engine.getActiveCount());
        }

        @Test
        @DisplayName("unregisterMob 应注销所有子系统")
        void unregisterMobShouldUnregisterAllSubsystems() {
            engine.registerMob(entity, "scav");
            assertTrue(engine.isActive(entity));
            
            engine.unregisterMob(entity);
            
            assertFalse(engine.isActive(entity));
        }

        @Test
        @DisplayName("registerMob 不同 tier 应正确注册")
        void registerMobDifferentTiersShouldRegisterCorrectly() {
            engine.registerMob(entity, "scav");
            assertTrue(engine.isActive(entity));
            
            engine.unregisterMob(entity);
            
            engine.registerMob(entity, "pmc");
            assertTrue(engine.isActive(entity));
            
            engine.unregisterMob(entity);
            
            engine.registerMob(entity, "boss");
            assertTrue(engine.isActive(entity));
        }
    }

    @Nested
    @DisplayName("子系统访问")
    class SubsystemAccessTests {

        @Test
        @DisplayName("getAIVisionManager 应返回视觉管理器")
        void getAIVisionManagerShouldReturnManager() {
            assertNotNull(engine.getAIVisionManager());
            assertTrue(engine.getAIVisionManager() instanceof AIVisionManager);
        }

        @Test
        @DisplayName("getFactionManager 应返回阵营管理器")
        void getFactionManagerShouldReturnManager() {
            assertNotNull(engine.getFactionManager());
            assertTrue(engine.getFactionManager() instanceof FactionManager);
        }

        @Test
        @DisplayName("getSquadManager 应返回小队管理器")
        void getSquadManagerShouldReturnManager() {
            assertNotNull(engine.getSquadManager());
            assertTrue(engine.getSquadManager() instanceof SquadManager);
        }
    }

    @Nested
    @DisplayName("AI 状态类验证")
    class AIStateTests {

        @Test
        @DisplayName("AIState 构造函数应正确初始化")
        void aiStateConstructorShouldInitialize() {
            TarkovAIEngine.AIState state = new TarkovAIEngine.AIState("scav");
            
            assertEquals("scav", state.tier);
            assertNull(state.target);
            assertEquals(0, state.ticksSinceEngage);
        }

        @Test
        @DisplayName("AIState 不同 tier 应正确设置")
        void aiStateDifferentTiersShouldSetCorrectly() {
            TarkovAIEngine.AIState scavState = new TarkovAIEngine.AIState("scav");
            TarkovAIEngine.AIState pmcState = new TarkovAIEngine.AIState("pmc");
            TarkovAIEngine.AIState bossState = new TarkovAIEngine.AIState("boss");
            
            assertEquals("scav", scavState.tier);
            assertEquals("pmc", pmcState.tier);
            assertEquals("boss", bossState.tier);
        }
    }
}