package com.emwbridge.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EMWMWeaponConfig 单元测试
 *
 * 覆盖：
 * - 所有数值字段默认值为 null（Integer/Double/Boolean 包装类型）
 * - explicitlySetFields 在 setter 调用后被正确标记
 * - mergeWithTemplate() 三级优先级规则
 * - getXxxOrDefault() 安全取值方法
 * - getRandomWeapon() 权重随机选择逻辑
 * - validate() 参数校验逻辑
 */
@DisplayName("EMWMWeaponConfig 数据模型测试")
class EMWMWeaponConfigTest {

    private EMWMWeaponConfig config;

    @BeforeEach
    void setUp() {
        config = new EMWMWeaponConfig();
    }

    // ==================== 字段默认值测试 ====================

    @Nested
    @DisplayName("字段默认值")
    class FieldDefaults {

        @Test
        @DisplayName("所有 Integer 字段默认应为 null")
        void allIntegerFieldsDefaultToNull() {
            assertNull(config.getMagazineSize(), "magazineSize 默认应为 null");
            assertNull(config.getReloadDuration(), "reloadDuration 默认应为 null");
            assertNull(config.getEffectiveRange(), "effectiveRange 默认应为 null");
            assertNull(config.getMaxRange(), "maxRange 默认应为 null");
            assertNull(config.getAdsRangeThreshold(), "adsRangeThreshold 默认应为 null");
            assertNull(config.getSearchDuration(), "searchDuration 默认应为 null");
            assertNull(config.getFragInterval(), "fragInterval 默认应为 null");
            assertNull(config.getFlashInterval(), "flashInterval 默认应为 null");
            assertNull(config.getSmokeInterval(), "smokeInterval 默认应为 null");
            assertNull(config.getThrowMinRange(), "throwMinRange 默认应为 null");
            assertNull(config.getThrowMaxRange(), "throwMaxRange 默认应为 null");
            assertNull(config.getEquipDelay(), "equipDelay 默认应为 null");
            assertNull(config.getAimDelay(), "aimDelay 默认应为 null");
            assertNull(config.getDurabilityPerShot(), "durabilityPerShot 默认应为 null");
            assertNull(config.getReserveAmmo(), "reserveAmmo 默认应为 null");
            assertNull(config.getGrenadeMaxRange(), "grenadeMaxRange 默认应为 null");
            assertNull(config.getGrenadeCooldownTicks(), "grenadeCooldownTicks 默认应为 null");
            assertNull(config.getGrenadeMinEnemyCoverTime(), "grenadeMinEnemyCoverTime 默认应为 null");
            assertNull(config.getMaxGrenadeCarry(), "maxGrenadeCarry 默认应为 null");
        }

        @Test
        @DisplayName("所有 Double 字段默认应为 null")
        void allDoubleFieldsDefaultToNull() {
            assertNull(config.getFireRate(), "fireRate 默认应为 null");
            assertNull(config.getSpread(), "spread 默认应为 null");
            assertNull(config.getAdsSpreadMultiplier(), "adsSpreadMultiplier 默认应为 null");
            assertNull(config.getMeleeRange(), "meleeRange 默认应为 null");
            assertNull(config.getSuppressHpThreshold(), "suppressHpThreshold 默认应为 null");
            assertNull(config.getRetreatHpThreshold(), "retreatHpThreshold 默认应为 null");
            assertNull(config.getAggressiveness(), "aggressiveness 默认应为 null");
            assertNull(config.getCoverUsage(), "coverUsage 默认应为 null");
            assertNull(config.getProjectileSpeed(), "projectileSpeed 默认应为 null");
            assertNull(config.getBulletPenetration(), "bulletPenetration 默认应为 null");
            assertNull(config.getDamageMultiplier(), "damageMultiplier 默认应为 null");
            assertNull(config.getRecoil(), "recoil 默认应为 null");
            assertNull(config.getCrouchAimSpreadReduction(), "crouchAimSpreadReduction 默认应为 null");
            assertNull(config.getMeleeSwitchHealthPercent(), "meleeSwitchHealthPercent 默认应为 null");
        }

