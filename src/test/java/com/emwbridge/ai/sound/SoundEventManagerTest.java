package com.emwbridge.ai.sound;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.perception.AIVisionManager;
import com.emwbridge.ai.perception.AuditoryPerception;
import com.emwbridge.ai.perception.SoundSource;
import com.emwbridge.ai.perception.SoundType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("SoundEventManager 声音事件链路集成测试")
class SoundEventManagerTest {

    private EMWMBridge plugin;
    private AIVisionManager aiVisionManager;
    private AuditoryPerception auditoryPerception;
    private SoundEventManager soundEventManager;
    private PluginManager pluginManager;
    private World world;
    private Player player;
    private LivingEntity aiEntity;
    private Server server;
    private MockedStatic<Bukkit> bukkitMock;

    @BeforeEach
    void setUp() {
        plugin = mock(EMWMBridge.class);
        aiVisionManager = mock(AIVisionManager.class);
        auditoryPerception = mock(AuditoryPerception.class);
        pluginManager = mock(PluginManager.class);
        server = mock(Server.class);
        world = mock(World.class);
        player = mock(Player.class);
        aiEntity = mock(LivingEntity.class);

        when(server.getPluginManager()).thenReturn(pluginManager);
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getCurrentTick).thenReturn(1000);
        bukkitMock.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        when(aiVisionManager.getAuditory()).thenReturn(auditoryPerception);
        when(aiVisionManager.isAuditoryEnabled()).thenReturn(true);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        Location playerLoc = mock(Location.class);
        when(playerLoc.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(playerLoc);
        when(player.isSneaking()).thenReturn(false);
        when(player.isSprinting()).thenReturn(false);

        when(aiEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(aiEntity.hasMetadata("emwm_ai_enabled")).thenReturn(true);
        when(aiEntity.getWorld()).thenReturn(world);

        List<LivingEntity> nearbyAI = Collections.singletonList(aiEntity);
        when(world.getEntitiesByClass(eq(LivingEntity.class))).thenReturn(new HashSet<>(nearbyAI));

        soundEventManager = new SoundEventManager(plugin, aiVisionManager);
    }

    @Nested
    @DisplayName("事件注册")
    class EventRegistration {

        @Test
        @DisplayName("registerEvents 应向 PluginManager 注册监听器")
        void registerEventsShouldRegisterListener() {
            soundEventManager.registerEvents();

            verify(pluginManager).registerEvents(any(SoundEventManager.class), any());
        }
    }

    @Nested
    @DisplayName("爆炸事件")
    class ExplosionEvents {

        @Test
        @DisplayName("ExplosionPrimeEvent 应生成 EXPLOSION 声源并广播")
        void explosionShouldBroadcastExplosionSound() {
            org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenReturn(loc);
            when(entity.getWorld()).thenReturn(world);

            ExplosionPrimeEvent event = new ExplosionPrimeEvent(entity, 4.0f, false);

            soundEventManager.onExplosion(event);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());

            SoundSource source = captor.getValue();
            assertEquals(SoundType.EXPLOSION, source.getType());
            assertEquals(1.0, source.getRawLoudness());
        }
    }

    @Nested
    @DisplayName("伤害事件")
    class DamageEvents {

        @Test
        @DisplayName("非玩家实体受伤害应生成 THROWABLE 声源")
        void nonPlayerDamageShouldBroadcastSound() {
            LivingEntity entity = mock(LivingEntity.class);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenReturn(loc);
            when(entity.getWorld()).thenReturn(world);

            EntityDamageEvent event = mock(EntityDamageEvent.class);
            when(event.getEntity()).thenReturn(entity);

            soundEventManager.onDamage(event);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.THROWABLE, captor.getValue().getType());
        }

