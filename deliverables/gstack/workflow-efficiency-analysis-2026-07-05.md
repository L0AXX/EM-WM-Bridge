# EM-WM-Bridge 开发流程效率审计与改进方案

**日期**：2026-07-05
**执行方**：gstack-product-reviewer（产品官）
**范围**：开发流程效率、依赖管理、部署交付、实施路线图

---

## TL;DR

当前 EM-WM-Bridge 的开发流程效率极低：每次代码变更的验证周期约 **4-6 分钟**（编译 30s + 部署 5s + 服务器启动 120s + 手动测试 60s + 关闭 30s），且 **零自动化回归**。一个简单的 if 分支修改也要跑完整真机流程。按每天 20 次验证循环计算，**每天浪费约 80-100 分钟在等待服务器启动上**。

核心问题不是"缺工具"——工具链（JUnit 5 + Mockito + JaCoCo + Gradle）都在——而是**工具链没有被串联成流水线**。JaCoCo 70% 门禁配了但从没绑到 build 任务上；deployAndTest 只是打印提示文字；.gitignore 是 Node.js 模板不是 Java 的；.gradle/ 目录 24 个文件被提交进了 git。

**最高优先级**：1 周内建立"保存即验证"的本地快速循环 + GitHub Actions CI，把 80%+ 的验证从真机移到自动化测试，真机只做最终集成验收。

---

## A. 开发流程效率审计

### A1. 当前"编码→测试→验证"全流程时间成本

当前验证一次代码变更的完整路径：

```
改代码 → gradlew compileJava (30s) → gradlew deploy (5s)
  → 手动启动 MC 服务器 (120s) → 等待 "Done" 消息
  → 手动运行 test-server.ps1 (60s) → 检查 6 个场景结果
  → 手动 stop 服务器 (30s)
```

| 阶段 | 耗时 | 自动化 | 瓶颈原因 |
|------|------|--------|----------|
| 编译 | 30s | 自动 | Gradle 冷启动慢，无 daemon |
| 部署 | 5s | 自动 | 复制 jar 到 plugins 目录 |
| 服务器启动 | 120s | 手动 | Paper 1.21.4 + EliteMobs + WeaponMechanics 全量加载 |
| 测试执行 | 60s | 半自动 | test-server.ps1 能跑但需手动触发 |
| 服务器关闭 | 30s | 半自动 | 脚本发送 stop 但需等待 |
| **合计** | **~245s (4min)** | | |

**实际场景中更糟**：如果测试发现 bug，你需要改代码 → 重新走全流程。一天如果做 15-20 次迭代，**纯等待时间就占 60-80 分钟**，还不算上下文切换的认知成本。

### A2. 手动测试带来的效率损失量化

#### 直接损失

| 损失项 | 单次成本 | 日频次（估） | 日损失 | 月损失（22天） |
|--------|----------|-------------|--------|---------------|
| 服务器启动等待 | 120s | 15 | 30min | 11h |
| 人工检查测试结果 | 30s | 15 | 7.5min | 2.75h |
| 上下文切换（等待→回来→回忆刚才改了啥） | 60s | 15 | 15min | 5.5h |
| **月度总计** | | | | **~19.25h** |

每月浪费近 **20 个工时**在可自动化的等待和检查上。

#### 隐性损失（更致命）

1. **回归测试缺失**：当前 6 个 test-server.ps1 场景只验证最基础的"插件能加载、命令能用"。P0 修复中的 11 项致命问题（双射击冲突、Folia 线程安全、热重载幽灵怪等）**没有一个被这 6 个场景覆盖**。每次改代码你不知道有没有破坏之前能工作的东西——这不是效率问题，是**质量风险**。

2. **不敢重构**：因为没有自动化回归，开发者本能地避免改动已有代码。P0 报告里 8 个死代码功能长期存在却没人清理，就是因为"动了不知道会不会坏"。

3. **无法持续集成**：团队成员（哪怕只有一个人）无法信任 main 分支的状态。3 次 git 提交中有 2 次是"feat: auto committed"——提交习惯已经退化到无意义级别。

