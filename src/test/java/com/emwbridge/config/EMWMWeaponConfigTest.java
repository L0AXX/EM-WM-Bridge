package com.emwbridge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1.3 — EMWMWeaponConfig 模板合并逻辑单元测试
 *
 * 验证三级继承中“怪物个体配置 → 全局模板”这一层：
 * 个体显式设置（非null）的值保留，未设置（null）的值从模板继承。
 */
@DisplayName("EMWMWeaponConfig 模板合并逻辑测试")
class EMWMWeaponConfigTest {

    /** 构造一个填好全部字段的全局模板 */
    private EMWMWeaponConfig fullTemplate() {
        EMWMWeaponConfig t = new EMWMWeaponConfig();
        t.setWeaponPool(List.of("AK_47", "MP5"));
        t.setWeaponWeights(Map.of("AK_47", 1.0, "MP5", 2.0));
        t.setMagazineSize(30);
        t.setReloadDuration(60);
        t.setFireRate(8.0);
        t.setSpread(3.0);
        t.setEffectiveRange(20);
        t.setMaxRange(40);
        t.setAggressiveness(0.7);
        t.setMeleeRange(3.0);
        t.setDurabilityPerShot(1);
        return t;
    }

    @Test
    @DisplayName("null 字段应从模板继承，非 null 字段保留个体值")
    void mergeInheritsOnlyNullFields() {
        EMWMWeaponConfig template = fullTemplate();

        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        // 个体只显式设置武器池 + 弹匣
        individual.setWeaponPool(List.of("M4"));
        individual.setMagazineSize(45);
        // 其余保持 null，等待从模板继承

        individual.mergeWithTemplate(template);

        // 个体显式值保留
        assertEquals(List.of("M4"), individual.getWeaponPool());
        assertEquals(45, individual.getMagazineSize());
        // 其余从模板继承
        assertEquals(60, individual.getReloadDuration());
        assertEquals(8.0, individual.getFireRate(), 0.001);
        assertEquals(3.0, individual.getSpread(), 0.001);
        assertEquals(20, individual.getEffectiveRange());
        assertEquals(40, individual.getMaxRange());
        assertEquals(0.7, individual.getAggressiveness(), 0.001);
        assertEquals(3.0, individual.getMeleeRange(), 0.001);
        assertEquals(1, individual.getDurabilityPerShot());
    }

    @Test
    @DisplayName("模板为 null 时不做任何合并（保持原值）")
    void mergeNullTemplateIsNoop() {
        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.setMagazineSize(50);
        individual.mergeWithTemplate(null);
        assertEquals(50, individual.getMagazineSize());
        assertNull(individual.getReloadDuration());
    }

    @Test
    @DisplayName("武器权重为空时从模板继承")
    void mergeInheritsWeaponWeights() {
        EMWMWeaponConfig template = fullTemplate();
        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.setWeaponPool(List.of("AK_47"));
        // weaponWeights 保持空 Map（默认）
        individual.mergeWithTemplate(template);
        assertEquals(Map.of("AK_47", 1.0, "MP5", 2.0), individual.getWeaponWeights());
    }

    @Test
    @DisplayName("模板已是完整配置时，个体可完全不配置")
    void individualFullyInheritsFromTemplate() {
        EMWMWeaponConfig template = fullTemplate();
        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.mergeWithTemplate(template);
        assertEquals(List.of("AK_47", "MP5"), individual.getWeaponPool());
        assertEquals(30, individual.getMagazineSize());
        assertEquals(8.0, individual.getFireRate(), 0.001);
        assertEquals(0.7, individual.getAggressiveness(), 0.001);
    }

    @Test
    @DisplayName("consumeAmmo 默认 true(有限,向后兼容)")
    void consumeAmmoDefaultsToTrue() {
        EMWMWeaponConfig c = new EMWMWeaponConfig();
        assertTrue(c.isConsumeAmmoOrDefault(), "未设置时默认 true(有限弹药)");
        assertNull(c.getConsumeAmmo(), "原始值应为 null");
    }

