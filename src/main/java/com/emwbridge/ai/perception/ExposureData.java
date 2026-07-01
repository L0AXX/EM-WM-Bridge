package com.emwbridge.ai.perception;

import org.bukkit.Location;

/**
 * 曝光值实体 — 替代裸 double，携带完整状态
 * 0~100 浮点进度，附带最后已知位置、锁定时间、局部暴露信息
 */
public class ExposureData {

    /** 当前累积曝光值 (0.0 ~ 100.0) */
    private double value;

    /** 最后目击目标位置 */
    private Location lastKnownPosition;

    /** 进入 ALERT 阶段的 tick 时间戳 */
    private long alertStartTick;

    /** 进入 HOSTILE 阶段时目标所在位置 (锁头倒计时起点) */
    private Location hostileLockPosition;

    /** HOSTILE 锁定持续 tick 数 */
    private int hostileLockTicks;

    /** 身体可见部位 (位掩码: 1=头 2=躯干 4=左臂 8=右臂 16=左腿 32=右腿) */
    private int visibleBodyParts;

    /** 是否被闪光弹致盲 (致盲时清零且锁定) */
    private boolean flashBlinded;

    /** 致盲剩余 tick */
    private int flashBlindRemainingTicks;

    public ExposureData() {
        this.value = 0.0;
        this.alertStartTick = -1;
        this.hostileLockTicks = 0;
        this.visibleBodyParts = 0;
        this.flashBlinded = false;
        this.flashBlindRemainingTicks = 0;
    }

    // ==================== 值操作 ====================

    public void addExposure(double increment) {
        if (flashBlinded && increment > 0) return; // 致盲期间不接受增量
        this.value = Math.min(100.0, this.value + increment);
    }

    public void decayExposure(double decayRate) {
        if (flashBlinded && flashBlindRemainingTicks <= 0) {
            flashBlinded = false;
        }
        this.value = Math.max(0.0, this.value - decayRate);
    }

    public double getValue() { return value; }

    public void setValue(double val) { this.value = Math.max(0.0, Math.min(100.0, val)); }

    // ==================== 位置追踪 ====================

    public void updatePosition(Location loc) {
        this.lastKnownPosition = loc.clone();
    }

    public Location getLastKnownPosition() { return lastKnownPosition; }

    // ==================== 警觉时间线 ====================

    public void markAlertStart(long currentTick) {
        if (alertStartTick < 0) this.alertStartTick = currentTick;
    }

    public long getAlertStartTick() { return alertStartTick; }

    public void resetAlertTimeline() {
        this.alertStartTick = -1;
        this.hostileLockTicks = 0;
        this.hostileLockPosition = null;
    }

    // ==================== HOSTILE 锁头 ====================

    /** 记录进入 HOSTILE 时的目标位置（15s 锁头倒计时起点） */
    public void lockHostileTarget(Location targetPos, long currentTick) {
        this.hostileLockPosition = targetPos.clone();
        this.hostileLockTicks = 0;
    }

    /** 递增锁头计数，返回是否还在锁头窗口内 */
    public boolean tickHostileLock() {
        if (hostileLockTicks < 0) return false;
        hostileLockTicks++;
        return hostileLockTicks <= 300; // 15秒 = 300 tick
    }

    public Location getHostileLockPosition() { return hostileLockPosition; }
    public int getHostileLockTicks() { return hostileLockTicks; }

    // ==================== 身体部位可见性 ====================

    public void setVisibleBodyParts(int parts) { this.visibleBodyParts = parts; }

    public int getVisibleBodyParts() { return visibleBodyParts; }

    public boolean isHeadVisible() { return (visibleBodyParts & 1) != 0; }
    public boolean isTorsoVisible() { return (visibleBodyParts & 2) != 0; }
    public boolean isAnyPartVisible() { return visibleBodyParts != 0; }

    /** 计算暴露度系数: 头部=全暴露，仅腿部=低暴露 */
    public double getExposeMultiplier() {
        if (visibleBodyParts == 0) return 0.3;    // 全遮挡 → 30%
        if (isHeadVisible() && isTorsoVisible()) return 1.0;
        if (isHeadVisible()) return 0.9;
        if (isTorsoVisible()) return 0.7;
        if ((visibleBodyParts & 0x3C) != 0) return 0.4; // 仅四肢
        return 0.5;
    }

    // ==================== 闪光弹致盲 ====================

    public void applyFlashBlind() {
        this.flashBlinded = true;
        this.flashBlindRemainingTicks = 120; // 6秒
        this.value = 0.0;                    // 清零曝光
        this.visibleBodyParts = 0;
    }

    public void tickFlashBlind() {
        if (flashBlindRemainingTicks > 0) {
            flashBlindRemainingTicks--;
            if (flashBlindRemainingTicks <= 0) {
                flashBlinded = false;
            }
        }
    }

    public boolean isFlashBlinded() { return flashBlinded; }

    // ==================== 工具 ====================

    public void reset() {
        this.value = 0.0;
        this.lastKnownPosition = null;
        this.alertStartTick = -1;
        this.hostileLockTicks = 0;
        this.hostileLockPosition = null;
        this.visibleBodyParts = 0;
        this.flashBlinded = false;
        this.flashBlindRemainingTicks = 0;
    }
}
