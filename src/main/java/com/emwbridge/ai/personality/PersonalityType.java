package com.emwbridge.ai.personality;

public enum PersonalityType {
    COWARD(0.1, 1.0, 0.5),
    RECKLESS(0.9, 0.1, 0.15),
    CAUTIOUS(0.4, 0.8, 0.35),
    AMBUSH(0.2, 0.9, 0.3),
    LOOTER(0.3, 0.4, 0.4),
    CAPTAIN(0.6, 0.5, 0.3),
    FLANKER(0.7, 0.4, 0.25),
    SUPPRESSOR(0.5, 0.5, 0.3);

    public final double aggressiveness;
    public final double coverPreference;
    public final double retreatHpThreshold;

    PersonalityType(double aggressiveness, double coverPreference, double retreatHpThreshold) {
        this.aggressiveness = aggressiveness;
        this.coverPreference = coverPreference;
        this.retreatHpThreshold = retreatHpThreshold;
    }

    public double getAttackWeight(double exposureValue) {
        return aggressiveness * (exposureValue / 100.0);
    }

    public double getDefendWeight(double exposureValue) {
        return (1.0 - aggressiveness) * (1.0 - exposureValue / 100.0);
    }

    public double getAmbushWeight(double exposureValue) {
        if (coverPreference > 0.8 && exposureValue < 50) {
            return coverPreference;
        }
        return 0;
    }

    public double getRetreatWeight(double hpRatio) {
        if (hpRatio < retreatHpThreshold) {
            return (retreatHpThreshold - hpRatio) / retreatHpThreshold * 2.0;
        }
        return 0;
    }
}
