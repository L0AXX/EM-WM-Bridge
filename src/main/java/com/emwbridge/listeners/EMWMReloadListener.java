package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import org.bukkit.event.Listener;

/**
 * EliteMobs配置热重监听器
 *
 * 注意：EliteMobs 没有官方的 ReloadEvent，此监听器仅保留 reloadEMWMConfig() 方法
 * 供 /emwm reload 命令手动调用。不应通过 pm.registerEvents() 注册。
 */
public class EMWMReloadListener implements Listener {

    private final EMWMBridge plugin;

    public EMWMReloadListener(EMWMBridge plugin) {
        this.plugin = plugin;
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