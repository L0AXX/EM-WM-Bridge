package com.emwbridge.events;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * EMWM 精英怪击杀事件
 *
 * 当 EM-WM-Bridge 管理的精英怪被击杀时触发。
 * 包含击杀方式（枪械/手雷/近战/爆炸/其他），供 Chemdah 等任务插件监听。
 *
 * Chemdah 示例：
 * listen<EMWMKillEvent> {
 *     if (it.killMethod == KillMethod.GRENADE) {
 *         player.quest("grenade_kills").progress += 1
 *     }
 * }
 */
public class EMWMKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity victim;
    private final Player killer;
    private final KillMethod killMethod;
    private final String tier;
    private final String weaponTitle;
    private final String combatState;

    public EMWMKillEvent(@NotNull LivingEntity victim, @Nullable Player killer,
                         @NotNull KillMethod killMethod, @Nullable String tier,
                         @Nullable String weaponTitle, @Nullable String combatState) {
        this.victim = victim;
        this.killer = killer;
        this.killMethod = killMethod;
        this.tier = tier;
        this.weaponTitle = weaponTitle;
        this.combatState = combatState;
    }

    @NotNull
    public LivingEntity getVictim() {
        return victim;
    }

    @Nullable
    public Player getKiller() {
        return killer;
    }

    @NotNull
    public KillMethod getKillMethod() {
        return killMethod;
    }

    @Nullable
    public String getTier() {
        return tier;
    }

    @Nullable
    public String getWeaponTitle() {
        return weaponTitle;
    }

    @Nullable
    public String getCombatState() {
        return combatState;
    }

    /**
     * 是否被手雷击杀（破片雷/闪光弹/烟雾弹/任何爆炸）
     */
    public boolean isGrenadeKill() {
        return killMethod == KillMethod.GRENADE || killMethod == KillMethod.EXPLOSION;
    }

    /**
     * 是否被枪械击杀
     */
    public boolean isGunKill() {
        return killMethod == KillMethod.GUN;
    }

    /**
     * 是否击杀的是 Boss
     */
    public boolean isBossKill() {
        return "boss".equalsIgnoreCase(tier);
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

    /**
     * 击杀方式枚举
     */
    public enum KillMethod {
        /** 枪械（WM 子弹/投射物） */
        GUN,
        /** 手雷（破片雷/闪光弹/烟雾弹/ThrowableManager） */
        GRENADE,
        /** 近战攻击 */
        MELEE,
        /** 爆炸（TNT/苦力怕等非手雷爆炸） */
        EXPLOSION,
        /** 其他（掉落/火焰/药水等） */
        OTHER
    }
}
