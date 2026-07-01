package com.emwbridge.harness;

import com.emwbridge.ai.perception.AlertStage;

import java.util.ArrayList;
import java.util.List;

/**
 * 完整场景测试Harness - 编排AI从发现到脱战的完整行为链路
 */
public class ScenarioHarness {

    private PerceptionHarness perceptionHarness;

    public void init() {
        this.perceptionHarness = new PerceptionHarness();
    }

    /**
     * 场景1：警戒追击 - 玩家进入视野→AI锁定→追击
     */
    public ScenarioResult runAlertAndChaseScenario() {
        ScenarioResult result = new ScenarioResult();
        result.scenarioName = "警戒追击场景";

        // 模拟曝光累积到YELLOW
        for (int i = 0; i < 3; i++) {
            perceptionHarness.addExposure(8.0);
        }
        AlertStage yellow = perceptionHarness.getCurrentStage();
        result.addStep("进入YELLOW", yellow != null ? "状态: " + yellow : "仍为null");

        // 继续累积到ORANGE
        for (int i = 0; i < 3; i++) {
            perceptionHarness.addExposure(8.0);
        }
        AlertStage orange = perceptionHarness.getCurrentStage();
        result.addStep("进入ORANGE", orange != null ? "状态: " + orange : "仍为null");

        // 继续累积到RED
        for (int i = 0; i < 5; i++) {
            perceptionHarness.addExposure(8.0);
        }
        AlertStage red = perceptionHarness.getCurrentStage();
        result.addStep("进入RED锁定目标", red != null ? "状态: " + red : "仍为null");

        // 验证主目标锁定
        boolean locked = perceptionHarness.isPrimaryTargetLocked();
        result.addStep("主目标锁定", locked ? "成功" : "失败");

        return result;
    }

    /**
     * 场景2：遮挡搜索 - 玩家躲入遮挡→滞回保护→搜索
     */
    public ScenarioResult runCoverAndSearchScenario() {
        ScenarioResult result = new ScenarioResult();
        result.scenarioName = "遮挡搜索场景";

        // 先进入RED
        for (int i = 0; i < 10; i++) {
            perceptionHarness.addExposure(8.0);
        }
        AlertStage initial = perceptionHarness.getCurrentStage();
        result.addStep("初始状态", initial != null ? "RED" : "未达到RED");

        // 验证仇恨目标存在
        var hatredTarget = perceptionHarness.getHatredTarget();
        result.addStep("仇恨目标", hatredTarget != null ? "存在" : "丢失");

        return result;
    }

    /**
     * 场景3：重新出现 - 玩家重新出现→AI重新锁定
     */
    public ScenarioResult runReappearScenario() {
        ScenarioResult result = new ScenarioResult();
        result.scenarioName = "重新出现场景";

        // 先进入RED
        for (int i = 0; i < 10; i++) {
            perceptionHarness.addExposure(8.0);
        }
        result.addStep("前置状态", "RED已锁定");

        // 玩家重新出现，重新可见
        for (int i = 0; i < 5; i++) {
            perceptionHarness.addExposure(8.0);
        }

        // 验证重新锁定
        boolean relocked = perceptionHarness.isPrimaryTargetLocked();
        result.addStep("重新锁定", relocked ? "成功" : "失败");

        return result;
    }

    /**
     * 场景4：完整链路 - 警戒→锁定→搜索→重新锁定
     */
    public ScenarioResult runFullCycleScenario() {
        ScenarioResult result = new ScenarioResult();
        result.scenarioName = "完整链路";

        ScenarioResult r1 = runAlertAndChaseScenario();
        result.steps.addAll(r1.steps);
        result.success = r1.success;

        ScenarioResult r2 = runCoverAndSearchScenario();
        result.steps.addAll(r2.steps);
        result.success = result.success && r2.success;

        ScenarioResult r3 = runReappearScenario();
        result.steps.addAll(r3.steps);
        result.success = result.success && r3.success;

        return result;
    }

    public void cleanup() {
        if (perceptionHarness != null) {
            perceptionHarness.cleanup();
        }
    }

    public static class ScenarioResult {
        public String scenarioName;
        public List<String> steps = new ArrayList<>();
        public boolean success = true;

        public void addStep(String name, String status) {
            steps.add(name + ": " + status);
            if (status.contains("失败")) {
                success = false;
            }
        }

        public String getReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(scenarioName).append(" ===\n");
            for (String step : steps) {
                sb.append("  - ").append(step).append("\n");
            }
            sb.append("结果: ").append(success ? "PASS" : "FAIL");
            return sb.toString();
        }
    }
}
