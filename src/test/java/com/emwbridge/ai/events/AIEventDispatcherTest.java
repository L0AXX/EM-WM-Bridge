package com.emwbridge.ai.events;

import com.emwbridge.ai.events.AIEventDispatcher.AIEvent;
import com.emwbridge.ai.events.AIEventDispatcher.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AIEventDispatcher 事件总线集成测试")
class AIEventDispatcherTest {

    private AIEventDispatcher dispatcher;
    private UUID aiUuid;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        dispatcher = new AIEventDispatcher();
        aiUuid = UUID.randomUUID();
        playerUuid = UUID.randomUUID();
    }

    @Nested
    @DisplayName("事件注册与分发")
    class EventRegistrationAndDispatch {

        @Test
        @DisplayName("注册监听器后应能接收事件")
        void registeredListenerShouldReceiveEvent() {
            java.util.function.Consumer<AIEvent> handler = mock(java.util.function.Consumer.class);
            dispatcher.register(EventType.SIGHT, handler);

            dispatcher.sight(aiUuid, playerUuid, 50.0);

            ArgumentCaptor<AIEvent> captor = ArgumentCaptor.forClass(AIEvent.class);
            verify(handler, times(1)).accept(captor.capture());

            AIEvent event = captor.getValue();
            assertEquals(EventType.SIGHT, event.type());
            assertEquals(aiUuid, event.aiEntityUuid());
            assertEquals(playerUuid, event.targetPlayerUuid());
            assertEquals(50.0, event.value());
        }

        @Test
        @DisplayName("未注册的事件类型不应触发监听器")
        void unregisteredEventTypeShouldNotTrigger() {
            java.util.function.Consumer<AIEvent> handler = mock(java.util.function.Consumer.class);
            dispatcher.register(EventType.SIGHT, handler);

            dispatcher.sound(aiUuid, playerUuid, null, 0.8);

            verify(handler, never()).accept(any());
        }

        @Test
        @DisplayName("多个监听器应全部收到事件")
        void multipleListenersShouldAllReceive() {
            java.util.function.Consumer<AIEvent> handler1 = mock(java.util.function.Consumer.class);
            java.util.function.Consumer<AIEvent> handler2 = mock(java.util.function.Consumer.class);
            java.util.function.Consumer<AIEvent> handler3 = mock(java.util.function.Consumer.class);

            dispatcher.register(EventType.SIGHT, handler1);
            dispatcher.register(EventType.SIGHT, handler2);
            dispatcher.register(EventType.SIGHT, handler3);

            dispatcher.sight(aiUuid, playerUuid, 30.0);

            verify(handler1, times(1)).accept(any());
            verify(handler2, times(1)).accept(any());
            verify(handler3, times(1)).accept(any());
        }

        @Test
        @DisplayName("单个监听器异常不应影响其他监听器")
        void listenerExceptionShouldNotAffectOthers() {
            java.util.function.Consumer<AIEvent> badHandler = event -> {
                throw new RuntimeException("测试异常");
            };
            java.util.function.Consumer<AIEvent> goodHandler = mock(java.util.function.Consumer.class);

            dispatcher.register(EventType.SIGHT, badHandler);
            dispatcher.register(EventType.SIGHT, goodHandler);

            assertDoesNotThrow(() -> dispatcher.sight(aiUuid, playerUuid, 50.0));
            verify(goodHandler, times(1)).accept(any());
        }
    }

    @Nested
    @DisplayName("快捷广播方法")
    class ConvenienceBroadcastMethods {

        @Test
        @DisplayName("sight 应正确广播 SIGHT 事件")
        void sightShouldBroadcastSightEvent() {
            java.util.function.Consumer<AIEvent> handler = mock(java.util.function.Consumer.class);
            dispatcher.register(EventType.SIGHT, handler);

            dispatcher.sight(aiUuid, playerUuid, 75.5);

            ArgumentCaptor<AIEvent> captor = ArgumentCaptor.forClass(AIEvent.class);
            verify(handler).accept(captor.capture());
            assertEquals(EventType.SIGHT, captor.getValue().type());
            assertEquals(75.5, captor.getValue().value());
        }

        @Test
        @DisplayName("sound 应正确广播 SOUND 事件")
        void soundShouldBroadcastSoundEvent() {
            java.util.function.Consumer<AIEvent> handler = mock(java.util.function.Consumer.class);
            dispatcher.register(EventType.SOUND, handler);

            org.bukkit.Location mockLoc = mock(org.bukkit.Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            dispatcher.sound(aiUuid, playerUuid, mockLoc, 0.9);

            ArgumentCaptor<AIEvent> captor = ArgumentCaptor.forClass(AIEvent.class);
            verify(handler).accept(captor.capture());
            assertEquals(EventType.SOUND, captor.getValue().type());
            assertEquals(0.9, captor.getValue().value());
            assertNotNull(captor.getValue().location());
        }

        @Test
        @DisplayName("hostileLock 应正确广播 HOSTILE_LOCK 事件")
        void hostileLockShouldBroadcastHostileLockEvent() {
            java.util.function.Consumer<AIEvent> handler = mock(java.util.function.Consumer.class);
            dispatcher.register(EventType.HOSTILE_LOCK, handler);

            org.bukkit.Location mockLoc = mock(org.bukkit.Location.class);
            when(mockLoc.clone()).thenReturn(mockLoc);
            dispatcher.hostileLock(aiUuid, playerUuid, mockLoc);

            ArgumentCaptor<AIEvent> captor = ArgumentCaptor.forClass(AIEvent.class);
            verify(handler).accept(captor.capture());
            assertEquals(EventType.HOSTILE_LOCK, captor.getValue().type());
            assertEquals(100.0, captor.getValue().value());
            assertEquals(aiUuid, captor.getValue().aiEntityUuid());
            assertEquals(playerUuid, captor.getValue().targetPlayerUuid());
        }

        @Test
        @DisplayName("flashBlind 应正确广播 FLASH_BLIND 事件")
        void flashBlindShouldBroadcastFlashBlindEvent() {
            java.util.function.Consumer<AIEvent> handler = mock(java.util.function.Consumer.class);
            dispatcher.register(EventType.FLASH_BLIND, handler);

            dispatcher.flashBlind(aiUuid);

            ArgumentCaptor<AIEvent> captor = ArgumentCaptor.forClass(AIEvent.class);
            verify(handler).accept(captor.capture());
            assertEquals(EventType.FLASH_BLIND, captor.getValue().type());
            assertEquals(aiUuid, captor.getValue().aiEntityUuid());
            assertNull(captor.getValue().targetPlayerUuid());
        }
    }

    @Nested
    @DisplayName("事件数据完整性")
    class EventDataIntegrity {

        @Test
        @DisplayName("AIEvent 应深拷贝 Location 防止外部修改")
        void eventShouldCloneLocation() {
            org.bukkit.Location originalLoc = mock(org.bukkit.Location.class);
            org.bukkit.Location clonedLoc = mock(org.bukkit.Location.class);
            when(originalLoc.clone()).thenReturn(clonedLoc);

            AIEvent event = new AIEvent(EventType.SOUND, aiUuid, playerUuid, originalLoc, 1.0);

            assertNotSame(originalLoc, event.location());
            assertSame(clonedLoc, event.location());
        }

        @Test
        @DisplayName("Location 为 null 时不应报错")
        void nullLocationShouldNotThrow() {
            assertDoesNotThrow(() ->
                new AIEvent(EventType.SIGHT, aiUuid, playerUuid, null, 0.0)
            );
        }

        @Test
        @DisplayName("事件类型枚举应完整")
        void eventTypeEnumShouldBeComplete() {
            EventType[] types = EventType.values();
            assertEquals(4, types.length);
            assertTrue(java.util.Arrays.asList(types).contains(EventType.SIGHT));
            assertTrue(java.util.Arrays.asList(types).contains(EventType.SOUND));
            assertTrue(java.util.Arrays.asList(types).contains(EventType.HOSTILE_LOCK));
            assertTrue(java.util.Arrays.asList(types).contains(EventType.FLASH_BLIND));
        }
    }

    @Nested
    @DisplayName("事件计数验证")
    class EventCountVerification {

        @Test
        @DisplayName("高频事件应全部送达")
        void highFrequencyEventsShouldAllDeliver() {
            AtomicInteger count = new AtomicInteger(0);
            dispatcher.register(EventType.SIGHT, event -> count.incrementAndGet());

            int total = 100;
            for (int i = 0; i < total; i++) {
                dispatcher.sight(aiUuid, playerUuid, i);
            }

            assertEquals(total, count.get());
        }
    }
}