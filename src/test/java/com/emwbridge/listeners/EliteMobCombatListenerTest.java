package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P1.4 — EliteMobCombatListener 伤害类型分类单元测试
 *
 * 通过 onEntityDamageByEntity（MONITOR）触发 classifyDamage，
 * 验证写入 emwm_last_damage_type 的元数据值是否正确：
 *   GRENADE（emwm_grenade 盔甲架标签 / 爆炸伤害）
 *   GUN（PROJECTILE）
 *   MELEE（ENTITY_ATTACK / ENTITY_SWEEP_ATTACK）
 *   OTHER（火焰 / 未知）
 */
@DisplayName("EliteMobCombatListener 伤害分类测试")
class EliteMobCombatListenerTest {

    private EMWMBridge plugin;
    private MobWeaponManager weaponManager;
    private EliteMobCombatListener listener;
    private LivingEntity victim;

    @BeforeEach
    void setUp() {
        plugin = mock(EMWMBridge.class);
        weaponManager = mock(MobWeaponManager.class);
        TarkovAIManager aiManager = mock(TarkovAIManager.class);

        when(plugin.getMobWeaponManager()).thenReturn(weaponManager);
        when(plugin.getTarkovAIManager()).thenReturn(aiManager);
        when(plugin.getName()).thenReturn("EM-WM-Bridge");

        listener = new EliteMobCombatListener(plugin);

        victim = mock(LivingEntity.class);
        when(weaponManager.hasWeapon(victim)).thenReturn(true);
    }

    /** 触发 onEntityDamageByEntity 并返回写入的 emwm_last_damage_type */
    private String fireAndGetDamageType(EntityDamageEvent.DamageCause cause, Entity damager) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getCause()).thenReturn(cause);
        when(event.getDamager()).thenReturn(damager);
        // 调试日志会调用 getDamager().getType()
        if (damager instanceof Player p) {
            when(p.getType()).thenReturn(EntityType.PLAYER);
            when(p.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        } else if (damager instanceof Projectile) when(((Projectile) damager).getType()).thenReturn(EntityType.ARROW);
        else if (damager instanceof ArmorStand) when(((ArmorStand) damager).getType()).thenReturn(EntityType.ARMOR_STAND);

        listener.onEntityDamageByEntity(event);

        // 捕获 emwm_last_damage_type 元数据
        org.mockito.ArgumentCaptor<MetadataValue> cap = org.mockito.ArgumentCaptor.forClass(MetadataValue.class);
        verify(victim).setMetadata(eq("emwm_last_damage_type"), cap.capture());
        return cap.getValue().asString();
    }

    @Test
    @DisplayName("emwm_grenade 盔甲架标签 → GRENADE")
    void grenadeArmorStandTag() {
        ArmorStand stand = mock(ArmorStand.class);
        Set<String> tags = new HashSet<>();
        tags.add("emwm_grenade");
        when(stand.getScoreboardTags()).thenReturn(tags);
        assertEquals("GRENADE", fireAndGetDamageType(EntityDamageEvent.DamageCause.CUSTOM, stand));
    }

    @Test
    @DisplayName("ENTITY_EXPLOSION 伤害 → GRENADE")
    void entityExplosion() {
        Entity damager = mock(Entity.class);
        assertEquals("GRENADE", fireAndGetDamageType(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, damager));
    }

    @Test
    @DisplayName("BLOCK_EXPLOSION 伤害 → GRENADE")
    void blockExplosion() {
        Entity damager = mock(Entity.class);
        assertEquals("GRENADE", fireAndGetDamageType(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, damager));
    }

    @Test
    @DisplayName("PROJECTILE 伤害 → GUN")
    void projectileGun() {
        Projectile projectile = mock(Projectile.class);
        assertEquals("GUN", fireAndGetDamageType(EntityDamageEvent.DamageCause.PROJECTILE, projectile));
    }

    @Test
    @DisplayName("ENTITY_ATTACK → MELEE")
    void entityAttackMelee() {
        Player player = mock(Player.class);
        assertEquals("MELEE", fireAndGetDamageType(EntityDamageEvent.DamageCause.ENTITY_ATTACK, player));
    }

    @Test
    @DisplayName("ENTITY_SWEEP_ATTACK → MELEE")
    void sweepAttackMelee() {
        Player player = mock(Player.class);
        assertEquals("MELEE", fireAndGetDamageType(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK, player));
    }

    @Test
    @DisplayName("FIRE 伤害 → OTHER")
    void fireOther() {
        Entity damager = mock(Entity.class);
        assertEquals("OTHER", fireAndGetDamageType(EntityDamageEvent.DamageCause.FIRE, damager));
    }

    @Test
    @DisplayName("未绑定武器的实体应被忽略（不写入元数据）")
    void ignoresNonBoundEntity() {
        LivingEntity nonBound = mock(LivingEntity.class);
        when(weaponManager.hasWeapon(nonBound)).thenReturn(false);

        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(nonBound);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.PROJECTILE);
        when(event.getDamager()).thenReturn(mock(Projectile.class));

        listener.onEntityDamageByEntity(event);

        verify(nonBound, never()).setMetadata(anyString(), any(MetadataValue.class));
    }
}
