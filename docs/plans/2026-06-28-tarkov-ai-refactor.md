# Tarkov AI 重构实施计划

> **For agentic workers:** 按任务顺序逐条实现，每步使用 checkbox (`- [ ]`) 语法追踪进度。

**Goal:** 将单体 `TarkovAIManager` 拆分为 6 个独立 AI 子系统，基于塔科夫 0.16 机制实现双感知、阵营、性格、小队、瞄准收敛、声音事件系统。

**Architecture:** 新建 `com.emwbridge.ai` 包体系，16 个新类按 engine/perception/faction/personality/squad/combat/sound 分层。现有 `TarkovAIManager` 缩减为桥接层委托给 `TarkovAIEngine`。config.yml 新增 5 个配置段。

**Tech Stack:** Java 21, Paper 1.21.4 API, Bukkit metadata, WeaponMechanics events

---

## Phase 1: 搭骨架 — 枚举 + 空壳类

### Task 1.1: 创建 AlertStage 枚举

**Files:**
- Create: `src/main/java/com/emwbridge/ai/perception/AlertStage.java`

- [ ] **Step 1: 写枚举**

```java
package com.emwbridge.ai.perception;

/**
 * AI 警戒三阶段（渐进式仇恨）
 * 对应塔科夫：黄(SUSPICIOUS) → 橙(ALERT) → 红(HOSTILE)
 */
public enum AlertStage {
    /** 未察觉 - 初始状态 */
    IDLE,
    /** 黄 - 听见异响/余光瞥见，原地警戒、转头、不主动开火 */
    SUSPICIOUS,
    /** 橙 - 曝光过半，缓慢向可疑点位推进、找掩体架枪 */
    ALERT,
    /** 红 - 曝光拉满，锁定目标、开火、持续追踪 */
    HOSTILE;

    /**
     * 根据曝光值(0-100)返回对应警戒阶段
     */
    public static AlertStage fromExposure(double exposure) {
        if (exposure >= 80) return HOSTILE;
        if (exposure >= 40) return ALERT;
        if (exposure > 0) return SUSPICIOUS;
        return IDLE;
    }

    /** 是否达到可攻击阶段 */
    public boolean canAttack() {
        return this == HOSTILE;
    }

    /** 是否处于警戒中 */
    public boolean isAlerted() {
        return this == SUSPICIOUS || this == ALERT || this == HOSTILE;
    }
}
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/perception/AlertStage.java
git commit -m "feat(ai): add AlertStage enum (suspicious/alert/hostile)"
```

---

### Task 1.2: 创建 TarkovFaction 枚举

**Files:**
- Create: `src/main/java/com/emwbridge/ai/faction/TarkovFaction.java`

- [ ] **Step 1: 写枚举**

```java
package com.emwbridge.ai.faction;

/**
 * 塔科夫 6 大阵营
 */
public enum TarkovFaction {
    /** 玩家PMC (USEC/BEAR) */
    PLAYER_PMC,
    /** 玩家Scav */
    PLAYER_SCAV,
    /** AI 普通Scav */
    AI_SCAV,
    /** AI PMC */
    AI_PMC,
    /** Boss 男团 */
    BOSS,
    /** 邪教徒/游击队 */
    CULTIST;

    /**
     * 从 tier 字符串映射到 AI 阵营
     */
    public static TarkovFaction fromTier(String tier) {
        if (tier == null) return AI_SCAV;
        return switch (tier.toLowerCase()) {
            case "pmc" -> AI_PMC;
            case "boss" -> BOSS;
            case "cultist" -> CULTIST;
            default -> AI_SCAV;
        };
    }

    /** 是否为 AI 阵营（非玩家） */
    public boolean isAI() {
        return this == AI_SCAV || this == AI_PMC || this == BOSS || this == CULTIST;
    }

    /** 是否为玩家阵营 */
    public boolean isPlayer() {
        return this == PLAYER_PMC || this == PLAYER_SCAV;
    }

    /** 同阵营检查（用于 AI_PMC 跨服友军 / BOSS 同派系） */
    public boolean isSameFaction(TarkovFaction other) {
        if (this == other) return true;
        // AI同一tier视为同派系
        if (this == AI_PMC && other == AI_PMC) return true;
        if (this == BOSS && other == BOSS) return true;
        if (this == CULTIST && other == CULTIST) return true;
        return false;
    }
}
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/faction/TarkovFaction.java
git commit -m "feat(ai): add TarkovFaction enum (6 factions)"
```

---

### Task 1.3: 创建 PersonalityType 枚举

**Files:**
- Create: `src/main/java/com/emwbridge/ai/personality/PersonalityType.java`

- [ ] **Step 1: 写枚举**

```java
package com.emwbridge.ai.personality;

/**
 * AI 8 类性格 — 决定行为决策权重
 */
public enum PersonalityType {
    /** 胆小 - 听见枪声找掩体蹲守，极少推进，HP<50%就逃 */
    COWARD(0.1, 1.0, 0.5),
    /** 鲁莽 - 直线冲锋，不找掩体，近距离火力拉满 */
    RECKLESS(0.9, 0.1, 0.15),
    /** 谨慎 - 交替卡位，频繁探头，优先掩体安全 */
    CAUTIOUS(0.4, 0.8, 0.35),
    /** 伏击型 - 静止卡点，等玩家露身再开火 */
    AMBUSH(0.2, 0.9, 0.3),
    /** 搜刮型 - 交战间隙暂停，容易被偷袭 */
    LOOTER(0.3, 0.4, 0.4),
    /** 队长型 - 报点指挥走位，全队战术核心 */
    CAPTAIN(0.6, 0.5, 0.3),
    /** 绕后型 - 偏好侧翼包抄 */
    FLANKER(0.7, 0.4, 0.25),
    /** 压制型 - 持续开火压制，弹幕消耗 */
    SUPPRESSOR(0.5, 0.5, 0.3);

    /** 激进程度 0-1 */
    public final double aggressiveness;
    /** 掩体偏好 0-1 */
    public final double coverPreference;
    /** 撤退血量阈值 */
    public final double retreatHpThreshold;

    PersonalityType(double aggressiveness, double coverPreference, double retreatHpThreshold) {
        this.aggressiveness = aggressiveness;
        this.coverPreference = coverPreference;
        this.retreatHpThreshold = retreatHpThreshold;
    }

    /**
     * 计算进攻权重
     */
    public double getAttackWeight(double exposureValue) {
        return aggressiveness * (exposureValue / 100.0);
    }

    /**
     * 计算防守权重
     */
    public double getDefendWeight(double exposureValue) {
        return (1.0 - aggressiveness) * (1.0 - exposureValue / 100.0);
    }

    /**
     * 计算伏击权重
     */
    public double getAmbushWeight(double exposureValue) {
        if (coverPreference > 0.8 && exposureValue < 50) {
            return coverPreference;
        }
        return 0;
    }

    /**
     * 计算撤退权重
     */
    public double getRetreatWeight(double hpRatio) {
        if (hpRatio < retreatHpThreshold) {
            return (retreatHpThreshold - hpRatio) / retreatHpThreshold * 2.0;
        }
        return 0;
    }
}
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/personality/PersonalityType.java
git commit -m "feat(ai): add PersonalityType enum (8 personalities)"
```

---

### Task 1.4: 创建 SquadRole 枚举

**Files:**
- Create: `src/main/java/com/emwbridge/ai/squad/SquadRole.java`

- [ ] **Step 1: 写枚举**

```java
package com.emwbridge.ai.squad;

import com.emwbridge.managers.TarkovAIManager.Tactic;

/**
 * 小队分工
 */
public enum SquadRole {
    /** 突击手 - 正面冲锋推进 */
    ASSAULT(3, 10, Tactic.BERSERKER),
    /** 狙击手 - 远距离架枪卡点 */
    SNIPER(20, 50, Tactic.PRECISE),
    /** 火力手 - 火力压制掩护 */
    SUPPRESSOR(10, 25, Tactic.BARRAGE),
    /** 绕后 - 侧翼包抄 */
    FLANKER(5, 15, Tactic.STALKING);

    /** 最小理想距离 */
    public final int minDistance;
    /** 最大理想距离 */
    public final int maxDistance;
    /** 首选战术 */
    public final Tactic preferredTactic;

    SquadRole(int minDistance, int maxDistance, Tactic preferredTactic) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.preferredTactic = preferredTactic;
    }

    /** 检查距离是否在角色理想范围内 */
    public boolean isInRange(double distance) {
        return distance >= minDistance && distance <= maxDistance;
    }
}
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/squad/SquadRole.java
git commit -m "feat(ai): add SquadRole enum (4 roles)"
```

---

### Task 1.5: 批量创建空壳类（15 个）

**Files:**
- Create: `src/main/java/com/emwbridge/ai/engine/TarkovAIEngine.java`
- Create: `src/main/java/com/emwbridge/ai/perception/PerceptionManager.java`
- Create: `src/main/java/com/emwbridge/ai/perception/VisualPerception.java`
- Create: `src/main/java/com/emwbridge/ai/perception/AuditoryPerception.java`
- Create: `src/main/java/com/emwbridge/ai/faction/FactionManager.java`
- Create: `src/main/java/com/emwbridge/ai/faction/HostilityMatrix.java`
- Create: `src/main/java/com/emwbridge/ai/personality/PersonalityManager.java`
- Create: `src/main/java/com/emwbridge/ai/squad/SquadManager.java`
- Create: `src/main/java/com/emwbridge/ai/combat/AimConvergenceManager.java`
- Create: `src/main/java/com/emwbridge/ai/combat/TarkovTactics.java`
- Create: `src/main/java/com/emwbridge/ai/combat/CoverMovement.java`
- Create: `src/main/java/com/emwbridge/ai/sound/SoundEventManager.java`
- Create: `src/main/java/com/emwbridge/ai/AIDecision.java`

