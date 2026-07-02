package com.emwbridge.integration;

import com.emwbridge.EMWMBridge;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 集成测试基类
 *
 * 使用 Mockito 模拟 Bukkit API 边界，结合 mockStatic 屏蔽 WeaponMechanics 静态调用，
 * 实现插件模块间真实交互的集成测试（无 Bukkit 服务器依赖）。
 */
@ExtendWith(MockitoExtension.class)
public abstract class IntegrationTestBase {

    @Mock
    protected Server mockServer;

    @Mock
    protected PluginManager mockPluginManager;

    @Mock
    protected World mockWorld;

    // WeaponMechanicsAPI 静态 mock（类级别，只设置一次）
    private static MockedStatic<WeaponMechanicsAPI> wmApiMock;

    @BeforeAll
    static void setupStaticMocks() {
        wmApiMock = mockStatic(WeaponMechanicsAPI.class);
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon(anyString()))
                .thenAnswer(invocation -> createMockWeaponItem(invocation.getArgument(0)));
        // shoot 的两个重载
        wmApiMock.when(() -> WeaponMechanicsAPI.shoot(
                any(LivingEntity.class), anyString(), any(Location.class)))
                .then(invocation -> null);
        wmApiMock.when(() -> WeaponMechanicsAPI.shoot(
                any(LivingEntity.class), anyString(), any(org.bukkit.util.Vector.class)))
                .then(invocation -> null);
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (wmApiMock != null) {
            wmApiMock.close();
        }
    }

    @BeforeEach
    void setUpBase() {
        // 设置 Bukkit 静态 mock
        lenient().when(mockServer.getPluginManager()).thenReturn(mockPluginManager);
        lenient().when(mockPluginManager.isPluginEnabled("EliteMobs")).thenReturn(true);
        lenient().when(mockPluginManager.isPluginEnabled("WeaponMechanics")).thenReturn(true);

        // 设置世界
        lenient().when(mockWorld.getName()).thenReturn("test_world");
    }

    @AfterEach
    void tearDownBase() {
        // 清理 Bukkit 静态状态
        try {
            java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * 创建一个模拟的 WM 武器物品
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

    // ========== 测试辅助方法 ==========

    /**
     * 创建一个模拟的怪物实体（Zombie），带自定义名称和装备槽
     */
    protected Zombie createMockMob(String customName) {
        Zombie mob = mock(Zombie.class);
        EntityEquipment equipment = mock(EntityEquipment.class);
        ItemStack mainHand = mock(ItemStack.class);
        org.bukkit.entity.Player target = mock(org.bukkit.entity.Player.class);

        lenient().when(mob.getCustomName()).thenReturn(customName);
        lenient().when(mob.getEquipment()).thenReturn(equipment);
        lenient().when(mob.getType()).thenReturn(EntityType.ZOMBIE);
        lenient().when(mob.getWorld()).thenReturn(mockWorld);
        lenient().when(mob.getLocation()).thenReturn(new Location(mockWorld, 0, 64, 0));
        lenient().when(mob.getTarget()).thenReturn(target);
        lenient().when(mob.isDead()).thenReturn(false);

        lenient().when(equipment.getItemInMainHand()).thenReturn(mainHand);
        lenient().when(mainHand.getType()).thenReturn(Material.AIR);

        return mob;
    }

    /**
     * 设置 Bukkit 静态 Server 引用（用于 EMWMBridge 等需要 Bukkit.getServer() 的代码）
     */
    protected void setBukkitServer(Server server) {
        try {
            java.lang.reflect.Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Bukkit server", e);
        }
    }
}