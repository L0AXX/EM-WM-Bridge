package com.emwbridge.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.bukkit.event.Event;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MobWeaponShootEvent 自定义事件测试")
class MobWeaponShootEventTest {

    private LivingEntity shooter;
    private Player target;
    private UUID shooterUuid;
    private UUID targetUuid;

    @BeforeEach
    void setUp() {
        shooter = mock(LivingEntity.class);
        target = mock(Player.class);
        shooterUuid = UUID.randomUUID();
        targetUuid = UUID.randomUUID();
        when(shooter.getUniqueId()).thenReturn(shooterUuid);
        when(target.getUniqueId()).thenReturn(targetUuid);
    }

    @Nested
    @DisplayName("事件创建与属性")
    class EventCreationAndProperties {

        @Test
        @DisplayName("应正确设置所有属性")
        void shouldSetAllPropertiesCorrectly() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "AK_47", 25.5, true);

            assertSame(shooter, event.getShooter());
            assertSame(target, event.getTarget());
            assertEquals("AK_47", event.getWeaponTitle());
            assertEquals(25.5, event.getDistance());
            assertTrue(event.isAds());
        }

        @Test
        @DisplayName("非 ADS 状态应返回 false")
        void nonAdsShouldReturnFalse() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "M9", 15.0, false);

            assertFalse(event.isAds());
        }

        @Test
        @DisplayName("距离为0应正确处理")
        void zeroDistanceShouldBeHandled() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "Knife", 0.0, false);

            assertEquals(0.0, event.getDistance());
        }

        @Test
        @DisplayName("武器名称为空应保留")
        void emptyWeaponNameShouldBePreserved() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "", 10.0, false);

            assertEquals("", event.getWeaponTitle());
        }

        @Test
        @DisplayName("武器名称为 null 应保留")
        void nullWeaponNameShouldBePreserved() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, null, 10.0, false);

            assertNull(event.getWeaponTitle());
        }
    }

    @Nested
    @DisplayName("HandlerList")
    class HandlerListTest {

        @Test
        @DisplayName("getHandlers 应返回非空 HandlerList")
        void getHandlersShouldReturnHandlerList() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "AK_47", 25.0, true);

            HandlerList handlers = event.getHandlers();
            assertNotNull(handlers);
        }

        @Test
        @DisplayName("静态 getHandlerList 应返回同一实例")
        void staticGetHandlerListShouldReturnSameInstance() {
            HandlerList list1 = MobWeaponShootEvent.getHandlerList();
            HandlerList list2 = MobWeaponShootEvent.getHandlerList();

            assertSame(list1, list2);
        }

        @Test
        @DisplayName("实例 getHandlers 应与静态 getHandlerList 一致")
        void instanceHandlersShouldMatchStatic() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "AK_47", 25.0, true);

            assertSame(event.getHandlers(), MobWeaponShootEvent.getHandlerList());
        }
    }

    @Nested
    @DisplayName("事件类型")
    class EventType {

        @Test
        @DisplayName("事件应继承自 Event")
        void shouldExtendEvent() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "AK_47", 25.0, true);

            assertTrue(event instanceof Event);
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryValues {

        @Test
        @DisplayName("极大距离值应正确处理")
        void veryLargeDistanceShouldBeHandled() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "Sniper", Double.MAX_VALUE, true);

            assertEquals(Double.MAX_VALUE, event.getDistance());
        }

        @Test
        @DisplayName("负距离值应保留")
        void negativeDistanceShouldBePreserved() {
            MobWeaponShootEvent event = new MobWeaponShootEvent(
                shooter, target, "Test", -1.0, false);

            assertEquals(-1.0, event.getDistance());
        }
    }
}
