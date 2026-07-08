package com.emwbridge.config;

import java.util.*;
import java.util.logging.Logger;

/**
 * EMWM武器配置数据模型实体类
 *
 * 映射emwm配置段所有字段：
 * - 武器池与权重
 * - 弹药参数
 * - 射击参数
 * - 战术参数
 * - 行为参数
 * - 投掷物参数（Boss专属）
 *
 * 参数优先级（从高到低）：
 * 1. 怪物单独emwm配置（显式填写的值）
 * 2. 全局兵种模板配置
 * 3. WM武器原生参数（通过WeaponMechanics API读取）
 * 4. 安全兜底默认值
 */
public class EMWMWeaponConfig {

    private static final Logger LOGGER = Logger.getLogger(EMWMWeaponConfig.class.getName());

    // 显式设置的字段标记（用于判断是否继承模板/WM参数）
    private final Set<String> explicitlySetFields = new HashSet<>();

    // 武器池配置
    private List<String> weaponPool = new ArrayList<>();
    private Map<String, Double> weaponWeights = new HashMap<>();

    // 弹药配置（Integer允许null，表示未设置，需从WM读取）
    private Integer magazineSize;
    private Integer reloadDuration;    // tick
    private Boolean autoReload;

    // 射击配置（Double/Integer允许null，表示未设置，需从WM读取）
    private Double fireRate;           // shots/second
    private Double spread;             // 度
    private Double adsSpreadMultiplier;
    private Integer effectiveRange;    // 格
    private Integer maxRange;          // 格
    private Integer adsRangeThreshold; // 格

    // 战术配置
    private Double meleeRange;         // 格
    private Boolean standAndShoot;
    private Double suppressHpThreshold;
    private Double retreatHpThreshold;

    // 行为配置
    private Double aggressiveness;
    private Double coverUsage;
    private Integer searchDuration;    // tick

    // 投掷物配置（Boss专属）
    private Integer fragInterval;      // tick
    private Integer flashInterval;     // tick
    private Integer smokeInterval;     // tick
    private Boolean throwIfCover;
    private Integer throwMinRange;     // 格
    private Integer throwMaxRange;     // 格

    // 特殊能力
    private Boolean callReinforcements;
    private Boolean squadLeader;
    private Boolean preferLongRange;
    private Boolean preferRush;

    // ==================== 扩展：射击模块（Shoot）====================
    private String fireMode;              // AUTO/SINGLE/BURST，null=跟随WM默认
    private Double projectileSpeed;       // 子弹飞行速度，null=WM原生
    private Double bulletPenetration;     // 方块穿透倍率，null=WM原生
    private Boolean muzzleFlashEnabled;   // 枪口火焰，null=WM原生
    private Boolean suppressed;           // 消音，null=WM原生
    private Boolean onlyAimShoot;         // 必须开镜才能射击
    private Double damageMultiplier;      // 伤害倍率，null=1.0
    private Double recoil;                // 后坐力系数，null=WM原生

    // ==================== 扩展：弹匣换弹模块（Reload）====================
    private String ammoType;              // 弹药类型（5.56x45等），null=无限制
    private Integer reserveAmmo;          // 备用弹药总量，null=无限（AI默认无限）
    private Boolean cancelReloadOnMove;   // 移动是否中断换弹，null=WM原生

    // ==================== 扩展：动作动画模块（Cosmetics）====================
    private Integer equipDelay;           // 拔枪延迟tick，null=WM原生
    private Integer aimDelay;             // 开镜延迟tick，null=WM原生
    private String equipAnimation;        // 拔枪动作ID
    private String aimAnimation;          // 瞄准动作ID
    private String reloadAnimation;       // 换弹动作ID
    private String shootAnimation;        // 开火后坐动作ID
    private Double crouchAimSpreadReduction; // 蹲姿散布降低系数，null=1.0

    // ==================== 扩展：耐久配件模块（Durability + Attachments）====================
    private Integer durabilityPerShot;    // 每发射一发消耗耐久，null=使用WM耐久
    private Boolean breakOnZeroDurability; // 耐久归零是否丢弃武器切近战，null=false
    private List<String> attachments;     // 强制绑定配件列表

