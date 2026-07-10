package com.emwbridge.loot;

import com.emwbridge.EMWMBridge;
import com.emwbridge.config.EMWMWeaponConfig;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/**
 * 需求7 护甲混用（ArmorMechanics）管理器。
 *
 * <p>从 {@code greyzone_armors.yml} 加载护甲物品定义（材质 / 显示名 / 描述），供桥接层在 EMWM 控制的怪物
 * 生成时套甲、死亡时掉落。本管理器只负责「物品定义 + 套甲 + 掉落」薄胶水。
 *
 * <p>护甲实际减伤依赖 ArmorMechanics 对非玩家实体的支持（验证门 7.4，待测试服确认）：
 * <ul>
 *   <li>若 AM 对非玩家实体生效 → 本类即为全部所需（成本≈0）。</li>
 *   <li>若 AM 仅限玩家实体 → 通过模板 {@code gear.maxHealthBoost > 0} 由桥接层补 NPC 最大生命（薄垫片）；
 *       严格不另建独立 NPC 护甲模型。</li>
 * </ul>
 *
 * <p>文件缺失或加载失败时静默关闭套甲/掉落，不影响插件启动（与 LootManager 一致）。
 */
public class GearManager {

    /** 4 个护甲槽位（与 config gear 块字段一致）。 */
    public static final List<String> GEAR_SLOTS =
            Collections.unmodifiableList(Arrays.asList("helmet", "chestplate", "leggings", "boots"));

    private final EMWMBridge plugin;
    final Map<String, ArmorItemDef> armorDefs = new HashMap<>();

    public GearManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        armorDefs.clear();
        try {
            File dataFolder = plugin.getDataFolder();
            if (dataFolder == null) return;
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File file = new File(dataFolder, "greyzone_armors.yml");
            if (!file.exists()) {
                plugin.saveResource("greyzone_armors.yml", false);
            }
            if (!file.exists()) return;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            ConfigurationSection armors = yaml.getConfigurationSection("armors");
            if (armors != null) {
                for (String key : armors.getKeys(false)) {
                    ConfigurationSection sec = armors.getConfigurationSection(key);
                    if (sec == null) continue;
                    String matStr = sec.getString("material", "AIR");
                    if (Material.matchMaterial(matStr) == null) {
                        plugin.getLogger().warning("[GearManager] 护甲 '" + key + "' 材质无效: " + matStr + "，已跳过");
                        continue;
                    }
                    armorDefs.put(key, new ArmorItemDef(matStr,
                            sec.getString("display-name"),
                            sec.getStringList("lore")));
                }
            }
            plugin.getLogger().info("[GearManager] 已加载 " + armorDefs.size() + " 种护甲定义");
        } catch (Exception e) {
            // 加载失败（文件缺失/损坏，或测试环境 mock）时静默关闭护甲，不影响插件启动
            armorDefs.clear();
            try {
                plugin.getLogger().warning("[GearManager] 加载 greyzone_armors.yml 失败，护甲套用关闭: " + e.getMessage());
            } catch (Exception ignored) {
                // 连 logger 都不可用（极端 mock 环境）则忽略
            }
        }
    }

    /**
     * 解析某槽位的最终护甲 key：直接读 config（已含模板继承）。
     * 返回 null 表示该槽位不穿护甲。
     */
    public String resolveSlotKey(EMWMWeaponConfig config, String slot) {
        if (config == null) return null;
        switch (slot) {
            case "helmet":     return config.getGearHelmet();
            case "chestplate": return config.getGearChestplate();
            case "leggings":   return config.getGearLeggings();
            case "boots":      return config.getGearBoots();
            default:           return null;
        }
    }

    /**
     * 死亡是否掉落护甲（默认 true，防玩家白嫖护甲经济）。
     */
    public boolean shouldDropGear(EMWMWeaponConfig config) {
        return config != null && config.getGearDropGearOrDefault();
    }

    /**
     * 构建护甲 ItemStack（运行时调用）。
     * <ul>
     *   <li>白名单校验：key 未在 {@code armors} 定义或材质无效 → 返回 null（不套/不掉）。</li>
     *   <li>数量为 1（护甲为单件装备）。</li>
     * </ul>
     */
    public ItemStack buildArmorItem(String armorKey) {
        ArmorItemDef def = armorDefs.get(armorKey);
        if (def == null) return null; // 白名单：未定义不套/不掉

        Material mat = Material.matchMaterial(def.materialName);
        if (mat == null) return null; // 材质无效不套/不掉

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (def.displayName != null) meta.setDisplayName(def.displayName);
            if (def.lore != null && !def.lore.isEmpty()) meta.setLore(def.lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 生成时套甲（运行时调用，仅 EMWM 控制怪；玩家交战路径不受影响）。
     * 逐槽读取 key → buildArmorItem → setXxx；某槽无 key/构造失败则跳过该槽。
     */
    public void equipGear(LivingEntity entity, EMWMWeaponConfig config) {
        if (entity == null || config == null) return;
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        for (String slot : GEAR_SLOTS) {
            String key = resolveSlotKey(config, slot);
            if (key == null) continue;
            ItemStack armor = buildArmorItem(key);
            if (armor == null) continue;
            setSlot(equipment, slot, armor);
        }
    }

    private void setSlot(EntityEquipment equipment, String slot, ItemStack armor) {
        switch (slot) {
            case "helmet":     equipment.setHelmet(armor); break;
            case "chestplate": equipment.setChestplate(armor); break;
            case "leggings":   equipment.setLeggings(armor); break;
            case "boots":      equipment.setBoots(armor); break;
            default: break;
        }
    }

    /**
     * 7.4 验证门垫片：当 AM 不对非玩家实体加血时，由桥接层补 NPC 额外最大生命。
     * boost=0 视为「信赖 AM」，无操作。运行时调用。
     */
    public void applyMaxHealthBoost(LivingEntity entity, int boost) {
        if (entity == null || boost <= 0) return;
        AttributeInstance maxHp = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp == null) return;
        double newMax = maxHp.getBaseValue() + boost;
        maxHp.setBaseValue(newMax); // 当前血量由 Bukkit 自动钳制到新上限，不强制回满
    }

    /** 护甲物品定义（材质名 + 显示名 + 描述）。包级可见以便单测直接注入。 */
    static class ArmorItemDef {
        final String materialName;
        final String displayName;
        final List<String> lore;

        ArmorItemDef(String materialName, String displayName, List<String> lore) {
            this.materialName = materialName;
            this.displayName = displayName;
            this.lore = lore;
        }
    }
}
