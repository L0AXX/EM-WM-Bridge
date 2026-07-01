package com.emwbridge.ai.combat;

import com.emwbridge.EMWMBridge;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * 投掷物管理器 — 破片雷 / 闪光弹 / 烟雾弹
 * 
 * 核心原则：绝不破坏地形。所有爆炸效果通过粒子和直接伤害实现，
 * 不使用原版 TNT/Creeper 机制。
 */
public class ThrowableManager {

    private final EMWMBridge plugin;
    private final Map<UUID, ThrowableCooldowns> cooldowns = new HashMap<>();

    // 破片雷参数
    private double fragFuseSeconds = 3.0;
    private double fragRadius = 5.0;
    private double fragMaxDamage = 16.0;
    private double fragMinDamage = 4.0;

    // 闪光弹参数
    private double flashFuseSeconds = 2.0;
    private double flashRadius = 8.0;
    private int flashBlindnessDuration = 80;  // ticks
    private int flashSlownessDuration = 60;
    private int flashNauseaDuration = 40;

    // 烟雾弹参数
    private double smokeFuseSeconds = 1.0;
    private double smokeRadius = 6.0;
    private int smokeDuration = 160;  // ticks (8s)

    // 冷却参数 (毫秒)
    private long fragCooldownMs = 15000;
    private long flashCooldownMs = 20000;
    private long smokeCooldownMs = 25000;

    public ThrowableManager(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    public void reload(org.bukkit.configuration.file.FileConfiguration config) {
        fragFuseSeconds = config.getDouble("throwables.frag.fuse-seconds", 3.0);
        fragRadius = config.getDouble("throwables.frag.radius", 5.0);
        fragMaxDamage = config.getDouble("throwables.frag.max-damage", 16.0);
        fragMinDamage = config.getDouble("throwables.frag.min-damage", 4.0);

        flashFuseSeconds = config.getDouble("throwables.flash.fuse-seconds", 2.0);
        flashRadius = config.getDouble("throwables.flash.radius", 8.0);
        flashBlindnessDuration = config.getInt("throwables.flash.blindness-ticks", 80);
        flashSlownessDuration = config.getInt("throwables.flash.slowness-ticks", 60);
        flashNauseaDuration = config.getInt("throwables.flash.nausea-ticks", 40);

        smokeFuseSeconds = config.getDouble("throwables.smoke.fuse-seconds", 1.0);
        smokeRadius = config.getDouble("throwables.smoke.radius", 6.0);
        smokeDuration = config.getInt("throwables.smoke.duration-ticks", 160);

        fragCooldownMs = config.getLong("throwables.cooldowns.frag-ms", 15000);
        flashCooldownMs = config.getLong("throwables.cooldowns.flash-ms", 20000);
        smokeCooldownMs = config.getLong("throwables.cooldowns.smoke-ms", 25000);

        plugin.debug("[投掷物] 加载: 破片R=" + fragRadius + " Dmg=" + fragMaxDamage
                + " 闪光R=" + flashRadius + " 烟雾R=" + smokeRadius);
    }

    // ==================== 公共 API ====================

    /**
     * 投掷破片手雷 — 粒子爆炸 + 半径伤害，不破坏方块
     */
    public boolean throwFrag(LivingEntity thrower, Player target) {
        UUID uuid = thrower.getUniqueId();
        if (!canThrow(uuid, ThrowableType.FRAG)) return false;

        Location targetLoc = predictTargetLocation(thrower, target);
        markCooldown(uuid, ThrowableType.FRAG);
        spawnTrajectoryParticle(thrower, targetLoc, ThrowableType.FRAG);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            explodeFrag(thrower, targetLoc);
        }, (long) (fragFuseSeconds * 20));