    // ==================== 扩展：投掷物战术AI（WMPlus）====================
    private Boolean enableGrenadeAI;      // 是否开启AI手雷战术，null=false
    private String grenadeType;           // 默认投掷物：frag/flashbang/tear
    private Integer grenadeMaxRange;      // 最大投掷距离，null=25
    private Integer grenadeCooldownTicks; // 投掷冷却tick，null=120
    private Integer grenadeMinEnemyCoverTime; // 玩家躲掩体多久后AI扔雷tick，null=40
    private Integer maxGrenadeCarry;      // AI最多携带投掷物数量，null=2
    private List<String> allowedGrenadeTypes; // 可用投掷物类型列表，null=["frag","flashbang"]

    // ==================== 扩展：桥接业务控制参数 ====================
    private Double meleeSwitchHealthPercent; // 血量低于该比例强制切近战，null=0.3
    private Boolean consumeAmmo;             // 是否消耗弹药供给：AI 开火是否递减 emwm_ammo；null=true(默认有限,向后兼容)，GreyZone 模板设 false=无限

    // ==================== 武器池随机选择 ====================

    /**
     * 根据权重随机选择武器
     *
     * @return 选中的武器ID
     */
    public String getRandomWeapon() {
        if (weaponPool.isEmpty()) return null;

        // 无权重配置时均匀随机
        if (weaponWeights.isEmpty()) {
            int index = (int) (Math.random() * weaponPool.size());
            return weaponPool.get(index);
        }

        // 有权重配置时加权随机
        double totalWeight = 0;
        for (String weapon : weaponPool) {
            totalWeight += weaponWeights.getOrDefault(weapon, 1.0);
        }

        double random = Math.random() * totalWeight;
        double accumulated = 0;

        for (String weapon : weaponPool) {
            accumulated += weaponWeights.getOrDefault(weapon, 1.0);
            if (random <= accumulated) {
                return weapon;
            }
        }

        return weaponPool.get(0);
    }

    // ==================== 参数校验 ====================

    /**
     * 校验配置参数合法性，非法值回退默认值
     * 注意：null值不校验，保留用于后续继承/WM参数读取
     */
    public void validate() {
        // 武器池校验
        if (weaponPool.isEmpty()) {
            weaponPool = Collections.singletonList("MP5");
        }

        // 弹药校验（只校验非null的非法值）
        if (magazineSize != null && magazineSize <= 0) magazineSize = 30;
        if (reloadDuration != null && reloadDuration <= 0) reloadDuration = 60;

        // 射击校验（只校验非null的非法值）
        if (fireRate != null && fireRate <= 0) fireRate = 4.0;
        if (spread != null && spread < 0) spread = 5.0;
        if (adsSpreadMultiplier != null && adsSpreadMultiplier <= 0) adsSpreadMultiplier = 0.5;
        if (effectiveRange != null && effectiveRange <= 0) effectiveRange = 25;
        if (maxRange != null && maxRange <= 0) maxRange = 40;
        if (maxRange != null && effectiveRange != null && maxRange < effectiveRange) {
            maxRange = effectiveRange + 15;
        }
        if (adsRangeThreshold != null && adsRangeThreshold <= 0) adsRangeThreshold = 15;

        // 战术校验（只校验非null的非法值）
        if (meleeRange != null && meleeRange < 0) meleeRange = 3.0;
        if (suppressHpThreshold != null && (suppressHpThreshold < 0 || suppressHpThreshold > 1)) {
            suppressHpThreshold = 0.5;
        }
        if (retreatHpThreshold != null && (retreatHpThreshold < 0 || retreatHpThreshold > 1)) {
            retreatHpThreshold = 0.3;
        }
        if (retreatHpThreshold != null && suppressHpThreshold != null && retreatHpThreshold > suppressHpThreshold) {
            retreatHpThreshold = suppressHpThreshold - 0.1;
        }

        // 行为校验（只校验非null的非法值）
        if (aggressiveness != null && (aggressiveness < 0 || aggressiveness > 1)) aggressiveness = 0.6;
        if (coverUsage != null && (coverUsage < 0 || coverUsage > 1)) coverUsage = 0.4;
        if (searchDuration != null && searchDuration <= 0) searchDuration = 120;

        // 投掷物校验（只校验非null的非法值）
        if (fragInterval != null && fragInterval <= 0) fragInterval = 200;
        if (flashInterval != null && flashInterval <= 0) flashInterval = 150;
        if (smokeInterval != null && smokeInterval <= 0) smokeInterval = 300;
        if (throwMinRange != null && throwMinRange < 0) throwMinRange = 10;
        if (throwMaxRange != null && throwMinRange != null && throwMaxRange <= throwMinRange) {
            throwMaxRange = throwMinRange + 15;
        }

        // 手雷白名单校验
        validateAllowedGrenadeTypes();
    }

