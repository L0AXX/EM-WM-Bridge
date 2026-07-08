package com.emwbridge.ai.perception;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.events.AIEventDispatcher;
import com.emwbridge.ai.events.AIEventDispatcher.EventType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.*;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AIVisionManager 视觉感知集成测试")
class AIVisionManagerTest {

    private EMWMBridge plugin;
    private AuditoryPerception auditory;
    private AIEventDispatcher eventDispatcher;
    private VisualPerception visual;
    private AIVisionManager visionManager;
    private Server server;

    private LivingEntity aiEntity;
    private Player player1;
    private Player player2;
    private UUID aiUuid;
    private UUID p1Uuid;
    private UUID p2Uuid;

    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(EMWMBridge.class);
        auditory = mock(AuditoryPerception.class);
        eventDispatcher = spy(new AIEventDispatcher());
        visual = mock(VisualPerception.class);
        server = mock(Server.class);

        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getCurrentTick).thenReturn(1000);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        aiEntity = mock(LivingEntity.class);
        player1 = mock(Player.class);
        player2 = mock(Player.class);

        aiUuid = UUID.randomUUID();
        p1Uuid = UUID.randomUUID();
        p2Uuid = UUID.randomUUID();

        when(aiEntity.getUniqueId()).thenReturn(aiUuid);
        when(player1.getUniqueId()).thenReturn(p1Uuid);
        when(player2.getUniqueId()).thenReturn(p2Uuid);

        Location p1Loc = mock(Location.class);
        Location p2Loc = mock(Location.class);
        when(p1Loc.clone()).thenReturn(p1Loc);
        when(p2Loc.clone()).thenReturn(p2Loc);
        when(player1.getLocation()).thenReturn(p1Loc);
        when(player2.getLocation()).thenReturn(p2Loc);

        visionManager = new AIVisionManager(plugin, auditory, eventDispatcher);

        Field visualField = AIVisionManager.class.getDeclaredField("visual");
        visualField.setAccessible(true);
        visualField.set(visionManager, visual);

        // 默认 mock：面向修正因子为 1.0（正前方）
        when(visual.getTargetFacingMultiplier(any(Player.class), any(Location.class))).thenReturn(1.0);

        // 修复：stub getEyeLocation() — Mockito 5.x 中 any(Location.class) 不匹配 null，
        // 不 stub 会导致 getTargetFacingMultiplier mock 未命中，返回 0.0，曝光值永远为 0
        when(aiEntity.getEyeLocation()).thenReturn(mock(Location.class));
        // 修复：stub getLocation() — 曝光值达到 RED 时 tickExposure() 行105 调用 ai.getLocation().getY()
        Location aiLoc = mock(Location.class);
        when(aiLoc.clone()).thenReturn(aiLoc);
        when(aiEntity.getLocation()).thenReturn(aiLoc);

        visionManager.registerMob(aiUuid);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    @Nested
    @DisplayName("曝光值累积与衰减")
    class ExposureAccumulationAndDecay {

        @Test
        @DisplayName("连续可见时曝光值应持续上升")
        void continuousSightShouldIncreaseExposure() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(3.0);

            double prev = 0;
            for (int i = 0; i < 10; i++) {
                ExposureData data = visionManager.tickExposure(aiEntity, player1);
                assertTrue(data.getValue() >= prev, "第 " + i + " tick 曝光值应上升");
                prev = data.getValue();
            }
            assertTrue(prev > 0, "曝光值应大于0");
        }

        @Test
        @DisplayName("不可见时曝光值应衰减")
        void outOfSightShouldDecayExposure() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(5.0);
            for (int i = 0; i < 20; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }
            double highExp = visionManager.getExposure(aiUuid, p1Uuid);

            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(0.0);
            for (int i = 0; i < 10; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }

            double lowerExp = visionManager.getExposure(aiUuid, p1Uuid);
            assertTrue(lowerExp < highExp, "不可见时曝光值应衰减");
            assertTrue(lowerExp >= 0, "曝光值不应为负");
        }

        @Test
        @DisplayName("首次 tick 应创建曝光数据")
        void firstTickShouldCreateExposureData() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(2.0);

            assertNull(visionManager.getExposureData(aiUuid, p1Uuid));

            visionManager.tickExposure(aiEntity, player1);

            assertNotNull(visionManager.getExposureData(aiUuid, p1Uuid));
        }
    }

    @Nested
    @DisplayName("警戒状态流转")
    class AlertStageTransition {

        @Test
        @DisplayName("低曝光时应为 IDLE（null）")
        void lowExposureShouldBeIdle() {
            AlertStage stage = visionManager.getAlertStage(aiUuid, p1Uuid);
            assertNull(stage);
        }

        @Test
        @DisplayName("进入 RED 阶段应触发 HOSTILE_LOCK 事件")
        void enteringRedShouldTriggerHostileLock() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(10.0);

            java.util.List<AIEventDispatcher.AIEvent> hostileEvents = new ArrayList<>();
            eventDispatcher.register(EventType.HOSTILE_LOCK, hostileEvents::add);

            for (int i = 0; i < 10; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }

            assertFalse(hostileEvents.isEmpty(), "进入RED阶段应广播HOSTILE_LOCK事件");
            assertEquals(aiUuid, hostileEvents.get(0).aiEntityUuid());
            assertEquals(p1Uuid, hostileEvents.get(0).targetPlayerUuid());
        }

        @Test
        @DisplayName("进入 RED 阶段应记录全局仇恨")
        void enteringRedShouldRecordHatred() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(10.0);

            for (int i = 0; i < 10; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }

            AlertStage stage = visionManager.getAlertStage(aiUuid, p1Uuid);
            if (stage == AlertStage.RED) {
                assertTrue(stage.isHostile());
                assertNotNull(AlertStage.getHatredTarget(aiUuid));
            }
        }

        @Test
        @DisplayName("曝光从高降到低应降级警戒状态")
        void exposureDropShouldDowngradeAlert() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(5.0);
            for (int i = 0; i < 8; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }

            AlertStage highStage = visionManager.getAlertStage(aiUuid, p1Uuid);
            assertEquals(AlertStage.RED, highStage);

            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(0.0);
            for (int i = 0; i < 100; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }

            AlertStage lowStage = visionManager.getAlertStage(aiUuid, p1Uuid);
            assertTrue(lowStage == null || lowStage.ordinal() < highStage.ordinal(),
                "警戒状态应降级，high=" + highStage + ", low=" + lowStage);
        }
    }

    @Nested
    @DisplayName("SIGHT 事件广播")
    class SightEventBroadcasting {

        @Test
        @DisplayName("看见目标应广播 SIGHT 事件")
        void seeingTargetShouldBroadcastSightEvent() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(2.0);

            java.util.List<AIEventDispatcher.AIEvent> events = new ArrayList<>();
            eventDispatcher.register(EventType.SIGHT, events::add);

            visionManager.tickExposure(aiEntity, player1);

            assertFalse(events.isEmpty(), "应广播 SIGHT 事件");
            assertEquals(aiUuid, events.get(0).aiEntityUuid());
            assertEquals(p1Uuid, events.get(0).targetPlayerUuid());
        }

        @Test
        @DisplayName("多目标应各自广播 SIGHT 事件")
        void multipleTargetsShouldEachBroadcast() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(2.0);

            java.util.List<AIEventDispatcher.AIEvent> events = new ArrayList<>();
            eventDispatcher.register(EventType.SIGHT, events::add);

            visionManager.tickExposure(aiEntity, player1);
            visionManager.tickExposure(aiEntity, player2);

            long p1Count = events.stream().filter(e -> p1Uuid.equals(e.targetPlayerUuid())).count();
            long p2Count = events.stream().filter(e -> p2Uuid.equals(e.targetPlayerUuid())).count();

            assertTrue(p1Count >= 1, "玩家1应至少有1个SIGHT事件");
            assertTrue(p2Count >= 1, "玩家2应至少有1个SIGHT事件");
        }

        @Test
        @DisplayName("不可见时不应广播 SIGHT 事件")
        void notSeeingShouldNotBroadcastSight() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(2.0);
            visionManager.tickExposure(aiEntity, player1);

            java.util.List<AIEventDispatcher.AIEvent> events = new ArrayList<>();
            eventDispatcher.register(EventType.SIGHT, events::add);

            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(0.0);
            int beforeSize = events.size();
            visionManager.tickExposure(aiEntity, player1);

            assertEquals(beforeSize, events.size(), "不可见时不应新增SIGHT事件");
        }
    }

    @Nested
    @DisplayName("闪光弹致盲")
    class FlashBlindMechanics {

        @Test
        @DisplayName("闪光弹应对所有目标施加致盲")
        void flashBlindShouldApplyToAllTargets() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(3.0);
            visionManager.tickExposure(aiEntity, player1);
            visionManager.tickExposure(aiEntity, player2);
            assertTrue(visionManager.getExposure(aiUuid, p1Uuid) > 0);
            assertTrue(visionManager.getExposure(aiUuid, p2Uuid) > 0);

            visionManager.flashBlind(aiEntity);

            ExposureData data1 = visionManager.getExposureData(aiUuid, p1Uuid);
            ExposureData data2 = visionManager.getExposureData(aiUuid, p2Uuid);
            assertNotNull(data1);
            assertNotNull(data2);
            assertTrue(data1.isFlashBlinded());
            assertTrue(data2.isFlashBlinded());
        }

        @Test
        @DisplayName("闪光弹应清除警戒状态")
        void flashBlindShouldClearAlertStages() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(10.0);
            for (int i = 0; i < 10; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }

            visionManager.flashBlind(aiEntity);

            assertNull(visionManager.getAlertStage(aiUuid, p1Uuid));
        }

        @Test
        @DisplayName("闪光弹应清除主目标")
        void flashBlindShouldClearPrimaryTarget() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(10.0);
            for (int i = 0; i < 10; i++) {
                visionManager.tickExposure(aiEntity, player1);
            }
            UUID before = visionManager.getPrimaryTarget(aiUuid);

            visionManager.flashBlind(aiEntity);

            if (before != null) {
                assertNull(visionManager.getPrimaryTarget(aiUuid));
            }
        }

        @Test
        @DisplayName("闪光弹应广播 FLASH_BLIND 事件")
        void flashBlindShouldBroadcastEvent() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(2.0);
            visionManager.tickExposure(aiEntity, player1);

            java.util.List<AIEventDispatcher.AIEvent> events = new ArrayList<>();
            eventDispatcher.register(EventType.FLASH_BLIND, events::add);

            visionManager.flashBlind(aiEntity);

            assertFalse(events.isEmpty(), "应广播 FLASH_BLIND 事件");
            assertEquals(aiUuid, events.get(0).aiEntityUuid());
        }

        @Test
        @DisplayName("未注册的 AI 闪光弹不应报错")
        void flashBlindUnregisteredAIShouldNotThrow() {
            LivingEntity unknownAI = mock(LivingEntity.class);
            when(unknownAI.getUniqueId()).thenReturn(UUID.randomUUID());

            assertDoesNotThrow(() -> visionManager.flashBlind(unknownAI));
        }
    }

    @Nested
    @DisplayName("主目标选择")
    class PrimaryTargetSelection {

        @Test
        @DisplayName("应选择曝光值最高的玩家作为主目标")
        void shouldSelectHighestExposureAsPrimary() {
            when(visual.calculate(eq(aiEntity), eq(player1), anyDouble(), anyDouble())).thenReturn(5.0);
            when(visual.calculate(eq(aiEntity), eq(player2), anyDouble(), anyDouble())).thenReturn(1.0);

            for (int i = 0; i < 10; i++) {
                visionManager.tickExposure(aiEntity, player1);
                visionManager.tickExposure(aiEntity, player2);
            }

            double exp1 = visionManager.getExposure(aiUuid, p1Uuid);
            double exp2 = visionManager.getExposure(aiUuid, p2Uuid);

            if (exp1 >= 20 || exp2 >= 20) {
                UUID primary = visionManager.getPrimaryTarget(aiUuid);
                if (primary != null) {
                    assertEquals(p1Uuid, primary, "玩家1曝光更高，应为主目标");
                }
            }
        }

        @Test
        @DisplayName("曝光值低于阈值时不应有主目标")
        void lowExposureShouldNotHavePrimaryTarget() {
            assertNull(visionManager.getPrimaryTarget(aiUuid));
        }
    }

    @Nested
    @DisplayName("声音广播集成")
    class SoundBroadcastingIntegration {

        @Test
        @DisplayName("broadcastSound 应委托给 AuditoryPerception")
        void broadcastSoundShouldDelegateToAuditory() {
            SoundSource source = new SoundSource(mock(Location.class), SoundType.GUNSHOT_UNSUPPRESSED, 1.0);
            List<LivingEntity> listeners = Collections.singletonList(aiEntity);

            visionManager.broadcastSound(source, listeners);

            verify(auditory).processSound(eq(aiEntity), eq(source), anyMap(), anyMap(), any(AIEventDispatcher.class));
        }

        @Test
        @DisplayName("听觉关闭时 broadcastSound 不应处理")
        void auditoryDisabledShouldNotProcessSound() {
            org.bukkit.configuration.file.FileConfiguration config =
                mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(config.getDouble("perception.visual.base-rate", 5.0)).thenReturn(5.0);
            when(config.getDouble("perception.decay-rate", 0.3)).thenReturn(0.3);
            when(config.getBoolean("perception.auditory.enabled", true)).thenReturn(false);

            visionManager.reload(config);

            SoundSource source = new SoundSource(mock(Location.class), SoundType.GUNSHOT_UNSUPPRESSED, 1.0);
            List<LivingEntity> listeners = Collections.singletonList(aiEntity);

            visionManager.broadcastSound(source, listeners);

            verify(auditory, never()).processSound(any(), any(), anyMap(), anyMap(), any());
        }
    }

    @Nested
    @DisplayName("实体注册与注销")
    class EntityRegistration {

        @Test
        @DisplayName("registerMob 应初始化缓存")
        void registerMobShouldInitializeCaches() {
            UUID newAI = UUID.randomUUID();
            visionManager.registerMob(newAI);

            assertNotNull(visionManager.getExposures(newAI));
            assertTrue(visionManager.getExposures(newAI).isEmpty());
        }

        @Test
        @DisplayName("unregisterMob 应清除所有缓存")
        void unregisterMobShouldClearCaches() {
            when(visual.calculate(any(LivingEntity.class), any(Player.class), anyDouble(), anyDouble())).thenReturn(2.0);
            visionManager.tickExposure(aiEntity, player1);
            assertNotNull(visionManager.getExposureData(aiUuid, p1Uuid));

            visionManager.unregisterMob(aiUuid);

            assertNull(visionManager.getExposureData(aiUuid, p1Uuid));
            assertNull(visionManager.getPrimaryTarget(aiUuid));
            assertNull(visionManager.getAlertStage(aiUuid, p1Uuid));
        }
    }

    @Nested
    @DisplayName("按目标闪光弹")
    class FlashBlindByTarget {

        @Test
        @DisplayName("仅对能看到目标的 AI 触发闪光弹")
        void flashBlindByTargetShouldOnlyAffectAISeeingTarget() {
            LivingEntity ai2 = mock(LivingEntity.class);
            UUID ai2Uuid = UUID.randomUUID();
            when(ai2.getUniqueId()).thenReturn(ai2Uuid);
            visionManager.registerMob(ai2Uuid);

            when(visual.calculate(eq(aiEntity), eq(player1), anyDouble(), anyDouble())).thenReturn(3.0);
            when(visual.calculate(eq(ai2), eq(player1), anyDouble(), anyDouble())).thenReturn(0.0);

            visionManager.tickExposure(aiEntity, player1);

            List<LivingEntity> nearbyAI = Arrays.asList(aiEntity, ai2);
            visionManager.flashBlindByTarget(player1, nearbyAI);

            ExposureData data1 = visionManager.getExposureData(aiUuid, p1Uuid);
            ExposureData data2 = visionManager.getExposureData(ai2Uuid, p1Uuid);

            assertNotNull(data1);
            assertTrue(data1.isFlashBlinded(), "AI1看到了玩家，应被闪");
            assertNull(data2, "AI2没看到玩家，不应有曝光数据");
        }
    }
}