        return true;
    }

    /**
     * 投掷闪光弹 — 致盲 + 减速 + 反胃，不破坏方块
     */
    public boolean throwFlash(LivingEntity thrower, Player target) {
        UUID uuid = thrower.getUniqueId();
        if (!canThrow(uuid, ThrowableType.FLASH)) return false;

        Location targetLoc = predictTargetLocation(thrower, target);
        markCooldown(uuid, ThrowableType.FLASH);
        spawnTrajectoryParticle(thrower, targetLoc, ThrowableType.FLASH);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            explodeFlash(thrower, targetLoc);
        }, (long) (flashFuseSeconds * 20));

        return true;
    }

    /**
     * 投掷烟雾弹 — 大范围粒子遮挡视线，不破坏方块
     */
    public boolean throwSmoke(LivingEntity thrower, Location targetLoc) {
        UUID uuid = thrower.getUniqueId();
        if (!canThrow(uuid, ThrowableType.SMOKE)) return false;

        markCooldown(uuid, ThrowableType.SMOKE);
        spawnTrajectoryParticle(thrower, targetLoc, ThrowableType.SMOKE);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deploySmoke(targetLoc);
        }, (long) (smokeFuseSeconds * 20));

        return true;
    }

    public long getCooldownRemaining(UUID uuid, ThrowableType type) {
        ThrowableCooldowns tc = cooldowns.get(uuid);
        if (tc == null) return 0;
        Long last = tc.lastThrow.get(type);
        if (last == null) return 0;
        long cooldown = getCooldownMs(type);
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, cooldown - elapsed);
    }

    public void registerMob(UUID uuid) {
        cooldowns.put(uuid, new ThrowableCooldowns());
    }

    public void unregisterMob(UUID uuid) {
        cooldowns.remove(uuid);
    }

    // ==================== 内部实现 ====================

    private boolean canThrow(UUID uuid, ThrowableType type) {
        return getCooldownRemaining(uuid, type) == 0;
    }

    private long getCooldownMs(ThrowableType type) {
        return switch (type) {
            case FRAG -> fragCooldownMs;
            case FLASH -> flashCooldownMs;
            case SMOKE -> smokeCooldownMs;
        };
    }

    private void markCooldown(UUID uuid, ThrowableType type) {
        cooldowns.computeIfAbsent(uuid, k -> new ThrowableCooldowns())
                .lastThrow.put(type, System.currentTimeMillis());
    }

    /**
     * 预判目标位置 — 根据目标速度向量偏移
     */
    private Location predictTargetLocation(LivingEntity thrower, Player target) {
        Location targetLoc = target.getLocation().clone();
        Vector velocity = target.getVelocity();
        if (velocity.lengthSquared() > 0.01) {
            double predictTime = 1.5; // 预判1.5秒
            targetLoc.add(velocity.clone().multiply(predictTime));
        }
        return targetLoc;
    }

    /**
     * 投掷轨迹粒子 — 模拟手雷飞行路径
     */
    private void spawnTrajectoryParticle(LivingEntity thrower, Location target, ThrowableType type) {
        Location start = thrower.getEyeLocation().clone();
        Vector direction = target.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        Particle particleType = switch (type) {
            case FRAG -> Particle.ITEM;
            case FLASH -> Particle.ELECTRIC_SPARK;
            case SMOKE -> Particle.CAMPFIRE_COSY_SMOKE;
        };

        // 用盔甲架作为可见投掷物
        World world = thrower.getWorld();
        ArmorStand marker = (ArmorStand) world.spawnEntity(
                start.clone().add(direction.clone().multiply(0.5)),
                EntityType.ARMOR_STAND);
        marker.setVisible(false);
        marker.setMarker(true);
        marker.setGravity(false);
        marker.setSmall(true);
        marker.setCustomNameVisible(false);
        marker.addScoreboardTag("emwm_grenade");

        // 赋予头盔显示
        Material headMaterial = switch (type) {
            case FRAG -> Material.IRON_NUGGET;
            case FLASH -> Material.GLOWSTONE_DUST;
            case SMOKE -> Material.GUNPOWDER;
        };
        org.bukkit.inventory.ItemStack helmet = new org.bukkit.inventory.ItemStack(headMaterial);
        marker.getEquipment().setHelmet(helmet);

        // 物理模拟飞行
        Vector velocity = direction.clone().multiply(1.2);
        double arc = Math.min(distance * 0.3, 10.0);
        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = (int) (distance / 1.2) + 20;

            @Override
            public void run() {
                tick++;
                if (tick > maxTicks || marker.isDead()) {
                    marker.remove();
                    cancel();
                    return;
                }
                // 抛物线
                velocity.setY(velocity.getY() - 0.04 * (tick / 20.0)); // 轻微重力
                Location next = marker.getLocation().add(velocity);
                if (next.getBlock().getType().isSolid()) {
                    // 碰到方块立即引爆
                    marker.remove();
                    switch (type) {
                        case FRAG -> explodeFrag(thrower, next);
                        case FLASH -> explodeFlash(thrower, next);
                        case SMOKE -> deploySmoke(next);
                    }
                    cancel();
                    return;
                }
                marker.teleport(next);
                world.spawnParticle(particleType, next, 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * 破片爆炸 — 纯粒子 + 直接伤害，不破坏方块
     */
    private void explodeFrag(LivingEntity thrower, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // 爆炸粒子
        world.spawnParticle(Particle.EXPLOSION, center, 8, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.FLAME, center, 30, fragRadius * 0.6, fragRadius * 0.6, fragRadius * 0.6, 0.05);
        world.spawnParticle(Particle.SMOKE, center, 20, fragRadius * 0.4, 1.5, fragRadius * 0.4, 0.02);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);

        // 伤害计算 — 距离衰减
        for (Player player : world.getPlayers()) {
            double dist = player.getLocation().distance(center);
            if (dist > fragRadius) continue;
            if (!hasLineOfSight(center, player.getEyeLocation())) continue;

            double damage = fragMaxDamage - (fragMaxDamage - fragMinDamage) * (dist / fragRadius);
            damage = Math.min(damage, fragMaxDamage);
            player.damage(damage, thrower);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1.2f);
        }
    }

    /**
     * 闪光爆炸 — 致盲 + 减速 + 反胃效果
     */
    private void explodeFlash(LivingEntity thrower, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // 白光粒子
        world.spawnParticle(Particle.FLASH, center, 5, 0.1, 0.1, 0.1, 1.0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 40, flashRadius * 0.5, flashRadius * 0.5, flashRadius * 0.5, 0.02);
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 1.5f);

        for (Player player : world.getPlayers()) {
            double dist = player.getLocation().distance(center);
            if (dist > flashRadius) continue;

            // 距离衰减 — 越近效果越强
            double factor = 1.0 - (dist / flashRadius);
            int blindness = (int) (flashBlindnessDuration * factor);
            int slowness = (int) (flashSlownessDuration * factor);
            int nausea = (int) (flashNauseaDuration * factor);

            // 检查是否面朝爆炸点（没被闪到正面效果减半）
            if (isFacingExplosion(player, center)) {
                factor *= 0.5;
                blindness = (int) (blindness * 0.5);
            }

            if (blindness > 0)
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindness, 0, false, true));
            if (slowness > 0)
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowness, 1, false, true));
            if (nausea > 0)
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nausea, 0, false, true));

            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
        }
    }

    /**
     * 烟雾部署 — 持续粒子云遮挡视线
     */
    private void deploySmoke(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.6f);

        // 持续粒子云 — 每5tick生成一圈
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 5;
                if (ticks > smokeDuration) {
                    cancel();
                    return;
                }
                if (center.getWorld() == null) {
                    cancel();
                    return;
                }
                // 多层烟雾粒子柱
                for (int y = 0; y < 4; y++) {
                    Location layer = center.clone().add(0, y * 0.8, 0);
                    center.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, layer,
                            15, smokeRadius * 0.5, 0.5, smokeRadius * 0.5, 0.01);
                    center.getWorld().spawnParticle(Particle.LARGE_SMOKE, layer,
                            5, smokeRadius * 0.3, 0.3, smokeRadius * 0.3, 0.005);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // ==================== 工具方法 ====================

    private boolean hasLineOfSight(Location from, Location to) {
        World world = from.getWorld();
        if (world == null) return false;
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        direction.normalize();
        for (double d = 0; d < distance; d += 0.5) {
            Location check = from.clone().add(direction.clone().multiply(d));
            if (check.getBlock().getType().isSolid()) return false;
        }
        return true;
    }

    private boolean isFacingExplosion(Player player, Location explosion) {
        Vector toExplosion = explosion.toVector().subtract(player.getEyeLocation().toVector()).normalize();
        Vector facing = player.getEyeLocation().getDirection().normalize();
        return toExplosion.dot(facing) > 0;
    }

    public enum ThrowableType {
        FRAG, FLASH, SMOKE
    }

    private static class ThrowableCooldowns {
        final Map<ThrowableType, Long> lastThrow = new EnumMap<>(ThrowableType.class);
    }
}
