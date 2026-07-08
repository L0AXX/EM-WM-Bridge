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
}
