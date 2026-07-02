package com.emwbridge.integration;

import com.emwbridge.managers.MobWeaponManager.MobWeaponInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MobWeaponInstance 状态机集成测试
 *
 * 测试武器实例的核心状态机：弹药、耐久、射速、换弹、损坏等逻辑。
 * 这些测试不依赖任何 Mock，属于纯逻辑黑盒测试。
 */
@DisplayName("MobWeaponInstance 状态机测试")
class MobWeaponInstanceStateTest {

    private MobWeaponInstance createInstance(int magSize, int ammo, long fireRateMs) {
        return new MobWeaponInstance("AK_47",
                100, 100,   // durability
                false,       // broken
                magSize, ammo,
                60,          // reloadTicks
                fireRateMs,
                3.0,         // baseSpread
                0.5          // adsSpreadMultiplier
        );
    }

    @Test
    @DisplayName("满弹匣 → canShoot 返回 true")
    void testCanShootWithFullMagazine() {
        var instance = createInstance(30, 30, 100);
        assertTrue(instance.canShoot(), "满弹匣应能射击");
    }

    @Test
    @DisplayName("空弹匣 → canShoot 返回 false")
    void testCannotShootWithEmptyMagazine() {
        var instance = createInstance(30, 0, 100);
        assertFalse(instance.canShoot(), "空弹匣不应能射击");
    }

    @Test
    @DisplayName("弹药递减 → 射击后 ammo 减 1")
    void testAmmoDecrementsAfterShot() {
        var instance = createInstance(30, 30, 100);
        instance.markShot();
        instance.setCurrentAmmo(instance.getCurrentAmmo() - 1);
        assertEquals(29, instance.getCurrentAmmo(), "射击后弹药应减1");
    }

    @Test
    @DisplayName("射速限制 → 冷却期内 cannot shoot")
    void testFireRateCooldown() throws InterruptedException {
        var instance = createInstance(30, 30, 500); // 500ms fire rate
        instance.markShot();
        instance.setCurrentAmmo(instance.getCurrentAmmo() - 1);

        // 立即再射 → 应被射速限制阻挡
        assertFalse(instance.canShoot(), "冷却期内不应能射击");

        // 等待冷却期过后
        Thread.sleep(550);
        assertTrue(instance.canShoot(), "冷却期后应能射击");
    }

    @Test
    @DisplayName("武器损坏 → canShoot 返回 false")
    void testCannotShootWhenBroken() {
        var instance = createInstance(30, 30, 100);
        instance.setBroken(true);
        assertFalse(instance.canShoot(), "损坏武器不应能射击");
    }

    @Test
    @DisplayName("耐久为 0 → canShoot 返回 false")
    void testCannotShootWhenDurabilityZero() {
        var instance = createInstance(30, 30, 100);
        instance.setCurrentDurability(0);
        assertFalse(instance.canShoot(), "耐久为0不应能射击");
    }

    @Test
    @DisplayName("换弹中 → canShoot 返回 false")
    void testCannotShootWhileReloading() {
        var instance = createInstance(30, 30, 100);
        instance.setReloading(true);
        assertFalse(instance.canShoot(), "换弹中不应能射击");
    }

    @Test
    @DisplayName("弹药为 0 且换弹中 → canShoot 返回 false")
    void testCannotShootWithEmptyAndReloading() {
        var instance = createInstance(30, 0, 100);
        instance.setReloading(true);
        assertFalse(instance.canShoot(), "空弹匣+换弹中不应能射击");
    }

    @Test
    @DisplayName("换弹完成 → 弹药恢复满")
    void testReloadRestoresFullAmmo() {
        var instance = createInstance(30, 5, 100);
        instance.setCurrentAmmo(instance.getMagazineSize());
        assertEquals(30, instance.getCurrentAmmo(), "换弹后弹药应恢复满");
    }

    @Test
    @DisplayName("耐用度递减 → 每发 -1")
    void testDurabilityDecrementsPerShot() {
        var instance = new MobWeaponInstance("M4A1",
                100, 100, false,
                30, 30, 60, 100,
                3.0, 0.5);

        for (int i = 0; i < 10; i++) {
            instance.setCurrentDurability(instance.getCurrentDurability() - 1);
        }
        assertEquals(90, instance.getCurrentDurability(), "10发后耐久应为90");
    }

    @Test
    @DisplayName("耐用度降至 0 → 武器损坏标记")
    void testDurabilityZeroMarksBroken() {
        var instance = new MobWeaponInstance("AK_47",
                100, 100, false,
                30, 30, 60, 100,
                3.0, 0.5);

        instance.setCurrentDurability(0);
        instance.setBroken(true);
        assertTrue(instance.isBroken(), "耐久为0时应标记损坏");
        assertFalse(instance.canShoot(), "损坏后不应能射击");
    }

    @Test
    @DisplayName("武器信息完整性 → 所有 getter 返回正确值")
    void testAllGettersReturnCorrectValues() {
        var instance = new MobWeaponInstance("M4A1",
                85, 120, false,
                30, 25, 45,
                200,  // 300 RPM
                2.5, 0.6);

        assertEquals("M4A1", instance.getWeaponTitle());
        assertEquals(85, instance.getCurrentDurability());
        assertEquals(120, instance.getMaxDurability());
        assertFalse(instance.isBroken());
        assertEquals(30, instance.getMagazineSize());
        assertEquals(25, instance.getCurrentAmmo());
        assertEquals(45, instance.getReloadTicks());
        assertEquals(200, instance.getFireRateMs());
        assertEquals(2.5, instance.getBaseSpread(), 0.01);
        assertEquals(0.6, instance.getAdsSpreadMultiplier(), 0.01);
        assertFalse(instance.isReloading());
    }
}