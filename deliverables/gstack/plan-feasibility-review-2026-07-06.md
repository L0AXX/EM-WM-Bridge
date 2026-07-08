# EM-WM-Bridge 3阶段测试基础设施改造方案 — 可行性评审报告

**日期**：2026-07-06
**场景**：计划审查（Plan Review）
**参与成员**：产品官（gstack-product-reviewer） + 质量门神（gstack-qa-lead）

---

## TL;DR（执行摘要）

- **整体判定**：🟡 **有条件可行** — 方案方向正确（分层测试金字塔、sourceSet隔离、CI自动化），但执行细节有3处致命错误
- **最致命问题**：方案对8个失败测试的根因诊断完全错误，按方案修复将无法解决任何失败
- **不可执行项**：WM/MC Maven Central迁移不可行（本地fat jar与Maven Central thin jar不是同一构建产物）
- **修正后总工期**：方案声称5-6周 → 实际3-4周（砍Docker路线）或6-8周（保留Docker但修正估算）
- **下一步**：先修8个测试（实际只需2个根因修复，各加1-2行stub）→ 调整JaCoCo门禁 → 推GitHub仓库

---

## 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🟡 条件 Go — 3处致命修正后可执行 |
| 严重度分布 | 🔴 3 / 🟠 4 / 🟡 5 / 🟢 3 |
| 关键行动项 | 11 条 |
| 修正后工期 | P0: 1.5-2天 / P1: 6-7天 / P2: 2周（砍Docker） |

---

## 1. 各成员核心结论

### 产品官（产品评审）
- **核心判断**：方案方向正确（修测试→隔离MockBukkit→自动化黑盒），但时间估算严重偏低、根因诊断有误、遗漏JaCoCo门禁阻断风险。3阶段评分分别 2/5、3/5、2/5。
- **关键建议**：P0必须先确认覆盖率再调门禁；P1合并PS1迁移（6个场景非5个）；P2砍掉Docker路线，保留PS1脚本作为手动回归。方案声称的ROI（3秒+10秒）夸大，实际为30-60秒（仍远优于4-6分钟真机）。

### 质量门神（QA技术验证）
- **核心判断**：实际运行测试套件并读取全部失败XML报告后确认——方案对8个失败测试的根因诊断100%错误。真正根因是 `aiEntity.getEyeLocation()` 返回null导致Mockito 5.x的 `any(Location.class)` 不匹配null参数。
- **关键建议**：AIVisionManagerTest加一行 `when(aiEntity.getEyeLocation()).thenReturn(mock(Location.class))` 即可修复6个；MobWeaponManagerIntegrationTest的 `shootEffect()` 加null检查修复2个。WM/MC Maven Central迁移不可行——本地jar(4.6MB fat)与Maven Central jar(506KB thin)差异9倍，迁移会导致依赖图剧变。

---

## 2. 综合审查发现（去重合并后按严重度排序）

