package com.emwbridge.managers;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 需求8 Boss 协同召唤 — 微型服务端 Harness 集成测试。
 *
 * <p>通过 {@link BossCoordinationHarness} 自建的微型服务端（事件总线 + 调度器），
 * 端到端验证 BossCoordinationManager 的完整链路：
 * 阶段切换事件 → 规则解析 → 阈值/冷却判定 → 召唤波次 → 延迟编队（需求2 编制 API）。
 *
 * <p>覆盖：阈值命中召唤、阈值未达不召唤、冷却拦截、无阈值任意阶段触发、未知 Boss 无规则。
 */
@DisplayName("需求8 Boss 协同召唤 — 微型服务端 Harness 测试")
public class BossCoordinationHarnessTest {

    private BossCoordinationHarness harness;

    @BeforeEach
    void setUp() {
        harness = new BossCoordinationHarness();
        harness.init();
    }

    @AfterEach
    void tearDown() {
        harness.cleanup();
    }

    @Test
    @DisplayName("场景A：阶段切换血量 <= 阈值 → 召唤 count 名协同并编队")
    void belowThreshold_summonsAndEnlists() {
        harness.injectRule("greyzone_warlord", Arrays.asList(0.66, 0.33), 30, "wardens", 4, "greyzone_raider");

        LivingEntity boss = harness.makeBossEntity("greyzone_warlord", 0.5); // 50% <= 66%
        harness.firePhaseSwitch(boss, "greyzone_warlord", 0.5);
        harness.flushScheduler(); // 推进编队延迟任务

        assertEquals(4, harness.spawnedMinionCount(), "应召唤 4 名协同");
        // 4 名 minion 各自加入编队 → tryJoin 应被调用 4 次
        verify(harness.getSquadManager(), times(4))
                .tryJoin(any(LivingEntity.class), isNull(), isNull(), eq("wardens"));
    }

    @Test
    @DisplayName("场景B：阶段切换血量 > 所有阈值 → 不召唤")
    void aboveThreshold_noSummon() {
        harness.injectRule("greyzone_warlord", Arrays.asList(0.66, 0.33), 30, "wardens", 4, "greyzone_raider");

        LivingEntity boss = harness.makeBossEntity("greyzone_warlord", 0.9); // 90% > 66%
        harness.firePhaseSwitch(boss, "greyzone_warlord", 0.9);
        harness.flushScheduler();

        assertEquals(0, harness.spawnedMinionCount(), "血量高于阈值不应召唤");
        verify(harness.getSquadManager(), never()).tryJoin(any(), any(), any(), any());
    }

    @Test
    @DisplayName("场景C：冷却期内二次阶段切换 → 仅首波召唤")
    void cooldown_blocksSecondSummon() {
        harness.injectRule("greyzone_warlord", Arrays.asList(0.33), 30, "wardens", 2, "greyzone_raider");

        // 同一 Boss 实体（同 UUID）复用，以触发冷却判定
        LivingEntity boss = harness.makeBossEntity("greyzone_warlord", 0.2);
        harness.firePhaseSwitch(boss, "greyzone_warlord", 0.2); // 第1次：召唤
        harness.flushScheduler();
        harness.firePhaseSwitch(boss, "greyzone_warlord", 0.2); // 冷却期内（瞬时）：拦截
        harness.flushScheduler();

        assertEquals(2, harness.spawnedMinionCount(), "冷却期内只应召唤第一波（2 名）");
    }

    @Test
    @DisplayName("场景D：无阈值配置 → 任意阶段切换都触发")
    void noThreshold_triggersAnyPhase() {
        harness.injectRule("greyzone_warlord", Collections.emptyList(), 30, "wardens", 3, "greyzone_raider");

        LivingEntity boss = harness.makeBossEntity("greyzone_warlord", 1.0); // 满血
        harness.firePhaseSwitch(boss, "greyzone_warlord", 1.0);
        harness.flushScheduler();

        assertEquals(3, harness.spawnedMinionCount(), "无阈值时应任意阶段召唤");
    }

    @Test
    @DisplayName("场景E：未知 Boss → 无匹配规则不召唤")
    void unknownBoss_noRule() {
        // 未注入任何规则
        LivingEntity boss = harness.makeBossEntity("greyzone_unknown", 0.1);
        harness.firePhaseSwitch(boss, "greyzone_unknown", 0.1);
        harness.flushScheduler();

        assertEquals(0, harness.spawnedMinionCount(), "未知 Boss 不应召唤");
        verify(harness.getSquadManager(), never()).tryJoin(any(), any(), any(), any());
    }
}
