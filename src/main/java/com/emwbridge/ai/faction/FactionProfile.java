package com.emwbridge.ai.faction;

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