### A3. 不同场景下的痛点分析

| 场景 | 当前流程痛点 | 核心问题 |
|------|-------------|---------|
| **新功能开发** | 写完代码无法快速验证逻辑正确性，必须部署到真机 | 缺乏单元测试层的快速反馈 |
| **Bug 修复** | 修复后无法确认是否引入新 bug，需要全量手动回归 | 无自动化回归测试套件 |
| **重构** | 每次重构后需要 4 分钟验证"没搞坏"，导致重构意愿极低 | 测试反馈周期太长 |
| **发布** | 没有发布检查清单，没有版本标记，没有回滚预案 | 零发布流程 |
| **协作** | 新人 clone 后无法直接构建（硬编码路径），.gradle/ 被提交 | 构建环境不可复现 |

---

## B. 开发流程改进方案

### B1. 三阶段验证流程设计

目标：**80%+ 的验证在 < 10 秒内完成，真机测试只在里程碑节点执行**。

```
阶段 1：本地快速验证（开发者每次保存）
  gradlew test → 6-10 秒 → 398 个测试 + JaCoCo 覆盖率门禁
  └─ 覆盖：纯逻辑（战术决策、感知计算、配置解析、武器管理）

阶段 2：CI 自动验证（每次 push / PR）
  GitHub Actions → compile + test + coverage + 静态分析
  └─ 覆盖：跨平台编译验证 + 覆盖率回归 + 代码质量

阶段 3：真机集成验证（里程碑节点）
  自动部署到测试服务器 → test-server.ps1 → 人工验收
  └─ 覆盖：Bukkit API 集成、多插件交互、Folia 线程、性能
```

### B2. "保存即验证"快速反馈循环

**方案：Gradle continuous build + IDE 集成**

```groovy
// build.gradle 新增
tasks.register('watchTest') {
    dependsOn 'test'
    doLast {
        println "测试完成，等待文件变更..."
    }
}
```

实际操作路径：

1. **IDE 配置**：IntelliJ IDEA / VS Code 配置 "Save Action" → 触发 `gradlew test --continuous`
2. **Gradle daemon 常驻**：`gradlew --daemon` 首次启动 30s，后续增量编译 + 测试 < 10s
3. **测试分层**：将测试分为 `unit`（纯逻辑，无 Bukkit 依赖）和 `integration`（需要 mock Bukkit），unit 测试在 3s 内跑完

```groovy
// 分层测试配置
sourceSets {
    unitTest {
        java {
            srcDir 'src/test/java'
            exclude '**/integration/**'
            exclude '**/harness/**'
        }
    }
}

task unitTest(type: Test) {
    testClassesDirs = sourceSets.unitTest.output.classesDirs
    classpath = sourceSets.unitTest.runtimeClasspath
    useJUnitPlatform()
    // 快速失败：第一个测试失败就停止
    failFast = true
}
```

**预期效果**：开发者按 Ctrl+S，3 秒内看到 200+ 个单元测试结果。只有单元测试全绿才进入下一步。

### B3. 无真实 MC 服务器完成 80%+ 验证

当前项目已有的测试基础设施评估：

| 组件 | 状态 | 可信度 | 评价 |
|------|------|--------|------|
| IntegrationTestBase | 可用 | 中 | Mockito mock Bukkit 边界，WeaponMechanicsAPI 用 mockStatic |
| PerceptionHarness | 可用 | 低 | 反射注入私有字段，脆弱但有效 |
| ScenarioHarness | 可用 | 低 | 场景覆盖浅，只验证状态机转换 |
| JaCoCo | 配了没用 | 零 | 70% 门禁从未执行 |

**改进策略：三层测试金字塔**