        @Test
        @DisplayName("所有 Boolean 字段默认应为 null")
        void allBooleanFieldsDefaultToNull() {
            assertNull(config.isAutoReload(), "autoReload 默认应为 null");
            assertNull(config.isStandAndShoot(), "standAndShoot 默认应为 null");
            assertNull(config.isCallReinforcements(), "callReinforcements 默认应为 null");
            assertNull(config.isSquadLeader(), "squadLeader 默认应为 null");
            assertNull(config.isPreferLongRange(), "preferLongRange 默认应为 null");
            assertNull(config.isPreferRush(), "preferRush 默认应为 null");
            assertNull(config.isMuzzleFlashEnabled(), "muzzleFlashEnabled 默认应为 null");
            assertNull(config.isSuppressed(), "suppressed 默认应为 null");
            assertNull(config.isOnlyAimShoot(), "onlyAimShoot 默认应为 null");
            assertNull(config.isCancelReloadOnMove(), "cancelReloadOnMove 默认应为 null");
            assertNull(config.isThrowIfCover(), "throwIfCover 默认应为 null");
            assertNull(config.isBreakOnZeroDurability(), "breakOnZeroDurability 默认应为 null");
            assertNull(config.isEnableGrenadeAI(), "enableGrenadeAI 默认应为 null");
        }

        @Test
        @DisplayName("String/List/Map 字段默认值应为空集合或 null")
        void collectionFieldsDefaultValues() {
            assertTrue(config.getWeaponPool().isEmpty(), "weaponPool 默认应为空列表");
            assertTrue(config.getWeaponWeights().isEmpty(), "weaponWeights 默认应为空映射");
            assertNull(config.getAllowedGrenadeTypes(), "allowedGrenadeTypes 默认应为 null");
            assertNull(config.getFireMode(), "fireMode 默认应为 null");
            assertNull(config.getAmmoType(), "ammoType 默认应为 null");
            assertNull(config.getGrenadeType(), "grenadeType 默认应为 null");
        }
    }

    // ==================== explicitlySetFields 测试 ====================

    @Nested
    @DisplayName("显式设置字段标记")
    class ExplicitlySetFields {

        @Test
        @DisplayName("setter 调用后 isFieldExplicitlySet 应返回 true")
        void setterShouldMarkFieldAsExplicitlySet() {
            config.setMagazineSize(30);
            assertTrue(config.isFieldExplicitlySet("magazineSize"));

            config.setFireRate(8.0);
            assertTrue(config.isFieldExplicitlySet("fireRate"));

            config.setSuppressed(true);
            assertTrue(config.isFieldExplicitlySet("suppressed"));
        }

        @Test
        @DisplayName("未调用 setter 的字段 isFieldExplicitlySet 应返回 false")
        void unsetFieldShouldReturnFalse() {
            assertFalse(config.isFieldExplicitlySet("magazineSize"));
            assertFalse(config.isFieldExplicitlySet("fireRate"));
            assertFalse(config.isFieldExplicitlySet("spread"));
        }

        @Test
        @DisplayName("多个 setter 都应正确标记")
        void multipleSettersAllMarked() {
            config.setMagazineSize(20);
            config.setReloadDuration(40);
            config.setFireRate(6.0);
            config.setSpread(2.5);
            config.setSuppressed(true);
            config.setEnableGrenadeAI(true);
            config.setAllowedGrenadeTypes(Arrays.asList("frag", "smoke"));

            assertTrue(config.isFieldExplicitlySet("magazineSize"));
            assertTrue(config.isFieldExplicitlySet("reloadDuration"));
            assertTrue(config.isFieldExplicitlySet("fireRate"));
            assertTrue(config.isFieldExplicitlySet("spread"));
            assertTrue(config.isFieldExplicitlySet("suppressed"));
            assertTrue(config.isFieldExplicitlySet("enableGrenadeAI"));
            assertTrue(config.isFieldExplicitlySet("allowedGrenadeTypes"));
        }

        @Test
        @DisplayName("setter 传入 null 仍应标记字段")
        void setterWithNullShouldStillMark() {
            config.setMagazineSize(null);
            assertTrue(config.isFieldExplicitlySet("magazineSize"));
            assertNull(config.getMagazineSize());
        }
    }

    // ==================== mergeWithTemplate 测试 ====================

    @Nested
    @DisplayName("mergeWithTemplate 模板合并规则")
    class MergeWithTemplate {