- [ ] **Step 1: 创建 TarkovAIEngine 骨架**

```java
package com.emwbridge.ai.engine;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.*;
import com.emwbridge.ai.combat.AimConvergenceManager;
import com.emwbridge.ai.combat.CoverMovement;
import com.emwbridge.ai.combat.TarkovTactics;
import com.emwbridge.ai.faction.FactionManager;
import com.emwbridge.ai.perception.PerceptionManager;
import com.emwbridge.ai.personality.PersonalityManager;
import com.emwbridge.ai.sound.SoundEventManager;
import com.emwbridge.ai.squad.SquadManager;
import com.emwbridge.managers.ExtremeEventManager;
import com.emwbridge.managers.MobWeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TarkovAIEngine {

    private final EMWMBridge plugin;
    private final MobWeaponManager weaponManager;
    private final ExtremeEventManager extremeEventManager;

    private final PerceptionManager perceptionManager;
    private final FactionManager factionManager;
    private final PersonalityManager personalityManager;
    private final SquadManager squadManager;
    private final AimConvergenceManager aimConvergenceManager;
    private final TarkovTactics tactics;
    private final CoverMovement coverMovement;
    private final SoundEventManager soundEventManager;

    private final Map<UUID, AIState> activeMobs = new ConcurrentHashMap<>();
    private Object schedulerTask;
    private int aiTickRate = 4;
    private int tickCounter = 0;

    public TarkovAIEngine(EMWMBridge plugin, MobWeaponManager weaponManager,
                          ExtremeEventManager extremeEventManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.extremeEventManager = extremeEventManager;

        this.perceptionManager = new PerceptionManager(plugin);
        this.factionManager = new FactionManager();
        this.personalityManager = new PersonalityManager(plugin);
        this.squadManager = new SquadManager(plugin);
        this.aimConvergenceManager = new AimConvergenceManager(plugin);
        this.tactics = new TarkovTactics(plugin, weaponManager);
        this.coverMovement = new CoverMovement();
        this.soundEventManager = new SoundEventManager(plugin, perceptionManager);
    }

    public void start() { /* Phase 8 实现 */ }
    public void stop() { /* Phase 8 实现 */ }

    public void registerMob(LivingEntity entity, String tier) {
        activeMobs.put(entity.getUniqueId(), new AIState(tier));
    }

    public void unregisterMob(LivingEntity entity) {
        activeMobs.remove(entity.getUniqueId());
    }

    public boolean isActive(LivingEntity entity) {
        return activeMobs.containsKey(entity.getUniqueId());
    }

    public int getActiveCount() {
        return activeMobs.size();
    }

    // Getters
    public PerceptionManager getPerceptionManager() { return perceptionManager; }
    public FactionManager getFactionManager() { return factionManager; }
    public SquadManager getSquadManager() { return squadManager; }

    public void shutdown() {
        stop();
        activeMobs.clear();
        soundEventManager.shutdown();
    }
}
```

- [ ] **Step 2: 创建 PerceptionManager 骨架**

```java
package com.emwbridge.ai.perception;

import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PerceptionManager {

    private final EMWMBridge plugin;
    private final VisualPerception visual;
    private final AuditoryPerception auditory;

    /** 每个怪物对每个玩家的曝光映射: mobUUID -> (playerUUID -> exposure) */
    private final Map<UUID, Map<UUID, Double>> exposureMap = new ConcurrentHashMap<>();

    /** 每个怪物对每个玩家的警戒阶段 */
    private final Map<UUID, Map<UUID, AlertStage>> alertStageMap = new ConcurrentHashMap<>();

    /** 每个怪物对每个玩家的最后已知位置 */
    private final Map<UUID, Map<UUID, Location>> lastKnownPosition = new ConcurrentHashMap<>();

    /** 每个怪物的当前主目标 */
    private final Map<UUID, UUID> primaryTargetMap = new ConcurrentHashMap<>();

    private double baseRate;
    private boolean auditoryEnabled;

    public PerceptionManager(EMWMBridge plugin) {
        this.plugin = plugin;
        this.visual = new VisualPerception();
        this.auditory = new AuditoryPerception(plugin);
    }

    public void reload() {
        var config = plugin.getConfig();
        this.baseRate = config.getDouble("perception.visual.base-rate", 5.0);
        visual.reload(config);
        this.auditoryEnabled = config.getBoolean("perception.auditory.enabled", true);
        auditory.reload(config);
    }

    // Phase 2 填充
    public void updateExposure(LivingEntity entity, Player target) {}
    public AlertStage getAlertStage(UUID mobUuid, UUID playerUuid) { return AlertStage.IDLE; }
    public double getExposure(UUID mobUuid, UUID playerUuid) { return 0; }
    public Location getLastKnownPosition(UUID mobUuid, UUID playerUuid) { return null; }
    public UUID getPrimaryTarget(UUID mobUuid) { return primaryTargetMap.get(mobUuid); }
    public void registerMob(UUID mobUuid) {
        exposureMap.put(mobUuid, new ConcurrentHashMap<>());
        alertStageMap.put(mobUuid, new ConcurrentHashMap<>());
        lastKnownPosition.put(mobUuid, new ConcurrentHashMap<>());
    }
    public void unregisterMob(UUID mobUuid) {
        exposureMap.remove(mobUuid);
        alertStageMap.remove(mobUuid);
        lastKnownPosition.remove(mobUuid);
        primaryTargetMap.remove(mobUuid);
    }

    /** 听觉事件入口 — SoundEventManager 调此方法分发声音 */
    public void receiveSoundEvent(LivingEntity listener, Location soundOrigin,
                                  double rawExposure, double distance, double maxDistance) {
        if (!auditoryEnabled) return;
        auditory.processSound(listener, soundOrigin, rawExposure, distance, maxDistance, exposureMap, alertStageMap);
    }
}
```

- [ ] **Step 3: 创建 VisualPerception 骨架**

```java
package com.emwbridge.ai.perception;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class VisualPerception {

    private double postureStanding = 1.0;
    private double postureSneaking = 0.6;
    private double motionStill = 0.5;
    private double motionWalking = 0.8;
    private double motionSprinting = 2.0;
    private double angleFront = 1.0;
    private double angleSide = 0.6;
    private double angleBack = 0.3;
    private double envDayClear = 1.0;
    private double envNight = 0.5;
    private double envRain = 0.7;
    private double envThunder = 0.6;
    private double lightMaxLevel = 15.0;
    private double lightMinMultiplier = 0.4;

    public void reload(FileConfiguration config) {
        postureStanding = config.getDouble("perception.visual.posture.standing", 1.0);
        postureSneaking = config.getDouble("perception.visual.posture.sneaking", 0.6);
        motionStill = config.getDouble("perception.visual.motion.still", 0.5);
        motionWalking = config.getDouble("perception.visual.motion.walking", 0.8);
        motionSprinting = config.getDouble("perception.visual.motion.sprinting", 2.0);
        angleFront = config.getDouble("perception.visual.angle.front", 1.0);
        angleSide = config.getDouble("perception.visual.angle.side", 0.6);
        angleBack = config.getDouble("perception.visual.angle.back", 0.3);
        envDayClear = config.getDouble("perception.visual.environment.day-clear", 1.0);
        envNight = config.getDouble("perception.visual.environment.night", 0.5);
        envRain = config.getDouble("perception.visual.environment.rain", 0.7);
        envThunder = config.getDouble("perception.visual.environment.thunder", 0.6);
        lightMaxLevel = config.getDouble("perception.visual.light.max-level", 15.0);
        lightMinMultiplier = config.getDouble("perception.visual.light.min-multiplier", 0.4);
    }

    /**
     * 计算本 tick 的视觉曝光增量
     * @return 曝光增量 (0 = 完全不可见)
     */
    public double calculate(LivingEntity entity, Player target) {
        return 0; // Phase 2 实现
    }
}
```

- [ ] **Step 4: 创建 AuditoryPerception 骨架**

```java
package com.emwbridge.ai.perception;

import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;

public class AuditoryPerception {

    private final EMWMBridge plugin;
    private double headsetMultiplier = 1.4;
    private long earRingingMs = 2000;
    private double sameDirectionBonus = 5.0;
    private boolean processMcEvents = true;

    public AuditoryPerception(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        headsetMultiplier = config.getDouble("perception.auditory.headset-multiplier", 1.4);
        earRingingMs = config.getLong("perception.auditory.ear-ringing-ms", 2000L);
        sameDirectionBonus = config.getDouble("perception.auditory.same-direction-bonus", 5.0);
        processMcEvents = config.getBoolean("perception.auditory.process-mc-events", true);
    }

    /**
     * 处理声音事件
     */
    public void processSound(LivingEntity listener, Location soundOrigin,
                             double rawExposure, double distance, double maxDistance,
                             Map<UUID, Map<UUID, Double>> exposureMap,
                             Map<UUID, Map<UUID, AlertStage>> alertStageMap) {
        // Phase 2 实现
    }

    public boolean isProcessMcEvents() { return processMcEvents; }
}
```

- [ ] **Step 5: 创建 FactionManager 骨架**

