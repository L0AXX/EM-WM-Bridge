package com.emwbridge.ai.perception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertStage 警戒阶段测试")
class AlertStageTest {

    private UUID aiUuid;
    private UUID targetUuid;

    @BeforeEach
    void setUp() {
        aiUuid = UUID.randomUUID();
        targetUuid = UUID.randomUUID();
    }

    @Nested
    @DisplayName("fromExposure 状态判定")
    class FromExposure {

        @ParameterizedTest(name = "曝光值 {0} → {1}")
        @CsvSource({
            "-10.0, IDLE",
            "0.0, IDLE",
            "0.1, YELLOW",
            "19.9, YELLOW",
            "20.0, ORANGE",
            "34.9, ORANGE",
            "35.0, RED",
            "50.0, RED",
            "100.0, RED"
        })
        @DisplayName("应根据曝光值返回正确阶段")
        void shouldReturnCorrectStage(double exposure, String expected) {
            AlertStage actual = AlertStage.fromExposure(exposure);
            if ("IDLE".equals(expected)) {
                assertNull(actual);
            } else {
                assertEquals(AlertStage.valueOf(expected), actual);
            }
        }
    }

    @Nested
    @DisplayName("transition 状态流转")
    class Transition {

        @Test
        @DisplayName("曝光归零应返回 null")
        void zeroExposureShouldReturnNull() {
            assertNull(AlertStage.transition(AlertStage.RED, 0.0, false));
            assertNull(AlertStage.transition(AlertStage.ORANGE, 0.0, false));
            assertNull(AlertStage.transition(AlertStage.YELLOW, 0.0, false));
        }

        @Test
        @DisplayName("声音触发时 YELLOW 应跳到 ORANGE")
        void soundTriggeredYellowShouldJumpToOrange() {
            assertEquals(AlertStage.ORANGE, AlertStage.transition(null, 10.0, true));
            assertEquals(AlertStage.ORANGE, AlertStage.transition(AlertStage.YELLOW, 10.0, true));
        }

        @Test
        @DisplayName("声音触发不影响 ORANGE 和 RED")
        void soundTriggeredShouldNotAffectOrangeAndRed() {
            assertEquals(AlertStage.ORANGE, AlertStage.transition(null, 25.0, true));
            assertEquals(AlertStage.RED, AlertStage.transition(null, 40.0, true));
        }

        @Test
        @DisplayName("RED 降级需要滞回阈值")
        void redShouldHaveHysteresis() {
            assertEquals(AlertStage.RED, AlertStage.transition(AlertStage.RED, 30.0, false));
            assertEquals(AlertStage.RED, AlertStage.transition(AlertStage.RED, 25.0, false));
            assertEquals(AlertStage.ORANGE, AlertStage.transition(AlertStage.RED, 24.9, false));
        }

        @Test
        @DisplayName("正常状态升级")
        void normalStateUpgrade() {
            assertEquals(AlertStage.YELLOW, AlertStage.transition(null, 10.0, false));
            assertEquals(AlertStage.ORANGE, AlertStage.transition(AlertStage.YELLOW, 25.0, false));
            assertEquals(AlertStage.RED, AlertStage.transition(AlertStage.ORANGE, 40.0, false));
        }
    }

    @Nested
    @DisplayName("全局仇恨缓存")
    class HatredCache {

        @Test
        @DisplayName("recordHatred 应记录仇恨")
        void recordHatredShouldStore() {
            org.bukkit.Location mockLoc = Mockito.mock(org.bukkit.Location.class);
            AlertStage.recordHatred(aiUuid, targetUuid, mockLoc, 1000);

            assertEquals(targetUuid, AlertStage.getHatredTarget(aiUuid));
            assertEquals(0, AlertStage.getRedElapsedTicks(aiUuid, 1000));
        }

        @Test
        @DisplayName("clearHatred 应清除仇恨")
        void clearHatredShouldRemove() {
            org.bukkit.Location mockLoc = Mockito.mock(org.bukkit.Location.class);
            AlertStage.recordHatred(aiUuid, targetUuid, mockLoc, 1000);
            assertNotNull(AlertStage.getHatredTarget(aiUuid));

            AlertStage.clearHatred(aiUuid);
            assertNull(AlertStage.getHatredTarget(aiUuid));
        }

        @Test
        @DisplayName("isHeadshotWindow 应正确判定锁头窗口")
        void isHeadshotWindowShouldCorrect() {
            org.bukkit.Location mockLoc = Mockito.mock(org.bukkit.Location.class);
            AlertStage.recordHatred(aiUuid, targetUuid, mockLoc, 1000);

            assertTrue(AlertStage.isHeadshotWindow(aiUuid, 1000));
            assertTrue(AlertStage.isHeadshotWindow(aiUuid, 1200));
            assertTrue(AlertStage.isHeadshotWindow(aiUuid, 1300));
            assertFalse(AlertStage.isHeadshotWindow(aiUuid, 1301));

            AlertStage.clearHatred(aiUuid);
            assertFalse(AlertStage.isHeadshotWindow(aiUuid, 1000));
        }

        @Test
        @DisplayName("getRedElapsedTicks 应正确计算")
        void getRedElapsedTicksShouldCalculate() {
            org.bukkit.Location mockLoc = Mockito.mock(org.bukkit.Location.class);
            AlertStage.recordHatred(aiUuid, targetUuid, mockLoc, 1000);

            assertEquals(0, AlertStage.getRedElapsedTicks(aiUuid, 1000));
            assertEquals(100, AlertStage.getRedElapsedTicks(aiUuid, 1100));
            assertEquals(300, AlertStage.getRedElapsedTicks(aiUuid, 1300));
        }
    }

    @Nested
    @DisplayName("行为判定")
    class BehaviorChecks {

        @Test
        @DisplayName("canAttack 只有 RED 能攻击")
        void canAttackOnlyRed() {
            assertFalse(AlertStage.YELLOW.canAttack());
            assertFalse(AlertStage.ORANGE.canAttack());
            assertTrue(AlertStage.RED.canAttack());
        }

        @Test
        @DisplayName("isAlerted 非 IDLE 都处于警戒")
        void isAlertedAllNonIdle() {
            assertTrue(AlertStage.YELLOW.isAlerted());
            assertTrue(AlertStage.ORANGE.isAlerted());
            assertTrue(AlertStage.RED.isAlerted());
        }

        @Test
        @DisplayName("isHostile 只有 RED 是敌对")
        void isHostileOnlyRed() {
            assertFalse(AlertStage.YELLOW.isHostile());
            assertFalse(AlertStage.ORANGE.isHostile());
            assertTrue(AlertStage.RED.isHostile());
        }
    }
}