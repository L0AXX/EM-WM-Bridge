package com.emwbridge.config;

import com.emwbridge.EMWMBridge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EMWM自定义配置缓存管理器
 *
 * 核心职责：
 * 1. 缓存EliteMobs怪物yml中的emwm自定义配置段
 * 2. 支持全局兵种模板继承（emwm_mob_templates.yml）
 * 3. 提供怪物唯一标识→武器配置的快速映射
 * 4. 支持热重载刷新缓存
 */
public class EMWMConfigCache {

    private final EMWMBridge plugin;
    private final LuaPowerParser luaPowerParser;

    // 怪物文件名 → EMWM武器配置
    private final Map<String, EMWMWeaponConfig> mobConfigs = new ConcurrentHashMap<>();

    // 全局兵种模板：模板名 → 配置
    private final Map<String, EMWMWeaponConfig> globalTemplates = new ConcurrentHashMap<>();

    // 怪物文件名 → 继承的模板名
    private final Map<String, String> templateInheritance = new ConcurrentHashMap<>();

    private WeaponMetaCache weaponMetaCache;

    public EMWMConfigCache(EMWMBridge plugin) {
        this.plugin = plugin;
        this.luaPowerParser = new LuaPowerParser(plugin);
        this.weaponMetaCache = new WeaponMetaCache(plugin);
    }

    /**
     * 加载所有配置（怪物配置 + 全局模板）
     */
    public void loadAll() {
        loadGlobalTemplates();
        loadMobConfigs();
        preloadWeaponsFromConfigs();
        plugin.getLogger().info("[EMWMConfigCache] 已加载 " + mobConfigs.size() + " 个怪物配置, "
                + globalTemplates.size() + " 个全局模板, "
                + weaponMetaCache.getLoadedWeaponIds().size() + " 个武器元数据");
    }

    /**
     * 从所有已加载配置中收集武器ID，预加载WM武器元数据
     */
    private void preloadWeaponsFromConfigs() {
        Set<String> weaponIds = new HashSet<>();
        // 从全局模板收集
        for (EMWMWeaponConfig template : globalTemplates.values()) {
            if (template.getWeaponPool() != null) {
                weaponIds.addAll(template.getWeaponPool());
            }
        }
        // 从怪物配置收集
        for (EMWMWeaponConfig mobConfig : mobConfigs.values()) {
            if (mobConfig.getWeaponPool() != null) {
                weaponIds.addAll(mobConfig.getWeaponPool());
            }
        }
        // 从config.yml配置的武器池收集
        weaponIds.addAll(plugin.getConfig().getStringList("weapons.scav-pool"));
        weaponIds.addAll(plugin.getConfig().getStringList("weapons.pmc-pool"));
        weaponIds.addAll(plugin.getConfig().getStringList("weapons.boss-pool"));
        weaponIds.add(plugin.getConfig().getString("weapons.default-weapon", "AK_47"));

        weaponMetaCache.preloadWeapons(weaponIds);
    }

    public WeaponMetaCache getWeaponMetaCache() {
        return weaponMetaCache;
    }

    /**
     * 加载全局兵种模板（emwm_mob_templates.yml）
     * 模板文件必须放在 plugins/EM-WM-Bridge/emwm_mob_templates.yml
     * 模板格式（扁平结构，无 emwm: 嵌套）：
     *   templates:
     *     scav_rifle:
     *       weapon: AUG
     *       maxRange: 38
     *       spread: 0.16
     *       ...
     */
    private void loadGlobalTemplates() {
        // 自动部署内置模板文件（如果用户没有手动创建）
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            plugin.saveResource("emwm_mob_templates.yml", false);
        } catch (Exception e) {
            plugin.debug("[EMWMConfigCache] 内置模板文件不存在或已存在: " + e.getMessage());
        }