    /**
     * 校验allowedGrenadeTypes列表，过滤无效手雷类型
     * 合法类型: frag, flashbang, smoke
     * 过滤后列表为空则设为null（触发兜底默认值）
     */
    private void validateAllowedGrenadeTypes() {
        if (allowedGrenadeTypes == null) return;

        List<String> validTypes = Arrays.asList("frag", "flashbang", "smoke");
        List<String> filtered = new ArrayList<>();

        for (String type : allowedGrenadeTypes) {
            if (validTypes.contains(type)) {
                filtered.add(type);
            } else {
                LOGGER.warning("[EMWM] 无效的手雷类型: " + type + "，已过滤（合法类型: frag, flashbang, smoke）");
            }
        }

        if (filtered.isEmpty()) {
            allowedGrenadeTypes = null; // 触发兜底默认值
        } else {
            allowedGrenadeTypes = filtered;
        }
    }

    /**
     * 合并模板配置（怪物自身配置覆盖模板默认值）
     * null值表示未设置，从模板继承；非null值表示显式设置，保留
     *
     * @param template 全局模板配置
     */
    public void mergeWithTemplate(EMWMWeaponConfig template) {
        if (template == null) return;

        // 武器池：怪物未配置时继承模板
        if (weaponPool.isEmpty()) {
            weaponPool = template.weaponPool;
        }
        if (weaponWeights.isEmpty()) {
            weaponWeights = template.weaponWeights;
        }

        // 弹药：怪物未配置（null）时继承模板
        if (magazineSize == null) {
            magazineSize = template.magazineSize;
        }
        if (reloadDuration == null) {
            reloadDuration = template.reloadDuration;
        }
        if (autoReload == null) {
            autoReload = template.autoReload;
        }

        // 射击：怪物未配置（null）时继承模板
        if (fireRate == null) {
            fireRate = template.fireRate;
        }
        if (spread == null) {
            spread = template.spread;
        }
        if (adsSpreadMultiplier == null) {
            adsSpreadMultiplier = template.adsSpreadMultiplier;
        }
        if (effectiveRange == null) {
            effectiveRange = template.effectiveRange;
        }
        if (maxRange == null) {
            maxRange = template.maxRange;
        }
        if (adsRangeThreshold == null) {
            adsRangeThreshold = template.adsRangeThreshold;
        }

        // 战术：怪物未配置（null）时继承模板
        if (meleeRange == null) {
            meleeRange = template.meleeRange;
        }
        if (standAndShoot == null) {
            standAndShoot = template.standAndShoot;
        }
        if (suppressHpThreshold == null) {
            suppressHpThreshold = template.suppressHpThreshold;
        }
        if (retreatHpThreshold == null) {
            retreatHpThreshold = template.retreatHpThreshold;
        }

        // 行为：怪物未配置（null）时继承模板
        if (aggressiveness == null) {
            aggressiveness = template.aggressiveness;
        }
        if (coverUsage == null) {
            coverUsage = template.coverUsage;
        }
        if (searchDuration == null) {
            searchDuration = template.searchDuration;
        }

        // 特殊能力继承
        if (callReinforcements == null) {
            callReinforcements = template.callReinforcements;
        }
        if (squadLeader == null) {
            squadLeader = template.squadLeader;
        }
        if (preferLongRange == null) {
            preferLongRange = template.preferLongRange;
        }
        if (preferRush == null) {
            preferRush = template.preferRush;
        }

        // 投掷物继承
        if (fragInterval == null) {
            fragInterval = template.fragInterval;
        }
        if (flashInterval == null) {
            flashInterval = template.flashInterval;
        }
        if (smokeInterval == null) {
            smokeInterval = template.smokeInterval;
        }
        if (throwIfCover == null) {
            throwIfCover = template.throwIfCover;
        }
        if (throwMinRange == null) {
            throwMinRange = template.throwMinRange;
        }
        if (throwMaxRange == null) {
            throwMaxRange = template.throwMaxRange;
        }

        // 扩展射击模块（Shoot）
        if (fireMode == null) fireMode = template.fireMode;
        if (projectileSpeed == null) projectileSpeed = template.projectileSpeed;
        if (bulletPenetration == null) bulletPenetration = template.bulletPenetration;
        if (muzzleFlashEnabled == null) muzzleFlashEnabled = template.muzzleFlashEnabled;
        if (suppressed == null) suppressed = template.suppressed;
        if (onlyAimShoot == null) onlyAimShoot = template.onlyAimShoot;
        if (damageMultiplier == null) damageMultiplier = template.damageMultiplier;
        if (recoil == null) recoil = template.recoil;

        // 扩展弹匣模块（Reload）
        if (ammoType == null) ammoType = template.ammoType;
        if (reserveAmmo == null) reserveAmmo = template.reserveAmmo;
        if (cancelReloadOnMove == null) cancelReloadOnMove = template.cancelReloadOnMove;

        // 扩展动画模块（Cosmetics）
        if (equipDelay == null) equipDelay = template.equipDelay;
        if (aimDelay == null) aimDelay = template.aimDelay;
        if (equipAnimation == null) equipAnimation = template.equipAnimation;
        if (aimAnimation == null) aimAnimation = template.aimAnimation;
        if (reloadAnimation == null) reloadAnimation = template.reloadAnimation;
        if (shootAnimation == null) shootAnimation = template.shootAnimation;
        if (crouchAimSpreadReduction == null) crouchAimSpreadReduction = template.crouchAimSpreadReduction;

        // 扩展耐久配件模块
        if (durabilityPerShot == null) durabilityPerShot = template.durabilityPerShot;
        if (breakOnZeroDurability == null) breakOnZeroDurability = template.breakOnZeroDurability;
        if (attachments == null || attachments.isEmpty()) {
            attachments = template.attachments;
        }

        // 扩展投掷物模块
        if (enableGrenadeAI == null) enableGrenadeAI = template.enableGrenadeAI;
        if (grenadeType == null) grenadeType = template.grenadeType;
        if (grenadeMaxRange == null) grenadeMaxRange = template.grenadeMaxRange;
        if (grenadeCooldownTicks == null) grenadeCooldownTicks = template.grenadeCooldownTicks;
        if (grenadeMinEnemyCoverTime == null) grenadeMinEnemyCoverTime = template.grenadeMinEnemyCoverTime;
        if (maxGrenadeCarry == null) maxGrenadeCarry = template.maxGrenadeCarry;
        if (allowedGrenadeTypes == null) allowedGrenadeTypes = template.allowedGrenadeTypes;

        // 扩展桥接控制参数
        if (meleeSwitchHealthPercent == null) meleeSwitchHealthPercent = template.meleeSwitchHealthPercent;
        if (consumeAmmo == null) consumeAmmo = template.consumeAmmo;
    }

