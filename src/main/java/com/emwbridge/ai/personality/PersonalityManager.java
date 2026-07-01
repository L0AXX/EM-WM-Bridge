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
        entityPersonalities.put(entityUuid, personality);
    }

    public PersonalityType getPersonality(UUID entityUuid) {
        return entityPersonalities.getOrDefault(entityUuid, PersonalityType.CAUTIOUS);
    }

    public void removeEntity(UUID uuid) {
        entityPersonalities.remove(uuid);
    }

    public AIDecision decide(UUID entityUuid, double hpRatio, double exposureValue) {
        PersonalityType personality = getPersonality(entityUuid);
        double attackWeight = personality.getAttackWeight(exposureValue);
        double defendWeight = personality.getDefendWeight(exposureValue);
        double ambushWeight = personality.getAmbushWeight(exposureValue);
        double retreatWeight = personality.getRetreatWeight(hpRatio);

        if (hpRatio < 0.15) return AIDecision.RETREAT;

        double max = Math.max(Math.max(attackWeight, defendWeight),
                              Math.max(ambushWeight, retreatWeight));
        if (retreatWeight >= max) return AIDecision.RETREAT;
        if (ambushWeight >= max) return AIDecision.AMBUSH;
        if (defendWeight >= max) return AIDecision.DEFEND;
        return AIDecision.ENGAGE;
    }
}
