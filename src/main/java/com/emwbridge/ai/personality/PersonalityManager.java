package com.emwbridge.ai.personality;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.AIDecision;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalityManager {

    private final EMWMBridge plugin;
    private final Random random = new Random();
    private final Map<UUID, PersonalityType> entityPersonalities = new ConcurrentHashMap<>();
    private Map<String, Map<PersonalityType, Double>> tierWeights = new HashMap<>();
    // 需求4：per-entity 撤退控制覆盖（来自 EMWMWeaponConfig，经 emwm_* 元数据注入）
    private final Map<UUID, Boolean> neverRetreatMap = new ConcurrentHashMap<>();
    private final Map<UUID, Double> retreatHpMap = new ConcurrentHashMap<>();
    // 需求4.2：命名性格预设（config.yml personality.presets.<name> → PersonalityType）
    private final Map<String, PersonalityType> presetTypes = new HashMap<>();

    public PersonalityManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration config) {
        tierWeights.clear();
        for (String tier : new String[]{"scav", "pmc", "boss", "cultist"}) {
            Map<PersonalityType, Double> weights = new EnumMap<>(PersonalityType.class);
            String path = "personality.tier-weights." + tier;
            if (config.contains(path)) {
                for (PersonalityType type : PersonalityType.values()) {
                    double weight = config.getDouble(path + "." + type.name(), 0);
                    if (weight > 0) weights.put(type, weight);
                }
            }
            tierWeights.put(tier, weights);
        }
        // 需求4.2：解析命名性格预设
        presetTypes.clear();
        String presetPath = "personality.presets";
        if (config.contains(presetPath)) {
            for (String name : config.getConfigurationSection(presetPath).getKeys(false)) {
                String typeName = config.getString(presetPath + "." + name);
                if (typeName == null) continue;
                try {
                    PersonalityType type = PersonalityType.valueOf(typeName.trim().toUpperCase());
                    presetTypes.put(name, type);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[EMWM] 无效性格预设 " + name + " = " + typeName
                            + "（合法值: " + java.util.Arrays.toString(PersonalityType.values()) + "）");
                }
            }
        }
    }

    public PersonalityType rollByTier(String tier) {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (PersonalityType type : PersonalityType.values()) {
            double weight = getWeight(tier, type);
            cumulative += weight;
            if (roll < cumulative) return type;
        }
        return PersonalityType.CAUTIOUS;
    }

    private double getWeight(String tier, PersonalityType type) {
        Map<PersonalityType, Double> weights = tierWeights.get(tier.toLowerCase());
        if (weights != null) {
            Double w = weights.get(type);
            if (w != null) return w;
        }
        return switch (tier.toLowerCase()) {
            case "scav" -> switch (type) {
                case COWARD -> 0.35; case CAUTIOUS -> 0.30;
                case LOOTER -> 0.25; case RECKLESS -> 0.10;
                default -> 0;
            };
            case "pmc" -> switch (type) {
                case CAUTIOUS -> 0.30; case FLANKER -> 0.20;
                case CAPTAIN -> 0.15; case SUPPRESSOR -> 0.15;
                case AMBUSH -> 0.10; case RECKLESS -> 0.10;
                default -> 0;
            };
            case "boss" -> switch (type) {
                case CAPTAIN -> 0.40; case RECKLESS -> 0.25;
                case SUPPRESSOR -> 0.20; case FLANKER -> 0.15;
                default -> 0;
            };
            case "cultist" -> switch (type) {
                case AMBUSH -> 0.40; case FLANKER -> 0.30;
                case CAUTIOUS -> 0.30;
                default -> 0;
            };
            default -> 0;
        };
    }

    public void assignPersonality(UUID entityUuid, PersonalityType personality) {
        assignPersonality(entityUuid, personality, false, null);
    }

    /**
     * 需求4：分配性格并注入 per-entity 撤退覆盖。
     *
     * @param neverRetreat    true=永不撤退（忽略任何撤退阈值）
     * @param retreatHpThreshold 撤退血量阈值覆盖（来自 EMWMWeaponConfig.retreatHpThreshold），null=使用历史 0.15 兜底
     */
    public void assignPersonality(UUID entityUuid, PersonalityType personality,
                                   boolean neverRetreat, Double retreatHpThreshold) {
        entityPersonalities.put(entityUuid, personality);
        neverRetreatMap.put(entityUuid, neverRetreat);
        if (retreatHpThreshold != null) {
            retreatHpMap.put(entityUuid, retreatHpThreshold);
        } else {
            retreatHpMap.remove(entityUuid);
        }
    }

    /**
     * 需求4.2：解析命名性格预设，返回对应的 PersonalityType；不存在返回 null。
     */
    public PersonalityType resolvePreset(String name) {
        if (name == null) return null;
        return presetTypes.get(name);
    }

    public PersonalityType getPersonality(UUID entityUuid) {
        return entityPersonalities.getOrDefault(entityUuid, PersonalityType.CAUTIOUS);
    }

    public boolean isNeverRetreat(UUID entityUuid) {
        return neverRetreatMap.getOrDefault(entityUuid, false);
    }

    public Double getRetreatHpThreshold(UUID entityUuid) {
        return retreatHpMap.get(entityUuid);
    }

    public void removeEntity(UUID uuid) {
        entityPersonalities.remove(uuid);
        neverRetreatMap.remove(uuid);
        retreatHpMap.remove(uuid);
    }

    public AIDecision decide(UUID entityUuid, double hpRatio, double exposureValue) {
        PersonalityType personality = getPersonality(entityUuid);
        boolean neverRetreat = isNeverRetreat(entityUuid);

        // 需求4：撤退阈值。per-entity 覆盖（来自 EMWMWeaponConfig.retreatHpThreshold）优先；
        // 否则保留历史 0.15 兜底，确保未配置 retreatHpThreshold 的服务器行为零变化。
        double threshold = 0.15;
        Double override = retreatHpMap.get(entityUuid);
        if (override != null) threshold = override;

        if (!neverRetreat && hpRatio < threshold) return AIDecision.RETREAT;

        double attackWeight = personality.getAttackWeight(exposureValue);
        double defendWeight = personality.getDefendWeight(exposureValue);
        double ambushWeight = personality.getAmbushWeight(exposureValue);
        double retreatWeight = personality.getRetreatWeight(hpRatio);

        double max = Math.max(Math.max(attackWeight, defendWeight),
                              Math.max(ambushWeight, retreatWeight));
        if (!neverRetreat && retreatWeight >= max) return AIDecision.RETREAT;
        if (ambushWeight >= max) return AIDecision.AMBUSH;
        if (defendWeight >= max) return AIDecision.DEFEND;
        return AIDecision.ENGAGE;
    }
}