    // ==================== Getter/Setter ====================

    public List<String> getWeaponPool() { return weaponPool; }
    public void setWeaponPool(List<String> weaponPool) { this.weaponPool = weaponPool; }

    public Map<String, Double> getWeaponWeights() { return weaponWeights; }
    public void setWeaponWeights(Map<String, Double> weaponWeights) { this.weaponWeights = weaponWeights; }

    public Integer getMagazineSize() { return magazineSize; }
    public int getMagazineSizeOrDefault() { return magazineSize != null ? magazineSize : 30; }
    public void setMagazineSize(Integer magazineSize) { this.magazineSize = magazineSize; explicitlySetFields.add("magazineSize"); }

    public Integer getReloadDuration() { return reloadDuration; }
    public int getReloadDurationOrDefault() { return reloadDuration != null ? reloadDuration : 60; }
    public void setReloadDuration(Integer reloadDuration) { this.reloadDuration = reloadDuration; explicitlySetFields.add("reloadDuration"); }

    public Boolean isAutoReload() { return autoReload; }
    public boolean isAutoReloadOrDefault() { return autoReload != null ? autoReload : true; }
    public void setAutoReload(Boolean autoReload) { this.autoReload = autoReload; explicitlySetFields.add("autoReload"); }

    public Double getFireRate() { return fireRate; }
    public double getFireRateOrDefault() { return fireRate != null ? fireRate : 4.0; }
    public void setFireRate(Double fireRate) { this.fireRate = fireRate; explicitlySetFields.add("fireRate"); }

