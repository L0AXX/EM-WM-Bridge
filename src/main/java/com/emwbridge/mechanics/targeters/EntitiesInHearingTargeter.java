package com.emwbridge.mechanics.targeters;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.targeters.Targeter;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EntitiesInHearingTargeter extends Targeter {

    private double maxRange = 15.0;

    public EntitiesInHearingTargeter() {
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey("emwmbridge", "entities_in_hearing");
    }

    @Override
    public boolean isEntity() {
        return true;
    }

    @Override
    protected Iterator<CastData> getTargets0(CastData cast) {
        LivingEntity source = cast.getSource();
        Location sourceLoc = source.getLocation();

        List<CastData> targets = new ArrayList<>();

        source.getWorld().getNearbyEntities(sourceLoc, maxRange, maxRange, maxRange)
            .stream()
            .filter(entity -> entity instanceof Player && entity != source)
            .forEach(entity -> {
                Player player = (Player) entity;
                CastData targetCast = cast.clone();
                targetCast.setTargetEntity(player);
                targets.add(targetCast);
            });

        return targets.iterator();
    }

    public double getMaxRange() {
        return maxRange;
    }

    @Override
    public Targeter serialize(SerializeData data) throws SerializerException {
        EntitiesInHearingTargeter targeter = new EntitiesInHearingTargeter();
        targeter.maxRange = data.of("Max_Range").getDouble().orElse(15.0);
        return applyParentArgs(data, targeter);
    }
}
