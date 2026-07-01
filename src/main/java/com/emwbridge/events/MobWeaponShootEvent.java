package com.emwbridge.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class MobWeaponShootEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity shooter;
    private final Player target;
    private final String weaponTitle;
    private final double distance;
    private final boolean ads;

    public MobWeaponShootEvent(@NotNull LivingEntity shooter, @NotNull Player target,
                               String weaponTitle, double distance, boolean ads) {
        this.shooter = shooter;
        this.target = target;
        this.weaponTitle = weaponTitle;
        this.distance = distance;
        this.ads = ads;
    }

    @NotNull
    public LivingEntity getShooter() {
        return shooter;
    }

    @NotNull
    public Player getTarget() {
        return target;
    }

    public String getWeaponTitle() {
        return weaponTitle;
    }

    public double getDistance() {
        return distance;
    }

    public boolean isAds() {
        return ads;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