    @Test
    @DisplayName("consumeAmmo=false → isConsumeAmmoOrDefault 为 false(经济护栏)")
    void consumeAmmoFalseOverridesDefault() {
        EMWMWeaponConfig c = new EMWMWeaponConfig();
        c.setConsumeAmmo(false);
        assertFalse(c.isConsumeAmmoOrDefault(), "显式 false → 无限弹药(经济护栏)");
    }

    @Test
    @DisplayName("合并时 consumeAmmo 从模板继承(null 字段)")
    void consumeAmmoInheritsFromTemplate() {
        EMWMWeaponConfig template = new EMWMWeaponConfig();
        template.setConsumeAmmo(false);
        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.mergeWithTemplate(template);
        assertFalse(individual.isConsumeAmmoOrDefault(), "个体未设置时继承模板的 false");
    }

    // ==================== 需求4：neverRetreat + personalityPreset ====================

    @Test
    @DisplayName("neverRetreat 默认 false(可撤退,向后兼容)")
    void neverRetreatDefaultsToFalse() {
        EMWMWeaponConfig c = new EMWMWeaponConfig();
        assertFalse(c.isNeverRetreatOrDefault(), "未设置时默认 false(允许撤退)");
        assertNull(c.getNeverRetreat(), "原始值应为 null");
    }

    @Test
    @DisplayName("neverRetreat=true → isNeverRetreatOrDefault 为 true(死守不退)")
    void neverRetreatTrueOverridesDefault() {
        EMWMWeaponConfig c = new EMWMWeaponConfig();
        c.setNeverRetreat(true);
        assertTrue(c.isNeverRetreatOrDefault(), "显式 true → 永不撤退");
    }

    @Test
    @DisplayName("合并时 neverRetreat 从模板继承(null 字段)")
    void neverRetreatInheritsFromTemplate() {
        EMWMWeaponConfig template = new EMWMWeaponConfig();
        template.setNeverRetreat(true);
        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.mergeWithTemplate(template);
        assertTrue(individual.isNeverRetreatOrDefault(), "个体未设置时继承模板的 true");
    }

    @Test
    @DisplayName("personalityPreset 默认 null(按 tier 随机 roll)")
    void personalityPresetDefaultsToNull() {
        EMWMWeaponConfig c = new EMWMWeaponConfig();
        assertNull(c.getPersonalityPreset(), "未设置时默认 null");
    }

    @Test
    @DisplayName("合并时 personalityPreset 从模板继承(null 字段)")
    void personalityPresetInheritsFromTemplate() {
        EMWMWeaponConfig template = new EMWMWeaponConfig();
        template.setPersonalityPreset("fanatic");
        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.mergeWithTemplate(template);
        assertEquals("fanatic", individual.getPersonalityPreset(), "个体未设置时继承模板的预设名");
    }

    // ==================== 需求2/需求3：squad + 据点守卫行为 默认与继承 ====================

    @Test
    @DisplayName("behavior 默认 null(=FREE 游荡,向后兼容)")
    void behaviorDefaultsToNull() {
        EMWMWeaponConfig c = new EMWMWeaponConfig();
        assertNull(c.getBehavior(), "未设置时默认 null(=FREE)");
    }

    @Test
    @DisplayName("据点守卫半径默认值：guard=12 / aggro=35 / leash=45")
    void guardRadiiDefaults() {
        EMWMWeaponConfig c = new EMWMWeaponConfig();
        assertEquals(12.0, c.getGuardRadiusOrDefault(), 0.001);
        assertEquals(35.0, c.getAggroRadiusOrDefault(), 0.001);
        assertEquals(45.0, c.getLeashDistanceOrDefault(), 0.001);
    }