    public Double getSpread() { return spread; }
    public double getSpreadOrDefault() { return spread != null ? spread : 5.0; }
    public void setSpread(Double spread) { this.spread = spread; explicitlySetFields.add("spread"); }

    public Double getAdsSpreadMultiplier() { return adsSpreadMultiplier; }
    public double getAdsSpreadMultiplierOrDefault() { return adsSpreadMultiplier != null ? adsSpreadMultiplier : 0.5; }
    public void setAdsSpreadMultiplier(Double adsSpreadMultiplier) { this.adsSpreadMultiplier = adsSpreadMultiplier; explicitlySetFields.add("adsSpreadMultiplier"); }

    public Integer getEffectiveRange() { return effectiveRange; }
    public int getEffectiveRangeOrDefault() { return effectiveRange != null ? effectiveRange : 25; }
    public void setEffectiveRange(Integer effectiveRange) { this.effectiveRange = effectiveRange; explicitlySetFields.add("effectiveRange"); }

    public Integer getMaxRange() { return maxRange; }
    public int getMaxRangeOrDefault() { return maxRange != null ? maxRange : 40; }
    public void setMaxRange(Integer maxRange) { this.maxRange = maxRange; explicitlySetFields.add("maxRange"); }

    public Integer getAdsRangeThreshold() { return adsRangeThreshold; }
    public int getAdsRangeThresholdOrDefault() { return adsRangeThreshold != null ? adsRangeThreshold : 15; }
    public void setAdsRangeThreshold(Integer adsRangeThreshold) { this.adsRangeThreshold = adsRangeThreshold; explicitlySetFields.add("adsRangeThreshold"); }

    public Double getMeleeRange() { return meleeRange; }
    public double getMeleeRangeOrDefault() { return meleeRange != null ? meleeRange : 3.0; }
    public void setMeleeRange(Double meleeRange) { this.meleeRange = meleeRange; explicitlySetFields.add("meleeRange"); }

    public Boolean isStandAndShoot() { return standAndShoot; }
    public boolean isStandAndShootOrDefault() { return standAndShoot != null ? standAndShoot : true; }
    public void setStandAndShoot(Boolean standAndShoot) { this.standAndShoot = standAndShoot; explicitlySetFields.add("standAndShoot"); }

    public Double getSuppressHpThreshold() { return suppressHpThreshold; }
    public double getSuppressHpThresholdOrDefault() { return suppressHpThreshold != null ? suppressHpThreshold : 0.5; }
    public void setSuppressHpThreshold(Double suppressHpThreshold) { this.suppressHpThreshold = suppressHpThreshold; explicitlySetFields.add("suppressHpThreshold"); }

    public Double getRetreatHpThreshold() { return retreatHpThreshold; }
    public double getRetreatHpThresholdOrDefault() { return retreatHpThreshold != null ? retreatHpThreshold : 0.3; }
    public void setRetreatHpThreshold(Double retreatHpThreshold) { this.retreatHpThreshold = retreatHpThreshold; explicitlySetFields.add("retreatHpThreshold"); }

    public Double getAggressiveness() { return aggressiveness; }
    public double getAggressivenessOrDefault() { return aggressiveness != null ? aggressiveness : 0.6; }
    public void setAggressiveness(Double aggressiveness) { this.aggressiveness = aggressiveness; explicitlySetFields.add("aggressiveness"); }

    public Double getCoverUsage() { return coverUsage; }
    public double getCoverUsageOrDefault() { return coverUsage != null ? coverUsage : 0.4; }
    public void setCoverUsage(Double coverUsage) { this.coverUsage = coverUsage; explicitlySetFields.add("coverUsage"); }

    public Integer getSearchDuration() { return searchDuration; }
    public int getSearchDurationOrDefault() { return searchDuration != null ? searchDuration : 120; }
    public void setSearchDuration(Integer searchDuration) { this.searchDuration = searchDuration; explicitlySetFields.add("searchDuration"); }

    public Integer getFragInterval() { return fragInterval; }
    public int getFragIntervalOrDefault() { return fragInterval != null ? fragInterval : 200; }
    public void setFragInterval(Integer fragInterval) { this.fragInterval = fragInterval; explicitlySetFields.add("fragInterval"); }

