package com.emwbridge.config;

import com.emwbridge.EMWMBridge;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局武器元数据预缓存
 *
 * 核心职责：
 * 1. 服务端启动时预加载所有WM武器配置到内存，避免运行时重复反射读取
 * 2. 提供WM武器原生参数的快速读取方法
 * 3. 配合 EMWMWeaponConfig 实现三级参数优先级解析
 *
 * 三级参数优先级（从高到低）：
 * 1. 怪物emwm配置（显式填写的值，非null）
 * 2. 全局兵种模板配置
 * 3. WeaponMetaCache预缓存的WM武器原生参数
 * 4. 安全兜底默认值
 */
public class WeaponMetaCache {

    private final EMWMBridge plugin;

    // 武器ID → WM武器配置段缓存（所有武器配置段，按路径索引）
    private final Map<String, NativeWeaponData> weaponDataCache = new ConcurrentHashMap<>();

    // 已注册的武器ID列表
    private final Set<String> registeredWeapons = ConcurrentHashMap.newKeySet();

    // 待预加载的武器ID（用于延迟重试）
    private final Set<String> pendingWeaponIds = ConcurrentHashMap.newKeySet();

    public WeaponMetaCache(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 预加载所有用到的WM武器
     * 立即尝试加载；对失败的武器，分两轮延迟重试（等待WM就绪）
     */
    public void preloadWeapons(Collection<String> weaponIds) {
        plugin.debug("[WeaponMetaCache] 开始预加载 " + weaponIds.size() + " 个武器元数据");
        pendingWeaponIds.addAll(weaponIds);

        // 立即尝试加载
        for (String weaponId : weaponIds) {
            if (weaponId == null || weaponId.isEmpty()) continue;
            loadWeapon(weaponId);
        }

        int loaded = weaponDataCache.size();
        int failed = pendingWeaponIds.size();
        if (failed > 0) {
            // 第一轮重试：200 tick (10秒) 后，等待 WM 配置加载完成
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Set<String> retryIds = new HashSet<>(pendingWeaponIds);
                pendingWeaponIds.clear();
                for (String weaponId : retryIds) {
                    loadWeapon(weaponId);
                }
                int stillFailed = pendingWeaponIds.size();
                plugin.debug("[WeaponMetaCache] 第一轮重试完成，共缓存 " + weaponDataCache.size()
                        + " 个武器, 仍失败: " + stillFailed);

                // 第二轮兜底：再等 200 tick，处理极端慢加载情况
                if (stillFailed > 0) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        Set<String> finalIds = new HashSet<>(pendingWeaponIds);
                        pendingWeaponIds.clear();
                        for (String weaponId : finalIds) {
                            loadWeapon(weaponId);
                        }
                        plugin.debug("[WeaponMetaCache] 第二轮重试完成，共缓存 " + weaponDataCache.size()
                                + " 个武器, 仍失败: " + pendingWeaponIds.size());
                    }, 200L);
                }
            }, 200L);
        }

        plugin.debug("[WeaponMetaCache] 首轮预加载完成，共缓存 " + loaded + " 个武器, 待重试: " + failed);
    }

    /**
     * 加载单个武器元数据
     */
    private void loadWeapon(String weaponId) {
        if (registeredWeapons.contains(weaponId)) return;

        try {
            // 先用 generateWeapon 验证武器是否存在（此方法已证明可用）
            ItemStack testItem = WeaponMechanicsAPI.generateWeapon(weaponId);
            if (testItem == null || testItem.getType() == org.bukkit.Material.AIR) {
                plugin.debug("[WeaponMetaCache] 武器不存在: " + weaponId);
                return;
            }

            WeaponMechanics wm = WeaponMechanics.getInstance();
            if (wm == null) {
                plugin.debug("[WeaponMetaCache] WeaponMechanics 未就绪，延迟加载: " + weaponId);
                pendingWeaponIds.add(weaponId);
                return;
            }
            var config = wm.getWeaponConfigurations();
            NativeWeaponData data = new NativeWeaponData(weaponId);
            data.weaponType = "GUN";

            // 射速：先读全自动射速(Shoot下)，再读单发间隔(Shoot下)，再读Cooldown(秒)
            // 注意：WM getWeaponConfigurations() 返回的 Delay_Between_Shots 已是毫秒值(ticks×50)，无需再乘50
            double shotsPerSecond = config.getDouble(weaponId + ".Shoot.Fully_Automatic_Shots_Per_Second", 0);
            if (shotsPerSecond > 0) {
                data.fireRateMs = (long) (1000.0 / shotsPerSecond);
                data.fireMode = "AUTO";
            } else {
                int delayMs = config.getInt(weaponId + ".Shoot.Delay_Between_Shots", 0);
                if (delayMs > 0) {
                    data.fireRateMs = delayMs;
                } else {
                    // 部分简化配置用 Cooldown（单位：秒）
                    double cooldownSec = config.getDouble(weaponId + ".Shoot.Cooldown", 0);
                    if (cooldownSec > 0) {
                        data.fireRateMs = (long) (cooldownSec * 1000);
                    } else {
                        data.fireRateMs = 300L;
                    }
                }
                data.fireMode = "SINGLE";
            }

            // 弹匣
            data.magazineSize = config.getInt(weaponId + ".Reload.Magazine_Size", 30);
            data.reloadDuration = config.getInt(weaponId + ".Reload.Reload_Duration", 60);

            // 散布（Shoot.Spread.Base_Spread）
            data.baseSpread = config.getDouble(weaponId + ".Shoot.Spread.Base_Spread", 3.0);
            String zoomSpread = config.getString(weaponId + ".Shoot.Spread.Modify_Spread_When.Zooming", "50%");
            if (zoomSpread != null && zoomSpread.endsWith("%")) {
                double percent = Double.parseDouble(zoomSpread.replace("%", ""));
                data.adsSpreadMultiplier = percent / 100.0;
            } else {
                data.adsSpreadMultiplier = 0.5;
            }

            // 子弹速度（Shoot.Projectile_Speed）
            data.projectileSpeed = config.getDouble(weaponId + ".Shoot.Projectile_Speed", 100.0);

            // 子弹穿透
            data.bulletPenetration = config.getDouble(weaponId + ".Info.Penetration.Multiplier", 1.0);

            // 后坐力（Shoot.Recoil.Mean_X / Mean_Y）
            data.recoilPitch = config.getDouble(weaponId + ".Shoot.Recoil.Mean_Y", 1.0);
            data.recoilYaw = config.getDouble(weaponId + ".Shoot.Recoil.Mean_X", 0.0);

            // 最大射程（WM 标准配置无 Max_Range 字段，用 Shoot.Range 兜底，无则默认 40）
            data.maxRange = config.getInt(weaponId + ".Shoot.Range", 40);

            // 消音（WM 标准配置无此字段，默认 false）
            data.suppressed = false;

            // 动作延迟（Info.Weapon_Equip_Delay，注意字段名不是 Equip_Delay）
            data.equipDelay = config.getInt(weaponId + ".Info.Weapon_Equip_Delay", 0);
            data.aimDelay = 0;

            weaponDataCache.put(weaponId, data);
            registeredWeapons.add(weaponId);
            pendingWeaponIds.remove(weaponId);
            plugin.debug("[WeaponMetaCache] 已缓存武器: " + weaponId + " (射速=" + data.fireRateMs + "ms, 弹匣=" + data.magazineSize + ")");
        } catch (Exception e) {
            plugin.getLogger().warning("[WeaponMetaCache] 加载武器失败: " + weaponId + " - " + e.getMessage());
        }
    }

    /**
     * 使用三级参数优先级解析最终火速率(ms)
     * config非null字段 → template非null字段 → WM原生(Shoot.Fully_Automatic_Shots_Per_Second / Shoot.Delay_Between_Shots) → 默认300ms
     */
    public long resolveFireRateMs(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.getFireRate() != null) {
            return (long) (1000.0 / config.getFireRate());
        }
        if (template != null && template.getFireRate() != null) {
            return (long) (1000.0 / template.getFireRate());
        }
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        if (nativeData != null) return nativeData.fireRateMs;
        return 300L;
    }

    /**
     * 解析最终弹匣容量
     */
    public int resolveMagazineSize(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.getMagazineSize() != null) return config.getMagazineSize();
        if (template != null && template.getMagazineSize() != null) return template.getMagazineSize();
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        if (nativeData != null) return nativeData.magazineSize;
        return 30;
    }

    /**
     * 解析最终换弹时长(tick)
     */
    public int resolveReloadDuration(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.getReloadDuration() != null) return config.getReloadDuration();
        if (template != null && template.getReloadDuration() != null) return template.getReloadDuration();
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        if (nativeData != null) return nativeData.reloadDuration;
        return 60;
    }

    /**
     * 解析最终散布
     */
    public double resolveSpread(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.getSpread() != null) return config.getSpread();
        if (template != null && template.getSpread() != null) return template.getSpread();
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        if (nativeData != null) return nativeData.baseSpread;
        return 3.0;
    }

    /**
     * 解析最终ADS散布倍率
     */
    public double resolveAdsSpreadMultiplier(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.getAdsSpreadMultiplier() != null) return config.getAdsSpreadMultiplier();
        if (template != null && template.getAdsSpreadMultiplier() != null) return template.getAdsSpreadMultiplier();
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        if (nativeData != null) return nativeData.adsSpreadMultiplier;
        return 0.5;
    }

    /**
     * 解析最终最大射程
     */
    public int resolveMaxRange(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.getMaxRange() != null) return config.getMaxRange();
        if (template != null && template.getMaxRange() != null) return template.getMaxRange();
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        if (nativeData != null) return nativeData.maxRange;
        return 40;
    }

    /**
     * 解析最终弹速
     */
    public double resolveProjectileSpeed(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.getProjectileSpeed() != null) return config.getProjectileSpeed();
        if (template != null && template.getProjectileSpeed() != null) return template.getProjectileSpeed();
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        if (nativeData != null) return nativeData.projectileSpeed;
        return 100.0;
    }

    /**
     * 解析是否消音
     */
    public boolean resolveSuppressed(EMWMWeaponConfig config, EMWMWeaponConfig template, String weaponId) {
        if (config != null && config.isSuppressed() != null) return config.isSuppressed();
        if (template != null && template.isSuppressed() != null) return template.isSuppressed();
        NativeWeaponData nativeData = weaponDataCache.get(weaponId);
        return nativeData != null && nativeData.suppressed;
    }

    @Nullable
    public NativeWeaponData getNativeData(String weaponId) {
        return weaponDataCache.get(weaponId);
    }

    public boolean isWeaponLoaded(String weaponId) {
        return registeredWeapons.contains(weaponId);
    }

    public Set<String> getLoadedWeaponIds() {
        return Collections.unmodifiableSet(registeredWeapons);
    }

    public void clear() {
        weaponDataCache.clear();
        registeredWeapons.clear();
    }

    /**
     * WM武器原生数据模型
     * 存储从WM配置读取的不可变武器参数
     */
    public static class NativeWeaponData {
        public final String weaponId;
        public String weaponType;
        public long fireRateMs = 300L;
        public int magazineSize = 30;
        public int reloadDuration = 60;
        public double baseSpread = 3.0;
        public double adsSpreadMultiplier = 0.5;
        public double projectileSpeed = 100.0;
        public double bulletPenetration = 1.0;
        public double recoilPitch = 1.0;
        public double recoilYaw = 1.0;
        public int maxRange = 40;
        public boolean suppressed;
        public int equipDelay;
        public int aimDelay;
        public String fireMode = "SINGLE";

        public NativeWeaponData(String weaponId) {
            this.weaponId = weaponId;
        }
    }
}
