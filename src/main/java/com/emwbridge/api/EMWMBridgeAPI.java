package com.emwbridge.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.MetadataValue;

import javax.annotation.Nullable;
import java.util.List;

/**
 * EM-WM-Bridge 公开 API
 *
 * 提供便捷方法读取精英怪的状态信息，供 Chemdah 等第三方插件调用。
 * 所有方法都是静态的，无需获取插件实例。
 *
 * 使用示例（Chemdah Kotlin 脚本）：
 *   val tier = EMWMBridgeAPI.getTier(entity)
 *   val reloading = EMWMBridgeAPI.isReloading(entity)
 *   val ammo = EMWMBridgeAPI.getAmmo(entity)
 *
 * 或直接读 metadata（效果相同）：
 *   entity.getMetadata("emwm_tier").firstOrNull()?.asString()
 */
public final class EMWMBridgeAPI {

    private EMWMBridgeAPI() {}

    // ==================== 基础信息 ====================

    /**
     * 获取精英怪的兵种类型
     * @return "scav" / "pmc" / "boss" / "raider" / "sniper"，非EMWM实体返回 null
     */
    @Nullable
    public static String getTier(LivingEntity entity) {
        return getStringMeta(entity, "emwm_tier");
    }

    /**
     * 获取精英怪当前战斗状态
     * @return "patrol" / "combat" / "suppressing" / "search" / "retreat"，非EMWM实体返回 null
     */
    @Nullable
    public static String getCombatState(LivingEntity entity) {
        return getStringMeta(entity, "emwm_combat_state");
    }

    /**
     * 获取精英怪绑定的武器标题（WM 武器名）
     */
    @Nullable
    public static String getWeapon(LivingEntity entity) {
        return getStringMeta(entity, "emwm_weapon");
    }

    /**
     * AI 是否启用
     */
    public static boolean isAIEnabled(LivingEntity entity) {
        return getBooleanMeta(entity, "emwm_ai_enabled");
    }

    /**
     * EMWM 配置是否已加载
     */
    public static boolean isConfigLoaded(LivingEntity entity) {
        return getBooleanMeta(entity, "emwm_config_loaded");
    }

    // ==================== 武器/弹药 ====================

    /**
     * 获取剩余弹药量
     */
    public static int getAmmo(LivingEntity entity) {
        return getIntMeta(entity, "emwm_ammo", 0);
    }

    /**
     * 获取武器耐久度
     */
    public static int getDurability(LivingEntity entity) {
        return getIntMeta(entity, "emwm_durability", 0);
    }

    /**
     * 是否正在换弹
     */
    public static boolean isReloading(LivingEntity entity) {
        return getBooleanMeta(entity, "emwm_reloading");
    }

    /**
     * 是否在瞄准状态（ADS）
     */
    public static boolean isADS(LivingEntity entity) {
        return getBooleanMeta(entity, "emwm_ads");
    }

    /**
     * 获取射速（tick 单位）
     */
    public static int getFireRateTicks(LivingEntity entity) {
        return getIntMeta(entity, "emwm_fire_rate_ticks", 10);
    }

    // ==================== 战术参数 ====================

    /**
     * 获取攻击性（0.0 - 1.0）
     */
    public static double getAggressiveness(LivingEntity entity) {
        return getDoubleMeta(entity, "emwm_aggressiveness", 0.5);
    }

    /**
     * 获取散布角度
     */
    public static double getSpread(LivingEntity entity) {
        return getDoubleMeta(entity, "emwm_spread", 2.0);
    }

    /**
     * 获取最大射程（格）
     */
    public static double getMaxRange(LivingEntity entity) {
        return getDoubleMeta(entity, "emwm_max_range", 40.0);
    }

    /**
     * 获取有效射程（格）
     */
    public static double getEffectiveRange(LivingEntity entity) {
        return getDoubleMeta(entity, "emwm_effective_range", 20.0);
    }

    /**
     * 获取近战切换距离（格）
     */
    public static double getMeleeRange(LivingEntity entity) {
        return getDoubleMeta(entity, "emwm_melee_range", 3.0);
    }

