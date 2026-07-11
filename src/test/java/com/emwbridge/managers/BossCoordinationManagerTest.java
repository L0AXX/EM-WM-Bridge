package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 需求8 BossCoordinationManager 单元测试（纯逻辑）。
 *
 * <p>说明：依赖 EliteMobs 的 {@code ElitePhaseSwitchEvent}/{@code CustomBossEntity} 与 Bukkit 调度的
 * 部分仅在运行时调用，不在单测范围（Mockito-only 环境无法实例化 EliteMobs 类）。单测聚焦纯逻辑：
 * 规则解析（resolveRule）、触发阈值+冷却判定（shouldSummon）、环形生成点（computeSpawnLocation）。
 *
 * <p>阵营继承（8.2）由 EliteMobSpawnListener 名称匹配自动完成，无额外代码，故不在此单测。
 */
@DisplayName("BossCoordinationManager 协同召唤测试")
class BossCoordinationManagerTest {

    private BossCoordinationManager mgr;

    @BeforeEach
    void setUp() {
        mgr = new BossCoordinationManager(mock(EMWMBridge.class));
    }

    private BossCoordinationManager.CoordinationRule rule(String boss, List<Double> triggers, int cooldown) {
        return new BossCoordinationManager.CoordinationRule(boss, "greyzone_raider", "wardens", 4, triggers, cooldown);
    }

    @Test
    @DisplayName("resolveRule：按 Boss 名解析规则（忽略大小写）")
    void resolveRuleCaseInsensitive() {
        mgr.rules.put("greyzone_warlord", rule("greyzone_warlord", Arrays.asList(0.66, 0.33), 30));
        assertNotNull(mgr.resolveRule("greyzone_warlord"), "小写匹配");
        assertNotNull(mgr.resolveRule("GREYZONE_WARLORD"), "忽略大小写");
        assertNull(mgr.resolveRule("unknown_boss"), "未知 Boss 返回 null");
        assertNull(mgr.resolveRule(null), "null 返回 null");
    }

    @Test
    @DisplayName("shouldSummon：血量 <= 阈值 且 冷却已过 → true")
    void shouldSummonWhenThresholdAndCooldownPassed() {
        var r = rule("b", Arrays.asList(0.66, 0.33), 30);
        assertTrue(mgr.shouldSummon(r, 0.5, 100_000L, 0L), "首次召唤（last=0）且血量达标");
        assertTrue(mgr.shouldSummon(r, 0.33, 200_000L, 100_000L), "冷却 30s 已过");
    }

    @Test
    @DisplayName("shouldSummon：冷却期内 → false")
    void shouldSummonCooldownBlocks() {
        var r = rule("b", Arrays.asList(0.66, 0.33), 30);
        assertFalse(mgr.shouldSummon(r, 0.5, 110_000L, 100_000L), "仅过 10s < 30s 冷却");
    }

    @Test
    @DisplayName("shouldSummon：血量高于所有阈值 → false")
    void shouldSummonAboveThresholdFalse() {
        var r = rule("b", Arrays.asList(0.66, 0.33), 30);
        assertFalse(mgr.shouldSummon(r, 0.9, 100_000L, 0L), "血量 90% > 66% 阈值");
    }

    @Test
    @DisplayName("shouldSummon：无阈值配置（任意阶段切换）→ 冷却过后即触发")
    void shouldSummonNoThresholdTriggersAnyPhase() {
        var r = rule("b", Collections.emptyList(), 30);
        assertTrue(mgr.shouldSummon(r, 1.0, 100_000L, 0L), "无阈值时满血也触发");
        assertFalse(mgr.shouldSummon(r, 1.0, 110_000L, 100_000L), "冷却仍拦截");
    }

    @Test
    @DisplayName("computeSpawnLocation：Boss 周围环形分布（半径 3）")
    void computeSpawnLocationRing() {
        Location boss = new Location(null, 100, 64, 200);
        Location p0 = mgr.computeSpawnLocation(boss, 0, 4);
        Location p1 = mgr.computeSpawnLocation(boss, 1, 4);
        assertEquals(103.0, p0.getX(), 1e-9, "index0 角度0 → x+3");
        assertEquals(200.0, p0.getZ(), 1e-9);
        assertEquals(100.0, p1.getX(), 1e-9, "index1 角度90° → z+3");
        assertEquals(203.0, p1.getZ(), 1e-9);
        // 各点距 boss 约 3 格（手动计算，避免 null world 的 distance 问题）
        assertEquals(3.0, Math.hypot(p0.getX() - 100, p0.getZ() - 200), 1e-9);
        assertEquals(3.0, Math.hypot(p1.getX() - 100, p1.getZ() - 200), 1e-9);
    }
}
