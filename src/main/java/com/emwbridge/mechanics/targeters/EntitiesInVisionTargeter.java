package com.emwbridge.mechanics.targeters;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.targeters.Targeter;
import me.deecaad.core.utils.ray.RayTrace;
import me.deecaad.core.utils.ray.EntityTraceResult;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EntitiesInVisionTargeter extends Targeter {

    private double maxRange = 30.0;
    private double coneAngle = 90.0;

    public EntitiesInVisionTargeter() {
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey("emwmbridge", "entities_in_vision");
    }

    @Override
    public boolean isEntity() {
        return true;
    }

    @Override
    protected Iterator<CastData> getTargets0(CastData cast) {
        LivingEntity source = cast.getSource();
        Location sourceLoc = source.getLocation();
        Vector direction = sourceLoc.getDirection();

        List<CastData> targets = new ArrayList<>();

        RayTrace rayTrace = new RayTrace()
            .withEntityFilter(entity -> entity == source || !(entity instanceof Player))
            .withRaySize(0.2);

        List<me.deecaad.core.utils.ray.RayTraceResult> hits = rayTrace.cast(
            source.getWorld(),
            sourceLoc.toVector().add(new Vector(0, 1.6, 0)),
            direction,
            maxRange
        );

        if (hits != null) {
            for (me.deecaad.core.utils.ray.RayTraceResult hit : hits) {
                if (hit instanceof EntityTraceResult entityHit) {
                    LivingEntity target = (LivingEntity) entityHit.getEntity();

                    Vector toTarget = target.getLocation().toVector()
                        .subtract(sourceLoc.toVector()).normalize();
                    double angle = Math.toDegrees(direction.angle(toTarget));

                    if (angle <= coneAngle / 2) {
                        CastData targetCast = cast.clone();
                        targetCast.setTargetEntity(target);
                        targets.add(targetCast);
                    }
                }
            }
        }

        return targets.iterator();
    }

    public double getMaxRange() {
        return maxRange;
    }

    public double getConeAngle() {
        return coneAngle;
    }

    @Override
    public Targeter serialize(SerializeData data) throws SerializerException {
        EntitiesInVisionTargeter targeter = new EntitiesInVisionTargeter();
        targeter.maxRange = data.of("Max_Range").getDouble().orElse(30.0);
        targeter.coneAngle = data.of("Cone_Angle").getDouble().orElse(90.0);
        return applyParentArgs(data, targeter);
    }
}