| # | 严重度 | 类别 | 位置 | 问题描述 | 建议 | 来源 |
|---|--------|------|------|---------|------|------|
| 1 | 🔴 | 测试诊断 | AIVisionManagerTest:85 | 方案说"getTargetFacingMultiplier未mock"，实际已mock（行85）。根因是 `aiEntity.getEyeLocation()` 返回null，`any(Location.class)` 不匹配null（Mockito 5.x），mock未命中返回0.0，曝光值始终为0 | 加 `when(aiEntity.getEyeLocation()).thenReturn(mock(Location.class))` | 质量门神 |
| 2 | 🔴 | 测试诊断 | MobWeaponManagerIntegrationTest | 方案说"WM依赖不可用需mock getInstance()"，实际根因是 `shootEffect()` 中 `entity.getEyeLocation()` 返回null → NPE → catch吞掉 → 返回false。WM单例完全无关 | 在 `shootEffect()` 加null检查（生产代码修复） | 质量门神 |
| 3 | 🔴 | 依赖迁移 | build.gradle:libs/ | WM/MC Maven Central迁移不可行：本地MC jar 4.6MB(fat/shaded) vs Maven Central 506KB(thin)，差异9倍。MC Maven Central版引入kotlin-stdlib、HikariCP、XSeries等大量传递依赖，与本地shaded类可能冲突 | 放弃Maven Central迁移，保留本地jar + sourceSet隔离方案 | 质量门神 |
| 4 | 🟠 | CI门禁 | build.gradle:106-114 | JaCoCo `minimum = 0.70` + `check.dependsOn jacocoTestCoverageVerification` 已绑定。CI首次运行大概率在覆盖率检查步骤失败（当前覆盖率可能<70%） | P0先确认实际覆盖率，调门禁到合理值（如40%）或临时跳过 | 产品官 |
| 5 | 🟠 | sourceSet | build.gradle (新增) | 方案遗漏：testMockBukkit sourceSet需要显式声明compileOnly依赖（paper-api、WM、MC、EliteMobs），仅 `compileClasspath += sourceSets.main.output` 不包含compileOnly | 补充 `testMockBukkitCompileOnly` 声明 | 质量门神 |
| 6 | 🟠 | 时间估算 | 全方案 | P0声称1天→实际1.5-2天；P1声称3-7天→实际6-7天（不含WM迁移）；P2声称4周→Docker部分过重 | 按修正后工期排期 | 产品官 |
| 7 | 🟠 | 过度工程 | P2路线B | 49个源文件的项目搭Docker + Self-hosted Runner，运维成本远超收益。test-server.ps1是Windows脚本需重写bash，跨平台风险 | 砍掉Docker路线，保留PS1手动回归 | 双方共识 |
| 8 | 🟡 | ROI夸大 | 全方案 | "3秒单元测试+10秒MockBukkit"不现实：418个测试首次运行含JVM启动15-30秒；MockBukkit 6场景setup/teardown 30-60秒 | 修正为"30-60秒（仍远优于4-6分钟）" | 产品官 |
| 9 | 🟡 | 代码兼容 | IntegrationTestBase:101 | `new ItemStack(Material.IRON_HOE)` 与MockBukkit不兼容（需registry初始化），MobWeaponManagerIntegrationTest已改用mock但基类未改 | 基类也改为mock(ItemStack.class) | 产品官 |
| 10 | 🟡 | Folia兼容 | MobWeaponManager.reload() | 使用Folia调度器API `entity.getScheduler().execute()`，MockBukkit是否支持未知 | 验证MockBukkit对Folia API的支持度 | 产品官 |
| 11 | 🟡 | 目标矛盾 | build.gradle vs 方案 | build.gradle门禁70%，方案说30%→60%基线，两个目标矛盾 | 统一为单一覆盖率目标 | 产品官 |
| 12 | 🟢 | CI性能 | ci.yml | `--no-daemon` 冷启动，Gradle 9.5.1 + JDK 21 CI构建3-5分钟 | 可接受，后续优化 | 产品官 |
| 13 | 🟢 | git仓库 | libs/ 12MB | 3个jar共12MB已提交git，GitHub 100MB限制（当前未超但需监控） | 暂可接受 | 产品官 |
| 14 | 🟢 | 通知机制 | CI | 无监控/告警机制，CI失败通知未配置 | 配置GitHub Actions邮件通知 | 产品官 |
| 15 | 🟢 | PS1交互 | test-server.ps1:199 | `Read-Host`交互在CI环境会挂起 | CI不调用PS1 | 产品官 |

---

## 3. 逐阶段可行性判定

### 第一阶段 P0（声称1天 → 实际1.5-2天）— 🟡 有条件可行

| 子项 | 方案声称 | 实际验证 | 判定 |
|------|---------|---------|------|
| 修8个测试 | mock getTargetFacingMultiplier + mock WM.getInstance() + 拆分shoot逻辑 | 根因是getEyeLocation()返回null；修复只需2处各加1-2行stub | ❌ 诊断错误，但修复比方案更简单 |
| GitHub仓库 | 新建仓库+推送+CI验证 | 当前无git remote，需配置SSH/Token，推送12MB libs | ✅ 可行 |
| CI验证 | 流水线完整执行 | JaCoCo 70%门禁会阻断首次运行 | ❌ 需先调门禁 |

**修正执行顺序**：
1. `./gradlew jacocoTestReport` 确认实际覆盖率
2. 调整JaCoCo门禁到合理值（如40%）
3. 修8个测试（2个根因，各加1-2行stub）
4. 创建GitHub仓库 + 推送
5. CI验证

### 第二阶段 P1（声称3-7天 → 实际6-7天）— 🟢 基本可行