```java
package com.emwbridge.ai.faction;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionManager {

    private final Map<UUID, TarkovFaction> entityFactions = new ConcurrentHashMap<>();
    private final HostilityMatrix matrix;

    public FactionManager() {
        this.matrix = new HostilityMatrix();
    }

    public void assignFaction(UUID entityUuid, TarkovFaction faction) {
        entityFactions.put(entityUuid, faction);
    }

    public TarkovFaction getFaction(UUID entityUuid) {
        return entityFactions.getOrDefault(entityUuid, TarkovFaction.AI_SCAV);
    }

    /**
     * 从 tier 字符串自动分配阵营
     */
    public void assignByTier(UUID entityUuid, String tier) {
        entityFactions.put(entityUuid, TarkovFaction.fromTier(tier));
    }

    /**
     * 查询两个实体间的阵营关系
     * @return HOSTILE / NEUTRAL / FRIENDLY
     */
    public HostilityMatrix.Relation getRelation(LivingEntity self, LivingEntity target) {
        TarkovFaction selfFaction = getFaction(self.getUniqueId());
        TarkovFaction targetFaction = resolveTargetFaction(target);
        return matrix.getRelation(selfFaction, targetFaction);
    }

    /**
     * 解析目标阵营 — AI用注册的阵营，玩家默认PMC
     */
    private TarkovFaction resolveTargetFaction(LivingEntity target) {
        if (target instanceof Player) {
            if (target.hasPermission("emwm.scav")) {
                return TarkovFaction.PLAYER_SCAV;
            }
            return TarkovFaction.PLAYER_PMC;
        }
        return getFaction(target.getUniqueId());
    }

    /**
     * 检查 NEUTRAL→HOSTILE 切换条件
     */
    public boolean shouldTurnHostile(LivingEntity self, LivingEntity target) {
        // 距离 < 3m (贴脸)
        if (self.getLocation().distance(target.getLocation()) < 3.0) return true;
        // AI 近期受到该目标伤害
        if (self.getLastDamageCause() != null
            && self.getLastDamageCause().getEntity() == target) return true;
        return false;
    }

    public void removeEntity(UUID uuid) {
        entityFactions.remove(uuid);
    }
}
```

- [ ] **Step 6: 创建 HostilityMatrix 骨架**

```java
package com.emwbridge.ai.faction;

import java.util.EnumMap;
import java.util.Map;

/**
 * 塔科夫阵营仇恨矩阵
 */
public class HostilityMatrix {

    public enum Relation {
        /** 敌对 — 可见即打 */
        HOSTILE,
        /** 中立 — 不主动攻击，可被触发转为敌对 */
        NEUTRAL,
        /** 友军 — 绝不互相攻击 */
        FRIENDLY
    }

    private final Map<TarkovFaction, Map<TarkovFaction, Relation>> matrix;

    public HostilityMatrix() {
        matrix = new EnumMap<>(TarkovFaction.class);
        initMatrix();
    }

    private void initMatrix() {
        // AI_SCAV 对其他阵营的态度
        setAll(TarkovFaction.AI_SCAV,
            Relation.HOSTILE, // PLAYER_PMC
            Relation.NEUTRAL, // PLAYER_SCAV
            Relation.FRIENDLY, // AI_SCAV (自己)
            Relation.HOSTILE, // AI_PMC (80%概率在getRelation中处理)
            Relation.HOSTILE, // BOSS
            Relation.HOSTILE  // CULTIST
        );

        // AI_PMC 对其他阵营的态度
        setAll(TarkovFaction.AI_PMC,
            Relation.HOSTILE,  // PLAYER_PMC
            Relation.NEUTRAL,  // PLAYER_SCAV
            Relation.HOSTILE,  // AI_SCAV (80%概率)
            Relation.FRIENDLY, // AI_PMC
            Relation.HOSTILE,  // BOSS
            Relation.HOSTILE   // CULTIST
        );

        // BOSS 对所有人敌对（同派系在getRelation中处理）
        setAll(TarkovFaction.BOSS,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE,
            Relation.HOSTILE, Relation.FRIENDLY, Relation.HOSTILE
        );

        // CULTIST 对所有人敌对
        setAll(TarkovFaction.CULTIST,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE,
            Relation.HOSTILE, Relation.HOSTILE, Relation.FRIENDLY
        );
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

    /**
     * 获取两个阵营的基础关系
     */
    public Relation getRelation(TarkovFaction self, TarkovFaction target) {
        if (self == target) return Relation.FRIENDLY;

        Map<TarkovFaction, Relation> row = matrix.get(self);
        if (row == null) return Relation.HOSTILE;

        Relation base = row.get(target);

        // AI_SCAV vs AI_PMC & AI_PMC vs AI_SCAV: 80%概率敌对
        if ((self == TarkovFaction.AI_SCAV && target == TarkovFaction.AI_PMC)
            || (self == TarkovFaction.AI_PMC && target == TarkovFaction.AI_SCAV)) {
            return Math.random() < 0.8 ? Relation.HOSTILE : Relation.NEUTRAL;
        }

        return base != null ? base : Relation.HOSTILE;
    }
}
```

- [ ] **Step 7: 创建 PersonalityManager 骨架**

```java
package com.emwbridge.ai.personality;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.AIDecision;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalityManager {

    private final EMWMBridge plugin;
    private final Random random = new Random();
    private final Map<UUID, PersonalityType> entityPersonalities = new ConcurrentHashMap<>();

    public PersonalityManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        // Phase 4: 从 config 读取 tier-weights
    }

    /**
     * 根据 tier 权重随机分配性格
     */
    public PersonalityType rollByTier(String tier) {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (PersonalityType type : PersonalityType.values()) {
            double weight = getWeight(tier, type);
            cumulative += weight;
            if (roll < cumulative) return type;
        }
        return PersonalityType.CAUTIOUS;
    }

    private double getWeight(String tier, PersonalityType type) {
        // Phase 4: 从 config 读取
        return switch (tier.toLowerCase()) {
            case "scav" -> switch (type) {
                case COWARD -> 0.35; case CAUTIOUS -> 0.30;
                case LOOTER -> 0.25; case RECKLESS -> 0.10;
                default -> 0;
            };
            case "pmc" -> switch (type) {
                case CAUTIOUS -> 0.30; case FLANKER -> 0.20;
                case CAPTAIN -> 0.15; case SUPPRESSOR -> 0.15;
                case AMBUSH -> 0.10; case RECKLESS -> 0.10;
                default -> 0;
            };
            case "boss" -> switch (type) {
                case CAPTAIN -> 0.40; case RECKLESS -> 0.25;
                case SUPPRESSOR -> 0.20; case FLANKER -> 0.15;
                default -> 0;
            };
            default -> 0;
        };
    }

    public void assignPersonality(UUID entityUuid, PersonalityType personality) {
        entityPersonalities.put(entityUuid, personality);
    }

    public PersonalityType getPersonality(UUID entityUuid) {
        return entityPersonalities.getOrDefault(entityUuid, PersonalityType.CAUTIOUS);
    }

    public void removeEntity(UUID uuid) {
        entityPersonalities.remove(uuid);
    }

    /**
     * 根据性格+状态计算行为决策权重
     */
    @SuppressWarnings("unused")
    public AIDecision decide(UUID entityUuid, double hpRatio, double exposureValue) {
        return AIDecision.ENGAGE; // Phase 4 实现
    }
}
```

- [ ] **Step 8: 创建 AIDecision 枚举**

```java
package com.emwbridge.ai;

/**
 * AI 行为决策结果
 */
public enum AIDecision {
    /** 进攻 — 主动接敌 */
    ENGAGE,
    /** 防守 — 找掩体、探头 */
    DEFEND,
    /** 伏击 — 静止卡点等待 */
    AMBUSH,
    /** 撤退 — 战术撤退/逃跑 */
    RETREAT
}
```

- [ ] **Step 9: 创建 SquadManager 骨架**

```java
package com.emwbridge.ai.squad;

import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

public class SquadManager {

    private final EMWMBridge plugin;
    private final Map<UUID, UUID> entitySquadMap = new HashMap<>(); // entity -> squadId
    private final Map<UUID, Squad> squads = new HashMap<>();

    public SquadManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 尝试加入附近同阵营小队，若无则创建新小队
     */
    public void tryJoin(LivingEntity entity, String tier) {
        // Phase 5 实现
    }

    /**
     * 共享情报 — 成员发现目标时全队同步
     */
    public void shareIntel(UUID discovererUuid, Player target) {
        // Phase 5 实现
    }

    public SquadRole getRole(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return null;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.getRole(entityUuid) : null;
    }

    public boolean isCaptain(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return false;
        Squad squad = squads.get(squadId);
        return squad != null && entityUuid.equals(squad.captainUuid);
    }

    public void removeEntity(UUID uuid) {
        UUID squadId = entitySquadMap.remove(uuid);
        if (squadId != null) {
            Squad squad = squads.get(squadId);
            if (squad != null) {
                squad.removeMember(uuid);
                if (squad.memberUuids.isEmpty()) {
                    squads.remove(squadId);
                }
            }
        }
    }

    /** 获取小队成员数量 */
    public int getSquadSize(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return 1;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.memberUuids.size() : 1;
    }

    private static class Squad {
        UUID captainUuid;
        final List<UUID> memberUuids = new ArrayList<>();
        final Map<UUID, SquadRole> roleAssignments = new HashMap<>();
        Player sharedTarget;
        Location sharedTargetLocation;

        SquadRole getRole(UUID uuid) {
            return roleAssignments.get(uuid);
        }

        void removeMember(UUID uuid) {
            memberUuids.remove(uuid);
            roleAssignments.remove(uuid);
        }
    }
}
```

- [ ] **Step 10: 创建 AimConvergenceManager 骨架**

