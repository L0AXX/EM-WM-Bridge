package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AimConvergenceManager {

    private final EMWMBridge plugin;
    private final Map<UUID, AimState> aimStates = new ConcurrentHashMap<>();
    private final Map<String, Double> initialDelays = new HashMap<>();

    private double headshotWindowSeconds = 15.0;
    private double convergenceRate = 0.85;
    private double minSpreadMultiplier = 0.2;
    private double visionLossResetSeconds = 2.0;

    public AimConvergenceManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        headshotWindowSeconds = config.getDouble("aim.headshot-window-seconds", 15.0);
        convergenceRate = config.getDouble("aim.convergence-rate", 0.85);
        minSpreadMultiplier = config.getDouble("aim.min-spread-multiplier", 0.2);
        visionLossResetSeconds = config.getDouble("aim.vision-loss-reset-seconds", 2.0);
        initialDelays.clear();
        for (String tier : new String[]{"scav", "pmc", "cultist", "boss"}) {
            initialDelays.put(tier, config.getDouble("aim.initial-delay." + tier, 1.0));
        }
    }

    public double getInitialDelay(String tier) {
        Double delay = initialDelays.get(tier.toLowerCase());
        if (delay == null) delay = 1.0;
        return delay + (Math.random() - 0.5) * delay;
    }

    public AimResult update(LivingEntity entity, LivingEntity target,
                            boolean hasEyeLOS, boolean hasBodyLOS, double baseSpread) {
        UUID uuid = entity.getUniqueId();
        AimState state = aimStates.computeIfAbsent(uuid, k -> new AimState());
        long now = System.currentTimeMillis();
        boolean canSee = hasEyeLOS || hasBodyLOS;

        if (canSee) {
            if (state.lockStartTime == 0) state.lockStartTime = now;
            state.lastVisionTime = now;
            double lockSeconds = (now - state.lockStartTime) / 1000.0;
            state.currentSpreadMultiplier = Math.max(minSpreadMultiplier,
                    Math.pow(convergenceRate, lockSeconds));
            Location targetPos = target.getLocation();
            if (state.lastEngagePosition != null
                    && state.lastEngagePosition.distance(targetPos) <= 2.0) {
                state.currentSpreadMultiplier *= 0.7;
            }
            state.lastEngagePosition = targetPos.clone();
        } else {
            if (now - state.lastVisionTime > visionLossResetSeconds * 1000) {
                state.currentSpreadMultiplier = 1.0;
                state.lockStartTime = 0;
            }
        }

        double spread = baseSpread * state.currentSpreadMultiplier;
        double lockSeconds = state.lockStartTime > 0
                ? (now - state.lockStartTime) / 1000.0 : 0;
        boolean headshotWindow = lockSeconds < headshotWindowSeconds && hasEyeLOS;

        Location aimPoint;
        if (headshotWindow) {
            aimPoint = target.getEyeLocation();
        } else if (hasBodyLOS) {
            aimPoint = target.getLocation();
        } else if (hasEyeLOS) {
            aimPoint = target.getEyeLocation();
        } else {
            aimPoint = target.getLocation();
            spread = baseSpread * 3.0;
        }

        Location finalPoint = aimPoint.clone().add(
                (Math.random() - 0.5) * spread,
                (Math.random() - 0.5) * spread * 0.6,
                (Math.random() - 0.5) * spread
        );

        return new AimResult(finalPoint, spread);
    }

    public void registerMob(UUID uuid) {
        aimStates.put(uuid, new AimState());
    }

    public void unregisterMob(UUID uuid) {
        aimStates.remove(uuid);
    }

    public static class AimResult {
        public final Location aimPoint;
        public final double spreadRadius;

        public AimResult(Location aimPoint, double spreadRadius) {
            this.aimPoint = aimPoint;
            this.spreadRadius = spreadRadius;
        }
    }

    static class AimState {
        long lockStartTime = 0;
        double currentSpreadMultiplier = 1.0;
        Location lastEngagePosition;
        long lastVisionTime = 0;
    }
}
