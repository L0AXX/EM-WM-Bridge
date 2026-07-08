package com.emwbridge.ai.faction;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 需求1 基础设施：可配置字符串阵营系统（GreyZone）的单元测试。
 * 通过 addFaction 注入阵营定义，验证 getRelation 矩阵与旧枚举回退。
 */
class FactionManagerGreyZoneTest {

    private FactionManager fm;
    private UUID wolfUuid;
    private LivingEntity wolf;

    @BeforeEach
    void setUp() {
        fm = new FactionManager();
        // 白狼：敌对噬体教/变异体/渡鸦；友方 车站镇/铁卫军/先驱者/玩家；中立拾荒者
        FactionProfile whiteWolf = new FactionProfile("white_wolf", "白狼");
        whiteWolf.addHostile("hive_cult");
        whiteWolf.addHostile("mutant");
        whiteWolf.addHostile("raven");
        whiteWolf.addAlly("station_town");
        whiteWolf.addAlly("iron_legion");
        whiteWolf.addAlly("precursor");
        whiteWolf.addAlly("player");
        whiteWolf.addNeutral("scav_clan");

        FactionProfile hiveCult = new FactionProfile("hive_cult", "噬体教");
        hiveCult.addHostile("white_wolf");
        hiveCult.addHostile("player");

        FactionProfile stationTown = new FactionProfile("station_town", "车站镇");
        stationTown.addAlly("white_wolf");

        FactionProfile scavClan = new FactionProfile("scav_clan", "拾荒者部族");
        scavClan.addNeutral("white_wolf");

        fm.addFaction(whiteWolf);
        fm.addFaction(hiveCult);
        fm.addFaction(stationTown);
        fm.addFaction(scavClan);
        fm.addFaction(new FactionProfile("player", "幸存者"));

        wolfUuid = UUID.randomUUID();
        wolf = Mockito.mock(LivingEntity.class);
        when(wolf.getUniqueId()).thenReturn(wolfUuid);
        fm.assignFactionId(wolfUuid, "white_wolf");
    }

    private LivingEntity factionMob(String id) {
        LivingEntity e = Mockito.mock(LivingEntity.class);
        UUID uuid = UUID.randomUUID();
        when(e.getUniqueId()).thenReturn(uuid);
        fm.assignFactionId(uuid, id);
        return e;
    }

    private Player playerTarget() {
        Player p = Mockito.mock(Player.class);
        when(p.hasPermission("emwm.scav")).thenReturn(false);
        return p;
    }

    @Test
    @DisplayName("isConfigured 在 addFaction 后为 true")
    void configuredFlag() {
        assertTrue(fm.isConfigured());
    }

    @Test
    @DisplayName("白狼对玩家(盟友) → FRIENDLY（不杀友方）")
    void whiteWolfVsPlayerFriendly() {
        assertEquals(HostilityMatrix.Relation.FRIENDLY, fm.getRelation(wolf, playerTarget()));
    }

    @Test
    @DisplayName("白狼对噬体教(敌对) → HOSTILE（主动猎杀）")
    void whiteWolfVsHiveCultHostile() {
        assertEquals(HostilityMatrix.Relation.HOSTILE, fm.getRelation(wolf, factionMob("hive_cult")));
    }

    @Test
    @DisplayName("白狼对车站镇(盟友) → FRIENDLY")
    void whiteWolfVsStationTownFriendly() {
        assertEquals(HostilityMatrix.Relation.FRIENDLY, fm.getRelation(wolf, factionMob("station_town")));
    }

    @Test
    @DisplayName("白狼对拾荒者(中立) → NEUTRAL")
    void whiteWolfVsScavNeutral() {
        assertEquals(HostilityMatrix.Relation.NEUTRAL, fm.getRelation(wolf, factionMob("scav_clan")));
    }

    @Test
    @DisplayName("噬体教对玩家 → HOSTILE（敌对阵营主动攻击玩家）")
    void hiveCultVsPlayerHostile() {
        LivingEntity cultist = factionMob("hive_cult");
        assertEquals(HostilityMatrix.Relation.HOSTILE, fm.getRelation(cultist, playerTarget()));
    }

    @Test
    @DisplayName("isHostile：白狼对噬体教=敌对；对车站镇/拾荒者=不敌对")
    void isHostileGating() {
        assertTrue(fm.isHostile(wolf, factionMob("hive_cult")), "白狼应视噬体教为敌对");
        assertFalse(fm.isHostile(wolf, factionMob("station_town")), "白狼不应视友方车站镇为敌对");
        assertFalse(fm.isHostile(wolf, factionMob("scav_clan")), "白狼不应视中立拾荒者为敌对(未被攻击时)");
    }

    @Test
    @DisplayName("未配置(emwm_factions 为空)时回退到内置 Tarkov 枚举")
    void fallbackToEnumWhenNotConfigured() {
        FactionManager legacy = new FactionManager();
        assertFalse(legacy.isConfigured());
        LivingEntity scav = Mockito.mock(LivingEntity.class);
        UUID su = UUID.randomUUID();
        when(scav.getUniqueId()).thenReturn(su);
        legacy.assignByTier(su, "scav"); // AI_SCAV
        Player player = Mockito.mock(Player.class);
        when(player.hasPermission("emwm.scav")).thenReturn(false); // PLAYER_PMC
        // AI_SCAV 对 PLAYER_PMC 在枚举矩阵中为 HOSTILE
        assertEquals(HostilityMatrix.Relation.HOSTILE, legacy.getRelation(scav, player));
    }
}