        @Test
        @DisplayName("玩家受伤害不应触发声音广播")
        void playerDamageShouldNotBroadcast() {
            when(player.getWorld()).thenReturn(world);

            EntityDamageEvent event = mock(EntityDamageEvent.class);
            when(event.getEntity()).thenReturn(player);

            soundEventManager.onDamage(event);

            verify(aiVisionManager, never()).broadcastSound(any(), anyList());
        }
    }

    @Nested
    @DisplayName("方块破坏事件")
    class BlockBreakEvents {

        @Test
        @DisplayName("BlockBreakEvent 应生成 THROWABLE 声源")
        void blockBreakShouldBroadcastSound() {
            Block block = mock(Block.class);
            Location loc = mock(Location.class);
            when(block.getLocation()).thenReturn(loc);
            when(loc.getWorld()).thenReturn(world);
            when(block.getType()).thenReturn(Material.STONE);

            BlockBreakEvent event = new BlockBreakEvent(block, player);

            soundEventManager.onBlockBreak(event);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.THROWABLE, captor.getValue().getType());
            assertEquals(0.4, captor.getValue().getRawLoudness());
        }
    }

    @Nested
    @DisplayName("门交互事件")
    class DoorInteractEvents {

        @Test
        @DisplayName("点击门应生成 DOOR 声源")
        void doorClickShouldBroadcastDoorSound() {
            Block block = mock(Block.class);
            Location loc = mock(Location.class);
            when(block.getLocation()).thenReturn(loc);
            when(loc.getWorld()).thenReturn(world);
            when(block.getType()).thenReturn(Material.OAK_DOOR);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);

            soundEventManager.onPlayerInteract(event);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.DOOR, captor.getValue().getType());
            assertEquals(0.8, captor.getValue().getRawLoudness());
        }

        @Test
        @DisplayName("点击栅栏门应生成 DOOR 声源")
        void gateClickShouldBroadcastDoorSound() {
            Block block = mock(Block.class);
            Location loc = mock(Location.class);
            when(block.getLocation()).thenReturn(loc);
            when(loc.getWorld()).thenReturn(world);
            when(block.getType()).thenReturn(Material.OAK_FENCE_GATE);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);

            soundEventManager.onPlayerInteract(event);

            verify(aiVisionManager).broadcastSound(any(), anyList());
        }

        @Test
        @DisplayName("点击活板门应生成 DOOR 声源")
        void trapdoorClickShouldBroadcastDoorSound() {
            Block block = mock(Block.class);
            Location loc = mock(Location.class);
            when(block.getLocation()).thenReturn(loc);
            when(loc.getWorld()).thenReturn(world);
            when(block.getType()).thenReturn(Material.OAK_TRAPDOOR);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);

            soundEventManager.onPlayerInteract(event);

            verify(aiVisionManager).broadcastSound(any(), anyList());
        }

        @Test
        @DisplayName("点击普通方块不应触发声音")
        void normalBlockClickShouldNotBroadcast() {
            Block block = mock(Block.class);
            Location loc = mock(Location.class);
            when(block.getLocation()).thenReturn(loc);
            when(loc.getWorld()).thenReturn(world);
            when(block.getType()).thenReturn(Material.STONE);

            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(block);

            soundEventManager.onPlayerInteract(event);

            verify(aiVisionManager, never()).broadcastSound(any(), anyList());
        }

        @Test
        @DisplayName("点击空方块不应触发声音")
        void nullClickedBlockShouldNotBroadcast() {
            PlayerInteractEvent event = mock(PlayerInteractEvent.class);
            when(event.getClickedBlock()).thenReturn(null);

            soundEventManager.onPlayerInteract(event);

            verify(aiVisionManager, never()).broadcastSound(any(), anyList());
        }
    }

    @Nested
    @DisplayName("脚步事件")
    class FootstepEvents {

        @Test
        @DisplayName("玩家走动应生成 FOOTSTEP_WALK 声源")
        void walkingShouldBroadcastWalkSound() {
            when(auditoryPerception.canHearFootstep(any(), anyLong())).thenReturn(true);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(loc);
            when(player.isSprinting()).thenReturn(false);
            when(player.isSneaking()).thenReturn(false);

            PlayerMoveEvent event = mock(PlayerMoveEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getFrom()).thenReturn(loc);
            when(event.getTo()).thenReturn(loc);

            soundEventManager.onPlayerMove(event);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.FOOTSTEP_WALK, captor.getValue().getType());
            assertEquals(0.5, captor.getValue().getRawLoudness());
        }

        @Test
        @DisplayName("玩家疾跑应生成 FOOTSTEP_SPRINT 声源")
        void sprintingShouldBroadcastSprintSound() {
            when(auditoryPerception.canHearFootstep(any(), anyLong())).thenReturn(true);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(loc);
            when(player.isSprinting()).thenReturn(true);
            when(player.isSneaking()).thenReturn(false);

            PlayerMoveEvent event = mock(PlayerMoveEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getFrom()).thenReturn(loc);
            when(event.getTo()).thenReturn(loc);

            soundEventManager.onPlayerMove(event);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.FOOTSTEP_SPRINT, captor.getValue().getType());
            assertEquals(0.8, captor.getValue().getRawLoudness());
        }

        @Test
        @DisplayName("玩家潜行应生成 FOOTSTEP_SNEAK 声源")
        void sneakingShouldBroadcastSneakSound() {
            when(auditoryPerception.canHearFootstep(any(), anyLong())).thenReturn(true);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(loc);
            when(player.isSprinting()).thenReturn(false);
            when(player.isSneaking()).thenReturn(true);

            PlayerMoveEvent event = mock(PlayerMoveEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getFrom()).thenReturn(loc);
            when(event.getTo()).thenReturn(loc);

            soundEventManager.onPlayerMove(event);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.FOOTSTEP_SNEAK, captor.getValue().getType());
        }

        @Test
        @DisplayName("脚步冷却内不应重复触发")
        void footstepCooldownShouldPreventDuplicate() {
            when(auditoryPerception.canHearFootstep(any(), anyLong())).thenReturn(false);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(loc);

            PlayerMoveEvent event = mock(PlayerMoveEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getFrom()).thenReturn(loc);
            when(event.getTo()).thenReturn(loc);

            soundEventManager.onPlayerMove(event);

            verify(aiVisionManager, never()).broadcastSound(any(), anyList());
        }
    }

    @Nested
    @DisplayName("外部触发声源")
    class ExternalSoundTriggers {

        @Test
        @DisplayName("普通枪声应生成 UNSUPPRESSED 声源")
        void normalGunshotShouldBeUnsuppressed() {
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);

            soundEventManager.onGunshot(loc, false);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.GUNSHOT_UNSUPPRESSED, captor.getValue().getType());
        }

        @Test
        @DisplayName("消音枪声应生成 SUPPRESSED 声源")
        void suppressedGunshotShouldBeSuppressed() {
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);

            soundEventManager.onGunshot(loc, true);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.GUNSHOT_SUPPRESSED_SUPERSONIC, captor.getValue().getType());
        }

        @Test
        @DisplayName("投掷物落地应生成 THROWABLE 声源")
        void throwableLandShouldBroadcastSound() {
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);

            soundEventManager.onThrowableLand(loc);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.THROWABLE, captor.getValue().getType());
            assertEquals(0.7, captor.getValue().getRawLoudness());
        }

        @Test
        @DisplayName("自定义声源应正确传递参数")
        void customSoundShouldPassParameters() {
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);

            soundEventManager.broadcastCustomSound(loc, SoundType.EXPLOSION, 0.95);

            ArgumentCaptor<SoundSource> captor = ArgumentCaptor.forClass(SoundSource.class);
            verify(aiVisionManager).broadcastSound(captor.capture(), anyList());
            assertEquals(SoundType.EXPLOSION, captor.getValue().getType());
            assertEquals(0.95, captor.getValue().getRawLoudness());
        }
    }

    @Nested
    @DisplayName("广播过滤逻辑")
    class BroadcastFiltering {

        @Test
        @DisplayName("听觉系统关闭时不应广播")
        void auditoryDisabledShouldNotBroadcast() {
            when(aiVisionManager.isAuditoryEnabled()).thenReturn(false);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);

            soundEventManager.onGunshot(loc, false);

            verify(aiVisionManager, never()).broadcastSound(any(), anyList());
        }

        @Test
        @DisplayName("附近无AI实体时不应广播")
        void noNearbyAIShouldNotBroadcast() {
            when(world.getEntitiesByClass(eq(LivingEntity.class))).thenReturn(Collections.emptySet());
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);

            soundEventManager.onGunshot(loc, false);

            verify(aiVisionManager, never()).broadcastSound(any(), anyList());
        }

        @Test
        @DisplayName("世界为 null 时不应抛出异常")
        void nullWorldShouldNotThrow() {
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(null);

            assertDoesNotThrow(() -> soundEventManager.onGunshot(loc, false));
        }

        @Test
        @DisplayName("仅广播给有 emwm_ai_enabled 标记的实体")
        void shouldOnlyBroadcastToEnabledEntities() {
            LivingEntity disabledAI = mock(LivingEntity.class);
            when(disabledAI.getUniqueId()).thenReturn(UUID.randomUUID());
            when(disabledAI.hasMetadata("emwm_ai_enabled")).thenReturn(false);
            when(disabledAI.getWorld()).thenReturn(world);

            Set<LivingEntity> allEntities = new HashSet<>(Arrays.asList(aiEntity, disabledAI));
            when(world.getEntitiesByClass(eq(LivingEntity.class))).thenReturn(allEntities);

            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);

            soundEventManager.onGunshot(loc, false);

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(aiVisionManager).broadcastSound(any(), captor.capture());

            List<LivingEntity> receivers = captor.getValue();
            assertEquals(1, receivers.size());
            assertTrue(receivers.contains(aiEntity));
        }
    }

    @Nested
    @DisplayName("生命周期")
    class Lifecycle {

        @Test
        @DisplayName("shutdown 应清空脚步时间缓存")
        void shutdownShouldClearFootstepCache() {
            when(auditoryPerception.canHearFootstep(any(), anyLong())).thenReturn(true);
            Location loc = mock(Location.class);
            when(loc.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(loc);

            PlayerMoveEvent event = mock(PlayerMoveEvent.class);
            when(event.getPlayer()).thenReturn(player);
            when(event.getFrom()).thenReturn(loc);
            when(event.getTo()).thenReturn(loc);

            soundEventManager.onPlayerMove(event);
            soundEventManager.shutdown();

            assertDoesNotThrow(() -> soundEventManager.shutdown());
        }
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }
}