```java
package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AimConvergenceManager {

    private final EMWMBridge plugin;

    /** 每个怪物的瞄准状态 */
    private final Map<UUID, AimState> aimStates = new ConcurrentHashMap<>();

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
    }

    /**
     * 获得初始瞄准延迟（秒）
     */
    public double getInitialDelay(String tier) {
        // Phase 6 从 config 读
        double delay = switch (tier.toLowerCase()) {
            case "scav" -> 1.0;
            case "pmc" -> 0.8;
            case "cultist" -> 0.5;
            case "boss" -> 0.3;
            default -> 1.0;
        };
        return delay + (Math.random() - 0.5) * delay; // ±50% 随机化
    }

    /**
     * 更新瞄准状态，返回瞄准点和散布半径
     * @param entity AI 实体
     * @param target 目标玩家
     * @param hasEyeLOS 是否有视线到 eyeLocation
     * @param hasBodyLOS 是否有视线到 bodyCenter
     * @param baseSpread 武器基础散布
     * @return AimResult 包含瞄准点和实际散布
     */
    @SuppressWarnings("unused")
    public AimResult update(LivingEntity entity, LivingEntity target,
                            boolean hasEyeLOS, boolean hasBodyLOS, double baseSpread) {
        return new AimResult(target.getLocation(), baseSpread); // Phase 6 实现
    }

    public void registerMob(UUID uuid) {
        aimStates.put(uuid, new AimState());
    }

    public void unregisterMob(UUID uuid) {
        aimStates.remove(uuid);
    }

    /**
     * 瞄准结果
     */
    public static class AimResult {
        public final Location aimPoint;
        public final double spreadRadius;

        public AimResult(Location aimPoint, double spreadRadius) {
            this.aimPoint = aimPoint;
            this.spreadRadius = spreadRadius;
        }
    }

    /**
     * AI 瞄准状态
     */
    static class AimState {
        long lockStartTime = 0;
        double currentSpreadMultiplier = 1.0;
        Location lastEngagePosition;
        long lastVisionTime = 0;
    }
}
```

- [ ] **Step 11: 创建 TarkovTactics 骨架**

```java
package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.AIDecision;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager.Tactic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TarkovTactics {

    private final EMWMBridge plugin;
    private final MobWeaponManager weaponManager;
    private final Map<UUID, TacticalState> states = new ConcurrentHashMap<>();

    private boolean standAndShoot = true;
    private double hipfireRange = 15.0;
    private long burstDelayMs = 500;
    private double repositionBetweenBursts = 0.35;

    public TarkovTactics(EMWMBridge plugin, MobWeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
    }

    public void reload(FileConfiguration config) {
        standAndShoot = config.getBoolean("tactical.stand-and-shoot", true);
        hipfireRange = config.getDouble("tactical.hipfire-range", 15.0);
        burstDelayMs = config.getLong("tactical.burst-delay-ms", 500L);
        repositionBetweenBursts = config.getDouble("tactical.reposition-between-bursts", 0.35);
    }

    /**
     * 根据决策执行战术行为
     * @return true 如果执行了射击
     */
    @SuppressWarnings("unused")
    public boolean execute(LivingEntity entity, Player target, AIDecision decision,
                           Tactic tactic, double distance, String tier,
                           AimConvergenceManager.AimResult aimResult,
                           CoverMovement coverMovement) {
        return false; // Phase 8 从 TarkovAIManager 迁移
    }

    public void registerMob(UUID uuid) {
        states.put(uuid, new TacticalState());
    }

    public void unregisterMob(UUID uuid) {
        states.remove(uuid);
    }

    public TacticalState getState(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new TacticalState());
    }

    public static class TacticalState {
        public long lastShotTime;
        public int burstCount;
    }
}
```

- [ ] **Step 12: 创建 CoverMovement 骨架**

```java
package com.emwbridge.ai.combat;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class CoverMovement {

    private boolean restrictMovement = true;

    public void reload(boolean restrictMovement) {
        this.restrictMovement = restrictMovement;
    }

    /**
     * 站立射击：停止移动并面朝目标
     */
    public void standAndAim(LivingEntity entity, Player target) {
        // Phase 8 从 TarkovAIManager 迁移 faceTarget + stopMoving
    }

    /**
     * Burst 后走位 — 掩体后横移 / 无掩体枪线方向
     */
    public void repositionAfterBurst(LivingEntity entity, Player target) {
        // Phase 8 从 TarkovAIManager 迁移 isBehindCover + repositionAfterBurst
    }

    /**
     * 面朝目标
     */
    public void faceTarget(LivingEntity entity, Player target) {
        Location loc = entity.getLocation();
        Location targetLoc = target.getLocation();
        double dx = targetLoc.getX() - loc.getX();
        double dz = targetLoc.getZ() - loc.getZ();
        double dy = target.getEyeLocation().getY() - loc.getY();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        loc.setYaw(yaw);
        loc.setPitch(pitch);
    }

    /**
     * 停止移动
     */
    public void stopMoving(LivingEntity entity) {
        if (entity instanceof org.bukkit.entity.Mob mob) {
            try { mob.getPathfinder().stopPathfinding(); }
            catch (Exception ignored) {}
        }
        entity.setVelocity(entity.getVelocity().setY(0).multiply(0));
    }

    /**
     * 检测是否在掩体后
     */
    public boolean isBehindCover(LivingEntity entity, Player target) {
        Location loc = entity.getLocation();
        Vector toTarget = target.getLocation().toVector()
                .subtract(loc.toVector()).normalize();
        Vector perpRight = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();

        for (double side : new double[]{1.5, -1.5}) {
            Location check = loc.clone().add(perpRight.clone().multiply(side));
            check.setY(loc.getY() + 1);
            if (check.getBlock().getType().isSolid()) return true;
            check.setY(loc.getY() + 2);
            if (check.getBlock().getType().isSolid()) return true;
        }

        Vector behind = toTarget.clone().multiply(-1).normalize();
        Location checkBehind = loc.clone().add(behind.multiply(1));
        checkBehind.setY(loc.getY() + 1);
        if (checkBehind.getBlock().getType().isSolid()) return true;

        return !entity.hasLineOfSight(target);
    }

    /**
     * 横移（掩体后切换射击角度）
     */
    public void strafe(LivingEntity entity) {
        Location loc = entity.getLocation();
        double angle = Math.random() < 0.5 ? 90 : -90;
        Vector strafeDir = new Vector(
                Math.cos(Math.toRadians(loc.getYaw() + angle)),
                0,
                Math.sin(Math.toRadians(loc.getYaw() + angle))
        ).normalize();
        entity.setVelocity(entity.getVelocity().setY(0).add(strafeDir.multiply(0.15)));
    }

    /**
     * 枪线方向移动
     */
    public void moveAlongLineOfFire(LivingEntity entity, Player target, double aggressiveness, double maxRange) {
        if (!restrictMovement) return;
        double dist = entity.getLocation().distance(target.getLocation());
        boolean advance = Math.random() < aggressiveness;
        Vector lineOfFire = target.getLocation().toVector()
                .subtract(entity.getLocation().toVector()).normalize();
        if (advance && dist > 5) {
            entity.setVelocity(entity.getVelocity().setY(0)
                    .add(lineOfFire.clone().multiply(0.12)));
        } else if (!advance && dist < maxRange * 1.2) {
            entity.setVelocity(entity.getVelocity().setY(0)
                    .add(lineOfFire.clone().multiply(-0.1)));
        }
    }

    /**
     * 向目标移动
     */
    public void moveTowards(LivingEntity entity, Location location) {
        if (entity instanceof org.bukkit.entity.Mob mob) {
            try { mob.getPathfinder().moveTo(location); return; }
            catch (Exception ignored) {}
        }
        Vector dir = location.toVector()
                .subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(entity.getVelocity().setY(0).add(dir.multiply(0.15)));
    }
}
```

- [ ] **Step 13: 创建 SoundEventManager 骨架**

