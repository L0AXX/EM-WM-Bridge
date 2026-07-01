package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMConfigCache;
import com.emwbridge.config.EMWMWeaponConfig;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

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

        // 阶段2：回退到tier-based逻辑
        boolean hasEliteMobsMeta = entity.hasMetadata("elitemobs");
        boolean hasTarkovTier = entity.hasMetadata("tarkov_tier");
        plugin.debug("Metadata检查 - elitemobs: " + hasEliteMobsMeta + " | tarkov_tier: " + hasTarkovTier);

        String tier = detectTarkovMobTier(entity);
        plugin.getLogger().info("§b[EMWM] §f阶段2 tier检测: " + tier + " | elitemobsMeta=" + hasEliteMobsMeta);

        if (tier != null) {
            plugin.getLogger().info("§b[EMWM] §a直接绑定: tier=" + tier);
            bindAndConfigure(entity, tier);
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

                // 回退tier
                String delayedTier = detectTarkovMobTier(entity);
                plugin.getLogger().info("§b[EMWM] §e延迟重检结果: tier=" + delayedTier
                        + " name=" + entity.getCustomName()
                        + " meta=" + hasEliteMobsMeta);
                if (delayedTier != null) {
                    bindAndConfigure(entity, delayedTier);
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
        if (customName == null) return null;

        // 尝试匹配配置文件名
        String matchedFileName = configCache.matchConfigByName(customName);
        if (matchedFileName != null) {
            EMWMWeaponConfig config = configCache.getConfig(matchedFileName);
            if (config != null) {
                plugin.debug("[EMWM] 匹配到配置: " + matchedFileName + " → " + config.getWeaponPool());
                return config;
            }
        }

        // 尝试通过tier匹配模板
        String tier = detectTarkovMobTier(entity);
        if (tier != null && configCache.hasConfig(tier)) {
            EMWMWeaponConfig config = configCache.getConfig(tier);
            if (config != null) {
                plugin.debug("[EMWM] 通过tier匹配模板: " + tier + " → " + config.getWeaponPool());
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

        plugin.debug("[EMWM] 准备绑定武器: " + weapon + " | 配置: " + config);

        // 绑定武器（使用配置参数）
        boolean bound = weaponManager.bindWeaponWithConfig(entity, weapon, config);

        if (!bound) {
            plugin.getLogger().warning("[EMWM] 武器绑定失败: " + entity.getType() + " | 武器: " + weapon);
            return;
        }

        // 获取tier（用于AI注册）
        String tier = detectTarkovMobTier(entity);
        if (tier == null) {
            tier = "scav"; // 默认tier
        }

        // 设置metadata标记（与旧逻辑兼容）
        entity.setMetadata("emwm_weapon", new FixedMetadataValue(plugin, weapon));
        entity.setMetadata("emwm_tier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("emwm_config_loaded", new FixedMetadataValue(plugin, true));
        entity.setMetadata("emwm_combat_state", new FixedMetadataValue(plugin, "patrol"));
        entity.setMetadata("emwm_last_shot", new FixedMetadataValue(plugin, 0L));
        entity.setMetadata("emwm_ai_enabled", new FixedMetadataValue(plugin, true));

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
                weaponManager.bindWeaponWithConfig(entity, weapon, config);
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
     * 使用tier绑定武器（旧逻辑，回退方案）
     */
    private void bindAndConfigure(LivingEntity entity, String tier) {
        String weapon = weaponManager.getRandomWeaponForTier(tier);
        double durabilityMultiplier = plugin.getConfig().getDouble("tier-settings." + tier + ".durability-multiplier", 1.0);
        plugin.debug("准备绑定武器: " + weapon + " | 耐久倍率: " + durabilityMultiplier);

        boolean bound = weaponManager.bindWeapon(entity, weapon, durabilityMultiplier);

        if (!bound) {
            plugin.getLogger().warning("武器绑定失败: " + entity.getType() + " | 武器: " + weapon + " | tier: " + tier);
            return;
        }

        entity.setMetadata("emwm_tier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("emwm_combat_state", new FixedMetadataValue(plugin, "patrol"));
        entity.setMetadata("emwm_last_shot", new FixedMetadataValue(plugin, 0L));
        entity.setMetadata("emwm_ai_enabled", new FixedMetadataValue(plugin, true));

        // 禁用原版近战
        disableMeleeAttack(entity);

        // 注册到AI引擎
        aiManager.registerMob(entity, tier);

        plugin.debug("精英怪生成绑定武器: " + entity.getType() + " | 武器: " + weapon + " | tier: " + tier);

        // 防EliteMobs覆盖武器（延迟20tick，同时检查主手实际物品）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!entity.isValid() || entity.isDead()) return;
            if (!weaponManager.hasWeapon(entity) || !isHoldingWeapon(entity, weapon)) {
                plugin.debug("[EMWM] EliteMobs 覆盖了主手武器，重新绑定: " + entity.getType());
                weaponManager.bindWeapon(entity, weapon, durabilityMultiplier);
            }
        }, 20L);
    }

    private void disableMeleeAttack(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        if (weaponManager.hasWeapon(entity)) {
            weaponManager.unbindWeapon(entity);
            plugin.debug("精英怪死亡，解绑武器: " + entity.getType());
            // 注销AI引擎
            aiManager.unregisterMob(entity);
        }
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