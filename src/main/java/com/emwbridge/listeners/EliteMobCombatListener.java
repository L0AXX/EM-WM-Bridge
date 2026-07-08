package com.emwbridge.listeners;

import com.emwbridge.EMWMBridge;
import com.emwbridge.managers.MobWeaponManager;
import com.emwbridge.managers.TarkovAIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EliteMob 战斗监听器
 *
 * P0-1/P0-2 修复说明：
 * - 移除了独立的 scheduleShootingTask() 射击逻辑，射击统一由 TarkovAIEngine 控制
 * - 移除了 lastShootTime 和永不取消的 runTaskTimer，消除内存泄漏
 * - 保留 currentTargets 用于目标追踪（信息查询用途）
 */
public class EliteMobCombatListener implements Listener {

    private final EMWMBridge plugin;
    private final MobWeaponManager weaponManager;
    private final TarkovAIManager aiManager;

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

        // P0-1 修复：不再创建独立射击任务，射击由 AI 引擎统一管理
        // 仅记录目标关系供信息查询
        currentTargets.put(entityUuid, playerUuid);
    }

    @EventHandler
    public void onEntityTargetLost(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        if (!weaponManager.hasWeapon(entity)) return;

        UUID entityUuid = entity.getUniqueId();
        currentTargets.remove(entityUuid);

        // AI引擎管理的实体每tick主动清目标，不打印日志（避免刷屏）
        // 只有非AI引擎管理的实体失去目标才记录
        if (!entity.hasMetadata("emwm_ai_enabled")) {
            plugin.debug("[EM-Combat] " + entity.getName() + " 失去目标");
        }
    }

    /**
     * 伤害类型检测 — 在精英怪被伤害时记录伤害类型到 metadata
     * 供 EMWMKillEvent 在死亡时读取
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!weaponManager.hasWeapon(entity)) return;

        String damageType = classifyDamage(event);
        entity.setMetadata("emwm_last_damage_type", new FixedMetadataValue(plugin, damageType));
        entity.setMetadata("emwm_last_damager_uuid", new FixedMetadataValue(plugin,
                event.getDamager() instanceof Player p ? p.getUniqueId().toString() : ""));

        plugin.debug("[EM-Damage] " + entity.getName() + " 受伤 type=" + damageType
                + " cause=" + event.getCause() + " damager=" + event.getDamager().getType());
    }

    /**
     * 也监听非实体伤害（掉落/火焰等），记录为 OTHER
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!weaponManager.hasWeapon(entity)) return;
        if (event instanceof EntityDamageByEntityEvent) return; // 已在上面处理

        entity.setMetadata("emwm_last_damage_type", new FixedMetadataValue(plugin, "OTHER"));
    }

    /**
     * 分类伤害类型
     */
    private String classifyDamage(EntityDamageByEntityEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        Object damager = event.getDamager();

        // 手雷检测：EMWM ThrowableManager 的盔甲架标记
        if (damager instanceof org.bukkit.entity.ArmorStand stand) {
            if (stand.getScoreboardTags().contains("emwm_grenade")) {
                return "GRENADE";
            }
        }

        // 爆炸类伤害 → 手雷或爆炸
        switch (cause) {
            case ENTITY_EXPLOSION:
            case BLOCK_EXPLOSION:
                return "GRENADE"; // 在 EMWM 上下文中，爆炸基本来自手雷
            case PROJECTILE:
                // WM 子弹是 Projectile
                if (damager instanceof Projectile) {
                    return "GUN";
                }
                return "GUN";
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
                return "MELEE";
            case FIRE:
            case FIRE_TICK:
                return "OTHER";
            default:
                // 检查 damager 是否是玩家近战
                if (damager instanceof Player && (cause == EntityDamageEvent.DamageCause.CUSTOM)) {
                    return "MELEE";
                }
                return "OTHER";
        }
    }

    /**
     * 注册 EliteMobs 专属事件（P0-11 修复：在 onEnable 中调用）
     */
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

    /**
     * 清理资源（插件禁用时调用）
     */
    public void shutdown() {
        currentTargets.clear();
    }
}
