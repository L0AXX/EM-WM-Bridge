package com.emwbridge.ai.perception;

/**
 * 声音类型枚举 — 不同声源有不同传播距离和穿透能力
 */
public enum SoundType {
    /** 无消音器枪声 — 传播远、可穿墙 */
    GUNSHOT_UNSUPPRESSED(100.0, true),
    /** 消音超音速枪声 — 传播中远、可穿墙但衰减大 */
    GUNSHOT_SUPPRESSED_SUPERSONIC(60.0, true),
    /** 亚音速弹 — 传播近、难穿墙 */
    GUNSHOT_SUBSONIC(30.0, false),
    /** 冲刺脚步 */
    FOOTSTEP_SPRINT(30.0, false),
    /** 普通行走 */
    FOOTSTEP_WALK(18.0, false),
    /** 潜行/静步 */
    FOOTSTEP_SNEAK(6.0, false),
    /** 开门/关门 */
    DOOR(40.0, true),
    /** 投掷物撞击/滚动 */
    THROWABLE(25.0, false),
    /** 爆炸 */
    EXPLOSION(120.0, true);

    /** 基础传播距离（格） */
    private final double baseRange;
    /** 是否可穿透固体方块 */
    private final boolean penetrable;

    SoundType(double baseRange, boolean penetrable) {
        this.baseRange = baseRange;
        this.penetrable = penetrable;
    }

    public double getBaseRange() { return baseRange; }
    public boolean isPenetrable() { return penetrable; }
}
