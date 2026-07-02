package com.emwbridge.ai.combat;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 移动控制器 — 塔科夫式战术位移
 * 
 * 策略：
 * - 掩体后左右探头横移（SUSPICIOUS/ALERT）
 * - 侧翼包抄：以目标为圆心画大弧绕行
 * - 交替掩护：一人射击一人移动
 * - 撤退掩护：背向目标后退至最近掩体
 */
public class CoverMovement {

    private boolean restrictMovement = true;
    private double flankRadius = 15.0;      // 侧翼包抄距离
    private double retreatDistance = 25.0;  // 撤退目标距离

    public void reload(boolean restrictMovement) {
        this.restrictMovement = restrictMovement;
    }

    public void reloadAdvanced(double flankRadius, double retreatDistance) {
        this.flankRadius = flankRadius;
        this.retreatDistance = retreatDistance;
    }

    // ==================== 基础移动 ====================

    public void standAndAim(LivingEntity entity, Player target) {
        stopMoving(entity);
        faceTarget(entity, target);
    }

    public void faceTarget(LivingEntity entity, Player target) {
        Location loc = entity.getLocation();
        Location targetLoc = target.getLocation();
        double dx = targetLoc.getX() - loc.getX();
        double dz = targetLoc.getZ() - loc.getZ();
        double dy = target.getEyeLocation().getY() - loc.getY();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        loc.setYaw(yaw);
        loc.setPitch(pitch);
    }