    public Integer getFlashInterval() { return flashInterval; }
    public int getFlashIntervalOrDefault() { return flashInterval != null ? flashInterval : 150; }
    public void setFlashInterval(Integer flashInterval) { this.flashInterval = flashInterval; explicitlySetFields.add("flashInterval"); }

    public Integer getSmokeInterval() { return smokeInterval; }
    public int getSmokeIntervalOrDefault() { return smokeInterval != null ? smokeInterval : 300; }
    public void setSmokeInterval(Integer smokeInterval) { this.smokeInterval = smokeInterval; explicitlySetFields.add("smokeInterval"); }

    public Boolean isThrowIfCover() { return throwIfCover; }
    public boolean isThrowIfCoverOrDefault() { return throwIfCover != null ? throwIfCover : true; }
    public void setThrowIfCover(Boolean throwIfCover) { this.throwIfCover = throwIfCover; explicitlySetFields.add("throwIfCover"); }

    public Integer getThrowMinRange() { return throwMinRange; }
    public int getThrowMinRangeOrDefault() { return throwMinRange != null ? throwMinRange : 10; }
    public void setThrowMinRange(Integer throwMinRange) { this.throwMinRange = throwMinRange; explicitlySetFields.add("throwMinRange"); }

    public Integer getThrowMaxRange() { return throwMaxRange; }
    public int getThrowMaxRangeOrDefault() { return throwMaxRange != null ? throwMaxRange : 25; }
    public void setThrowMaxRange(Integer throwMaxRange) { this.throwMaxRange = throwMaxRange; explicitlySetFields.add("throwMaxRange"); }

    public Boolean isCallReinforcements() { return callReinforcements; }
    public boolean isCallReinforcementsOrDefault() { return callReinforcements != null ? callReinforcements : false; }
    public void setCallReinforcements(Boolean callReinforcements) { this.callReinforcements = callReinforcements; explicitlySetFields.add("callReinforcements"); }

    public Boolean isSquadLeader() { return squadLeader; }
    public boolean isSquadLeaderOrDefault() { return squadLeader != null ? squadLeader : false; }
    public void setSquadLeader(Boolean squadLeader) { this.squadLeader = squadLeader; explicitlySetFields.add("squadLeader"); }

    public Boolean isPreferLongRange() { return preferLongRange; }
    public boolean isPreferLongRangeOrDefault() { return preferLongRange != null ? preferLongRange : false; }
    public void setPreferLongRange(Boolean preferLongRange) { this.preferLongRange = preferLongRange; explicitlySetFields.add("preferLongRange"); }

    public Boolean isPreferRush() { return preferRush; }
    public boolean isPreferRushOrDefault() { return preferRush != null ? preferRush : false; }
    public void setPreferRush(Boolean preferRush) { this.preferRush = preferRush; explicitlySetFields.add("preferRush"); }

    // ==================== 扩展模块 Getter/Setter ====================

    public String getFireMode() { return fireMode; }
    public void setFireMode(String fireMode) { this.fireMode = fireMode; explicitlySetFields.add("fireMode"); }

    public Double getProjectileSpeed() { return projectileSpeed; }
    public void setProjectileSpeed(Double projectileSpeed) { this.projectileSpeed = projectileSpeed; explicitlySetFields.add("projectileSpeed"); }

    public Double getBulletPenetration() { return bulletPenetration; }
    public void setBulletPenetration(Double bulletPenetration) { this.bulletPenetration = bulletPenetration; explicitlySetFields.add("bulletPenetration"); }

    public Boolean isMuzzleFlashEnabled() { return muzzleFlashEnabled; }
    public boolean isMuzzleFlashEnabledOrDefault() { return muzzleFlashEnabled != null ? muzzleFlashEnabled : true; }
    public void setMuzzleFlashEnabled(Boolean muzzleFlashEnabled) { this.muzzleFlashEnabled = muzzleFlashEnabled; explicitlySetFields.add("muzzleFlashEnabled"); }

    public Boolean isSuppressed() { return suppressed; }
    public boolean isSuppressedOrDefault() { return suppressed != null ? suppressed : false; }
    public void setSuppressed(Boolean suppressed) { this.suppressed = suppressed; explicitlySetFields.add("suppressed"); }

