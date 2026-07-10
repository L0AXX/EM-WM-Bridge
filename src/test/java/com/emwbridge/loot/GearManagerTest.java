package com.emwbridge.loot;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMWeaponConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 需求7 GearManager 单元测试（同包，可直接注入包级可见的 armorDefs）。
 * 不调用 reload()（避免依赖 greyzone_armors.yml 文件），改为直接注入定义。
 *
 * <p>说明：本环境为 Mockito-only（无 MockBukkit），无法初始化 Bukkit Material/ItemStack/Equipment，
 * 故 buildArmorItem 的 ItemStack 组装、equipGear 的套甲、applyMaxHealthBoost 的加血均不在单测覆盖
 * （留待测试服黑盒验证）；单测聚焦纯逻辑：白名单校验、resolveSlotKey 槽位映射、shouldDropGear 默认值、
 * maxHealthBoost 默认值/垫片。
 */
@DisplayName("GearManager 护甲套用/掉落测试")
class GearManagerTest {

    private GearManager gearManager;

    @BeforeEach
    void setUp() {
        gearManager = new GearManager(mock(EMWMBridge.class));
        // 注入已知护甲定义（材质用字符串名，避免触碰 Bukkit Material 枚举）
        gearManager.armorDefs.put("greyzone_vest_kevlar",
                new GearManager.ArmorItemDef("IRON_CHESTPLATE", "§f凯夫拉背心",
                        Collections.singletonList("§7GreyZone 护甲")));
        gearManager.armorDefs.put("greyzone_helmet_light",
                new GearManager.ArmorItemDef("IRON_HELMET", "§f轻型头盔", Collections.emptyList()));
    }

    @Test
    @DisplayName("白名单：未定义护甲 key 返回 null（不套/不掉）")
    void unknownTypeReturnsNull() {
        assertNull(gearManager.buildArmorItem("not_defined"), "白名单外的护甲不套/不掉");
    }

    @Test
    @DisplayName("resolveSlotKey：helmet/chestplate/leggings/boots 映射到正确字段")
    void resolveSlotKeyMapsToField() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        config.setGearHelmet("greyzone_helmet_light");
        config.setGearChestplate("greyzone_vest_kevlar");
        config.setGearLeggings("greyzone_pads_light");
        config.setGearBoots("greyzone_boots_tactical");

        assertEquals("greyzone_helmet_light", gearManager.resolveSlotKey(config, "helmet"));
        assertEquals("greyzone_vest_kevlar", gearManager.resolveSlotKey(config, "chestplate"));
        assertEquals("greyzone_pads_light", gearManager.resolveSlotKey(config, "leggings"));
        assertEquals("greyzone_boots_tactical", gearManager.resolveSlotKey(config, "boots"));
    }

    @Test
    @DisplayName("resolveSlotKey：某槽未配置返回 null（不穿该槽）")
    void resolveSlotKeyUnsetReturnsNull() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        config.setGearChestplate("greyzone_vest_kevlar");
        // 其余槽未配置
        assertNull(gearManager.resolveSlotKey(config, "helmet"));
        assertNull(gearManager.resolveSlotKey(config, "leggings"));
        assertNull(gearManager.resolveSlotKey(config, "boots"));
        assertEquals("greyzone_vest_kevlar", gearManager.resolveSlotKey(config, "chestplate"));
    }

    @Test
    @DisplayName("resolveSlotKey：非法槽名返回 null")
    void resolveSlotKeyInvalidSlotReturnsNull() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        assertNull(gearManager.resolveSlotKey(config, "offhand"), "非法槽名返回 null");
    }

    @Test
    @DisplayName("shouldDropGear：默认 true（防玩家白嫖护甲经济）")
    void shouldDropGearDefaultsToTrue() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        assertTrue(gearManager.shouldDropGear(config), "未设置时默认掉落护甲");
    }

    @Test
    @DisplayName("shouldDropGear：显式 false 时关闭掉落")
    void shouldDropGearExplicitFalse() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        config.setGearDropGear(false);
        assertFalse(gearManager.shouldDropGear(config), "显式 false → 不掉落护甲");
    }

    @Test
    @DisplayName("maxHealthBoost：默认 0（信赖 AM，待 7.4 验证门）")
    void maxHealthBoostDefaultsToZero() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        assertEquals(0, config.getGearMaxHealthBoostOrDefault(), "未设置时默认 0");
    }

    @Test
    @DisplayName("maxHealthBoost：显式正值启用 7.4 垫片")
    void maxHealthBoostExplicitPositive() {
        EMWMWeaponConfig config = new EMWMWeaponConfig();
        config.setGearMaxHealthBoost(20);
        assertEquals(20, config.getGearMaxHealthBoostOrDefault(), "显式 20 → 由桥接层补 20 最大生命");
    }

    @Test
    @DisplayName("buildArmorItem：已知类型定义存在（白名单查找非空，ItemStack 组装留待测试服）")
    void buildArmorItemKnownTypeNotNullDef() {
        assertNotNull(gearManager.armorDefs.get("greyzone_vest_kevlar"), "已知护甲应在白名单内");
        assertNotNull(gearManager.armorDefs.get("greyzone_helmet_light"), "已知护甲应在白名单内");
    }

    @Test
    @DisplayName("GEAR_SLOTS 顺序固定为 helmet/chestplate/leggings/boots")
    void gearSlotsOrderFixed() {
        assertEquals(java.util.Arrays.asList("helmet", "chestplate", "leggings", "boots"),
                GearManager.GEAR_SLOTS, "槽位顺序须与 config gear 块字段一致");
    }
}