    public void stopMoving(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            try { mob.getPathfinder().stopPathfinding(); }
            catch (Exception ignored) {}
        }
        entity.setVelocity(entity.getVelocity().setY(0).multiply(0));
    }

    // ==================== 掩体机制 ====================

    public boolean isBehindCover(LivingEntity entity, Player target) {
        Location loc = entity.getLocation();
        Vector toTarget = target.getLocation().toVector()
                .subtract(loc.toVector()).normalize();
        Vector perpRight = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();
        for (double side : new double[]{1.5, -1.5}) {
            Location check = loc.clone().add(perpRight.clone().multiply(side));
            check.setY(loc.getY() + 1);
            if (check.getBlock().getType().isSolid()) return true;
            check.setY(loc.getY() + 2);
            if (check.getBlock().getType().isSolid()) return true;
        }
        Vector behind = toTarget.clone().multiply(-1).normalize();
        Location checkBehind = loc.clone().add(behind.multiply(1));
        checkBehind.setY(loc.getY() + 1);
        if (checkBehind.getBlock().getType().isSolid()) return true;
        return !entity.hasLineOfSight(target);
    }

    /**
     * 在掩体后左右探头横移 — 模拟真人探头射击后缩回
     */
    public void strafeBehindCover(LivingEntity entity, Player target) {
        Location loc = entity.getLocation();
        // 选择掩体方向：朝有墙壁的一侧移动
        Vector toTarget = target.getLocation().toVector()
                .subtract(loc.toVector()).normalize();
        Vector perpRight = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize();

        // 检测哪一侧有掩体
        boolean rightSolid = isSolidAt(loc, perpRight, 1.5);
        boolean leftSolid = isSolidAt(loc, perpRight.clone().multiply(-1), 1.5);

        Vector strafeDir;
        if (rightSolid && leftSolid) {
            strafeDir = Math.random() < 0.5 ? perpRight : perpRight.clone().multiply(-1);
        } else if (rightSolid) {
            strafeDir = perpRight;
        } else if (leftSolid) {
            strafeDir = perpRight.clone().multiply(-1);
        } else {
            strafeDir = Math.random() < 0.5 ? perpRight : perpRight.clone().multiply(-1);
        }

        entity.setVelocity(entity.getVelocity().setY(0).add(strafeDir.multiply(0.18)));
    }

    private boolean isSolidAt(Location origin, Vector direction, double distance) {
        Location check = origin.clone().add(direction.clone().multiply(distance));
        check.setY(origin.getY() + 1);
        return check.getBlock().getType().isSolid();
    }

    // ==================== 战术移动 ====================

    /**
     * 侧翼包抄 — 以目标为圆心大弧绕行，寻找侧射角度
     */
    public void moveFlanking(LivingEntity entity, Player target, double progress) {
        if (!restrictMovement) return;
        Location entityLoc = entity.getLocation();
        Location targetLoc = target.getLocation();

        // 当前方向 vs 侧翼方向
        Vector toTarget = targetLoc.toVector().subtract(entityLoc.toVector());
        double currentDist = toTarget.length();

        // 选择绕行方向（左/右）
        int sign = (entity.getEntityId() % 2 == 0) ? 1 : -1; // 基于ID确定性选择
        Vector perp = new Vector(-toTarget.getZ() / currentDist, 0, toTarget.getX() / currentDist)
                .normalize().multiply(sign);

        // 目标位置：在目标侧面某个角度
        double angle = Math.toRadians(progress * 90); // 0→90度
        Vector flankOffset = toTarget.normalize()
                .multiply(flankRadius * Math.cos(angle))
                .add(perp.clone().multiply(flankRadius * Math.sin(angle)));
        Location flankGoal = projectToGround(targetLoc.clone().add(flankOffset), entity);

        // 设为 goal 并移动
        if (entity instanceof Mob mob) {
            try {
                mob.getPathfinder().moveTo(flankGoal, 1.0);
                return;
            } catch (Exception ignored) {}
        }
        // 备选：速度向量移动
        Vector moveDir = flankGoal.toVector().subtract(entityLoc.toVector()).normalize();
        entity.setVelocity(entity.getVelocity().setY(0).add(moveDir.multiply(0.3)));
    }

    /**
     * 交替掩护 — 友方射击时移动，友方移动时射击
     * squadMate 是同一小队的另一成员位置
     */
    public void moveBoundingOverwatch(LivingEntity entity, Player target, Location squadMate, boolean isMoving) {
        if (!restrictMovement) return;
        if (!isMoving) {
            standAndAim(entity, target);
            return;
        }
        // 移动时寻找下一个掩体点
        moveToNearestCover(entity, target, 10);
    }

    /**
     * 战术撤退 — 背向目标后撤至最近掩体
     */
    public void retreatTowardCover(LivingEntity entity, Player target) {
        if (!restrictMovement) return;
        Location entityLoc = entity.getLocation();

        // 搜索背后20格内的掩体
        Vector awayFromTarget = entityLoc.toVector()
                .subtract(target.getLocation().toVector()).normalize();

        Location bestCover = null;
        double bestScore = Double.MAX_VALUE;

        for (double dist = 3; dist <= retreatDistance; dist += 2) {
            for (int angle = -60; angle <= 60; angle += 30) {
                double rad = Math.toRadians(angle);
                Vector dir = awayFromTarget.clone();
                // 绕后退方向旋转
                double cos = Math.cos(rad), sin = Math.sin(rad);
                double x = dir.getX() * cos - dir.getZ() * sin;
                double z = dir.getX() * sin + dir.getZ() * cos;
                Vector offset = new Vector(x, 0, z).normalize().multiply(dist);
                Location check = entityLoc.clone().add(offset);

                // 计分：离实体够远 + 有固体方块
                boolean hasCover = check.clone().add(0, 1, 0).getBlock().getType().isSolid()
                        || check.clone().add(0, 2, 0).getBlock().getType().isSolid();
                if (hasCover && !check.getBlock().getType().isSolid()) {
                    double score = dist * 0.8 + Math.abs(angle) * 0.2;
                    if (score < bestScore) {
                        bestScore = score;
                        bestCover = check.clone();
                    }
                }
            }
        }

        if (bestCover != null) {
            if (entity instanceof Mob mob) {
                try {
                    mob.getPathfinder().moveTo(bestCover, 1.0);
                    return;
                } catch (Exception ignored) {}
            }
            Vector moveDir = bestCover.toVector().subtract(entityLoc.toVector()).normalize();
            entity.setVelocity(entity.getVelocity().setY(0).add(moveDir.multiply(0.35)));
        } else {
            // 无掩体可退 → 直接向后冲刺撤退
            Vector awayDir = entityLoc.toVector().subtract(target.getLocation().toVector()).normalize();
            entity.setVelocity(entity.getVelocity().setY(0).add(awayDir.multiply(0.35)));
        }
    }

    /**
     * 闪光后冲刺 — 向目标快速突进
     */
    public void rushToward(LivingEntity entity, Player target) {
        if (!restrictMovement) return;
        Location groundTarget = projectToGround(target.getLocation(), entity);
        if (entity instanceof Mob mob) {
            try {
                mob.getPathfinder().moveTo(groundTarget, 1.2);
                return;
            } catch (Exception ignored) {}
        }
        Vector dir = groundTarget.toVector()
                .subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(entity.getVelocity().setY(0).add(dir.multiply(0.5)));
    }

    /**
     * 寻找最近掩体并移动
     */
    public void moveToNearestCover(LivingEntity entity, Player target, double maxDist) {
        Location entityLoc = entity.getLocation();
        Location bestCover = null;
        double bestDist = Double.MAX_VALUE;

        for (double dx = -maxDist; dx <= maxDist; dx += 2) {
            for (double dz = -maxDist; dz <= maxDist; dz += 2) {
                Location check = entityLoc.clone().add(dx, 0, dz);
                if (check.getBlock().getType().isSolid()) continue;
                if (check.clone().add(0, 1, 0).getBlock().getType().isSolid()
                        || check.clone().add(0, 2, 0).getBlock().getType().isSolid()) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < bestDist && d > 1) {
                        bestDist = d;
                        bestCover = check.clone().add(0.5, 0, 0.5);
                    }
                }
            }
        }

        if (bestCover != null) {
            if (entity instanceof Mob mob) {
                try { mob.getPathfinder().moveTo(bestCover, 1.0); return; }
                catch (Exception ignored) {}
            }
            Vector dir = bestCover.toVector().subtract(entityLoc.toVector()).normalize();
            entity.setVelocity(entity.getVelocity().setY(0).add(dir.multiply(0.35)));
        }
    }

    // ==================== 原有移动 ====================

    public void strafe(LivingEntity entity) {
        Location loc = entity.getLocation();
        double angle = Math.random() < 0.5 ? 90 : -90;
        Vector strafeDir = new Vector(
                Math.cos(Math.toRadians(loc.getYaw() + angle)), 0,
                Math.sin(Math.toRadians(loc.getYaw() + angle))
        ).normalize();
        entity.setVelocity(entity.getVelocity().setY(0).add(strafeDir.multiply(0.15)));
    }

    public void moveAlongLineOfFire(LivingEntity entity, Player target, double aggressiveness, double maxRange) {
        if (!restrictMovement) return;
        double dist = entity.getLocation().distance(target.getLocation());
        boolean advance = Math.random() < aggressiveness;
        Vector lineOfFire = target.getLocation().toVector()
                .subtract(entity.getLocation().toVector()).normalize();
        if (advance && dist > 5) {
            entity.setVelocity(entity.getVelocity().setY(0)
                    .add(lineOfFire.clone().multiply(0.12)));
        } else if (!advance && dist < maxRange * 1.2) {
            entity.setVelocity(entity.getVelocity().setY(0)
                    .add(lineOfFire.clone().multiply(-0.1)));
        }
    }

    public void moveTowards(LivingEntity entity, Location location) {
        Location groundLoc = projectToGround(location, entity);
        if (entity instanceof Mob mob) {
            try { mob.getPathfinder().moveTo(groundLoc); return; }
            catch (Exception ignored) {}
        }
        Vector dir = groundLoc.toVector()
                .subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(entity.getVelocity().setY(0).add(dir.multiply(0.3)));
    }

    /**
     * Y轴防飞投影 — 目标位置高于实体>3格时投影到实体地面高度，防止AI追飞天玩家
     */
    private static Location projectToGround(Location loc, LivingEntity entity) {
        Location ground = loc.clone();
        if (ground.getY() - entity.getLocation().getY() > 3) {
            ground.setY(entity.getLocation().getY());
        }
        return ground;
    }

    public void repositionAfterBurst(LivingEntity entity, Player target) {
        if (!restrictMovement) return;
        if (isBehindCover(entity, target)) {
            strafeBehindCover(entity, target);
        } else {
            moveAlongLineOfFire(entity, target, 0.5, 30);
        }
    }
}
