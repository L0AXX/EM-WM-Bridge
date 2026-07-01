package com.emwbridge.ai.perception;

import org.bukkit.Location;

/**
 * 声源实体 — 携带声音类型、位置、穿墙衰减信息
 */
public class SoundSource {

    private final Location sourceLoc;
    private final SoundType type;
    private final double baseRange;
    private final boolean penetrable;
    private final double rawLoudness; // 0~1 原始响度倍率

    /** 传播路径上的穿透方块数（运行时计算） */
    private int penetrationCount;

    public SoundSource(Location sourceLoc, SoundType type, double rawLoudness) {
        this.sourceLoc = sourceLoc.clone();
        this.type = type;
        this.baseRange = type.getBaseRange();
        this.penetrable = type.isPenetrable();
        this.rawLoudness = rawLoudness;
        this.penetrationCount = 0;
    }

    public Location getSourceLoc() { return sourceLoc; }
    public SoundType getType() { return type; }
    public double getBaseRange() { return baseRange; }
    public boolean isPenetrable() { return penetrable; }
    public double getRawLoudness() { return rawLoudness; }
    public int getPenetrationCount() { return penetrationCount; }
    public void setPenetrationCount(int count) { this.penetrationCount = count; }

    /**
     * 计算实际听觉暴露值
     * @param distance 听者到声源距离
     * @param headphoneMultiplier 耳机加成
     * @param tinnitusMultiplier 耳鸣衰减
     */
    public double calculateAuditoryExposure(double distance, double headphoneMultiplier, double tinnitusMultiplier) {
        if (distance > baseRange) return 0;

        // 距离线性衰减
        double distanceFactor = 1.0 - (distance / baseRange);

        // 墙体穿透衰减：每个方块衰减指定比例
        double penetrationFactor = 1.0;
        if (penetrable) {
            penetrationFactor = Math.pow(0.7, penetrationCount); // 每墙衰减30%
        } else if (penetrationCount > 0) {
            return 0; // 不可穿墙 + 有墙 = 听不见
        }

        double exposure = rawLoudness * distanceFactor * penetrationFactor * headphoneMultiplier * tinnitusMultiplier;
        return Math.max(0, Math.min(100.0, exposure * 100.0));
    }
}