```java
package com.emwbridge.ai.sound;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.perception.PerceptionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

/**
 * 声音事件管理器 — 监听 MC 事件，衰减后分发给范围内的 AI
 */
public class SoundEventManager implements Listener {

    private final EMWMBridge plugin;
    private final PerceptionManager perceptionManager;

    /** 声音事件分级配置 */
    private static final Map<Class<?>, SoundProfile> SOUND_PROFILES = new HashMap<>();

    static {
        SOUND_PROFILES.put(ExplosionPrimeEvent.class, new SoundProfile(80, 40));
        SOUND_PROFILES.put(EntityDamageEvent.class, new SoundProfile(30, 12));
        SOUND_PROFILES.put(BlockBreakEvent.class, new SoundProfile(25, 10));
        // PlayerInteractEvent 用于开门/翻箱
        SOUND_PROFILES.put(PlayerInteractEvent.class, new SoundProfile(20, 10));
    }

    /** 脚步声防刷屏 — 每玩家每秒最多一次 */
    private final Map<UUID, Long> lastFootstepTime = new HashMap<>();

    public SoundEventManager(EMWMBridge plugin, PerceptionManager perceptionManager) {
        this.plugin = plugin;
        this.perceptionManager = perceptionManager;
    }

    public void registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onExplosion(ExplosionPrimeEvent event) {
        broadcastSound(event.getEntity().getLocation(), SOUND_PROFILES.get(ExplosionPrimeEvent.class));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        broadcastSound(event.getEntity().getLocation(), SOUND_PROFILES.get(EntityDamageEvent.class));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        broadcastSound(event.getBlock().getLocation(), SOUND_PROFILES.get(BlockBreakEvent.class));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            broadcastSound(event.getClickedBlock().getLocation(), SOUND_PROFILES.get(PlayerInteractEvent.class));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 脚步声 — 低频率采样，每秒最多一次
        if (event.getPlayer().isSneaking()) return; // 静步无声
        UUID puid = event.getPlayer().getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastFootstepTime.get(puid);
        if (last != null && now - last < 1000) return;
        lastFootstepTime.put(puid, now);

        double maxDist = event.getPlayer().isSprinting() ? 50 : 20;
        double exposure = event.getPlayer().isSprinting() ? 15 : 5;
        SoundProfile profile = new SoundProfile(maxDist, exposure);
        broadcastSound(event.getPlayer().getLocation(), profile);
    }

    /**
     * 枪声事件 — WM WeaponShootEvent 外部调用
     */
    public void onGunshot(Location source, boolean suppressed) {
        SoundProfile profile = suppressed
            ? new SoundProfile(60, 20)   // 消音
            : new SoundProfile(150, 30); // 无消音
        broadcastSound(source, profile);
    }

    /**
     * 向范围内所有 AI 实体分发声音
     */
    private void broadcastSound(Location source, SoundProfile profile) {
        source.getWorld().getEntitiesByClass(LivingEntity.class).stream()
            .filter(e -> e.hasMetadata("emwm_ai_enabled"))
            .forEach(ai -> {
                double distance = ai.getLocation().distance(source);
                if (distance > profile.maxDistance) return;

                // 距离线性衰减
                double attenuatedExposure = profile.baseExposure
                    * Math.max(0, 1.0 - distance / profile.maxDistance);

                // 方块穿透衰减
                int solidBlocks = countSolidBlocksBetween(ai.getLocation(), source);
                attenuatedExposure *= Math.pow(0.7, solidBlocks);

                if (attenuatedExposure > 0) {
                    perceptionManager.receiveSoundEvent(
                        ai, source, attenuatedExposure, distance, profile.maxDistance);
                }
            });
    }

    private int countSolidBlocksBetween(Location from, Location to) {
        int count = 0;
        int steps = (int) from.distance(to) * 2;
        if (steps == 0) return 0;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(from.getX() + (to.getX() - from.getX()) * t);
            int y = (int) Math.floor(from.getY() + (to.getY() - from.getY()) * t);
            int z = (int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * t);
            if (from.getWorld().getBlockAt(x, y, z).getType().isSolid()) count++;
        }
        return count;
    }

    public void shutdown() {
        lastFootstepTime.clear();
    }

    /** 声音属性 */
    public record SoundProfile(double maxDistance, double baseExposure) {}
}
```

- [ ] **Step 14: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 15: Commit**

```bash
git add src/main/java/com/emwbridge/ai/
git commit -m "feat(ai): scaffold all 16 AI subsystem classes with skeletons"
```

---

## Phase 2: Perception 系统

### Task 2.1: 实现 VisualPerception.calculate()

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/perception/VisualPerception.java`

- [ ] **Step 1: 替换 calculate() 方法体**

将 VisualPerception 中的 `calculate()` 空方法替换为：

```java
/**
 * 计算本 tick 的视觉曝光增量
 *
 * 公式: exposureIncrement = baseRate
 *   × postureMultiplier(站立1.0/潜行0.6)
 *   × motionMultiplier(静止0.5/步行0.8/冲刺2.0)
 *   × angleMultiplier(正面1.0/侧面0.6/后方0.3)
 *   × distanceMultiplier(1.0 / (1 + distance/10))
 *   × environmentMultiplier(昼晴1.0/夜间0.5/雨天0.7/雷暴0.6)
 *   × lightMultiplier(光等级15→1.0, 光等级0→0.4)
 *
 * @param entity AI 实体
 * @param target 目标玩家
 * @param baseRate 基础曝光率(可配置)
 * @return 曝光增量 (不应用时返回0)
 */
public double calculate(LivingEntity entity, Player target, double baseRate) {
    if (!entity.hasLineOfSight(target)) return 0;

    double exposure = baseRate;

    // 姿态修正
    double posture;
    if (target.isSneaking()) {
        posture = postureSneaking;
    } else {
        posture = postureStanding;
    }
    exposure *= posture;

    // 移动修正
    double motion;
    if (target.isSprinting()) {
        motion = motionSprinting;
    } else if (target.getVelocity().length() < 0.01) {
        motion = motionStill;
    } else {
        motion = motionWalking;
    }
    exposure *= motion;

    // 角度修正 — 基于AI面朝方向与目标方位角的偏差
    double angle = calculateAngleMultiplier(entity, target);
    exposure *= angle;

    // 距离修正
    double distance = entity.getLocation().distance(target.getLocation());
    double distanceM = 1.0 / (1.0 + distance / 10.0);
    exposure *= distanceM;

    // 环境修正
    double env = getEnvironmentMultiplier(target.getWorld());
    exposure *= env;

    // 光照修正
    double light = getLightMultiplier(target);
    exposure *= light;

    return exposure;
}

private double calculateAngleMultiplier(LivingEntity entity, Player target) {
    Location entityLoc = entity.getLocation();
    Location targetLoc = target.getLocation();

    Vector entityDir = entityLoc.getDirection().setY(0).normalize();
    Vector toTarget = targetLoc.toVector().subtract(entityLoc.toVector()).setY(0).normalize();

    double dot = entityDir.dot(toTarget);

    if (dot > 0.5) return angleFront;       // 正面 ±60°
    if (dot > -0.5) return angleSide;        // 侧面
    return angleBack;                         // 后方
}

private double getEnvironmentMultiplier(org.bukkit.World world) {
    if (world.isThundering()) return envThunder;
    if (world.hasStorm()) return envRain; // raining, not thundering
    long time = world.getTime();
    if (time >= 13000 && time < 23000) return envNight;
    return envDayClear;
}

private double getLightMultiplier(Player player) {
    int lightLevel = player.getLocation().getBlock().getLightLevel();
    double lightRatio = lightLevel / lightMaxLevel;
    double multiplier = lightMinMultiplier + (1.0 - lightMinMultiplier) * lightRatio;
    multiplier = Math.max(lightMinMultiplier, Math.min(1.0, multiplier));
    return multiplier;
}
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/perception/VisualPerception.java
git commit -m "feat(ai): implement VisualPerception.calculate() with posture/motion/angle/distance/env/light modifiers"
```

---

### Task 2.2: 实现 AuditoryPerception.processSound()

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/perception/AuditoryPerception.java`

- [ ] **Step 1: 替换 processSound() 方法体**

```java
/** 上次听觉事件方向记录: mobUUID -> playerUUID -> (方向向量, timestamp) */
private final Map<UUID, Map<UUID, SoundMemory>> lastSoundDirection = new ConcurrentHashMap<>();
/** 耳鸣计时器 */
private final Map<UUID, Long> earRingingUntil = new ConcurrentHashMap<>();

/**
 * 处理声音事件
 * 逻辑:
 * 1. 距离线性衰减
 * 2. 方块穿透衰减 (在外部的 SoundEventManager.broadcastSound 已做)
 * 3. 耳机加成 40%
 * 4. 连续同方向声音 +5 额外增量
 * 5. 更新曝光值和警戒阶段
 * 6. AI 转向声源方向
 */
public void processSound(LivingEntity listener, Location soundOrigin,
                         double attenuatedExposure, double distance, double maxDistance,
                         Map<UUID, Map<UUID, Double>> exposureMap,
                         Map<UUID, Map<UUID, AlertStage>> alertStageMap) {

    UUID mobUuid = listener.getUniqueId();

    // 耳鸣检查
    Long ringingUntil = earRingingUntil.get(mobUuid);
    if (ringingUntil != null && System.currentTimeMillis() < ringingUntil) {
        return; // 耳鸣，不处理声音
    }

    // 耳机检查
    if (listener.hasMetadata("emwm_headset")) {
        attenuatedExposure *= headsetMultiplier;
        double extendedRange = maxDistance * 1.4;
        if (distance > extendedRange) return;
    }

    // 转向声源
    Location listenerLoc = listener.getLocation();
    double dx = soundOrigin.getX() - listenerLoc.getX();
    double dz = soundOrigin.getZ() - listenerLoc.getZ();
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    listenerLoc.setYaw(yaw);

    // 由于不知道具体哪个玩家发出的声音，我们找所有在监控中的玩家
    Map<UUID, Double> mobExposures = exposureMap.get(mobUuid);
    Map<UUID, AlertStage> mobAlerts = alertStageMap.get(mobUuid);
    if (mobExposures == null) {
        mobExposures = new ConcurrentHashMap<>();
        exposureMap.put(mobUuid, mobExposures);
    }
    if (mobAlerts == null) {
        mobAlerts = new ConcurrentHashMap<>();
        alertStageMap.put(mobUuid, mobAlerts);
    }

    // 向声源方向最近的玩家增加曝光
    if (!mobExposures.isEmpty()) {
        // 找到距离声源最近的被跟踪玩家
        UUID closestPlayer = null;
        double closestDist = Double.MAX_VALUE;
        for (Map.Entry<UUID, Long> entry : lastFootstepTimes.entrySet()) {
            UUID playerUuid = entry.getKey();
            if (mobExposures.containsKey(playerUuid)) {
                double d = soundOrigin.distance(listenerLoc);
                if (d < closestDist) {
                    closestDist = d;
                    closestPlayer = playerUuid;
                }
            }
        }
        if (closestPlayer == null) closestPlayer = mobExposures.keySet().iterator().next();

        // 连续同方向声音加成
        Map<UUID, SoundMemory> sounds = lastSoundDirection.computeIfAbsent(mobUuid, k -> new ConcurrentHashMap<>());
        SoundMemory memory = sounds.get(closestPlayer);
        if (memory != null
            && System.currentTimeMillis() - memory.timestamp < 3000
            && memory.direction.dot(listenerLoc.getDirection()) > 0.7) {
            attenuatedExposure += sameDirectionBonus;
        }
        sounds.put(closestPlayer, new SoundMemory(listenerLoc.getDirection(), System.currentTimeMillis()));

        // 更新曝光
        double current = mobExposures.getOrDefault(closestPlayer, 0.0);
        double updated = Math.min(100.0, current + attenuatedExposure);
        mobExposures.put(closestPlayer, updated);
        mobAlerts.put(closestPlayer, AlertStage.fromExposure(updated));
    }
}

private final Map<UUID, Long> lastFootstepTimes = new ConcurrentHashMap<>();

private record SoundMemory(Vector direction, long timestamp) {}

/** 通知听觉系统有新脚步声记录 */
public void recordFootstep(UUID playerUuid) {
    lastFootstepTimes.put(playerUuid, System.currentTimeMillis());
}
```

