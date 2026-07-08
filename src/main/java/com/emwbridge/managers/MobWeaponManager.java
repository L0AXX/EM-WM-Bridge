package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMWeaponConfig;
import com.emwbridge.config.WeaponMetaCache;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobWeaponManager {

    private final EMWMBridge plugin;
    private final WeaponMetaCache weaponMetaCache;
    private final Map<UUID, MobWeaponInstance> weaponCache;

    private List<String> scavWeapons;
    private List<String> pmcWeapons;
    private List<String> bossWeapons;
    private String defaultWeapon;
    private boolean durabilityEnabled;
    private int baseDurability;
    private int decayPerShot;
    private double malfunctionThreshold;
    private double accuracyPenaltyPer10Percent;

    public MobWeaponManager(EMWMBridge plugin) {
        this.plugin = plugin;
        var configCache = plugin.getEMWMConfigCache();
        this.weaponMetaCache = (configCache != null) ? configCache.getWeaponMetaCache() : null;
        this.weaponCache = new ConcurrentHashMap<>();
    }

    public void reload() {
        var config = plugin.getConfig();
        this.defaultWeapon = config.getString("weapons.default-weapon", "AK_47");
        this.scavWeapons = config.getStringList("weapons.scav-pool");
        this.pmcWeapons = config.getStringList("weapons.pmc-pool");
        this.bossWeapons = config.getStringList("weapons.boss-pool");
        this.durabilityEnabled = config.getBoolean("durability.enabled", true);
        this.baseDurability = config.getInt("weapons.base-durability", 100);
        this.decayPerShot = config.getInt("durability.decay-per-shot", 1);
        this.malfunctionThreshold = config.getDouble("durability.malfunction-chance-threshold", 0.2);
        this.accuracyPenaltyPer10Percent = config.getDouble("durability.accuracy-penalty-per-10-percent", 0.02);

        if (scavWeapons.isEmpty()) scavWeapons = List.of(defaultWeapon);
        if (pmcWeapons.isEmpty()) pmcWeapons = List.of(defaultWeapon);
        if (bossWeapons.isEmpty()) bossWeapons = List.of(defaultWeapon);
    }

    public void shutdown() {
        weaponCache.clear();
    }

    public boolean bindWeapon(@NotNull LivingEntity entity, @NotNull String weaponTitle) {
        return bindWeapon(entity, weaponTitle, 1.0);
    }

    public boolean bindWeapon(@NotNull LivingEntity entity, @NotNull String weaponTitle, double durabilityMultiplier) {
        try {
            ItemStack weaponItem = WeaponMechanicsAPI.generateWeapon(weaponTitle);
            if (weaponItem == null || weaponItem.getType() == org.bukkit.Material.AIR) {
                plugin.getLogger().warning("[EMWM] 武器生成失败（不存在）: " + weaponTitle);
                return false;
            }
            org.bukkit.inventory.meta.ItemMeta meta = weaponItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§c" + weaponTitle);
                weaponItem.setItemMeta(meta);
            }
            EntityEquipment equipment = entity.getEquipment();
            if (equipment == null) return false;

            equipment.setItemInMainHand(weaponItem);

            int magSize = getMagazineSize(weaponTitle);
            int reloadTicks = getReloadDuration(weaponTitle);
            long fireRateMs = getFireRateMs(weaponTitle);
            double baseSpread = getBaseSpread(weaponTitle);
            double adsSpreadMult = getAdsSpreadMultiplier(weaponTitle);
            int maxDurability = (int) (baseDurability * durabilityMultiplier);

            MobWeaponInstance instance = new MobWeaponInstance(
                    weaponTitle,
                    maxDurability,
                    maxDurability,
                    false,
                    magSize,
                    magSize,
                    reloadTicks,
                    fireRateMs,
                    baseSpread,
                    adsSpreadMult
            );

            weaponCache.put(entity.getUniqueId(), instance);

            entity.setMetadata("emwm_weapon", new FixedMetadataValue(plugin, weaponTitle));
            entity.setMetadata("emwm_durability", new FixedMetadataValue(plugin, maxDurability));
            entity.setMetadata("emwm_ammo", new FixedMetadataValue(plugin, magSize));

            plugin.debug("绑定武器: " + entity.getName() + " -> " + weaponTitle +
                    " | 弹匣: " + magSize +
                    " | 换弹: " + reloadTicks + "tick" +
                    " | 射速: " + fireRateMs + "ms" +
                    " | 散射: " + String.format("%.1f", baseSpread) +
                    " | 耐久: " + maxDurability);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[EMWM] 绑定武器失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 使用EMWMWeaponConfig绑定武器（新逻辑）
     *
     * 参数优先级（从高到低）：
     * 1. 怪物emwm配置（显式填写的值，非null）
     * 2. 全局兵种模板配置
     * 3. WM武器原生参数（通过WeaponMechanics API读取）
     * 4. 安全兜底默认值
     *
     * @param entity 目标实体
     * @param weaponTitle 武器ID
     * @param config EMWM武器配置
     * @return 是否成功绑定
     */
    public boolean bindWeaponWithConfig(@NotNull LivingEntity entity, @NotNull String weaponTitle, @NotNull EMWMWeaponConfig config) {
        return bindWeaponWithConfig(entity, weaponTitle, config, 1.0);
    }

    /**
     * 使用EMWMWeaponConfig绑定武器（支持耐久倍率）
     *
     * @param durabilityMultiplier 耐久倍率（来自 config.yml tier-settings.<tier>.durability-multiplier）
     */
    public boolean bindWeaponWithConfig(@NotNull LivingEntity entity, @NotNull String weaponTitle, @NotNull EMWMWeaponConfig config, double durabilityMultiplier) {
        try {
            ItemStack weaponItem = WeaponMechanicsAPI.generateWeapon(weaponTitle);
            if (weaponItem == null || weaponItem.getType() == org.bukkit.Material.AIR) {
                plugin.getLogger().warning("[EMWM] 武器生成失败（不存在）: " + weaponTitle);
                return false;
            }
            org.bukkit.inventory.meta.ItemMeta meta = weaponItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§c" + weaponTitle);
                weaponItem.setItemMeta(meta);
            }
            EntityEquipment equipment = entity.getEquipment();
            if (equipment == null) return false;

            equipment.setItemInMainHand(weaponItem);

            // 三级参数优先级解析
            int magSize = resolveMagazineSize(weaponTitle, config);
            int reloadTicks = resolveReloadDuration(weaponTitle, config);
            long fireRateMs = resolveFireRateMs(weaponTitle, config);
            double baseSpread = resolveBaseSpread(weaponTitle, config);
            double adsSpreadMult = resolveAdsSpreadMultiplier(weaponTitle, config);
            int maxRange = resolveMaxRange(weaponTitle, config);
            int effectiveRange = resolveEffectiveRange(weaponTitle, config);
            int maxDurability = (int) (baseDurability * durabilityMultiplier);

            // 参数来源日志（debug模式）
            if (plugin != null) {
                StringBuilder sb = new StringBuilder("[EMWM] 参数来源: ");

                // magazineSize
                sb.append("magazineSize:").append(magSize).append("(");
                if (config.isFieldExplicitlySet("magazineSize")) sb.append("config");
                else if (config.getMagazineSize() != null) sb.append("template");
                else sb.append("WM");
                sb.append(") ");

                // reloadDuration
                sb.append("reloadTicks:").append(reloadTicks).append("(");
                if (config.isFieldExplicitlySet("reloadDuration")) sb.append("config");
                else if (config.getReloadDuration() != null) sb.append("template");
                else sb.append("WM");
                sb.append(") ");

                // fireRate
                sb.append("fireRateMs:").append(fireRateMs).append("(");
                if (config.isFieldExplicitlySet("fireRate")) sb.append("config");
                else if (config.getFireRate() != null) sb.append("template");
                else sb.append("WM");
                sb.append(") ");

                // spread
                sb.append("spread:").append(String.format("%.1f", baseSpread)).append("(");
                if (config.isFieldExplicitlySet("spread")) sb.append("config");
                else if (config.getSpread() != null) sb.append("template");
                else sb.append("WM");
                sb.append(") ");

                // maxRange
                sb.append("maxRange:").append(maxRange).append("(");
                if (config.isFieldExplicitlySet("maxRange")) sb.append("config");
                else if (config.getMaxRange() != null) sb.append("template");
                else sb.append("WM");
                sb.append(")");

                plugin.debug(sb.toString());
            }

            MobWeaponInstance instance = new MobWeaponInstance(
                    weaponTitle,
                    maxDurability,
                    maxDurability,
                    false,
                    magSize,
                    magSize,
                    reloadTicks,
                    fireRateMs,
                    baseSpread,
                    adsSpreadMult
            );

            instance.setConsumeAmmo(config.isConsumeAmmoOrDefault());

            weaponCache.put(entity.getUniqueId(), instance);

            entity.setMetadata("emwm_weapon", new FixedMetadataValue(plugin, weaponTitle));
            entity.setMetadata("emwm_durability", new FixedMetadataValue(plugin, maxDurability));
            entity.setMetadata("emwm_ammo", new FixedMetadataValue(plugin, magSize));

            // 存储EMWM配置参数
            entity.setMetadata("emwm_max_range", new FixedMetadataValue(plugin, maxRange));
            entity.setMetadata("emwm_effective_range", new FixedMetadataValue(plugin, effectiveRange));
            entity.setMetadata("emwm_melee_range", new FixedMetadataValue(plugin, config.getMeleeRangeOrDefault()));
            entity.setMetadata("emwm_aggressiveness", new FixedMetadataValue(plugin, config.getAggressivenessOrDefault()));
            entity.setMetadata("emwm_suppress_threshold", new FixedMetadataValue(plugin, config.getSuppressHpThresholdOrDefault()));
            entity.setMetadata("emwm_retreat_threshold", new FixedMetadataValue(plugin, config.getRetreatHpThresholdOrDefault()));
            entity.setMetadata("emwm_fire_rate_ticks", new FixedMetadataValue(plugin, fireRateMs / 50));

            // 写入手雷白名单到实体元数据（经过WM校验过滤）
            List<String> rawTypes = config.getAllowedGrenadeTypesOrDefault();
            List<String> validTypes = validateGrenadeTypesAgainstWM(rawTypes);
            entity.setMetadata("emwm_allowed_grenade_types", new FixedMetadataValue(plugin, String.join(",", validTypes)));
            if (validTypes.size() != rawTypes.size()) {
                plugin.debug("[EMWM] 手雷类型过滤后: " + validTypes + " (原始: " + rawTypes + ")");
            }

            plugin.debug("[EMWM] 绑定武器: " + entity.getName() + " -> " + weaponTitle +
                    " | 弹匣: " + magSize +
                    " | 换弹: " + reloadTicks + "tick" +
                    " | 射速: " + fireRateMs + "ms" +
                    " | 散射: " + String.format("%.1f", baseSpread) +
                    " | 有效射程: " + effectiveRange +
                    " | 最大射程: " + maxRange);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[EMWM] 绑定武器失败: " + e.getMessage());
            return false;
        }
    }

    private int resolveMagazineSize(String weaponTitle, EMWMWeaponConfig config) {
        if (config.getMagazineSize() != null) {
            return config.getMagazineSize();
        }
        return weaponMetaCache.resolveMagazineSize(config, null, weaponTitle);
    }

    private int resolveReloadDuration(String weaponTitle, EMWMWeaponConfig config) {
        if (config.getReloadDuration() != null) {
            return config.getReloadDuration();
        }
        return weaponMetaCache.resolveReloadDuration(config, null, weaponTitle);
    }

    private long resolveFireRateMs(String weaponTitle, EMWMWeaponConfig config) {
        if (config.getFireRate() != null) {
            return (long) (1000.0 / config.getFireRate());
        }
        return weaponMetaCache.resolveFireRateMs(config, null, weaponTitle);
    }

    private double resolveBaseSpread(String weaponTitle, EMWMWeaponConfig config) {
        if (config.getSpread() != null) {
            return config.getSpread();
        }
        return weaponMetaCache.resolveSpread(config, null, weaponTitle);
    }

    private double resolveAdsSpreadMultiplier(String weaponTitle, EMWMWeaponConfig config) {
        if (config.getAdsSpreadMultiplier() != null) {
            return config.getAdsSpreadMultiplier();
        }
        return weaponMetaCache.resolveAdsSpreadMultiplier(config, null, weaponTitle);
    }

    private int resolveMaxRange(String weaponTitle, EMWMWeaponConfig config) {
        if (config.getMaxRange() != null) {
            return config.getMaxRange();
        }
        return weaponMetaCache.resolveMaxRange(config, null, weaponTitle);
    }

    private int resolveEffectiveRange(String weaponTitle, EMWMWeaponConfig config) {
        if (config.getEffectiveRange() != null) {
            return config.getEffectiveRange();
        }
        return 25;
    }

    /**
     * 检查武器是否存在
     *
     * @param weaponTitle 武器ID
     * @return 是否存在
     */
    public boolean weaponExists(@NotNull String weaponTitle) {
        try {
            var config = me.deecaad.weaponmechanics.WeaponMechanics.getInstance().getWeaponConfigurations();
            return config.getString(weaponTitle + ".Info.Weapon_Type") != null;
        } catch (Throwable e) {
            // 尝试生成武器作为备用检查
            try {
                ItemStack testItem = WeaponMechanicsAPI.generateWeapon(weaponTitle);
                return testItem != null && testItem.getType() != org.bukkit.Material.AIR;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    /**
     * 校验手雷类型列表中哪些是WM的有效武器
     * 过滤无效WM武器ID，仅保留可用手雷类型
     *
     * @param grenadeTypes 待校验的手雷类型列表
     * @return 过滤后的有效手雷类型列表
     */
    public List<String> validateGrenadeTypesAgainstWM(List<String> grenadeTypes) {
        if (grenadeTypes == null || grenadeTypes.isEmpty()) return grenadeTypes;
        List<String> valid = new ArrayList<>();
        for (String type : grenadeTypes) {
            try {
                ItemStack testItem = WeaponMechanicsAPI.generateWeapon(type);
                if (testItem != null && testItem.getType() != org.bukkit.Material.AIR) {
                    valid.add(type);
                } else {
                    plugin.getLogger().warning("[EMWM] 手雷类型 \"" + type + "\" 在WM中不存在，已过滤");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[EMWM] 校验手雷类型 \"" + type + "\" 时出错: " + e.getMessage());
            }
        }
        return valid;
    }

    /**
     * @deprecated 已收拢至 WeaponMetaCache — 仅保留给旧 bindWeapon() 调用，新代码禁止直接调用
     */
    @Deprecated
    private int getMagazineSize(String weaponTitle) {
        try {
            var config = me.deecaad.weaponmechanics.WeaponMechanics.getInstance().getWeaponConfigurations();
            return config.getInt(weaponTitle + ".Reload.Magazine_Size", 30);
        } catch (Throwable e) {
            // 捕获 Throwable 而非 Exception：WeaponMechanics 类加载可能抛 NoClassDefFoundError
            // （测试环境或 WM 插件未加载时），返回默认值 30
            return 30;
        }
    }

    /**
     * @deprecated 已收拢至 WeaponMetaCache — 仅保留给旧 bindWeapon() 调用，新代码禁止直接调用
     */
    @Deprecated
    private int getReloadDuration(String weaponTitle) {
        try {
            var config = me.deecaad.weaponmechanics.WeaponMechanics.getInstance().getWeaponConfigurations();
            return config.getInt(weaponTitle + ".Reload.Reload_Duration", 60);
        } catch (Throwable e) {
            return 60;
        }
    }

    /**
     * @deprecated 已收拢至 WeaponMetaCache — 仅保留给旧 bindWeapon() 调用，新代码禁止直接调用
     */
    @Deprecated
    private long getFireRateMs(String weaponTitle) {
        try {
            var config = me.deecaad.weaponmechanics.WeaponMechanics.getInstance().getWeaponConfigurations();
            double shotsPerSecond = config.getDouble(weaponTitle + ".Shoot.Fully_Automatic_Shots_Per_Second", 0);
            if (shotsPerSecond > 0) {
                return (long) (1000.0 / shotsPerSecond);
            }
            int delayMs = config.getInt(weaponTitle + ".Shoot.Delay_Between_Shots", 0);
            if (delayMs > 0) {
                return delayMs;
            }
            double cooldownSec = config.getDouble(weaponTitle + ".Shoot.Cooldown", 0);
            if (cooldownSec > 0) {
                return (long) (cooldownSec * 1000);
            }
            return 300L;
        } catch (Throwable e) {
            return 300L;
        }
    }

    /**
     * @deprecated 已收拢至 WeaponMetaCache — 仅保留给旧 bindWeapon() 调用，新代码禁止直接调用
     */
    @Deprecated
    private double getBaseSpread(String weaponTitle) {
        try {
            var config = me.deecaad.weaponmechanics.WeaponMechanics.getInstance().getWeaponConfigurations();
            return config.getDouble(weaponTitle + ".Shoot.Spread.Base_Spread", 3.0);
        } catch (Throwable e) {
            return 3.0;
        }
    }

    /**
     * @deprecated 已收拢至 WeaponMetaCache — 仅保留给旧 bindWeapon() 调用，新代码禁止直接调用
     */
    @Deprecated
    private double getAdsSpreadMultiplier(String weaponTitle) {
        try {
            var config = me.deecaad.weaponmechanics.WeaponMechanics.getInstance().getWeaponConfigurations();
            String zoomSpread = config.getString(weaponTitle + ".Shoot.Spread.Modify_Spread_When.Zooming", "50%");
            if (zoomSpread != null && zoomSpread.endsWith("%")) {
                double percent = Double.parseDouble(zoomSpread.replace("%", ""));
                return percent / 100.0;
            }
            return 0.5;
        } catch (Throwable e) {
            return 0.5;
        }
    }

    public boolean shoot(@NotNull LivingEntity entity, @NotNull Location target) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        if (instance == null) return false;
        if (!instance.canShoot()) return false;

        if (durabilityEnabled && instance.getCurrentDurability() <= 0) {
            return false;
        }

        if (instance.consumesAmmo() && instance.getCurrentAmmo() <= 0) {
            return false;
        }

        try {
            // 添加代码层散布偏移（弥补WM生物端射击可能不生效的内置Spread）
            Location spreadTarget = target.clone();
            double spread = instance.getBaseSpread();
            if (spread > 0) {
                // 安全计算距离（跨世界防护）
                double distance = 10.0;
                if (entity.getWorld() != null && target.getWorld() != null
                        && entity.getWorld().equals(target.getWorld())) {
                    distance = entity.getLocation().distance(target);
                }
                // 每1度散布在10格距离上约产生0.17格偏移（tan(1°) ≈ 0.017）
                // 简化公式：distance * spread * 0.01，确保最小0.1格偏移
                double offsetScale = Math.max(distance * spread * 0.01, 0.1);
                spreadTarget.add(
                        (Math.random() - 0.5) * 2 * offsetScale,
                        (Math.random() - 0.5) * offsetScale,
                        (Math.random() - 0.5) * 2 * offsetScale
                );
                plugin.debug("[Shoot] 散布偏移: " + entity.getName()
                        + " | spread=" + String.format("%.2f", spread)
                        + " | dist=" + String.format("%.1f", distance)
                        + " | offset=" + String.format("%.2f", offsetScale));
            }

            WeaponMechanicsAPI.shoot(entity, instance.getWeaponTitle(), spreadTarget);
            // 生物端 WM Trail 粒子可能不渲染 → 兜底: 强制生成一个枪口粒子包
            shootEffect(entity, spreadTarget);

            if (instance.consumesAmmo()) {
                instance.setCurrentAmmo(instance.getCurrentAmmo() - 1);
                entity.setMetadata("emwm_ammo",
                        new FixedMetadataValue(plugin, instance.getCurrentAmmo()));
            }
            instance.markShot();

            if (durabilityEnabled) {
                int newDurability = Math.max(0, instance.getCurrentDurability() - decayPerShot);
                instance.setCurrentDurability(newDurability);
                entity.setMetadata("emwm_durability",
                        new FixedMetadataValue(plugin, newDurability));

                if (newDurability <= 0) {
                    instance.setBroken(true);
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[EMWM] 射击失败: " + e.getMessage());
            return false;
        }
    }

    public boolean shoot(@NotNull LivingEntity entity, @NotNull Location target, boolean ads) {
        entity.setMetadata("emwm_ads", new FixedMetadataValue(plugin, ads));
        return shoot(entity, target);
    }

    public boolean reload(@NotNull LivingEntity entity) {
        return reload(entity, 1.0);
    }

    public boolean reload(@NotNull LivingEntity entity, double speedMultiplier) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        if (instance == null) return false;
        if (instance.isReloading()) return false;

        instance.setReloading(true);
        entity.setMetadata("emwm_reloading", new FixedMetadataValue(plugin, true));

        long reloadTicks = (long) (instance.getReloadTicks() * speedMultiplier);

        plugin.debug("开始换弹: " + entity.getName() + " | " + reloadTicks + "tick" +
                " (速度x" + String.format("%.1f", 1.0 / speedMultiplier) + ")");

        // P0-5 修复：Folia 环境下使用 EntityScheduler 调度实体状态操作
        Runnable reloadComplete = () -> {
            if (!entity.isValid() || entity.isDead()) {
                weaponCache.remove(entity.getUniqueId());
                return;
            }
            MobWeaponInstance inst = weaponCache.get(entity.getUniqueId());
            if (inst != null) {
                inst.setCurrentAmmo(inst.getMagazineSize());
                inst.setReloading(false);
                entity.setMetadata("emwm_ammo",
                        new FixedMetadataValue(plugin, inst.getMagazineSize()));
                entity.removeMetadata("emwm_reloading", plugin);
                plugin.debug("换弹完成: " + entity.getName() + " | 弹药: " + inst.getMagazineSize());
            }
        };
        if (plugin.isFolia()) {
            entity.getScheduler().execute(plugin, reloadComplete, null, reloadTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, reloadComplete, reloadTicks);
        }

        return true;
    }

    public boolean isReloading(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null && instance.isReloading();
    }

    public boolean isMagazineEmpty(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null && instance.getCurrentAmmo() <= 0;
    }

    public int getCurrentAmmo(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null ? instance.getCurrentAmmo() : 0;
    }

    public int getWeaponDurability(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null ? instance.getCurrentDurability() : 0;
    }

    public double getDurabilityRatio(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        if (instance == null || instance.getMaxDurability() <= 0) return 1.0;
        return (double) instance.getCurrentDurability() / instance.getMaxDurability();
    }

    public int getMagazineSize(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null ? instance.getMagazineSize() : 30;
    }

    public long getFireRateMs(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null ? instance.getFireRateMs() : 300L;
    }

    public long getFireRateMs(@NotNull UUID uuid) {
        MobWeaponInstance instance = weaponCache.get(uuid);
        return instance != null ? instance.getFireRateMs() : 300L;
    }

    public double getBaseSpread(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null ? instance.getBaseSpread() : 3.0;
    }

    public double getAdsSpreadMultiplier(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null ? instance.getAdsSpreadMultiplier() : 0.5;
    }

    public double getAccuracyModifier(@NotNull LivingEntity entity) {
        if (!durabilityEnabled) return 1.0;
        double ratio = getDurabilityRatio(entity);
        int penaltySteps = (int) ((1.0 - ratio) / 0.1);
        return Math.max(0.5, 1.0 - penaltySteps * accuracyPenaltyPer10Percent);
    }

    public boolean shouldMalfunction(@NotNull LivingEntity entity) {
        if (!durabilityEnabled) return false;
        double ratio = getDurabilityRatio(entity);
        return ratio < malfunctionThreshold && Math.random() < (malfunctionThreshold - ratio);
    }

    @Nullable
    public String getWeaponTitle(@NotNull LivingEntity entity) {
        MobWeaponInstance instance = weaponCache.get(entity.getUniqueId());
        return instance != null ? instance.getWeaponTitle() : null;
    }

    public boolean hasWeapon(@NotNull LivingEntity entity) {
        return weaponCache.containsKey(entity.getUniqueId());
    }

    public void unbindWeapon(@NotNull LivingEntity entity) {
        weaponCache.remove(entity.getUniqueId());
        entity.removeMetadata("emwm_weapon", plugin);
        entity.removeMetadata("emwm_durability", plugin);
        entity.removeMetadata("emwm_ammo", plugin);
        entity.removeMetadata("emwm_ads", plugin);
        entity.removeMetadata("emwm_reloading", plugin);
        entity.removeMetadata("emwm_allowed_grenade_types", plugin);
    }

    /**
     * 射击视觉兜底 — 生物端 WM Trail 粒子可能不渲染, 手动生成粒子束 + 音效
     */
    private void shootEffect(LivingEntity entity, Location target) {
        Location muzzle = entity.getEyeLocation();
        if (muzzle == null || target == null) return;
        // P0-8 修复：跨世界安全距离，防止 distance() 抛异常
        if (muzzle.getWorld() == null || target.getWorld() == null
                || !muzzle.getWorld().equals(target.getWorld())) return;
        Vector direction = target.toVector().subtract(muzzle.toVector()).normalize();
        double distance = muzzle.distance(target);

        // 枪口火焰
        muzzle.getWorld().spawnParticle(Particle.FLAME, muzzle, 3, 0.1, 0.1, 0.1, 0.02);

        // 弹道粒子 (橙红色粉尘, 模拟曳光)
        for (double d = 0.5; d < distance && d < 50; d += 0.8) {
            Location point = muzzle.clone().add(direction.clone().multiply(d));
            muzzle.getWorld().spawnParticle(
                    Particle.DUST, point, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.ORANGE, 1.5f));
        }

        // 枪声 — 距离衰减
        muzzle.getWorld().playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 2.0f);
    }

    public String getRandomWeaponForTier(String tier) {
        List<String> pool = switch (tier.toLowerCase()) {
            case "scav" -> scavWeapons;
            case "pmc" -> pmcWeapons;
            case "boss" -> bossWeapons;
            default -> List.of(defaultWeapon);
        };
        return pool.get((int) (Math.random() * pool.size()));
    }

    @Nullable
    public String getMetadataString(@NotNull LivingEntity entity, @NotNull String key) {
        List<MetadataValue> values = entity.getMetadata(key);
        for (MetadataValue value : values) {
            if (value.getOwningPlugin() == plugin) {
                return value.asString();
            }
        }
        return null;
    }

    public int getMetadataInt(@NotNull LivingEntity entity, @NotNull String key, int defaultValue) {
        List<MetadataValue> values = entity.getMetadata(key);
        for (MetadataValue value : values) {
            if (value.getOwningPlugin() == plugin) {
                try {
                    return value.asInt();
                } catch (Exception e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    public static class MobWeaponInstance {
        private final String weaponTitle;
        private int currentDurability;
        private final int maxDurability;
        private boolean broken;
        private final int magazineSize;
        private int currentAmmo;
        private final int reloadTicks;
        private boolean reloading;
        private final long fireRateMs;
        private final double baseSpread;
        private final double adsSpreadMultiplier;
        private long lastShotTime;
        private boolean consumeAmmo = true; // 默认有限(向后兼容); false=无限(经济护栏,不递减 emwm_ammo)

        public MobWeaponInstance(String weaponTitle, int currentDurability, int maxDurability,
                                  boolean broken, int magazineSize, int currentAmmo, int reloadTicks,
                                  long fireRateMs, double baseSpread, double adsSpreadMultiplier) {
            this.weaponTitle = weaponTitle;
            this.currentDurability = currentDurability;
            this.maxDurability = maxDurability;
            this.broken = broken;
            this.magazineSize = magazineSize;
            this.currentAmmo = currentAmmo;
            this.reloadTicks = reloadTicks;
            this.reloading = false;
            this.fireRateMs = fireRateMs;
            this.baseSpread = baseSpread;
            this.adsSpreadMultiplier = adsSpreadMultiplier;
            this.lastShotTime = 0;
        }

        public String getWeaponTitle() { return weaponTitle; }
        public int getCurrentDurability() { return currentDurability; }
        public void setCurrentDurability(int durability) { this.currentDurability = durability; }
        public int getMaxDurability() { return maxDurability; }
        public boolean isBroken() { return broken; }
        public void setBroken(boolean broken) { this.broken = broken; }
        public int getMagazineSize() { return magazineSize; }
        public int getCurrentAmmo() { return currentAmmo; }
        public void setCurrentAmmo(int ammo) { this.currentAmmo = ammo; }
        public boolean consumesAmmo() { return consumeAmmo; }
        public void setConsumeAmmo(boolean value) { this.consumeAmmo = value; }
        public int getReloadTicks() { return reloadTicks; }
        public boolean isReloading() { return reloading; }
        public void setReloading(boolean reloading) { this.reloading = reloading; }
        public long getFireRateMs() { return fireRateMs; }
        public double getBaseSpread() { return baseSpread; }
        public double getAdsSpreadMultiplier() { return adsSpreadMultiplier; }
        public boolean canShoot() {
            if (broken || currentDurability <= 0 || reloading || currentAmmo <= 0) return false;
            if (System.currentTimeMillis() - lastShotTime < fireRateMs) return false;
            return true;
        }

        public void markShot() { this.lastShotTime = System.currentTimeMillis(); }
        public long getLastShotTime() { return lastShotTime; }
    }
}
