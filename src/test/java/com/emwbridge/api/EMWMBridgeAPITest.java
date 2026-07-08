package com.emwbridge.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EMWMBridgeAPI 公开 API 单元测试")
class EMWMBridgeAPITest {

    private static final String PLUGIN_NAME = "EM-WM-Bridge";

    private LivingEntity mockMob() {
        return mock(LivingEntity.class);
    }

    /** 设置由 EM-WM-Bridge 插件拥有的 metadata */
    private void setMeta(LivingEntity entity, String key, Object value) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn(PLUGIN_NAME);
        MetadataValue mv = new FixedMetadataValue(plugin, value);
        when(entity.hasMetadata(key)).thenReturn(true);
        when(entity.getMetadata(key)).thenReturn(List.of(mv));
    }

    @Test
    @DisplayName("getTier 应读取 emwm_tier")
    void getTierShouldReadTier() {
        LivingEntity e = mockMob();
        setMeta(e, "emwm_tier", "boss");
        assertEquals("boss", EMWMBridgeAPI.getTier(e));
    }

    @Test
    @DisplayName("getCombatState 应读取 emwm_combat_state")
    void getCombatStateShouldReadState() {
        LivingEntity e = mockMob();
        setMeta(e, "emwm_combat_state", "suppressing");
        assertEquals("suppressing", EMWMBridgeAPI.getCombatState(e));
    }

    @Test
    @DisplayName("getWeapon 应读取 emwm_weapon")
    void getWeaponShouldReadWeapon() {
        LivingEntity e = mockMob();
        setMeta(e, "emwm_weapon", "AK_47");
        assertEquals("AK_47", EMWMBridgeAPI.getWeapon(e));
    }

    @Test
    @DisplayName("getAmmo 应读取 emwm_ammo 整数")
    void getAmmoShouldReadInt() {
        LivingEntity e = mockMob();
        setMeta(e, "emwm_ammo", 30);
        assertEquals(30, EMWMBridgeAPI.getAmmo(e));
    }

    @Test
    @DisplayName("getAggressiveness 应读取 emwm_aggressiveness 浮点")
    void getAggressivenessShouldReadDouble() {
        LivingEntity e = mockMob();
        setMeta(e, "emwm_aggressiveness", 0.9);
        assertEquals(0.9, EMWMBridgeAPI.getAggressiveness(e), 0.001);
    }

    @Test
    @DisplayName("isReloading 应读取 emwm_reloading 布尔")
    void isReloadingShouldReadBoolean() {
        LivingEntity e = mockMob();
        setMeta(e, "emwm_reloading", true);
        assertTrue(EMWMBridgeAPI.isReloading(e));
    }

    @Test
    @DisplayName("无 metadata 时应返回默认值而非抛异常")
    void defaultsWhenNoMetadata() {
        LivingEntity e = mockMob();
        // 未设置任何 metadata，所有读取型方法应安全返回 null / 默认值
        assertNull(EMWMBridgeAPI.getTier(e));
        assertEquals(0, EMWMBridgeAPI.getAmmo(e));
        assertEquals(0.5, EMWMBridgeAPI.getAggressiveness(e), 0.001);
        assertEquals(40.0, EMWMBridgeAPI.getMaxRange(e), 0.001);
        assertFalse(EMWMBridgeAPI.isReloading(e));
        assertFalse(EMWMBridgeAPI.isAIEnabled(e));
    }

    @Test
    @DisplayName("isBoss 应基于 tier 判断")
    void isBossShouldCheckTier() {
        LivingEntity boss = mockMob();
        setMeta(boss, "emwm_tier", "boss");
        assertTrue(EMWMBridgeAPI.isBoss(boss));

        LivingEntity scav = mockMob();
        setMeta(scav, "emwm_tier", "scav");
        assertFalse(EMWMBridgeAPI.isBoss(scav));
    }

    @Test
    @DisplayName("isEMWMMob 应基于 emwm_ai_enabled 判断")
    void isEMWMMobShouldCheckFlag() {
        LivingEntity mob = mockMob();
        setMeta(mob, "emwm_ai_enabled", true);
        assertTrue(EMWMBridgeAPI.isEMWMMob(mob));

        LivingEntity nonMob = mockMob();
        assertFalse(EMWMBridgeAPI.isEMWMMob(nonMob));
    }

    @Test
    @DisplayName("isInCombat / isSuppressing 应基于战斗状态判断")
    void combatStateHelpers() {
        LivingEntity suppressing = mockMob();
        setMeta(suppressing, "emwm_combat_state", "suppressing");
        assertTrue(EMWMBridgeAPI.isInCombat(suppressing));
        assertTrue(EMWMBridgeAPI.isSuppressing(suppressing));

        LivingEntity patrol = mockMob();
        setMeta(patrol, "emwm_combat_state", "patrol");
        assertFalse(EMWMBridgeAPI.isInCombat(patrol));
        assertFalse(EMWMBridgeAPI.isSuppressing(patrol));

        LivingEntity retreat = mockMob();
        setMeta(retreat, "emwm_combat_state", "retreat");
        assertTrue(EMWMBridgeAPI.isRetreating(retreat));
    }
}
