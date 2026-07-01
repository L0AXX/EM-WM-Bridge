package com.emwbridge.ai.perception;

/**
 * 玩家姿态枚举 — 影响视觉暴露增速倍率
 */
public enum PostureType {
    STAND(1.0),
    SNEAK(0.6),
    CROUCH(0.5),
    SWIM(1.2);

    private final double exposureMultiplier;

    PostureType(double m) { this.exposureMultiplier = m; }

    public double getExposureMultiplier() { return exposureMultiplier; }

    public static PostureType fromPlayer(org.bukkit.entity.Player player) {
        if (player.isSneaking()) return SNEAK;
        if (player.isSwimming()) return SWIM;
        if (player.getPose() == org.bukkit.entity.Pose.SNEAKING) return CROUCH;
        return STAND;
    }
}
