package com.emwbridge.ai.squad;

import com.emwbridge.managers.TarkovAIManager;

public enum SquadRole {
    ASSAULT(3, 10, TarkovAIManager.Tactic.BERSERKER),
    SNIPER(20, 50, TarkovAIManager.Tactic.PRECISE),
    SUPPRESSOR(10, 25, TarkovAIManager.Tactic.BARRAGE),
    FLANKER(5, 15, TarkovAIManager.Tactic.STALKING);

    public final int minDistance;
    public final int maxDistance;
    public final TarkovAIManager.Tactic preferredTactic;

    SquadRole(int minDistance, int maxDistance, TarkovAIManager.Tactic preferredTactic) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.preferredTactic = preferredTactic;
    }

    public boolean isInRange(double distance) {
        return distance >= minDistance && distance <= maxDistance;
    }
}
