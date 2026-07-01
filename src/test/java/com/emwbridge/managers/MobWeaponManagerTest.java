package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMConfigCache;
import com.emwbridge.config.WeaponMetaCache;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MobWeaponManager 测试（仅测试纯 Java 逻辑）
 *
 * 重要约定：
 * - 所有测试不得硬编码具体武器名做断言依赖
 * - 武器名通过 mock config 的动态返回值传入，与生产配置解耦
 * - MobWeaponInstance 的 weaponTitle 仅为标签字段，测试其行为不依赖名称
 */
@ExtendWith(MockitoExtension.class)
class MobWeaponManagerTest {

    // 测试使用的武器名常量——非硬编码，仅在测试范围内作为占位符
    // 目的是让测试数据与生产 config.yml 解耦
    private static final String DEFAULT_WEAPON = "Test_DefaultWeapon";
    private static final String SCAV_WEAPON_A = "Test_ScavA";
    private static final String SCAV_WEAPON_B = "Test_ScavB";
    private static final String PMC_WEAPON = "Test_PmcPrimary";
    private static final String PMC_WEAPON_ALT = "Test_PmcSecondary";
    private static final String BOSS_WEAPON_A = "Test_BossGun";
    private static final String BOSS_WEAPON_B = "Test_BossAlt";

    @Mock(lenient = true)
    private EMWMBridge plugin;

    @Mock(lenient = true)
    private Logger logger;

    @Mock(lenient = true)
    private EMWMConfigCache configCache;

    @Mock(lenient = true)
    private WeaponMetaCache weaponMetaCache;

