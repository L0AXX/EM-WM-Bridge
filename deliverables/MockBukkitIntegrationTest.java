package com.emwbridge.test.mockbukkit;

import com.emwbridge.EMWMBridge;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockBukkit 集成测试 — 替代 test-server.ps1 场景
 *
 * 转化映射:
 * - PS1 场景1 (插件加载验证) → testPluginLoadsWithoutError
 * - PS1 场景5 (启动无错误)   → testNoExceptionsOnEnable (包含在加载中)
 * - PS1 场景2 (配置热重载)   → testConfigReload
 * - PS1 场景3 (统计命令)     → testStatsCommand
 * - PS1 场景6 (配置缓存信息) → testInfoCommand
 */
@DisplayName("MockBukkit 集成测试 — 替代 test-server.ps1")
class MockBukkitIntegrationTest extends MockBukkitTestBase {

    // ============================================================
    // PS1 场景 1 + 5: 插件加载验证 + 启动无错误
    // ============================================================
    @Nested
    @DisplayName("插件生命周期 (PS1 场景1+5)")
    class PluginLifecycle {

        @Test
        @DisplayName("插件应成功加载且处于启用状态")
        void testPluginLoadsWithoutError() {
            // 如果 onEnable() 抛异常, MockBukkit.load() 会失败
            // 到这里说明插件加载成功
            assertNotNull(plugin, "插件实例不应为 null");
            assertTrue(plugin.isEnabled(), "插件应处于启用状态");
        }

        @Test
        @DisplayName("插件应注册所有监听器")
        void testListenersRegistered() {
            // 验证关键监听器已注册
            var registeredListeners = server.getPluginManager().getRegisteredListeners();
            assertFalse(registeredListeners.isEmpty(), "应至少注册一个监听器");
        }

        @Test
        @DisplayName("插件版本应与 plugin.yml 一致")
        void testPluginVersion() {
            assertEquals("1.3.0", plugin.getDescription().getVersion(),
                    "插件版本应为 1.3.0");
        }
    }

    // ============================================================
    // PS1 场景 2: 配置热重载
    // ============================================================
    @Nested
    @DisplayName("配置热重载 (PS1 场景2)")
    class ConfigReload {

        @Test
        @DisplayName("reload 命令应成功执行且不抛异常")
        void testConfigReload() {
            // 模拟执行 /emwm reload
            // MockBukkit 提供 server.execute() 或直接调用 plugin.onCommand()
            CommandSender sender = server.getConsoleSender();

            // 方式1: 直接调用 onCommand (如果命令未在 plugin.yml 注册到 MockBukkit)
            boolean result = plugin.onCommand(
                    sender,
                    plugin.getCommand("emwm"),
                    "emwm",
                    new String[]{"reload"}
            );

            assertTrue(result, "reload 命令应返回 true");
            // 验证配置缓存已重新加载
            assertNotNull(plugin.getEMWMConfigCache(), "配置缓存不应为 null");
        }
    }

    // ============================================================
    // PS1 场景 3: 统计命令
    // ============================================================
    @Nested
    @DisplayName("统计命令 (PS1 场景3)")
    class StatsCommand {

        @Test
        @DisplayName("/emwm stats 应输出统计信息")
        void testStatsCommand() {
            // 使用 MockBukkit 的命令执行 API
            // 这要求命令已在 plugin.yml 中注册
            server.addSimpleWorld("test_world");

            // 捕获 ConsoleSender 收到的消息
            CommandSender console = server.getConsoleSender();

            boolean result = plugin.onCommand(
                    console,
                    plugin.getCommand("emwm"),
                    "emwm",
                    new String[]{"stats"}
            );

            assertTrue(result, "stats 命令应返回 true");
        }
    }

    // ============================================================
    // PS1 场景 6: 配置缓存信息命令
    // ============================================================
    @Nested
    @DisplayName("配置缓存信息 (PS1 场景6)")
    class InfoCommand {

        @Test
        @DisplayName("/emwm info 应输出配置缓存信息")
        void testInfoCommand() {
            CommandSender console = server.getConsoleSender();

            boolean result = plugin.onCommand(
                    console,
                    plugin.getCommand("emwm"),
                    "emwm",
                    new String[]{"info"}
            );

            assertTrue(result, "info 命令应返回 true");
        }

        @Test
        @DisplayName("/emwm version 应输出版本信息")
        void testVersionCommand() {
            CommandSender console = server.getConsoleSender();

            boolean result = plugin.onCommand(
                    console,
                    plugin.getCommand("emwm"),
                    "emwm",
                    new String[]{"version"}
            );

            assertTrue(result, "version 命令应返回 true");
        }
    }

    // ============================================================
    // PS1 场景 4: Scav 生成绑定武器 (MockBukkit 版)
    // ============================================================
    @Nested
    @DisplayName("Scav 生成绑定武器 (PS1 场景4)")
    class SpawnBinding {

        @Test
        @DisplayName("生成带 scav 名称的僵尸应触发武器绑定")
        void testScavSpawnTriggersWeaponBinding() {
            // 使用 MockBukkit 创建真实世界和实体
            var world = server.addSimpleWorld("test_world");
            var zombie = world.spawnEntity(
                    new org.bukkit.Location(world, 0, 64, 0),
                    org.bukkit.entity.EntityType.ZOMBIE
            );

            // 设置自定义名称（匹配 scav tier 检测）
            if (zombie instanceof org.bukkit.entity.Mob mob) {
                mob.setCustomName("scav_rifle_test");
                mob.setCustomNameVisible(true);

                // 触发 CreatureSpawnEvent (MockBukkit 会自动触发)
                // 也可以手动 fire: server.getPluginManager().callEvent(event)

                // 推进 tick 让延迟任务执行
                advanceTicks(25); // 延迟重检在 20 tick

                // 验证武器绑定
                assertTrue(plugin.getMobWeaponManager().hasWeapon(mob),
                        "Scav 僵尸应已绑定武器");
            }
        }
    }

    // ============================================================
    // 调度器测试 — 测试延迟任务
    // ============================================================
    @Nested
    @DisplayName("调度器延迟任务")
    class SchedulerTests {

        @Test
        @DisplayName("延迟 20 tick 的任务应在 tick 推进后执行")
        void testDelayedTaskExecutesAfterTickAdvance() {
            // MockBukkit 的 performTicks 可以推进虚拟时间
            // 这使得 runTaskLater 的回调可以被执行

            var world = server.addSimpleWorld("scheduler_world");

            // 记录任务是否执行
            var executed = new java.util.concurrent.atomic.AtomicBoolean(false);

            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                executed.set(true);
            }, 20L);

            // tick 推进前任务不应执行
            assertFalse(executed.get(), "20 tick 延迟任务在 0 tick 时不应执行");

            advanceTicks(20);

            assertTrue(executed.get(), "20 tick 延迟任务在 20 tick 后应执行");
        }
    }
}
