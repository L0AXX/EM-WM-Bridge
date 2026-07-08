package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import com.emwbridge.managers.MobWeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.MetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战术行为控制器 — 连发节奏 / 投掷物决策 / 压制模式 / 侧翼包抄 / 战术撤退
 * 
 * 参照 EFT 0.16 AI 战术特征实现：
 * - 点射/连发节奏：burstSize 发后强制冷却
 * - 投掷物决策：破片雷（目标掩体后/多人）、闪光弹（突入前）、烟雾弹（撤退/跨越开阔地）
 * - 压制射击：对掩体后目标持续低精度射击
 * - 侧翼包抄：长时间对峙后尝试侧射
 * - 战术撤退：低血量后退至掩体并投掷烟雾掩护
 */
public class TarkovTactics {

    private final EMWMBridge plugin;
    private final MobWeaponManager weaponManager;
    private final Map<UUID, TacticalState> states = new ConcurrentHashMap<>();

    // === 连发参数 ===
    private int burstSize = 5;
    private long burstCooldownMs = 1200;
    private double burstSizeHpRatioMultiplier = 1.5;

    // === 投掷物决策参数 ===
    private double fragOnCoverChance = 0.15;       // 目标掩体后时每tick投雷概率
    private double fragOnGroupChance = 0.20;       // 多人扎堆时投雷概率
    private double flashBeforeRushChance = 0.30;   // 冲锋前闪光概率
    private double smokeOnRetreatChance = 0.12;    // 撤退时烟雾概率
    private double smokeOnOpenAreaChance = 0.08;   // 开阔地烟雾概率

    // === 投掷物距离范围 ===
    private double fragMinDist = 5.0;               // 破片雷最小距离（太近炸到自己）
    private double fragMaxDist = 25.0;              // 破片雷最大距离（太远扔不到）
    private double flashMinDist = 3.0;              // 闪光弹最小距离
    private double flashMaxDist = 15.0;             // 闪光弹最大距离

    // === 最小交战时间（tick）后才开始扔雷 ===
    private int minEngageTicksBeforeGrenade = 20;

    // === 掩体判定稳定性：连续多少 tick 无 LOS 才算掩体后 ===
    private int coverConfirmTicks = 3;
    private final Map<UUID, Integer> behindCoverCounters = new HashMap<>();

    // === 压制参数 ===
    private double minHealthForSuppress = 0.3;
    private double suppressionSpreadMultiplier = 2.5;

    // === 战术决策参数 ===
    private int flankAfterTicks = 200;
    private double flankChance = 0.15;
    private double retreatHpThreshold = 0.25;
    private double rushHpThreshold = 0.7;
    private double rushDistThreshold = 12.0;

    public TarkovTactics(EMWMBridge plugin, MobWeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
    }

    public void reload(FileConfiguration config) {
        burstSize = config.getInt("tactics.burst-size", 5);
        burstCooldownMs = config.getLong("tactics.burst-cooldown-ms", 1200);
        burstSizeHpRatioMultiplier = config.getDouble("tactics.burst-size-hp-ratio-multiplier", 1.5);

        fragOnCoverChance = config.getDouble("tactics.throwable.frag-on-cover-chance", 0.15);
        fragOnGroupChance = config.getDouble("tactics.throwable.frag-on-group-chance", 0.20);
        flashBeforeRushChance = config.getDouble("tactics.throwable.flash-before-rush-chance", 0.30);
        smokeOnRetreatChance = config.getDouble("tactics.throwable.smoke-on-retreat-chance", 0.12);
        smokeOnOpenAreaChance = config.getDouble("tactics.throwable.smoke-on-open-area-chance", 0.08);

        fragMinDist = config.getDouble("tactics.throwable.frag-min-dist", 5.0);
        fragMaxDist = config.getDouble("tactics.throwable.frag-max-dist", 25.0);
        flashMinDist = config.getDouble("tactics.throwable.flash-min-dist", 3.0);
        flashMaxDist = config.getDouble("tactics.throwable.flash-max-dist", 15.0);
        minEngageTicksBeforeGrenade = config.getInt("tactics.throwable.min-engage-ticks", 20);
        coverConfirmTicks = config.getInt("tactics.throwable.cover-confirm-ticks", 3);

        minHealthForSuppress = config.getDouble("tactics.suppression.min-health", 0.3);
        suppressionSpreadMultiplier = config.getDouble("tactics.suppression.spread-multiplier", 2.5);

        flankAfterTicks = config.getInt("tactics.flank.after-ticks", 200);
        flankChance = config.getDouble("tactics.flank.chance", 0.15);
        retreatHpThreshold = config.getDouble("tactics.retreat.hp-threshold", 0.25);
        rushHpThreshold = config.getDouble("tactics.rush.hp-threshold", 0.7);
        rushDistThreshold = config.getDouble("tactics.rush.dist-threshold", 12.0);

        plugin.debug("[战术] 投掷物决策=" + fragOnCoverChance + "/" + flashBeforeRushChance
                + "/" + smokeOnRetreatChance + " 侧翼=" + flankAfterTicks + "tick"
                + " 雷距=" + fragMinDist + "-" + fragMaxDist
                + " 掩体确认=" + coverConfirmTicks + "tick");
    }

