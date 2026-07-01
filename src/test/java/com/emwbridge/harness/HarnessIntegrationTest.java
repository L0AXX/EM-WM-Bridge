package com.emwbridge.harness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness集成测试 - 验证AI感知链路
 */
@DisplayName("Harness集成测试")
public class HarnessIntegrationTest {

    private ScenarioHarness harness;

    @BeforeEach
    void setUp() {
        harness = new ScenarioHarness();
        harness.init();
    }

    @AfterEach
    void tearDown() {
        harness.cleanup();
    }

    @Test
    @DisplayName("场景1：警戒追击")
    void testAlertAndChaseScenario() {
        ScenarioHarness.ScenarioResult result = harness.runAlertAndChaseScenario();
        System.out.println(result.getReport());
        assertTrue(result.success, "警戒追击场景失败");
    }

    @Test
    @DisplayName("场景2：遮挡搜索滞回保护")
    void testCoverAndSearchScenario() {
        ScenarioHarness.ScenarioResult result = harness.runCoverAndSearchScenario();
        System.out.println(result.getReport());
        assertTrue(result.success, "遮挡搜索场景失败");
    }

    @Test
    @DisplayName("场景3：重新出现重新锁定")
    void testReappearScenario() {
        ScenarioHarness.ScenarioResult result = harness.runReappearScenario();
        System.out.println(result.getReport());
        assertTrue(result.success, "重新出现场景失败");
    }

    @Test
    @DisplayName("场景4：完整脱战链路")
    void testFullCycle() {
        ScenarioHarness.ScenarioResult result = harness.runFullCycleScenario();
        System.out.println(result.getReport());
        assertTrue(result.success, "完整链路验证失败");
    }
}