| 子项 | 方案声称 | 实际验证 | 判定 |
|------|---------|---------|------|
| sourceSet隔离 | 4行Groovy配置 | 方向正确但需补齐compileOnly声明（约15行） | ⚠️ 需补充配置 |
| WM Maven迁移 | 短期排除jar + 长期迁Maven Central | 不可行：fat jar≠thin jar，迁移导致依赖图剧变 | ❌ 放弃迁移 |
| MockBukkit启用 | 注释取消即可 | 需sourceSet隔离后才能启用 | ✅ 隔离后可行 |
| PS1场景迁移 | 5个场景翻译为JUnit5 | 实际6个场景，复杂度中高 | ✅ 可行，3-4天 |
| CI双测试 | 加testMockBukkit job | 约10-15行YAML改动 | ✅ 可行 |

**关键修正**：
- 砍掉WM Maven Central迁移，保留本地jar + sourceSet隔离
- 补齐testMockBukkit的compileOnly依赖声明
- 增加JaCoCoMerge task合并两套报告
- PS1场景是6个非5个

### 第三阶段 P2（声称4周 → 修正后2周）— 🟡 大幅缩减

| 子项 | 方案声称 | 实际验证 | 判定 |
|------|---------|---------|------|
| MockBukkit仿真黑盒 | P2路线A | 应与P1合并，不需等到P2 | ✅ 合并到P1 |
| Docker真机黑盒 | Self-hosted Runner + Paper容器 | 过度工程，GitHub-hosted runner也可跑Docker但不推荐 | ❌ 砍掉 |
| 部署参数化 | 环境变量指定插件目录 | 已在上一轮完成（-PserverPlugins） | ✅ 已完成 |
| 覆盖率基线 | 30%→60% | build.gradle门禁70%，目标矛盾，需统一 | ⚠️ 需统一目标 |

**修正后P2内容**：覆盖率提升 + 测试文档 + PS1脚本保留为手动回归（每月/每版本一次）

---

## 4. 8个失败测试正确修复方案（质量门神实测验证）

### AIVisionManagerTest 6个失败 — 1行修复

**根因链**：
```
aiEntity.getEyeLocation() 返回 null (mock未stub)
  → any(Location.class) 不匹配 null (Mockito 5.x: Class.isInstance(null) = false)
  → getTargetFacingMultiplier mock未命中
  → 返回double默认值 0.0
  → increment = 3.0 * 0.0 = 0.0
  → 曝光值始终为0
  → 6个测试级联失败
```

**修复**（AIVisionManagerTest.java setUp() 中添加）：
```java
when(aiEntity.getEyeLocation()).thenReturn(mock(Location.class));
```

### MobWeaponManagerIntegrationTest 2个失败 — 生产代码修复

**根因链**：
```
mockEntity.getEyeLocation() 返回 null
  → shootEffect(): Location muzzle = entity.getEyeLocation() = null
  → muzzle.getWorld() 抛 NPE
  → catch(Exception) 捕获，返回 false
  → shoot测试失败
```

**修复**（MobWeaponManager.java shootEffect() 中添加null检查）：
```java
private void shootEffect(LivingEntity entity, Location target) {
    Location muzzle = entity.getEyeLocation();
    if (muzzle == null || target == null) return;  // 新增
    if (muzzle.getWorld() == null || ...) return;
```

---

## 5. 修正后的推荐执行计划

| 阶段 | 内容 | 修正工期 | 关键变化 |
|------|------|---------|---------|
| P0 | 确认覆盖率+调门禁 → 修8个测试（2个根因） → GitHub仓库 → CI验证 | 1.5-2天 | 增加覆盖率确认步骤；修复方案完全不同 |
| P1 | sourceSet隔离（补齐配置） → MockBukkit启用 → PS1迁移（6个场景） → CI双测试 | 6-7天 | 砍WM迁移；合并P2路线A；补compileOnly |
| P2 | 覆盖率提升 + 测试文档 + PS1保留为手动回归 | 2周 | 砍Docker路线 |

**总工期**：3-4周（砍Docker） vs 方案声称5-6周

---

## 6. sourceSet隔离完整配置（质量门神修正版）

```groovy
sourceSets {
    testMockBukkit {
        java.srcDirs = ['src/test-mockbukkit/java']
        resources.srcDirs = ['src/test-mockbukkit/resources']
        compileClasspath += sourceSets.main.output
        runtimeClasspath += sourceSets.main.output
    }
}

configurations {
    testMockBukkitImplementation.extendsFrom testImplementation
    testMockBukkitCompileOnly.extendsFrom compileOnly
    testMockBukkitRuntimeOnly.extendsFrom testRuntimeOnly
}

task testMockBukkit(type: Test) {
    testClassesDirs = sourceSets.testMockBukkit.output.classesDirs
    classpath = sourceSets.testMockBukkit.runtimeClasspath
    useJUnitPlatform()
}

dependencies {
    testMockBukkitImplementation 'org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.28.4'
}

check.dependsOn testMockBukkit

// JaCoCo合并
task jacocoMerge(type: JacocoMerge) {
    executionData files(
        layout.buildDirectory.file('jacoco/test.exec'),
        layout.buildDirectory.file('jacoco/testMockBukkit.exec')
    )
}
jacocoTestReport {
    dependsOn jacocoMerge
    executionData jacocoMerge.destinationFile
}
```

