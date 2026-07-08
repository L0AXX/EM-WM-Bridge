package com.emwbridge.ai.faction;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionManager {

    private final Map<UUID, TarkovFaction> entityFactions = new ConcurrentHashMap<>();
    private final HostilityMatrix matrix;

    public FactionManager() {
        this.matrix = new HostilityMatrix();
    }

    public void assignFaction(UUID entityUuid, TarkovFaction faction) {
        entityFactions.put(entityUuid, faction);
    }

    public TarkovFaction getFaction(UUID entityUuid) {
        return entityFactions.getOrDefault(entityUuid, TarkovFaction.AI_SCAV);
    }

    public void assignByTier(UUID entityUuid, String tier) {
        entityFactions.put(entityUuid, TarkovFaction.fromTier(tier));
    }

    public HostilityMatrix.Relation getRelation(LivingEntity self, LivingEntity target) {
        TarkovFaction selfFaction = getFaction(self.getUniqueId());
        TarkovFaction targetFaction = resolveTargetFaction(target);
        return matrix.getRelation(selfFaction, targetFaction);
    }

    private TarkovFaction resolveTargetFaction(LivingEntity target) {
        if (target instanceof Player) {
            if (target.hasPermission("emwm.scav")) {
                return TarkovFaction.PLAYER_SCAV;
            }
            return TarkovFaction.PLAYER_PMC;
        }
        return getFaction(target.getUniqueId());
    }

    public boolean shouldTurnHostile(LivingEntity self, LivingEntity target) {
        // P0-8 修复：跨世界安全距离检查
        if (self.getLocation().getWorld() != null && target.getLocation().getWorld() != null
                && self.getLocation().getWorld().equals(target.getLocation().getWorld())
                && self.getLocation().distance(target.getLocation()) < 3.0) return true;
        if (self.getLastDamageCause() != null
                && self.getLastDamageCause().getEntity() == target) return true;
        return false;
    }

    public void removeEntity(UUID uuid) {
        entityFactions.remove(uuid);
    }
}
