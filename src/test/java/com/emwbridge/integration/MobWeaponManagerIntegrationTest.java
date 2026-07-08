package com.emwbridge.integration;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMConfigCache;
import com.emwbridge.config.WeaponMetaCache;
import com.emwbridge.managers.MobWeaponManager;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Field;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MobWeaponManager 集成测试
 *
 * 测试武器管理器的核心流程：绑定、射击、解绑。
 * 使用 mockStatic 屏蔽 WeaponMechanicsAPI 静态调用，其余使用 Mockito 模拟 Bukkit API。
 */
@DisplayName("MobWeaponManager 集成测试")
class MobWeaponManagerIntegrationTest {

    private static MockedStatic<WeaponMechanicsAPI> wmApiMock;

    private EMWMBridge mockPlugin;
    private FileConfiguration mockConfig;
    private EMWMConfigCache mockConfigCache;
    private WeaponMetaCache mockWeaponMetaCache;
    private MobWeaponManager weaponManager;
    private Zombie mockEntity;
    private EntityEquipment mockEquipment;
    private ItemStack mockMainHand;
    private ItemMeta mockItemMeta;

    @BeforeAll
    static void setupStaticMocks() {
        wmApiMock = mockStatic(WeaponMechanicsAPI.class);
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (wmApiMock != null) {
            wmApiMock.close();
        }
    }

    @BeforeEach
    void setUp() {
        // 重置 mockStatic 的桩（清除上一测试的特定桩）
        wmApiMock.reset();
        // 重新注册通用桩
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon(anyString()))
                .thenAnswer(invocation -> createWeaponItem(invocation.getArgument(0)));

        // 创建 mock 插件（RETURNS_DEFAULTS 避免调用真实 getLogger() 等方法）
        mockPlugin = mock(EMWMBridge.class, RETURNS_DEFAULTS);
        mockConfig = mock(FileConfiguration.class);
        mockConfigCache = mock(EMWMConfigCache.class);
        mockWeaponMetaCache = mock(WeaponMetaCache.class);

        doReturn(mockConfig).when(mockPlugin).getConfig();
        doReturn(mockConfigCache).when(mockPlugin).getEMWMConfigCache();
        doReturn(Logger.getAnonymousLogger()).when(mockPlugin).getLogger();
        doReturn(mockWeaponMetaCache).when(mockConfigCache).getWeaponMetaCache();

        // 配置武器池
        doReturn("AK_47").when(mockConfig).getString("weapons.default-weapon", "AK_47");
        doReturn(List.of("AK_47", "M4A1")).when(mockConfig).getStringList("weapons.scav-pool");
        doReturn(List.of("M4A1")).when(mockConfig).getStringList("weapons.pmc-pool");
        doReturn(List.of("PKM")).when(mockConfig).getStringList("weapons.boss-pool");
        doReturn(true).when(mockConfig).getBoolean("durability.enabled", true);
        doReturn(100).when(mockConfig).getInt("weapons.base-durability", 100);
        doReturn(1).when(mockConfig).getInt("durability.decay-per-shot", 1);
        doReturn(0.2).when(mockConfig).getDouble("durability.malfunction-chance-threshold", 0.2);
        doReturn(0.02).when(mockConfig).getDouble("durability.accuracy-penalty-per-10-percent", 0.02);

        // 创建 mock 实体
        mockEntity = mock(Zombie.class);
        mockEquipment = mock(EntityEquipment.class);
        mockMainHand = mock(ItemStack.class);
        mockItemMeta = mock(ItemMeta.class);

        UUID uuid = UUID.randomUUID();
        when(mockEntity.getUniqueId()).thenReturn(uuid);
        when(mockEntity.getEquipment()).thenReturn(mockEquipment);
        when(mockEntity.getName()).thenReturn("TestZombie");
        when(mockEntity.isValid()).thenReturn(true);
        when(mockEntity.isDead()).thenReturn(false);

