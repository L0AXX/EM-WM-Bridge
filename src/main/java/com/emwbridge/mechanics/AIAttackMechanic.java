package com.emwbridge.mechanics;

import me.deecaad.core.file.SerializeData;
import me.deecaad.core.file.SerializerException;
import me.deecaad.core.mechanics.CastData;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class AIAttackMechanic extends Mechanic {

    private double meleeRange = 5.0;
    private double shootRange = 30.0;

    public AIAttackMechanic() {
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return new NamespacedKey("emwmbridge", "ai_attack");
    }

    @Override
    protected void use0(CastData cast) {
        LivingEntity source = cast.getSource();
        LivingEntity target = cast.getTarget();

        if (target == null || target.isDead()) return;

        double distance = source.getLocation().distance(target.getLocation());

        if (distance <= meleeRange) {
            source.attack(target);
        }
    }

    public double getMeleeRange() {
        return meleeRange;
    }

    public double getShootRange() {
        return shootRange;
    }

    @Override
    public Mechanic serialize(SerializeData data) throws SerializerException {
        AIAttackMechanic mechanic = new AIAttackMechanic();
        mechanic.meleeRange = data.of("Melee_Range").getDouble().orElse(5.0);
        mechanic.shootRange = data.of("Shoot_Range").getDouble().orElse(30.0);
        return applyParentArgs(data, mechanic);
    }
}