        @Test
        @DisplayName("null 模板不应改变任何字段")
        void nullTemplateDoesNothing() {
            config.setMagazineSize(15);
            config.setFireRate(5.0);

            config.mergeWithTemplate(null);

            assertEquals(15, config.getMagazineSize());
            assertEquals(5.0, config.getFireRate(), 0.01);
        }

        @Test
        @DisplayName("本地非 null 字段不应被模板覆盖")
        void localNonNullNotOverwrittenByTemplate() {
            config.setMagazineSize(15);
            config.setFireRate(5.0);

            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setMagazineSize(30);
            template.setFireRate(10.0);

            config.mergeWithTemplate(template);

            assertEquals(15, config.getMagazineSize(), "本地非 null 值应保留");
            assertEquals(5.0, config.getFireRate(), 0.01, "本地非 null 值应保留");
        }

        @Test
        @DisplayName("本地 null 字段应继承模板值")
        void localNullInheritsTemplate() {
            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setMagazineSize(30);
            template.setFireRate(8.0);
            template.setSuppressed(true);

            config.mergeWithTemplate(template);

            assertEquals(30, config.getMagazineSize(), "null 字段应继承模板值");
            assertEquals(8.0, config.getFireRate(), 0.01, "null 字段应继承模板值");
            assertTrue(config.isSuppressed(), "null 字段应继承模板值");
        }

        @Test
        @DisplayName("混合场景：部分字段显式设置，部分继承")
        void mixedExplicitAndInherited() {
            config.setMagazineSize(50);
            // fireRate 不设置，保持 null

            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setMagazineSize(30);
            template.setFireRate(6.0);
            template.setSpread(2.0);

            config.mergeWithTemplate(template);

            assertEquals(50, config.getMagazineSize(), "显式设置应保留");
            assertEquals(6.0, config.getFireRate(), 0.01, "null 应继承模板");
            assertEquals(2.0, config.getSpread(), 0.01, "null 应继承模板");
        }

        @Test
        @DisplayName("模板也为 null 的字段应保持 null")
        void templateAlsoNullShouldStayNull() {
            config.setMagazineSize(30);
            // fireRate 是 null, 模板也是 null

            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setMagazineSize(30);
            // fireRate 不设置

            config.mergeWithTemplate(template);

            assertNull(config.getFireRate(), "模板也是 null 时应保持 null");
        }

        @Test
        @DisplayName("allowedGrenadeTypes 列表正确继承")
        void allowedGrenadeTypesInherited() {
            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setAllowedGrenadeTypes(Arrays.asList("frag", "flashbang", "smoke"));

            config.mergeWithTemplate(template);

            assertNotNull(config.getAllowedGrenadeTypes());
            assertEquals(3, config.getAllowedGrenadeTypes().size());
            assertTrue(config.getAllowedGrenadeTypes().contains("frag"));
            assertTrue(config.getAllowedGrenadeTypes().contains("smoke"));
        }

        @Test
        @DisplayName("allowedGrenadeTypes 本地值覆盖模板值")
        void allowedGrenadeTypesLocalOverrides() {
            config.setAllowedGrenadeTypes(Arrays.asList("tear"));

            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setAllowedGrenadeTypes(Arrays.asList("frag", "flashbang"));

            config.mergeWithTemplate(template);

            assertNotNull(config.getAllowedGrenadeTypes());
            assertEquals(1, config.getAllowedGrenadeTypes().size());
            assertEquals("tear", config.getAllowedGrenadeTypes().getFirst());
        }

        @Test
        @DisplayName("weaponPool 为空时应继承模板武器池")
        void emptyWeaponPoolInheritsFromTemplate() {
            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setWeaponPool(Arrays.asList("AK_47", "M4A1"));

            config.mergeWithTemplate(template);

            assertEquals(2, config.getWeaponPool().size());
            assertTrue(config.getWeaponPool().contains("AK_47"));
        }

        @Test
        @DisplayName("weaponPool 非空时应保留本地值")
        void nonEmptyWeaponPoolPreservesLocal() {
            config.setWeaponPool(Collections.singletonList("SCAR"));

            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setWeaponPool(Arrays.asList("AK_47", "M4A1"));

            config.mergeWithTemplate(template);

            assertEquals(1, config.getWeaponPool().size());
            assertEquals("SCAR", config.getWeaponPool().getFirst());
        }
    }

