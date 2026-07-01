package com.emwbridge.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TarkovEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final LivingEntity entity;
    private final Player trigger;
    private final EventType eventType;
    private final double intensity;
    private boolean cancelled;

    public TarkovEvent(LivingEntity entity, Player trigger, EventType eventType, double intensity) {
        this.entity = entity;
        this.trigger = trigger;
        this.eventType = eventType;
        this.intensity = intensity;
        this.cancelled = false;
    }

    public LivingEntity getEntity() { return entity; }
    public Player getTrigger() { return trigger; }
    public EventType getEventType() { return eventType; }
    public double getIntensity() { return intensity; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }

    public enum EventType {
        PANIC_MODE,       // 恐慌模式 - 被打急了什么都不管疯狂扫射
        LUCK_SHOT,        // 幸运一击 - 小概率打出完美射击
        MALFUNCTION,      // 卡壳 - 武器故障
        SPRINT_EXHAUST,   // 冲刺疲劳 - 跑太久需要休息
        TACTICAL_MISTAKE, // 战术失误 - 走位失误暴露自己
        ADRENALINE        // 肾上腺素 - 残血时爆发
    }
}