    // ==================== 生命周期 ====================

    public void registerMob(UUID uuid) {
        states.put(uuid, new TacticalState());
    }

    public void unregisterMob(UUID uuid) {
        states.remove(uuid);
        behindCoverCounters.remove(uuid);
    }

    public TacticalState getState(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new TacticalState());
    }

    // ==================== 射击控制 ====================

    public boolean shouldShoot(UUID uuid, double hpRatio, double exposure) {
        TacticalState s = getState(uuid);
        long now = System.currentTimeMillis();

        if (s.lastShotTime == 0) return true;

        long sinceLastShot = now - s.lastShotTime;

        if (s.isSuppressing) return true;

        int effectiveBurstSize = (hpRatio < 0.3)
                ? (int) (burstSize * burstSizeHpRatioMultiplier)
                : burstSize;

        if (s.burstCount > 0 && s.burstCount < effectiveBurstSize) {
            long weaponCooldown = weaponManager.getFireRateMs(uuid);
            if (sinceLastShot >= (weaponCooldown > 0 ? weaponCooldown : 100)) {
                return true;
            }
            return false;
        }

        long sinceBurstStart = now - s.burstStartTime;
        long burstDuration = s.burstCount * weaponManager.getFireRateMs(uuid);
        long timeSinceBurstEnd = sinceBurstStart - burstDuration;

        // 压制状态下冷却时间减半，更积极开火
        long effectiveCooldown = s.isSuppressing ? burstCooldownMs / 2 : burstCooldownMs;

        if (s.burstCount >= effectiveBurstSize && timeSinceBurstEnd < effectiveCooldown) {
            return false;
        }

        if (s.burstCount >= effectiveBurstSize && timeSinceBurstEnd >= effectiveCooldown) {
            s.burstCount = 0;
            s.burstStartTime = 0;
        }

        return true;
    }

    public void recordShot(UUID uuid) {
        TacticalState s = getState(uuid);
        long now = System.currentTimeMillis();
        if (s.burstCount == 0) {
            s.burstStartTime = now;
        }
        s.burstCount++;
        s.lastShotTime = now;
    }

    // ==================== 压制模式 ====================

    public void enterSuppress(UUID uuid) {
        TacticalState s = getState(uuid);
        s.isSuppressing = true;
        s.burstCount = 0;
        s.burstStartTime = System.currentTimeMillis();
    }

    public void exitSuppress(UUID uuid) {
        TacticalState s = getState(uuid);
        s.isSuppressing = false;
        s.burstCount = 0;
        s.burstStartTime = 0;
    }

    public boolean isSuppressing(UUID uuid) {
        return getState(uuid).isSuppressing;
    }

    public double getSuppressionSpreadMultiplier() {
        return suppressionSpreadMultiplier;
    }

    // ==================== 投掷物决策 ====================

    /**
     * 综合战术决策 — 返回本 tick 应执行的战术动作
     */
    public TacticalAction decideTacticalAction(UUID uuid, double hpRatio, boolean targetBehindCoverNow,
                                                int nearbyEnemyCount, double distance, boolean isOpenArea,
                                                int ticksSinceEngage, String tier) {
        TacticalState s = getState(uuid);
        s.ticksSinceEngage = ticksSinceEngage;

        // === 稳定的掩体判定 ===
        // 使用计数器：必须连续 N tick 检测到 behindCover 才算
        int counter = behindCoverCounters.getOrDefault(uuid, 0);
        if (targetBehindCoverNow) {
            counter = Math.min(counter + 1, coverConfirmTicks + 5);
        } else {
            counter = 0;
        }
        behindCoverCounters.put(uuid, counter);
        boolean targetBehindCover = counter >= coverConfirmTicks;

        // === 投掷物冷却是否还在持续？=== 
        // 如果 grenadePrep 活跃，强制等待
        if (s.grenadePrepRemaining > 0) {
            s.grenadePrepRemaining--;
            return TacticalAction.HOLD; // 准备中，不射击不移动
        }

        // 低血量 → 撤退 + 烟雾（按 tier 读取撤退血量阈值）
        double effectiveRetreatHp = plugin.getConfig().getDouble(
                "tier-settings." + tier + ".tactical-retreat-hp", retreatHpThreshold);
        if (hpRatio < effectiveRetreatHp) {
            if (!s.fallbackSmokeUsed && Math.random() < smokeOnRetreatChance
                    && distance >= fragMinDist && distance <= fragMaxDist) {
                if (isGrenadeTypeAllowed(uuid, TacticalAction.THROW_SMOKE)) {
                    s.fallbackSmokeUsed = true;
                    s.grenadePrepRemaining = 10; // 扔后等待
                    return TacticalAction.THROW_SMOKE;
                }
            }
            return TacticalAction.RETREAT;
        }

        // 高血量 / 距离近 → 闪光 + 准备 + 冲刺
        if (hpRatio > rushHpThreshold && distance < rushDistThreshold && !s.flashUsed
                && distance >= flashMinDist && distance <= flashMaxDist
                && ticksSinceEngage >= minEngageTicksBeforeGrenade) {
            if (Math.random() < flashBeforeRushChance) {
                if (isGrenadeTypeAllowed(uuid, TacticalAction.THROW_FLASH)) {
                    s.flashUsed = true;
                    s.grenadePrepRemaining = 30; // 扔闪光后等 30 tick（约 1.5 秒）再冲
                    return TacticalAction.THROW_FLASH;
                }
            }
        }

        // 冲锋 (tier感知 + 掩体/开阔地检查)
        if (hpRatio > rushHpThreshold) {
            // 目标在掩体后或开阔地时不冲锋
            if (targetBehindCover || isOpenArea) {
                // 不冲锋，让后续逻辑决定动作
            } else {
                double tierRushDist = plugin.getConfig().getDouble(
                        "tier-settings." + tier + ".rush-dist-threshold", rushDistThreshold);
                if (distance < tierRushDist) {
                    return TacticalAction.RUSH;
                }
            }
        }

        // 最小交战时间保护：刚接战不要立马扔雷
        if (ticksSinceEngage < minEngageTicksBeforeGrenade) {
            return TacticalAction.HOLD;
        }

        // 目标掩体后 + 多人扎堆 → 破片雷
        if (targetBehindCover && nearbyEnemyCount >= 2 && !s.fragUsed
                && distance >= fragMinDist && distance <= fragMaxDist
                && Math.random() < fragOnGroupChance) {
            if (isGrenadeTypeAllowed(uuid, TacticalAction.THROW_FRAG)) {
                s.fragUsed = true;
                s.grenadePrepRemaining = 10;
                return TacticalAction.THROW_FRAG;
            }
        }

        // 目标掩体后单独 → 破片雷（较低概率，且已扔过不再扔）
        if (targetBehindCover && !s.fragUsed
                && distance >= fragMinDist && distance <= fragMaxDist
                && Math.random() < fragOnCoverChance) {
            if (isGrenadeTypeAllowed(uuid, TacticalAction.THROW_FRAG)) {
                s.fragUsed = true;
                s.grenadePrepRemaining = 10;
                return TacticalAction.THROW_FRAG;
            }
        }

        // 跨越开阔地 → 烟雾掩护
        if (isOpenArea && distance > 20 && !s.fallbackSmokeUsed
                && Math.random() < smokeOnOpenAreaChance) {
            if (isGrenadeTypeAllowed(uuid, TacticalAction.THROW_SMOKE)) {
                s.fallbackSmokeUsed = true;
                s.grenadePrepRemaining = 10;
                return TacticalAction.THROW_SMOKE;
            }
        }

        // 长时间对峙 → 侧翼包抄
        if (ticksSinceEngage > flankAfterTicks && Math.random() < flankChance) {
            return TacticalAction.FLANK;
        }

        // 正常交火 → 保持当前位置射击
        return TacticalAction.HOLD;
    }

    /**
     * 检查实体元数据手雷白名单，判断指定投掷动作是否被允许
     * 如果实体不存在/无metadata/Bukkit未初始化，默认允许（容错）
     *
     * @param uuid   实体UUID
     * @param action 战术动作（仅THROW_FRAG/THROW_FLASH/THROW_SMOKE有实际校验）
     * @return 是否允许投掷
     */
    private boolean isGrenadeTypeAllowed(UUID uuid, TacticalAction action) {
        try {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null) return true;

            List<MetadataValue> values = entity.getMetadata("emwm_allowed_grenade_types");
            if (values.isEmpty()) return true;

            String allowedStr = values.get(0).asString();
            Set<String> allowedSet = new HashSet<>(Arrays.asList(allowedStr.split(",")));

            String targetType;
            switch (action) {
                case THROW_FRAG:
                    targetType = "frag";
                    break;
                case THROW_FLASH:
                    targetType = "flashbang";
                    break;
                case THROW_SMOKE:
                    targetType = "smoke";
                    break;
                default:
                    return true;
            }

            return allowedSet.contains(targetType);
        } catch (Exception e) {
            // Bukkit未初始化（如单元测试环境），默认允许
            return true;
        }
    }

    /**
     * 投掷物是否可用 — 检查冷却
     */
    public boolean canUseThrowable(UUID uuid, ThrowableManager.ThrowableType type, ThrowableManager throwableManager) {
        return throwableManager.getCooldownRemaining(uuid, type) == 0;
    }

    // ==================== 查询方法 ====================

    public int getBurstCount(UUID uuid) {
        return getState(uuid).burstCount;
    }

    public long getLastShotTime(UUID uuid) {
        return getState(uuid).lastShotTime;
    }

    public int getTicksSinceEngage(UUID uuid) {
        return getState(uuid).ticksSinceEngage;
    }

    public void resetThrowableFlags(UUID uuid) {
        TacticalState s = getState(uuid);
        s.flashUsed = false;
        s.fragUsed = false;
        s.fallbackSmokeUsed = false;
        s.grenadePrepRemaining = 0;
    }

    // ==================== 战术动作枚举 ====================

    public enum TacticalAction {
        /** 保持当前位置射击 */
        HOLD,
        /** 投掷破片手雷 */
        THROW_FRAG,
        /** 投掷闪光弹 */
        THROW_FLASH,
        /** 投掷烟雾弹 */
        THROW_SMOKE,
        /** 侧翼包抄 */
        FLANK,
        /** 撤退 */
        RETREAT,
        /** 冲锋突进 */
        RUSH
    }

    // ==================== 内部状态 ====================

    public static class TacticalState {
        public long lastShotTime;
        public int burstCount;
        public long burstStartTime;
        public boolean isSuppressing;
        public int ticksSinceEngage;
        // 一次性标记 — 防止在同一交战中反复投掷
        public boolean flashUsed;
        public boolean fragUsed;
        public boolean fallbackSmokeUsed;
        // 投掷后准备等待时间 — 闪光弹等爆炸后才行动
        public int grenadePrepRemaining;
    }
}
