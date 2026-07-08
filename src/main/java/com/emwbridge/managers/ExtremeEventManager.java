package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import com.emwbridge.events.TarkovEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExtremeEventManager {

    private final EMWMBridge plugin;
    private final Map<UUID, ExtremeState> states;
    private final Random random;

    // 极限事件概率配置
    private double panicModeChance;
    private double luckShotChance;
    private double malfunctionChance;
    private double tacticalMistakeChance;
    private double adrenalineChance;

    // 肾上腺素触发血量阈值
    private double adrenalineHpThreshold;

    public ExtremeEventManager(EMWMBridge plugin) {
        this.plugin = plugin;
        this.states = new ConcurrentHashMap<>();
        this.random = new Random();
    }

    public void reload() {
        var config = plugin.getConfig();
        this.panicModeChance = config.getDouble("extreme-events.panic-mode-chance", 0.02);
        this.luckShotChance = config.getDouble("extreme-events.luck-shot-chance", 0.05);
        this.malfunctionChance = config.getDouble("extreme-events.malfunction-chance", 0.03);
        this.tacticalMistakeChance = config.getDouble("extreme-events.tactical-mistake-chance", 0.08);
        this.adrenalineChance = config.getDouble("extreme-events.adrenaline-chance", 0.10);
        this.adrenalineHpThreshold = config.getDouble("extreme-events.adrenaline-hp-threshold", 0.25);
    }

    public void shutdown() {
        states.clear();
    }

    /**
     * 检查并触发极限事件
     * @return true 如果触发了事件
     */
    public boolean checkExtremeEvents(LivingEntity entity, Player target, String tier) {
        ExtremeState state = states.computeIfAbsent(entity.getUniqueId(), k -> new ExtremeState());

        // 更新状态
        updateState(entity, state, target);

        // 检查各种事件
        if (checkPanicMode(entity, target, state)) return true;
        if (checkAdrenaline(entity, state)) return true;
        if (checkTacticalMistake(entity, state)) return true;

        // 幸运一击在计算瞄准点时检查
        return false;
    }

    /**
     * 获取幸运一击偏移
     */
    public double getLuckShotBonus() {
        return random.nextDouble() < luckShotChance ? 0.1 : 0;
    }

    /**
     * 获取恐慌模式额外射击概率
     */
    public double getPanicModeBonus() {
        return panicModeChance * 10;
    }

    /**
     * 获取战术失误惩罚
     */
    public double getTacticalMistakePenalty() {
        return tacticalMistakeChance * 5;
    }

    private void updateState(LivingEntity entity, ExtremeState state, Player target) {
        long now = System.currentTimeMillis();

        // P0-4 修复：getLastDamage() 返回的是伤害数值(double)，不是时间戳
        // 正确做法：检测伤害值变化来判定新伤害事件，用单独字段记录时间戳
        double lastDamage = entity.getLastDamage();
        if (lastDamage > 0 && Double.compare(lastDamage, state.lastDamageValue) != 0) {
            state.lastDamageValue = lastDamage;
            state.lastDamageTimestamp = now;
            state.damageCount++;
            state.consecutiveDamage++;

            if (state.consecutiveDamage >= 3) {
                state.panicLevel = Math.min(1.0, state.panicLevel + 0.3);
            }
        } else if (now - state.lastDamageTimestamp > 2000) {
            // 2秒内未受伤 → 重置连续伤害计数
            state.consecutiveDamage = 0;
            state.panicLevel = Math.max(0, state.panicLevel - 0.05);
        }

        // 懒初始化移动时间戳：避免初始 0 导致 now-0>500 恒为 true（误判"移动过多"）
        if (state.lastMoveTime == 0) {
            state.lastMoveTime = now;
        }

        // 基于实际位置变化更新移动状态（仅在能取到位置时生效）
        Location cur = entity.getLocation();
        if (cur != null) {
            if (state.lastLocation == null) {
                state.lastLocation = cur.clone();
            } else if (cur.distanceSquared(state.lastLocation) > 0.04) { // 移动超过 0.2 格
                state.lastMoveTime = now;
                state.lastLocation = cur.clone();
            }
        }

        // 更新移动状态
        if (now - state.lastMoveTime > 500) {
            state.movingTooMuch = true;
        } else {
            state.movingTooMuch = false;
        }

        // 暴露时间
        if (entity.hasLineOfSight(target)) {
            state.exposedTime += 100;
        } else {
            state.exposedTime = 0;
        }
    }

    private boolean checkPanicMode(LivingEntity entity, Player target, ExtremeState state) {
        // 被打多次且血量低时触发恐慌模式
        if (state.damageCount >= 3 && state.panicLevel > 0.3) {
            if (random.nextDouble() < panicModeChance * state.panicLevel) {
                TarkovEvent event = new TarkovEvent(entity, target,
                        TarkovEvent.EventType.PANIC_MODE, state.panicLevel);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    state.inPanicMode = true;
                    state.panicEndTime = System.currentTimeMillis() + 2000 + (long)(random.nextDouble() * 2000);
                    plugin.debug("⚠️ PANIC: " + entity.getName() + " 进入恐慌模式！疯狂扫射！");
                    return true;
                }
            }
        }

        // 恐慌模式结束
        if (state.inPanicMode && System.currentTimeMillis() > state.panicEndTime) {
            state.inPanicMode = false;
            state.panicLevel = 0;
            plugin.debug("✅ PANIC: " + entity.getName() + " 恐慌模式结束");
        }

        return false;
    }

    private boolean checkAdrenaline(LivingEntity entity, ExtremeState state) {
        // P0-9 修复：防止 getMaxHealth() 为零导致除零
        double maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0) return false;
        double hpRatio = entity.getHealth() / maxHealth;

        // 残血时触发肾上腺素
        if (hpRatio < adrenalineHpThreshold && !state.adrenalineActive) {
            if (random.nextDouble() < adrenalineChance) {
                TarkovEvent event = new TarkovEvent(entity, null,
                        TarkovEvent.EventType.ADRENALINE, 1.0 - hpRatio);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    state.adrenalineActive = true;
                    state.adrenalineEndTime = System.currentTimeMillis() + 3000;
                    plugin.debug("💉 ADRENALINE: " + entity.getName() + " 肾上腺素爆发！攻速+50%！");
                    return true;
                }
            }
        }

        // 肾上腺素结束
        if (state.adrenalineActive && System.currentTimeMillis() > state.adrenalineEndTime) {
            state.adrenalineActive = false;
        }

        return false;
    }

    private boolean checkTacticalMistake(LivingEntity entity, ExtremeState state) {
        // 暴露太久或移动太多时可能失误
        if (state.exposedTime > 5000 || state.movingTooMuch) {
            if (random.nextDouble() < tacticalMistakeChance * 0.1) {
                TarkovEvent event = new TarkovEvent(entity, null,
                        TarkovEvent.EventType.TACTICAL_MISTAKE, 0.5);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    state.mistakeCooldown = System.currentTimeMillis() + 3000;
                    plugin.debug("😅 MISTAKE: " + entity.getName() + " 战术失误！移动变慢！");
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isInPanicMode(LivingEntity entity) {
        ExtremeState state = states.get(entity.getUniqueId());
        return state != null && state.inPanicMode;
    }

    public boolean isAdrenalineActive(LivingEntity entity) {
        ExtremeState state = states.get(entity.getUniqueId());
        return state != null && state.adrenalineActive;
    }

    public boolean isSlowedByMistake(LivingEntity entity) {
        ExtremeState state = states.get(entity.getUniqueId());
        return state != null && System.currentTimeMillis() < state.mistakeCooldown;
    }

    public double getSpeedModifier(LivingEntity entity) {
        ExtremeState state = states.get(entity.getUniqueId());
        if (state == null) return 1.0;

        double modifier = 1.0;

        // 肾上腺素加速
        if (state.adrenalineActive) {
            modifier *= 1.3;
        }

        // 恐慌模式速度不变但射击加快
        if (state.inPanicMode) {
            modifier *= 0.8; // 稍微慢一点因为慌张
        }

        // 战术失误减速
        if (System.currentTimeMillis() < state.mistakeCooldown) {
            modifier *= 0.6;
        }

        return modifier;
    }

    public double getFireRateModifier(LivingEntity entity) {
        ExtremeState state = states.get(entity.getUniqueId());
        if (state == null) return 1.0;

        double modifier = 1.0;

        // 肾上腺素攻速+50%
        if (state.adrenalineActive) {
            modifier *= 1.5;
        }

        // 恐慌模式攻速+100%
        if (state.inPanicMode) {
            modifier *= 2.0;
        }

        return modifier;
    }

    public void onEntityDeath(UUID uuid) {
        states.remove(uuid);
    }

    public static class ExtremeState {
        public double lastDamageValue;      // P0-4 修复：存储伤害数值（非时间戳）
        public long lastDamageTimestamp;    // P0-4 修复：上次受伤的时间戳
        public int damageCount;
        public int consecutiveDamage;
        public double panicLevel;
        public boolean inPanicMode;
        public long panicEndTime;
        public boolean adrenalineActive;
        public long adrenalineEndTime;
        public long lastMoveTime;
        public Location lastLocation;
        public boolean movingTooMuch;
        public long exposedTime;
        public long mistakeCooldown;

        public ExtremeState() {
            this.lastDamageValue = 0;
            this.lastDamageTimestamp = 0;
            this.damageCount = 0;
            this.consecutiveDamage = 0;
            this.panicLevel = 0;
            this.inPanicMode = false;
            this.panicEndTime = 0;
            this.adrenalineActive = false;
            this.adrenalineEndTime = 0;
            this.lastMoveTime = 0;
            this.movingTooMuch = false;
            this.exposedTime = 0;
            this.mistakeCooldown = 0;
        }
    }
}
