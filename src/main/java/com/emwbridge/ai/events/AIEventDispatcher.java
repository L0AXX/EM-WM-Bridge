package com.emwbridge.ai.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * AI 事件总线 — 跨模块广播 "发现目标" / "听到声音" 事件
 * 用于小队情报同步、战术决策触发
 */
public class AIEventDispatcher {

    // 事件类型
    public enum EventType {
        /** 视觉发现目标 */
        SIGHT,
        /** 听觉检测到声源 */
        SOUND,
        /** 进入 HOSTILE 锁定状态 */
        HOSTILE_LOCK,
        /** 闪光弹致盲 */
        FLASH_BLIND
    }

    private final Map<EventType, List<Consumer<AIEvent>>> listeners = new EnumMap<>(EventType.class);

    public AIEventDispatcher() {
        for (EventType type : EventType.values()) {
            listeners.put(type, new ArrayList<>());
        }
    }

    /** 注册事件监听器 */
    public void register(EventType type, Consumer<AIEvent> handler) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
    }

    /** 广播事件 */
    public void dispatch(AIEvent event) {
        List<Consumer<AIEvent>> handlers = listeners.get(event.type());
        if (handlers != null) {
            for (Consumer<AIEvent> h : handlers) {
                try {
                    h.accept(event);
                } catch (Exception ignored) {}
            }
        }
    }

    /** 创建并广播 SIGHT 事件 */
    public void sight(UUID aiEntityUuid, UUID targetPlayerUuid, double exposure) {
        dispatch(new AIEvent(EventType.SIGHT, aiEntityUuid, targetPlayerUuid, null, exposure));
    }

    /** 创建并广播 SOUND 事件 */
    public void sound(UUID aiEntityUuid, UUID sourcePlayerUuid, Location soundLoc, double loudness) {
        dispatch(new AIEvent(EventType.SOUND, aiEntityUuid, sourcePlayerUuid, soundLoc, loudness));
    }

    /** 创建并广播 HOSTILE_LOCK 事件 */
    public void hostileLock(UUID aiEntityUuid, UUID targetPlayerUuid, Location targetLoc) {
        dispatch(new AIEvent(EventType.HOSTILE_LOCK, aiEntityUuid, targetPlayerUuid, targetLoc, 100.0));
    }

    /** 创建并广播 FLASH_BLIND 事件 — 通知同小队所有 AI 目标已被闪 */
    public void flashBlind(UUID aiEntityUuid) {
        dispatch(new AIEvent(EventType.FLASH_BLIND, aiEntityUuid, null, null, 0));
    }

    // ==================== 事件类 ====================

    public static class AIEvent {
        private final EventType type;
        private final UUID aiEntityUuid;
        private final UUID targetPlayerUuid;
        private final Location location;
        private final double value;

        public AIEvent(EventType type, UUID aiEntityUuid, UUID targetPlayerUuid, Location location, double value) {
            this.type = type;
            this.aiEntityUuid = aiEntityUuid;
            this.targetPlayerUuid = targetPlayerUuid;
            this.location = location != null ? location.clone() : null;
            this.value = value;
        }

        public EventType type() { return type; }
        public UUID aiEntityUuid() { return aiEntityUuid; }
        public UUID targetPlayerUuid() { return targetPlayerUuid; }
        public Location location() { return location; }
        public double value() { return value; }
    }
}
