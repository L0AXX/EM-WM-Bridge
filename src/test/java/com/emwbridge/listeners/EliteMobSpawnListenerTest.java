package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMConfigCache;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("EliteMobSpawnListener 生物生成链路集成测试")
class EliteMobSpawnListenerTest {

    private EMWMBridge plugin;
    private MobWeaponManager weaponManager;
    private TarkovAIManager aiManager;
    private EMWMConfigCache configCache;
    private EliteMobSpawnListener listener;
    private PluginManager pluginManager;
    private BukkitScheduler scheduler;
    private Server server;
    private World world;
    private FileConfiguration config;

    private Map<String, List<MetadataValue>> metadataMap;

    @BeforeEach
    void setUp() {
        plugin = mock(EMWMBridge.class);
        weaponManager = mock(MobWeaponManager.class);
        aiManager = mock(TarkovAIManager.class);
        configCache = mock(EMWMConfigCache.class);
        pluginManager = mock(PluginManager.class);
        scheduler = mock(BukkitScheduler.class);
        server = mock(Server.class);
        world = mock(World.class);
        metadataMap = new HashMap<>();

        when(plugin.getMobWeaponManager()).thenReturn(weaponManager);
        when(plugin.getTarkovAIManager()).thenReturn(aiManager);
        when(plugin.getEMWMConfigCache()).thenReturn(configCache);
        when(configCache.matchConfigByName(anyString())).thenReturn(null);
        when(configCache.getConfig(anyString())).thenReturn(null);
        when(configCache.hasConfig(anyString())).thenReturn(false);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EMWMBridge"));
        when(plugin.getServer()).thenReturn(server);
        config = mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(config.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPluginManager()).thenReturn(pluginManager);

        when(weaponManager.getRandomWeaponForTier(anyString())).thenAnswer(inv -> {
            String tier = inv.getArgument(0);
            return tier + "_weapon_01";
        });
        when(weaponManager.bindWeapon(any(LivingEntity.class), anyString(), anyDouble())).thenReturn(true);
        when(weaponManager.hasWeapon(any(LivingEntity.class))).thenReturn(true);

        listener = new EliteMobSpawnListener(plugin);
    }

    private Mob createMockMob(EntityType type, String customName) {
        Mob mob = mock(Mob.class);
        UUID uuid = UUID.randomUUID();
        when(mob.getUniqueId()).thenReturn(uuid);
        when(mob.getType()).thenReturn(type);
        when(mob.getCustomName()).thenReturn(customName);
        when(mob.isValid()).thenReturn(true);
        when(mob.isDead()).thenReturn(false);
        when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenReturn(mock(Location.class));
        when(mob.getEyeLocation()).thenReturn(mock(Location.class));

        EntityEquipment equipment = mock(EntityEquipment.class);
        when(mob.getEquipment()).thenReturn(equipment);

        AttributeInstance attackAttr = mock(AttributeInstance.class);
        when(mob.getAttribute(any())).thenReturn(attackAttr);

        return mob;
    }

    @SuppressWarnings("unchecked")
    private void setMetadata(org.bukkit.entity.Entity entity, String key, MetadataValue value) {
        String mapKey = entity.getUniqueId() + "_" + key;
        List<MetadataValue> values = metadataMap.computeIfAbsent(mapKey, k -> new ArrayList<>());
        values.add(value);
        when(entity.hasMetadata(key)).thenReturn(true);
        when(entity.getMetadata(key)).thenReturn(values);
    }

    @Nested
    @DisplayName("Tier 检测逻辑")
    class TierDetection {

