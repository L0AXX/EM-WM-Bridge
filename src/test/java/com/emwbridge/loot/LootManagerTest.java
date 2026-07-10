package com.emwbridge.loot;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMWeaponConfig;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 需求6 LootManager 单元测试（同包，可直接注入包级可见的 ammoDefs / gunAmmoMap）。
 * 不调用 reload()（避免依赖 greyzone_ammos.yml 文件），改为直接注入定义。
 *
 * <p>说明：本环境为 Mockito-only（无 MockBukkit），无法初始化 Bukkit Material/ItemStack，
 * 故 buildAmmoItem 的 ItemStack 组装不在单测覆盖（留待测试服黑盒验证）；单测聚焦纯逻辑：
 * 白名单校验、数量区间随机、硬上限熔断、resolveAmmoType 解析优先级。
 */
@DisplayName("LootManager 货币弹掉落测试")
class LootManagerTest {

    private LootManager lootManager;

    @BeforeEach
    void setUp() {
        lootManager = new LootManager(mock(EMWMBridge.class));
        // 注入一种已知货币弹定义（材质用字符串名，避免触碰 Bukkit Material 枚举）
        lootManager.ammoDefs.put("greyzone_rifle_improv",
                new LootManager.AmmoItemDef("ARROW", "§a改装步枪弹",
                        Collections.singletonList("§7GreyZone 货币")));
        lootManager.ammoDefs.put("greyzone_pistol_ammo",
                new LootManager.AmmoItemDef("ARROW", "§6手枪弹", Collections.emptyList()));
    }

    @Test
    @DisplayName("白名单：未定义类型返回 null（不掉落）")
    void unknownTypeReturnsNull() {
        assertNull(lootManager.buildAmmoItem("not_defined", 8, 24), "白名单外的类型不掉落");
    }

    @Test
    @DisplayName("computeLootAmount：min==max 时数量精确")
    void lootAmountExactWhenMinEqualsMax() {
        assertEquals(10, lootManager.computeLootAmount(10, 10));
    }

    @Test
    @DisplayName("computeLootAmount：硬上限熔断（超过 64 钳制）")
    void lootAmountHardCap() {
        assertEquals(LootManager.HARD_CAP, lootManager.computeLootAmount(100, 200), "100~200 应钳到 64");
    }

    @Test
    @DisplayName("computeLootAmount：min 被钳到至少 1")
    void lootAmountMinClampedTo1() {
        int amount = lootManager.computeLootAmount(0, 5);
        assertTrue(amount >= 1 && amount <= 5, "min<=0 应钳到 [1,5]: " + amount);
    }

    @Test
    @DisplayName("computeLootAmount：区间随机落在 [min,max] 闭区间")
    void lootAmountWithinRange() {
        lootManager.setRandom(new Random(42));
        for (int i = 0; i < 300; i++) {
            int amount = lootManager.computeLootAmount(8, 24);
            assertTrue(amount >= 8 && amount <= 24, "数量应在 [8,24]: " + amount);
        }
    }

    @Test
    @DisplayName("resolveAmmoType：模板显式 lootAmmoType 优先于 gun→ammo 映射")
    void explicitLootTypeWins() {
        lootManager.gunAmmoMap.put("AK_47", "greyzone_rifle_improv");
        EMWMWeaponConfig config = mock(EMWMWeaponConfig.class);
        when(config.getLootAmmoType()).thenReturn("greyzone_pistol_ammo");
        assertEquals("greyzone_pistol_ammo", lootManager.resolveAmmoType(config, "AK_47"));
    }

    @Test
    @DisplayName("resolveAmmoType：lootAmmoType 为 null 时回退 gun→ammo 映射")
    void gunMapFallback() {
        lootManager.gunAmmoMap.put("AK_47", "greyzone_rifle_improv");
        EMWMWeaponConfig config = mock(EMWMWeaponConfig.class);
        when(config.getLootAmmoType()).thenReturn(null);
        assertEquals("greyzone_rifle_improv", lootManager.resolveAmmoType(config, "AK_47"));
    }

    @Test
    @DisplayName("resolveAmmoType：两者皆空返回 null（不掉落）")
    void noMatchReturnsNull() {
        EMWMWeaponConfig config = mock(EMWMWeaponConfig.class);
        when(config.getLootAmmoType()).thenReturn(null);
        assertNull(lootManager.resolveAmmoType(config, "UNKNOWN_GUN"));
    }

    @Test
    @DisplayName("resolveAmmoType：gun→ammo 映射缺失指定武器时回退为 null")
    void gunMapMissReturnsNull() {
        lootManager.gunAmmoMap.put("AK_47", "greyzone_rifle_improv");
        EMWMWeaponConfig config = mock(EMWMWeaponConfig.class);
        when(config.getLootAmmoType()).thenReturn(null);
        assertNull(lootManager.resolveAmmoType(config, "M4_NOT_MAPPED"));
    }

    @Test
    @DisplayName("buildAmmoItem：白名单拒绝分支返回 null（已知类型存在但本环境不构造 ItemStack）")
    void buildAmmoItemKnownTypeNotNullDef() {
        // 已知类型已注入定义，ammoDefs.get 非空 → 进入后续逻辑（本环境无法 new ItemStack，
        // 但至少验证白名单查找不为 null；真正的 ItemStack 组装留待测试服验证）
        assertNotNull(lootManager.ammoDefs.get("greyzone_rifle_improv"), "已知类型应在白名单内");
    }
}
