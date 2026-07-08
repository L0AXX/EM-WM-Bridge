package com.emwbridge.ai.faction;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HostilityMatrix {

    public enum Relation {
        HOSTILE,
        NEUTRAL,
        FRIENDLY
    }

    private final Map<TarkovFaction, Map<TarkovFaction, Relation>> matrix;

    // P0-10 修复：缓存 SCAV↔PMC 随机敌对结果，避免同一 tick 内反复切换
    private volatile Relation cachedScavPmcRelation = null;
    private volatile long cacheExpiry = 0;
    private static final long CACHE_TTL_MS = 5000; // 5秒刷新一次

    // P0-10 修复：按实体对缓存敌对关系，确保同一对实体行为一致
    private static final long ENTITY_CACHE_TTL_MS = 10000; // 10秒

    public HostilityMatrix() {
        matrix = new EnumMap<>(TarkovFaction.class);
        initMatrix();
    }

    private void initMatrix() {
        setAll(TarkovFaction.AI_SCAV,
            Relation.HOSTILE, Relation.NEUTRAL, Relation.FRIENDLY,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE);
        setAll(TarkovFaction.AI_PMC,
            Relation.HOSTILE, Relation.NEUTRAL, Relation.HOSTILE,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE);
        setAll(TarkovFaction.BOSS,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE,
            Relation.HOSTILE, Relation.FRIENDLY, Relation.HOSTILE);
        setAll(TarkovFaction.CULTIST,
            Relation.HOSTILE, Relation.HOSTILE, Relation.HOSTILE,
            Relation.HOSTILE, Relation.HOSTILE, Relation.FRIENDLY);
    }

    private void setAll(TarkovFaction self, Relation r1, Relation r2, Relation r3,
                        Relation r4, Relation r5, Relation r6) {
        Map<TarkovFaction, Relation> row = new EnumMap<>(TarkovFaction.class);
        row.put(TarkovFaction.PLAYER_PMC, r1);
        row.put(TarkovFaction.PLAYER_SCAV, r2);
        row.put(TarkovFaction.AI_SCAV, r3);
        row.put(TarkovFaction.AI_PMC, r4);
        row.put(TarkovFaction.BOSS, r5);
        row.put(TarkovFaction.CULTIST, r6);
        matrix.put(self, row);
    }

    public Relation getRelation(TarkovFaction self, TarkovFaction target) {
        if (self == target) return Relation.FRIENDLY;
        Map<TarkovFaction, Relation> row = matrix.get(self);
        if (row == null) return Relation.HOSTILE;
        Relation base = row.get(target);
        if ((self == TarkovFaction.AI_SCAV && target == TarkovFaction.AI_PMC)
            || (self == TarkovFaction.AI_PMC && target == TarkovFaction.AI_SCAV)) {
            // P0-10 修复：使用 TTL 缓存，5秒内同一阵营对的关系保持一致
            long now = System.currentTimeMillis();
            if (cachedScavPmcRelation == null || now > cacheExpiry) {
                cachedScavPmcRelation = Math.random() < 0.8 ? Relation.HOSTILE : Relation.NEUTRAL;
                cacheExpiry = now + CACHE_TTL_MS;
            }
            return cachedScavPmcRelation;
        }
        return base != null ? base : Relation.HOSTILE;
    }

    /**
     * P0-10 修复：按实体对获取敌对关系，确保同一对实体在一段时间内行为一致
     */
    public Relation getRelation(TarkovFaction self, TarkovFaction target, UUID selfUuid, UUID targetUuid) {
        if (self == target) return Relation.FRIENDLY;
        if ((self == TarkovFaction.AI_SCAV && target == TarkovFaction.AI_PMC)
            || (self == TarkovFaction.AI_PMC && target == TarkovFaction.AI_SCAV)) {
            String key = selfUuid + ":" + targetUuid;
            long now = System.currentTimeMillis();
            CachedRelation cached = entityPairCacheMap.get(key);
            if (cached != null && now < cached.expiry) {
                return cached.relation;
            }
            Relation result = Math.random() < 0.8 ? Relation.HOSTILE : Relation.NEUTRAL;
            entityPairCacheMap.put(key, new CachedRelation(result, now + ENTITY_CACHE_TTL_MS));
            return result;
        }
        return getRelation(self, target);
    }

    private final Map<String, CachedRelation> entityPairCacheMap = new ConcurrentHashMap<>();

    private record CachedRelation(Relation relation, long expiry) {}
}