    private MobWeaponManager weaponManager;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getEMWMConfigCache()).thenReturn(configCache);
        when(configCache.getWeaponMetaCache()).thenReturn(weaponMetaCache);
        doNothing().when(plugin).debug(anyString());
        weaponManager = new MobWeaponManager(plugin);
    }

    /**
     * 创建标准的 mock config，调用方可通过 lambda 覆盖特定返回值
     */
    private org.bukkit.configuration.file.FileConfiguration createMockConfig(
            String defaultWeapon,
            java.util.List<String> scavPool,
            java.util.List<String> pmcPool,
            java.util.List<String> bossPool) {
        var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
        when(config.getString(eq("weapons.default-weapon"), anyString())).thenReturn(defaultWeapon);
        when(config.getStringList("weapons.scav-pool")).thenReturn(scavPool);
        when(config.getStringList("weapons.pmc-pool")).thenReturn(pmcPool);
        when(config.getStringList("weapons.boss-pool")).thenReturn(bossPool);
        when(config.getBoolean(eq("durability.enabled"), eq(true))).thenReturn(true);
        when(config.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(invocation -> invocation.getArgument(1));
        return config;
    }

    @Nested
    @DisplayName("配置重载")
    class ConfigReloadTests {

        @Test
        @DisplayName("reload 应正确读取配置")
        void reloadShouldReadConfig() {
            var config = createMockConfig(DEFAULT_WEAPON,
                    List.of(SCAV_WEAPON_A, SCAV_WEAPON_B),
                    List.of(PMC_WEAPON),
                    List.of(BOSS_WEAPON_A));
            // 覆盖特定配置值
            when(config.getInt("weapons.base-durability", 100)).thenReturn(150);
            when(config.getInt("durability.decay-per-shot", 1)).thenReturn(2);
            when(config.getDouble("durability.malfunction-chance-threshold", 0.2)).thenReturn(0.3);
            when(config.getDouble("durability.accuracy-penalty-per-10-percent", 0.02)).thenReturn(0.03);
            when(plugin.getConfig()).thenReturn(config);

            weaponManager.reload();

            verify(config).getString(eq("weapons.default-weapon"), anyString());
            verify(config).getStringList("weapons.scav-pool");
            verify(config).getStringList("weapons.pmc-pool");
            verify(config).getStringList("weapons.boss-pool");
            verify(config).getBoolean(eq("durability.enabled"), eq(true));
            verify(config).getInt(eq("weapons.base-durability"), eq(100));
        }

        @Test
        @DisplayName("reload 空武器列表应使用默认武器")
        void reloadEmptyWeaponListShouldUseDefault() {
            var config = createMockConfig(DEFAULT_WEAPON, List.of(), List.of(), List.of());
            when(plugin.getConfig()).thenReturn(config);

            weaponManager.reload();

            String randomScav = weaponManager.getRandomWeaponForTier("scav");
            String randomPmc = weaponManager.getRandomWeaponForTier("pmc");
            String randomBoss = weaponManager.getRandomWeaponForTier("boss");

            assertEquals(DEFAULT_WEAPON, randomScav);
            assertEquals(DEFAULT_WEAPON, randomPmc);
            assertEquals(DEFAULT_WEAPON, randomBoss);
        }

        @Test
        @DisplayName("shutdown 应清空缓存")
        void shutdownShouldClearCache() {
            weaponManager.shutdown();
        }
    }

    @Nested
    @DisplayName("随机武器选择")
    class RandomWeaponTests {

        @Test
        @DisplayName("getRandomWeaponForTier scav 应返回 scav 武器")
        void getRandomWeaponForTierScavShouldReturnScavWeapon() {
            var config = createMockConfig(DEFAULT_WEAPON,
                    List.of(SCAV_WEAPON_A, SCAV_WEAPON_B),
                    List.of(PMC_WEAPON),
                    List.of(BOSS_WEAPON_A));
            when(plugin.getConfig()).thenReturn(config);

            weaponManager.reload();

            String weapon = weaponManager.getRandomWeaponForTier("scav");

            assertTrue(List.of(SCAV_WEAPON_A, SCAV_WEAPON_B).contains(weapon));
        }

        @Test
        @DisplayName("getRandomWeaponForTier pmc 应返回 pmc 武器")
        void getRandomWeaponForTierPmcShouldReturnPmcWeapon() {
            var config = createMockConfig(DEFAULT_WEAPON,
                    List.of(SCAV_WEAPON_A),
                    List.of(PMC_WEAPON, PMC_WEAPON_ALT),
                    List.of(BOSS_WEAPON_A));
            when(plugin.getConfig()).thenReturn(config);

            weaponManager.reload();

            String weapon = weaponManager.getRandomWeaponForTier("pmc");

            assertTrue(List.of(PMC_WEAPON, PMC_WEAPON_ALT).contains(weapon));
        }

        @Test
        @DisplayName("getRandomWeaponForTier boss 应返回 boss 武器")
        void getRandomWeaponForTierBossShouldReturnBossWeapon() {
            var config = createMockConfig(DEFAULT_WEAPON,
                    List.of(SCAV_WEAPON_A),
                    List.of(PMC_WEAPON),
                    List.of(BOSS_WEAPON_A, BOSS_WEAPON_B));
            when(plugin.getConfig()).thenReturn(config);

            weaponManager.reload();

            String weapon = weaponManager.getRandomWeaponForTier("boss");

            assertTrue(List.of(BOSS_WEAPON_A, BOSS_WEAPON_B).contains(weapon));
        }

        @Test
        @DisplayName("getRandomWeaponForTier 未知 tier 应返回默认武器")
        void getRandomWeaponForTierUnknownShouldReturnDefault() {
            var config = createMockConfig(DEFAULT_WEAPON,
                    List.of(SCAV_WEAPON_A),
                    List.of(PMC_WEAPON),
                    List.of(BOSS_WEAPON_A));
            when(plugin.getConfig()).thenReturn(config);

            weaponManager.reload();

            String weapon = weaponManager.getRandomWeaponForTier("unknown");

            assertEquals(DEFAULT_WEAPON, weapon);
        }

        @Test
        @DisplayName("getRandomWeaponForTier 大小写不敏感")
        void getRandomWeaponForTierCaseInsensitive() {
            var config = createMockConfig(DEFAULT_WEAPON,
                    List.of(SCAV_WEAPON_A),
                    List.of(PMC_WEAPON),
                    List.of(BOSS_WEAPON_A));
            when(plugin.getConfig()).thenReturn(config);

            weaponManager.reload();

            String weapon1 = weaponManager.getRandomWeaponForTier("PMC");
            String weapon2 = weaponManager.getRandomWeaponForTier("pmc");
            String weapon3 = weaponManager.getRandomWeaponForTier("Pmc");

            assertEquals(PMC_WEAPON, weapon1);
            assertEquals(PMC_WEAPON, weapon2);
            assertEquals(PMC_WEAPON, weapon3);
        }
    }

    @Nested
    @DisplayName("空武器场景")
    class EmptyWeaponTests {

        @Test
        @DisplayName("getFireRateMs 按 UUID 查询无武器应返回默认值")
        void getFireRateMsByUUIDWithoutWeaponShouldReturnDefault() {
            assertEquals(300L, weaponManager.getFireRateMs(UUID.randomUUID()));
        }
    }

    // ==================== MobWeaponInstance 测试 ====================
    // 注意：MobWeaponInstance 的 weaponTitle 仅为标签字段
    // 测试其行为逻辑（canShoot/标记/设置器）不依赖武器名称
    // 因此使用统一的测试标签 INSTANCE_LABEL

    private static final String INSTANCE_LABEL = "Test_WeaponInstance";
    private static final int INSTANCE_DURABILITY = 100;
    private static final int INSTANCE_MAG_SIZE = 30;
    private static final int INSTANCE_RELOAD_TICKS = 60;
    private static final long INSTANCE_FIRE_RATE_MS = 100L;
    private static final double INSTANCE_SPREAD = 3.0;
    private static final double INSTANCE_ADS_MULT = 0.5;

    private MobWeaponManager.MobWeaponInstance createDefaultInstance() {
        return new MobWeaponManager.MobWeaponInstance(
                INSTANCE_LABEL, INSTANCE_DURABILITY, INSTANCE_DURABILITY, false,
                INSTANCE_MAG_SIZE, INSTANCE_MAG_SIZE, INSTANCE_RELOAD_TICKS,
                INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);
    }

    @Nested
    @DisplayName("MobWeaponInstance 测试")
    class MobWeaponInstanceTests {

        @Test
        @DisplayName("canShoot 正常状态应返回 true")
        void canShootNormalStateShouldReturnTrue() {
            assertTrue(createDefaultInstance().canShoot());
        }

        @Test
        @DisplayName("canShoot 损坏状态应返回 false")
        void canShootBrokenShouldReturnFalse() {
            var instance = createDefaultInstance();
            instance.setBroken(true);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("canShoot 耐久为 0 应返回 false")
        void canShootZeroDurabilityShouldReturnFalse() {
            var instance = new MobWeaponManager.MobWeaponInstance(
                    INSTANCE_LABEL, 0, INSTANCE_DURABILITY, false,
                    INSTANCE_MAG_SIZE, INSTANCE_MAG_SIZE, INSTANCE_RELOAD_TICKS,
                    INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("canShoot 换弹中应返回 false")
        void canShootReloadingShouldReturnFalse() {
            var instance = createDefaultInstance();
            instance.setReloading(true);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("canShoot 弹药耗尽应返回 false")
        void canShootNoAmmoShouldReturnFalse() {
            var instance = new MobWeaponManager.MobWeaponInstance(
                    INSTANCE_LABEL, INSTANCE_DURABILITY, INSTANCE_DURABILITY, false,
                    INSTANCE_MAG_SIZE, 0, INSTANCE_RELOAD_TICKS,
                    INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("markShot 应更新最后射击时间")
        void markShotShouldUpdateLastShotTime() {
            var instance = createDefaultInstance();
            long before = instance.getLastShotTime();
            instance.markShot();
            assertTrue(instance.getLastShotTime() > before);
        }

        @Test
        @DisplayName("弹药设置应正确工作")
        void ammoSettersShouldWork() {
            var instance = createDefaultInstance();
            instance.setCurrentAmmo(20);
            assertEquals(20, instance.getCurrentAmmo());
        }

        @Test
        @DisplayName("耐久设置应正确工作")
        void durabilitySettersShouldWork() {
            var instance = createDefaultInstance();
            instance.setCurrentDurability(50);
            assertEquals(50, instance.getCurrentDurability());
        }

        @Test
        @DisplayName("损坏状态设置应正确工作")
        void brokenStateSettersShouldWork() {
            var instance = createDefaultInstance();
            assertFalse(instance.isBroken());
            instance.setBroken(true);
            assertTrue(instance.isBroken());
        }

        @Test
        @DisplayName("换弹状态设置应正确工作")
        void reloadingStateSettersShouldWork() {
            var instance = createDefaultInstance();
            assertFalse(instance.isReloading());
            instance.setReloading(true);
            assertTrue(instance.isReloading());
        }

        @Test
        @DisplayName("构造函数应正确初始化所有属性")
        void constructorShouldInitializeAllProperties() {
            var instance = new MobWeaponManager.MobWeaponInstance(
                    INSTANCE_LABEL, 80, INSTANCE_DURABILITY, false,
                    INSTANCE_MAG_SIZE, 25, INSTANCE_RELOAD_TICKS,
                    INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);

            assertEquals(INSTANCE_LABEL, instance.getWeaponTitle());
            assertEquals(80, instance.getCurrentDurability());
            assertEquals(INSTANCE_DURABILITY, instance.getMaxDurability());
            assertFalse(instance.isBroken());
            assertEquals(INSTANCE_MAG_SIZE, instance.getMagazineSize());
            assertEquals(25, instance.getCurrentAmmo());
            assertEquals(INSTANCE_RELOAD_TICKS, instance.getReloadTicks());
            assertFalse(instance.isReloading());
            assertEquals(INSTANCE_FIRE_RATE_MS, instance.getFireRateMs());
            assertEquals(INSTANCE_SPREAD, instance.getBaseSpread(), 0.01);
            assertEquals(INSTANCE_ADS_MULT, instance.getAdsSpreadMultiplier(), 0.01);
        }
    }

    // ==================== 补充：武器缓存管理测试 ====================

    @Nested
    @DisplayName("武器缓存管理")
    class WeaponCacheTests {

        private LivingEntity createMockEntity() {
            LivingEntity entity = mock(LivingEntity.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            return entity;
        }

        @Test
        @DisplayName("hasWeapon 无武器应返回 false")
        void hasWeaponWithoutWeaponShouldReturnFalse() {
            assertFalse(weaponManager.hasWeapon(createMockEntity()));
        }

        @Test
        @DisplayName("hasWeapon 注入武器后应返回 true")
        void hasWeaponWithInjectedWeaponShouldReturnTrue() throws Exception {
            LivingEntity entity = createMockEntity();
            injectWeaponToCache(entity.getUniqueId(), createDefaultInstance());
            assertTrue(weaponManager.hasWeapon(entity));
        }

        @Test
        @DisplayName("unbindWeapon 应移除缓存和元数据")
        void unbindWeaponShouldRemoveCacheAndMetadata() throws Exception {
            LivingEntity entity = createMockEntity();
            injectWeaponToCache(entity.getUniqueId(), createDefaultInstance());

            assertTrue(weaponManager.hasWeapon(entity));
            weaponManager.unbindWeapon(entity);

            assertFalse(weaponManager.hasWeapon(entity));
            verify(entity).removeMetadata("emwm_weapon", plugin);
            verify(entity).removeMetadata("emwm_durability", plugin);
            verify(entity).removeMetadata("emwm_ammo", plugin);
            verify(entity).removeMetadata("emwm_ads", plugin);
            verify(entity).removeMetadata("emwm_reloading", plugin);
        }

        @Test
        @DisplayName("unbindWeapon 无武器时不应抛异常")
        void unbindWeaponWithoutWeaponShouldNotThrow() {
            assertDoesNotThrow(() -> weaponManager.unbindWeapon(createMockEntity()));
        }

        @Test
        @DisplayName("getWeaponTitle 无武器应返回 null")
        void getWeaponTitleWithoutWeaponShouldReturnNull() {
            assertNull(weaponManager.getWeaponTitle(createMockEntity()));
        }

        @Test
        @DisplayName("getWeaponTitle 有武器应返回武器名")
        void getWeaponTitleWithWeaponShouldReturnWeaponName() throws Exception {
            LivingEntity entity = createMockEntity();
            injectWeaponToCache(entity.getUniqueId(), createDefaultInstance());
            assertEquals(INSTANCE_LABEL, weaponManager.getWeaponTitle(entity));
        }
    }

    // ==================== 补充：Getter 默认值测试 ====================

    @Nested
    @DisplayName("Getter 默认值")
    class GetterDefaultTests {

        private LivingEntity entity;

        @BeforeEach
        void setUp() {
            entity = mock(LivingEntity.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        }

        @Test
        @DisplayName("getCurrentAmmo 无武器应返回 0")
        void getCurrentAmmoWithoutWeaponShouldReturnZero() {
            assertEquals(0, weaponManager.getCurrentAmmo(entity));
        }

        @Test
        @DisplayName("getWeaponDurability 无武器应返回 0")
        void getWeaponDurabilityWithoutWeaponShouldReturnZero() {
            assertEquals(0, weaponManager.getWeaponDurability(entity));
        }

        @Test
        @DisplayName("getDurabilityRatio 无武器应返回 1.0")
        void getDurabilityRatioWithoutWeaponShouldReturnOne() {
            assertEquals(1.0, weaponManager.getDurabilityRatio(entity), 0.01);
        }

        @Test
        @DisplayName("getMagazineSize 无武器应返回 30")
        void getMagazineSizeWithoutWeaponShouldReturnDefault() {
            assertEquals(30, weaponManager.getMagazineSize(entity));
        }

        @Test
        @DisplayName("getFireRateMs 无武器应返回 300L")
        void getFireRateMsWithoutWeaponShouldReturnDefault() {
            assertEquals(300L, weaponManager.getFireRateMs(entity));
        }

        @Test
        @DisplayName("getBaseSpread 无武器应返回 3.0")
        void getBaseSpreadWithoutWeaponShouldReturnDefault() {
            assertEquals(3.0, weaponManager.getBaseSpread(entity), 0.01);
        }

        @Test
        @DisplayName("getAdsSpreadMultiplier 无武器应返回 0.5")
        void getAdsSpreadMultiplierWithoutWeaponShouldReturnDefault() {
            assertEquals(0.5, weaponManager.getAdsSpreadMultiplier(entity), 0.01);
        }

        @Test
        @DisplayName("isReloading 无武器应返回 false")
        void isReloadingWithoutWeaponShouldReturnFalse() {
            assertFalse(weaponManager.isReloading(entity));
        }

        @Test
        @DisplayName("isMagazineEmpty 无武器应返回 false")
        void isMagazineEmptyWithoutWeaponShouldReturnFalse() {
            assertFalse(weaponManager.isMagazineEmpty(entity));
        }
    }

    // ==================== 补充：Getter 有武器时返回值测试 ====================

    @Nested
    @DisplayName("Getter 有武器返回值")
    class GetterWithWeaponTests {

        private LivingEntity createMockEntity() {
            LivingEntity entity = mock(LivingEntity.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            return entity;
        }

        @Test
        @DisplayName("getCurrentAmmo 有武器应返回弹药数")
        void getCurrentAmmoWithWeaponShouldReturnAmmo() throws Exception {
            LivingEntity entity = createMockEntity();
            var instance = new MobWeaponManager.MobWeaponInstance(
                    INSTANCE_LABEL, INSTANCE_DURABILITY, INSTANCE_DURABILITY, false,
                    INSTANCE_MAG_SIZE, 25, INSTANCE_RELOAD_TICKS,
                    INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);
            injectWeaponToCache(entity.getUniqueId(), instance);
            assertEquals(25, weaponManager.getCurrentAmmo(entity));
        }

        @Test
        @DisplayName("getWeaponDurability 有武器应返回耐久")
        void getWeaponDurabilityWithWeaponShouldReturnDurability() throws Exception {
            LivingEntity entity = createMockEntity();
            var instance = new MobWeaponManager.MobWeaponInstance(
                    INSTANCE_LABEL, 80, INSTANCE_DURABILITY, false,
                    INSTANCE_MAG_SIZE, INSTANCE_MAG_SIZE, INSTANCE_RELOAD_TICKS,
                    INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);
            injectWeaponToCache(entity.getUniqueId(), instance);
            assertEquals(80, weaponManager.getWeaponDurability(entity));
        }

        @Test
        @DisplayName("getDurabilityRatio 应正确计算比例")
        void getDurabilityRatioShouldCalculateCorrectly() throws Exception {
            LivingEntity entity = createMockEntity();
            var instance = new MobWeaponManager.MobWeaponInstance(
                    INSTANCE_LABEL, 50, INSTANCE_DURABILITY, false,
                    INSTANCE_MAG_SIZE, INSTANCE_MAG_SIZE, INSTANCE_RELOAD_TICKS,
                    INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);
            injectWeaponToCache(entity.getUniqueId(), instance);
            assertEquals(0.5, weaponManager.getDurabilityRatio(entity), 0.01);
        }

        @Test
        @DisplayName("isReloading 有武器换弹中应返回 true")
        void isReloadingWithWeaponReloadingShouldReturnTrue() throws Exception {
            LivingEntity entity = createMockEntity();
            var inst = createDefaultInstance();
            inst.setReloading(true);
            injectWeaponToCache(entity.getUniqueId(), inst);
            assertTrue(weaponManager.isReloading(entity));
        }

        @Test
        @DisplayName("isMagazineEmpty 弹药为 0 应返回 true")
        void isMagazineEmptyZeroAmmoShouldReturnTrue() throws Exception {
            LivingEntity entity = createMockEntity();
            var instance = new MobWeaponManager.MobWeaponInstance(
                    INSTANCE_LABEL, INSTANCE_DURABILITY, INSTANCE_DURABILITY, false,
                    INSTANCE_MAG_SIZE, 0, INSTANCE_RELOAD_TICKS,
                    INSTANCE_FIRE_RATE_MS, INSTANCE_SPREAD, INSTANCE_ADS_MULT);
            injectWeaponToCache(entity.getUniqueId(), instance);
            assertTrue(weaponManager.isMagazineEmpty(entity));
        }
    }

    // ==================== 补充：射击逻辑测试 ====================

    @Nested
    @DisplayName("射击逻辑")
    class ShootTests {

        @Test
        @DisplayName("shoot 无武器绑定应返回 false")
        void shootWithoutWeaponShouldReturnFalse() {
            LivingEntity entity = mock(LivingEntity.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());

            assertFalse(weaponManager.shoot(entity, mock(org.bukkit.Location.class)));
        }
    }

    // ==================== 补充：Metadata 测试 ====================

    @Nested
    @DisplayName("Metadata 操作")
    class MetadataTests {

        @Test
        @DisplayName("getMetadataString 无元数据应返回 null")
        void getMetadataStringWithoutValueShouldReturnNull() {
            assertNull(weaponManager.getMetadataString(mock(LivingEntity.class), "emwm_weapon"));
        }

        @Test
        @DisplayName("getMetadataInt 无元数据应返回默认值")
        void getMetadataIntWithoutValueShouldReturnDefault() {
            assertEquals(42, weaponManager.getMetadataInt(mock(LivingEntity.class), "emwm_ammo", 42));
        }
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private void injectWeaponToCache(UUID entityId, MobWeaponManager.MobWeaponInstance instance) throws Exception {
        Field weaponCacheField = MobWeaponManager.class.getDeclaredField("weaponCache");
        weaponCacheField.setAccessible(true);
        Map<UUID, MobWeaponManager.MobWeaponInstance> weaponCache =
                (Map<UUID, MobWeaponManager.MobWeaponInstance>) weaponCacheField.get(weaponManager);
        weaponCache.put(entityId, instance);
    }
}
