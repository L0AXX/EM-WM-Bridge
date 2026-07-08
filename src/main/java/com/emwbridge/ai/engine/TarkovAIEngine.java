package com.emwbridge.ai.engine;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.combat.AimConvergenceManager;
import com.emwbridge.ai.combat.CoverMovement;
import com.emwbridge.ai.combat.TarkovTactics;
import com.emwbridge.ai.combat.ThrowableManager;
import com.emwbridge.ai.events.AIEventDispatcher;
import com.emwbridge.ai.faction.FactionManager;
import com.emwbridge.ai.perception.AIVisionManager;
import com.emwbridge.ai.perception.AlertStage;
import com.emwbridge.ai.perception.AuditoryPerception;
import com.emwbridge.ai.personality.PersonalityManager;
import com.emwbridge.ai.sound.SoundEventManager;
import com.emwbridge.ai.squad.SquadManager;
import com.emwbridge.managers.ExtremeEventManager;
import com.emwbridge.managers.MobWeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TarkovAIEngine {

    private final EMWMBridge plugin;
    private final MobWeaponManager weaponManager;
    private final ExtremeEventManager extremeEventManager;

    private final AIVisionManager aiVisionManager;
    private final AIEventDispatcher eventDispatcher;
    private final FactionManager factionManager;
    private final PersonalityManager personalityManager;
    private final SquadManager squadManager;
    private final AimConvergenceManager aimConvergenceManager;
    private final TarkovTactics tactics;
    private final CoverMovement coverMovement;
    private final SoundEventManager soundEventManager;
    private final ThrowableManager throwableManager;

    private final Map<UUID, AIState> activeMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> unreachableLogCooldown = new ConcurrentHashMap<>();
    private Object schedulerTask;
    private int aiTickRate = 4;
    private int tickCounter = 0;
    private int farDistanceThreshold = 40;
    private boolean standAndShoot = true;
    private double hipfireRange = 15.0;
    private double repositionBetweenBursts = 0.35;

    private int searchDurationTicks = 240;
    private double meleeDistanceThreshold = 5.0;
    private int patrolMinRadius = 8;
    private int patrolMaxRadius = 20;
    private double patrolNewPointChance = 0.02;
    private int patrolStayMinTicks = 60;
    private int patrolStayMaxTicks = 120;

    private boolean useEMEventDriven = false;

    // P0-7 修复：PersistentDataContainer 键，用于服务器重启后恢复 AI 状态
    private final NamespacedKey tierKey;

    public TarkovAIEngine(EMWMBridge plugin, MobWeaponManager weaponManager,
                          ExtremeEventManager extremeEventManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
        this.extremeEventManager = extremeEventManager;
        this.tierKey = new NamespacedKey(plugin, "emwm_tier_pdc");

        this.eventDispatcher = new AIEventDispatcher();
        AuditoryPerception auditory = new AuditoryPerception();
        this.aiVisionManager = new AIVisionManager(plugin, auditory, eventDispatcher);

        this.factionManager = new FactionManager();
        this.factionManager.load(plugin);
        this.personalityManager = new PersonalityManager(plugin);
        this.squadManager = new SquadManager(plugin);
        this.aimConvergenceManager = new AimConvergenceManager(plugin);
        this.tactics = new TarkovTactics(plugin, weaponManager);
        this.coverMovement = new CoverMovement();
        this.soundEventManager = new SoundEventManager(plugin, aiVisionManager);
        this.throwableManager = new ThrowableManager(plugin);

        eventDispatcher.register(AIEventDispatcher.EventType.SIGHT, event -> {
            squadManager.shareIntel(event.aiEntityUuid(), Bukkit.getPlayer(event.targetPlayerUuid()));
        });
        eventDispatcher.register(AIEventDispatcher.EventType.SOUND, event -> {
            squadManager.shareSoundIntel(event.aiEntityUuid(), event.location(), event.value());
        });
        eventDispatcher.register(AIEventDispatcher.EventType.FLASH_BLIND, event -> {
            var squad = squadManager.getSquad(event.aiEntityUuid());
            if (squad != null) {
                for (UUID memberUuid : squad) {
                    if (memberUuid.equals(event.aiEntityUuid())) continue;
                    LivingEntity member = (LivingEntity) Bukkit.getEntity(memberUuid);
                    if (member != null && member.isValid()) {
                        aiVisionManager.flashBlind(member);
                    }
                }
            }
        });
    }

    public void start() {
        reloadConfig();
        soundEventManager.registerEvents();

        if (useEMEventDriven) {
            plugin.getLogger().info("TarkovAIEngine 已启动 (EliteMobs事件驱动模式)");
        } else {
            startScheduler();
            plugin.getLogger().info("TarkovAIEngine 已启动 (独立AI模式), AI tick=" + aiTickRate);
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

    private void reloadConfig() {
        var config = plugin.getConfig();
        aiTickRate = config.getInt("settings.ai-tick-rate", 4);
        farDistanceThreshold = config.getInt("settings.far-distance-threshold", 40);
        standAndShoot = config.getBoolean("tactical.stand-and-shoot", true);
        hipfireRange = config.getDouble("tactical.hipfire-range", 15.0);
        repositionBetweenBursts = config.getDouble("tactical.reposition-between-bursts", 0.35);

        searchDurationTicks = config.getInt("settings.search.duration-ticks", 120);
        meleeDistanceThreshold = config.getDouble("settings.melee.distance-threshold", 5.0);
        patrolMinRadius = config.getInt("settings.patrol.min-radius", 8);
        patrolMaxRadius = config.getInt("settings.patrol.max-radius", 20);
        patrolNewPointChance = config.getDouble("settings.patrol.new-point-chance", 0.02);

        useEMEventDriven = config.getBoolean("settings.use-elitemobs-events", false);

        String stayTicks = config.getString("settings.patrol.stay-ticks", "60-120");
        parseStayTicks(stayTicks);
        aiVisionManager.reload(config);
        personalityManager.reload(config);
        squadManager.reload(config);
        aimConvergenceManager.reload(config);
        tactics.reload(config);
        throwableManager.reload(config);
        coverMovement.reload(config.getBoolean("tactical.restrict-movement", true));
        coverMovement.reloadAdvanced(
                config.getDouble("tactical.flank.radius", 15.0),
                config.getDouble("tactical.retreat.distance", 25.0));
    }

    @SuppressWarnings("unchecked")
    private void startScheduler() {
        Runnable task = () -> {
            tickCounter++;
            for (Iterator<Map.Entry<UUID, AIState>> it = activeMobs.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, AIState> entry = it.next();
                UUID uuid = entry.getKey();
                AIState state = entry.getValue();
                LivingEntity entity = (LivingEntity) Bukkit.getEntity(uuid);
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    cleanupMob(uuid);
                    it.remove();
                    continue;
                }
                // P0-5 修复：Folia 环境下将实体操作调度到正确的 Region 线程
                if (plugin.isFolia()) {
                    try {
                        entity.getScheduler().execute(plugin, () -> {
                            if (entity.isValid() && !entity.isDead()) {
                                tickEntity(entity, state);
                            }
                        }, null, 1);
                    } catch (Exception e) {
                        // Folia EntityScheduler 不可用时降级为直接调用
                        tickEntity(entity, state);
                    }
                } else {
                    tickEntity(entity, state);
                }
            }
        };

        if (plugin.isFolia()) {
            try {
                Object server = Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                        .getMethod("getInstance").invoke(null);
                Object globalScheduler = server.getClass().getMethod("getGlobalScheduler").invoke(server);
                schedulerTask = globalScheduler.getClass().getMethod("runAtFixedRate",
                        org.bukkit.plugin.Plugin.class,
                        java.util.function.Consumer.class,
                        long.class, long.class)
                        .invoke(globalScheduler, plugin,
                                (java.util.function.Consumer<Object>) t -> task.run(),
                                20L, (long) aiTickRate);
            } catch (Exception e) {
                schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 20L, aiTickRate);
            }
        } else {
            schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 20L, aiTickRate);
        }
    }

    private void cleanupMob(UUID uuid) {
        aiVisionManager.unregisterMob(uuid);
        aimConvergenceManager.unregisterMob(uuid);
        tactics.unregisterMob(uuid);
        throwableManager.unregisterMob(uuid);
        personalityManager.removeEntity(uuid);
        factionManager.removeEntity(uuid);
        squadManager.removeEntity(uuid);
        unreachableLogCooldown.remove(uuid);
    }

    private void tickEntity(LivingEntity entity, AIState state) {
        // 始终清除原生Mob目标，防止原版AI近战攻击（不触发WeaponMechanics死亡消息带枪械名）
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
        }

        // 有主目标时让实体面向目标方向，确保 FOV 检查能通过
        UUID preTargetUuid = aiVisionManager.getPrimaryTarget(entity.getUniqueId());
        if (preTargetUuid != null) {
            Player preTarget = Bukkit.getPlayer(preTargetUuid);
            if (preTarget != null && preTarget.isOnline() && !preTarget.isDead()) {
                Location mobLoc = entity.getLocation();
                Vector dir = preTarget.getLocation().toVector().subtract(mobLoc.toVector()).setY(0);
                if (dir.lengthSquared() > 0.01) {
                    dir.normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                    entity.setRotation(yaw, mobLoc.getPitch());
                }
            }
        }

        UUID uuid = entity.getUniqueId();
        var personality = personalityManager.getPersonality(uuid);
        // P0-9 修复：防止 getMaxHealth() 为零导致除零和 NaN 传播
        double maxHealth = entity.getMaxHealth();
        double hpRatio = maxHealth > 0 ? entity.getHealth() / maxHealth : 1.0;

        double tierVisionRange = plugin.getConfig().getDouble("tier-settings." + state.tier + ".vision-range", 50.0);
        aiVisionManager.getVisual().setMaxVisionRange(tierVisionRange);

        double tierSoundRange = plugin.getConfig().getDouble("tier-settings." + state.tier + ".sound-range", 60.0);

        List<Player> nearbyPlayers = entity.getWorld().getPlayers().stream()
                .filter(p -> !p.isDead() && p.isOnline()
                        && safeDistance(p.getLocation(), entity.getLocation()) < Math.max(tierVisionRange, tierSoundRange) + 20)
                .toList();

        for (Player p : nearbyPlayers) {
            aiVisionManager.tickExposure(entity, p);
        }

        UUID primaryUuid = aiVisionManager.getPrimaryTarget(entity.getUniqueId());
        Player primaryTarget = primaryUuid != null ? Bukkit.getPlayer(primaryUuid) : null;

        if (primaryTarget == null) {
            UUID hatredTargetUuid = AlertStage.getHatredTarget(entity.getUniqueId());
            if (hatredTargetUuid != null) {
                Player hatredTarget = Bukkit.getPlayer(hatredTargetUuid);
                if (hatredTarget != null && hatredTarget.isOnline() && !hatredTarget.isDead()) {
                    primaryTarget = hatredTarget;
                    primaryUuid = hatredTargetUuid;
                }
            }
        }

        if (primaryTarget == null) {
            // 需求1.4-1.5：无玩家目标时，按 GreyZone 阵营关系扫描最近的敌对 AI 实体并交战
            LivingEntity aiTarget = selectHostileAITarget(entity, tierVisionRange, tierSoundRange);
            if (aiTarget != null) {
                engageAITarget(entity, aiTarget, state, hpRatio);
                return;
            }
            if (state.target != null) {
                state.lastKnownLocation = state.target.getLocation().clone();
                state.searchTicks = searchDurationTicks;
            }
            handleNoTargetState(entity, state);
            state.target = null;
            return;
        }

        state.target = primaryTarget;
        double distance = safeDistance(entity.getLocation(), primaryTarget.getLocation());

        AlertStage alert = aiVisionManager.getAlertStage(uuid, primaryTarget.getUniqueId());

        // Y轴不可达检测：目标远高于AI(>6格)且无视线 → 快速衰减，不做交战行为
        // 使用自定义射线检测（与曝光计算一致），避免 Bukkit hasLineOfSight 不一致导致死循环
        boolean heightUnreachable = primaryTarget.getLocation().getY() - entity.getLocation().getY() > 6
                && !aiVisionManager.getVisual().hasLineOfSight(entity, primaryTarget);
        if (heightUnreachable && alert != null && alert.isHostile()) {
            // 快速衰减暴露值
            aiVisionManager.fastDecayExposure(uuid, primaryTarget.getUniqueId());
            // 日志限流：每5秒（25 tick @ aiTickRate=4）最多打印一次
            int cooldown = unreachableLogCooldown.getOrDefault(uuid, 0);
            if (cooldown <= 0) {
                plugin.debug("[AI] " + entity.getName() + " 目标不可达(太高)，衰减警觉");
                unreachableLogCooldown.put(uuid, 25);
            } else {
                unreachableLogCooldown.put(uuid, cooldown - 1);
            }
            alert = aiVisionManager.getAlertStage(uuid, primaryTarget.getUniqueId());
            if (alert == null || !alert.isHostile()) {
                handleNoTargetState(entity, state);
                state.target = null;
                unreachableLogCooldown.remove(uuid);
                return;
            }
        } else {
            unreachableLogCooldown.remove(uuid);
        }

        state.ticksSinceEngage++;

        var rel = factionManager.getRelation(entity, primaryTarget);
        if (rel == com.emwbridge.ai.faction.HostilityMatrix.Relation.NEUTRAL) {
            if (factionManager.shouldTurnHostile(entity, primaryTarget)) {
                rel = com.emwbridge.ai.faction.HostilityMatrix.Relation.HOSTILE;
            }
        }
        if (rel == com.emwbridge.ai.faction.HostilityMatrix.Relation.FRIENDLY) return;
        if (rel == com.emwbridge.ai.faction.HostilityMatrix.Relation.NEUTRAL) return;

        if (alert == null) return;

        if (state.ticksSinceEngage == 1) {
            tactics.resetThrowableFlags(uuid);
        }

        squadManager.shareIntel(uuid, primaryTarget);

        double exposure = aiVisionManager.getExposure(uuid, primaryTarget.getUniqueId());
        com.emwbridge.ai.AIDecision decision = personalityManager.decide(uuid, hpRatio, exposure);

        boolean hasBodyLOS = aiVisionManager.getVisual().hasLineOfSight(entity, primaryTarget);
        // 眼部LOS：检查头部是否透过透明方块可见
        boolean hasEyeLOS = aiVisionManager.getVisual().hasHeadLOS(entity, primaryTarget);

        boolean targetBehindCover = !hasBodyLOS;
        boolean isOpenArea = isOpenArea(entity);
        int nearbyEnemyCount = countNearbyEnemies(entity, primaryTarget, 10);

        TarkovTactics.TacticalAction tacticalAction = tactics.decideTacticalAction(
                uuid, hpRatio, targetBehindCover, nearbyEnemyCount, distance, isOpenArea, state.ticksSinceEngage, state.tier);

        // WM内部已经处理武器散布，这里只选择瞄点（躯干/头/腿）+ 不可见时偏移
        AimConvergenceManager.AimResult aim = aimConvergenceManager.update(
                entity, primaryTarget, hasEyeLOS, hasBodyLOS, distance,
                state.tier, tactics.isSuppressing(uuid));

        // P0-3 修复：接入极限事件系统（恐慌/肾上腺素/战术失误/幸运一击）
        extremeEventManager.checkExtremeEvents(entity, primaryTarget, state.tier);

        executeTacticalAction(entity, primaryTarget, uuid, hpRatio, distance, exposure,
                hasEyeLOS, hasBodyLOS, decision, aim, tacticalAction, personality);

        // P0-3 修复：应用极限事件速度修正（肾上腺素加速/战术失误减速）
        double speedMod = extremeEventManager.getSpeedModifier(entity);
        if (speedMod != 1.0 && entity.getVelocity().lengthSquared() > 0.001) {
            entity.setVelocity(entity.getVelocity().multiply(speedMod));
        }

        if (!coverMovement.isBehindCover(entity, primaryTarget) && isOpenArea(entity)) {
            if (hpRatio < 0.7 && Math.random() < 0.15) {
                coverMovement.moveToNearestCover(entity, primaryTarget, 10);
                plugin.debug("[AI] " + entity.getName() + " 寻找掩体!");
            }
        }
    }

    private void executeTacticalAction(LivingEntity entity, Player target, UUID uuid,
                                       double hpRatio, double distance, double exposure,
                                       boolean hasEyeLOS, boolean hasBodyLOS,
                                       com.emwbridge.ai.AIDecision decision, AimConvergenceManager.AimResult aim,
                                       TarkovTactics.TacticalAction action,
                                       com.emwbridge.ai.personality.PersonalityType personality) {

        switch (action) {
            case THROW_FRAG -> {
                if (throwableManager.throwFrag(entity, target)) {
                    plugin.debug("[AI] " + entity.getName() + " 投掷破片雷! dist=" + String.format("%.1f", distance));
                }
            }
            case THROW_FLASH -> {
                if (throwableManager.throwFlash(entity, target)) {
                    plugin.debug("[AI] " + entity.getName() + " 投掷闪光弹! dist=" + String.format("%.1f", distance));
                    flashNearbyAI(target.getLocation(), 8.0);
                }
            }
            case THROW_SMOKE -> {
                Location smokeLoc = entity.getLocation().clone().add(
                        target.getLocation().toVector()
                                .subtract(entity.getLocation().toVector())
                                .normalize().multiply(distance * 0.5));
                if (throwableManager.throwSmoke(entity, smokeLoc)) {
                    plugin.debug("[AI] " + entity.getName() + " 投掷烟雾弹! dist=" + String.format("%.1f", distance));
                }
            }
            case FLANK -> {
                AIState state = activeMobs.get(uuid);
                double progress = state != null ? Math.min(1.0, state.ticksSinceEngage / 400.0) : 0.5;
                coverMovement.moveFlanking(entity, target, progress);
            }
            case RETREAT -> {
                AIState retreatState = activeMobs.get(uuid);
                coverMovement.retreatTowardCover(entity, target);
                plugin.debug("[AI] " + entity.getName() + " 撤退! hpRatio=" + String.format("%.2f", hpRatio)
                        + " dist=" + String.format("%.1f", distance) + " tier=" + (retreatState != null ? retreatState.tier : "?"));
                if (hasBodyLOS && Math.random() < 0.3 && tactics.shouldShoot(uuid, hpRatio, exposure)) {
                    if (weaponManager.shoot(entity, aim.aimPoint, distance > hipfireRange)) {
                        tactics.recordShot(uuid);
                        plugin.debug("[AI] " + entity.getName() + " 撤退中射击! tier=" + (retreatState != null ? retreatState.tier : "?"));
                    }
                }
            }
            case RUSH -> {
                AIState rushState = activeMobs.get(uuid);
                coverMovement.rushToward(entity, target);
                plugin.debug("[AI] " + entity.getName() + " 冲锋! hpRatio=" + String.format("%.2f", hpRatio)
                        + " dist=" + String.format("%.1f", distance) + " tier=" + (rushState != null ? rushState.tier : "?"));
                if (hasBodyLOS && tactics.shouldShoot(uuid, hpRatio, exposure)) {
                    if (weaponManager.shoot(entity, aim.aimPoint, true)) {
                        tactics.recordShot(uuid);
                    }
                }
            }
            case HOLD -> {
                if (distance < meleeDistanceThreshold && hasBodyLOS) {
                    if (weaponManager.shoot(entity, target.getEyeLocation(), false)) {
                        tactics.recordShot(uuid);
                        plugin.debug("[AI] " + entity.getName() + " 近战射击! dist=" + String.format("%.1f", distance));
                    }
                    break;
                }

                if (decision == com.emwbridge.ai.AIDecision.ENGAGE || decision == com.emwbridge.ai.AIDecision.DEFEND) {
                    if (standAndShoot) {
                        coverMovement.standAndAim(entity, target);
                    }

                    var currentAlert = aiVisionManager.getAlertStage(uuid, target.getUniqueId());
                    if ((currentAlert == AlertStage.ORANGE || currentAlert == AlertStage.RED)
                            && hpRatio > 0.5 && !hasBodyLOS && hasEyeLOS) {
                        if (!tactics.isSuppressing(uuid)) {
                            tactics.enterSuppress(uuid);
                            plugin.debug("[AI] " + entity.getName() + " 开始压制射击!");
                        }
                    } else if (tactics.isSuppressing(uuid) && (hasBodyLOS || hpRatio < 0.3)) {
                        tactics.exitSuppress(uuid);
                    }

                    // 压制状态下允许无BodyLOS射击（朝目标大致方向）
                    boolean canShoot = weaponManager.hasWeapon(entity)
                            && (hasBodyLOS || tactics.isSuppressing(uuid))
                            && tactics.shouldShoot(uuid, hpRatio, exposure);
                    if (canShoot) {
                        double shootChance = personality.aggressiveness * 0.8;
                        if (currentAlert == AlertStage.RED) shootChance *= 1.5;
                        if (currentAlert == AlertStage.ORANGE) shootChance *= 1.2;
                        if (tactics.isSuppressing(uuid)) shootChance = 0.6;
                        // P0-3 修复：应用极限事件射速修正（恐慌模式/肾上腺素）
                        shootChance *= extremeEventManager.getFireRateModifier(entity);
                        if (Math.random() < shootChance) {
                            if (weaponManager.shoot(entity, aim.aimPoint, distance > hipfireRange)) {
                                tactics.recordShot(uuid);
                                plugin.debug("[AI] " + entity.getName() + " 射击! decision=" + decision
                                        + " dist=" + String.format("%.1f", distance)
                                        + " burst=" + tactics.getBurstCount(uuid)
                                        + " action=" + action);
                            } else {
                                if (!weaponManager.isReloading(entity) && weaponManager.isMagazineEmpty(entity)) {
                                    weaponManager.reload(entity);
                                }
                            }
                        }
                    }
                    if (Math.random() < repositionBetweenBursts) {
                        coverMovement.repositionAfterBurst(entity, target);
                    }
                } else if (decision == com.emwbridge.ai.AIDecision.AMBUSH) {
                    coverMovement.stopMoving(entity);
                    if (weaponManager.hasWeapon(entity) && hasEyeLOS && distance < 25
                            && tactics.shouldShoot(uuid, hpRatio, exposure)) {
                        if (weaponManager.shoot(entity, aim.aimPoint, true)) {
                            tactics.recordShot(uuid);
                            plugin.debug("[AI] " + entity.getName() + " 伏击射击! dist=" + String.format("%.1f", distance));
                        }
                    }
                }
            }
        }
    }

    private void handleNoTargetState(LivingEntity entity, AIState state) {
        UUID uuid = entity.getUniqueId();

        if (state.searchTicks > 0) {
            executeSearchState(entity, state);
            return;
        }

        UUID hatredTargetUuid = AlertStage.getHatredTarget(uuid);
        if (hatredTargetUuid != null) {
            Player hatredTarget = Bukkit.getPlayer(hatredTargetUuid);
            if (hatredTarget != null && hatredTarget.isOnline() && !hatredTarget.isDead()) {
                Location lastPos = AlertStage.getHatredLastPosition(uuid);
                if (lastPos != null) {
                    // 投影仇恨位置到实体地面高度，避免追空中坐标
                    Location groundPos = lastPos.clone();
                    if (groundPos.getY() - entity.getLocation().getY() > 3) {
                        groundPos.setY(entity.getLocation().getY());
                    }
                    double dist = safeDistance(entity.getLocation(), groundPos);
                    if (dist > 3) {
                        moveToward(entity, groundPos);
                    }
                    return;
                }
            }
        }

        executePatrolState(entity, state);
    }

    private void executeSearchState(LivingEntity entity, AIState state) {
        state.searchTicks--;

        if (state.lastKnownLocation != null) {
            double dist = safeDistance(entity.getLocation(), state.lastKnownLocation);
            if (dist > 2) {
                moveToward(entity, state.lastKnownLocation);
            } else {
                if (state.searchTicks % 20 == 0) {
                    double yaw = (Math.random() - 0.5) * 90;
                    entity.setRotation((float) (entity.getLocation().getYaw() + yaw), entity.getLocation().getPitch());
                }
            }
        }

        if (state.searchTicks <= 0) {
            state.lastKnownLocation = null;
        }
    }

    private void executePatrolState(LivingEntity entity, AIState state) {
        if (state.patrolTarget == null || state.patrolCooldown > 0) {
            state.patrolCooldown--;
            if (state.patrolCooldown <= 0 && Math.random() < patrolNewPointChance) {
                Location point = generatePatrolPoint(entity);
                if (point != null) {
                    state.patrolTarget = point;
                } else {
                    state.patrolCooldown = 20;
                }
            }
            return;
        }

        double dist = safeDistance(entity.getLocation(), state.patrolTarget);
        if (dist > 3) {
            moveToward(entity, state.patrolTarget);
        } else {
            state.patrolTarget = null;
            state.patrolCooldown = patrolStayMinTicks + (int) (Math.random() * (patrolStayMaxTicks - patrolStayMinTicks));
        }
    }

    private void parseStayTicks(String stayTicks) {
        if (stayTicks.contains("-")) {
            String[] parts = stayTicks.split("-");
            try {
                patrolStayMinTicks = Integer.parseInt(parts[0].trim());
                patrolStayMaxTicks = Integer.parseInt(parts[1].trim());
            } catch (Exception e) {
                plugin.getLogger().warning("无效的 patrol.stay-ticks 配置: " + stayTicks);
            }
        } else {
            try {
                patrolStayMinTicks = Integer.parseInt(stayTicks.trim());
                patrolStayMaxTicks = patrolStayMinTicks;
            } catch (Exception e) {
                plugin.getLogger().warning("无效的 patrol.stay-ticks 配置: " + stayTicks);
            }
        }
    }

    private Location generatePatrolPoint(LivingEntity entity) {
        Location current = entity.getLocation();
        double angle = Math.random() * Math.PI * 2;
        double radius = patrolMinRadius + Math.random() * (patrolMaxRadius - patrolMinRadius);
        double x = current.getX() + Math.cos(angle) * radius;
        double z = current.getZ() + Math.sin(angle) * radius;

        double y = current.getWorld().getHighestBlockYAt((int) x, (int) z);
        if (y < current.getY() - 3 || y > current.getY() + 10) {
            y = current.getY();
        }

        Location target = new Location(current.getWorld(), x, y, z);
        if (target.getBlock().getType().isSolid()) {
            target.add(0, 1, 0);
        }
        if (target.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
            return null;
        }

        return target;
    }

    private void moveToward(LivingEntity entity, Location target) {
        if (!(entity instanceof Mob mob)) return;
        // Y轴防飞：目标比实体高>3格则投影到实体地面高度
        Location adjusted = target.clone();
        if (adjusted.getY() - entity.getLocation().getY() > 3) {
            adjusted.setY(entity.getLocation().getY());
        }
        mob.getPathfinder().moveTo(adjusted, 1.0);
    }

    private boolean isOpenArea(LivingEntity entity) {
        Location loc = entity.getLocation();
        int solidCount = 0;
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dz = -5; dz <= 5; dz += 2) {
                Location check = loc.clone().add(dx, 1, dz);
                if (check.getBlock().getType().isSolid()) solidCount++;
            }
        }
        return solidCount <= 3;
    }

    private int countNearbyEnemies(LivingEntity entity, Player primaryTarget, double radius) {
        Location center = primaryTarget.getLocation();
        return (int) entity.getWorld().getPlayers().stream()
                .filter(p -> !p.isDead() && !p.equals(primaryTarget)
                        && safeDistance(p.getLocation(), center) < radius)
                .count();
    }

    private void flashNearbyAI(Location center, double radius) {
        center.getWorld().getEntitiesByClass(LivingEntity.class).stream()
                .filter(e -> e.hasMetadata("emwm_ai_enabled")
                        && safeDistance(e.getLocation(), center) < radius)
                .forEach(ai -> aiVisionManager.flashBlind(ai));
    }

    /**
     * 安全距离计算：自动处理跨世界和 null 情况
     * 不同世界或任意位置为 null 时返回 Double.MAX_VALUE
     */
    private double safeDistance(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return Double.MAX_VALUE;
        if (!a.getWorld().equals(b.getWorld())) return Double.MAX_VALUE;
        return a.distance(b);
    }

    // ============ 需求1.4-1.5：跨阵营 AI 目标选择（GreyZone 阵营战争）============

    /**
     * 在视野/听觉范围内寻找最近的敌对 AI 实体（依据 GreyZone 阵营关系）。
     * 仅当字符串阵营系统已配置（emwm_factions.yml）且自身已分配阵营时生效，否则返回 null。
     */
    private LivingEntity selectHostileAITarget(LivingEntity entity, double visionRange, double soundRange) {
        if (!factionManager.isConfigured()) return null;
        if (factionManager.getFactionId(entity.getUniqueId()) == null) return null;
        double maxRange = Math.max(visionRange, soundRange) + 20;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : entity.getWorld().getEntitiesByClass(LivingEntity.class)) {
            if (candidate == entity || candidate.isDead() || !candidate.hasMetadata("emwm_ai_enabled")) continue;
            if (factionManager.getFactionId(candidate.getUniqueId()) == null) continue;
            if (!factionManager.isHostile(entity, candidate)) continue;
            double d = safeDistance(entity.getLocation(), candidate.getLocation());
            if (d < maxRange && d < bestDist) {
                best = candidate;
                bestDist = d;
            }
        }
        return best;
    }

    /**
     * 对敌对 AI 目标的轻量交战：复用瞄准收敛与射击，暂不含玩家专属的掩体/投掷/极限事件战术。
     * 玩家交战路径（executeTacticalAction）保持不变。
     */
    private void engageAITarget(LivingEntity entity, LivingEntity target, AIState state, double hpRatio) {
        state.ticksSinceEngage++;
        double distance = safeDistance(entity.getLocation(), target.getLocation());
        // 轻量路径简化 LOS：近距离内视为有视线（详细通用射线检测留待后续增强）
        boolean hasBodyLOS = distance < 60;
        AimConvergenceManager.AimResult aim = aimConvergenceManager.update(
                entity, target, hasBodyLOS, hasBodyLOS, distance, state.tier, false);
        boolean ads = distance > hipfireRange;
        if (hasBodyLOS && tactics.shouldShoot(entity.getUniqueId(), hpRatio, 1.0)) {
            if (weaponManager.shoot(entity, aim.aimPoint, ads)) {
                tactics.recordShot(entity.getUniqueId());
            }
        }
    }

    public void registerMob(LivingEntity entity, String tier) {
        UUID uuid = entity.getUniqueId();
        AIState state = new AIState(tier);
        state.patrolCooldown = patrolStayMinTicks + (int) (Math.random() * (patrolStayMaxTicks - patrolStayMinTicks));
        activeMobs.put(uuid, state);
        aiVisionManager.registerMob(uuid);
        factionManager.assignByTier(uuid, tier);
        // 需求1 基础设施：若实体携带 emwm_faction 标签（由 EMWM 模板 faction 字段或外部设置），
        // 且 emwm_factions.yml 已配置该阵营，则启用 GreyZone 字符串阵营系统。
        List<org.bukkit.metadata.MetadataValue> factionMeta = entity.getMetadata("emwm_faction");
        if (!factionMeta.isEmpty() && factionManager.isConfigured()) {
            String fid = factionMeta.get(0).asString();
            if (fid != null && factionManager.getProfile(fid) != null) {
                factionManager.assignFactionId(uuid, fid);
            }
        }
        var personality = personalityManager.rollByTier(tier);
        // 需求4.2：若模板强制指定性格预设（emwm_personality_preset），则覆盖 tier 随机 roll
        List<org.bukkit.metadata.MetadataValue> presetMeta = entity.getMetadata("emwm_personality_preset");
        if (!presetMeta.isEmpty()) {
            com.emwbridge.ai.personality.PersonalityType preset =
                    personalityManager.resolvePreset(presetMeta.get(0).asString());
            if (preset != null) personality = preset;
        }
        // 需求4.1：读取 per-entity 撤退覆盖（来自 EMWMWeaponConfig 经 emwm_* 元数据注入）
        boolean neverRetreat = false;
        List<org.bukkit.metadata.MetadataValue> nrMeta = entity.getMetadata("emwm_never_retreat");
        if (!nrMeta.isEmpty()) neverRetreat = nrMeta.get(0).asBoolean();
        Double retreatHp = null;
        List<org.bukkit.metadata.MetadataValue> rhMeta = entity.getMetadata("emwm_retreat_hp");
        if (!rhMeta.isEmpty()) retreatHp = rhMeta.get(0).asDouble();
        personalityManager.assignPersonality(uuid, personality, neverRetreat, retreatHp);
        squadManager.tryJoin(entity, tier, personality);
        aimConvergenceManager.registerMob(uuid);
        aimConvergenceManager.setInitialDelayTicks(uuid, aimConvergenceManager.getInitialDelay(tier));
        tactics.registerMob(uuid);
        throwableManager.registerMob(uuid);
        entity.setMetadata("emwm_ai_enabled", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        // P0-7 修复：将 tier 持久化到 PDC，支持服务器重启后恢复 AI
        entity.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier);
    }

    public void unregisterMob(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        activeMobs.remove(uuid);
        cleanupMob(uuid);
        entity.removeMetadata("emwm_ai_enabled", plugin);
        // P0-7 修复：移除 PDC 标记
        entity.getPersistentDataContainer().remove(tierKey);
    }

    /**
     * P0-7 修复：扫描所有已加载的活体实体，恢复具有 emwm_tier_pdc 标记的 AI 状态。
     * 在 restart() 或服务器重启后调用，防止"幽灵怪"（有 PDC 标记但 AI 未注册）。
     *
     * @return 恢复的实体数量
     */
    public int recoverMobs() {
        int recovered = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (activeMobs.containsKey(entity.getUniqueId())) continue;
                if (!entity.isValid() || entity.isDead()) continue;
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                if (pdc.has(tierKey, PersistentDataType.STRING)) {
                    String tier = pdc.get(tierKey, PersistentDataType.STRING);
                    if (tier != null) {
                        registerMob(entity, tier);
                        recovered++;
                    }
                }
            }
        }
        if (recovered > 0) {
            plugin.getLogger().info("[EMWM] 恢复了 " + recovered + " 个 AI 实体（PDC 持久化恢复）");
        }
        return recovered;
    }

    public boolean isActive(LivingEntity entity) {
        return activeMobs.containsKey(entity.getUniqueId());
    }

    public int getActiveCount() {
        return activeMobs.size();
    }

    public AIVisionManager getAIVisionManager() { return aiVisionManager; }
    public FactionManager getFactionManager() { return factionManager; }
    public SquadManager getSquadManager() { return squadManager; }
    public SoundEventManager getSoundEventManager() { return soundEventManager; }

    public void shutdown() {
        stop();
        activeMobs.clear();
        soundEventManager.shutdown();
    }

    public boolean isUseEMEventDriven() {
        return useEMEventDriven;
    }

    public static class AIState {
        public String tier;
        public Player target;
        public int ticksSinceEngage;
        public int searchTicks;
        public Location lastKnownLocation;
        public Location patrolTarget;
        public int patrolCooldown;

        public AIState(String tier) {
            this.tier = tier;
        }
    }
}
