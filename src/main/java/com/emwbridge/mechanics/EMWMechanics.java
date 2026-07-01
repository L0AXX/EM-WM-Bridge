package com.emwbridge.mechanics;

import me.deecaad.core.mechanics.Mechanics;
import me.deecaad.core.mechanics.Targeters;
import me.deecaad.core.mechanics.defaultmechanics.Mechanic;
import me.deecaad.core.mechanics.targeters.Targeter;
import com.emwbridge.mechanics.targeters.EntitiesInVisionTargeter;
import com.emwbridge.mechanics.targeters.EntitiesInHearingTargeter;

/**
 * EM-WM-Bridge自定义Mechanic和Targeter注册器
 * 在插件启动时调用initialize()注册所有扩展组件
 */
public final class EMWMechanics {

    public static final Mechanic AI_ATTACK = registerMechanic(new AIAttackMechanic());
    public static final Mechanic AI_SEARCH = registerMechanic(new AISearchMechanic());
    public static final Mechanic AI_ALERT = registerMechanic(new AIAlertMechanic());

    public static final Targeter ENTITIES_IN_VISION = registerTargeter(new EntitiesInVisionTargeter());
    public static final Targeter ENTITIES_IN_HEARING = registerTargeter(new EntitiesInHearingTargeter());

    private EMWMechanics() {
    }

    private static Mechanic registerMechanic(Mechanic mechanic) {
        Mechanics.REGISTRY.add(mechanic);
        return mechanic;
    }

    private static Targeter registerTargeter(Targeter targeter) {
        Targeters.REGISTRY.add(targeter);
        return targeter;
    }

    /**
     * 初始化 - 确保静态块执行，注册所有自定义组件
     */
    public static void initialize() {
    }
}
