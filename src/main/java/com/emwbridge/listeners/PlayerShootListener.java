package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.sound.SoundEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * 玩家射击事件监听器 — 监听 WeaponMechanics 玩家射击事件，触发 AI 听觉仇恨
 * 
 * 通过反射监听 WeaponMechanics 的 PlayerShootEvent，避免硬依赖
 */
public class PlayerShootListener implements Listener {

    private final EMWMBridge plugin;
    private final SoundEventManager soundEventManager;
    private final Logger logger;

    private boolean weaponMechanicsLoaded = false;

    public PlayerShootListener(EMWMBridge plugin, SoundEventManager soundEventManager) {
        this.plugin = plugin;
        this.soundEventManager = soundEventManager;
        this.logger = plugin.getLogger();
        checkWeaponMechanics();
    }

    private void checkWeaponMechanics() {
        weaponMechanicsLoaded = Bukkit.getPluginManager().isPluginEnabled("WeaponMechanics");
        if (weaponMechanicsLoaded) {
            logger.info("[PlayerShootListener] WeaponMechanics 已加载，准备监听玩家射击事件");
        }
    }

    /**
     * WeaponMechanics PlayerShootEvent 监听器
     * 使用 Object 参数避免编译时依赖
     */
    public void onPlayerShoot(Object event) {
        if (!weaponMechanicsLoaded) return;

        try {
            Method getPlayerMethod = event.getClass().getMethod("getPlayer");
            Object playerObj = getPlayerMethod.invoke(event);
            if (!(playerObj instanceof Player player)) return;

            Location shootLoc = player.getEyeLocation();

            boolean suppressed = isSuppressed(event);

            soundEventManager.onGunshot(shootLoc, suppressed);

            plugin.debug("[枪声仇恨] 玩家 " + player.getName() + " 在位置 "
                    + formatLocation(shootLoc) + " 开枪 (消音:" + suppressed + ")");

        } catch (Exception e) {
            logger.warning("[PlayerShootListener] 处理玩家射击事件失败: " + e.getMessage());
        }
    }

    /**
     * 判断是否使用消音器
     */
    private boolean isSuppressed(Object event) {
        try {
            Method getWeaponTitle = event.getClass().getMethod("getWeaponTitle");
            String weaponTitle = (String) getWeaponTitle.invoke(event);
            if (weaponTitle == null) return false;

            return weaponTitle.toLowerCase().contains("suppress")
                    || weaponTitle.toLowerCase().contains("silent")
                    || weaponTitle.toLowerCase().contains("quiet");

        } catch (Exception e) {
            return false;
        }
    }

    private String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)",
                loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * 注册监听器
     * 使用反射注册以避免编译时依赖
     */
    @SuppressWarnings("unchecked")
    public void register() {
        if (!weaponMechanicsLoaded) return;

        try {
            Class<? extends org.bukkit.event.Event> eventClass =
                    (Class<? extends org.bukkit.event.Event>) Class.forName("me.deecaad.weaponmechanics.weapon.shoot.PlayerShootEvent");
            Bukkit.getPluginManager().registerEvent(eventClass, this,
                    org.bukkit.event.EventPriority.NORMAL,
                    (listener, event) -> onPlayerShoot(event),
                    plugin);
            logger.info("[PlayerShootListener] 已注册 WeaponMechanics PlayerShootEvent 监听器");
        } catch (ClassNotFoundException e) {
            logger.warning("[PlayerShootListener] 未找到 PlayerShootEvent 类，可能是版本不兼容");
        }
    }
}
