package com.emwbridge.mechanics;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class AIAlertMechanic extends Mechanic {

    private double alertRadius = 20.0;

    public AIAlertMechanic() {
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey("emwmbridge", "ai_alert");
    }

    @Override
    protected void use0(CastData cast) {
        LivingEntity ai = cast.getSource();
        LivingEntity target = cast.getTarget();

        if (target == null) return;

        ai.getWorld().getNearbyEntities(ai.getLocation(), alertRadius, alertRadius, alertRadius)
            .stream()
            .filter(entity -> entity instanceof LivingEntity && entity != ai)
            .forEach(entity -> {
            });
    }

    public double getAlertRadius() {
        return alertRadius;
    }

    @Override
    public Mechanic serialize(SerializeData data) throws SerializerException {
        AIAlertMechanic mechanic = new AIAlertMechanic();
        mechanic.alertRadius = data.of("Alert_Radius").getDouble().orElse(20.0);
        return applyParentArgs(data, mechanic);
    }
}
