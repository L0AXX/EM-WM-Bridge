package com.emwbridge.ai.squad;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.personality.PersonalityType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

public class SquadManager {

    private final EMWMBridge plugin;
    private final Map<UUID, UUID> entitySquadMap = new HashMap<>();
    private final Map<UUID, Squad> squads = new HashMap<>();

    private int maxSquadSize = 5;
    private double intelShareRange = 50.0;
    private boolean enabled = true;

    public SquadManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        enabled = config.getBoolean("squad.enabled", true);
        maxSquadSize = config.getInt("squad.max-size", 5);
        intelShareRange = config.getDouble("squad.intel-share-range", 50.0);
    }

    public void tryJoin(LivingEntity entity, String tier, PersonalityType personality) {
        if (!enabled) return;
        UUID entityUuid = entity.getUniqueId();

        for (Map.Entry<UUID, Squad> entry : squads.entrySet()) {
            Squad squad = entry.getValue();
            if (squad.memberUuids.size() >= maxSquadSize) continue;
            UUID sampleMember = squad.memberUuids.get(0);
            org.bukkit.entity.Entity memberEntity = Bukkit.getEntity(sampleMember);
            if (memberEntity == null) continue;
            if (memberEntity.getLocation().getWorld() == entity.getWorld()
                && memberEntity.getLocation().distance(entity.getLocation()) < 20.0) {
                entitySquadMap.put(entityUuid, entry.getKey());
                squad.memberUuids.add(entityUuid);
                assignRole(squad, entityUuid, personality, tier);
                return;
            }
        }

        UUID squadId = UUID.randomUUID();
        Squad newSquad = new Squad();
        newSquad.captainUuid = (personality == PersonalityType.CAPTAIN) ? entityUuid : entityUuid;
        newSquad.memberUuids.add(entityUuid);
        assignRole(newSquad, entityUuid, personality, tier);
        squads.put(squadId, newSquad);
        entitySquadMap.put(entityUuid, squadId);
    }

    private void assignRole(Squad squad, UUID uuid, PersonalityType personality, String tier) {
        if (personality == PersonalityType.CAPTAIN) {
            squad.captainUuid = uuid;
            squad.roleAssignments.put(uuid, SquadRole.ASSAULT);
            return;
        }
        SquadRole role = SquadRole.values()[(int)(Math.random() * SquadRole.values().length)];
        squad.roleAssignments.put(uuid, role);
    }

    public void shareIntel(UUID discovererUuid, Player target) {
        UUID squadId = entitySquadMap.get(discovererUuid);
        if (squadId == null) return;
        Squad squad = squads.get(squadId);
        if (squad == null) return;
        squad.sharedTarget = target;
        squad.sharedTargetLocation = target.getLocation().clone();
    }

    public Player getSharedTarget(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return null;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.sharedTarget : null;
    }

    public Location getSharedTargetLocation(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return null;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.sharedTargetLocation : null;
    }

    public SquadRole getRole(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return null;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.getRole(entityUuid) : null;
    }

    public boolean isCaptain(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return false;
        Squad squad = squads.get(squadId);
        return squad != null && entityUuid.equals(squad.captainUuid);
    }

    public int getSquadSize(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return 1;
        Squad squad = squads.get(squadId);
        return squad != null ? squad.memberUuids.size() : 1;
    }

    /** 获取同小队所有成员 UUID 列表（不含自身） */
    public List<UUID> getSquadMemberUuids(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return Collections.emptyList();
        Squad squad = squads.get(squadId);
        if (squad == null) return Collections.emptyList();
        List<UUID> result = new ArrayList<>(squad.memberUuids);
        result.remove(entityUuid);
        return result;
    }

    /** 获取完整小队成员列表（含自身） */
    public List<UUID> getSquad(UUID entityUuid) {
        UUID squadId = entitySquadMap.get(entityUuid);
        if (squadId == null) return List.of(entityUuid);
        Squad squad = squads.get(squadId);
        return squad != null ? List.copyOf(squad.memberUuids) : List.of(entityUuid);
    }

    /** 共享声音情报 — 记录声源位置供同小队参考 */
    public void shareSoundIntel(UUID discovererUuid, Location soundLocation, double exposure) {
        UUID squadId = entitySquadMap.get(discovererUuid);
        if (squadId == null) return;
        Squad squad = squads.get(squadId);
        if (squad == null) return;
        squad.sharedSoundLocation = soundLocation != null ? soundLocation.clone() : null;
        squad.sharedSoundTime = System.currentTimeMillis();
        squad.sharedSoundExposure = exposure;
    }

    public void removeEntity(UUID uuid) {
        UUID squadId = entitySquadMap.remove(uuid);
        if (squadId != null) {
            Squad squad = squads.get(squadId);
            if (squad != null) {
                squad.removeMember(uuid);
                if (squad.memberUuids.isEmpty()) {
                    squads.remove(squadId);
                }
            }
        }
    }

    private static class Squad {
        UUID captainUuid;
        final List<UUID> memberUuids = new ArrayList<>();
        final Map<UUID, SquadRole> roleAssignments = new HashMap<>();
        Player sharedTarget;
        Location sharedTargetLocation;
        Location sharedSoundLocation;
        long sharedSoundTime;
        double sharedSoundExposure;

        SquadRole getRole(UUID uuid) {
            return roleAssignments.get(uuid);
        }

        void removeMember(UUID uuid) {
            memberUuids.remove(uuid);
            roleAssignments.remove(uuid);
        }
    }
}