        when(mockEquipment.getItemInMainHand()).thenReturn(mockMainHand);
        when(mockMainHand.getType()).thenReturn(Material.AIR);

        // 创建武器管理器
        weaponManager = new MobWeaponManager(mockPlugin);
        weaponManager.reload();
    }

    @Test
    @DisplayName("绑定武器 → 武器管理器缓存命中")
    void testBindWeaponCachesEntity() {
        // Mock WeaponMechanicsAPI.generateWeapon 返回有效武器
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("AK_47"))
                .thenReturn(createWeaponItem("AK_47"));

        boolean result = weaponManager.bindWeapon(mockEntity, "AK_47");

        assertTrue(result, "绑定应成功");
        assertTrue(weaponManager.hasWeapon(mockEntity), "实体应在武器缓存中");
        assertEquals("AK_47", weaponManager.getWeaponTitle(mockEntity), "武器标题应匹配");
    }

    @Test
    @DisplayName("绑定武器 → 设置主手物品")
    void testBindWeaponSetsMainHand() {
        ItemStack weaponItem = createWeaponItem("AK_47");
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("AK_47"))
                .thenReturn(weaponItem);

        weaponManager.bindWeapon(mockEntity, "AK_47");

        verify(mockEquipment).setItemInMainHand(weaponItem);
    }

    @Test
    @DisplayName("绑定不存在的武器 → 返回 false")
    void testBindNonexistentWeaponFails() {
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("NONEXISTENT"))
                .thenReturn(null);

        boolean result = weaponManager.bindWeapon(mockEntity, "NONEXISTENT");

        assertFalse(result, "不存在的武器绑定应失败");
        assertFalse(weaponManager.hasWeapon(mockEntity), "缓存中不应有实体");
    }

    @Test
    @DisplayName("射击 → 弹药减少 1")
    void testShootDecrementsAmmo() {
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("AK_47"))
                .thenReturn(createWeaponItem("AK_47"));
        weaponManager.bindWeapon(mockEntity, "AK_47");

        int ammoBefore = weaponManager.getCurrentAmmo(mockEntity);
        assertTrue(ammoBefore > 0, "初始弹药应大于0");

        boolean shot = weaponManager.shoot(mockEntity,
                new org.bukkit.Location(mock(org.bukkit.World.class), 10, 64, 10));

        assertTrue(shot, "射击应成功");
        assertEquals(ammoBefore - 1, weaponManager.getCurrentAmmo(mockEntity), "弹药应减少1");
    }

    @Test
    @DisplayName("需求5: consumeAmmo=false 连射弹药不递减(经济护栏)")
    void testConsumeAmmoFalseKeepsAmmo() throws Exception {
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("AK_47"))
                .thenReturn(createWeaponItem("AK_47"));
        weaponManager.bindWeapon(mockEntity, "AK_47");

        // 取出实例并将 consumeAmmo 置为 false（模拟 GreyZone 模板配置）
        Field cacheField = MobWeaponManager.class.getDeclaredField("weaponCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, MobWeaponManager.MobWeaponInstance> cache =
                (Map<UUID, MobWeaponManager.MobWeaponInstance>) cacheField.get(weaponManager);
        MobWeaponManager.MobWeaponInstance instance = cache.get(mockEntity.getUniqueId());
        assertNotNull(instance, "绑定后实例应存在");
        instance.setConsumeAmmo(false);
        assertFalse(instance.consumesAmmo(), "consumeAmmo 应为 false");

        int ammoBefore = instance.getCurrentAmmo();
        assertTrue(ammoBefore > 0, "初始弹药应大于0");
        org.bukkit.Location target = new org.bukkit.Location(mock(org.bukkit.World.class), 10, 64, 10);

        // 连射 100 发：每次重置射击冷却(lastShotTime=0)以绕过 fireRate 限流
        Field lastShotField = MobWeaponManager.MobWeaponInstance.class.getDeclaredField("lastShotTime");
        lastShotField.setAccessible(true);
        for (int i = 0; i < 100; i++) {
            lastShotField.set(instance, 0L);
            weaponManager.shoot(mockEntity, target);
        }

        // 验收：AI 自有弹药(emwm_ammo)不递减；玩家货币弹药本就不经此路径(WM shoot 无弹药参)
        assertEquals(ammoBefore, instance.getCurrentAmmo(), "无限弹药: 连射100发 emwm_ammo 不应递减");
        assertEquals(ammoBefore, weaponManager.getCurrentAmmo(mockEntity), "通过 Manager 读取的弹药也应不变");
    }

    @Test
    @DisplayName("空弹匣射击 → 返回 false")
    void testEmptyMagazineCannotShoot() throws Exception {
        // 创建一个弹药为0的实例直接放入缓存
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("AK_47"))
                .thenReturn(createWeaponItem("AK_47"));
        weaponManager.bindWeapon(mockEntity, "AK_47");

        // 通过反射直接设置弹药为0（绕过射击冷却 fireRateMs 限制，
        // canShoot() 检查 System.currentTimeMillis() - lastShotTime < fireRateMs，
        // 循环 shoot 太快会全部被冷却拦截）
        Field cacheField = MobWeaponManager.class.getDeclaredField("weaponCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, MobWeaponManager.MobWeaponInstance> cache =
                (Map<UUID, MobWeaponManager.MobWeaponInstance>) cacheField.get(weaponManager);
        MobWeaponManager.MobWeaponInstance instance = cache.get(mockEntity.getUniqueId());
        assertNotNull(instance, "绑定后实例应存在");
        instance.setCurrentAmmo(0);

        assertEquals(0, weaponManager.getCurrentAmmo(mockEntity), "弹药应为0");
        assertTrue(weaponManager.isMagazineEmpty(mockEntity), "弹匣应为空");

        boolean shot = weaponManager.shoot(mockEntity,
                new org.bukkit.Location(mock(org.bukkit.World.class), 10, 64, 10));
        assertFalse(shot, "空弹匣射击应失败");
    }

    @Test
    @DisplayName("解绑武器 → 缓存清除")
    void testUnbindWeaponClearsCache() {
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("AK_47"))
                .thenReturn(createWeaponItem("AK_47"));
        weaponManager.bindWeapon(mockEntity, "AK_47");

        assertTrue(weaponManager.hasWeapon(mockEntity), "解绑前应有武器");

        weaponManager.unbindWeapon(mockEntity);

        assertFalse(weaponManager.hasWeapon(mockEntity), "解绑后缓存应清除");
        assertNull(weaponManager.getWeaponTitle(mockEntity), "解绑后武器标题应为null");
    }

    @Test
    @DisplayName("精度修正 → 满耐久返回 1.0")
    void testAccuracyModifierFullDurability() {
        wmApiMock.when(() -> WeaponMechanicsAPI.generateWeapon("AK_47"))
                .thenReturn(createWeaponItem("AK_47"));
        weaponManager.bindWeapon(mockEntity, "AK_47");

        double modifier = weaponManager.getAccuracyModifier(mockEntity);
        assertEquals(1.0, modifier, 0.01, "满耐久精度修正应为1.0");
    }

    @Test
    @DisplayName("武器池随机选择 → 返回池中武器")
    void testRandomWeaponFromPool() {
        // 多次调用，确保返回的武器在池中
        for (int i = 0; i < 50; i++) {
            String weapon = weaponManager.getRandomWeaponForTier("scav");
            assertTrue(weapon.equals("AK_47") || weapon.equals("M4A1"),
                    "scav武器应从池中选取");
        }
    }

    private ItemStack createWeaponItem(String title) {
        // 返回基本 mock — 不在内部使用 when() 以避免嵌套 stubbing 导致 UnfinishedStubbingException
        // bindWeapon() 内部对 getItemMeta() 返回 null 有 null-check，不影响功能逻辑
        return mock(ItemStack.class);
    }
}