package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EliteMobCombatListener implements Listener {

    private final EMWMBridge plugin;
    private final MobWeaponManager weaponManager;
    private final TarkovAIManager aiManager;

    private final ConcurrentHashMap<UUID, Long> lastShootTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> currentTargets = new ConcurrentHashMap<>();

    public EliteMobCombatListener(EMWMBridge plugin) {
        this.plugin = plugin;
        this.weaponManager = plugin.getMobWeaponManager();
        this.aiManager = plugin.getTarkovAIManager();
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(event.getTarget() instanceof Player player)) return;

        if (!weaponManager.hasWeapon(entity)) return;

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        if (event.getReason() == EntityTargetEvent.TargetReason.FORGOT_TARGET) {
            currentTargets.remove(entityUuid);
            return;
        }

        currentTargets.put(entityUuid, playerUuid);

        scheduleShootingTask(entity, player);
    }

    private void scheduleShootingTask(LivingEntity entity, Player target) {
        UUID entityUuid = entity.getUniqueId();

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid() || entity.isDead()) {
                currentTargets.remove(entityUuid);
                lastShootTime.remove(entityUuid);
                return;
            }

            if (!target.isOnline() || target.isDead()) {
                currentTargets.remove(entityUuid);
                return;
            }

            UUID currentTargetUuid = currentTargets.get(entityUuid);
            if (currentTargetUuid == null || !currentTargetUuid.equals(target.getUniqueId())) {
                return;
            }

            if (!entity.hasLineOfSight(target)) {
                return;
            }

            double distance = entity.getLocation().distance(target.getLocation());

            long now = System.currentTimeMillis();
            long lastShot = lastShootTime.getOrDefault(entityUuid, 0L);
            long fireRateMs = weaponManager.getFireRateMs(entity);

            if (now - lastShot >= fireRateMs) {
                if (weaponManager.shoot(entity, target.getEyeLocation(), distance > 15.0)) {
                    lastShootTime.put(entityUuid, now);
                    plugin.debug("[EM-Combat] " + entity.getName() + " 射击 " + target.getName() +
                            " | 距离: " + String.format("%.1f", distance));
                } else if (weaponManager.isMagazineEmpty(entity) && !weaponManager.isReloading(entity)) {
                    weaponManager.reload(entity);
                    plugin.debug("[EM-Combat] " + entity.getName() + " 换弹");
                }
            }
        }, 0L, 4L);
    }

    @EventHandler
    public void onEntityTargetLost(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        if (!weaponManager.hasWeapon(entity)) return;

        UUID entityUuid = entity.getUniqueId();
        currentTargets.remove(entityUuid);
        lastShootTime.remove(entityUuid);

        plugin.debug("[EM-Combat] " + entity.getName() + " 失去目标");
    }

    public void registerEMEvents() {
        try {
            Class<?> eliteMobDamagedEvent = Class.forName("com.magmaguy.elitemobs.api.EliteMobDamagedEvent");
            Class<?> eliteMobSpawnEvent = Class.forName("com.magmaguy.elitemobs.api.EliteMobSpawnEvent");
            Class<?> eliteMobDeathEvent = Class.forName("com.magmaguy.elitemobs.api.EliteMobDeathEvent");

            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onEliteMobDamaged(org.bukkit.event.Event event) {
                    if (!(event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent damageEvent)) return;
                    if (!(damageEvent.getEntity() instanceof LivingEntity entity)) return;
                    if (!(damageEvent.getDamager() instanceof Player player)) return;

                    if (!weaponManager.hasWeapon(entity)) return;

                    plugin.debug("[EM-Damage] " + entity.getName() + " 被 " + player.getName() + " 攻击");
                }
            }, plugin);

            plugin.getLogger().info("已注册EliteMobs事件监听器");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("无法找到EliteMobs API类，将使用Bukkit原生事件");
        }
    }

    public UUID getCurrentTarget(UUID entityUuid) {
        return currentTargets.get(entityUuid);
    }

    public boolean hasTarget(UUID entityUuid) {
        return currentTargets.containsKey(entityUuid);
    }
}