    /**
     * 获取压制血量阈值（0.0 - 1.0）
     */
    public static double getSuppressThreshold(LivingEntity entity) {
        return getDoubleMeta(entity, "emwm_suppress_threshold", 0.3);
    }

    /**
     * 获取撤退血量阈值（0.0 - 1.0）
     */
    public static double getRetreatThreshold(LivingEntity entity) {
        return getDoubleMeta(entity, "emwm_retreat_threshold", 0.15);
    }

    // ==================== 特殊状态 ====================

    /**
     * 是否处于耳鸣状态（被闪光弹影响）
     */
    public static boolean hasTinnitus(LivingEntity entity) {
        return getBooleanMeta(entity, "emwm_tinnitus");
    }

    /**
     * 获取允许使用的手雷类型（逗号分隔）
     * @return 如 "FRAG,FLASH,SMOKE"，非EMWM实体返回 null
     */
    @Nullable
    public static String getAllowedGrenadeTypes(LivingEntity entity) {
        return getStringMeta(entity, "emwm_allowed_grenade_types");
    }

    /**
     * 获取最后一次伤害类型（内部记录，供击杀事件使用）
     * @return "GUN" / "GRENADE" / "MELEE" / "EXPLOSION" / "OTHER"
     */
    @Nullable
    public static String getLastDamageType(LivingEntity entity) {
        return getStringMeta(entity, "emwm_last_damage_type");
    }

    // ==================== 便捷判断 ====================

    /**
     * 是否是 EMWM 管理的精英怪
     */
    public static boolean isEMWMMob(LivingEntity entity) {
        return entity.hasMetadata("emwm_ai_enabled");
    }

    /**
     * 是否是 Boss 级精英
     */
    public static boolean isBoss(LivingEntity entity) {
        String tier = getTier(entity);
        return "boss".equalsIgnoreCase(tier);
    }

    /**
     * 是否正在战斗中（非 patrol 状态）
     */
    public static boolean isInCombat(LivingEntity entity) {
        String state = getCombatState(entity);
        return state != null && !"patrol".equalsIgnoreCase(state);
    }

    /**
     * 是否处于压制状态
     */
    public static boolean isSuppressing(LivingEntity entity) {
        return "suppressing".equalsIgnoreCase(getCombatState(entity));
    }

    /**
     * 是否正在搜索玩家
     */
    public static boolean isSearching(LivingEntity entity) {
        return "search".equalsIgnoreCase(getCombatState(entity));
    }

    /**
     * 是否正在撤退
     */
    public static boolean isRetreating(LivingEntity entity) {
        return "retreat".equalsIgnoreCase(getCombatState(entity));
    }

    // ==================== Metadata 读取工具 ====================

    @Nullable
    private static String getStringMeta(LivingEntity entity, String key) {
        if (!entity.hasMetadata(key)) return null;
        for (MetadataValue mv : entity.getMetadata(key)) {
            if (mv.getOwningPlugin() != null && mv.getOwningPlugin().getName().equals("EM-WM-Bridge")) {
                return mv.asString();
            }
        }
        List<MetadataValue> values = entity.getMetadata(key);
        return values.isEmpty() ? null : values.get(0).asString();
    }

    private static int getIntMeta(LivingEntity entity, String key, int def) {
        String s = getStringMeta(entity, key);
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static double getDoubleMeta(LivingEntity entity, String key, double def) {
        String s = getStringMeta(entity, key);
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    private static boolean getBooleanMeta(LivingEntity entity, String key) {
        if (!entity.hasMetadata(key)) return false;
        for (MetadataValue mv : entity.getMetadata(key)) {
            if (mv.getOwningPlugin() != null && mv.getOwningPlugin().getName().equals("EM-WM-Bridge")) {
                return mv.asBoolean();
            }
        }
        List<MetadataValue> values = entity.getMetadata(key);
        return !values.isEmpty() && values.get(0).asBoolean();
    }
}
