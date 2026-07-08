package com.emwbridge.ai.personality;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.AIDecision;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 需求4（永不撤退 + 性格预设）单元测试。
 *
 * 覆盖：
 *  - 4.1 neverRetreat：实体即使血量见底也绝不进入 RETREAT
 *  - 4.1 撤退阈值覆盖：EMWMWeaponConfig.retreatHpThreshold 经 per-entity 覆盖生效
 *  - 4.1 零回归：未配置撤退阈值时保留历史 0.15 兜底
 *  - 4.2 性格预设：config.yml personality.presets.<name> 解析与强制指定
 */
@DisplayName("PersonalityManager 需求4（永不撤退 + 性格预设）测试")
class PersonalityManagerTest {

    private EMWMBridge plugin;
    private PersonalityManager pm;

    @BeforeEach
    void setUp() {
        plugin = mock(EMWMBridge.class, withSettings().lenient());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EMWM-Test"));
        pm = new PersonalityManager(plugin);
    }

    @Test
    @DisplayName("4.1 neverRetreat=true 时即使血量 1% 也绝不 RETREAT")
    void neverRetreat_blocksRetreatAtCriticalHp() {
        UUID uuid = UUID.randomUUID();
        pm.assignPersonality(uuid, PersonalityType.CAUTIOUS, true, null);

        // 极端低血量，普通单位必然撤退
        AIDecision critical = pm.decide(uuid, 0.01, 50.0);
        assertNotEquals(AIDecision.RETREAT, critical, "neverRetreat 单位不得进入 RETREAT");

        // 中等血量同样不应撤退
        AIDecision mid = pm.decide(uuid, 0.5, 50.0);
        assertNotEquals(AIDecision.RETREAT, mid, "neverRetreat 单位不得进入 RETREAT");
    }

    @Test
    @DisplayName("4.1 撤退阈值覆盖：retreatHpThreshold=0.5 时 40% 血量撤退、60% 不撤退")
    void retreatHpThresholdOverride_applies() {
        UUID uuid = UUID.randomUUID();
        pm.assignPersonality(uuid, PersonalityType.RECKLESS, false, 0.5);

        assertEquals(AIDecision.RETREAT, pm.decide(uuid, 0.40, 50.0),
                "低于覆盖阈值 0.5 应撤退");
        assertNotEquals(AIDecision.RETREAT, pm.decide(uuid, 0.60, 50.0),
                "高于覆盖阈值 0.5 不应撤退");
    }

    @Test
    @DisplayName("4.1 零回归：未配置撤退阈值时保留历史 0.15 兜底（RECKLESS）")
    void legacyFloor_preservedWhenNoOverride() {
        UUID uuid = UUID.randomUUID();
        pm.assignPersonality(uuid, PersonalityType.RECKLESS, false, null);

        assertEquals(AIDecision.RETREAT, pm.decide(uuid, 0.10, 50.0),
                "低于历史兜底 0.15 应撤退");
        assertNotEquals(AIDecision.RETREAT, pm.decide(uuid, 0.20, 50.0),
                "高于历史兜底 0.15 不应撤退");
    }

    @Test
    @DisplayName("4.2 性格预设解析：personality.presets.<name> 映射为 PersonalityType")
    void resolvePreset_parsesFromConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("personality.presets.fanatic", "RECKLESS");
        cfg.set("personality.presets.guardians", "CAPTAIN");
        cfg.set("personality.presets.ambusher", "AMBUSH");
        pm.reload(cfg);

        assertEquals(PersonalityType.RECKLESS, pm.resolvePreset("fanatic"));
        assertEquals(PersonalityType.CAPTAIN, pm.resolvePreset("guardians"));
        assertEquals(PersonalityType.AMBUSH, pm.resolvePreset("ambusher"));
        assertNull(pm.resolvePreset("does_not_exist"), "未知预设应返回 null");
    }

    @Test
    @DisplayName("4.2 模板强制指定性格：resolvePreset 结果可经 assignPersonality 生效")
    void preset_forcesPersonality() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("personality.presets.fanatic", "RECKLESS");
        pm.reload(cfg);

        PersonalityType resolved = pm.resolvePreset("fanatic");
        assertNotNull(resolved);

        UUID uuid = UUID.randomUUID();
        pm.assignPersonality(uuid, resolved, false, null);
        assertEquals(PersonalityType.RECKLESS, pm.getPersonality(uuid),
                "强制指定的性格应覆盖 tier 随机 roll");
    }

    @Test
    @DisplayName("4.2 无效性格预设名应被安全忽略（返回 null，不抛异常）")
    void resolvePreset_invalidName_returnsNull() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("personality.presets.bad", "NOT_A_REAL_TYPE");
        // 不应抛异常
        pm.reload(cfg);
        assertNull(pm.resolvePreset("bad"));
    }

    @Test
    @DisplayName("removeEntity 清理 per-entity 撤退覆盖状态")
    void removeEntity_clearsOverrides() {
        UUID uuid = UUID.randomUUID();
        pm.assignPersonality(uuid, PersonalityType.RECKLESS, true, 0.5);
        assertTrue(pm.isNeverRetreat(uuid));
        assertNotNull(pm.getRetreatHpThreshold(uuid));

        pm.removeEntity(uuid);
        assertFalse(pm.isNeverRetreat(uuid));
        assertNull(pm.getRetreatHpThreshold(uuid));
    }
}