```
           ┌─────────┐
           │  E2E    │  5-10 个（真机，手动/半自动）
           │ (真机)  │  覆盖：插件加载、命令、AI 行为、Folia
           ├─────────┤
           │ 集成测试 │  20-30 个（Mockito mock Bukkit）
           │ (JVM)   │  覆盖：模块间交互、事件链路、配置热重载
           ├─────────┤
           │ 单元测试 │  200+ 个（纯逻辑）
           │ (JVM)   │  覆盖：战术决策、感知算法、距离计算、配置解析
           └─────────┘
```

**关键行动：将可测试逻辑从 Bukkit 依赖中抽离**

项目中大量逻辑可以脱离 Bukkit 测试：
- `TarkovTactics.decideTacticalAction()` — 纯决策逻辑，输入参数输出动作
- `AlertStage.fromExposure()` — 纯数学计算
- `HostilityMatrix.getRelation()` — 纯查表逻辑
- `EMWMWeaponConfig` — 纯配置 POJO
- `TacticalUtils` — 纯几何计算
- `SoundPropagationUtils` — 纯物理计算

这些类的测试不需要 mock 任何 Bukkit API，应该成为"3 秒内跑完"的单元测试主力。

**对于需要 Bukkit API 的集成测试**：
- 维持当前 IntegrationTestBase 的 Mockito 方案（MockBukkit 不可用是暂时的）
- 补充关键路径：`MobWeaponManager.bindWeapon()` → `shoot()` → `reload()` 全链路
- 补充 `TarkovAIEngine.tickEntity()` 的集成测试（当前零覆盖，是最核心的方法）

### B4. 版本号管理策略

当前版本 `1.3.0` 完全脱离现实。建议：

```
v0.3.0-alpha    ← 当前（P0 修复后，编译通过但未经真机验证）
v0.4.0-alpha    ← P1 修复后（功能与声称一致）
v0.9.0-beta     ← 黑盒可验收（测试覆盖达标 + 真机冒烟通过）
v1.0.0          ← 正式发布（性能压测通过 + CI 全绿 + 文档完整）
```

**语义化版本规则**：
- `0.x.0-alpha`：功能不完整，API 不稳定，不保证向后兼容
- `0.x.0-beta`：功能完整，API 基本稳定，可能有已知 bug
- `1.0.0+`：生产可用，遵循 semver 向后兼容承诺

**版本号来源统一**：只从 `build.gradle` 的 `version` 属性读取，`plugin.yml` 中用 `${version}` 或构建时自动替换。当前两处硬编码 `1.3.0` 容易不同步。

---

## C. 依赖与构建管理

### C1. 解决本地 jar 硬编码问题

**当前问题**（build.gradle:37-41）：
```groovy
compileOnly files(
    'F:/WeaponMechaincs/MechanicsMain/weaponmechanics-build/build/libs/WeaponMechanics-4.3.0.jar',
    'F:/WeaponMechaincs/MechanicsCore/mechanicscore-build/build/libs/MechanicsCore-4.3.0.jar',
    'F:/LOAXX Devlop CLI/GreyZone S_1.21.4JDK21/plugins/EliteMobs.jar'
)
```

`libs/` 目录已有 jar 副本，`testImplementation` 已经用了 `libs/` 相对路径，但 `compileOnly` 还在用绝对路径。这是**最小修改量**的修复：

**方案 A（立即可做，5 分钟）**：compileOnly 也改用 libs/ 相对路径
```groovy
compileOnly files(
    'libs/WeaponMechanics-4.3.0.jar',
    'libs/MechanicsCore-4.3.0.jar',
    'libs/EliteMobs.jar'
)
```

**方案 B（中期，推荐）**：使用 Gradle 的 flatDir 仓库
```groovy
repositories {
    flatDir {
        dirs 'libs'
    }
}

dependencies {
    compileOnly name: 'WeaponMechanics', version: '4.3.0'
    compileOnly name: 'MechanicsCore', version: '4.3.0'
    compileOnly name: 'EliteMobs', version: 'unspecified'
}
```

**方案 C（长期，最优）**：如果 WeaponMechanics/MechanicsCore 有 Maven 仓库或 JitPack 支持，改用远程依赖。EliteMobs 如果有 JitPack 也一并迁移。

