package com.emwbridge.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("EMWMKillEvent 击杀事件单元测试")
class EMWMKillEventTest {

    private final LivingEntity victim = mock(LivingEntity.class);
    private final Player killer = mock(Player.class);

    @Test
    @DisplayName("构造器应正确存储击杀字段")
    void constructorShouldStoreFields() {
        EMWMKillEvent event = new EMWMKillEvent(victim, killer,
                EMWMKillEvent.KillMethod.GRENADE, "boss", "AK_47", "combat");

        assertSame(victim, event.getVictim());
        assertSame(killer, event.getKiller());
        assertEquals(EMWMKillEvent.KillMethod.GRENADE, event.getKillMethod());
        assertEquals("boss", event.getTier());
        assertEquals("AK_47", event.getWeaponTitle());
        assertEquals("combat", event.getCombatState());
    }

    @Test
    @DisplayName("isGrenadeKill 应匹配 GRENADE/EXPLOSION")
    void isGrenadeKill() {
        assertTrue(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.GRENADE, "scav", null, "combat").isGrenadeKill());
        assertTrue(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.EXPLOSION, "scav", null, "combat").isGrenadeKill());
        assertFalse(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.GUN, "scav", null, "combat").isGrenadeKill());
        assertFalse(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.MELEE, "scav", null, "combat").isGrenadeKill());
    }

    @Test
    @DisplayName("isGunKill 应匹配 GUN")
    void isGunKill() {
        assertTrue(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.GUN, "scav", null, "combat").isGunKill());
        assertFalse(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.MELEE, "scav", null, "combat").isGunKill());
    }

    @Test
    @DisplayName("isBossKill 应基于 tier 判断")
    void isBossKill() {
        assertTrue(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.GUN, "boss", null, "combat").isBossKill());
        assertTrue(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.GRENADE, "BOSS", null, "combat").isBossKill());
        assertFalse(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.GUN, "scav", null, "combat").isBossKill());
    }

    @Test
    @DisplayName("getHandlerList 应返回非 null")
    void handlerList() {
        assertNotNull(EMWMKillEvent.getHandlerList());
        assertNotNull(new EMWMKillEvent(victim, killer, EMWMKillEvent.KillMethod.OTHER, null, null, null).getHandlers());
    }
}
