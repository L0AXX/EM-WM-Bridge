package com.emwbridge.managers;

import com.emwbridge.managers.MobWeaponManager.MobWeaponInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MobWeaponInstance 武器实例测试")
class MobWeaponInstanceTest {

    private MobWeaponInstance instance;

    @BeforeEach
    void setUp() {
        instance = new MobWeaponInstance(
                "AK_47",
                100,
                100,
                false,
                30,
                30,
                60,
                100,
                3.0,
                0.5
        );
    }

    @Nested
    @DisplayName("构造与属性")
    class ConstructionAndProperties {

        @Test
        @DisplayName("构造器应正确初始化属性")
        void constructorShouldInitializeProperties() {
            assertEquals("AK_47", instance.getWeaponTitle());
            assertEquals(100, instance.getCurrentDurability());
            assertEquals(100, instance.getMaxDurability());
            assertFalse(instance.isBroken());
            assertEquals(30, instance.getMagazineSize());
            assertEquals(30, instance.getCurrentAmmo());
            assertEquals(60, instance.getReloadTicks());
            assertFalse(instance.isReloading());
            assertEquals(100, instance.getFireRateMs());
            assertEquals(3.0, instance.getBaseSpread());
            assertEquals(0.5, instance.getAdsSpreadMultiplier());
        }

        @Test
        @DisplayName("损坏状态应正确设置")
        void brokenStateShouldSetCorrectly() {
            assertFalse(instance.isBroken());
            instance.setBroken(true);
            assertTrue(instance.isBroken());
        }

        @Test
        @DisplayName("换弹状态应正确设置")
        void reloadingStateShouldSetCorrectly() {
            assertFalse(instance.isReloading());
            instance.setReloading(true);
            assertTrue(instance.isReloading());
            instance.setReloading(false);
            assertFalse(instance.isReloading());
        }
    }

    @Nested
    @DisplayName("canShoot 射击判定")
    class CanShoot {

        @Test
        @DisplayName("正常状态应可以射击")
        void normalStateShouldAllowShooting() {
            assertTrue(instance.canShoot());
        }

        @Test
        @DisplayName("损坏状态不应可以射击")
        void brokenStateShouldNotAllowShooting() {
            instance.setBroken(true);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("耐久为 0 不应可以射击")
        void zeroDurabilityShouldNotAllowShooting() {
            instance.setCurrentDurability(0);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("换弹中不应可以射击")
        void reloadingShouldNotAllowShooting() {
            instance.setReloading(true);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("弹药耗尽不应可以射击")
        void emptyMagazineShouldNotAllowShooting() {
            instance.setCurrentAmmo(0);
            assertFalse(instance.canShoot());
        }

        @Test
        @DisplayName("射速冷却期间不应可以射击")
        void fireRateCooldownShouldPreventShooting() throws InterruptedException {
            instance.markShot();
            assertFalse(instance.canShoot());

            Thread.sleep(110);
            assertTrue(instance.canShoot());
        }
    }

    @Nested
    @DisplayName("弹药管理")
    class AmmoManagement {

        @Test
        @DisplayName("弹药应正确递减")
        void ammoShouldDecrease() {
            assertEquals(30, instance.getCurrentAmmo());
            instance.setCurrentAmmo(25);
            assertEquals(25, instance.getCurrentAmmo());
            instance.setCurrentAmmo(0);
            assertEquals(0, instance.getCurrentAmmo());
        }

        @Test
        @DisplayName("换弹应填满弹匣")
        void reloadShouldFillMagazine() {
            instance.setCurrentAmmo(5);
            assertEquals(5, instance.getCurrentAmmo());

            instance.setCurrentAmmo(instance.getMagazineSize());
            assertEquals(30, instance.getCurrentAmmo());
        }
    }

    @Nested
    @DisplayName("耐久管理")
    class DurabilityManagement {

        @Test
        @DisplayName("耐久应正确递减")
        void durabilityShouldDecrease() {
            assertEquals(100, instance.getCurrentDurability());
            instance.setCurrentDurability(50);
            assertEquals(50, instance.getCurrentDurability());
            instance.setCurrentDurability(0);
            assertEquals(0, instance.getCurrentDurability());
        }

        @Test
        @DisplayName("耐久为 0 时损坏状态判定")
        void zeroDurabilityShouldTriggerBrokenCheck() {
            instance.setCurrentDurability(0);
            assertFalse(instance.isBroken());

            instance.setBroken(true);
            assertTrue(instance.isBroken());
        }
    }

    @Nested
    @DisplayName("射击记录")
    class ShotRecording {

        @Test
        @DisplayName("markShot 应更新最后射击时间")
        void markShotShouldUpdateLastShotTime() {
            long before = System.currentTimeMillis();
            instance.markShot();
            long after = System.currentTimeMillis();

            assertTrue(instance.getLastShotTime() >= before);
            assertTrue(instance.getLastShotTime() <= after);
        }

        @Test
        @DisplayName("初始最后射击时间应为 0")
        void initialLastShotTimeShouldBeZero() {
            assertEquals(0, instance.getLastShotTime());
        }
    }
}