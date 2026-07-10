package com.emwbridge.loot;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMWeaponConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/**
 * 需求6 死亡掉落货币弹管理器。
 *
 * <p>从 {@code greyzone_ammos.yml} 加载两类数据：
 * <ul>
 *   <li>{@code ammos.<type>}：货币弹物品定义（材质 / 显示名 / 描述）—— 这些物品即 GreyZone 经济货币。</li>
 *   <li>{@code gun-ammo-map.<weaponTitle>}：某把枪死亡时掉落哪种弹药（lootAmmoType 缺省时回退）。</li>
 * </ul>
 *
 * <p>掉落物由桥接层直接构建（NOT 来自 WeaponMechanics 原生弹药 API），确保掉落的是 GreyZone 货币弹
 * 而非 WM 原生 RifleAmmo（见实施计划 U4 / 6.3）。文件缺失或加载失败时静默关闭掉落，不影响插件启动。
 *
 * <p>非 GreyZone 服务器：不随插件默认提供该文件 → 映射表为空 → 仅当模板显式 {@code loot.ammoType} 才会掉落。
 */
public class LootManager {

    /** 单次掉落数量硬上限：防通胀 / 防刷（同时作为白名单之外的最后闸门）。 */
    public static final int HARD_CAP = 64;

    /** lootAmmoMin/Max 缺省值（U5 规格 §3 = [8,24]，需测试服校准回填）。 */
    public static final int DEFAULT_MIN = 8;
    public static final int DEFAULT_MAX = 24;

    private final EMWMBridge plugin;
    final Map<String, AmmoItemDef> ammoDefs = new HashMap<>();
    final Map<String, String> gunAmmoMap = new HashMap<>();
    private Random random = new Random();

    public LootManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    /** 供单测注入确定性随机源。 */
    public void setRandom(Random random) {
        this.random = random;
    }

    public void reload() {
        ammoDefs.clear();
        gunAmmoMap.clear();
        try {
            File dataFolder = plugin.getDataFolder();
            if (dataFolder == null) return;
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File file = new File(dataFolder, "greyzone_ammos.yml");
            if (!file.exists()) {
                plugin.saveResource("greyzone_ammos.yml", false);
            }
            if (!file.exists()) return;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            ConfigurationSection ammos = yaml.getConfigurationSection("ammos");
            if (ammos != null) {
                for (String key : ammos.getKeys(false)) {
                    ConfigurationSection sec = ammos.getConfigurationSection(key);
                    if (sec == null) continue;
                    String matStr = sec.getString("material", "ARROW");
                    if (Material.matchMaterial(matStr) == null) {
                        plugin.getLogger().warning("[LootManager] 货币弹 '" + key + "' 材质无效: " + matStr + "，已跳过");
                        continue;
                    }
                    ammoDefs.put(key, new AmmoItemDef(matStr,
                            sec.getString("display-name"),
                            sec.getStringList("lore")));
                }
            }

            ConfigurationSection gunMap = yaml.getConfigurationSection("gun-ammo-map");
            if (gunMap != null) {
                for (String gun : gunMap.getKeys(false)) {
                    gunAmmoMap.put(gun, gunMap.getString(gun));
                }
            }
            plugin.getLogger().info("[LootManager] 已加载 " + ammoDefs.size() + " 种货币弹定义 + "
                    + gunAmmoMap.size() + " 条 gun→ammo 映射");
        } catch (Exception e) {
            // 加载失败（文件缺失/损坏，或测试环境 mock）时静默关闭掉落，不影响插件启动
            ammoDefs.clear();
            gunAmmoMap.clear();
            try {
                plugin.getLogger().warning("[LootManager] 加载 greyzone_ammos.yml 失败，死亡掉落关闭: " + e.getMessage());
            } catch (Exception ignored) {
                // 连 logger 都不可用（极端 mock 环境）则忽略
            }
        }
    }

    /**
     * 解析最终掉落弹药类型：模板显式 {@code lootAmmoType} 优先；否则回退到 gun→ammo 映射。
     * 两者皆空 → 返回 null（不掉落）。
     */
    public String resolveAmmoType(EMWMWeaponConfig config, String weaponTitle) {
        if (config != null && config.getLootAmmoType() != null) {
            return config.getLootAmmoType();
        }
        if (weaponTitle != null) {
            String mapped = gunAmmoMap.get(weaponTitle);
            if (mapped != null) return mapped;
        }
        return null;
    }

    /**
     * 计算掉落数量（纯逻辑，可在无服务器环境下单测）。
     * <ul>
     *   <li>区间随机 + 硬上限熔断（防通胀）。</li>
     *   <li>返回 &le;0 表示不应掉落。</li>
     * </ul>
     */
    int computeLootAmount(int min, int max) {
        int lo = Math.max(1, Math.min(min, max));
        int hi = Math.max(min, max);
        int amount = (lo >= hi) ? lo : (lo + random.nextInt(hi - lo + 1));
        return Math.min(amount, HARD_CAP); // 硬上限熔断（防通胀）
    }

    /**
     * 构建掉落物 ItemStack。
     * <ul>
     *   <li>白名单校验：ammoType 未在 {@code ammos} 定义或材质无效 → 返回 null（不掉落）。</li>
     *   <li>数量由 {@link #computeLootAmount} 计算（区间随机 + 硬上限）。</li>
     * </ul>
     */
    public ItemStack buildAmmoItem(String ammoType, int min, int max) {
        AmmoItemDef def = ammoDefs.get(ammoType);
        if (def == null) return null; // 白名单：未定义不掉落

        int amount = computeLootAmount(min, max);
        if (amount <= 0) return null;

        Material mat = Material.matchMaterial(def.materialName);
        if (mat == null) return null; // 材质无效不掉落

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (def.displayName != null) meta.setDisplayName(def.displayName);
            if (def.lore != null && !def.lore.isEmpty()) meta.setLore(def.lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 货币弹物品定义（材质名 + 显示名 + 描述）。包级可见以便单测直接注入。 */
    static class AmmoItemDef {
        final String materialName;
        final String displayName;
        final List<String> lore;

        AmmoItemDef(String materialName, String displayName, List<String> lore) {
            this.materialName = materialName;
            this.displayName = displayName;
            this.lore = lore;
        }
    }
}