    public Boolean isOnlyAimShoot() { return onlyAimShoot; }
    public boolean isOnlyAimShootOrDefault() { return onlyAimShoot != null ? onlyAimShoot : false; }
    public void setOnlyAimShoot(Boolean onlyAimShoot) { this.onlyAimShoot = onlyAimShoot; explicitlySetFields.add("onlyAimShoot"); }

    public Double getDamageMultiplier() { return damageMultiplier; }
    public double getDamageMultiplierOrDefault() { return damageMultiplier != null ? damageMultiplier : 1.0; }
    public void setDamageMultiplier(Double damageMultiplier) { this.damageMultiplier = damageMultiplier; explicitlySetFields.add("damageMultiplier"); }

    public Double getRecoil() { return recoil; }
    public void setRecoil(Double recoil) { this.recoil = recoil; explicitlySetFields.add("recoil"); }

    public String getAmmoType() { return ammoType; }
    public void setAmmoType(String ammoType) { this.ammoType = ammoType; explicitlySetFields.add("ammoType"); }

    public Integer getReserveAmmo() { return reserveAmmo; }
    public int getReserveAmmoOrDefault() { return reserveAmmo != null ? reserveAmmo : Integer.MAX_VALUE; }
    public void setReserveAmmo(Integer reserveAmmo) { this.reserveAmmo = reserveAmmo; explicitlySetFields.add("reserveAmmo"); }

    public Boolean isCancelReloadOnMove() { return cancelReloadOnMove; }
    public boolean isCancelReloadOnMoveOrDefault() { return cancelReloadOnMove != null ? cancelReloadOnMove : true; }
    public void setCancelReloadOnMove(Boolean cancelReloadOnMove) { this.cancelReloadOnMove = cancelReloadOnMove; explicitlySetFields.add("cancelReloadOnMove"); }

    public Integer getEquipDelay() { return equipDelay; }
    public void setEquipDelay(Integer equipDelay) { this.equipDelay = equipDelay; explicitlySetFields.add("equipDelay"); }

    public Integer getAimDelay() { return aimDelay; }
    public void setAimDelay(Integer aimDelay) { this.aimDelay = aimDelay; explicitlySetFields.add("aimDelay"); }

    public String getEquipAnimation() { return equipAnimation; }
    public void setEquipAnimation(String equipAnimation) { this.equipAnimation = equipAnimation; explicitlySetFields.add("equipAnimation"); }

    public String getAimAnimation() { return aimAnimation; }
    public void setAimAnimation(String aimAnimation) { this.aimAnimation = aimAnimation; explicitlySetFields.add("aimAnimation"); }

    public String getReloadAnimation() { return reloadAnimation; }
    public void setReloadAnimation(String reloadAnimation) { this.reloadAnimation = reloadAnimation; explicitlySetFields.add("reloadAnimation"); }

    public String getShootAnimation() { return shootAnimation; }
    public void setShootAnimation(String shootAnimation) { this.shootAnimation = shootAnimation; explicitlySetFields.add("shootAnimation"); }

    public Double getCrouchAimSpreadReduction() { return crouchAimSpreadReduction; }
    public double getCrouchAimSpreadReductionOrDefault() { return crouchAimSpreadReduction != null ? crouchAimSpreadReduction : 1.0; }
    public void setCrouchAimSpreadReduction(Double crouchAimSpreadReduction) { this.crouchAimSpreadReduction = crouchAimSpreadReduction; explicitlySetFields.add("crouchAimSpreadReduction"); }

    public Integer getDurabilityPerShot() { return durabilityPerShot; }
    public void setDurabilityPerShot(Integer durabilityPerShot) { this.durabilityPerShot = durabilityPerShot; explicitlySetFields.add("durabilityPerShot"); }

    public Boolean isBreakOnZeroDurability() { return breakOnZeroDurability; }
    public boolean isBreakOnZeroDurabilityOrDefault() { return breakOnZeroDurability != null ? breakOnZeroDurability : false; }
    public void setBreakOnZeroDurability(Boolean breakOnZeroDurability) { this.breakOnZeroDurability = breakOnZeroDurability; explicitlySetFields.add("breakOnZeroDurability"); }

    public List<String> getAttachments() { return attachments; }
    public void setAttachments(List<String> attachments) { this.attachments = attachments; explicitlySetFields.add("attachments"); }

