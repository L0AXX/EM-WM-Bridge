package com.emwbridge.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TacticalUtilsTest {

    @Mock(lenient = true)
    private LivingEntity entity;

    @Mock(lenient = true)
    private LivingEntity target;

    @Mock(lenient = true)
    private World world;

    @Mock(lenient = true)
    private Block block;

    @Nested
    @DisplayName("视线检测")
    class LineOfSightTests {

        @Test
        @DisplayName("hasLineOfSight 不同世界应返回 false")
        void hasLineOfSightDifferentWorldShouldReturnFalse() {
            Location from = new Location(world, 0, 0, 0);
            World otherWorld = mock(World.class);
            Location to = new Location(otherWorld, 5, 0, 0);

            assertFalse(TacticalUtils.hasLineOfSight(from, to));
        }

        @Test
        @DisplayName("hasLineOfSight 同一点应返回 true")
        void hasLineOfSightSamePointShouldReturnTrue() {
            Location from = new Location(world, 0, 0, 0);
            Location to = new Location(world, 0, 0, 0);

            assertTrue(TacticalUtils.hasLineOfSight(from, to));
        }

        @Test
        @DisplayName("hasLineOfSight 世界为 null 应返回 false")
        void hasLineOfSightNullWorldShouldReturnFalse() {
            Location from = new Location(null, 0, 0, 0);
            Location to = new Location(null, 5, 0, 0);

            assertFalse(TacticalUtils.hasLineOfSight(from, to));
        }
    }
}