- [ ] **Step 2: 需要补充 import — 在文件头部声明添加**

```java
import org.bukkit.util.Vector;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
```

确保 `Vector` import 存在。

- [ ] **Step 3: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/emwbridge/ai/perception/AuditoryPerception.java
git commit -m "feat(ai): implement AuditoryPerception.processSound() with direction tracking, headset bonus, ear ringing"
```

---

### Task 2.3: 实现 PerceptionManager.updateExposure()

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/perception/PerceptionManager.java`

- [ ] **Step 1: 替换空方法体**

```java
/**
 * 每 tick 更新怪物对指定玩家的视觉曝光值
 */
public void updateExposure(LivingEntity entity, Player target) {
    UUID mobUuid = entity.getUniqueId();
    UUID playerUuid = target.getUniqueId();

    Map<UUID, Double> exposures = exposureMap.computeIfAbsent(mobUuid, k -> new ConcurrentHashMap<>());
    Map<UUID, AlertStage> alerts = alertStageMap.computeIfAbsent(mobUuid, k -> new ConcurrentHashMap<>());

    double increment = visual.calculate(entity, target, baseRate);

    double current = exposures.getOrDefault(playerUuid, 0.0);
    if (increment > 0) {
        current = Math.min(100.0, current + increment);
        // 更新最后已知位置
        lastKnownPosition.computeIfAbsent(mobUuid, k -> new ConcurrentHashMap<>())
            .put(playerUuid, target.getLocation().clone());
    } else {
        // 无视线时曝光缓慢衰减
        current = Math.max(0.0, current - 1.0); // 每秒衰减20 tick = 20 exposure
    }

    exposures.put(playerUuid, current);
    alerts.put(playerUuid, AlertStage.fromExposure(current));

    // 更新主目标 — 选曝光最高的玩家
    if (current >= 40) { // ALERT 以上才更新主目标
        updatePrimaryTarget(mobUuid, exposures);
    } else {
        // 所有玩家曝光都低时清除主目标
        boolean allLow = exposures.values().stream().allMatch(v -> v < 40);
        if (allLow) primaryTargetMap.remove(mobUuid);
    }
}

private void updatePrimaryTarget(UUID mobUuid, Map<UUID, Double> exposures) {
    UUID best = null;
    double bestExp = 0;
    for (Map.Entry<UUID, Double> e : exposures.entrySet()) {
        if (e.getValue() > bestExp) {
            bestExp = e.getValue();
            best = e.getKey();
        }
    }
    if (best != null) {
        primaryTargetMap.put(mobUuid, best);
    }
}

@Override
public AlertStage getAlertStage(UUID mobUuid, UUID playerUuid) {
    Map<UUID, AlertStage> alerts = alertStageMap.get(mobUuid);
    return alerts != null ? alerts.getOrDefault(playerUuid, AlertStage.IDLE) : AlertStage.IDLE;
}

@Override
public double getExposure(UUID mobUuid, UUID playerUuid) {
    Map<UUID, Double> exposures = exposureMap.get(mobUuid);
    return exposures != null ? exposures.getOrDefault(playerUuid, 0.0) : 0.0;
}

@Override
public Location getLastKnownPosition(UUID mobUuid, UUID playerUuid) {
    Map<UUID, Location> positions = lastKnownPosition.get(mobUuid);
    return positions != null ? positions.get(playerUuid) : null;
}
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/perception/PerceptionManager.java
git commit -m "feat(ai): implement PerceptionManager.updateExposure() with visual + exposure decay"
```

---

## Phase 3: Faction 系统

> Phase 3 已在 Task 1.5 中完整实现（FactionManager + HostilityMatrix 已含完整逻辑），仅需验证和配置集成。

### Task 3.1: 验证 FactionManager 集成

- [ ] **Step 1: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 2: 确认 list_issues 检查通过**

```
.\gradlew.bat build -x test --no-daemon
```

---

## Phase 4: Personality 系统

### Task 4.1: 实现 PersonalityManager.decide()

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/personality/PersonalityManager.java`

- [ ] **Step 1: 替换 decide() 方法体 + 添加 config 读取**

```java
import java.util.*;

// 添加字段
private Map<String, Map<PersonalityType, Double>> tierWeights = new HashMap<>();

// 替换 reload()
public void reload(FileConfiguration config) {
    tierWeights.clear();
    for (String tier : new String[]{"scav", "pmc", "boss", "cultist"}) {
        Map<PersonalityType, Double> weights = new EnumMap<>(PersonalityType.class);
        String path = "personality.tier-weights." + tier;
        if (config.contains(path)) {
            for (PersonalityType type : PersonalityType.values()) {
                double weight = config.getDouble(path + "." + type.name(), 0);
                if (weight > 0) weights.put(type, weight);
            }
        }
        tierWeights.put(tier, weights);
    }
}

// 更新 getWeight()
double getWeight(String tier, PersonalityType type) {
    Map<PersonalityType, Double> weights = tierWeights.get(tier.toLowerCase());
    if (weights != null) {
        Double w = weights.get(type);
        if (w != null) return w;
    }
    // 默认权重
    return switch (tier.toLowerCase()) {
        case "scav" -> switch (type) {
            case COWARD -> 0.35; case CAUTIOUS -> 0.30;
            case LOOTER -> 0.25; case RECKLESS -> 0.10;
            default -> 0;
        };
        case "pmc" -> switch (type) {
            case CAUTIOUS -> 0.30; case FLANKER -> 0.20;
            case CAPTAIN -> 0.15; case SUPPRESSOR -> 0.15;
            case AMBUSH -> 0.10; case RECKLESS -> 0.10;
            default -> 0;
        };
        case "boss" -> switch (type) {
            case CAPTAIN -> 0.40; case RECKLESS -> 0.25;
            case SUPPRESSOR -> 0.20; case FLANKER -> 0.15;
            default -> 0;
        };
        case "cultist" -> switch (type) {
            case AMBUSH -> 0.40; case FLANKER -> 0.30;
            case CAUTIOUS -> 0.30;
            default -> 0;
        };
        default -> 0;
    };
}

// 替换 decide()
/**
 * 根据性格+警戒阶段+HP 计算行为决策
 * @return 最优行为决策
 */
public AIDecision decide(UUID entityUuid, double hpRatio, double exposureValue) {
    PersonalityType personality = getPersonality(entityUuid);

    double attackWeight = personality.getAttackWeight(exposureValue);
    double defendWeight = personality.getDefendWeight(exposureValue);
    double ambushWeight = personality.getAmbushWeight(exposureValue);
    double retreatWeight = personality.getRetreatWeight(hpRatio);

    // 濒死强制撤退
    if (hpRatio < 0.15) return AIDecision.RETREAT;

    // 选最高权重
    double max = Math.max(Math.max(attackWeight, defendWeight),
                          Math.max(ambushWeight, retreatWeight));

    if (retreatWeight >= max) return AIDecision.RETREAT;
    if (ambushWeight >= max) return AIDecision.AMBUSH;
    if (defendWeight >= max) return AIDecision.DEFEND;
    return AIDecision.ENGAGE;
}
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/personality/PersonalityManager.java
git commit -m "feat(ai): implement PersonalityManager.decide() with config-driven tier weights"
```

---

## Phase 5: Squad

### Task 5.1: 实现 SquadManager.tryJoin() + shareIntel()

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/squad/SquadManager.java`

- [ ] **Step 1: 添加字段 + 实现 tryJoin()**

在 `private final Map<UUID, Squad> squads` 后添加:

```java
    private int maxSquadSize = 5;
    private double intelShareRange = 50.0;
    private boolean enabled = true;

    public void reload(FileConfiguration config) {
        enabled = config.getBoolean("squad.enabled", true);
        maxSquadSize = config.getInt("squad.max-size", 5);
        intelShareRange = config.getDouble("squad.intel-share-range", 50.0);
    }
```

替换 `tryJoin()`:

```java
    public void tryJoin(LivingEntity entity, String tier, PersonalityType personality) {
        if (!enabled) return;
        UUID entityUuid = entity.getUniqueId();

        // 在附近找同阵营小队
        for (Map.Entry<UUID, Squad> entry : squads.entrySet()) {
            Squad squad = entry.getValue();
            if (squad.memberUuids.size() >= maxSquadSize) continue;

            UUID sampleMember = squad.memberUuids.get(0);
            org.bukkit.entity.Entity memberEntity = Bukkit.getEntity(sampleMember);
            if (memberEntity == null) continue;

            if (memberEntity.getLocation().getWorld() == entity.getWorld()
                && memberEntity.getLocation().distance(entity.getLocation()) < 20.0) {
                entitySquadMap.put(entityUuid, entry.getKey());
                squad.memberUuids.add(entityUuid);
                assignRole(squad, entityUuid, personality, tier);
                return;
            }
        }

        // 无附近小队，创建新小队
        UUID squadId = UUID.randomUUID();
        Squad newSquad = new Squad();
        newSquad.captainUuid = (personality == PersonalityType.CAPTAIN) ? entityUuid : entityUuid;
        newSquad.memberUuids.add(entityUuid);
        assignRole(newSquad, entityUuid, personality, tier);
        squads.put(squadId, newSquad);
        entitySquadMap.put(entityUuid, squadId);
    }

    private void assignRole(Squad squad, UUID uuid, PersonalityType personality, String tier) {
        if (personality == PersonalityType.CAPTAIN) {
            squad.captainUuid = uuid;
            squad.roleAssignments.put(uuid, SquadRole.ASSAULT); // 队长同时是突击手
            return;
        }
        SquadRole role = SquadRole.values()[(int)(Math.random() * SquadRole.values().length)];
        squad.roleAssignments.put(uuid, role);
    }
```

