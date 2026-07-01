package com.emwbridge.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SoundPropagationUtils 声音传播工具类测试")
class SoundPropagationUtilsTest {

    @Nested
    @DisplayName("calculateSoundRadius 声音半径计算")
    class CalculateSoundRadius {

        @Test
        @DisplayName("无消音器应返回基础半径")
        void withoutSuppressorShouldReturnBaseRadius() {
            assertEquals(100.0, SoundPropagationUtils.calculateSoundRadius(100.0, false));
            assertEquals(50.0, SoundPropagationUtils.calculateSoundRadius(50.0, false));
        }

        @Test
        @DisplayName("有消音器应返回基础半径的 30%")
        void withSuppressorShouldReturnThirtyPercent() {
            assertEquals(30.0, SoundPropagationUtils.calculateSoundRadius(100.0, true));
            assertEquals(15.0, SoundPropagationUtils.calculateSoundRadius(50.0, true));
            assertEquals(3.0, SoundPropagationUtils.calculateSoundRadius(10.0, true));
        }

        @Test
        @DisplayName("基础半径为 0 时应返回 0")
        void zeroBaseRadiusShouldReturnZero() {
            assertEquals(0.0, SoundPropagationUtils.calculateSoundRadius(0.0, false));
            assertEquals(0.0, SoundPropagationUtils.calculateSoundRadius(0.0, true));
        }
    }

    @Nested
    @DisplayName("getAwarenessChance 警觉概率计算")
    class GetAwarenessChance {

        @ParameterizedTest(name = "距离 {0}, 半径 {1}, 警觉 {2} → 概率 {3}")
        @CsvSource({
            "0, 100, 1.0, 1.0",
            "50, 100, 1.0, 0.5",
            "90, 100, 1.0, 0.1",
            "100, 100, 1.0, 0.0",
            "110, 100, 1.0, 0.0",
            "0, 100, 0.5, 0.5",
            "50, 100, 0.5, 0.25",
            "0, 50, 1.0, 1.0",
            "25, 50, 1.0, 0.5"
        })
        @DisplayName("应根据距离和警觉度计算正确概率")
        void shouldCalculateCorrectChance(double distance, double radius, double awareness, double expected) {
            assertEquals(expected, SoundPropagationUtils.getAwarenessChance(distance, radius, awareness), 0.001);
        }

        @Test
        @DisplayName("距离超过半径时应返回 0")
        void distanceExceedsRadiusShouldReturnZero() {
            assertEquals(0.0, SoundPropagationUtils.getAwarenessChance(150.0, 100.0, 1.0));
            assertEquals(0.0, SoundPropagationUtils.getAwarenessChance(101.0, 100.0, 1.0));
        }

        @Test
        @DisplayName("警觉度为 0 时应返回 0")
        void zeroAwarenessShouldReturnZero() {
            assertEquals(0.0, SoundPropagationUtils.getAwarenessChance(0.0, 100.0, 0.0));
            assertEquals(0.0, SoundPropagationUtils.getAwarenessChance(50.0, 100.0, 0.0));
        }
    }

    @Nested
    @DisplayName("getAlertLevel 警戒等级")
    class GetAlertLevel {

        @Test
        @DisplayName("近距离应返回 CHARGE")
        void closeRangeShouldReturnCharge() {
            assertEquals(SoundPropagationUtils.AlertLevel.CHARGE, SoundPropagationUtils.getAlertLevel(20.0, 100.0));
            assertEquals(SoundPropagationUtils.AlertLevel.CHARGE, SoundPropagationUtils.getAlertLevel(0.0, 100.0));
            assertEquals(SoundPropagationUtils.AlertLevel.CHARGE, SoundPropagationUtils.getAlertLevel(29.9, 100.0));
        }

        @Test
        @DisplayName("中距离应返回 ALERT")
        void mediumRangeShouldReturnAlert() {
            assertEquals(SoundPropagationUtils.AlertLevel.ALERT, SoundPropagationUtils.getAlertLevel(30.0, 100.0));
            assertEquals(SoundPropagationUtils.AlertLevel.ALERT, SoundPropagationUtils.getAlertLevel(50.0, 100.0));
            assertEquals(SoundPropagationUtils.AlertLevel.ALERT, SoundPropagationUtils.getAlertLevel(59.9, 100.0));
        }

        @Test
        @DisplayName("远距离应返回 SEARCH")
        void farRangeShouldReturnSearch() {
            assertEquals(SoundPropagationUtils.AlertLevel.SEARCH, SoundPropagationUtils.getAlertLevel(60.0, 100.0));
            assertEquals(SoundPropagationUtils.AlertLevel.SEARCH, SoundPropagationUtils.getAlertLevel(90.0, 100.0));
            assertEquals(SoundPropagationUtils.AlertLevel.SEARCH, SoundPropagationUtils.getAlertLevel(100.0, 100.0));
        }

        @Test
        @DisplayName("AlertLevel 枚举应包含正确的响应倍率")
        void alertLevelShouldHaveCorrectMultiplier() {
            assertEquals(1.0, SoundPropagationUtils.AlertLevel.CHARGE.getResponseMultiplier());
            assertEquals(0.6, SoundPropagationUtils.AlertLevel.ALERT.getResponseMultiplier());
            assertEquals(0.3, SoundPropagationUtils.AlertLevel.SEARCH.getResponseMultiplier());
        }
    }
}