    @Test
    @DisplayName("合并时 squad/behavior/guard 半径从模板继承(null 字段)")
    void squadAndGuardInheritFromTemplate() {
        EMWMWeaponConfig template = new EMWMWeaponConfig();
        template.setSquad("iron_legion_fireteam");
        template.setBehavior("GUARD");
        template.setAggroRadius(30.0);
        template.setLeashDistance(50.0);

        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.mergeWithTemplate(template);

        assertEquals("iron_legion_fireteam", individual.getSquad(), "继承编制名");
        assertEquals("GUARD", individual.getBehavior(), "继承行为模式");
        assertEquals(30.0, individual.getAggroRadiusOrDefault(), 0.001, "继承 aggro 半径");
        assertEquals(50.0, individual.getLeashDistanceOrDefault(), 0.001, "继承 leash 距离");
    }

    @Test
    @DisplayName("合并时 lootAmmo 字段从模板继承(null 字段)")
    void lootAmmoInheritsFromTemplate() {
        EMWMWeaponConfig template = new EMWMWeaponConfig();
        template.setLootAmmoType("greyzone_rifle_improv");
        template.setLootAmmoMin(5);
        template.setLootAmmoMax(12);

        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.mergeWithTemplate(template);

        assertEquals("greyzone_rifle_improv", individual.getLootAmmoType(), "继承掉落类型");
        assertEquals(5, individual.getLootAmmoMin());
        assertEquals(12, individual.getLootAmmoMax());
    }

    @Test
    @DisplayName("lootAmmoMin/Max 默认值 = 规格 §3 [8,24]（待测试服校准）")
    void lootAmmoDefaults() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        assertEquals(8, config.getLootAmmoMinOrDefault(), "默认下限 8");
        assertEquals(24, config.getLootAmmoMaxOrDefault(), "默认上限 24");
    }

    // ==================== 需求7：护甲混用（ArmorMechanics）默认与继承 ====================

    @Test
    @DisplayName("gear 默认值：dropGear=true(掉落) / maxHealthBoost=0(信赖 AM)")
    void gearDefaults() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        assertTrue(config.getGearDropGearOrDefault(), "默认掉落护甲");
        assertNull(config.getGearDropGear(), "原始值应为 null");
        assertEquals(0, config.getGearMaxHealthBoostOrDefault(), "默认 0（信赖 AM，待 7.4 验证门）");
        assertNull(config.getGearMaxHealthBoost(), "原始值应为 null");
    }

    @Test
    @DisplayName("合并时 gear 字段从模板继承(null 字段)")
    void gearInheritsFromTemplate() {
        EMWMWeaponConfig template = new EMWMWeaponConfig();
        template.setGearHelmet("greyzone_helmet_light");
        template.setGearChestplate("greyzone_vest_kevlar");
        template.setGearLeggings("greyzone_pads_light");
        template.setGearBoots("greyzone_boots_tactical");
        template.setGearDropGear(false);
        template.setGearMaxHealthBoost(20);

        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.mergeWithTemplate(template);

        assertEquals("greyzone_helmet_light", individual.getGearHelmet(), "继承头盔");
        assertEquals("greyzone_vest_kevlar", individual.getGearChestplate(), "继承胸甲");
        assertEquals("greyzone_pads_light", individual.getGearLeggings(), "继承护腿");
        assertEquals("greyzone_boots_tactical", individual.getGearBoots(), "继承靴子");
        assertFalse(individual.getGearDropGearOrDefault(), "继承 dropGear=false");
        assertEquals(20, individual.getGearMaxHealthBoostOrDefault(), "继承 maxHealthBoost=20");
    }

    @Test
    @DisplayName("合并时个体显式 gear 值覆盖模板")
    void gearIndividualOverridesTemplate() {
        EMWMWeaponConfig template = new EMWMWeaponConfig();
        template.setGearChestplate("greyzone_vest_kevlar");
        template.setGearDropGear(false);

        EMWMWeaponConfig individual = new EMWMWeaponConfig();
        individual.setGearChestplate("greyzone_vest_plate");
        individual.setGearDropGear(true);
        individual.mergeWithTemplate(template);

        assertEquals("greyzone_vest_plate", individual.getGearChestplate(), "个体覆盖模板胸甲");
        assertTrue(individual.getGearDropGearOrDefault(), "个体覆盖 dropGear=true");
    }
}
