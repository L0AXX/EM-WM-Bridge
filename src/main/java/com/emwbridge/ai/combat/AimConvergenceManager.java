package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 瞄准控制器 — 只决定AI"瞄哪里"，WM武器配置决定"打多准"
 * 
 * 职责：
 * - 可见时：瞄躯干/瞄头（简配，不加自定义散布）
 * - 不可见时：瞄身体+大偏移
 * - 被压制时：瞄身体+大偏移
 * - 初始延迟：不同tier有不同反应时间
 */
public class AimConvergenceManager {

    private final EMWMBridge plugin;
    private final Map<UUID, AimState> aimStates = new ConcurrentHashMap<>();
    private final Map<String, Double> initialDelays = new HashMap<>();

    // 反应延迟后，投弹到躯干的总时间（秒）
    private double bodyAimDelayBase = 1.5;
    // 瞄准躯干的概率（剩余概率瞄腿，极低概率瞄头）
    private double torsoAimChance = 0.75;
    // 无LOS/被压制时的随机偏移半径（格）
    private double blindAimDrift = 3.0;
    // 头部瞄准加成概率（近距离且目标未警觉时）
    private double headshotChanceClose = 0.15;

    public AimConvergenceManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        bodyAimDelayBase = config.getDouble("aim.body-aim-delay", 1.5);
        torsoAimChance = config.getDouble("aim.torso-aim-chance", 0.75);
        blindAimDrift = config.getDouble("aim.blind-aim-drift", 3.0);
        headshotChanceClose = config.getDouble("aim.headshot-chance-close", 0.15);
        initialDelays.clear();
        for (String tier : new String[]{"scav", "pmc", "cultist", "boss"}) {
            initialDelays.put(tier, config.getDouble("aim.initial-delay." + tier, 1.0));
        }
    }

    public double getInitialDelay(String tier) {
        Double delay = initialDelays.get(tier.toLowerCase());
        if (delay == null) delay = 1.0;
        return delay + (Math.random() - 0.5) * delay * 0.5;
    }

    /**
     * 每次射击前计算瞄准点
     * @param entity     射击者
     * @param target     目标
     * @param hasEyeLOS  是否能看到头部
     * @param hasBodyLOS 是否能看到身体
     * @param distance   距离
     * @param tier       tier类型
     * @param suppressing 是否在压制射击
     * @return 瞄准结果（包含目标Location，spreadRadius仅用于debug）
     */
    public AimResult update(LivingEntity entity, LivingEntity target,
                            boolean hasEyeLOS, boolean hasBodyLOS, double distance,
                            String tier, boolean suppressing) {
        UUID uuid = entity.getUniqueId();
        AimState state = aimStates.computeIfAbsent(uuid, k -> new AimState());

        // 初始延迟递减
        if (state.remainingDelayTicks > 0) {
            state.remainingDelayTicks--;
            // 延迟期间目标偏移，模拟反应时间
            Location drift = target.getLocation().clone().add(
                    (Math.random() - 0.5) * 2.0,
                    0,
                    (Math.random() - 0.5) * 2.0
            );
            return new AimResult(drift, 1.0);
        }

        boolean canSee = hasEyeLOS || hasBodyLOS;

        if (!canSee) {
            // 看不见 → 大范围猜射击
            Location blindGuess = target.getLocation().clone().add(
                    (Math.random() - 0.5) * blindAimDrift,
                    (Math.random() - 0.5) * blindAimDrift * 0.5,
                    (Math.random() - 0.5) * blindAimDrift
            );
            return new AimResult(blindGuess, 2.0);
        }

        if (suppressing) {
            // 压制射击 → 瞄方向但故意打偏
            return new AimResult(target.getLocation(), 1.0);
        }

        // 可见 → 选择瞄点（基于可见部位）
        Location aimPoint = chooseAimPoint(target, distance, hasEyeLOS, hasBodyLOS, state);

        return new AimResult(aimPoint, 0);
    }

    private Location chooseAimPoint(LivingEntity target, double distance, boolean hasEyeLOS, boolean hasBodyLOS, AimState state) {
        double roll = Math.random();

        // 只能看到头 → 强制瞄头
        if (hasEyeLOS && !hasBodyLOS) {
            state.headshotAttempts++;
            return target.getEyeLocation().clone().add(
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.15,
                    (Math.random() - 0.5) * 0.3
            );
        }

        // 只能看到身体（头被遮挡）→ 瞄躯干，不尝试爆头
        if (hasBodyLOS && !hasEyeLOS) {
            return target.getLocation().clone().add(0, 0.8, 0);
        }

        // 头和身体都可见 → 正常选择
        if (distance > 30) {
            // 远距：全瞄躯干
            return target.getLocation().clone().add(0, 0.6, 0);
        }

        if (distance < 10 && hasEyeLOS && roll < headshotChanceClose) {
            // 近距+有视线+小概率爆头
            state.headshotAttempts++;
            return target.getEyeLocation().clone().add(
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.15,
                    (Math.random() - 0.5) * 0.3
            );
        }

        // 默认：瞄躯干
        if (roll < torsoAimChance) {
            return target.getLocation().clone().add(0, 0.8, 0);
        }

        // 其他：瞄腿部
        return target.getLocation();
    }

    /**
     * 设置初始延迟
     */
    public void setInitialDelayTicks(UUID uuid, double delaySeconds) {
        AimState state = aimStates.get(uuid);
        if (state != null) {
            state.remainingDelayTicks = (int) (delaySeconds * 20.0);
            state.headshotAttempts = 0;
        }
    }

    // ==================== 生命周期 ====================

    public void registerMob(UUID uuid) {
        aimStates.put(uuid, new AimState());
    }

    public void unregisterMob(UUID uuid) {
        aimStates.remove(uuid);
    }

    // ==================== 内部类 ====================

    public static class AimResult {
        /** WM用来射击的目标位置 */
        public final Location aimPoint;
        /** 仅供debug/统计的散布半径（WM内部散布不受此影响） */
        public final double spreadRadius;

        public AimResult(Location aimPoint, double spreadRadius) {
            this.aimPoint = aimPoint;
            this.spreadRadius = spreadRadius;
        }
    }

    static class AimState {
        int remainingDelayTicks = 0;
        int headshotAttempts = 0;
    }
}