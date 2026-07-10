package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMConfigCache;
import com.emwbridge.config.EMWMWeaponConfig;
import com.emwbridge.events.EMWMKillEvent;
import com.emwbridge.loot.LootManager;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;

public class EliteMobSpawnListener implements Listener {

    private final EMWMBridge plugin;
    private final MobWeaponManager weaponManager;
    private final EMWMConfigCache configCache;
    private final TarkovAIManager aiManager;

    public EliteMobSpawnListener(EMWMBridge plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getMobWeaponManager();
        this.configCache = plugin.getEMWMConfigCache();
        this.aiManager = plugin.getTarkovAIManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity instanceof Mob)) return;

        // 可见日志：每次 spawn 都打印，不开 debug 也能看见
        plugin.getLogger().info("§b[EMWM] §f生物生成: " + entity.getType()
                + " | 名字: " + entity.getCustomName()
                + " | 原因: " + event.getSpawnReason());

        // 阶段1：优先从EMWM配置缓存读取
        EMWMWeaponConfig emwmConfig = tryGetEMWMConfig(entity);
        if (emwmConfig != null) {
            plugin.getLogger().info("§b[EMWM] §a匹配到EMWM配置: " + emwmConfig);
            bindWithEMWMConfig(entity, emwmConfig);
            return;
        }

        // 阶段2：回退到tier-based逻辑 — 统一走 EMWM 路径
        boolean hasEliteMobsMeta = entity.hasMetadata("elitemobs");
        String tier = detectTarkovMobTier(entity);
        plugin.getLogger().info("§b[EMWM] §f阶段2 tier检测: " + tier + " | elitemobsMeta=" + hasEliteMobsMeta);

        if (tier != null) {
            // 尝试通过 tier 匹配模板/配置
            EMWMWeaponConfig tierConfig = configCache.getConfig(tier);
            if (tierConfig != null) {
                plugin.getLogger().info("§b[EMWM] §a通过tier匹配到模板: " + tier);
                bindWithEMWMConfig(entity, tierConfig);
                return;
            }
            // 没有匹配的模板，用 config.yml 武器池构造一个 EMWMWeaponConfig
            plugin.getLogger().info("§b[EMWM] §e无模板匹配，用config.yml武器池构造配置: tier=" + tier);
            EMWMWeaponConfig fallbackConfig = buildFallbackConfig(tier);
            bindWithEMWMConfig(entity, fallbackConfig);
            return;
        }

        // 延迟重试（EliteMobs异步设名）
        boolean canRetry = hasEliteMobsMeta || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM;
        plugin.getLogger().info("§b[EMWM] §e阶段1+2均无结果，延迟重试条件: " + canRetry);
        if (canRetry) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!entity.isValid() || entity.isDead()) return;

                // 再次尝试EMWM配置
                EMWMWeaponConfig delayedConfig = tryGetEMWMConfig(entity);
                if (delayedConfig != null) {
                    plugin.getLogger().info("§b[EMWM] §a延迟重试匹配到配置: " + delayedConfig);
                    bindWithEMWMConfig(entity, delayedConfig);
                    return;
                }

                // 回退tier — 统一走 EMWM 路径
                String delayedTier = detectTarkovMobTier(entity);
                plugin.getLogger().info("§b[EMWM] §e延迟重检结果: tier=" + delayedTier
                        + " name=" + entity.getCustomName()
                        + " meta=" + hasEliteMobsMeta);
                if (delayedTier != null) {
                    EMWMWeaponConfig tierConfig = configCache.getConfig(delayedTier);
                    if (tierConfig != null) {
                        bindWithEMWMConfig(entity, tierConfig);
                    } else {
                        EMWMWeaponConfig fallbackConfig = buildFallbackConfig(delayedTier);
                        bindWithEMWMConfig(entity, fallbackConfig);
                    }
                } else {
                    plugin.debug("延迟重试仍无结果，跳过: " + entity.getType());
                }
            }, 5L);
        }
    }

    /**
     * 尝试从EMWM配置缓存获取怪物配置
     * 优先匹配怪物名称，其次匹配配置文件名
     */
    private EMWMWeaponConfig tryGetEMWMConfig(LivingEntity entity) {
        String customName = entity.getCustomName();

        // 尝试匹配配置文件名（需要 customName）
        if (customName != null) {
            String matchedFileName = configCache.matchConfigByName(customName);
            if (matchedFileName != null) {
                EMWMWeaponConfig config = configCache.getConfig(matchedFileName);
                if (config != null) {
                    plugin.debug("[EMWM] 名称匹配成功: " + matchedFileName + " → " + config.getWeaponPool());
                    return config;
                }
            }
        }

        // 尝试通过tier匹配模板（不需要 customName，靠 metadata/类型判断）
        String tier = detectTarkovMobTier(entity);
        if (tier != null && configCache.hasConfig(tier)) {
            EMWMWeaponConfig config = configCache.getConfig(tier);
            if (config != null) {
                plugin.debug("[EMWM] tier匹配成功: " + tier + " → " + config.getWeaponPool());
                return config;
            }
        }

        return null;
    }

    /**
     * 使用EMWM配置绑定武器（新逻辑）
     */
    private void bindWithEMWMConfig(LivingEntity entity, EMWMWeaponConfig config) {
        config.validate();

        String weapon = config.getRandomWeapon();
        if (weapon == null) {
            plugin.getLogger().warning("[EMWM] 武器池为空，跳过绑定: " + entity.getCustomName());
            return;
        }

        // 校验武器是否存在
        if (!weaponManager.weaponExists(weapon)) {
            plugin.getLogger().warning("[EMWM] 武器不存在: " + weapon + " | 怪物: " + entity.getCustomName());
            return;
        }

        // 获取tier（用于AI注册 + 耐久倍率）
        String tier = detectTarkovMobTier(entity);
        if (tier == null) {
            tier = "scav"; // 默认tier
        }

        // 耐久倍率（恢复 tier-settings.<tier>.durability-multiplier 功能，旧 bindAndConfigure 具备）
        double durabilityMultiplier = plugin.getConfig().getDouble("tier-settings." + tier + ".durability-multiplier", 1.0);

        plugin.debug("[EMWM] 准备绑定武器: " + weapon + " | 配置: " + config + " | 耐久倍率: " + durabilityMultiplier);

        // 绑定武器（使用配置参数 + 耐久倍率）
        boolean bound = weaponManager.bindWeaponWithConfig(entity, weapon, config, durabilityMultiplier);

        if (!bound) {
            plugin.getLogger().warning("[EMWM] 武器绑定失败: " + entity.getType() + " | 武器: " + weapon);
            return;
        }

        // 禁用原版近战AI（spawn时清除一次目标；AI引擎接管后每tick也会清）
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
        }

        // 设置metadata标记（与旧逻辑兼容）
        entity.setMetadata("emwm_weapon", new FixedMetadataValue(plugin, weapon));
        entity.setMetadata("emwm_tier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("emwm_config_loaded", new FixedMetadataValue(plugin, true));
        entity.setMetadata("emwm_combat_state", new FixedMetadataValue(plugin, "patrol"));
        entity.setMetadata("emwm_last_shot", new FixedMetadataValue(plugin, 0L));
        entity.setMetadata("emwm_ai_enabled", new FixedMetadataValue(plugin, true));

        // 需求1 基础设施：若 EMWM 模板指定了 GreyZone 阵营ID，写入 emwm_faction 标签，
        // 供 TarkovAIEngine.registerMob 启用字符串阵营系统。null 时不影响旧枚举回退。
        if (config.getFaction() != null) {
            entity.setMetadata("emwm_faction", new FixedMetadataValue(plugin, config.getFaction()));
        }

        // 需求4：性格与撤退控制标签（供 TarkovAIEngine.registerMob 注入 PersonalityManager）。
        // null 时不写入，引擎回退到 tier 随机 roll + 历史 0.15 撤退兜底（零破坏）。
        if (config.getPersonalityPreset() != null) {
            entity.setMetadata("emwm_personality_preset", new FixedMetadataValue(plugin, config.getPersonalityPreset()));
        }
        if (config.getNeverRetreat() != null) {
            entity.setMetadata("emwm_never_retreat", new FixedMetadataValue(plugin, config.getNeverRetreat()));
        }
        if (config.getRetreatHpThreshold() != null) {
            entity.setMetadata("emwm_retreat_hp", new FixedMetadataValue(plugin, config.getRetreatHpThreshold()));
        }

        // 需求2：指定编制名（供 TarkovAIEngine.registerMob 走命名编队，null 时按距离自动编队）
        if (config.getSquad() != null) {
            entity.setMetadata("emwm_squad", new FixedMetadataValue(plugin, config.getSquad()));
        }

        // 需求3：据点守卫行为参数（供 TarkovAIEngine.registerMob 捕获 home 与半径）
        if (config.getBehavior() != null) {
            entity.setMetadata("emwm_behavior", new FixedMetadataValue(plugin, config.getBehavior()));
            entity.setMetadata("emwm_aggro_radius", new FixedMetadataValue(plugin, config.getAggroRadiusOrDefault()));
            entity.setMetadata("emwm_leash_distance", new FixedMetadataValue(plugin, config.getLeashDistanceOrDefault()));
        }

        // 需求6：死亡掉落货币弹参数（供 onEntityDeath 构建掉落物）
        // 解析最终弹药类型：模板 lootAmmoType 优先，否则回退 gun→ammo 映射；两者皆空则不掉落
        // 守卫：lootManager 可能为 null（mock/部分初始化），则跳过掉落，不阻断绑定流程
        LootManager lootManager = plugin.getLootManager();
        if (lootManager != null) {
            String lootAmmoType = lootManager.resolveAmmoType(config, weapon);
            if (lootAmmoType != null) {
                entity.setMetadata("emwm_loot_ammo_type", new FixedMetadataValue(plugin, lootAmmoType));
                entity.setMetadata("emwm_loot_ammo_min", new FixedMetadataValue(plugin, config.getLootAmmoMinOrDefault()));
                entity.setMetadata("emwm_loot_ammo_max", new FixedMetadataValue(plugin, config.getLootAmmoMaxOrDefault()));
            }
        }

        // 存储配置参数供AI使用
        entity.setMetadata("emwm_fire_rate_ticks", new FixedMetadataValue(plugin, config.getFireRateTicks()));
        entity.setMetadata("emwm_max_range", new FixedMetadataValue(plugin, config.getMaxRange()));
        entity.setMetadata("emwm_melee_range", new FixedMetadataValue(plugin, config.getMeleeRange()));
        entity.setMetadata("emwm_spread", new FixedMetadataValue(plugin, config.getSpread()));
        entity.setMetadata("emwm_aggressiveness", new FixedMetadataValue(plugin, config.getAggressiveness()));

        // 注册到AI引擎（关键步骤！）
        aiManager.registerMob(entity, tier);

        plugin.debug("[EMWM] 精英怪生成绑定武器: " + entity.getType() + " | 武器: " + weapon + " | tier: " + tier + " | 配置: " + config);

        // 防EliteMobs覆盖武器（延迟20tick等EliteMobs配置完成，检查主手实际物品）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!entity.isValid() || entity.isDead()) return;
            if (!weaponManager.hasWeapon(entity) || !isHoldingWeapon(entity, weapon)) {
                plugin.debug("[EMWM] EliteMobs 覆盖了主手武器，重新绑定: " + entity.getType());
                weaponManager.bindWeaponWithConfig(entity, weapon, config, durabilityMultiplier);
            }
        }, 20L);
    }

    /**
     * 检查实体主手是否持有指定WM武器
     */
    private boolean isHoldingWeapon(LivingEntity entity, String weaponId) {
        ItemStack hand = entity.getEquipment() != null ? entity.getEquipment().getItemInMainHand() : null;
        if (hand == null || hand.getType() == org.bukkit.Material.AIR) return false;
        // 检查物品的显示名是否包含武器ID
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            return hand.getItemMeta().getDisplayName().contains(weaponId);
        }
        return false;
    }

    /**
     * 从 config.yml 武器池构造兜底 EMWMWeaponConfig
     * 统一绑定路径：即使没有 EMWM 配置/模板，也走 bindWithEMWMConfig
     * 这样所有怪物都有一致的 AI 元数据（aggressiveness/maxRange/spread 等）
     */
    private EMWMWeaponConfig buildFallbackConfig(String tier) {
        EMWMWeaponConfig config = new EMWMWeaponConfig();

        // 从 config.yml 读取武器池（null-safe：mock 或未配置时回退默认武器）
        List<String> rawPool = switch (tier.toLowerCase()) {
            case "scav" -> plugin.getConfig().getStringList("weapons.scav-pool");
            case "pmc" -> plugin.getConfig().getStringList("weapons.pmc-pool");
            case "boss" -> plugin.getConfig().getStringList("weapons.boss-pool");
            default -> null;
        };
        List<String> pool;
        if (rawPool != null && !rawPool.isEmpty()) {
            pool = rawPool;
        } else {
            // getString 在 mock/未配置时可能返回 null，需兜底非空武器名
            String defaultWeapon = plugin.getConfig().getString("weapons.default-weapon", "AK_47");
            if (defaultWeapon == null) defaultWeapon = "AK_47";
            pool = List.of(defaultWeapon);
        }
        config.setWeaponPool(pool);

        // tier-based 默认行为参数（确保 AI 元数据一致）
        double aggressiveness = switch (tier.toLowerCase()) {
            case "boss" -> 0.9;
            case "pmc" -> 0.7;
            default -> 0.5;
        };
        config.setAggressiveness(aggressiveness);

        int maxRange = switch (tier.toLowerCase()) {
            case "boss" -> 50;
            case "pmc" -> 40;
            default -> 30;
        };
        config.setMaxRange(maxRange);

        // 耐久倍率（从 config.yml 读取，传给 bindWeaponWithConfig 通过 WM 兜底）
        // 注意：bindWeaponWithConfig 不直接使用 durabilityMultiplier，
        // 但 WM 原生武器参数会通过 WeaponMetaCache 兜底

        plugin.debug("[EMWM] 构造兜底配置: tier=" + tier + " | 武器池=" + pool + " | aggressiveness=" + aggressiveness);
        return config;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        if (weaponManager.hasWeapon(entity)) {
            // 触发 EMWMKillEvent 供 Chemdah 等插件监听
            fireKillEvent(entity, event);

            // 需求6：死亡掉落货币弹（桥接层拦截死亡，注入货币弹 ItemStack，U4）
            String lootType = getMetaString(entity, "emwm_loot_ammo_type");
            if (lootType != null) {
                int lootMin = getMetaInt(entity, "emwm_loot_ammo_min", LootManager.DEFAULT_MIN);
                int lootMax = getMetaInt(entity, "emwm_loot_ammo_max", LootManager.DEFAULT_MAX);
                ItemStack drop = plugin.getLootManager().buildAmmoItem(lootType, lootMin, lootMax);
                if (drop != null) {
                    event.getDrops().add(drop);
                    plugin.debug("[EMWM] 死亡掉落货币弹: " + lootType + " x" + drop.getAmount());
                }
            }

            weaponManager.unbindWeapon(entity);
            plugin.debug("精英怪死亡，解绑武器: " + entity.getType());
            // 注销AI引擎
            aiManager.unregisterMob(entity);
        }
    }

    /**
     * 触发 EMWMKillEvent
     */
    private void fireKillEvent(LivingEntity entity, EntityDeathEvent event) {
        try {
            Player killer = entity.getKiller();

            // 读取伤害类型
            String damageTypeStr = getMetaString(entity, "emwm_last_damage_type");
            EMWMKillEvent.KillMethod killMethod;
            if (damageTypeStr != null) {
                try {
                    killMethod = EMWMKillEvent.KillMethod.valueOf(damageTypeStr);
                } catch (IllegalArgumentException e) {
                    killMethod = EMWMKillEvent.KillMethod.OTHER;
                }
            } else {
                // 没有记录伤害类型，根据最后的伤害原因推断
                if (entity.getLastDamageCause() != null) {
                    switch (entity.getLastDamageCause().getCause()) {
                        case PROJECTILE -> killMethod = EMWMKillEvent.KillMethod.GUN;
                        case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> killMethod = EMWMKillEvent.KillMethod.GRENADE;
                        case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> killMethod = EMWMKillEvent.KillMethod.MELEE;
                        default -> killMethod = EMWMKillEvent.KillMethod.OTHER;
                    }
                } else {
                    killMethod = EMWMKillEvent.KillMethod.OTHER;
                }
            }

            String tier = getMetaString(entity, "emwm_tier");
            String weapon = getMetaString(entity, "emwm_weapon");
            String combatState = getMetaString(entity, "emwm_combat_state");

            EMWMKillEvent killEvent = new EMWMKillEvent(entity, killer, killMethod, tier, weapon, combatState);
            Bukkit.getPluginManager().callEvent(killEvent);

            plugin.debug("[EMWM] 击杀事件: victim=" + entity.getName()
                    + " killer=" + (killer != null ? killer.getName() : "null")
                    + " method=" + killMethod
                    + " tier=" + tier);
        } catch (Exception e) {
            plugin.getLogger().warning("触发 EMWMKillEvent 失败: " + e.getMessage());
        }
    }

    private String getMetaString(LivingEntity entity, String key) {
        if (!entity.hasMetadata(key)) return null;
        return entity.getMetadata(key).stream()
                .findFirst()
                .map(v -> v.asString())
                .orElse(null);
    }

    private int getMetaInt(LivingEntity entity, String key, int fallback) {
        if (!entity.hasMetadata(key)) return fallback;
        return entity.getMetadata(key).stream()
                .findFirst()
                .map(v -> v.asInt())
                .orElse(fallback);
    }

    private String detectTarkovMobTier(LivingEntity entity) {
        if (entity.hasMetadata("tarkov_tier")) {
            return entity.getMetadata("tarkov_tier")
                    .stream()
                    .findFirst()
                    .map(v -> v.asString())
                    .orElse(null);
        }

        String customName = entity.getCustomName();
        if (customName != null) {
            String lower = customName.toLowerCase();
            if (lower.contains("boss") || lower.contains("legendary") || lower.contains("raid")
                    || lower.contains("reshala")) {
                return "boss";
            }
            if (lower.contains("pmc") || lower.contains("elite") || lower.contains("tier")) {
                return "pmc";
            }
            if (lower.contains("scav") || lower.contains("raider") || lower.contains("tarkov")
                    || lower.contains("拾荒者") || lower.contains("雇佣兵")) {
                return "scav";
            }
        }

        if (entity.hasMetadata("elitemobs")) {
            return "scav";
        }

        return null;
    }

}