替换 `shareIntel()`:

```java
    public void shareIntel(UUID discovererUuid, Player target) {
        UUID squadId = entitySquadMap.get(discovererUuid);
        if (squadId == null) return;

        Squad squad = squads.get(squadId);
        if (squad == null) return;

        squad.sharedTarget = target;
        squad.sharedTargetLocation = target.getLocation().clone();
    }

    /** 获取小队共享目标 */
    public Player getSharedTarget(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return null;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.sharedTarget : null;
    }

    /** 获取小队共享目标位置 */
    public Location getSharedTargetLocation(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return null;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.sharedTargetLocation : null;
    }
```

添加 import:
```java
import com.emwbridge.ai.personality.PersonalityType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/squad/SquadManager.java
git commit -m "feat(ai): implement SquadManager.tryJoin() with auto-squad-creation and intel sharing"
```

---

## Phase 6: Aim 系统

### Task 6.1: 实现 AimConvergenceManager.update()

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/combat/AimConvergenceManager.java`

- [ ] **Step 1: 替换 update() 方法体**

```java
    /** 每个 tier 的初始瞄准延迟 */
    private Map<String, Double> initialDelays = new HashMap<>();

    // 更新 reload() - 在已有方法体后追加
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

    // 更新 getInitialDelay()
    public double getInitialDelay(String tier) {
        Double delay = initialDelays.get(tier.toLowerCase());
        if (delay == null) delay = 1.0;
        return delay + (Math.random() - 0.5) * delay;
    }

    // 替换 update()
    /**
     * 更新瞄准状态并返回瞄准结果
     *
     * 规则:
     * 1. 连续锁定 < 15s + 有 eyeLOS → 锁头 (aimPoint = eyeLocation)
     * 2. 锁定 > 15s OR 只有 bodyLOS → 躯干 (aimPoint = bodyCenter)
     * 3. 散布 = baseSpread × convergenceRate^锁定秒数
     * 4. 脱离视线 > 2s → 散布重置
     * 5. 掩体反复露身 → 散布保留记忆
     */
    public AimResult update(LivingEntity entity, LivingEntity target,
                            boolean hasEyeLOS, boolean hasBodyLOS, double baseSpread) {
        UUID uuid = entity.getUniqueId();
        AimState state = aimStates.computeIfAbsent(uuid, k -> new AimState());

        long now = System.currentTimeMillis();

        boolean canSee = hasEyeLOS || hasBodyLOS;

        if (canSee) {
            if (state.lockStartTime == 0) {
                state.lockStartTime = now;
            }
            state.lastVisionTime = now;

            // 散布收敛
            double lockSeconds = (now - state.lockStartTime) / 1000.0;
            state.currentSpreadMultiplier = Math.max(
                minSpreadMultiplier,
                Math.pow(convergenceRate, lockSeconds)
            );

            // 掩体记忆
            Location targetPos = target.getLocation();
            if (state.lastEngagePosition != null
                && state.lastEngagePosition.distance(targetPos) <= 2.0) {
                // 同一掩体反复露身，散布额外缩小
                state.currentSpreadMultiplier *= 0.7;
            }
            state.lastEngagePosition = targetPos.clone();

        } else {
            // 视线丢失
            if (now - state.lastVisionTime > visionLossResetSeconds * 1000) {
                state.currentSpreadMultiplier = 1.0;
                state.lockStartTime = 0;
            }
        }

        double spread = baseSpread * state.currentSpreadMultiplier;

        // 锁头窗口判定
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
            // 无视线 — 返回最后已知位置
            aimPoint = target.getLocation();
            spread = baseSpread * 3.0; // 无视线时高散布
        }

        // 散布随机偏移
        Location finalPoint = aimPoint.clone().add(
            (Math.random() - 0.5) * spread,
            (Math.random() - 0.5) * spread * 0.6, // 垂直散布更小
            (Math.random() - 0.5) * spread
        );

        return new AimResult(finalPoint, spread);
    }
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/combat/AimConvergenceManager.java
git commit -m "feat(ai): implement AimConvergenceManager.update() with lock-on convergence, headshot window, cover memory"
```

---

## Phase 7: Sound 系统

> SoundEventManager 已在 Task 1.5 完整实现（含 5 个 EventHandler + broadcastSound + 枪声 API + 方块穿透衰减）。仅需集成注册。

### Task 7.1: 集成 SoundEventManager 注册

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/engine/TarkovAIEngine.java`

- [ ] **Step 1: 在 start() 注册事件监听**

```java
    public void start() {
        reloadConfig();
        soundEventManager.registerEvents();
        startScheduler();
        plugin.getLogger().info("TarkovAIEngine 已启动");
    }

    private void reloadConfig() {
        var config = plugin.getConfig();
        aiTickRate = config.getInt("settings.ai-tick-rate", 4);
        perceptionManager.reload();
        personalityManager.reload(config);
        squadManager.reload(config);
        aimConvergenceManager.reload(config);
        tactics.reload(config);
        coverMovement.reload(config.getBoolean("tactical.restrict-movement", true));
    }
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/engine/TarkovAIEngine.java
git commit -m "feat(ai): integrate SoundEventManager registration in TarkovAIEngine.start()"
```

---

## Phase 8: TarkovAIEngine 集成

### Task 8.1: 实现 TarkovAIEngine.tick() 完整决策链

**Files:**
- Modify: `src/main/java/com/emwbridge/ai/engine/TarkovAIEngine.java`

- [ ] **Step 1: 在 TarkovAIEngine 添加完整 tick 逻辑**

```java
    private int farDistanceThreshold = 40;

    // 更新 reloadConfig()
    private void reloadConfig() {
        var config = plugin.getConfig();
        aiTickRate = config.getInt("settings.ai-tick-rate", 4);
        farDistanceThreshold = config.getInt("settings.far-distance-threshold", 40);
        perceptionManager.reload();
        personalityManager.reload(config);
        squadManager.reload(config);
        aimConvergenceManager.reload(config);
        tactics.reload(config);
        coverMovement.reload(config.getBoolean("tactical.restrict-movement", true));
    }

    // 完整 start() + scheduler
    public void start() {
        reloadConfig();
        soundEventManager.registerEvents();
        startScheduler();
        plugin.getLogger().info("TarkovAIEngine 已启动, AI tick=" + aiTickRate);
    }

    private void startScheduler() {
        Runnable task = () -> {
            tickCounter++;
            for (Iterator<Map.Entry<UUID, AIState>> it = activeMobs.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, AIState> entry = it.next();
                UUID uuid = entry.getKey();
                AIState state = entry.getValue();

                LivingEntity entity = (LivingEntity) Bukkit.getEntity(uuid);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    perceptionManager.unregisterMob(uuid);
                    aimConvergenceManager.unregisterMob(uuid);
                    tactics.unregisterMob(uuid);
                    personalityManager.removeEntity(uuid);
                    factionManager.removeEntity(uuid);
                    squadManager.removeEntity(uuid);
                    it.remove();
                    continue;
                }

                tickEntity(entity, state);
            }
        };

        if (plugin.isFolia()) {
            try {
                Object server = Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                    .getMethod("getInstance").invoke(null);
                Object globalScheduler = server.getClass().getMethod("getGlobalScheduler").invoke(server);
                schedulerTask = globalScheduler.getClass().getMethod("runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class)
                    .invoke(globalScheduler, plugin,
                        (java.util.function.Consumer<Object>) t -> task.run(), 20L, (long) aiTickRate);
            } catch (Exception e) {
                schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 20L, aiTickRate);
            }
        } else {
            schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 20L, aiTickRate);
        }
    }

    public void stop() {
        if (schedulerTask != null) {
            try {
                if (schedulerTask instanceof BukkitTask bt) bt.cancel();
                else schedulerTask.getClass().getMethod("cancel").invoke(schedulerTask);
            } catch (Exception ignored) {}
            schedulerTask = null;
        }
    }

    // 核心 tick
    private void tickEntity(LivingEntity entity, AIState state) {
        var tier = state.tier;
        PersonalityType personality = personalityManager.getPersonality(entity.getUniqueId());
        double hpRatio = entity.getHealth() / entity.getMaxHealth();

        // 1. 找附近玩家并更新感知
        List<Player> nearbyPlayers = entity.getWorld().getPlayers().stream()
            .filter(p -> !p.isDead() && p.isOnline()
                && p.getLocation().distance(entity.getLocation()) < 60)
            .toList();

        // 更新对所有附近玩家的视觉曝光
        for (Player p : nearbyPlayers) {
            perceptionManager.updateExposure(entity, p);
        }

        // 获取主目标
        UUID primaryUuid = perceptionManager.getPrimaryTarget(entity.getUniqueId());
        Player primaryTarget = primaryUuid != null ? Bukkit.getPlayer(primaryUuid) : null;

        if (primaryTarget == null) return;

        double distance = entity.getLocation().distance(primaryTarget.getLocation());
        AlertStage alert = perceptionManager.getAlertStage(entity.getUniqueId(), primaryTarget.getUniqueId());

        // 2. 阵营判定
        faction.Relation relation = factionManager.getRelation(entity, primaryTarget);
        if (relation == faction.Relation.NEUTRAL) {
            if (factionManager.shouldTurnHostile(entity, primaryTarget)) {
                relation = faction.Relation.HOSTILE;
            }
        }
        if (relation == faction.Relation.FRIENDLY) return;
        if (relation == faction.Relation.NEUTRAL) return;

        // 3. 警戒阶段检查
        if (alert == AlertStage.SUSPICIOUS) return; // 黄: 只警戒，不攻击
        if (alert != AlertStage.HOSTILE && alert != AlertStage.ALERT) return;

        // 4. 小队情报共享
        squadManager.shareIntel(entity.getUniqueId(), primaryTarget);
        SquadRole squadRole = squadManager.getRole(entity.getUniqueId());

        // 5. 性格决策
        double exposure = perceptionManager.getExposure(entity.getUniqueId(), primaryTarget.getUniqueId());
        AIDecision decision = personalityManager.decide(entity.getUniqueId(), hpRatio, exposure);

        // 6. 瞄准收敛
        boolean hasEyeLOS = entity.hasLineOfSight(primaryTarget) && entity.hasLineOfSight(primaryTarget.getEyeLocation());
        boolean hasBodyLOS = entity.hasLineOfSight(primaryTarget);
        double baseSpread = weaponManager.getBaseSpread(entity);
        AimConvergenceManager.AimResult aim = aimConvergenceManager.update(
            entity, primaryTarget, hasEyeLOS, hasBodyLOS, baseSpread);

        // 7. 战术执行
        if (decision == AIDecision.ENGAGE || decision == AIDecision.DEFEND) {
            if (standAndShoot) {
                coverMovement.standAndAim(entity, primaryTarget);
            }
            if (shouldFire(entity, primaryTarget, personality, aim)) {
                weaponManager.shoot(entity, aim.aimPoint, distance > hipfireRange);
            }
            if (Math.random() < repositionBetweenBursts) {
                coverMovement.repositionAfterBurst(entity, primaryTarget);
            }
        } else if (decision == AIDecision.AMBUSH) {
            coverMovement.stopMoving(entity);
            if (hasEyeLOS && distance < 25) weaponManager.shoot(entity, aim.aimPoint, true);
        } else if (decision == AIDecision.RETREAT) {
            coverMovement.moveAlongLineOfFire(entity, primaryTarget, 0.1, 30);
        }
    }

    private boolean shouldFire(LivingEntity entity, Player target, PersonalityType personality, AimConvergenceManager.AimResult aim) {
        return Math.random() < personality.aggressiveness * 0.8;
    }
```