        @Test
        @DisplayName("tarkov_tier metadata 应优先匹配")
        void tarkovTierMetadataShouldTakePriority() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "普通僵尸");
            setMetadata(mob, "tarkov_tier", new FixedMetadataValue(plugin, "boss"));

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.NATURAL);

            listener.onCreatureSpawn(event);

            verify(weaponManager).bindWeapon(eq(mob), eq("boss_weapon_01"), anyDouble());
            verify(aiManager).registerMob(eq(mob), eq("boss"));
        }

        @Test
        @DisplayName("包含 boss/legendary 名称应识别为 boss tier")
        void bossNameShouldDetectBossTier() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§cLegendary Boss");
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(weaponManager).bindWeapon(eq(mob), eq("boss_weapon_01"), anyDouble());
        }

        @Test
        @DisplayName("包含 pmc/elite 名称应识别为 pmc tier")
        void pmcNameShouldDetectPmcTier() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§eElite PMC");
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(weaponManager).bindWeapon(eq(mob), eq("pmc_weapon_01"), anyDouble());
        }

        @Test
        @DisplayName("包含 scav/raider 名称应识别为 scav tier")
        void scavNameShouldDetectScavTier() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§7Scav 拾荒者");
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(weaponManager).bindWeapon(eq(mob), eq("scav_weapon_01"), anyDouble());
        }

        @Test
        @DisplayName("仅含 elitemobs metadata 应识别为 scav tier")
        void eliteMobsMetaShouldDetectScavTier() {
            Mob mob = createMockMob(EntityType.ZOMBIE, null);
            setMetadata(mob, "elitemobs", new FixedMetadataValue(plugin, true));

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.NATURAL);

            listener.onCreatureSpawn(event);

            verify(weaponManager).bindWeapon(eq(mob), eq("scav_weapon_01"), anyDouble());
        }

        @Test
        @DisplayName("非 Mob 实体不应绑定武器")
        void nonMobEntityShouldNotBind() {
            LivingEntity entity = mock(LivingEntity.class);
            when(entity.getType()).thenReturn(EntityType.PLAYER);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                entity, CreatureSpawnEvent.SpawnReason.NATURAL);

            listener.onCreatureSpawn(event);

            verify(weaponManager, never()).bindWeapon(any(), anyString(), anyDouble());
            verify(aiManager, never()).registerMob(any(), anyString());
        }

        @Test
        @DisplayName("无匹配 tier 的实体不应绑定武器")
        void noTierMatchShouldNotBind() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "普通僵尸");
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.NATURAL);

            listener.onCreatureSpawn(event);

            verify(weaponManager, never()).bindWeapon(any(), anyString(), anyDouble());
            verify(aiManager, never()).registerMob(any(), anyString());
        }
    }

    @Nested
    @DisplayName("武器绑定链路")
    class WeaponBindingFlow {

        @Test
        @DisplayName("生物生成后应完整执行绑枪+禁用近战+注册AI")
        void spawnShouldCompleteFullBindingFlow() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§7Scav");
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            ArgumentCaptor<String> weaponCaptor = ArgumentCaptor.forClass(String.class);
            verify(weaponManager).bindWeapon(eq(mob), weaponCaptor.capture(), anyDouble());
            assertNotNull(weaponCaptor.getValue());
            assertTrue(weaponCaptor.getValue().contains("scav"));

            verify(aiManager).registerMob(eq(mob), eq("scav"));
            verify(mob).setTarget(null);
        }

        @Test
        @DisplayName("武器绑定失败时应跳过AI注册")
        void failedBindingShouldSkipAIRegister() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§7Scav");
            when(weaponManager.bindWeapon(any(LivingEntity.class), anyString(), anyDouble())).thenReturn(false);

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(aiManager, never()).registerMob(any(), anyString());
        }

        @Test
        @DisplayName("应设置正确的 metadata 标记")
        void shouldSetCorrectMetadata() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§eElite PMC");
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(mob).setMetadata(eq("emwm_tier"), any(FixedMetadataValue.class));
            verify(mob).setMetadata(eq("emwm_combat_state"), any(FixedMetadataValue.class));
            verify(mob).setMetadata(eq("emwm_last_shot"), any(FixedMetadataValue.class));
        }

        @Test
        @DisplayName("禁用近战应清除目标")
        void shouldDisableMeleeAttack() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§7Scav");

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(mob).setTarget(null);
        }
    }

    @Nested
    @DisplayName("延迟绑定机制")
    class DelayedBinding {

        @Test
        @DisplayName("CUSTOM 生成原因应触发延迟检测")
        void customSpawnReasonShouldDelayCheck() {
            Mob mob = createMockMob(EntityType.ZOMBIE, null);

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(5L));
        }

        @Test
        @DisplayName("elitemobs metadata 应直接识别为 scav tier 并触发武器覆盖重检")
        void eliteMobsMetaShouldDetectScavTier() {
            Mob mob = createMockMob(EntityType.ZOMBIE, null);
            setMetadata(mob, "elitemobs", new FixedMetadataValue(plugin, true));

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.NATURAL);

            listener.onCreatureSpawn(event);

            verify(weaponManager).bindWeapon(eq(mob), eq("scav_weapon_01"), anyDouble());
            verify(aiManager).registerMob(eq(mob), eq("scav"));
            verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
        }

        @Test
        @DisplayName("20tick 后应重新检测武器是否被覆盖")
        void shouldRecheckWeaponAfter10Ticks() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§7Scav");

            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(20L));
        }

        @Test
        @DisplayName("实体死亡后延迟任务应跳过")
        void deadEntityShouldSkipDelayedTask() {
            Mob mob = createMockMob(EntityType.ZOMBIE, null);
            when(mob.isDead()).thenReturn(true);

            ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(scheduler).runTaskLater(eq(plugin), taskCaptor.capture(), eq(5L));
            Runnable delayedTask = taskCaptor.getValue();

            when(mob.getCustomName()).thenReturn("§7Scav");
            delayedTask.run();

            verify(weaponManager, never()).bindWeapon(any(), anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("死亡解绑链路")
    class DeathUnbind {

        @Test
        @DisplayName("实体死亡应解绑武器并注销AI")
        void deathShouldUnbindWeaponAndUnregisterAI() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "§7Scav");
            EntityDeathEvent event = mock(EntityDeathEvent.class);
            when(event.getEntity()).thenReturn(mob);

            listener.onEntityDeath(event);

            verify(weaponManager).unbindWeapon(eq(mob));
            verify(aiManager).unregisterMob(eq(mob));
        }

        @Test
        @DisplayName("无武器的实体死亡不应触发解绑")
        void noWeaponDeathShouldNotUnbind() {
            Mob mob = createMockMob(EntityType.ZOMBIE, "普通僵尸");
            when(weaponManager.hasWeapon(any(LivingEntity.class))).thenReturn(false);

            EntityDeathEvent event = mock(EntityDeathEvent.class);
            when(event.getEntity()).thenReturn(mob);

            listener.onEntityDeath(event);

            verify(weaponManager, never()).unbindWeapon(any());
            verify(aiManager, never()).unregisterMob(any());
        }
    }

    @Nested
    @DisplayName("耐久倍率配置")
    class DurabilityMultiplier {

        @Test
        @DisplayName("应从配置读取对应 tier 的耐久倍率")
        void shouldReadDurabilityMultiplierFromConfig() {
            org.bukkit.configuration.file.FileConfiguration config = 
                mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.getDouble(eq("tier-settings.boss.durability-multiplier"), eq(1.0)))
                .thenReturn(2.5);

            Mob mob = createMockMob(EntityType.ZOMBIE, "§cBoss");
            CreatureSpawnEvent event = new CreatureSpawnEvent(
                mob, CreatureSpawnEvent.SpawnReason.CUSTOM);

            listener.onCreatureSpawn(event);

            verify(weaponManager).bindWeapon(eq(mob), anyString(), eq(2.5));
        }
    }
}
