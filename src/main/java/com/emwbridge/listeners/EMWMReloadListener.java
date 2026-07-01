package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * EliteMobs配置热重监听器
 *
 * 监听EliteMobs的reload事件，刷新EMWM配置缓存
 */
public class EMWMReloadListener implements Listener {

    private final EMWMBridge plugin;

    public EMWMReloadListener(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 监听EliteMobs配置重载事件
     * 注意：EliteMobs可能没有官方的ReloadEvent，这里使用命令监听替代方案
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEliteMobsReload(Object event) {
        //尝试使用反射检测EliteMobs的reload事件
        try {
            String eventClassName = event.getClass().getSimpleName();
            if (eventClassName.contains("Reload") || eventClassName.contains("Config")) {
                plugin.debug("[EMWM] 检测到EliteMobs重载事件，刷新配置缓存");
                reloadEMWMConfig();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 手动重载EMWM配置
     */
    public void reloadEMWMConfig() {
        plugin.getEMWMConfigCache().reload();
        plugin.getLogger().info("[EMWM] 配置缓存已刷新，已加载 "
                + plugin.getEMWMConfigCache().getLoadedMobFiles().size() + " 个怪物配置");
    }
}