### C2. 让新开发者 clone 后立即能构建

**当前障碍清单**：

| 障碍 | 严重度 | 修复方案 |
|------|--------|---------|
| `compileOnly` 硬编码绝对路径 | 阻断 | 改用 `libs/` 相对路径（方案 A） |
| 只有 `gradlew.bat` 没有 `gradlew` | 阻断 | `gradle wrapper --gradle-version 8.9` 生成跨平台 wrapper |
| `.gitignore` 是 Node.js 模板 | 误导 | 替换为 Java/Gradle 专用 .gitignore |
| `.gradle/` 24 个文件被提交进 git | 污染 | `git rm -r --cached .gradle/` + 加入 .gitignore |
| `build/` 在 .gitignore 但 `bin/` 不在 | 隐患 | 添加 `bin/` 到 .gitignore |
| `libs/` jar 文件被提交（12MB） | 可接受 | 二进制依赖入 git 可接受，但应记录版本来源 |
| 无 README | 阻断 | 添加 README.md 含构建步骤 |
| Java 21 要求未文档化 | 误导 | README + build.gradle 中显式声明 |

**修复后的 .gitignore**：
```gitignore
# Gradle
.gradle/
build/
bin/

# IDE
.idea/
.vscode/
*.iml
*.ipr
*.iws
.project
.classpath
.settings/

# OS
.DS_Store
Thumbs.db

# Logs
*.log

# Keep libs (binary dependencies)
!libs/*.jar
```

### C3. 构建产物管理策略

当前 `jar` 任务产出 `EM-WM-Bridge-1.3.0.jar`，每次构建覆盖，无版本历史。

**改进方案**：

1. **构建产物命名**：`EM-WM-Bridge-${version}.jar`（已实现）
2. **版本化存档**：每次 release tag 时归档 jar 到 `dist/` 或 GitHub Releases
3. **构建信息注入**：在 jar 中写入构建时间、git commit hash
```groovy
jar {
    manifest {
        attributes(
            'Implementation-Version': project.version,
            'Build-Time': new Date().format('yyyy-MM-dd HH:mm:ss'),
            'Git-Commit': 'git rev-parse --short HEAD'.execute().text.trim()
        )
    }
}
```

---

## D. 发布与交付流程

### D1. 自动化部署流程设计

```
开发者 push → GitHub Actions 触发
  ├─ Stage 1: 编译验证 (compileJava)
  ├─ Stage 2: 单元测试 (unitTest, < 10s)
  ├─ Stage 3: 集成测试 (test, < 30s)
  ├─ Stage 4: 覆盖率门禁 (jacocoTestCoverageVerification)
  ├─ Stage 5: 构建 jar (jar)
  └─ Stage 6: [仅 main 分支] 部署到测试服务器
      └─ SCP jar 到测试服务器 plugins/ 目录
      └─ 触发服务器重启（或等待下次重启）
```

**GitHub Actions workflow**（`.github/workflows/ci.yml`）：

```yaml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}
      - name: Compile
        run: ./gradlew compileJava --no-daemon
      - name: Test
        run: ./gradlew test --no-daemon
      - name: Coverage Verification
        run: ./gradlew jacocoTestCoverageVerification --no-daemon
      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: build/reports/jacoco/
      - name: Build jar
        run: ./gradlew jar --no-daemon
      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: EM-WM-Bridge-${{ github.sha }}
          path: build/libs/EM-WM-Bridge-*.jar
```

### D2. 发布前检查清单

