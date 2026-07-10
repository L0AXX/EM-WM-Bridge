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
        if (section.contains("consumeAmmo")) {
            config.setConsumeAmmo(section.getBoolean("consumeAmmo"));
        }
        if (section.contains("faction")) {
            config.setFaction(section.getString("faction"));
        }
        // 需求4：性格预设（强制指定性格，绕过 tier 随机 roll）
        if (section.contains("personalityPreset")) {
            config.setPersonalityPreset(section.getString("personalityPreset"));
        } else if (section.contains("personality-preset")) {
            config.setPersonalityPreset(section.getString("personality-preset"));
        }

        // 需求2：指定编制名（对应 config.yml squad.squads.<name>）
        if (section.contains("squad")) {
            config.setSquad(section.getString("squad"));
        }

        // 需求6：死亡掉落货币弹（loot 子块）
        if (section.contains("loot")) {
            ConfigurationSection loot = section.getConfigurationSection("loot");
            if (loot != null) {
                if (loot.contains("ammoType")) config.setLootAmmoType(loot.getString("ammoType"));
                else if (loot.contains("ammo-type")) config.setLootAmmoType(loot.getString("ammo-type"));
                if (loot.contains("min")) config.setLootAmmoMin(loot.getInt("min"));
                if (loot.contains("max")) config.setLootAmmoMax(loot.getInt("max"));
            }
        }

        // 需求7：护甲混用（gear 块）；实际减伤依赖 AM 对非玩家实体支持（7.4 验证门待测试服确认）
        if (section.contains("gear")) {
            ConfigurationSection gear = section.getConfigurationSection("gear");
            if (gear != null) {
                if (gear.contains("helmet")) config.setGearHelmet(gear.getString("helmet"));
                if (gear.contains("chestplate")) config.setGearChestplate(gear.getString("chestplate"));
                if (gear.contains("leggings")) config.setGearLeggings(gear.getString("leggings"));
                if (gear.contains("boots")) config.setGearBoots(gear.getString("boots"));
                if (gear.contains("dropGear")) config.setGearDropGear(gear.getBoolean("dropGear"));
                if (gear.contains("maxHealthBoost")) config.setGearMaxHealthBoost(gear.getInt("maxHealthBoost"));
            }
        }
        // 需求3：据点守卫行为参数（behavior 可置于顶层或 guard 子块）
        if (section.contains("behavior")) {
            config.setBehavior(section.getString("behavior"));
        }
        if (section.contains("guard")) {
            ConfigurationSection guard = section.getConfigurationSection("guard");
            if (guard != null) {
                if (guard.contains("behavior")) config.setBehavior(guard.getString("behavior"));
                if (guard.contains("guard-radius")) config.setGuardRadius(guard.getDouble("guard-radius"));
                if (guard.contains("aggro-radius")) config.setAggroRadius(guard.getDouble("aggro-radius"));
                if (guard.contains("leash-distance")) config.setLeashDistance(guard.getDouble("leash-distance"));
            }
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
     * 支持三种配置格式：
     * 1. emwm 配置段（标准格式）
     * 2. 根级别字段（weaponPool, fireRate 等直接写在顶层）
     * 3. 仅 emwmTemplate 继承模板（无个体配置）
     */
    private void loadMobConfig(File file, String pathPrefix) {
        String fileName = pathPrefix + file.getName().replace(".yml", "");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // 检查模板继承来源（三种可能的路径）
        String inheritTemplate = null;
        if (yaml.contains("emwmTemplate")) {
            inheritTemplate = yaml.getString("emwmTemplate");
        } else if (yaml.contains("emwm_template")) {
            inheritTemplate = yaml.getString("emwm_template");
        } else if (yaml.contains("customdata.emwm-template")) {
            inheritTemplate = yaml.getString("customdata.emwm-template");
        } else if (yaml.contains("customdata.emwm_template")) {
            inheritTemplate = yaml.getString("customdata.emwm_template");
        }

        // 检查是否有emwm配置段
        ConfigurationSection emwmSection = yaml.getConfigurationSection("emwm");
        if (emwmSection == null) {
            // 没有emwm段，检查根级别是否有EMWM相关字段（weaponPool/weapon/fireRate等）
            boolean hasRootLevelEMWM = yaml.contains("weaponPool") || yaml.contains("weapon")
                    || yaml.contains("fireRate") || yaml.contains("aggressiveness");
            if (hasRootLevelEMWM) {
                // 根级别字段当作emwm段解析
                emwmSection = yaml;
            }
        }

        if (emwmSection != null) {
            EMWMWeaponConfig config = parseEMWMConfig(emwmSection);
            if (config != null) {
                mobConfigs.put(fileName, config);
                plugin.debug("[EMWMConfigCache] 已加载怪物配置: " + fileName + " → " + config.getWeaponPool());

                if (inheritTemplate != null) {
                    templateInheritance.put(fileName, inheritTemplate);
                }
                return;
            }
        }

        // 没有个体配置，仅记录模板继承
        if (inheritTemplate != null) {
            templateInheritance.put(fileName, inheritTemplate);
            plugin.debug("[EMWMConfigCache] 怪物 " + fileName + " 继承模板: " + inheritTemplate);
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
            // 需求4：永不撤退开关（GreyZone 狂信徒/死守单位）
            if (tacticsSection.contains("neverRetreat")) {
                config.setNeverRetreat(tacticsSection.getBoolean("neverRetreat"));
            } else if (tacticsSection.contains("never-retreat")) {
                config.setNeverRetreat(tacticsSection.getBoolean("never-retreat"));
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
        String templateName = templateInheritance.get(mobFileName);

        // 如果同时有个体配置和模板继承，合并模板字段（模板基线 + 个体覆盖）
        if (config != null && templateName != null) {
            EMWMWeaponConfig templateConfig = globalTemplates.get(templateName);
            if (templateConfig != null) {
                EMWMWeaponConfig merged = new EMWMWeaponConfig();
                // 先复制个体配置（浅拷贝字段值）
                merged.setWeaponPool(new ArrayList<>(config.getWeaponPool()));
                merged.setWeaponWeights(new HashMap<>(config.getWeaponWeights()));
                if (config.getMagazineSize() != null) merged.setMagazineSize(config.getMagazineSize());
                if (config.getReloadDuration() != null) merged.setReloadDuration(config.getReloadDuration());
                if (config.isAutoReload() != null) merged.setAutoReload(config.isAutoReload());
                if (config.getFireRate() != null) merged.setFireRate(config.getFireRate());
                if (config.getSpread() != null) merged.setSpread(config.getSpread());
                if (config.getAdsSpreadMultiplier() != null) merged.setAdsSpreadMultiplier(config.getAdsSpreadMultiplier());
                if (config.getEffectiveRange() != null) merged.setEffectiveRange(config.getEffectiveRange());
                if (config.getMaxRange() != null) merged.setMaxRange(config.getMaxRange());
                if (config.getAdsRangeThreshold() != null) merged.setAdsRangeThreshold(config.getAdsRangeThreshold());
                if (config.getMeleeRange() != null) merged.setMeleeRange(config.getMeleeRange());
                if (config.isStandAndShoot() != null) merged.setStandAndShoot(config.isStandAndShoot());
                if (config.getSuppressHpThreshold() != null) merged.setSuppressHpThreshold(config.getSuppressHpThreshold());
                if (config.getRetreatHpThreshold() != null) merged.setRetreatHpThreshold(config.getRetreatHpThreshold());
                if (config.getAggressiveness() != null) merged.setAggressiveness(config.getAggressiveness());
                if (config.getCoverUsage() != null) merged.setCoverUsage(config.getCoverUsage());
                if (config.getSearchDuration() != null) merged.setSearchDuration(config.getSearchDuration());
                if (config.getFragInterval() != null) merged.setFragInterval(config.getFragInterval());
                if (config.getFlashInterval() != null) merged.setFlashInterval(config.getFlashInterval());
                if (config.getSmokeInterval() != null) merged.setSmokeInterval(config.getSmokeInterval());
                if (config.isThrowIfCover() != null) merged.setThrowIfCover(config.isThrowIfCover());
                if (config.getThrowMinRange() != null) merged.setThrowMinRange(config.getThrowMinRange());
                if (config.getThrowMaxRange() != null) merged.setThrowMaxRange(config.getThrowMaxRange());
                if (config.isCallReinforcements() != null) merged.setCallReinforcements(config.isCallReinforcements());
                if (config.isSquadLeader() != null) merged.setSquadLeader(config.isSquadLeader());
                if (config.isPreferLongRange() != null) merged.setPreferLongRange(config.isPreferLongRange());
                if (config.isPreferRush() != null) merged.setPreferRush(config.isPreferRush());
                if (config.getFireMode() != null) merged.setFireMode(config.getFireMode());
                if (config.getProjectileSpeed() != null) merged.setProjectileSpeed(config.getProjectileSpeed());
                if (config.getBulletPenetration() != null) merged.setBulletPenetration(config.getBulletPenetration());
                if (config.isMuzzleFlashEnabled() != null) merged.setMuzzleFlashEnabled(config.isMuzzleFlashEnabled());
                if (config.isSuppressed() != null) merged.setSuppressed(config.isSuppressed());
                if (config.isOnlyAimShoot() != null) merged.setOnlyAimShoot(config.isOnlyAimShoot());
                if (config.getDamageMultiplier() != null) merged.setDamageMultiplier(config.getDamageMultiplier());
                if (config.getRecoil() != null) merged.setRecoil(config.getRecoil());
                if (config.getAmmoType() != null) merged.setAmmoType(config.getAmmoType());
                if (config.getReserveAmmo() != null) merged.setReserveAmmo(config.getReserveAmmo());
                if (config.isCancelReloadOnMove() != null) merged.setCancelReloadOnMove(config.isCancelReloadOnMove());
                if (config.getEquipDelay() != null) merged.setEquipDelay(config.getEquipDelay());
                if (config.getAimDelay() != null) merged.setAimDelay(config.getAimDelay());
                if (config.getEquipAnimation() != null) merged.setEquipAnimation(config.getEquipAnimation());
                if (config.getAimAnimation() != null) merged.setAimAnimation(config.getAimAnimation());
                if (config.getReloadAnimation() != null) merged.setReloadAnimation(config.getReloadAnimation());
                if (config.getShootAnimation() != null) merged.setShootAnimation(config.getShootAnimation());
                if (config.getCrouchAimSpreadReduction() != null) merged.setCrouchAimSpreadReduction(config.getCrouchAimSpreadReduction());
                if (config.getDurabilityPerShot() != null) merged.setDurabilityPerShot(config.getDurabilityPerShot());
                if (config.isBreakOnZeroDurability() != null) merged.setBreakOnZeroDurability(config.isBreakOnZeroDurability());
                if (config.getAttachments() != null) merged.setAttachments(new ArrayList<>(config.getAttachments()));
                if (config.isEnableGrenadeAI() != null) merged.setEnableGrenadeAI(config.isEnableGrenadeAI());
                if (config.getGrenadeType() != null) merged.setGrenadeType(config.getGrenadeType());
                if (config.getGrenadeMaxRange() != null) merged.setGrenadeMaxRange(config.getGrenadeMaxRange());
                if (config.getGrenadeCooldownTicks() != null) merged.setGrenadeCooldownTicks(config.getGrenadeCooldownTicks());
                if (config.getGrenadeMinEnemyCoverTime() != null) merged.setGrenadeMinEnemyCoverTime(config.getGrenadeMinEnemyCoverTime());
                if (config.getMaxGrenadeCarry() != null) merged.setMaxGrenadeCarry(config.getMaxGrenadeCarry());
                if (config.getAllowedGrenadeTypes() != null) merged.setAllowedGrenadeTypes(new ArrayList<>(config.getAllowedGrenadeTypes()));
                if (config.getMeleeSwitchHealthPercent() != null) merged.setMeleeSwitchHealthPercent(config.getMeleeSwitchHealthPercent());
                if (config.getConsumeAmmo() != null) merged.setConsumeAmmo(config.getConsumeAmmo());
                if (config.getFaction() != null) merged.setFaction(config.getFaction());
                if (config.getNeverRetreat() != null) merged.setNeverRetreat(config.getNeverRetreat());
                if (config.getPersonalityPreset() != null) merged.setPersonalityPreset(config.getPersonalityPreset());
                if (config.getSquad() != null) merged.setSquad(config.getSquad());
                if (config.getBehavior() != null) merged.setBehavior(config.getBehavior());
                if (config.getGuardRadius() != null) merged.setGuardRadius(config.getGuardRadius());
                if (config.getAggroRadius() != null) merged.setAggroRadius(config.getAggroRadius());
                if (config.getLeashDistance() != null) merged.setLeashDistance(config.getLeashDistance());
                if (config.getLootAmmoType() != null) merged.setLootAmmoType(config.getLootAmmoType());
                if (config.getLootAmmoMin() != null) merged.setLootAmmoMin(config.getLootAmmoMin());
                if (config.getLootAmmoMax() != null) merged.setLootAmmoMax(config.getLootAmmoMax());
                if (config.getGearHelmet() != null) merged.setGearHelmet(config.getGearHelmet());
                if (config.getGearChestplate() != null) merged.setGearChestplate(config.getGearChestplate());
                if (config.getGearLeggings() != null) merged.setGearLeggings(config.getGearLeggings());
                if (config.getGearBoots() != null) merged.setGearBoots(config.getGearBoots());
                if (config.getGearDropGear() != null) merged.setGearDropGear(config.getGearDropGear());
                if (config.getGearMaxHealthBoost() != null) merged.setGearMaxHealthBoost(config.getGearMaxHealthBoost());

                // 用模板填充null字段
                merged.mergeWithTemplate(templateConfig);
                plugin.debug("[EMWMConfigCache] 合并配置: " + mobFileName + " (个体 + 模板:" + templateName + ")");
                return merged;
            }
        }

        // 只有个体配置，无模板
        if (config != null) return config;

        // 只有模板继承
        if (templateName != null) {
            EMWMWeaponConfig templateConfig = globalTemplates.get(templateName);
            if (templateConfig != null) {
                plugin.debug("[EMWMConfigCache] 怪物 " + mobFileName + " 使用模板配置: " + templateName);
                return templateConfig;
            }
        }

        // mobFileName 本身就是模板名（matchConfigByName 返回模板名时走这里）
        EMWMWeaponConfig directTemplate = globalTemplates.get(mobFileName);
        if (directTemplate != null) {
            plugin.debug("[EMWMConfigCache] 直接使用全局模板: " + mobFileName);
            return directTemplate;
        }

        // 模糊匹配：mobFileName 是 tier（如 "boss"），查找以 tier 开头的模板
        for (Map.Entry<String, EMWMWeaponConfig> entry : globalTemplates.entrySet()) {
            if (entry.getKey().toLowerCase().startsWith(mobFileName.toLowerCase() + "_")
                    || entry.getKey().equalsIgnoreCase(mobFileName)) {
                plugin.debug("[EMWMConfigCache] tier模糊匹配模板: " + mobFileName + " → " + entry.getKey());
                return entry.getValue();
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

        // 统一清理颜色代码和特殊字符
        String cleanName = customName.replace("&", "").replace("§", "").toLowerCase();

        // 尝试匹配配置文件名
        for (String fileName : mobConfigs.keySet()) {
            if (cleanName.contains(fileName.toLowerCase())) {
                return fileName;
            }
        }

        // 尝试匹配模板名
        for (String templateName : globalTemplates.keySet()) {
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
        if (mobConfigs.containsKey(mobFileName)
                || templateInheritance.containsKey(mobFileName)
                || globalTemplates.containsKey(mobFileName)) {
            return true;
        }
        // 模糊匹配：tier 前缀匹配模板名（如 "boss" → "boss_rifle"）
        for (String templateName : globalTemplates.keySet()) {
            if (templateName.toLowerCase().startsWith(mobFileName.toLowerCase() + "_")
                    || templateName.equalsIgnoreCase(mobFileName)) {
                return true;
            }
        }
        return false;
    }
}