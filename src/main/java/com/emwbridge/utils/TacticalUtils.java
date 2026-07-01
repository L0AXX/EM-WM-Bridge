package com.emwbridge.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class TacticalUtils {

    public static Location findCoverLocation(LivingEntity entity, LivingEntity target, double minDistance, double maxDistance) {
        World world = entity.getWorld();
        Location entityLoc = entity.getLocation();
        Location targetLoc = target.getLocation();

        Vector awayDir = entityLoc.toVector().subtract(targetLoc.toVector()).normalize();

        List<Location> candidates = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            // 计算方向向量（不包含距离）
            Vector dir = new Vector(
                    awayDir.getX() * cos - awayDir.getZ() * sin,
                    0,
                    awayDir.getX() * sin + awayDir.getZ() * cos
            ).normalize();

            for (double dist = minDistance; dist <= maxDistance; dist += 2) {
                // BUGFIX: clone再multiply，防止就地修改导致坐标爆炸
                Location candidate = entityLoc.clone().add(dir.clone().multiply(dist));
                candidate.setY(entityLoc.getY());

                // 世界边界校验，防止越界
                if (!isInWorldBorder(candidate, world)) {
                    break;
                }

                if (isValidCover(candidate, targetLoc, world)) {
                    candidates.add(candidate);
                    break;
                }
            }
        }

        if (candidates.isEmpty()) {
            // 安全备用：直接远离目标
            Location safeLoc = entityLoc.clone().add(awayDir.clone().multiply(minDistance));
            safeLoc.setY(entityLoc.getY());
            return isInWorldBorder(safeLoc, world) ? safeLoc : entityLoc;
        }

        return candidates.get((int) (Math.random() * candidates.size()));
    }

    /**
     * 检查位置是否在世界边界内
     */
    private static boolean isInWorldBorder(Location loc, World world) {
        double border = world.getWorldBorder().getSize() / 2 + 16;
        return Math.abs(loc.getX()) < border && Math.abs(loc.getZ()) < border;
    }

    private static boolean isValidCover(Location coverLoc, Location targetLoc, World world) {
        if (world == null) return false;

        Block feet = world.getBlockAt(coverLoc.getBlockX(), coverLoc.getBlockY(), coverLoc.getBlockZ());
        Block head = world.getBlockAt(coverLoc.getBlockX(), coverLoc.getBlockY() + 1, coverLoc.getBlockZ());
        Block ground = world.getBlockAt(coverLoc.getBlockX(), coverLoc.getBlockY() - 1, coverLoc.getBlockZ());

        if (!feet.getType().isAir() || !head.getType().isAir()) return false;
        if (ground.getType().isAir()) return false;

        return !hasLineOfSight(targetLoc, coverLoc.clone().add(0, 1, 0));
    }

    public static boolean hasLineOfSight(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || to.getWorld() != world) return false;

        int steps = (int) from.distance(to) * 2;
        if (steps == 0) return true;

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(from.getX() + (to.getX() - from.getX()) * t);
            int y = (int) Math.floor(from.getY() + (to.getY() - from.getY()) * t);
            int z = (int) Math.floor(from.getZ() + (to.getZ() - from.getZ()) * t);

            Block block = world.getBlockAt(x, y, z);
            if (isSolid(block)) return false;
        }
        return true;
    }

    private static boolean isSolid(Block block) {
        return block.getType().isSolid() && !block.isPassable();
    }
}
