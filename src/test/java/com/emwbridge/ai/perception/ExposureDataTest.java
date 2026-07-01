package com.emwbridge.ai.perception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExposureData 曝光值数据类测试")
class ExposureDataTest {

    private ExposureData data;

    @BeforeEach
    void setUp() {
        data = new ExposureData();
    }

    @Nested
    @DisplayName("值操作")
    class ValueOperations {

        @Test
        @DisplayName("初始值应为 0.0")
        void initialValueShouldBeZero() {
            assertEquals(0.0, data.getValue());
        }

        @Test
        @DisplayName("addExposure 应正确累加")
        void addExposureShouldAccumulate() {
            data.addExposure(10.0);
            assertEquals(10.0, data.getValue());

            data.addExposure(20.0);
            assertEquals(30.0, data.getValue());
        }

        @Test
        @DisplayName("addExposure 不应超过 100.0")
        void addExposureShouldNotExceedHundred() {
            data.addExposure(150.0);
            assertEquals(100.0, data.getValue());

            data.addExposure(10.0);
            assertEquals(100.0, data.getValue());
        }

        @Test
        @DisplayName("decayExposure 应正确衰减")
        void decayExposureShouldDecrease() {
            data.addExposure(50.0);
            data.decayExposure(5.0);
            assertEquals(45.0, data.getValue());
        }

        @Test
        @DisplayName("decayExposure 不应低于 0.0")
        void decayExposureShouldNotGoBelowZero() {
            data.addExposure(3.0);
            data.decayExposure(5.0);
            assertEquals(0.0, data.getValue());
        }

        @Test
        @DisplayName("setValue 应正确限制范围")
        void setValueShouldClamp() {
            data.setValue(-10.0);
            assertEquals(0.0, data.getValue());

            data.setValue(150.0);
            assertEquals(100.0, data.getValue());

            data.setValue(50.0);
            assertEquals(50.0, data.getValue());
        }
    }

    @Nested
    @DisplayName("身体部位可见性")
    class BodyPartsVisibility {

        @Test
        @DisplayName("isHeadVisible 应正确检测头部")
        void isHeadVisibleShouldDetectHead() {
            data.setVisibleBodyParts(1);
            assertTrue(data.isHeadVisible());
            assertFalse(data.isTorsoVisible());

            data.setVisibleBodyParts(3);
            assertTrue(data.isHeadVisible());
            assertTrue(data.isTorsoVisible());
        }

        @Test
        @DisplayName("isTorsoVisible 应正确检测躯干")
        void isTorsoVisibleShouldDetectTorso() {
            data.setVisibleBodyParts(2);
            assertFalse(data.isHeadVisible());
            assertTrue(data.isTorsoVisible());
        }

        @Test
        @DisplayName("isAnyPartVisible 应正确检测")
        void isAnyPartVisibleShouldDetect() {
            assertFalse(data.isAnyPartVisible());

            data.setVisibleBodyParts(1);
            assertTrue(data.isAnyPartVisible());

            data.setVisibleBodyParts(0);
            assertFalse(data.isAnyPartVisible());
        }

        @Test
        @DisplayName("getExposeMultiplier 应正确计算")
        void getExposeMultiplierShouldCalculate() {
            data.setVisibleBodyParts(0);
            assertEquals(0.3, data.getExposeMultiplier());

            data.setVisibleBodyParts(1);
            assertEquals(0.9, data.getExposeMultiplier());

            data.setVisibleBodyParts(2);
            assertEquals(0.7, data.getExposeMultiplier());

            data.setVisibleBodyParts(3);
            assertEquals(1.0, data.getExposeMultiplier());

            data.setVisibleBodyParts(4);
            assertEquals(0.4, data.getExposeMultiplier());
        }
    }

    @Nested
    @DisplayName("闪光弹致盲")
    class FlashBlind {

        @Test
        @DisplayName("applyFlashBlind 应重置状态")
        void applyFlashBlindShouldResetState() {
            data.addExposure(80.0);
            data.setVisibleBodyParts(3);

            data.applyFlashBlind();

            assertTrue(data.isFlashBlinded());
            assertEquals(0.0, data.getValue());
            assertEquals(0, data.getVisibleBodyParts());
        }

        @Test
        @DisplayName("致盲期间不应接受曝光增量")
        void blindShouldBlockExposureIncrement() {
            data.applyFlashBlind();
            data.addExposure(50.0);
            assertEquals(0.0, data.getValue());
        }

        @Test
        @DisplayName("tickFlashBlind 应递减致盲时间")
        void tickFlashBlindShouldDecrement() {
            data.applyFlashBlind();
            assertTrue(data.isFlashBlinded());

            for (int i = 0; i < 119; i++) {
                data.tickFlashBlind();
                assertTrue(data.isFlashBlinded());
            }

            data.tickFlashBlind();
            assertFalse(data.isFlashBlinded());
        }
    }

    @Nested
    @DisplayName("HOSTILE 锁头")
    class HostileLock {

        @Test
        @DisplayName("tickHostileLock 应正确计数")
        void tickHostileLockShouldCount() {
            org.bukkit.Location mockLoc = Mockito.mock(org.bukkit.Location.class);
            data.lockHostileTarget(mockLoc, 0);

            for (int i = 0; i < 300; i++) {
                assertTrue(data.tickHostileLock());
            }

            assertFalse(data.tickHostileLock());
        }

        @Test
        @DisplayName("reset 应清除所有状态")
        void resetShouldClearAll() {
            data.addExposure(50.0);
            data.markAlertStart(100);
            org.bukkit.Location mockLoc = Mockito.mock(org.bukkit.Location.class);
            data.lockHostileTarget(mockLoc, 200);
            data.setVisibleBodyParts(3);
            data.applyFlashBlind();

            data.reset();

            assertEquals(0.0, data.getValue());
            assertNull(data.getLastKnownPosition());
            assertEquals(-1, data.getAlertStartTick());
            assertEquals(0, data.getHostileLockTicks());
            assertNull(data.getHostileLockPosition());
            assertEquals(0, data.getVisibleBodyParts());
            assertFalse(data.isFlashBlinded());
        }
    }
}