    public Boolean isEnableGrenadeAI() { return enableGrenadeAI; }
    public boolean isEnableGrenadeAIOrDefault() { return enableGrenadeAI != null ? enableGrenadeAI : false; }
    public void setEnableGrenadeAI(Boolean enableGrenadeAI) { this.enableGrenadeAI = enableGrenadeAI; explicitlySetFields.add("enableGrenadeAI"); }

    public String getGrenadeType() { return grenadeType; }
    public void setGrenadeType(String grenadeType) { this.grenadeType = grenadeType; explicitlySetFields.add("grenadeType"); }

    public Integer getGrenadeMaxRange() { return grenadeMaxRange; }
    public int getGrenadeMaxRangeOrDefault() { return grenadeMaxRange != null ? grenadeMaxRange : 25; }
    public void setGrenadeMaxRange(Integer grenadeMaxRange) { this.grenadeMaxRange = grenadeMaxRange; explicitlySetFields.add("grenadeMaxRange"); }

    public Integer getGrenadeCooldownTicks() { return grenadeCooldownTicks; }
    public int getGrenadeCooldownTicksOrDefault() { return grenadeCooldownTicks != null ? grenadeCooldownTicks : 120; }
    public void setGrenadeCooldownTicks(Integer grenadeCooldownTicks) { this.grenadeCooldownTicks = grenadeCooldownTicks; explicitlySetFields.add("grenadeCooldownTicks"); }

    public Integer getGrenadeMinEnemyCoverTime() { return grenadeMinEnemyCoverTime; }
    public int getGrenadeMinEnemyCoverTimeOrDefault() { return grenadeMinEnemyCoverTime != null ? grenadeMinEnemyCoverTime : 40; }
    public void setGrenadeMinEnemyCoverTime(Integer grenadeMinEnemyCoverTime) { this.grenadeMinEnemyCoverTime = grenadeMinEnemyCoverTime; explicitlySetFields.add("grenadeMinEnemyCoverTime"); }

    public Integer getMaxGrenadeCarry() { return maxGrenadeCarry; }
    public int getMaxGrenadeCarryOrDefault() { return maxGrenadeCarry != null ? maxGrenadeCarry : 2; }
    public void setMaxGrenadeCarry(Integer maxGrenadeCarry) { this.maxGrenadeCarry = maxGrenadeCarry; explicitlySetFields.add("maxGrenadeCarry"); }

    public List<String> getAllowedGrenadeTypes() { return allowedGrenadeTypes; }
    public List<String> getAllowedGrenadeTypesOrDefault() { return allowedGrenadeTypes != null ? allowedGrenadeTypes : Arrays.asList("frag", "flashbang"); }
    public void setAllowedGrenadeTypes(List<String> allowedGrenadeTypes) { this.allowedGrenadeTypes = allowedGrenadeTypes; explicitlySetFields.add("allowedGrenadeTypes"); }

    public Double getMeleeSwitchHealthPercent() { return meleeSwitchHealthPercent; }
    public double getMeleeSwitchHealthPercentOrDefault() { return meleeSwitchHealthPercent != null ? meleeSwitchHealthPercent : 0.3; }
    public void setMeleeSwitchHealthPercent(Double meleeSwitchHealthPercent) { this.meleeSwitchHealthPercent = meleeSwitchHealthPercent; explicitlySetFields.add("meleeSwitchHealthPercent"); }
    public Boolean getConsumeAmmo() { return consumeAmmo; }
    public boolean isConsumeAmmoOrDefault() { return consumeAmmo != null ? consumeAmmo : true; }
    public void setConsumeAmmo(Boolean consumeAmmo) { this.consumeAmmo = consumeAmmo; explicitlySetFields.add("consumeAmmo"); }

    /**
     * 计算射击间隔（tick）
     */
    public long getFireRateTicks() {
        return fireRate != null ? (long) (20.0 / fireRate) : 5L;
    }

    /**
     * 检查字段是否显式设置过
     */
    public boolean isFieldExplicitlySet(String fieldName) {
        return explicitlySetFields.contains(fieldName);
    }

    @Override
    public String toString() {
        return "EMWMWeaponConfig{" +
                "weaponPool=" + weaponPool +
                ", fireRate=" + fireRate +
                ", effectiveRange=" + effectiveRange +
                ", maxRange=" + maxRange +
                ", meleeRange=" + meleeRange +
                ", aggressiveness=" + aggressiveness +
                '}';
    }
}