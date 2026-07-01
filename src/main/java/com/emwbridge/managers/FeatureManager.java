package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeatureManager {

    private final EMWMBridge plugin;
    private final Map<String, List<FeatureInfo>> featureCategories;

    public FeatureManager(EMWMBridge plugin) {
        this.plugin = plugin;
        this.featureCategories = new LinkedHashMap<>();
        initFeatures();
    }

    private void initFeatures() {
        // ========== 武器系统 ==========
        List<FeatureInfo> weapons = new ArrayList<>();
        weapons.add(new FeatureInfo("武器绑定", "从WeaponMechanics生成武器并绑定到怪物手上", true));
        weapons.add(new FeatureInfo("武器射击", "调用WM API实现怪物开火", true));
        weapons.add(new FeatureInfo("弹药系统", "从WM读取弹匣容量，每次射击消耗弹药", true));
        weapons.add(new FeatureInfo("自动换弹", "弹匣打空自动换弹，从WM读取换弹时间", true));
        weapons.add(new FeatureInfo("耐久度系统", "每次射击消耗耐久，耐久归零武器损坏", true));
        weapons.add(new FeatureInfo("武器故障", "耐久低时概率卡壳", true));
        weapons.add(new FeatureInfo("WM参数读取", "射速/散射/弹匣/换弹全部从WM配置读取", true));
        weapons.add(new FeatureInfo("武器池", "每个tier有独立武器池，随机抽取", true));
        featureCategories.put("武器系统", weapons);

        // ========== AI决策系统 ==========
        List<FeatureInfo> ai = new ArrayList<>();
        ai.add(new FeatureInfo("目标选择", "智能选择最优目标（距离+血量+威胁）", true));
        ai.add(new FeatureInfo("视线检测", "判断是否能看到玩家", true));
        ai.add(new FeatureInfo("反应延迟", "不同tier有不同反应时间", true));
        ai.add(new FeatureInfo("8种战斗状态", "IDLE/SEARCHING/APPROACHING/ENGAGING/CLOSING_IN/TACTICAL_RETREAT/FLEEING", true));
        ai.add(new FeatureInfo("决策优先级", "致命威胁>弹药危机>被压制>无视线>远距离>贴身>正常战斗", true));
        ai.add(new FeatureInfo("智能寻敌", "超出距离根据激进程度决定是否追击", true));
        featureCategories.put("AI决策系统", ai);

        // ========== 战术行为 ==========
        List<FeatureInfo> tactics = new ArrayList<>();
        tactics.add(new FeatureInfo("BERSERKER 贴身冲锋", "距离<3格时贴脸冲锋", true));
        tactics.add(new FeatureInfo("SUPPRESSING 压制射击", "近距离高速射击+左右横移", true));
        tactics.add(new FeatureInfo("BARRAGE 弹幕射击", "中距离连续射击+偶尔前进", true));
        tactics.add(new FeatureInfo("PRECISE 精准点射", "中远距离点射，开镜优先", true));
        tactics.add(new FeatureInfo("PEEKING 探头射击", "有掩体时探头打几枪缩回去", true));
        tactics.add(new FeatureInfo("STALKING 潜行追踪", "远距离潜行+侧翼移动", true));
        tactics.add(new FeatureInfo("掩体寻找", "扫描周围8方向寻找能挡视线的掩体", true));
        tactics.add(new FeatureInfo("战术撤退换弹", "弹匣空时找掩体换弹", true));
        tactics.add(new FeatureInfo("侧翼移动", "绕到玩家侧面攻击", true));
        tactics.add(new FeatureInfo("横移射击", "射击时左右移动躲避", true));
        featureCategories.put("战术行为", tactics);

        // ========== Tier差异化 ==========
        List<FeatureInfo> tier = new ArrayList<>();
        tier.add(new FeatureInfo("Scav 拾荒者", "胆小，精度差，怕死就跑", true));
        tier.add(new FeatureInfo("PMC 雇佣兵", "中等，敢打敢撤", true));
        tier.add(new FeatureInfo("Boss 莽夫", "激进，快死了才跑", true));
        tier.add(new FeatureInfo("射速倍率", "每个tier独立射速调整", true));
        tier.add(new FeatureInfo("精度倍率", "每个tier独立精度调整", true));
        tier.add(new FeatureInfo("反应延迟", "每个tier独立反应时间", true));
        tier.add(new FeatureInfo("最大射程", "每个tier独立开火距离", true));
        tier.add(new FeatureInfo("开镜能力", "scav不会开镜，pmc/boss会", true));
        tier.add(new FeatureInfo("开火模式", "SINGLE/BURST/AUTO", true));
        tier.add(new FeatureInfo("换弹速度", "每个tier独立换弹速度", true));
        tier.add(new FeatureInfo("耐久度倍率", "每个tier独立武器耐久", true));
        tier.add(new FeatureInfo("激进程度", "影响追击意愿和进攻倾向", true));
        tier.add(new FeatureInfo("战术撤退血量", "每个tier独立撤退阈值", true));
        featureCategories.put("Tier差异化", tier);

        // ========== 极限事件 ==========
        List<FeatureInfo> extreme = new ArrayList<>();
        extreme.add(new FeatureInfo("Panic Mode 恐慌模式", "被打急了疯狂扫射，精度下降射速提升", true));
        extreme.add(new FeatureInfo("Adrenaline 肾上腺素", "残血时爆发，攻速+50%精度+30%", true));
        extreme.add(new FeatureInfo("Luck Shot 幸运一击", "小概率打出完美射击", true));
        extreme.add(new FeatureInfo("Tactical Mistake 战术失误", "暴露太久/移动太多时失误减速", true));
        featureCategories.put("极限事件", extreme);

        // ========== 配置系统 ==========
        List<FeatureInfo> config = new ArrayList<>();
        config.add(new FeatureInfo("版本控制", "config-version 自动检测", true));
        config.add(new FeatureInfo("自动迁移", "缺失配置项自动添加，保留用户修改", true));
        config.add(new FeatureInfo("默认值补全", "启动时检查并补全缺失字段", true));
        config.add(new FeatureInfo("配置备份", "迁移前自动备份原配置", true));
        featureCategories.put("配置系统", config);

        // ========== DEBUG系统 ==========
        List<FeatureInfo> debug = new ArrayList<>();
        debug.add(new FeatureInfo("4级分级", "OFF/BASIC/DETAILED/TRACE", true));
        debug.add(new FeatureInfo("实体级调试", "可针对特定实体设置DEBUG等级", true));
        debug.add(new FeatureInfo("计数器系统", "统计射击/换弹/事件次数", true));
        debug.add(new FeatureInfo("防刷屏", "相同消息1秒内只输出一次", true));
        featureCategories.put("DEBUG系统", debug);

        // ========== 命令系统 ==========
        List<FeatureInfo> commands = new ArrayList<>();
        commands.add(new FeatureInfo("/emwm", "显示帮助", true));
        commands.add(new FeatureInfo("/emwm reload", "重新加载所有配置", true));
        commands.add(new FeatureInfo("/emwm debug [level]", "设置全局DEBUG等级", true));
        commands.add(new FeatureInfo("/emwm stats", "显示统计信息", true));
        commands.add(new FeatureInfo("/emwm version", "显示版本信息", true));
        commands.add(new FeatureInfo("/emwm track [level]", "跟踪你附近的实体", false));
        featureCategories.put("命令系统", commands);

        // ========== 事件系统 ==========
        List<FeatureInfo> events = new ArrayList<>();
        events.add(new FeatureInfo("MobWeaponShootEvent", "怪物开火事件", true));
        events.add(new FeatureInfo("TarkovEvent", "极限事件触发", true));
        events.add(new FeatureInfo("Metadata通信", "emwm_tier/emwm_combat_state/emwm_tactic", true));
        events.add(new FeatureInfo("EliteMobSpawn监听", "自动检测EliteMobs怪物生成", true));
        events.add(new FeatureInfo("Lua元数据支持", "读取EliteMobs Lua脚本设置的metadata", true));
        featureCategories.put("事件系统", events);
    }

    public void printFeatureSummary() {
        plugin.getLogger().info("");
        plugin.getLogger().info("╔══════════════════════════════════════════════════════════════╗");
        plugin.getLogger().info("║              EM-WM-Bridge 功能清单 v1.1.0                    ║");
        plugin.getLogger().info("╠══════════════════════════════════════════════════════════════╣");

        int totalImplemented = 0;
        int totalFeatures = 0;

        for (Map.Entry<String, List<FeatureInfo>> entry : featureCategories.entrySet()) {
            String category = entry.getKey();
            List<FeatureInfo> features = entry.getValue();

            int implemented = 0;
            for (FeatureInfo f : features) {
                if (f.implemented) implemented++;
                totalFeatures++;
            }
            totalImplemented += implemented;

            plugin.getLogger().info("║                                                              ║");
            plugin.getLogger().info("║  [" + category + "] " + implemented + "/" + features.size());

            for (FeatureInfo feature : features) {
                String status = feature.implemented ? "✅" : "⬜";
                String line = "║    " + status + " " + padRight(feature.name, 24);
                if (feature.description != null && !feature.description.isEmpty()) {
                    line += " - " + feature.description;
                }
                // 截断过长的行
                if (line.length() > 62) {
                    line = line.substring(0, 59) + "...";
                }
                plugin.getLogger().info(padRight(line, 62) + "║");
            }
        }

        plugin.getLogger().info("║                                                              ║");
        plugin.getLogger().info("╠══════════════════════════════════════════════════════════════╣");
        plugin.getLogger().info("║  总计: " + padRight(totalImplemented + "/" + totalFeatures + " 个功能已实现", 48) + "║");
        plugin.getLogger().info("╚══════════════════════════════════════════════════════════════╝");
        plugin.getLogger().info("");
    }

    private String padRight(String s, int n) {
        if (s.length() >= n) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(" ");
        return sb.toString();
    }

    public static class FeatureInfo {
        public final String name;
        public final String description;
        public final boolean implemented;

        public FeatureInfo(String name, String description, boolean implemented) {
            this.name = name;
            this.description = description;
            this.implemented = implemented;
        }
    }
}
