package com.emwbridge.config;

import com.emwbridge.EMWMBridge;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LuaPower解析工具类
 *
 * 当怪物没有emwm配置时，扫描EM Lua Power脚本中的WM武器参数作为降级策略
 */
public class LuaPowerParser {

    private final EMWMBridge plugin;

    // 缓存：怪物名 → LuaPower配置
    private final Map<String, EMWMWeaponConfig> luaCache = new HashMap<>();

    // Lua脚本中常见的WM武器参数模式
    private static final Pattern WEAPON_PATTERN = Pattern.compile("weapon\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern FIRE_RATE_PATTERN = Pattern.compile("fire_rate\\s*=\\s*(\\d+\\.?\\d*)");
    private static final Pattern RANGE_PATTERN = Pattern.compile("range\\s*=\\s*(\\d+)");
    private static final Pattern SPREAD_PATTERN = Pattern.compile("spread\\s*=\\s*(\\d+\\.?\\d*)");
    private static final Pattern DAMAGE_PATTERN = Pattern.compile("damage\\s*=\\s*(\\d+\\.?\\d*)");

    public LuaPowerParser(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 从怪物的powers脚本中提取WM武器配置
     *
     * @param mobName 怪物名称
     * @param powersList powers脚本列表
     * @return EMWMWeaponConfig，如果未找到返回null
     */
    public EMWMWeaponConfig parseFromPowers(String mobName, List<String> powersList) {
        if (powersList == null || powersList.isEmpty()) {
            return null;
        }

        // 检查缓存
        if (luaCache.containsKey(mobName)) {
            return luaCache.get(mobName);
        }

        EMWMWeaponConfig config = new EMWMWeaponConfig();

        for (String powerFile : powersList) {
            File file = findPowerFile(powerFile);
            if (file == null) continue;

            String content = readFileContent(file);
            if (content == null || content.isEmpty()) continue;

            parseLuaContent(content, config);
        }

        // 如果没有找到任何武器配置，返回null
        if (config.getWeaponPool().isEmpty()) {
            return null;
        }

        config.validate();
        luaCache.put(mobName, config);

        plugin.debug("[EMWM-LuaPower] 从Lua脚本解析到武器配置: " + mobName + " → " + config.getWeaponPool());
        return config;
    }

    /**
     * 查找LuaPower文件
     */
    private File findPowerFile(String powerName) {
        // 移除.yml后缀
        if (powerName.endsWith(".yml")) {
            powerName = powerName.substring(0, powerName.length() - 4);
        }

        // 尝试多种路径
        File emFolder = new File(plugin.getServer().getPluginsFolder(), "EliteMobs");
        String[] paths = {
                "powers/" + powerName + ".lua",
                "powers/" + powerName + ".yml",
                "content/powers/" + powerName + ".lua",
                "content/powers/" + powerName + ".yml",
                "custombosses/powers/" + powerName + ".lua",
                "custombosses/powers/" + powerName + ".yml"
        };

        for (String path : paths) {
            File file = new File(emFolder, path);
            if (file.exists()) {
                return file;
            }
        }

        return null;
    }

    /**
     * 读取文件内容
     */
    private String readFileContent(File file) {
        try {
            return java.nio.file.Files.readString(file.toPath());
        } catch (Exception e) {
            plugin.getLogger().warning("[EMWM-LuaPower] 读取文件失败: " + file.getName());
            return null;
        }
    }

    /**
     * 解析Lua脚本内容
     */
    private void parseLuaContent(String content, EMWMWeaponConfig config) {
        // 查找武器名称
        Matcher weaponMatcher = WEAPON_PATTERN.matcher(content);
        if (weaponMatcher.find()) {
            String weapon = weaponMatcher.group(1);
            if (!config.getWeaponPool().contains(weapon)) {
                config.getWeaponPool().add(weapon);
            }
        }

        // 查找射速
        Matcher fireRateMatcher = FIRE_RATE_PATTERN.matcher(content);
        if (fireRateMatcher.find()) {
            try {
                double fireRate = Double.parseDouble(fireRateMatcher.group(1));
                config.setFireRate(fireRate);
            } catch (NumberFormatException ignored) {}
        }

        // 查找射程
        Matcher rangeMatcher = RANGE_PATTERN.matcher(content);
        if (rangeMatcher.find()) {
            try {
                int range = Integer.parseInt(rangeMatcher.group(1));
                config.setEffectiveRange(range);
                config.setMaxRange(range + 15);
            } catch (NumberFormatException ignored) {}
        }

        // 查找散布
        Matcher spreadMatcher = SPREAD_PATTERN.matcher(content);
        if (spreadMatcher.find()) {
            try {
                double spread = Double.parseDouble(spreadMatcher.group(1));
                config.setSpread(spread);
            } catch (NumberFormatException ignored) {}
        }

        // 查找伤害（用于计算攻击性）
        Matcher damageMatcher = DAMAGE_PATTERN.matcher(content);
        if (damageMatcher.find()) {
            try {
                double damage = Double.parseDouble(damageMatcher.group(1));
                // 根据伤害值估算攻击性
                config.setAggressiveness(Math.min(1.0, damage / 20.0));
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * 从怪物配置文件中读取powers列表
     */
    public List<String> getPowersList(String mobFileName) {
        File emFolder = new File(plugin.getServer().getPluginsFolder(), "EliteMobs");
        File customBossesFolder = new File(emFolder, "custombosses");
        if (!customBossesFolder.exists()) {
            customBossesFolder = new File(emFolder, "content/custombosses");
        }

        File mobFile = new File(customBossesFolder, mobFileName + ".yml");
        if (!mobFile.exists()) {
            // 尝试其他子目录
            File[] files = customBossesFolder.listFiles((dir, name) -> name.equals(mobFileName + ".yml"));
            if (files != null && files.length > 0) {
                mobFile = files[0];
            } else {
                return null;
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(mobFile);
        return yaml.getStringList("powers");
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        luaCache.clear();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return luaCache.size();
    }
}