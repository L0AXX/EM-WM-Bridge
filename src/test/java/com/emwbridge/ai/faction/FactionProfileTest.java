package com.emwbridge.ai.faction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FactionProfile 动态关系写回单元：setRelationTo 在三类关系间切换，且清空旧归属。
 */
class FactionProfileTest {

    @Test
    @DisplayName("setRelationTo HOSTILE 归入 hostile 且 getRelationTo 返回 HOSTILE")
    void setHostile() {
        FactionProfile p = new FactionProfile("wolf", "白狼");
        p.setRelationTo("raven", HostilityMatrix.Relation.HOSTILE);
        assertEquals(HostilityMatrix.Relation.HOSTILE, p.getRelationTo("raven"));
        assertTrue(p.getHostile().contains("raven"));
        assertFalse(p.getAlly().contains("raven"));
        assertFalse(p.getNeutral().contains("raven"));
    }

    @Test
    @DisplayName("setRelationTo FRIENDLY 归入 ally")
    void setFriendly() {
        FactionProfile p = new FactionProfile("wolf", "白狼");
        p.setRelationTo("raven", HostilityMatrix.Relation.FRIENDLY);
        assertEquals(HostilityMatrix.Relation.FRIENDLY, p.getRelationTo("raven"));
        assertTrue(p.getAlly().contains("raven"));
    }

    @Test
    @DisplayName("setRelationTo NEUTRAL 归入 neutral")
    void setNeutral() {
        FactionProfile p = new FactionProfile("wolf", "白狼");
        p.setRelationTo("raven", HostilityMatrix.Relation.NEUTRAL);
        assertEquals(HostilityMatrix.Relation.NEUTRAL, p.getRelationTo("raven"));
        assertTrue(p.getNeutral().contains("raven"));
    }

    @Test
    @DisplayName("关系切换会清掉旧归属（HOSTILE→FRIENDLY 后不再 hostile）")
    void switchRemovesOld() {
        FactionProfile p = new FactionProfile("wolf", "白狼");
        p.setRelationTo("raven", HostilityMatrix.Relation.HOSTILE);
        assertTrue(p.getHostile().contains("raven"));
        p.setRelationTo("raven", HostilityMatrix.Relation.FRIENDLY);
        assertFalse(p.getHostile().contains("raven"), "切换后应脱离 hostile");
        assertTrue(p.getAlly().contains("raven"));
        assertEquals(HostilityMatrix.Relation.FRIENDLY, p.getRelationTo("raven"));
    }

    @Test
    @DisplayName("setRelationTo(null) 为 no-op；getRelationTo 未知默认 HOSTILE")
    void nullAndDefault() {
        FactionProfile p = new FactionProfile("wolf", "白狼");
        p.setRelationTo(null, HostilityMatrix.Relation.HOSTILE);
        p.setRelationTo("raven", null); // 等价于清除
        assertFalse(p.getHostile().contains("raven"));
        assertFalse(p.getAlly().contains("raven"));
        assertFalse(p.getNeutral().contains("raven"));
        assertEquals(HostilityMatrix.Relation.HOSTILE, p.getRelationTo("unknown"));
    }

    @Test
    @DisplayName("同阵营恒 FRIENDLY，不受 setRelationTo 影响")
    void selfAlwaysFriendly() {
        FactionProfile p = new FactionProfile("wolf", "白狼");
        p.setRelationTo("wolf", HostilityMatrix.Relation.HOSTILE);
        assertEquals(HostilityMatrix.Relation.FRIENDLY, p.getRelationTo("wolf"));
    }
}
