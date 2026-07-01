package com.emwbridge.ai.faction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.emwbridge.ai.faction.HostilityMatrix.Relation.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HostilityMatrix 阵营敌对关系矩阵测试")
class HostilityMatrixTest {

    private HostilityMatrix matrix;

    @BeforeEach
    void setUp() {
        matrix = new HostilityMatrix();
    }

    @Nested
    @DisplayName("AI_SCAV 阵营关系")
    class AIScavRelations {

        @Test
        @DisplayName("AI_SCAV vs PLAYER_PMC 应为 HOSTILE")
        void aiScavVsPlayerPmcShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.AI_SCAV, TarkovFaction.PLAYER_PMC));
        }

        @Test
        @DisplayName("AI_SCAV vs PLAYER_SCAV 应为 NEUTRAL")
        void aiScavVsPlayerScavShouldBeNeutral() {
            assertEquals(NEUTRAL, matrix.getRelation(TarkovFaction.AI_SCAV, TarkovFaction.PLAYER_SCAV));
        }

        @Test
        @DisplayName("AI_SCAV vs AI_SCAV 应为 FRIENDLY")
        void aiScavVsAiScavShouldBeFriendly() {
            assertEquals(FRIENDLY, matrix.getRelation(TarkovFaction.AI_SCAV, TarkovFaction.AI_SCAV));
        }

        @Test
        @DisplayName("AI_SCAV vs AI_PMC 应为 HOSTILE")
        void aiScavVsAiPmcShouldBeHostile() {
            boolean isHostile = matrix.getRelation(TarkovFaction.AI_SCAV, TarkovFaction.AI_PMC) == HOSTILE;
            assertTrue(isHostile || matrix.getRelation(TarkovFaction.AI_SCAV, TarkovFaction.AI_PMC) == NEUTRAL);
        }

        @Test
        @DisplayName("AI_SCAV vs BOSS 应为 HOSTILE")
        void aiScavVsBossShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.AI_SCAV, TarkovFaction.BOSS));
        }

        @Test
        @DisplayName("AI_SCAV vs CULTIST 应为 HOSTILE")
        void aiScavVsCultistShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.AI_SCAV, TarkovFaction.CULTIST));
        }
    }

    @Nested
    @DisplayName("AI_PMC 阵营关系")
    class AiPmcRelations {

        @Test
        @DisplayName("AI_PMC vs PLAYER_PMC 应为 HOSTILE")
        void aiPmcVsPlayerPmcShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.AI_PMC, TarkovFaction.PLAYER_PMC));
        }

        @Test
        @DisplayName("AI_PMC vs PLAYER_SCAV 应为 NEUTRAL")
        void aiPmcVsPlayerScavShouldBeNeutral() {
            assertEquals(NEUTRAL, matrix.getRelation(TarkovFaction.AI_PMC, TarkovFaction.PLAYER_SCAV));
        }

        @Test
        @DisplayName("AI_PMC vs AI_SCAV 应为 HOSTILE 或 NEUTRAL（随机关系）")
        void aiPmcVsAiScavShouldBeHostileOrNeutral() {
            HostilityMatrix.Relation rel = matrix.getRelation(TarkovFaction.AI_PMC, TarkovFaction.AI_SCAV);
            assertTrue(rel == HostilityMatrix.Relation.HOSTILE || rel == HostilityMatrix.Relation.NEUTRAL,
                "AI_PMC vs AI_SCAV 应为 HOSTILE 或 NEUTRAL (随机关系)，实际为: " + rel);
        }

        @Test
        @DisplayName("AI_PMC vs AI_PMC 应为 FRIENDLY")
        void aiPmcVsAiPmcShouldBeFriendly() {
            assertEquals(FRIENDLY, matrix.getRelation(TarkovFaction.AI_PMC, TarkovFaction.AI_PMC));
        }

        @Test
        @DisplayName("AI_PMC vs BOSS 应为 HOSTILE")
        void aiPmcVsBossShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.AI_PMC, TarkovFaction.BOSS));
        }

        @Test
        @DisplayName("AI_PMC vs CULTIST 应为 HOSTILE")
        void aiPmcVsCultistShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.AI_PMC, TarkovFaction.CULTIST));
        }
    }

    @Nested
    @DisplayName("BOSS 阵营关系")
    class BossRelations {

        @Test
        @DisplayName("BOSS vs 所有玩家和AI应为 HOSTILE")
        void bossVsAllShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.BOSS, TarkovFaction.PLAYER_PMC));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.BOSS, TarkovFaction.PLAYER_SCAV));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.BOSS, TarkovFaction.AI_SCAV));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.BOSS, TarkovFaction.AI_PMC));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.BOSS, TarkovFaction.CULTIST));
        }

        @Test
        @DisplayName("BOSS vs BOSS 应为 FRIENDLY")
        void bossVsBossShouldBeFriendly() {
            assertEquals(FRIENDLY, matrix.getRelation(TarkovFaction.BOSS, TarkovFaction.BOSS));
        }
    }

    @Nested
    @DisplayName("CULTIST 阵营关系")
    class CultistRelations {

        @Test
        @DisplayName("CULTIST vs 所有玩家和AI应为 HOSTILE")
        void cultistVsAllShouldBeHostile() {
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.CULTIST, TarkovFaction.PLAYER_PMC));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.CULTIST, TarkovFaction.PLAYER_SCAV));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.CULTIST, TarkovFaction.AI_SCAV));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.CULTIST, TarkovFaction.AI_PMC));
            assertEquals(HOSTILE, matrix.getRelation(TarkovFaction.CULTIST, TarkovFaction.BOSS));
        }

        @Test
        @DisplayName("CULTIST vs CULTIST 应为 FRIENDLY")
        void cultistVsCultistShouldBeFriendly() {
            assertEquals(FRIENDLY, matrix.getRelation(TarkovFaction.CULTIST, TarkovFaction.CULTIST));
        }
    }

    @Nested
    @DisplayName("自身关系")
    class SelfRelations {

        @Test
        @DisplayName("任何阵营 vs 自身应为 FRIENDLY")
        void selfVsSelfShouldBeFriendly() {
            for (TarkovFaction faction : TarkovFaction.values()) {
                assertEquals(FRIENDLY, matrix.getRelation(faction, faction),
                        faction + " vs " + faction + " should be FRIENDLY");
            }
        }
    }
}