package com.emwbridge.mechanics;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class AISearchMechanic extends Mechanic {

    private double searchRadius = 15.0;
    private int searchDuration = 240;

    public AISearchMechanic() {
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey("emwmbridge", "ai_search");
    }

    @Override
    protected void use0(CastData cast) {
        LivingEntity ai = cast.getSource();
        Location startLoc = ai.getLocation();

        Vector randomDir = new Vector(
            (Math.random() - 0.5) * 2,
            0,
            (Math.random() - 0.5) * 2
        ).normalize();

        Location targetLoc = startLoc.clone().add(randomDir.multiply(searchRadius));

        if (ai instanceof Mob mob) {
            mob.getPathfinder().moveTo(targetLoc);
        }
    }

    public double getSearchRadius() {
        return searchRadius;
    }

    public int getSearchDuration() {
        return searchDuration;
    }

    @Override
    public Mechanic serialize(SerializeData data) throws SerializerException {
        AISearchMechanic mechanic = new AISearchMechanic();
        mechanic.searchRadius = data.of("Search_Radius").getDouble().orElse(15.0);
        mechanic.searchDuration = data.of("Search_Duration").getInt().orElse(240);
        return applyParentArgs(data, mechanic);
    }
}