        File templateFile = new File(plugin.getDataFolder(), "emwm_mob_templates.yml");
        if (!templateFile.exists()) {
            plugin.getLogger().warning("[EMWMConfigCache] 未找到全局模板配置文件，跳过模板加载");
            plugin.getLogger().warning("[EMWMConfigCache] 请创建 plugins/EM-WM-Bridge/emwm_mob_templates.yml");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(templateFile);
        ConfigurationSection templatesSection = yaml.getConfigurationSection("templates");
        if (templatesSection == null) {
            plugin.getLogger().warning("[EMWMConfigCache] 模板文件缺少 templates: 根节点");
            return;
        }

        for (String templateName : templatesSection.getKeys(false)) {
            ConfigurationSection section = templatesSection.getConfigurationSection(templateName);
            if (section == null) continue;

            EMWMWeaponConfig config = parseTemplateSection(section);
            if (config != null) {
                globalTemplates.put(templateName, config);
                plugin.debug("[EMWMConfigCache] 已加载全局模板: " + templateName);
            }
        }
        plugin.getLogger().info("[EMWMConfigCache] 成功加载全局兵种模板文件，共解析 " + globalTemplates.size() + " 套兵种模板");
    }

    /**
     * 解析模板配置段（扁平结构，与怪物 emwm 配置段格式兼容）
     * 支持字段名映射：
     *   weapon → weapon-pool（单武器）
     *   fireRateTicks → shooting.fire-rate（反向换算）
     *   maxRange → shooting.max-range
     *   spread → shooting.spread
     *   damageMultiplier → shooting.damage-multiplier
     *   projectileSpeed → shooting.projectile-speed
     *   bulletPenetration → shooting.bullet-penetration
     *   recoil → shooting.recoil
     *   suppressed → shooting.suppressed
     *   onlyAimShoot → shooting.only-aim-shoot
     *   meleeSwitchHealthPercent → tactics.melee-switch-hp-percent
     *   allowAutoReload → ammo.auto-reload
     *   enableGrenadeAI → special.grenade-ai.enabled
     *   grenadeThrowRange → special.grenade-ai.max-range
     *   grenadeCooldown → special.grenade-ai.cooldown-ticks
     *   allowedGrenadeTypes → special.grenade-ai.allowed-types
     */
    private EMWMWeaponConfig parseTemplateSection(ConfigurationSection section) {
        if (section == null) return null;
        EMWMWeaponConfig config = new EMWMWeaponConfig();

        // 武器（单武器 → weapon-pool）
        if (section.contains("weapon")) {
            config.setWeaponPool(Collections.singletonList(section.getString("weapon")));
        }

        // 射击参数
        if (section.contains("fireRateTicks")) {
            int ticks = section.getInt("fireRateTicks");
            if (ticks > 0) {
                config.setFireRate(20.0 / ticks); // ticks → shots/second
            }
        }
        if (section.contains("maxRange")) {
            config.setMaxRange(section.getInt("maxRange"));
        }
        if (section.contains("spread")) {
            config.setSpread(section.getDouble("spread"));
        }
        if (section.contains("damageMultiplier")) {
            config.setDamageMultiplier(section.getDouble("damageMultiplier"));
        }
        if (section.contains("projectileSpeed")) {
            config.setProjectileSpeed(section.getDouble("projectileSpeed"));
        }
        if (section.contains("bulletPenetration")) {
            config.setBulletPenetration(section.getDouble("bulletPenetration"));
        }
        if (section.contains("recoil")) {
            config.setRecoil(section.getDouble("recoil"));
        }
        if (section.contains("suppressed")) {
            config.setSuppressed(section.getBoolean("suppressed"));
        }
        if (section.contains("onlyAimShoot")) {
            config.setOnlyAimShoot(section.getBoolean("onlyAimShoot"));
        }

        // 弹药/换弹参数
        if (section.contains("allowAutoReload")) {
            config.setAutoReload(section.getBoolean("allowAutoReload"));
        }

        // 战术参数
        if (section.contains("meleeSwitchHealthPercent")) {
            config.setMeleeSwitchHealthPercent(section.getDouble("meleeSwitchHealthPercent"));
        }

        // 投掷物AI参数
        if (section.contains("enableGrenadeAI")) {
            config.setEnableGrenadeAI(section.getBoolean("enableGrenadeAI"));
        }
        if (section.contains("grenadeThrowRange")) {
            config.setGrenadeMaxRange(section.getInt("grenadeThrowRange"));
        }
        if (section.contains("grenadeCooldown")) {
            config.setGrenadeCooldownTicks(section.getInt("grenadeCooldown"));
        }
        if (section.isList("allowedGrenadeTypes") || section.contains("allowedGrenadeTypes")) {
            config.setAllowedGrenadeTypes(section.getStringList("allowedGrenadeTypes"));
        }

        return config;
    }