```markdown
## 发布前检查清单 (Pre-Release Checklist)

### 编译与测试
- [ ] `./gradlew clean build` 零错误零警告
- [ ] 全部单元测试通过
- [ ] 全部集成测试通过
- [ ] JaCoCo 覆盖率 ≥ 70%（或当前商定阈值）
- [ ] 无新增 TODO/FIXME 关键字在核心路径

### 功能验证
- [ ] 真机冒烟测试 6 项全通过（test-server.ps1）
- [ ] P0/P1 问题清单中无新增未解决项
- [ ] FeatureManager 标记与实际功能一致
- [ ] /emwm version 显示正确版本号

### 版本管理
- [ ] build.gradle version 已更新
- [ ] plugin.yml version 已更新（或自动同步）
- [ ] CHANGELOG.md 已更新
- [ ] Git tag 已创建（格式：v0.3.0-alpha）
- [ ] GitHub Release 已发布（含 jar 附件）

### 文档
- [ ] CODE_WIKI.md 与代码一致
- [ ] README.md 构建步骤有效
- [ ] 已知问题列表已更新

### 回滚准备
- [ ] 上一版本 jar 已备份
- [ ] 回滚步骤已文档化
```

### D3. 回滚预案

```markdown
## 回滚预案

### 场景 1：部署后服务器崩溃
1. 停止服务器
2. 删除 plugins/EM-WM-Bridge-*.jar
3. 恢复上一版本 jar 到 plugins/
4. 重启服务器
5. 验证 /emwm version 显示旧版本号

### 场景 2：部署后功能异常但不崩溃
1. 在游戏内执行 /emwm debug TRACE 获取详细日志
2. 如果问题可容忍：记录问题，计划热修复
3. 如果问题不可容忍：执行场景 1 回滚

### 场景 3：配置迁移导致数据问题
1. 备份 plugins/EM-WM-Bridge/ 目录
2. 恢复上一版本配置文件
3. 如果 PDC 数据（emwm_tier_pdc）格式变更导致不兼容：
   - 使用 /emwm reload 无法修复
   - 需要手动清除 PDC 标记或接受 AI 状态丢失

### 回滚时间目标
- 场景 1：< 5 分钟（有备份 jar）
- 场景 2：即时（游戏内操作）
- 场景 3：< 15 分钟（需文件操作）
```

---

## E. 实施路线图

### 第 1 周：止血 — 建立最小可用的自动化反馈循环

| 序号 | 任务 | 投入 | 产出 | 原则 |
|------|------|------|------|------|
| 1 | compileOnly 路径改为 libs/ 相对路径 | 10min | 任何人 clone 可编译 | 倾向行动 |
| 2 | 生成跨平台 gradlew（`gradle wrapper`） | 5min | Linux/Mac 可构建 | 实用主义 |
| 3 | 替换 .gitignore 为 Java/Gradle 版 | 5min | 不再提交 .gradle/ | 显式优于巧妙 |
| 4 | `git rm -r --cached .gradle/` 清理仓库 | 5min | 仓库干净 | 倾向行动 |
| 5 | JaCoCo 门禁绑定到 build：`check.dependsOn jacocoTestCoverageVerification` | 5min | 覆盖率门禁真正生效 | 显式优于巧妙 |
| 6 | 版本号降级 1.3.0 → 0.3.0-alpha（build.gradle + plugin.yml） | 5min | 版本号诚实 | 实用主义 |
| 7 | 创建 GitHub Actions CI workflow（compile + test + coverage） | 2h | push 自动验证 | 倾向行动 |
| 8 | 添加 README.md（构建步骤 + 环境要求） | 30min | 新人可上手 | 选完整性 |

**第 1 周总投入**：约 3.5 小时
**第 1 周产出**：clone 即可构建 + push 自动验证 + 覆盖率门禁生效 + 版本号诚实

### 第 2 周：提速 — 补充测试覆盖 + 分层测试

| 序号 | 任务 | 投入 | 产出 | 原则 |
|------|------|------|------|------|
| 1 | 将测试分为 unitTest / integrationTest 两层 | 2h | unitTest < 3s | 煮湖 |
| 2 | 补充 TarkovAIEngine.tickEntity() 单元测试 | 4h | 核心方法有覆盖 | 煮湖 |
| 3 | 补充 VisualPerception.calculate() 单元测试 | 3h | 感知系统有覆盖 | 煮湖 |
| 4 | 补充 MobWeaponManager 全链路集成测试 | 3h | 武器管理有保障 | 煮湖 |
| 5 | IDE 配置 Save Action 触发 unitTest | 30min | 保存即验证 | 倾向行动 |
| 6 | gradlew deploy 支持 -PserverPlugins 参数覆盖 | 30min | 部署路径可配置 | 显式优于巧妙 |

