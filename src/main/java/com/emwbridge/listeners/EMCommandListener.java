package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * EliteMobs命令监听
 *
 * 精确监听 /elitemobs reload 命令，触发EMWM配置缓存热重载
 */
public class EMCommandListener implements Listener {

    private final EMWMBridge plugin;

    public EMCommandListener(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();

        // 监听EliteMobs的reload命令
        if (command.startsWith("/elitemobs") || command.startsWith("/em")) {
            String[] args = command.split(" ");
            if (args.length >= 2 && args[1].equals("reload")) {
                plugin.debug("[EMWM] 检测到EliteMobs重载命令，计划刷新配置缓存");

                // 延迟执行（等待EM完成重载）
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getEMWMConfigCache().reload();
                    plugin.getLogger().info("[EMWM] 配置缓存已跟随EliteMobs重载刷新");
                }, 10L);
            }
        }
    }
}