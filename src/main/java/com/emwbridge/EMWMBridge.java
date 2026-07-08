package com.emwbridge;

import com.emwbridge.config.EMWMConfigCache;
import com.emwbridge.listeners.EliteMobCombatListener;
import com.emwbridge.listeners.EliteMobSpawnListener;
import com.emwbridge.listeners.EMCommandListener;
import com.emwbridge.listeners.EMWMReloadListener;
import com.emwbridge.listeners.PlayerShootListener;
import com.emwbridge.managers.ConfigManager;
import com.emwbridge.managers.ExtremeEventManager;
import com.emwbridge.managers.FeatureManager;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager;
import com.emwbridge.mechanics.EMWMechanics;
import com.emwbridge.utils.DebugManager;
import com.emwbridge.utils.DebugManager.DebugLevel;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EMWMBridge extends JavaPlugin {

    private static EMWMBridge instance;

    private MobWeaponManager mobWeaponManager;
    private TarkovAIManager tarkovAIManager;
    private ExtremeEventManager extremeEventManager;
    private ConfigManager configManager;
    private DebugManager debugManager;
    private FeatureManager featureManager;
    private EMWMConfigCache emwmConfigCache;
    private EMWMReloadListener reloadListener;
    private boolean isFolia;

    @Override
    public void onEnable() {
        instance = this;

        // 检测Folia环境
        isFolia = detectFolia();
        getLogger().info("检测到运行环境: " + (isFolia ? "Folia" : "Paper"));

        // 初始化配置管理器并迁移
        configManager = new ConfigManager(this);
        configManager.loadAndMigrate();

        // 初始化DEBUG管理器
        debugManager = new DebugManager(this);

        if (!checkDependencies()) {
            getLogger().severe("缺少必需依赖插件，插件将禁用！");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化EMWM配置缓存（必须在 MobWeaponManager 之前，因为其构造函数依赖它）
        emwmConfigCache = new EMWMConfigCache(this);
        emwmConfigCache.loadAll();
        getLogger().info("[EMWM] 配置缓存初始化完成");

        // 注册自定义Mechanic和Targeter到MechanicsCore
        EMWMechanics.initialize();
        getLogger().info("已注册EMW自定义Mechanic和Targeter");

        // 初始化管理器
        mobWeaponManager = new MobWeaponManager(this);
        extremeEventManager = new ExtremeEventManager(this);
        tarkovAIManager = new TarkovAIManager(this);

        // 加载配置
        mobWeaponManager.reload();
        extremeEventManager.reload();
        tarkovAIManager.start();

        registerListeners();

        // 打印功能清单
        featureManager = new FeatureManager(this);
        featureManager.printFeatureSummary();

        getLogger().info("EM-WM-Bridge 已启用！");
        getLogger().info("版本: " + getDescription().getVersion() + " | 配置版本: " + configManager.getConfigVersion());
    }

    @Override
    public void onDisable() {
        if (tarkovAIManager != null) {
            tarkovAIManager.stop();
        }
        if (mobWeaponManager != null) {
            mobWeaponManager.shutdown();
        }
        if (extremeEventManager != null) {
            extremeEventManager.shutdown();
        }
        getLogger().info("EM-WM-Bridge 已禁用！");
    }

    private boolean checkDependencies() {
        PluginManager pm = Bukkit.getPluginManager();
        boolean eliteMobs = pm.isPluginEnabled("EliteMobs");
        boolean weaponMechanics = pm.isPluginEnabled("WeaponMechanics");

        if (!eliteMobs) {
            getLogger().severe("未找到 EliteMobs 插件！");
        }
        if (!weaponMechanics) {
            getLogger().severe("未找到 WeaponMechanics 插件！");
        }

        return eliteMobs && weaponMechanics;
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new EliteMobSpawnListener(this), this);
        pm.registerEvents(new EliteMobCombatListener(this), this);

        // 注册热重监听器（命令监听）
        pm.registerEvents(new EMCommandListener(this), this);

        // EMWMReloadListener 不再通过 pm.registerEvents 注册
        // EliteMobs 没有官方 ReloadEvent，@EventHandler(Object) 签名非法
        // reloadEMWMConfig() 方法仍可通过 /emwm reload 手动调用
        reloadListener = new EMWMReloadListener(this);

        new PlayerShootListener(this, tarkovAIManager.getEngine().getSoundEventManager()).register();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("emwm.admin")) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                reloadAll();
                sender.sendMessage("§a[EM-WM-Bridge] 配置已重新加载！");
                return true;

            case "debug":
                if (!sender.hasPermission("emwm.admin")) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (args.length > 1) {
                    handleDebugCommand(sender, args);
                } else {
                    handleDebugCommand(sender, new String[]{"debug"});
                }
                return true;

            case "stats":
                handleStatsCommand(sender);
                return true;

            case "version":
                sender.sendMessage("§e[EM-WM-Bridge] §f版本: " + getDescription().getVersion());
                sender.sendMessage("§f配置版本: " + configManager.getConfigVersion());
                sender.sendMessage("§fDEBUG等级: " + debugManager.getGlobalDebugLevel().getName());
                return true;

            case "track":
                if (sender instanceof Player player) {
                    handleTrackCommand(player, args);
                } else {
                    sender.sendMessage("§c此命令只能由玩家执行");
                }
                return true;

            case "info":
                if (!sender.hasPermission("emwm.admin")) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                handleInfoCommand(sender, args);
                return true;

            case "test":
                if (!sender.hasPermission("emwm.admin")) {
                    sender.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                sender.sendMessage("§e[EM-WM-Bridge] §f正在执行黑盒测试，结果输出到控制台...");
                String json = new com.emwbridge.test.PluginTestRunner(this).run();
                // 解析 JSON 中的 passed/failed 发给玩家
                int passedCount = json.contains("\"passed\":") 
                    ? Integer.parseInt(json.replaceAll(".*\"passed\":(\\d+).*", "$1")) : -1;
                int failedCount = json.contains("\"failed\":")
                    ? Integer.parseInt(json.replaceAll(".*\"failed\":(\\d+).*", "$1")) : -1;
                String color = failedCount > 0 ? "§c" : "§a";
                sender.sendMessage(color + "[EM-WM-Bridge] 测试完成: §a通过 " + passedCount + color + " | 失败 " + failedCount + " §7(详见控制台)");
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e=== EM-WM-Bridge 命令帮助 ===");
        sender.sendMessage("§f/emwm §7- 显示帮助");
        sender.sendMessage("§f/emwm reload §7- 重新加载配置");
        sender.sendMessage("§f/emwm debug [level] §7- 设置DEBUG等级 (OFF/BASIC/DETAILED/TRACE)");
        sender.sendMessage("§f/emwm debug entity <player> [level] §7- 设置实体DEBUG");
        sender.sendMessage("§f/emwm stats §7- 显示统计信息");
        sender.sendMessage("§f/emwm version §7- 显示版本信息");
        sender.sendMessage("§f/emwm track §7- 跟踪你正在看的实体");
        sender.sendMessage("§f/emwm info [怪物ID] §7- 查看怪物EMWM配置缓存");
        sender.sendMessage("§f/emwm test §7- 执行黑盒测试（输出JSON）");
    }

    private void handleDebugCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            DebugLevel current = debugManager.getGlobalDebugLevel();
            sender.sendMessage("§e当前DEBUG等级: §f" + current.getName() + " (Level " + current.getLevel() + ")");
            sender.sendMessage("§7可用等级: OFF, BASIC, DETAILED, TRACE");
            sender.sendMessage("§7使用 /emwm debug <等级> 切换");
            return;
        }

        String levelName = args[1].toUpperCase();
        if (levelName.equals("ENTITY") && args.length > 2) {
            sender.sendMessage("§e实体DEBUG请使用: /emwm debug entity <玩家> [等级]");
            return;
        }

        try {
            DebugLevel newLevel = DebugLevel.valueOf(levelName);
            getConfig().set("settings.debug-level", newLevel.getName());
            getConfig().set("settings.debug", newLevel != DebugLevel.OFF);
            saveConfig();
            debugManager.reload();
            sender.sendMessage("§aDEBUG等级已设置为: §f" + newLevel.getName());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的DEBUG等级: " + levelName);
            sender.sendMessage("§7可用等级: OFF, BASIC, DETAILED, TRACE");
        }
    }

    private void handleTrackCommand(Player player, String[] args) {
        UUID uuid = player.getUniqueId();

        if (args.length == 1) {
            DebugLevel level = debugManager.getEntityDebugLevel(uuid);
            player.sendMessage("§e你的跟踪状态: §f" + (level == DebugLevel.OFF ? "关闭" : level.getName()));
            return;
        }

        if (args[1].equalsIgnoreCase("off")) {
            debugManager.setEntityDebugLevel(uuid, null);
            player.sendMessage("§a已关闭实体跟踪");
            return;
        }

        try {
            DebugLevel newLevel = DebugLevel.valueOf(args[1].toUpperCase());
            debugManager.setEntityDebugLevel(uuid, newLevel);
            player.sendMessage("§a实体DEBUG已设置为: §f" + newLevel.getName());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的等级: " + args[1]);
        }
    }

    private void handleStatsCommand(CommandSender sender) {
        sender.sendMessage("§e=== EM-WM-Bridge 统计 ===");
        sender.sendMessage("§f活动AI: §e" + tarkovAIManager.getActiveCount());
        sender.sendMessage("§f武器绑定: §e" + debugManager.getCounter("weapon_bind"));
        sender.sendMessage("§f射击次数: §e" + debugManager.getCounter("shots_fired"));
        sender.sendMessage("§f换弹次数: §e" + debugManager.getCounter("reload"));
        sender.sendMessage("§f恐慌模式: §e" + debugManager.getCounter("panic_mode"));
        sender.sendMessage("§f肾上腺素: §e" + debugManager.getCounter("adrenaline"));
    }

    private void handleInfoCommand(CommandSender sender, String[] args) {
        if (emwmConfigCache == null) {
            sender.sendMessage("§c配置缓存未初始化");
            return;
        }

        if (args.length == 1) {
            sender.sendMessage("§e=== EMWM配置缓存信息 ===");
            sender.sendMessage("§f已加载怪物配置: §e" + emwmConfigCache.getLoadedMobFiles().size());
            sender.sendMessage("§f全局模板数量: §e" + emwmConfigCache.getGlobalTemplateNames().size());

            sender.sendMessage("");
            sender.sendMessage("§e--- 已加载的怪物配置 ---");
            for (String fileName : emwmConfigCache.getLoadedMobFiles()) {
                sender.sendMessage("§f  - " + fileName);
            }

            sender.sendMessage("");
            sender.sendMessage("§e--- 全局模板 ---");
            for (String templateName : emwmConfigCache.getGlobalTemplateNames()) {
                sender.sendMessage("§f  - " + templateName);
            }

            sender.sendMessage("");
            sender.sendMessage("§7使用 /emwm info <怪物ID> 查看详细配置");
            return;
        }

        String mobId = args[1];
        com.emwbridge.config.EMWMWeaponConfig config = emwmConfigCache.getConfig(mobId);

        if (config == null) {
            sender.sendMessage("§c未找到怪物配置: " + mobId);
            sender.sendMessage("§7可用怪物配置: " + String.join(", ", emwmConfigCache.getLoadedMobFiles()));
            return;
        }

        sender.sendMessage("§e=== EMWM配置详情 [" + mobId + "] ===");

        sender.sendMessage("");
        sender.sendMessage("§6--- 武器配置 ---");
        sender.sendMessage("§f武器池: §e" + config.getWeaponPool());
        sender.sendMessage("§f射速: §e" + config.getFireRate() + " shots/s");
        sender.sendMessage("§f散布: §e" + config.getSpread() + "°");
        sender.sendMessage("§fADS散布倍率: §e" + config.getAdsSpreadMultiplier());

        sender.sendMessage("");
        sender.sendMessage("§6--- 弹药配置 ---");
        sender.sendMessage("§f弹匣容量: §e" + config.getMagazineSize());
        sender.sendMessage("§f换弹时长: §e" + config.getReloadDuration() + " tick");
        sender.sendMessage("§f自动换弹: §e" + (config.isAutoReload() ? "是" : "否"));

        sender.sendMessage("");
        sender.sendMessage("§6--- 射程配置 ---");
        sender.sendMessage("§f有效射程: §e" + config.getEffectiveRange() + " 格");
        sender.sendMessage("§f最大射程: §e" + config.getMaxRange() + " 格");
        sender.sendMessage("§f近战切换距离: §e" + config.getMeleeRange() + " 格");
        sender.sendMessage("§fADS距离阈值: §e" + config.getAdsRangeThreshold() + " 格");

        sender.sendMessage("");
        sender.sendMessage("§6--- 战术配置 ---");
        sender.sendMessage("§f站立射击: §e" + (config.isStandAndShoot() ? "是" : "否"));
        sender.sendMessage("§f压制血量阈值: §e" + (config.getSuppressHpThreshold() * 100) + "%");
        sender.sendMessage("§f撤退血量阈值: §e" + (config.getRetreatHpThreshold() * 100) + "%");

        sender.sendMessage("");
        sender.sendMessage("§6--- 行为配置 ---");
        sender.sendMessage("§f攻击性: §e" + String.format("%.2f", config.getAggressiveness()));
        sender.sendMessage("§f掩体利用率: §e" + String.format("%.2f", config.getCoverUsage()));
        sender.sendMessage("§f搜索持续时间: §e" + config.getSearchDuration() + " tick");

        sender.sendMessage("");
        sender.sendMessage("§6--- 特殊能力 ---");
        sender.sendMessage("§f呼叫支援: §e" + (config.isCallReinforcements() ? "是" : "否"));
        sender.sendMessage("§f小队队长: §e" + (config.isSquadLeader() ? "是" : "否"));
        sender.sendMessage("§f远程偏好: §e" + (config.isPreferLongRange() ? "是" : "否"));
        sender.sendMessage("§f冲锋偏好: §e" + (config.isPreferRush() ? "是" : "否"));
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void reloadAll() {
        reloadConfig();
        debugManager.reload();
        mobWeaponManager.reload();
        extremeEventManager.reload();
        tarkovAIManager.restart();

        // 刷新EMWM配置缓存
        if (emwmConfigCache != null) {
            emwmConfigCache.reload();
        }
    }

    public static EMWMBridge getInstance() {
        return instance;
    }

    public MobWeaponManager getMobWeaponManager() {
        return mobWeaponManager;
    }

    public TarkovAIManager getTarkovAIManager() {
        return tarkovAIManager;
    }

    public ExtremeEventManager getExtremeEventManager() {
        return extremeEventManager;
    }

    public DebugManager getDebugManager() {
        return debugManager;
    }

    public FeatureManager getFeatureManager() {
        return featureManager;
    }

    public EMWMConfigCache getEMWMConfigCache() {
        return emwmConfigCache;
    }

    public EMWMReloadListener getReloadListener() {
        return reloadListener;
    }

    public boolean isFolia() {
        return isFolia;
    }

    public boolean isDebug() {
        return debugManager.getGlobalDebugLevel() != DebugLevel.OFF;
    }

    public void debug(String message) {
        debugManager.debug(message);
    }
}