    // ==================== getXxxOrDefault 测试 ====================

    @Nested
    @DisplayName("getXxxOrDefault 安全取值方法")
    class GetOrDefault {

        @Test
        @DisplayName("getMagazineSizeOrDefault null 应返回 30")
        void magazineSizeDefault() {
            assertEquals(30, config.getMagazineSizeOrDefault());
            config.setMagazineSize(50);
            assertEquals(50, config.getMagazineSizeOrDefault());
        }

        @Test
        @DisplayName("getReloadDurationOrDefault null 应返回 60")
        void reloadDurationDefault() {
            assertEquals(60, config.getReloadDurationOrDefault());
            config.setReloadDuration(80);
            assertEquals(80, config.getReloadDurationOrDefault());
        }

        @Test
        @DisplayName("getFireRateOrDefault null 应返回 4.0")
        void fireRateDefault() {
            assertEquals(4.0, config.getFireRateOrDefault(), 0.01);
            config.setFireRate(8.0);
            assertEquals(8.0, config.getFireRateOrDefault(), 0.01);
        }

        @Test
        @DisplayName("getSpreadOrDefault null 应返回 5.0")
        void spreadDefault() {
            assertEquals(5.0, config.getSpreadOrDefault(), 0.01);
            config.setSpread(2.0);
            assertEquals(2.0, config.getSpreadOrDefault(), 0.01);
        }

        @Test
        @DisplayName("getAllowedGrenadeTypesOrDefault null 应返回 [frag, flashbang]")
        void allowedGrenadeTypesDefault() {
            List<String> defaultTypes = config.getAllowedGrenadeTypesOrDefault();
            assertNotNull(defaultTypes);
            assertEquals(2, defaultTypes.size());
            assertEquals("frag", defaultTypes.get(0));
            assertEquals("flashbang", defaultTypes.get(1));

            config.setAllowedGrenadeTypes(Collections.singletonList("tear"));
            assertEquals(1, config.getAllowedGrenadeTypesOrDefault().size());
            assertEquals("tear", config.getAllowedGrenadeTypesOrDefault().getFirst());
        }

        @Test
        @DisplayName("isSuppressedOrDefault null 应返回 false")
        void suppressedDefault() {
            assertFalse(config.isSuppressedOrDefault());
            config.setSuppressed(true);
            assertTrue(config.isSuppressedOrDefault());
        }

        @Test
        @DisplayName("getDamageMultiplierOrDefault null 应返回 1.0")
        void damageMultiplierDefault() {
            assertEquals(1.0, config.getDamageMultiplierOrDefault(), 0.01);
            config.setDamageMultiplier(1.5);
            assertEquals(1.5, config.getDamageMultiplierOrDefault(), 0.01);
        }

        @Test
        @DisplayName("getEffectiveRangeOrDefault null 应返回 25")
        void effectiveRangeDefault() {
            assertEquals(25, config.getEffectiveRangeOrDefault());
            config.setEffectiveRange(50);
            assertEquals(50, config.getEffectiveRangeOrDefault());
        }

        @Test
        @DisplayName("getMaxRangeOrDefault null 应返回 40")
        void maxRangeDefault() {
            assertEquals(40, config.getMaxRangeOrDefault());
            config.setMaxRange(80);
            assertEquals(80, config.getMaxRangeOrDefault());
        }

        @Test
        @DisplayName("getMeleeSwitchHealthPercentOrDefault null 应返回 0.3")
        void meleeSwitchHealthPercentDefault() {
            assertEquals(0.3, config.getMeleeSwitchHealthPercentOrDefault(), 0.01);
            config.setMeleeSwitchHealthPercent(0.5);
            assertEquals(0.5, config.getMeleeSwitchHealthPercentOrDefault(), 0.01);
        }
    }

    // ==================== getRandomWeapon 测试 ====================

    @Nested
    @DisplayName("getRandomWeapon 权重随机选择")
    class RandomWeapon {

        @Test
        @DisplayName("空武器池应返回 null")
        void emptyPoolReturnsNull() {
            assertNull(config.getRandomWeapon());
        }

        @Test
        @DisplayName("单武器武器池应返回该武器")
        void singleWeaponReturnsThatWeapon() {
            config.setWeaponPool(Collections.singletonList("AK_47"));
            assertEquals("AK_47", config.getRandomWeapon());
        }

