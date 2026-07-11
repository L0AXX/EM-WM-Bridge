package com.emwbridge.ai.faction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 单个阵营的定义（GreyZone 可配置字符串阵营系统）。
 * 关系矩阵来自 emwm_factions.yml，替代写死的 TarkovFaction 枚举。
 */
public class FactionProfile {

    private final String id;
    private final String display;
    private final Set<String> hostile = new HashSet<>();
    private final Set<String> ally = new HashSet<>();
    private final Set<String> neutral = new HashSet<>();

    public FactionProfile(String id, String display) {
        this.id = id;
        this.display = display != null ? display : id;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public void addHostile(String otherId) {
        if (otherId != null) hostile.add(otherId);
    }

    public void addAlly(String otherId) {
        if (otherId != null) ally.add(otherId);
    }

    public void addNeutral(String otherId) {
        if (otherId != null) neutral.add(otherId);
    }

    // ===================== 动态关系写回（据点易主 / 声望）=====================

    /** 只读视图，防止外部直接改内部集合。 */
    public Set<String> getHostile() {
        return Collections.unmodifiableSet(hostile);
    }

    public Set<String> getAlly() {
        return Collections.unmodifiableSet(ally);
    }

    public Set<String> getNeutral() {
        return Collections.unmodifiableSet(neutral);
    }

    /**
     * 从所有关系集合中移除 otherId（使其退回默认 HOSTILE 查询）。
     */
    public void clearRelationTo(String otherId) {
        if (otherId == null) return;
        hostile.remove(otherId);
        ally.remove(otherId);
        neutral.remove(otherId);
    }

    /**
     * 运行时重写本阵营对 otherId 的关系：先清除旧关系，再按 rel 归入对应集合。
     * HOSTILE→hostile / FRIENDLY→ally / NEUTRAL→neutral。
     * rel 为 null 等价于 clearRelationTo（即退回未定义→查询默认 HOSTILE）。
     */
    public void setRelationTo(String otherId, HostilityMatrix.Relation rel) {
        if (otherId == null) return;
        clearRelationTo(otherId);
        if (rel == null) return;
        switch (rel) {
            case HOSTILE -> hostile.add(otherId);
            case FRIENDLY -> ally.add(otherId);
            case NEUTRAL -> neutral.add(otherId);
        }
    }

    /**
     * 返回本阵营对 otherId 的关系。
     * 同阵营=FRIENDLY；列出的 hostile/ally/neutral 优先；未定义默认 HOSTILE。
     */
    public HostilityMatrix.Relation getRelationTo(String otherId) {
        if (otherId == null) return HostilityMatrix.Relation.HOSTILE;
        if (id.equals(otherId)) return HostilityMatrix.Relation.FRIENDLY;
        if (hostile.contains(otherId)) return HostilityMatrix.Relation.HOSTILE;
        if (ally.contains(otherId)) return HostilityMatrix.Relation.FRIENDLY;
        if (neutral.contains(otherId)) return HostilityMatrix.Relation.NEUTRAL;
        return HostilityMatrix.Relation.HOSTILE;
    }
}
