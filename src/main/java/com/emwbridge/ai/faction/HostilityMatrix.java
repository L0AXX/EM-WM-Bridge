package com.emwbridge.ai.faction;

import java.util.EnumMap;
import java.util.Map;

public class HostilityMatrix {

    public enum Relation {
        HOSTILE,
        NEUTRAL,
        FRIENDLY
    }

    private final Map<TarkovFaction, Map<TarkovFaction, Relation>> matrix;

    public HostilityMatrix() {
        matrix = new EnumMap<>(TarkovFaction.class);
        initMatrix();
    }

    private void initMatrix() {
        setAll(TarkovFaction.AI_SCAV,
            Relation.HOSTILE, Relation.NEUTRAL, Relation.FRIENDLY,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE);
        setAll(TarkovFaction.AI_PMC,
            Relation.HOSTILE, Relation.NEUTRAL, Relation.HOSTILE,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE);
        setAll(TarkovFaction.BOSS,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE,
            Relation.HOSTILE, Relation.FRIENDLY, Relation.HOSTILE);
        setAll(TarkovFaction.CULTIST,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE,
            Relation.HOSTILE, Relation.HOSTILE, Relation.FRIENDLY);
    }

    private void setAll(TarkovFaction self, Relation r1, Relation r2, Relation r3,
                        Relation r4, Relation r5, Relation r6) {
        Map<TarkovFaction, Relation> row = new EnumMap<>(TarkovFaction.class);
        row.put(TarkovFaction.PLAYER_PMC, r1);
        row.put(TarkovFaction.PLAYER_SCAV, r2);
        row.put(TarkovFaction.AI_SCAV, r3);
        row.put(TarkovFaction.AI_PMC, r4);
        row.put(TarkovFaction.BOSS, r5);
        row.put(TarkovFaction.CULTIST, r6);
        matrix.put(self, row);
    }

    public Relation getRelation(TarkovFaction self, TarkovFaction target) {
        if (self == target) return Relation.FRIENDLY;
        Map<TarkovFaction, Relation> row = matrix.get(self);
        if (row == null) return Relation.HOSTILE;
        Relation base = row.get(target);
        if ((self == TarkovFaction.AI_SCAV && target == TarkovFaction.AI_PMC)
            || (self == TarkovFaction.AI_PMC && target == TarkovFaction.AI_SCAV)) {
            return Math.random() < 0.8 ? Relation.HOSTILE : Relation.NEUTRAL;
        }
        return base != null ? base : Relation.HOSTILE;
    }
}