---

## 7. 被遗漏的风险清单

| # | 风险 | 严重度 | 方案是否提及 | 修正建议 |
|---|------|--------|-------------|---------|
| 1 | JaCoCo 70%门禁阻断CI首次运行 | 🔴致命 | ❌ | P0先确认覆盖率，调低门禁 |
| 2 | 8个失败测试根因诊断完全错误 | 🔴致命 | ❌ | 按实测根因修复 |
| 3 | WM/MC Maven Central迁移不可行（fat≠thin jar） | 🔴致命 | ❌ | 放弃迁移 |
| 4 | sourceSet遗漏compileOnly依赖声明 | 🟠严重 | ❌ | 补充配置 |
| 5 | MobWeaponManagerIntegrationTest未继承IntegrationTestBase | 🟡中等 | ❌ | 继承或提取共享mock |
| 6 | IntegrationTestBase的new ItemStack()与MockBukkit不兼容 | 🟡中等 | ❌ | 改用mock(ItemStack.class) |
| 7 | Folia调度器API在MockBukkit中支持度未知 | 🟡中等 | ❌ | 验证 |
| 8 | EliteMobs永远是本地依赖，长期管理策略缺失 | 🟡中等 | ❌ | 接受现状 |
| 9 | 覆盖率目标矛盾（70%门禁 vs 30%→60%基线） | 🟡中等 | ❌ | 统一目标 |
| 10 | PS1是Windows脚本，CI环境需重写 | 🟢低 | ❌ | 暂不迁移 |

---

## 8. 行动清单

| # | 行动 | 负责方 | 紧急度 | 期望完成 |
|---|------|--------|--------|---------|
| 1 | 运行 `./gradlew jacocoTestReport` 确认当前覆盖率 | 开发 | P0 | 立即 |
| 2 | 根据实际覆盖率调整JaCoCo门禁（建议先设40%） | 开发 | P0 | 确认覆盖率后 |
| 3 | AIVisionManagerTest setUp() 加 `when(aiEntity.getEyeLocation()).thenReturn(mock(Location.class))` | 开发 | P0 | 立即 |
| 4 | MobWeaponManager.shootEffect() 加null检查 | 开发 | P0 | 立即 |
| 5 | 创建GitHub仓库 + 配置git remote + 首次推送 | 开发 | P0 | 测试修复后 |
| 6 | 验证CI首次运行（编译→测试→覆盖率→打包） | 开发 | P0 | 推送后 |
| 7 | 创建testMockBukkit sourceSet（含补齐compileOnly配置） | 开发 | P1 | P0完成后 |
| 8 | 启用MockBukkit + 编写MockBukkit测试基类 | 开发 | P1 | sourceSet完成后 |
| 9 | 迁移PS1的6个场景为MockBukkit集成测试 | 开发 | P1 | MockBukkit启用后 |
| 10 | CI增加testMockBukkit job + JaCoCo合并报告 | 开发 | P1 | 测试编写后 |
| 11 | 统一覆盖率目标 + 编写测试执行文档 | 开发 | P2 | P1完成后 |

---

## 待完善 / 已知局限

- 本报告基于代码静态分析 + 测试XML报告 + Maven metadata验证，未实际执行sourceSet隔离方案
- MockBukkit 4.28.4对Paper 1.21.4的API覆盖度需在P1阶段实测验证
- Folia调度器API在MockBukkit中的支持度需实测
- PS1场景迁移的具体工作量需在MockBukkit基类就绪后才能精确评估

---

## 成员产出索引

- gstack-product-reviewer（产品官）原始产出：3阶段评分（2/5、3/5、2/5）、10项遗漏风险清单、修正执行计划、ROI修正
- gstack-qa-lead（质量门神）原始产出：8个失败测试实际堆栈分析、Mockito 5.x null匹配机制确认、WM/MC jar大小对比(9倍差异)、sourceSet完整配置修正、Maven Central传递依赖分析

---

> 本报告由软件工坊 AI 协作生成，关键决策请由工程负责人复核。