    /**
     * 加载EliteMobs怪物配置（遍历custombosses目录）
     */
    private void loadMobConfigs() {
        File emFolder = new File(plugin.getServer().getPluginsFolder(), "EliteMobs");
        if (!emFolder.exists()) {
            plugin.getLogger().warning("[EMWMConfigCache] 未找到EliteMobs插件目录");
            return;
        }

        File customBossesFolder = new File(emFolder, "custombosses");
        if (!customBossesFolder.exists()) {
            customBossesFolder = new File(emFolder, "content/custombosses");
        }

        if (!customBossesFolder.exists()) {
            plugin.getLogger().warning("[EMWMConfigCache] 未找到EliteMobs custombosses目录");
            return;
        }

        loadFromDirectory(customBossesFolder, "");
    }

    /**
     * 递归遍历目录加载yml配置
     */
    private void loadFromDirectory(File directory, String prefix) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadFromDirectory(file, prefix + file.getName() + "/");
            } else if (file.getName().endsWith(".yml")) {
                loadMobConfig(file, prefix);
            }
        }
    }

    /**
     * 加载单个怪物配置文件
     */
    private void loadMobConfig(File file, String pathPrefix) {
        String fileName = pathPrefix + file.getName().replace(".yml", "");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // 检查是否有emwm配置段
        ConfigurationSection emwmSection = yaml.getConfigurationSection("emwm");
        if (emwmSection == null) {
            // 检查是否有继承模板配置（向后兼容：优先读驼峰，回退读横杠）
            String inheritTemplate = yaml.contains("emwmTemplate") ? yaml.getString("emwmTemplate") : yaml.getString("emwm_template");
            if (inheritTemplate != null) {
                templateInheritance.put(fileName, inheritTemplate);
                plugin.debug("[EMWMConfigCache] 怪物 " + fileName + " 继承模板: " + inheritTemplate);
            }
            return;
        }

        EMWMWeaponConfig config = parseEMWMConfig(emwmSection);
        if (config != null) {
            mobConfigs.put(fileName, config);
            plugin.debug("[EMWMConfigCache] 已加载怪物配置: " + fileName + " → " + config.getWeaponPool());

            // 检查是否有继承模板（用于补充未定义的字段，向后兼容：优先读驼峰，回退读横杠）
            String inheritTemplate = yaml.contains("emwmTemplate") ? yaml.getString("emwmTemplate") : yaml.getString("emwm_template");
            if (inheritTemplate != null) {
                templateInheritance.put(fileName, inheritTemplate);
            }
        }
    }

    /**
     * 解析emwm配置段
     * 只在字段存在时赋值，保留null值用于后续继承/WM参数读取
     */
    private EMWMWeaponConfig parseEMWMConfig(ConfigurationSection section) {
        if (section == null) return null;

        EMWMWeaponConfig config = new EMWMWeaponConfig();

        // 武器池配置（向后兼容：优先读驼峰，回退读横杠）
        List<String> weaponPool = section.getStringList("weaponPool");
        if (weaponPool.isEmpty()) {
            weaponPool = section.getStringList("weapon-pool");
        }
        if (!weaponPool.isEmpty()) {
            config.setWeaponPool(weaponPool);
        } else {
            String singleWeapon = section.getString("weapon");
            if (singleWeapon != null) {
                config.setWeaponPool(Collections.singletonList(singleWeapon));
            }
        }

        // 武器权重配置（向后兼容：优先读驼峰，回退读横杠）
        ConfigurationSection weightSection = section.getConfigurationSection("weaponWeight");
        if (weightSection == null) {
            weightSection = section.getConfigurationSection("weapon-weight");
        }
        if (weightSection != null) {
            Map<String, Double> weights = new HashMap<>();
            for (String weapon : weightSection.getKeys(false)) {
                weights.put(weapon, weightSection.getDouble(weapon));
            }
            config.setWeaponWeights(weights);
        }

        // 弹药配置（只在字段存在时赋值，保留null）
        ConfigurationSection ammoSection = section.getConfigurationSection("ammo");
        if (ammoSection != null) {
            if (ammoSection.contains("magazineSize")) {
                config.setMagazineSize(ammoSection.getInt("magazineSize"));
            } else if (ammoSection.contains("magazine-size")) {
                config.setMagazineSize(ammoSection.getInt("magazine-size"));
            }
            if (ammoSection.contains("reloadDuration")) {
                config.setReloadDuration(ammoSection.getInt("reloadDuration"));
            } else if (ammoSection.contains("reload-duration")) {
                config.setReloadDuration(ammoSection.getInt("reload-duration"));
            }
            if (ammoSection.contains("autoReload")) {
                config.setAutoReload(ammoSection.getBoolean("autoReload"));
            } else if (ammoSection.contains("auto-reload")) {
                config.setAutoReload(ammoSection.getBoolean("auto-reload"));
            }
            if (ammoSection.contains("ammoType")) {
                config.setAmmoType(ammoSection.getString("ammoType"));
            } else if (ammoSection.contains("ammo-type")) {
                config.setAmmoType(ammoSection.getString("ammo-type"));
            }
            if (ammoSection.contains("reserveAmmo")) {
                config.setReserveAmmo(ammoSection.getInt("reserveAmmo"));
            } else if (ammoSection.contains("reserve-ammo")) {
                config.setReserveAmmo(ammoSection.getInt("reserve-ammo"));
            }
            if (ammoSection.contains("cancelReloadOnMove")) {
                config.setCancelReloadOnMove(ammoSection.getBoolean("cancelReloadOnMove"));
            } else if (ammoSection.contains("cancel-reload-on-move")) {
                config.setCancelReloadOnMove(ammoSection.getBoolean("cancel-reload-on-move"));
            }
        }

        // 射击配置（只在字段存在时赋值，保留null）
        ConfigurationSection shootingSection = section.getConfigurationSection("shooting");
        if (shootingSection != null) {
            if (shootingSection.contains("fireRateTicks")) {
                config.setFireRate(shootingSection.getDouble("fireRateTicks"));
            } else if (shootingSection.contains("fire-rate")) {
                config.setFireRate(shootingSection.getDouble("fire-rate"));
            }
            if (shootingSection.contains("spread")) {
                config.setSpread(shootingSection.getDouble("spread"));
            }
            if (shootingSection.contains("adsSpreadMultiplier")) {
                config.setAdsSpreadMultiplier(shootingSection.getDouble("adsSpreadMultiplier"));
            } else if (shootingSection.contains("ads-spread-multiplier")) {
                config.setAdsSpreadMultiplier(shootingSection.getDouble("ads-spread-multiplier"));
            }
            if (shootingSection.contains("effectiveRange")) {
                config.setEffectiveRange(shootingSection.getInt("effectiveRange"));
            } else if (shootingSection.contains("effective-range")) {
                config.setEffectiveRange(shootingSection.getInt("effective-range"));
            }
            if (shootingSection.contains("maxRange")) {
                config.setMaxRange(shootingSection.getInt("maxRange"));
            } else if (shootingSection.contains("max-range")) {
                config.setMaxRange(shootingSection.getInt("max-range"));
            }
            if (shootingSection.contains("adsRangeThreshold")) {
                config.setAdsRangeThreshold(shootingSection.getInt("adsRangeThreshold"));
            } else if (shootingSection.contains("ads-range-threshold")) {
                config.setAdsRangeThreshold(shootingSection.getInt("ads-range-threshold"));
            }
            // 扩展射击字段
            if (shootingSection.contains("fireMode")) {
                config.setFireMode(shootingSection.getString("fireMode"));
            } else if (shootingSection.contains("fire-mode")) {
                config.setFireMode(shootingSection.getString("fire-mode"));
            }
            if (shootingSection.contains("projectileSpeed")) {
                config.setProjectileSpeed(shootingSection.getDouble("projectileSpeed"));
            } else if (shootingSection.contains("projectile-speed")) {
                config.setProjectileSpeed(shootingSection.getDouble("projectile-speed"));
            }
            if (shootingSection.contains("bulletPenetration")) {
                config.setBulletPenetration(shootingSection.getDouble("bulletPenetration"));
            } else if (shootingSection.contains("bullet-penetration")) {
                config.setBulletPenetration(shootingSection.getDouble("bullet-penetration"));
            }
            if (shootingSection.contains("muzzleFlashEnabled")) {
                config.setMuzzleFlashEnabled(shootingSection.getBoolean("muzzleFlashEnabled"));
            } else if (shootingSection.contains("muzzle-flash-enabled")) {
                config.setMuzzleFlashEnabled(shootingSection.getBoolean("muzzle-flash-enabled"));
            }
            if (shootingSection.contains("suppressed")) {
                config.setSuppressed(shootingSection.getBoolean("suppressed"));
            }
            if (shootingSection.contains("onlyAimShoot")) {
                config.setOnlyAimShoot(shootingSection.getBoolean("onlyAimShoot"));
            } else if (shootingSection.contains("only-aim-shoot")) {
                config.setOnlyAimShoot(shootingSection.getBoolean("only-aim-shoot"));
            }
            if (shootingSection.contains("damageMultiplier")) {
                config.setDamageMultiplier(shootingSection.getDouble("damageMultiplier"));
            } else if (shootingSection.contains("damage-multiplier")) {
                config.setDamageMultiplier(shootingSection.getDouble("damage-multiplier"));
            }
            if (shootingSection.contains("recoil")) {
                config.setRecoil(shootingSection.getDouble("recoil"));
            }
        }

        // 战术配置（只在字段存在时赋值，保留null）
        ConfigurationSection tacticsSection = section.getConfigurationSection("tactics");
        if (tacticsSection != null) {
            if (tacticsSection.contains("meleeRange")) {
                config.setMeleeRange(tacticsSection.getDouble("meleeRange"));
            } else if (tacticsSection.contains("melee-range")) {
                config.setMeleeRange(tacticsSection.getDouble("melee-range"));
            }
            if (tacticsSection.contains("standAndShoot")) {
                config.setStandAndShoot(tacticsSection.getBoolean("standAndShoot"));
            } else if (tacticsSection.contains("stand-and-shoot")) {
                config.setStandAndShoot(tacticsSection.getBoolean("stand-and-shoot"));
            }
            if (tacticsSection.contains("suppressHpThreshold")) {
                config.setSuppressHpThreshold(tacticsSection.getDouble("suppressHpThreshold"));
            } else if (tacticsSection.contains("suppress-hp-threshold")) {
                config.setSuppressHpThreshold(tacticsSection.getDouble("suppress-hp-threshold"));
            }
            if (tacticsSection.contains("retreatHpThreshold")) {
                config.setRetreatHpThreshold(tacticsSection.getDouble("retreatHpThreshold"));
            } else if (tacticsSection.contains("retreat-hp-threshold")) {
                config.setRetreatHpThreshold(tacticsSection.getDouble("retreat-hp-threshold"));
            }
            if (tacticsSection.contains("meleeSwitchHealthPercent")) {
                config.setMeleeSwitchHealthPercent(tacticsSection.getDouble("meleeSwitchHealthPercent"));
            } else if (tacticsSection.contains("melee-switch-hp-percent")) {
                config.setMeleeSwitchHealthPercent(tacticsSection.getDouble("melee-switch-hp-percent"));
            }
        }

        // 行为配置（只在字段存在时赋值，保留null）
        ConfigurationSection behaviorSection = section.getConfigurationSection("behavior");
        if (behaviorSection != null) {
            if (behaviorSection.contains("aggressiveness")) {
                config.setAggressiveness(behaviorSection.getDouble("aggressiveness"));
            }
            if (behaviorSection.contains("coverUsage")) {
                config.setCoverUsage(behaviorSection.getDouble("coverUsage"));
            } else if (behaviorSection.contains("cover-usage")) {
                config.setCoverUsage(behaviorSection.getDouble("cover-usage"));
            }
            if (behaviorSection.contains("searchDuration")) {
                config.setSearchDuration(behaviorSection.getInt("searchDuration"));
            } else if (behaviorSection.contains("search-duration")) {
                config.setSearchDuration(behaviorSection.getInt("search-duration"));
            }
        }

        // 动画配置（新段：cosmetics）
        ConfigurationSection cosmeticsSection = section.getConfigurationSection("cosmetics");
        if (cosmeticsSection != null) {
            if (cosmeticsSection.contains("equipDelay")) {
                config.setEquipDelay(cosmeticsSection.getInt("equipDelay"));
            } else if (cosmeticsSection.contains("equip-delay")) {
                config.setEquipDelay(cosmeticsSection.getInt("equip-delay"));
            }
            if (cosmeticsSection.contains("aimDelay")) {
                config.setAimDelay(cosmeticsSection.getInt("aimDelay"));
            } else if (cosmeticsSection.contains("aim-delay")) {
                config.setAimDelay(cosmeticsSection.getInt("aim-delay"));
            }
            if (cosmeticsSection.contains("equipAnimation")) {
                config.setEquipAnimation(cosmeticsSection.getString("equipAnimation"));
            } else if (cosmeticsSection.contains("equip-animation")) {
                config.setEquipAnimation(cosmeticsSection.getString("equip-animation"));
            }
            if (cosmeticsSection.contains("aimAnimation")) {
                config.setAimAnimation(cosmeticsSection.getString("aimAnimation"));
            } else if (cosmeticsSection.contains("aim-animation")) {
                config.setAimAnimation(cosmeticsSection.getString("aim-animation"));
            }
            if (cosmeticsSection.contains("reloadAnimation")) {
                config.setReloadAnimation(cosmeticsSection.getString("reloadAnimation"));
            } else if (cosmeticsSection.contains("reload-animation")) {
                config.setReloadAnimation(cosmeticsSection.getString("reload-animation"));
            }
            if (cosmeticsSection.contains("shootAnimation")) {
                config.setShootAnimation(cosmeticsSection.getString("shootAnimation"));
            } else if (cosmeticsSection.contains("shoot-animation")) {
                config.setShootAnimation(cosmeticsSection.getString("shoot-animation"));
            }
            if (cosmeticsSection.contains("crouchAimSpreadReduction")) {
                config.setCrouchAimSpreadReduction(cosmeticsSection.getDouble("crouchAimSpreadReduction"));
            } else if (cosmeticsSection.contains("crouch-aim-spread-reduction")) {
                config.setCrouchAimSpreadReduction(cosmeticsSection.getDouble("crouch-aim-spread-reduction"));
            }
        }

        // 耐久配件配置（新段：durability）
        ConfigurationSection durabilitySection = section.getConfigurationSection("durability");
        if (durabilitySection != null) {
            if (durabilitySection.contains("durabilityPerShot")) {
                config.setDurabilityPerShot(durabilitySection.getInt("durabilityPerShot"));
            } else if (durabilitySection.contains("durability-per-shot")) {
                config.setDurabilityPerShot(durabilitySection.getInt("durability-per-shot"));
            }
            if (durabilitySection.contains("breakOnZeroDurability")) {
                config.setBreakOnZeroDurability(durabilitySection.getBoolean("breakOnZeroDurability"));
            } else if (durabilitySection.contains("break-on-zero-durability")) {
                config.setBreakOnZeroDurability(durabilitySection.getBoolean("break-on-zero-durability"));
            }
        }

        // 配件列表（新段：attachments）
        List<String> attachments = section.getStringList("attachments");
        if (!attachments.isEmpty()) {
            config.setAttachments(attachments);
        }

        // 投掷物配置（Boss专属，只在字段存在时赋值）
        ConfigurationSection specialSection = section.getConfigurationSection("special");
        if (specialSection != null) {
            ConfigurationSection throwablesSection = specialSection.getConfigurationSection("throwables");
            if (throwablesSection != null) {
                if (throwablesSection.contains("fragInterval")) {
                    config.setFragInterval(throwablesSection.getInt("fragInterval"));
                } else if (throwablesSection.contains("frag-interval")) {
                    config.setFragInterval(throwablesSection.getInt("frag-interval"));
                }
                if (throwablesSection.contains("flashInterval")) {
                    config.setFlashInterval(throwablesSection.getInt("flashInterval"));
                } else if (throwablesSection.contains("flash-interval")) {
                    config.setFlashInterval(throwablesSection.getInt("flash-interval"));
                }
                if (throwablesSection.contains("smokeInterval")) {
                    config.setSmokeInterval(throwablesSection.getInt("smokeInterval"));
                } else if (throwablesSection.contains("smoke-interval")) {
                    config.setSmokeInterval(throwablesSection.getInt("smoke-interval"));
                }
                if (throwablesSection.contains("throwIfCover")) {
                    config.setThrowIfCover(throwablesSection.getBoolean("throwIfCover"));
                } else if (throwablesSection.contains("throw-if-cover")) {
                    config.setThrowIfCover(throwablesSection.getBoolean("throw-if-cover"));
                }
                if (throwablesSection.contains("throwMinRange")) {
                    config.setThrowMinRange(throwablesSection.getInt("throwMinRange"));
                } else if (throwablesSection.contains("throw-min-range")) {
                    config.setThrowMinRange(throwablesSection.getInt("throw-min-range"));
                }
                if (throwablesSection.contains("throwMaxRange")) {
                    config.setThrowMaxRange(throwablesSection.getInt("throwMaxRange"));
                } else if (throwablesSection.contains("throw-max-range")) {
                    config.setThrowMaxRange(throwablesSection.getInt("throw-max-range"));
                }
            }

            // 投掷物AI参数
            ConfigurationSection grenadeSection = specialSection.getConfigurationSection("grenade-ai");
            if (grenadeSection != null) {
                if (grenadeSection.contains("enabled")) {
                    config.setEnableGrenadeAI(grenadeSection.getBoolean("enabled"));
                }
                if (grenadeSection.contains("grenadeType")) {
                    config.setGrenadeType(grenadeSection.getString("grenadeType"));
                } else if (grenadeSection.contains("grenade-type")) {
                    config.setGrenadeType(grenadeSection.getString("grenade-type"));
                }
                if (grenadeSection.contains("maxRange")) {
                    config.setGrenadeMaxRange(grenadeSection.getInt("maxRange"));
                } else if (grenadeSection.contains("max-range")) {
                    config.setGrenadeMaxRange(grenadeSection.getInt("max-range"));
                }
                if (grenadeSection.contains("cooldownTicks")) {
                    config.setGrenadeCooldownTicks(grenadeSection.getInt("cooldownTicks"));
                } else if (grenadeSection.contains("cooldown-ticks")) {
                    config.setGrenadeCooldownTicks(grenadeSection.getInt("cooldown-ticks"));
                }
                if (grenadeSection.contains("minEnemyCoverTime")) {
                    config.setGrenadeMinEnemyCoverTime(grenadeSection.getInt("minEnemyCoverTime"));
                } else if (grenadeSection.contains("min-enemy-cover-time")) {
                    config.setGrenadeMinEnemyCoverTime(grenadeSection.getInt("min-enemy-cover-time"));
                }
                if (grenadeSection.contains("maxCarry")) {
                    config.setMaxGrenadeCarry(grenadeSection.getInt("maxCarry"));
                } else if (grenadeSection.contains("max-carry")) {
                    config.setMaxGrenadeCarry(grenadeSection.getInt("max-carry"));
                }
                if (grenadeSection.isList("allowedGrenadeTypes")) {
                    config.setAllowedGrenadeTypes(grenadeSection.getStringList("allowedGrenadeTypes"));
                }
            }

            // 特殊能力配置
            if (specialSection.contains("callReinforcements")) {
                config.setCallReinforcements(specialSection.getBoolean("callReinforcements"));
            } else if (specialSection.contains("call-reinforcements")) {
                config.setCallReinforcements(specialSection.getBoolean("call-reinforcements"));
            }
            if (specialSection.contains("squadLeader")) {
                config.setSquadLeader(specialSection.getBoolean("squadLeader"));
            } else if (specialSection.contains("squad-leader")) {
                config.setSquadLeader(specialSection.getBoolean("squad-leader"));
            }
            if (specialSection.contains("preferLongRange")) {
                config.setPreferLongRange(specialSection.getBoolean("preferLongRange"));
            } else if (specialSection.contains("prefer-long-range")) {
                config.setPreferLongRange(specialSection.getBoolean("prefer-long-range"));
            }
            if (specialSection.contains("preferRush")) {
                config.setPreferRush(specialSection.getBoolean("preferRush"));
            } else if (specialSection.contains("prefer-rush")) {
                config.setPreferRush(specialSection.getBoolean("prefer-rush"));
            }
        }

        return config;
    }

    /**
     * 获取怪物的完整武器配置（支持模板继承 + LuaPower兜底）
     *
     * @param mobFileName 怪物配置文件名（如 "scav_basic"）
     * @return 合并后的完整配置
     */
    public EMWMWeaponConfig getConfig(String mobFileName) {
        EMWMWeaponConfig config = mobConfigs.get(mobFileName);

        // 如果怪物自身有配置，直接返回
        if (config != null) return config;

        // 检查是否有继承模板
        String templateName = templateInheritance.get(mobFileName);
        if (templateName != null) {
            EMWMWeaponConfig templateConfig = globalTemplates.get(templateName);
            if (templateConfig != null) {
                plugin.debug("[EMWMConfigCache] 怪物 " + mobFileName + " 使用模板配置: " + templateName);
                return templateConfig;
            }
        }

        // 阶段3：LuaPower兜底（降级策略）
        EMWMWeaponConfig luaConfig = getLuaPowerConfig(mobFileName);
        if (luaConfig != null) {
            plugin.debug("[EMWMConfigCache] 怪物 " + mobFileName + " 使用LuaPower配置");
            return luaConfig;
        }

        // 未找到配置
        return null;
    }

    /**
     * 从LuaPower脚本中获取配置（降级策略）
     *
     * @param mobFileName 怪物配置文件名
     * @return LuaPower解析的配置，如果未找到返回null
     */
    public EMWMWeaponConfig getLuaPowerConfig(String mobFileName) {
        // 尝试从powers脚本中解析
        List<String> powersList = luaPowerParser.getPowersList(mobFileName);
        if (powersList == null || powersList.isEmpty()) {
            return null;
        }

        return luaPowerParser.parseFromPowers(mobFileName, powersList);
    }

    /**
     * 根据怪物实体名称匹配配置
     *
     * @param customName 怪物自定义名称（如 "&e&l拾荒者"）
     * @return 匹配的配置文件名
     */
    public String matchConfigByName(String customName) {
        if (customName == null) return null;

        // 尝试匹配配置文件名
        for (String fileName : mobConfigs.keySet()) {
            if (customName.toLowerCase().contains(fileName.toLowerCase())) {
                return fileName;
            }
        }

        // 尝试匹配模板名
        for (String templateName : globalTemplates.keySet()) {
            String cleanName = customName.replace("&", "").replace("§", "").toLowerCase();
            if (cleanName.contains(templateName.toLowerCase())) {
                return templateName;
            }
        }

        return null;
    }

    /**
     * 热重载配置
     */
    public void reload() {
        mobConfigs.clear();
        globalTemplates.clear();
        templateInheritance.clear();
        luaPowerParser.clearCache();
        weaponMetaCache.clear();
        loadAll();
    }

    /**
     * 获取所有已加载的怪物配置文件名
     */
    public Set<String> getLoadedMobFiles() {
        return mobConfigs.keySet();
    }

    /**
     * 获取所有全局模板名
     */
    public Set<String> getGlobalTemplateNames() {
        return globalTemplates.keySet();
    }

    /**
     * 检查怪物是否有emwm配置
     */
    public boolean hasConfig(String mobFileName) {
        return mobConfigs.containsKey(mobFileName)
                || templateInheritance.containsKey(mobFileName);
    }
}