package com.emwbridge.config;

import com.emwbridge.EMWMBridge;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EMWMConfigCache 单元测试
 *
 * 覆盖：
 * - parseTemplateSection 解析根节点扁平模板结构
 * - 驼峰字段映射（fireRateTicks → 20/ticks 反向换算）
 * - 字段映射（grenadeThrowRange → grenadeMaxRange 等）
 * - YAML 未配置的字段保留 null
 * - 单 weapon 自动转武器池列表
 * - allowedGrenadeTypes 列表解析
 * - getConfig() 正确返回模板/LuaPower兜底
 * - matchConfigByName() 名称匹配逻辑
 * - reload() 缓存重置行为
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EMWMConfigCache 配置缓存测试")
class EMWMConfigCacheTest {

    @Mock(lenient = true)
    private EMWMBridge plugin;

    @Mock(lenient = true)
    private Logger logger;

    @Mock(lenient = true)
    private Server server;

    private EMWMConfigCache configCache;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginsFolder()).thenReturn(new File("build/tmp/test"));
        // EMWMBridge.debug() 委托给 debugManager，但 DebugManager 可能为 null
        // 所以 mock 为 no-op
        doNothing().when(plugin).debug(anyString());

        org.bukkit.configuration.file.FileConfiguration pluginConfig =
                mock(org.bukkit.configuration.file.FileConfiguration.class, withSettings().lenient());
        when(pluginConfig.getStringList(anyString())).thenReturn(new java.util.ArrayList<>());
        when(pluginConfig.getString(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(plugin.getConfig()).thenReturn(pluginConfig);

        configCache = new EMWMConfigCache(plugin);
    }

    // ==================== parseTemplateSection 测试（通过反射）====================

    @Nested
    @DisplayName("parseTemplateSection 模板解析")
    class ParseTemplateSection {

        private Method parseTemplateSectionMethod;

        @BeforeEach
        void setUp() throws Exception {
            parseTemplateSectionMethod = EMWMConfigCache.class
                    .getDeclaredMethod("parseTemplateSection", ConfigurationSection.class);
            parseTemplateSectionMethod.setAccessible(true);
        }

        private EMWMWeaponConfig parse(ConfigurationSection section) throws Exception {
            return (EMWMWeaponConfig) parseTemplateSectionMethod.invoke(configCache, section);
        }

        /**
         * 创建 lenient 模式的 ConfigurationSection mock，避免 PotentialStubbingProblem
         */
        private ConfigurationSection mockSection() {
            return mock(ConfigurationSection.class, withSettings().lenient());
        }

        @Test
        @DisplayName("null 配置段应返回 null")
        void nullSectionReturnsNull() throws Exception {
            assertNull(parse(null));
        }

        @Test
        @DisplayName("单 weapon 字段应自动转武器池列表")
        void weaponFieldConvertsToWeaponPool() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("weapon")).thenReturn(true);
            when(section.getString("weapon")).thenReturn("AK_47");

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(1, result.getWeaponPool().size());
            assertEquals("AK_47", result.getWeaponPool().getFirst());
        }

        @Test
        @DisplayName("fireRateTicks 应正确换算为 fireRate (20/ticks)")
        void fireRateTicksConvertsCorrectly() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("fireRateTicks")).thenReturn(true);
            when(section.getInt("fireRateTicks")).thenReturn(5);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertNotNull(result.getFireRate());
            assertEquals(4.0, result.getFireRate(), 0.001, "20/5 = 4.0 shots/second");
        }

        @Test
        @DisplayName("需求3：guard 子块解析 behavior/guard-radius/aggro-radius/leash-distance")
        void guardBlockParsed() throws Exception {
            ConfigurationSection section = mockSection();
            ConfigurationSection guard = mockSection();
            when(section.contains("guard")).thenReturn(true);
            when(section.getConfigurationSection("guard")).thenReturn(guard);
            when(guard.contains("behavior")).thenReturn(true);
            when(guard.getString("behavior")).thenReturn("GUARD");
            when(guard.contains("guard-radius")).thenReturn(true);
            when(guard.getDouble("guard-radius")).thenReturn(12.0);
            when(guard.contains("aggro-radius")).thenReturn(true);
            when(guard.getDouble("aggro-radius")).thenReturn(30.0);
            when(guard.contains("leash-distance")).thenReturn(true);
            when(guard.getDouble("leash-distance")).thenReturn(50.0);

            EMWMWeaponConfig result = parse(section);
            assertEquals("GUARD", result.getBehavior());
            assertEquals(12.0, result.getGuardRadius(), 0.001);
            assertEquals(30.0, result.getAggroRadius(), 0.001);
            assertEquals(50.0, result.getLeashDistance(), 0.001);
        }

        @Test
        @DisplayName("需求6：loot 子块解析 ammoType/min/max（模板路径 parseTemplateSection）")
        void lootBlockParsed() throws Exception {
            YamlConfiguration section = new YamlConfiguration();
            section.set("loot.ammoType", "greyzone_rifle_improv");
            section.set("loot.min", 5);
            section.set("loot.max", 10);

            EMWMWeaponConfig result = parse(section);
            assertEquals("greyzone_rifle_improv", result.getLootAmmoType());
            assertEquals(5, result.getLootAmmoMin());
            assertEquals(10, result.getLootAmmoMax());
        }

        @Test
        @DisplayName("需求2/3：顶层 squad 与 behavior 解析")
        void squadAndBehaviorTopLevel() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("squad")).thenReturn(true);
            when(section.getString("squad")).thenReturn("iron_legion_fireteam");
            when(section.contains("behavior")).thenReturn(true);
            when(section.getString("behavior")).thenReturn("GUARD");

            EMWMWeaponConfig result = parse(section);
            assertEquals("iron_legion_fireteam", result.getSquad());
            assertEquals("GUARD", result.getBehavior());
        }

        @Test
        @DisplayName("fireRateTicks 为 0 时不应设置 fireRate")
        void zeroFireRateTicksDoesNotSetFireRate() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("fireRateTicks")).thenReturn(true);
            when(section.getInt("fireRateTicks")).thenReturn(0);
            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertNull(result.getFireRate(), "fireRateTicks=0 时不应设置 fireRate");
        }

        @Test
        @DisplayName("fireRateTicks=2 应换算为 10.0 shots/second")
        void fireRateTicks2ConvertsTo10() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("fireRateTicks")).thenReturn(true);
            when(section.getInt("fireRateTicks")).thenReturn(2);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(10.0, result.getFireRate(), 0.001);
        }

        @Test
        @DisplayName("maxRange 字段正确解析")
        void maxRangeParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("maxRange")).thenReturn(true);
            when(section.getInt("maxRange")).thenReturn(50);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(50, result.getMaxRange());
        }

        @Test
        @DisplayName("spread 字段正确解析")
        void spreadParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("spread")).thenReturn(true);
            when(section.getDouble("spread")).thenReturn(2.5);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(2.5, result.getSpread(), 0.01);
        }

        @Test
        @DisplayName("damageMultiplier 字段正确解析")
        void damageMultiplierParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("damageMultiplier")).thenReturn(true);
            when(section.getDouble("damageMultiplier")).thenReturn(1.5);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(1.5, result.getDamageMultiplier(), 0.01);
        }

        @Test
        @DisplayName("suppressed 布尔字段正确解析")
        void suppressedParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("suppressed")).thenReturn(true);
            when(section.getBoolean("suppressed")).thenReturn(true);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertTrue(result.isSuppressed());
        }

        @Test
        @DisplayName("onlyAimShoot 布尔字段正确解析")
        void onlyAimShootParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("onlyAimShoot")).thenReturn(true);
            when(section.getBoolean("onlyAimShoot")).thenReturn(true);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertTrue(result.isOnlyAimShoot());
        }

        @Test
        @DisplayName("allowAutoReload 字段正确解析")
        void allowAutoReloadParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("allowAutoReload")).thenReturn(true);
            when(section.getBoolean("allowAutoReload")).thenReturn(false);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertFalse(result.isAutoReload());
        }

        @Test
        @DisplayName("meleeSwitchHealthPercent 字段正确解析")
        void meleeSwitchHealthPercentParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("meleeSwitchHealthPercent")).thenReturn(true);
            when(section.getDouble("meleeSwitchHealthPercent")).thenReturn(0.2);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(0.2, result.getMeleeSwitchHealthPercent(), 0.01);
        }

        @Test
        @DisplayName("enableGrenadeAI 字段正确解析")
        void enableGrenadeAIParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("enableGrenadeAI")).thenReturn(true);
            when(section.getBoolean("enableGrenadeAI")).thenReturn(true);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertTrue(result.isEnableGrenadeAI());
        }

        @Test
        @DisplayName("grenadeThrowRange 应映射到 grenadeMaxRange")
        void grenadeThrowRangeMapsToGrenadeMaxRange() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("grenadeThrowRange")).thenReturn(true);
            when(section.getInt("grenadeThrowRange")).thenReturn(30);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(30, result.getGrenadeMaxRange());
        }

        @Test
        @DisplayName("grenadeCooldown 应映射到 grenadeCooldownTicks")
        void grenadeCooldownMapsToGrenadeCooldownTicks() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("grenadeCooldown")).thenReturn(true);
            when(section.getInt("grenadeCooldown")).thenReturn(200);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(200, result.getGrenadeCooldownTicks());
        }

        @Test
        @DisplayName("allowedGrenadeTypes 列表解析正确")
        void allowedGrenadeTypesParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("allowedGrenadeTypes")).thenReturn(true);
            when(section.isList("allowedGrenadeTypes")).thenReturn(true);
            when(section.getStringList("allowedGrenadeTypes")).thenReturn(Arrays.asList("frag", "flashbang", "smoke"));

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertNotNull(result.getAllowedGrenadeTypes());
            assertEquals(3, result.getAllowedGrenadeTypes().size());
            assertTrue(result.getAllowedGrenadeTypes().contains("frag"));
            assertTrue(result.getAllowedGrenadeTypes().contains("smoke"));
        }

        @Test
        @DisplayName("YAML 未配置的字段应保留 null")
        void missingFieldsPreserveNull() throws Exception {
            ConfigurationSection section = mockSection();
            // 只配置 weapon，其他字段不设置
            when(section.contains("weapon")).thenReturn(true);
            when(section.getString("weapon")).thenReturn("AK_47");

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertNull(result.getFireRate(), "未配置 fireRate 应为 null");
            assertNull(result.getMagazineSize(), "未配置 magazineSize 应为 null");
            assertNull(result.getSpread(), "未配置 spread 应为 null");
            assertNull(result.getMaxRange(), "未配置 maxRange 应为 null");
            assertNull(result.getSuppressHpThreshold(), "未配置 suppressHpThreshold 应为 null");
            assertNull(result.isSuppressed(), "未配置 suppressed 应为 null");
        }

        @Test
        @DisplayName("多个字段混合解析")
        void multipleFieldsParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("weapon")).thenReturn(true);
            when(section.getString("weapon")).thenReturn("M4A1");
            when(section.contains("fireRateTicks")).thenReturn(true);
            when(section.getInt("fireRateTicks")).thenReturn(3);
            when(section.contains("maxRange")).thenReturn(true);
            when(section.getInt("maxRange")).thenReturn(60);
            when(section.contains("spread")).thenReturn(true);
            when(section.getDouble("spread")).thenReturn(1.5);
            when(section.contains("suppressed")).thenReturn(true);
            when(section.getBoolean("suppressed")).thenReturn(true);
            when(section.contains("allowAutoReload")).thenReturn(true);
            when(section.getBoolean("allowAutoReload")).thenReturn(true);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals("M4A1", result.getWeaponPool().getFirst());
            assertEquals(20.0 / 3.0, result.getFireRate(), 0.001);
            assertEquals(60, result.getMaxRange());
            assertEquals(1.5, result.getSpread(), 0.01);
            assertTrue(result.isSuppressed());
            assertTrue(result.isAutoReload());
            assertNull(result.getMagazineSize(), "未配置的字段应为 null");
        }

        @Test
        @DisplayName("recoil 字段正确解析")
        void recoilParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("recoil")).thenReturn(true);
            when(section.getDouble("recoil")).thenReturn(0.8);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(0.8, result.getRecoil(), 0.01);
        }

        @Test
        @DisplayName("projectileSpeed 字段正确解析")
        void projectileSpeedParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("projectileSpeed")).thenReturn(true);
            when(section.getDouble("projectileSpeed")).thenReturn(200.0);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(200.0, result.getProjectileSpeed(), 0.01);
        }

        @Test
        @DisplayName("bulletPenetration 字段正确解析")
        void bulletPenetrationParsed() throws Exception {
            ConfigurationSection section = mockSection();
            when(section.contains("bulletPenetration")).thenReturn(true);
            when(section.getDouble("bulletPenetration")).thenReturn(2.0);

            EMWMWeaponConfig result = parse(section);

            assertNotNull(result);
            assertEquals(2.0, result.getBulletPenetration(), 0.01);
        }
    }

    // ==================== getConfig 测试 ====================

    @Nested
    @DisplayName("getConfig 配置获取")
    class GetConfig {

        @Test
        @DisplayName("null mob 文件名应抛出 NullPointerException")
        void nullMobFileThrowsNpe() {
            assertThrows(NullPointerException.class, () -> configCache.getConfig(null));
        }

        @Test
        @DisplayName("不存在的配置文件名应返回 null")
        void nonExistentConfigReturnsNull() {
            assertNull(configCache.getConfig("nonexistent_mob"));
        }

        @Test
        @DisplayName("已加载的怪物配置应直接返回")
        void loadedMobConfigReturnedDirectly() throws Exception {
            EMWMWeaponConfig testConfig = new EMWMWeaponConfig();
            testConfig.setWeaponPool(Collections.singletonList("AK_47"));
            testConfig.setFireRate(6.0);

            // 通过反射注入 mobConfigs
            injectMobConfig("test_mob", testConfig);

            EMWMWeaponConfig result = configCache.getConfig("test_mob");
            assertNotNull(result);
            assertEquals("AK_47", result.getWeaponPool().getFirst());
            assertEquals(6.0, result.getFireRate(), 0.01);
        }

        @Test
        @DisplayName("继承模板的怪物应返回模板配置")
        void templateInheritedConfig() throws Exception {
            EMWMWeaponConfig templateConfig = new EMWMWeaponConfig();
            templateConfig.setWeaponPool(Collections.singletonList("M4A1"));
            templateConfig.setMaxRange(50);

            // 通过反射注入 globalTemplates 和 templateInheritance
            injectGlobalTemplate("soldier_template", templateConfig);
            injectTemplateInheritance("boss_mob", "soldier_template");

            EMWMWeaponConfig result = configCache.getConfig("boss_mob");
            assertNotNull(result);
            assertEquals("M4A1", result.getWeaponPool().getFirst());
            assertEquals(50, result.getMaxRange());
        }

        @Test
        @DisplayName("怪物自身配置优先于模板配置")
        void mobConfigOverridesTemplate() throws Exception {
            EMWMWeaponConfig mobConfig = new EMWMWeaponConfig();
            mobConfig.setWeaponPool(Collections.singletonList("SCAR"));
            mobConfig.setFireRate(8.0);

            EMWMWeaponConfig templateConfig = new EMWMWeaponConfig();
            templateConfig.setWeaponPool(Collections.singletonList("M4A1"));
            templateConfig.setFireRate(4.0);

            injectMobConfig("soldier", mobConfig);
            injectGlobalTemplate("soldier_template", templateConfig);
            injectTemplateInheritance("soldier", "soldier_template");

            // 注意：getConfig 对已有 mobConfigs 的直接返回，不检查 templateInheritance
            EMWMWeaponConfig result = configCache.getConfig("soldier");
            assertNotNull(result);
            assertEquals("SCAR", result.getWeaponPool().getFirst());
            assertEquals(8.0, result.getFireRate(), 0.01);
        }

        @Test
        @DisplayName("不存在的模板名应返回 null")
        void nonExistentTemplateReturnsNull() throws Exception {
            injectTemplateInheritance("orphan_mob", "ghost_template");

            assertNull(configCache.getConfig("orphan_mob"));
        }
    }

    // ==================== matchConfigByName 测试 ====================

    @Nested
    @DisplayName("matchConfigByName 名称匹配")
    class MatchConfigByName {

        @Test
        @DisplayName("null 名称应返回 null")
        void nullNameReturnsNull() {
            assertNull(configCache.matchConfigByName(null));
        }

        @Test
        @DisplayName("匹配已加载的配置文件名")
        void matchLoadedConfigName() throws Exception {
            EMWMWeaponConfig config = new EMWMWeaponConfig();
            config.setWeaponPool(Collections.singletonList("AK_47"));
            injectMobConfig("scav_basic", config);

            String result = configCache.matchConfigByName("&e&lScav_Basic");
            assertEquals("scav_basic", result);
        }

        @Test
        @DisplayName("大小写不敏感匹配")
        void caseInsensitiveMatch() throws Exception {
            EMWMWeaponConfig config = new EMWMWeaponConfig();
            config.setWeaponPool(Collections.singletonList("AK_47"));
            injectMobConfig("Scav_Elite", config);

            String result = configCache.matchConfigByName("scav_elite");
            assertEquals("Scav_Elite", result);
        }

        @Test
        @DisplayName("匹配模板名")
        void matchTemplateName() throws Exception {
            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setWeaponPool(Collections.singletonList("M4A1"));
            injectGlobalTemplate("soldier", template);

            String result = configCache.matchConfigByName("&e&lSoldier");
            assertEquals("soldier", result);
        }

        @Test
        @DisplayName("无匹配时返回 null")
        void noMatchReturnsNull() {
            String result = configCache.matchConfigByName("&e&lUnknown");
            assertNull(result);
        }
    }

    // ==================== reload / 其他公共方法测试 ====================

    @Nested
    @DisplayName("缓存管理")
    class CacheManagement {

        @Test
        @DisplayName("reload 应清空缓存并重新加载（无模板文件时不报错）")
        void reloadClearsAndReloads() throws Exception {
            // 先注入一些数据
            EMWMWeaponConfig config = new EMWMWeaponConfig();
            config.setWeaponPool(Collections.singletonList("AK_47"));
            injectMobConfig("test_mob", config);
            injectGlobalTemplate("test_template", config);
            injectTemplateInheritance("test_mob", "test_template");

            assertNotNull(configCache.getConfig("test_mob"));

            // 用 mock WeaponMetaCache 替换，避免触发 WeaponMechanicsAPI 类加载
            WeaponMetaCache mockWeaponCache = mock(WeaponMetaCache.class, withSettings().lenient());
            Field weaponMetaField = EMWMConfigCache.class.getDeclaredField("weaponMetaCache");
            weaponMetaField.setAccessible(true);
            weaponMetaField.set(configCache, mockWeaponCache);

            // 现在没有实际文件，reload 应清空后优雅降级
            configCache.reload();

            assertNull(configCache.getConfig("test_mob"), "reload 后应清空缓存");
            assertTrue(configCache.getLoadedMobFiles().isEmpty());
            assertTrue(configCache.getGlobalTemplateNames().isEmpty());
        }

        @Test
        @DisplayName("hasConfig 应正确判断配置存在性")
        void hasConfigWorksCorrectly() throws Exception {
            assertFalse(configCache.hasConfig("test_mob"));

            EMWMWeaponConfig config = new EMWMWeaponConfig();
            config.setWeaponPool(Collections.singletonList("AK_47"));
            injectMobConfig("test_mob", config);

            assertTrue(configCache.hasConfig("test_mob"));
        }

        @Test
        @DisplayName("通过模板继承也应算作有配置")
        void templateInheritanceCountsAsConfig() throws Exception {
            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setWeaponPool(Collections.singletonList("M4A1"));
            injectGlobalTemplate("test_template", template);
            injectTemplateInheritance("orphan_mob", "test_template");

            assertTrue(configCache.hasConfig("orphan_mob"));
        }

        @Test
        @DisplayName("getLoadedMobFiles 返回已加载的文件名集合")
        void getLoadedMobFilesReturnsKeys() throws Exception {
            injectMobConfig("mob_a", new EMWMWeaponConfig());
            injectMobConfig("mob_b", new EMWMWeaponConfig());

            Set<String> files = configCache.getLoadedMobFiles();
            assertEquals(2, files.size());
            assertTrue(files.contains("mob_a"));
            assertTrue(files.contains("mob_b"));
        }

        @Test
        @DisplayName("getGlobalTemplateNames 返回已加载的模板名集合")
        void getGlobalTemplateNamesReturnsKeys() throws Exception {
            injectGlobalTemplate("template_a", new EMWMWeaponConfig());
            injectGlobalTemplate("template_b", new EMWMWeaponConfig());

            Set<String> names = configCache.getGlobalTemplateNames();
            assertEquals(2, names.size());
            assertTrue(names.contains("template_a"));
            assertTrue(names.contains("template_b"));
        }
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private void injectMobConfig(String fileName, EMWMWeaponConfig config) throws Exception {
        Field mobConfigsField = EMWMConfigCache.class.getDeclaredField("mobConfigs");
        mobConfigsField.setAccessible(true);
        Map<String, EMWMWeaponConfig> mobConfigs = (Map<String, EMWMWeaponConfig>) mobConfigsField.get(configCache);
        mobConfigs.put(fileName, config);
    }

    @SuppressWarnings("unchecked")
    private void injectGlobalTemplate(String templateName, EMWMWeaponConfig config) throws Exception {
        Field templatesField = EMWMConfigCache.class.getDeclaredField("globalTemplates");
        templatesField.setAccessible(true);
        Map<String, EMWMWeaponConfig> globalTemplates = (Map<String, EMWMWeaponConfig>) templatesField.get(configCache);
        globalTemplates.put(templateName, config);
    }

    @SuppressWarnings("unchecked")
    private void injectTemplateInheritance(String mobFileName, String templateName) throws Exception {
        Field inheritanceField = EMWMConfigCache.class.getDeclaredField("templateInheritance");
        inheritanceField.setAccessible(true);
        Map<String, String> templateInheritance = (Map<String, String>) inheritanceField.get(configCache);
        templateInheritance.put(mobFileName, templateName);
    }
}
