package com.emwbridge.utils;

import com.emwbridge.EMWMBridge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DebugManagerTest {

    @Nested
    @DisplayName("调试等级管理")
    class DebugLevelTests {

        @Test
        @DisplayName("DebugLevel 枚举值应正确")
        void debugLevelEnumValuesShouldBeCorrect() {
            assertEquals(0, DebugManager.DebugLevel.OFF.getLevel());
            assertEquals(1, DebugManager.DebugLevel.BASIC.getLevel());
            assertEquals(2, DebugManager.DebugLevel.DETAILED.getLevel());
            assertEquals(3, DebugManager.DebugLevel.TRACE.getLevel());
        }

        @Test
        @DisplayName("DebugLevel getName 应正确")
        void debugLevelGetNameShouldBeCorrect() {
            assertEquals("关闭", DebugManager.DebugLevel.OFF.getName());
            assertEquals("基础", DebugManager.DebugLevel.BASIC.getName());
            assertEquals("详细", DebugManager.DebugLevel.DETAILED.getName());
            assertEquals("追踪", DebugManager.DebugLevel.TRACE.getName());
        }
    }

    @Nested
    @DisplayName("计数器管理")
    class CounterTests {

        @Test
        @DisplayName("incrementCounter 应正确计数")
        void incrementCounterShouldCountCorrectly() {
            var plugin = mockPlugin();
            DebugManager dm = new DebugManager(plugin);

            assertEquals(0, dm.getCounter("test"));
            assertEquals(1, dm.incrementCounter("test"));
            assertEquals(2, dm.incrementCounter("test"));
            assertEquals(2, dm.getCounter("test"));
        }

        @Test
        @DisplayName("resetCounter 应重置指定计数器")
        void resetCounterShouldReset() {
            var plugin = mockPlugin();
            DebugManager dm = new DebugManager(plugin);

            dm.incrementCounter("test");
            assertEquals(1, dm.getCounter("test"));

            dm.resetCounter("test");
            assertEquals(0, dm.getCounter("test"));
        }

        @Test
        @DisplayName("resetAllCounters 应重置所有计数器")
        void resetAllCountersShouldResetAll() {
            var plugin = mockPlugin();
            DebugManager dm = new DebugManager(plugin);

            dm.incrementCounter("test1");
            dm.incrementCounter("test2");

            assertEquals(1, dm.getCounter("test1"));
            assertEquals(1, dm.getCounter("test2"));

            dm.resetAllCounters();

            assertEquals(0, dm.getCounter("test1"));
            assertEquals(0, dm.getCounter("test2"));
        }

        @Test
        @DisplayName("getAllCounters 应返回副本")
        void getAllCountersShouldReturnCopy() {
            var plugin = mockPlugin();
            DebugManager dm = new DebugManager(plugin);

            dm.incrementCounter("test");
            Map<String, Integer> counters = dm.getAllCounters();

            counters.put("test", 999);

            assertEquals(1, dm.getCounter("test"));
        }
    }

    @Nested
    @DisplayName("实体调试等级")
    class EntityLevelTests {

        @Test
        @DisplayName("setEntityDebugLevel 设置为 null 应移除")
        void setEntityDebugLevelWithNullShouldRemove() {
            var plugin = mockPlugin();
            DebugManager dm = new DebugManager(plugin);
            UUID uuid = UUID.randomUUID();

            dm.setEntityDebugLevel(uuid, DebugManager.DebugLevel.TRACE);
            assertEquals(DebugManager.DebugLevel.TRACE, dm.getEntityDebugLevel(uuid));

            dm.setEntityDebugLevel(uuid, null);
            assertEquals(dm.getGlobalDebugLevel(), dm.getEntityDebugLevel(uuid));
        }
    }

    @Nested
    @DisplayName("调试报告")
    class ReportTests {

        @Test
        @DisplayName("generateDebugReport 应包含正确信息")
        void generateDebugReportShouldContainInfo() {
            var plugin = mockPlugin();
            DebugManager dm = new DebugManager(plugin);

            dm.incrementCounter("shoot");
            dm.incrementCounter("reload");

            String report = dm.generateDebugReport();

            assertTrue(report.contains("EM-WM-Bridge Debug Report"));
            assertTrue(report.contains("Global Level:"));
            assertTrue(report.contains("shoot:"));
            assertTrue(report.contains("reload:"));
        }
    }

    private EMWMBridge mockPlugin() {
        EMWMBridge plugin = Mockito.mock(EMWMBridge.class, Mockito.withSettings().lenient());
        Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("Test"));
        
        var config = Mockito.mock(org.bukkit.configuration.file.FileConfiguration.class, Mockito.withSettings().lenient());
        Mockito.when(config.getString("settings.debug-level", "BASIC")).thenReturn("BASIC");
        Mockito.when(config.getBoolean("settings.debug", true)).thenReturn(true);
        Mockito.when(plugin.getConfig()).thenReturn(config);
        
        return plugin;
    }
}
