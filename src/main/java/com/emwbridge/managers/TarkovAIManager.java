package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.engine.TarkovAIEngine;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Tarkov AI 桥接层 —— 对外暴露原有API，内部委托给 TarkovAIEngine。
 */
public class TarkovAIManager {

    private final EMWMBridge plugin;
    private final TarkovAIEngine engine;

    public TarkovAIManager(EMWMBridge plugin) {
        this.plugin = plugin;
        this.engine = new TarkovAIEngine(
                plugin,
                plugin.getMobWeaponManager(),
                plugin.getExtremeEventManager()
        );
    }

    public void start() {
        engine.start();
    }

    public void stop() {
        engine.stop();
    }

    public void restart() {
        engine.stop();
        engine.start();
    }

    public void registerMob(LivingEntity entity, String tier) {
        engine.registerMob(entity, tier);
    }

    public void unregisterMob(LivingEntity entity) {
        engine.unregisterMob(entity);
    }

    public boolean isActive(LivingEntity entity) {
        return engine.isActive(entity);
    }

    public int getActiveCount() {
        return engine.getActiveCount();
    }

    public TarkovAIEngine getEngine() {
        return engine;
    }

    // ========== 枚举定义 (对外API — SquadRole等引用 Tactic) ==========

    public enum CombatState {
        IDLE, SEARCHING, APPROACHING, ENGAGING, CLOSING_IN,
        TACTICAL_RETREAT, FLEEING
    }

    public enum Tactic {
        BERSERKER,
        SUPPRESSING,
        BARRAGE,
        PRECISE,
        PEEKING,
        STALKING,
        SNIPING
    }

    public enum FireMode {
        SINGLE, BURST, AUTO
    }

    public static class AIState {
        public Player target;
        public UUID lastTarget;
        public CombatState combatState;
        public Tactic currentTactic;
        public String tier;
        public long lastShotTime;
        public long targetFoundTime;
        public int burstCount;
        public Location coverLocation;
        public boolean isPeeking;
        public long peekEndTime;

        public AIState(String tier) {
            this.tier = tier;
            this.combatState = CombatState.IDLE;
            this.currentTactic = Tactic.PRECISE;
            this.lastShotTime = 0;
            this.targetFoundTime = 0;
            this.burstCount = 0;
            this.coverLocation = null;
            this.isPeeking = false;
            this.peekEndTime = 0;
        }
    }

    public static class TierSettings {
        public double fireRateMultiplier;
        public double accuracyMultiplier;
        public long reactionDelayMs;
        public double maxRange;
        public double visionRange;
        public double soundRange;
        public boolean canAds;
        public FireMode fireMode;
        public int burstSize;
        public double reloadSpeedMultiplier;
        public double durabilityMultiplier;
        public double aggressiveness;
        public double tacticalRetreatHp;
    }
}