**第 2 周总投入**：约 13 小时
**第 2 周产出**：核心方法有测试覆盖 + 保存即验证循环建立 + 部署可配置

### 第 1 个月（含第 3-4 周）：成熟 — CI/CD 完善 + 发布流程

| 序号 | 任务 | 投入 | 产出 | 原则 |
|------|------|------|------|------|
| 1 | CI 添加静态分析（SpotBugs / Checkstyle） | 2h | 代码质量门禁 | 选完整性 |
| 2 | CI 添加构建产物归档（GitHub Releases） | 1h | 版本化 jar 存档 | 不重复 |
| 3 | test-server.ps1 改造为 CI 可触发（无需交互） | 3h | 真机测试半自动化 | 倾向行动 |
| 4 | 编写发布检查清单 + 回滚预案文档 | 1h | 发布流程标准化 | 显式优于巧妙 |
| 5 | 补充 20 个黑盒验收测试用例 | 4h | E2E 覆盖 | 煮湖 |
| 6 | CHANGELOG.md 建立 + Git Tag 策略 | 1h | 版本可追溯 | 不重复 |
| 7 | 依赖管理迁移到 flatDir 或远程仓库（方案 B/C） | 2h | 依赖可管理 | 实用主义 |

**第 3-4 周总投入**：约 14 小时
**第 1 个月总投入**：约 30.5 小时

### 预期效果对比

| 指标 | 当前 | 第 1 周后 | 第 2 周后 | 1 个月后 |
|------|------|----------|----------|---------|
| 单次验证耗时 | 4-6 min | 30s (CI) | 3s (unitTest) | 3s (unitTest) |
| 自动化回归 | 0% | 60% (CI) | 80% (分层) | 90% (含 E2E) |
| clone 到可构建 | 不可能 | < 5 min | < 5 min | < 5 min |
| 覆盖率门禁 | 装饰 | 生效 | 生效 | 生效 |
| 发布流程 | 无 | 无 | 有清单 | 有清单+回滚 |
| 版本号诚实度 | 虚高 | 诚实 | 诚实 | 诚实 + 可追溯 |

---

## 附：关键发现清单（按"快速见效"排序）

| # | 发现 | 影响 | 修复时间 | 优先级 |
|---|------|------|---------|--------|
| 1 | compileOnly 硬编码绝对路径 | 他人无法构建 | 10min | P0 |
| 2 | .gradle/ 24 个文件被提交进 git | 仓库污染 | 5min | P0 |
| 3 | .gitignore 是 Node.js 模板 | 误导 + 漏忽略 | 5min | P0 |
| 4 | JaCoCo 门禁未绑定到 build | 覆盖率门禁形同虚设 | 5min | P0 |
| 5 | 只有 gradlew.bat 无 gradlew | 无法跨平台构建 | 5min | P0 |
| 6 | 版本号 1.3.0 严重虚高 | 误导使用者 | 5min | P0 |
| 7 | 零 CI/CD | 无自动化验证 | 2h | P1 |
| 8 | deployAndTest 只打印提示 | 假自动化 | 30min | P1 |
| 9 | deploy 路径硬编码 | 不可配置 | 30min | P1 |
| 10 | 无 README | 新人无法上手 | 30min | P1 |
| 11 | Git 提交习惯差（"auto committed"） | 不可追溯 | 流程改进 | P2 |
| 12 | libs/ jar 无版本来源记录 | 依赖来源不明 | 文档 | P2 |

---

> 本报告基于对项目源码、构建配置、测试文件、Git 历史的逐项审读。所有时间估算基于单人开发场景。
