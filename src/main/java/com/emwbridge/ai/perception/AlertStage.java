package com.emwbridge.ai.perception;

import java.util.*;

/**
 * AI 三阶段警戒状态机 — 黄(YELLOW) → 橙(ORANGE) → 红(RED)
 * 
 * 状态流转规则：
 * - 曝光值驱动自动切换
 * - 声音事件可跳过 YELLOW 直接进入 ORANGE
 * - RED 绑定全局仇恨缓存：记录目标最后坐标，启动 15s 锁头倒计时
 */
public enum AlertStage {
    /** 黄 — 听见异响/余光瞥见，原地警戒、转头、不主动开火 */
    YELLOW,
    /** 橙 — 曝光过半，缓慢向可疑点位推进、找掩体架枪 */
    ORANGE,
    /** 红 — 曝光拉满，锁定目标、开火、持续追踪、15s锁头 */
    RED;

    /** 全局仇恨缓存: K=AI实体UUID V=在RED状态锁定的目标UUID(仅RED时记录) */
    private static final Map<UUID, HatredRecord> GLOBAL_HATRED = new HashMap<>();

    // ==================== 状态判定 ====================

    public static AlertStage fromExposure(double exposure) {
        if (exposure >= 35) return RED;
        if (exposure >= 20) return ORANGE;
        if (exposure > 0) return YELLOW;
        return null;
    }

    public static AlertStage transition(AlertStage current, double exposure, boolean soundTriggered) {
        AlertStage target = fromExposure(exposure);

        if (target == null) {
            return null;
        }

        if (soundTriggered && target == YELLOW) {
            target = ORANGE;
        }

        if (current != null && target != null && target.ordinal() < current.ordinal()) {
            if (current == RED) {
                if (exposure >= 25) return RED;
            } else if (current == ORANGE) {
                if (exposure >= 6) return ORANGE;
            }
        }

        return target;
    }

    public static AlertStage transitionWithProtection(AlertStage current, double exposure, 
                                                       boolean soundTriggered, UUID aiUuid) {
        AlertStage target = fromExposure(exposure);

        if (target == null) {
            return null;
        }

        if (soundTriggered && target == YELLOW) {
            target = ORANGE;
        }

        if (current != null && target != null && target.ordinal() < current.ordinal()) {
            if (current == RED) {
                if (exposure >= 25) return RED;
            } else if (current == ORANGE) {
                if (exposure >= 6) return ORANGE;
            }
        }

        return target;
    }

    // ==================== 全局仇恨缓存 ====================

    /** RED 状态进入时记录仇恨目标 */
    public static void recordHatred(UUID aiUuid, UUID targetUuid, org.bukkit.Location targetLoc, long currentTick) {
        GLOBAL_HATRED.put(aiUuid, new HatredRecord(targetUuid, targetLoc, currentTick));
    }

    /** 清除 AI 实体的仇恨记录（脱离 RED 时调用） */
    public static void clearHatred(UUID aiUuid) {
        GLOBAL_HATRED.remove(aiUuid);
    }

    /** 获取仇恨目标 UUID */
    public static UUID getHatredTarget(UUID aiUuid) {
        HatredRecord r = GLOBAL_HATRED.get(aiUuid);
        return r != null ? r.targetUuid : null;
    }

    /** 获取仇恨目标最后位置 */
    public static org.bukkit.Location getHatredLastPosition(UUID aiUuid) {
        HatredRecord r = GLOBAL_HATRED.get(aiUuid);
        return r != null ? r.lastPosition : null;
    }

    /** 检查锁头窗口是否有效（进入 RED 后 15s 内） */
    public static boolean isHeadshotWindow(UUID aiUuid, long currentTick) {
        HatredRecord r = GLOBAL_HATRED.get(aiUuid);
        if (r == null) return false;
        return (currentTick - r.redStartTick) <= 300; // 15s = 300 tick
    }

    /** 获取进入 RED 后的 tick 数（用于锁头收敛进度） */
    public static int getRedElapsedTicks(UUID aiUuid, long currentTick) {
        HatredRecord r = GLOBAL_HATRED.get(aiUuid);
        if (r == null) return 0;
        return (int) (currentTick - r.redStartTick);
    }

    // ==================== 行为判定 ====================

    public boolean canAttack() {
        return this == RED;
    }

    public boolean isAlerted() {
        return this == YELLOW || this == ORANGE || this == RED;
    }

    public boolean isHostile() {
        return this == RED;
    }

    // ==================== 仇恨记录 ====================

    public static class HatredRecord {
        public final UUID targetUuid;
        public final org.bukkit.Location lastPosition;
        public final long redStartTick;

        public HatredRecord(UUID targetUuid, org.bukkit.Location lastPosition, long redStartTick) {
            this.targetUuid = targetUuid;
            this.lastPosition = lastPosition.clone();
            this.redStartTick = redStartTick;
        }
    }
}