        @Test
        @DisplayName("有权重时优先返回高权重武器（多次调用应包含高权重武器）")
        void weightedSelectionRespectsWeights() {
            config.setWeaponPool(Arrays.asList("AK_47", "PM"));
            Map<String, Double> weights = new HashMap<>();
            weights.put("AK_47", 99.0);
            weights.put("PM", 1.0);
            config.setWeaponWeights(weights);

            // 多次调用，确保高权重武器出现次数多
            int akCount = 0;
            int pmCount = 0;
            for (int i = 0; i < 1000; i++) {
                String weapon = config.getRandomWeapon();
                if ("AK_47".equals(weapon)) akCount++;
                else if ("PM".equals(weapon)) pmCount++;
            }

            assertTrue(akCount > pmCount, "高权重武器应被更频繁选中");
        }

        @Test
        @DisplayName("无权重时应均匀随机（所有武器都可能出现）")
        void noWeightsUniformRandom() {
            config.setWeaponPool(Arrays.asList("AK_47", "M4A1", "SCAR"));

            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                seen.add(config.getRandomWeapon());
            }

            assertEquals(3, seen.size(), "所有武器都应被选中过");
        }

        @Test
        @DisplayName("多武器池有权重应选择正确武器")
        void weightedWithMultipleWeapons() {
            config.setWeaponPool(Arrays.asList("AK_47", "M4A1", "SCAR"));
            Map<String, Double> weights = new HashMap<>();
            weights.put("AK_47", 1.0);
            weights.put("M4A1", 1.0);
            weights.put("SCAR", 98.0);
            config.setWeaponWeights(weights);

            int scarCount = 0;
            for (int i = 0; i < 500; i++) {
                if ("SCAR".equals(config.getRandomWeapon())) scarCount++;
            }

            assertTrue(scarCount > 300, "SCAR 权重 98% 应被频繁选中");
        }
    }

    // ==================== validate 测试 ====================

    @Nested
    @DisplayName("validate 参数校验")
    class Validate {

        @Test
        @DisplayName("空武器池应回退默认 MP5")
        void emptyWeaponPoolUsesDefault() {
            config.validate();
            assertFalse(config.getWeaponPool().isEmpty());
            assertEquals("MP5", config.getWeaponPool().getFirst());
        }

        @Test
        @DisplayName("非空武器池应保持不变")
        void nonEmptyWeaponPoolPreserved() {
            config.setWeaponPool(Arrays.asList("AK_47", "M4A1"));
            config.validate();
            assertEquals(2, config.getWeaponPool().size());
        }

        @Test
        @DisplayName("magazineSize <= 0 应回退 30")
        void invalidMagazineSizeReset() {
            config.setMagazineSize(0);
            config.validate();
            assertEquals(30, config.getMagazineSize());

            config.setMagazineSize(-5);
            config.validate();
            assertEquals(30, config.getMagazineSize());
        }

        @Test
        @DisplayName("null magazineSize 应保持不变（未校验）")
        void nullMagazineSizeUnchanged() {
            config.validate();
            assertNull(config.getMagazineSize(), "null 字段不应被 validate 改变");
        }

        @ParameterizedTest
        @ValueSource(doubles = {-1.0, -0.1, 0.0})
        @DisplayName("fireRate <= 0 应回退 4.0")
        void invalidFireRateReset(double invalidRate) {
            config.setFireRate(invalidRate);
            config.validate();
            assertEquals(4.0, config.getFireRate(), 0.01);
        }

        @Test
        @DisplayName("null fireRate 应保持不变")
        void nullFireRateUnchanged() {
            config.validate();
            assertNull(config.getFireRate());
        }

        @Test
        @DisplayName("spread < 0 应回退 5.0")
        void invalidSpreadReset() {
            config.setSpread(-1.0);
            config.validate();
            assertEquals(5.0, config.getSpread(), 0.01);
        }

        @Test
        @DisplayName("maxRange < effectiveRange 时 maxRange 应自动调整")
        void maxRangeAdjustedWhenLessThanEffective() {
            config.setMaxRange(10);
            config.setEffectiveRange(20);
            config.validate();
            assertEquals(35, config.getMaxRange(), "maxRange 应调整为 effectiveRange + 15 = 35");
        }

        @Test
        @DisplayName("retreatHpThreshold > suppressHpThreshold 时应调整")
        void retreatThresholdAdjusted() {
            config.setSuppressHpThreshold(0.5);
            config.setRetreatHpThreshold(0.7);
            config.validate();
            assertEquals(0.4, config.getRetreatHpThreshold(), 0.01, "应调整为 suppressHpThreshold - 0.1");
        }

        @Test
        @DisplayName("aggressiveness 超出 [0,1] 范围应回退 0.6")
        void invalidAggressivenessReset() {
            config.setAggressiveness(1.5);
            config.validate();
            assertEquals(0.6, config.getAggressiveness(), 0.01);

            config.setAggressiveness(-0.5);
            config.validate();
            assertEquals(0.6, config.getAggressiveness(), 0.01);
        }

        @Test
        @DisplayName("searchDuration <= 0 应回退 120")
        void invalidSearchDurationReset() {
            config.setSearchDuration(0);
            config.validate();
            assertEquals(120, config.getSearchDuration());

            config.setSearchDuration(-10);
            config.validate();
            assertEquals(120, config.getSearchDuration());
        }

        @Test
        @DisplayName("throwMaxRange <= throwMinRange 时应调整")
        void throwMaxRangeAdjusted() {
            config.setThrowMinRange(20);
            config.setThrowMaxRange(15);
            config.validate();
            assertEquals(35, config.getThrowMaxRange(), "应为 throwMinRange + 15");
        }

        @Test
        @DisplayName("null 字段不应被 validate 影响")
        void nullFieldsNotAffected() {
            // 只设置武器池
            config.setWeaponPool(Collections.singletonList("AK_47"));

            config.validate();

            assertNull(config.getMagazineSize());
            assertNull(config.getFireRate());
            assertNull(config.getSpread());
            assertNull(config.getReloadDuration());
            assertNull(config.getEffectiveRange());
            assertNull(config.getMaxRange());
        }
    }

    // ==================== getFireRateTicks 测试 ====================

    @Nested
    @DisplayName("getFireRateTicks 射速换算")
    class FireRateTicks {

        @Test
        @DisplayName("fireRate=10 应返回 2 tick")
        void fireRate10Returns2Ticks() {
            config.setFireRate(10.0);
            assertEquals(2L, config.getFireRateTicks());
        }

        @Test
        @DisplayName("fireRate=4 应返回 5 tick")
        void fireRate4Returns5Ticks() {
            config.setFireRate(4.0);
            assertEquals(5L, config.getFireRateTicks());
        }

        @Test
        @DisplayName("fireRate=2.5 应返回 8 tick")
        void fireRate2_5Returns8Ticks() {
            config.setFireRate(2.5);
            assertEquals(8L, config.getFireRateTicks());
        }

        @Test
        @DisplayName("fireRate=null 应返回默认 5 tick")
        void nullFireRateReturnsDefault() {
            assertEquals(5L, config.getFireRateTicks());
        }
    }

    // ==================== 边界与异常场景 ====================

    @Nested
    @DisplayName("边界与异常场景")
    class EdgeCases {

        @Test
        @DisplayName("武器池单武器自动构成列表")
        void singleWeaponInPool() {
            config.setWeaponPool(Collections.singletonList("AK_47"));
            assertEquals(1, config.getWeaponPool().size());
            assertEquals("AK_47", config.getWeaponPool().getFirst());
        }

        @Test
        @DisplayName("toString 不抛异常")
        void toStringDoesNotThrow() {
            config.setWeaponPool(Collections.singletonList("AK_47"));
            config.setFireRate(5.0);
            assertDoesNotThrow(() -> config.toString());
            assertNotNull(config.toString());
        }

        @Test
        @DisplayName("mergeWithTemplate 后显式标记字段仍应保留")
        void explicitlySetFieldsPreservedAfterMerge() {
            config.setMagazineSize(15);
            EMWMWeaponConfig template = new EMWMWeaponConfig();
            template.setMagazineSize(30);
            template.setFireRate(8.0);

            config.mergeWithTemplate(template);

            assertTrue(config.isFieldExplicitlySet("magazineSize"), "显式标记应保留");
            assertFalse(config.isFieldExplicitlySet("fireRate"), "未设置的字段不应标记");
        }
    }
}