需要添加 import:
```java
import com.emwbridge.ai.AIDecision;
import com.emwbridge.ai.faction;
import com.emwbridge.ai.personality.PersonalityType;
import com.emwbridge.ai.squad.SquadRole;
import com.emwbridge.ai.perception.AlertStage;
import java.util.Iterator;
import java.util.List;
```

同时需要将 `registerMob` 升级为调用各子系统的注册：

```java
    public void registerMob(LivingEntity entity, String tier) {
        UUID uuid = entity.getUniqueId();
        activeMobs.put(uuid, new AIState(tier));
        perceptionManager.registerMob(uuid);
        factionManager.assignByTier(uuid, tier);
        PersonalityType personality = personalityManager.rollByTier(tier);
        personalityManager.assignPersonality(uuid, personality);
        squadManager.tryJoin(entity, tier, personality);
        aimConvergenceManager.registerMob(uuid);
        tactics.registerMob(uuid);
        entity.setMetadata("emwm_ai_enabled", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    }

    public void unregisterMob(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        activeMobs.remove(uuid);
        perceptionManager.unregisterMob(uuid);
        factionManager.removeEntity(uuid);
        personalityManager.removeEntity(uuid);
        squadManager.removeEntity(uuid);
        aimConvergenceManager.unregisterMob(uuid);
        tactics.unregisterMob(uuid);
        entity.removeMetadata("emwm_ai_enabled", plugin);
    }
```

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat compileJava --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/ai/engine/TarkovAIEngine.java
git commit -m "feat(ai): implement full TarkovAIEngine tick chain with all 6 subsystems integrated"
```

---

## Phase 9: TarkovAIManager 缩减为桥接层

### Task 9.1: 重构 TarkovAIManager → 委托给 Engine

**Files:**
- Modify: `src/main/java/com/emwbridge/managers/TarkovAIManager.java`

- [ ] **Step 1: 简化 start()**

替换 TarkovAIManager 的字段和关键方法为 Engine 委托:

```java
public class TarkovAIManager {

    private final EMWMBridge plugin;
    private final TarkovAIEngine engine;

    public TarkovAIManager(EMWMBridge plugin) {
        this.plugin = plugin;
        this.engine = new TarkovAIEngine(plugin,
            plugin.getMobWeaponManager(), plugin.getExtremeEventManager());
    }

    public void start() {
        engine.start();
        plugin.getLogger().info("Tarkov AI Manager 已启动 (桥接层→Engine)");
    }

    public void stop() { engine.stop(); }
    public void restart() { engine.stop(); engine.start(); }
    public void registerMob(LivingEntity entity, String tier) { engine.registerMob(entity, tier); }
    public void unregisterMob(LivingEntity entity) { engine.unregisterMob(entity); }
    public boolean isActive(LivingEntity entity) { return engine.isActive(entity); }
    public int getActiveCount() { return engine.getActiveCount(); }
    public void shutdown() { engine.shutdown(); }
    public PerceptionManager getPerceptionManager() { return engine.getPerceptionManager(); }
}
```

保留枚举定义(Tactic, CombatState, FireMode, AIState, TierSettings)不变，因为 EliteMobSpawnListener 等外部类引用它们。

- [ ] **Step 2: 编译验证**

```
.\gradlew.bat build -x test --no-daemon
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/emwbridge/managers/TarkovAIManager.java
git commit -m "refactor(ai): reduce TarkovAIManager to bridge layer, delegate to TarkovAIEngine"
```

---

## Phase 10: config.yml 新增配置段

### Task 10.1: 追加新配置

**Files:**
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: 在文件末尾追加**

```yaml
# ==================== AI 感知系统 ====================
perception:
  visual:
    base-rate: 5.0
    posture:
      standing: 1.0
      sneaking: 0.6
    motion:
      still: 0.5
      walking: 0.8
      sprinting: 2.0
    angle:
      front: 1.0
      side: 0.6
      back: 0.3
    environment:
      day-clear: 1.0
      night: 0.5
      rain: 0.7
      thunder: 0.6
    light:
      max-level: 15.0
      min-multiplier: 0.4
  auditory:
    enabled: true
    process-mc-events: true
    headset-multiplier: 1.4
    ear-ringing-ms: 2000
    same-direction-bonus: 5.0

# ==================== 阵营系统 ====================
factions:
  default-faction: AI_SCAV
  metadata-key: emwm_faction
  player-scav-permission: emwm.scav

# ==================== 性格系统 ====================
personality:
  assignment: TIER_BASED
  tier-weights:
    scav:
      COWARD: 0.35
      CAUTIOUS: 0.30
      LOOTER: 0.25
      RECKLESS: 0.10
    pmc:
      CAUTIOUS: 0.30
      FLANKER: 0.20
      CAPTAIN: 0.15
      SUPPRESSOR: 0.15
      AMBUSH: 0.10
      RECKLESS: 0.10
    boss:
      CAPTAIN: 0.40
      RECKLESS: 0.25
      SUPPRESSOR: 0.20
      FLANKER: 0.15
    cultist:
      AMBUSH: 0.40
      FLANKER: 0.30
      CAUTIOUS: 0.30

# ==================== 小队系统 ====================
squad:
  enabled: true
  max-size: 5
  intel-share-range: 50.0

# ==================== 瞄准系统 ====================
aim:
  initial-delay:
    scav: 1.0
    pmc: 0.8
    cultist: 0.5
    boss: 0.3
  headshot-window-seconds: 15.0
  convergence-rate: 0.85
  min-spread-multiplier: 0.2
  vision-loss-reset-seconds: 2.0
```

- [ ] **Step 2: 升级 config version**

修改 config.yml 头部版本号为 `1.2.0`。

- [ ] **Step 3: 编译 + 部署验证**

```
.\gradlew.bat build -x test --no-daemon
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/config.yml
git commit -m "feat(config): add perception/faction/personality/squad/aim config sections"
```

---

## 验证清单

- [ ] Phase 1: 所有 16 类编译通过，4 个枚举值完整
- [ ] Phase 2: VisualPerception 返回正确曝光值（受姿态/移动/角度/距离/天气/光照影响）
- [ ] Phase 3: FactionManager 返回正确阵营关系
- [ ] Phase 4: PersonalityManager.rollByTier() 按权重分配性格
- [ ] Phase 5: SquadManager 在 20 格内同阵营怪物加入同一小队
- [ ] Phase 6: AimConvergenceManager 15 秒后散布收敛到 min，掩体反复露身散布保留
- [ ] Phase 7: SoundEventManager 爆炸/伤害/破坏/脚步声/枪声事件正确衰减分发
- [ ] Phase 8: TarkovAIEngine.tick() 串联所有子系统，怪物正常战斗
- [ ] Phase 9: TarkovAIManager 桥接层兼容现有 EliteMobSpawnListener
- [ ] Phase 10: config.yml 包含所有新增配置段
