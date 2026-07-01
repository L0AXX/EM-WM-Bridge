package com.emwbridge.ai.perception;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;

/**
 * 视觉感知引擎 — 基于射线的局部暴露检测
 * 
 * 替代原 hasLineOfSight 粗糙检测，改用：
 * - 身体部位射线：头(eye) / 躯干(body center) / 腿(feet+0.5)
 * - 植被方块(草/树叶/藤蔓等)不阻断射线但施加衰减
 * - 固体方块全阻断
 * - 视野角度扇形检测(FOV cone)
 */
public class VisualPerception {

    // 最大视野距离（由外部传入tier配置）
    private double maxVisionRange = 80.0;

    // 姿态倍率
    private double postureStanding = 1.0;
    private double postureSneaking = 0.6;
    private double postureCrouch = 0.5;
    private double postureSwim = 1.2;

    // 动作倍率
    private double motionStill = 0.5;
    private double motionWalking = 0.8;
    private double motionSprinting = 2.0;

    // 角度倍率
    private double fovDegrees = 120.0;
    private double angleFront = 1.0;
    private double angleSide = 0.6;
    private double angleBack = 0.3;

    // 环境倍率
    private double envDayClear = 1.0;
    private double envNight = 0.5;
    private double envRain = 0.7;
    private double envThunder = 0.6;
    private double envFog = 0.4;    // 雾(恶劣天气)

    // 光照
    private double lightMaxLevel = 15.0;
    private double lightMinMultiplier = 0.35;

    // 植被衰减
    private double foliageAttenuation = 0.6; // 每层植被衰减到60%

    // 身体部位权重
    private double bodyPartHead = 1.0;
    private double bodyPartTorso = 0.7;
    private double bodyPartArms = 0.4;
    private double bodyPartLegs = 0.3;

    // 手电筒/激光标记倍率
    private double flashlightBoost = 2.0;
    private double laserBoost = 1.5;

