package com.emwbridge.utils;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class SoundPropagationUtils {

    private SoundPropagationUtils() {
    }

    public static double calculateSoundRadius(double baseRadius, boolean suppressor) {
        return suppressor ? baseRadius * 0.3 : baseRadius;
    }

    public static double getAwarenessChance(double distance, double soundRadius, double awareness) {
        if (distance > soundRadius) return 0.0;
        double ratio = 1.0 - (distance / soundRadius);
        return ratio * awareness;
    }

    public static AlertLevel getAlertLevel(double distance, double soundRadius) {
        double ratio = distance / soundRadius;
        if (ratio < 0.3) return AlertLevel.CHARGE;
        if (ratio < 0.6) return AlertLevel.ALERT;
        return AlertLevel.SEARCH;
    }

    public static Vector calculateSoundDirection(Player player, org.bukkit.Location soundSource) {
        return soundSource.toVector().subtract(player.getEyeLocation().toVector()).normalize();
    }

    public enum AlertLevel {
        CHARGE(1.0),
        ALERT(0.6),
        SEARCH(0.3);

        private final double responseMultiplier;

        AlertLevel(double responseMultiplier) {
            this.responseMultiplier = responseMultiplier;
        }

        public double getResponseMultiplier() {
            return responseMultiplier;
        }
    }
}
