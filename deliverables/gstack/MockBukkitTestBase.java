package com.emwbridge.test.mockbukkit;

import com.emwbridge.EMWMBridge;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * MockBukkit 测试基类
 *
 * 提供 Bukkit 服务器级模拟能力:
 * - 完整的 JavaPlugin 生命周期 (onEnable/onDisable)
 * - 事件分发 (callEvent)
 * - 调度器时间推进 (performTicks)
 * - 命令执行 (execute)
 * - 世界/实体/物品模拟
 *
 * 与 IntegrationTestBase 的区别:
 * - IntegrationTestBase: Mockito mock Bukkit API 边界，用于单元/边界集成测试
 * - MockBukkitTestBase: 完整服务器模拟，用于 Bukkit 行为级测试
 *
 * 两者互补: MockBukkit 管不到 WM/EM 静态方法，继续用 mockStatic;
 * MockBukkit 能管的 (scheduler/event/lifecycle/command)，用它替代手动 mock。
 */
public abstract class MockBukkitTestBase {

    protected static ServerMock server;
    protected static EMWMBridge plugin;

    /**
     * WeaponMechanicsAPI 静态 mock — MockBukkit 不覆盖第三方插件静态方法
     */
    private static org.mockito.MockedStatic<WeaponMechanicsAPI> wmApiMock;

    @BeforeAll
    static void setUpServer() {
        // 启动 MockBukkit 服务器
        server = MockBukkit.mock();

        // mockStatic 必须在 MockBukkit.load 之前设置，
        // 因为 onEnable() 中可能调用 WM API
        wmApiMock = org.mockito.Mockito.mockStatic(WeaponMechanicsAPI.class);
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> createMockWeaponItem(invocation.getArgument(0)));
        wmApiMock.when(() -> WeaponMechanicsAPI.shoot(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .then(invocation -> null);

        // 加载插件 — 触发真实 onEnable()
        // 注意: 如果 onEnable 中的依赖检查 (checkDependencies) 会失败,
        // 需要在 load 之前注册 mock 插件:
        server.getPluginManager().registerInterface(MockEliteMobsPlugin.class);
        // 或者使用 MockBukkit 的简化方式:
        // server.getPluginManager().enablePlugin(mockPlugin);

        // 加载 EM-WM-Bridge 插件
        plugin = MockBukkit.load(EMWMBridge.class);
    }

    @AfterAll
    static void tearDownServer() {
        if (wmApiMock != null) {
            wmApiMock.close();
        }
        MockBukkit.unmock();
        server = null;
        plugin = null;
    }

    /**
     * 推进服务器时间 (模拟 tick)
     * 用于测试延迟任务 (runTaskLater 回调)
     */
    protected void advanceTicks(int ticks) {
        server.getScheduler().performTicks(ticks);
    }

    /**
     * 创建模拟的 WM 武器物品
     */
    private static ItemStack createMockWeaponItem(String weaponTitle) {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c" + weaponTitle);
            item.setItemMeta(meta);
        }
        return item;
    }
}