    // 落叶/植被方块材质
    private static final Set<Material> FOLIAGE_MATERIALS = Set.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES, Material.MANGROVE_LEAVES, Material.PALE_OAK_LEAVES,
            Material.SHORT_GRASS, Material.TALL_GRASS,
            Material.FERN, Material.LARGE_FERN, Material.VINE, Material.GLOW_LICHEN,
            Material.SUGAR_CANE, Material.BAMBOO, Material.BAMBOO_SAPLING,
            Material.DEAD_BUSH, Material.WEEPING_VINES, Material.TWISTING_VINES,
            Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.SWEET_BERRY_BUSH,
            Material.MOSS_BLOCK, Material.MOSS_CARPET, Material.PALE_MOSS_BLOCK, Material.PALE_MOSS_CARPET,
            Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT
    );

    public void reload(FileConfiguration config) {
        postureStanding = config.getDouble("perception.visual.posture.standing", 1.0);
        postureSneaking = config.getDouble("perception.visual.posture.sneaking", 0.6);
        postureCrouch = config.getDouble("perception.visual.posture.crouch", 0.5);
        postureSwim = config.getDouble("perception.visual.posture.swim", 1.2);
        motionStill = config.getDouble("perception.visual.motion.still", 0.5);
        motionWalking = config.getDouble("perception.visual.motion.walking", 0.8);
        motionSprinting = config.getDouble("perception.visual.motion.sprinting", 2.0);
        fovDegrees = config.getDouble("perception.visual.fov-degrees", 120.0);
        angleFront = config.getDouble("perception.visual.angle.front", 1.0);
        angleSide = config.getDouble("perception.visual.angle.side", 0.6);
        angleBack = config.getDouble("perception.visual.angle.back", 0.3);
        envDayClear = config.getDouble("perception.visual.environment.day-clear", 1.0);
        envNight = config.getDouble("perception.visual.environment.night", 0.5);
        envRain = config.getDouble("perception.visual.environment.rain", 0.7);
        envThunder = config.getDouble("perception.visual.environment.thunder", 0.6);
        envFog = config.getDouble("perception.visual.environment.fog", 0.4);
        lightMaxLevel = config.getDouble("perception.visual.light.max-level", 15.0);
        lightMinMultiplier = config.getDouble("perception.visual.light.min-multiplier", 0.35);
        foliageAttenuation = config.getDouble("perception.visual.foliage-attenuation", 0.6);
        bodyPartHead = config.getDouble("perception.visual.body-part.head", 1.0);
        bodyPartTorso = config.getDouble("perception.visual.body-part.torso", 0.7);
        bodyPartArms = config.getDouble("perception.visual.body-part.arms", 0.4);
        bodyPartLegs = config.getDouble("perception.visual.body-part.legs", 0.3);
        flashlightBoost = config.getDouble("perception.visual.boost.flashlight", 2.0);
        laserBoost = config.getDouble("perception.visual.boost.laser", 1.5);
    }

    /**
     * 计算视觉曝光增量 — 射线检测版
     * @return 曝光增量 (0 = 完全不可见)
     */
    public double calculate(LivingEntity entity, Player target, double baseRate) {
        Location eyeLoc = entity.getEyeLocation();
        Location targetLoc = target.getLocation();

        // ====== 步骤 1: 距离衰减 ======
        double distance = eyeLoc.distance(targetLoc);
        if (distance > maxVisionRange) return 0;
        double distanceM = 1.0 / (1.0 + distance / (maxVisionRange * 0.3));
        distanceM = Math.max(0.15, distanceM);

        // ====== 步骤 2: FOV 扇形检测 ======
        Vector entityDir = eyeLoc.getDirection().setY(0).normalize();
        Vector toTarget = targetLoc.toVector().subtract(eyeLoc.toVector()).setY(0).normalize();
        double dot = entityDir.dot(toTarget);
        double halfFovRad = Math.toRadians(fovDegrees / 2.0);
        if (Math.acos(Math.max(-1.0, Math.min(1.0, dot))) > halfFovRad) {
            return 0; // 目标在视野外
        }

        double angleM;
        if (dot > 0.5) angleM = angleFront;
        else if (dot > -0.5) angleM = angleSide;
        else angleM = angleBack;

        // ====== 步骤 3: 身体部位射线检测 ======
        int visibleParts = 0;
        double bodyVisibleScore = 0;
        double foliageCount = 0;

        // 三点检测：头(eye) / 躯干(body center) / 腿(feet+0.5)
        double headY = target.getEyeHeight();
        double torsoY = headY * 0.55;
        double feetY = 0.3;

        RayResult headRay = castRay(entity, eyeLoc, target, headY);
        RayResult torsoRay = castRay(entity, eyeLoc, target, torsoY);
        RayResult feetRay = castRay(entity, eyeLoc, target, feetY);

        if (headRay.blocked) {
            // 头部完全遮挡 → 仍可能看到躯干/腿
        } else {
            visibleParts |= 1; // head bit
            bodyVisibleScore += bodyPartHead;
        }
        if (!torsoRay.blocked) {
            visibleParts |= 2; // torso bit
            bodyVisibleScore += bodyPartTorso;
        }
        if (!feetRay.blocked) {
            visibleParts |= (16 + 32); // legs bit
            bodyVisibleScore += bodyPartLegs;
        }

        foliageCount = headRay.foliageCount + torsoRay.foliageCount + feetRay.foliageCount;
        double foliageM = Math.pow(foliageAttenuation, foliageCount);

        // 全遮挡 → 不可见
        if (visibleParts == 0) return 0;

        // ====== 步骤 4: 姿态倍率 ======
        PostureType posture = PostureType.fromPlayer(target);
        double postureM = switch (posture) {
            case STAND -> postureStanding;
            case SNEAK -> postureSneaking;
            case CROUCH -> postureCrouch;
            case SWIM -> postureSwim;
        };

        // ====== 步骤 5: 动作倍率 ======
        double motionM;
        if (target.isSprinting()) {
            motionM = motionSprinting;
        } else if (target.getVelocity().lengthSquared() < 0.0001) {
            motionM = motionStill;
        } else {
            motionM = motionWalking;
        }

        // ====== 步骤 6: 环境倍率 ======
        double envM = getEnvironmentMultiplier(target.getWorld());

        // ====== 步骤 7: 光照倍率 ======
        double lightM = getLightMultiplier(target);

        // ====== 步骤 8: 手电筒/激光加成 ======
        double boostM = getGlowBoost(target);

        // ====== 综合计算 ======
        double exposure = baseRate
                * distanceM
                * angleM
                * bodyVisibleScore
                * foliageM
                * postureM
                * motionM
                * envM
                * lightM
                * boostM;

        return Math.max(0, exposure);
    }

    /**
     * 身体部位射线 — 从 entity 眼睛射向 target 某高度
     */
    private RayResult castRay(LivingEntity entity, Location eyeLoc, Player target, double targetHeight) {
        Location targetPoint = target.getLocation().clone();
        targetPoint.setY(targetPoint.getY() + targetHeight);

        Vector direction = targetPoint.toVector().subtract(eyeLoc.toVector());
        double maxDist = direction.length();
        if (maxDist < 0.1) return new RayResult(false, 0);
        direction.normalize();

        int foliageCount = 0;
        Location check = eyeLoc.clone();

        for (double d = 0.5; d < maxDist; d += 0.3) {
            check.add(direction.clone().multiply(0.3));
            Material mat = check.getBlock().getType();

            if (!mat.isSolid()) continue;

            // 植被方块 → 不阻断但计入衰减
            if (FOLIAGE_MATERIALS.contains(mat)) {
                foliageCount++;
                continue;
            }

            // 透明方块 → 跳过
            if (isTransparent(mat)) continue;

            // 固体方块 → 阻断
            return new RayResult(true, foliageCount);
        }

        return new RayResult(false, foliageCount);
    }

    private boolean isTransparent(Material mat) {
        return mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR
                || mat == Material.WATER || mat == Material.LAVA
                || mat.name().contains("GLASS") || mat.name().contains("PANE")
                || mat == Material.IRON_BARS || mat.name().contains("TRAPDOOR")
                || mat.name().contains("DOOR") && !mat.name().contains("IRON")
                || mat.name().contains("FENCE") || mat.name().contains("GATE");
    }

    private double getEnvironmentMultiplier(World world) {
        if (world.isThundering()) return envThunder;
        if (world.hasStorm()) {
            // 雷暴时可能有雾
            if (world.getTime() >= 13000 && world.getTime() < 23000) return envFog;
            return envRain;
        }
        long time = world.getTime();
        if (time >= 13000 && time < 23000) return envNight;
        return envDayClear;
    }

    private double getLightMultiplier(Player player) {
        int lightLevel = player.getLocation().getBlock().getLightLevel();
        double lightRatio = Math.min(1.0, lightLevel / lightMaxLevel);
        return lightMinMultiplier + (1.0 - lightMinMultiplier) * lightRatio;
    }

    /**
     * 检测玩家手持/头戴发光物品的加成
     * - 手电筒：手持光源方块/灯笼/火把
     * - 激光：手持末影之眼/钻石（模拟）
     */
    private double getGlowBoost(Player player) {
        Material mainHand = player.getInventory().getItemInMainHand().getType();
        Material offHand = player.getInventory().getItemInOffHand().getType();

        boolean hasFlashlight = isLightSource(mainHand) || isLightSource(offHand);
        boolean hasLaser = isLaserSource(mainHand) || isLaserSource(offHand);

        if (hasFlashlight && hasLaser) return flashlightBoost;
        if (hasFlashlight) return flashlightBoost;
        if (hasLaser) return laserBoost;
        return 1.0;
    }

    private boolean isLightSource(Material mat) {
        return mat == Material.TORCH || mat == Material.SOUL_TORCH
                || mat == Material.LANTERN || mat == Material.SOUL_LANTERN
                || mat == Material.SEA_LANTERN || mat == Material.GLOWSTONE
                || mat == Material.SHROOMLIGHT || mat == Material.JACK_O_LANTERN
                || mat == Material.END_ROD || mat == Material.CANDLE
                || mat == Material.CAMPFIRE || mat == Material.SOUL_CAMPFIRE;
    }

    private boolean isLaserSource(Material mat) {
        return mat == Material.ENDER_EYE || mat == Material.ENDER_PEARL
                || mat == Material.BEACON || mat == Material.DIAMOND
                || mat == Material.AMETHYST_SHARD || mat == Material.SPYGLASS;
    }

    // ==================== Getter/Setter ====================

    public void setMaxVisionRange(double maxVisionRange) {
        this.maxVisionRange = maxVisionRange;
    }

    public double getMaxVisionRange() {
        return maxVisionRange;
    }

    // ==================== 内部类 ====================

    private record RayResult(boolean blocked, int foliageCount) {}
}
