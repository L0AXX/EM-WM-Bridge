package com.emwbridge.ai.squad;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.personality.PersonalityType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

public class SquadManager {

    private final EMWMBridge plugin;
    private final Map<UUID, UUID> entitySquadMap = new HashMap<>();
    private final Map<UUID, Squad> squads = new HashMap<>();
    private final Map<String, UUID> squadNameToId = new HashMap<>();

    // 需求2：命名编制配置（config.yml squad.squads.<name>）
    private final Map<String, Integer> namedSquadMaxSize = new HashMap<>();
    private final Map<String, Map<SquadRole, Integer>> squadRoleQuotas = new HashMap<>();

    private int maxSquadSize = 5;
    private double intelShareRange = 50.0;
    private boolean enabled = true;

    public SquadManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        enabled = config.getBoolean("squad.enabled", true);
        // 需求2.1：全局 max-size 放开（GreyZone 可设 ≥8），命名编制可覆盖
        maxSquadSize = config.getInt("squad.max-size", 5);
        intelShareRange = config.getDouble("squad.intel-share-range", 50.0);

        namedSquadMaxSize.clear();
        squadRoleQuotas.clear();
        if (config.contains("squad.squads")) {
            ConfigurationSection squadsSec = config.getConfigurationSection("squad.squads");
            if (squadsSec != null) {
                for (String name : squadsSec.getKeys(false)) {
                    ConfigurationSection s = squadsSec.getConfigurationSection(name);
                    if (s == null) continue;
                    if (s.contains("max-size")) {
                        namedSquadMaxSize.put(name, s.getInt("max-size"));
                    }
                    if (s.contains("roles")) {
                        ConfigurationSection roles = s.getConfigurationSection("roles");
                        Map<SquadRole, Integer> quota = new EnumMap<>(SquadRole.class);
                        if (roles != null) {
                            for (SquadRole role : SquadRole.values()) {
                                int count = roles.getInt(role.name(), 0);
                                if (count > 0) quota.put(role, count);
                            }
                        }
                        if (!quota.isEmpty()) squadRoleQuotas.put(name, quota);
                    }
                }
            }
        }
    }

    /**
     * 需求2.2：按距离自动编队（保留原有逻辑）。
     */
    public void tryJoin(LivingEntity entity, String tier, PersonalityType personality) {
        tryJoin(entity, tier, personality, null);
    }

    /**
     * 需求2.2/2.4：指定编制名时直接加入该命名编制（无视距离），否则走距离自动编队。
     * 命名编制满员则放弃加入（实体保持存活但不编队）。
     */
    public void tryJoin(LivingEntity entity, String tier, PersonalityType personality, String squadName) {
        if (!enabled) return;
        UUID entityUuid = entity.getUniqueId();

        if (squadName != null && !squadName.isEmpty()) {
            UUID squadId = squadNameToId.get(squadName);
            Squad squad = squadId != null ? squads.get(squadId) : null;
            if (squad == null) {
                squadId = UUID.randomUUID();
                squad = new Squad();
                squad.name = squadName;
                squad.maxSize = namedSquadMaxSize.getOrDefault(squadName, maxSquadSize);
                final Squad created = squad;
                if (squadRoleQuotas.containsKey(squadName)) {
                    created.remainingQuota = new EnumMap<>(SquadRole.class);
                    squadRoleQuotas.get(squadName).forEach((r, c) -> created.remainingQuota.put(r, c));
                }
                squads.put(squadId, squad);
                squadNameToId.put(squadName, squadId);
            }
            if (squad.memberUuids.size() >= squad.maxSize) return; // 编制满员
            squad.memberUuids.add(entityUuid);
            entitySquadMap.put(entityUuid, squadId);
            assignRole(squad, entityUuid, personality, tier);
            return;
        }

        for (Map.Entry<UUID, Squad> entry : squads.entrySet()) {
            Squad squad = entry.getValue();
            if (squad.memberUuids.size() >= squad.maxSize) continue;
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
        // 需求2.3 修复：新建编制的首名成员即队长（原三元表达式两分支相同，实为写死 bug）
        newSquad.captainUuid = entityUuid;
        newSquad.maxSize = maxSquadSize;
        newSquad.memberUuids.add(entityUuid);
        assignRole(newSquad, entityUuid, personality, tier);
        squads.put(squadId, newSquad);
        entitySquadMap.put(entityUuid, squadId);
    }

    /**
     * 需求2.3：按 roles 配额定角色；配额耗尽或无配额则随机。CAPTAIN 性格强制为队长（突击位）。
     */
    private void assignRole(Squad squad, UUID uuid, PersonalityType personality, String tier) {
        if (personality == PersonalityType.CAPTAIN) {
            squad.captainUuid = uuid;
            squad.roleAssignments.put(uuid, SquadRole.ASSAULT);
            return;
        }
        SquadRole chosen = pickRoleByQuota(squad);
        squad.roleAssignments.put(uuid, chosen);
    }

    private SquadRole pickRoleByQuota(Squad squad) {
        if (squad.remainingQuota != null) {
            for (Map.Entry<SquadRole, Integer> e : squad.remainingQuota.entrySet()) {
                if (e.getValue() > 0) {
                    e.setValue(e.getValue() - 1);
                    return e.getKey();
                }
            }
        }
        return SquadRole.values()[(int) (Math.random() * SquadRole.values().length)];
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
                    if (squad.name != null) squadNameToId.remove(squad.name);
                }
            }
        }
    }

    /** 需求2.1：返回命名编制的最大人数（含全局回退），供测试与调试 */
    public int getNamedSquadMaxSize(String name) {
        return namedSquadMaxSize.getOrDefault(name, maxSquadSize);
    }

    private static class Squad {
        String name;
        int maxSize = 5;
        Map<SquadRole, Integer> remainingQuota;
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
