package com.emwbridge.ai.faction;

public enum TarkovFaction {
    PLAYER_PMC,
    PLAYER_SCAV,
    AI_SCAV,
    AI_PMC,
    BOSS,
    CULTIST;

    public static TarkovFaction fromTier(String tier) {
        if (tier == null) return AI_SCAV;
        return switch (tier.toLowerCase()) {
            case "pmc" -> AI_PMC;
            case "boss" -> BOSS;
            case "cultist" -> CULTIST;
            default -> AI_SCAV;
        };
    }

    public boolean isAI() {
        return this == AI_SCAV || this == AI_PMC || this == BOSS || this == CULTIST;
    }

    public boolean isPlayer() {
        return this == PLAYER_PMC || this == PLAYER_SCAV;
    }

    public boolean isSameFaction(TarkovFaction other) {
        if (this == other) return true;
        if (this == AI_PMC && other == AI_PMC) return true;
        if (this == BOSS && other == BOSS) return true;
        if (this == CULTIST && other == CULTIST) return true;
        return false;
    }
}
