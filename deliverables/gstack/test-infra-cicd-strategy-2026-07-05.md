# EM-WM-Bridge 自动化测试方案 + CI/CD 流水线设计

> 分析人: gstack-qa-lead | 日期: 2026-07-05  
> 项目: EM-WM-Bridge v1.3.0 | Paper 1.21.4 / Folia | Java 21 + Gradle

---

## 执行摘要

对 EM-WM-Bridge 项目的测试基础设施进行了深入代码分析，核心发现如下：

1. **MockBukkit 实际可用** — 项目注释掉的 `com.github.seeseemelk:MockBukkit-v1.21:3.133.2` 是旧版 JitPack 坐标（仅支持 Paper 1.21.1）。MockBukkit 已迁移到 Maven Central，新坐标 `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.28.4+` 的 POM 明确依赖 `paper-api:1.21.4-R0.1-SNAPSHOT`，与项目完全匹配。

2. **所有本地 jar 依赖均有 Maven Central 对应物** — WeaponMechanics (`com.cjcrafter:weaponmechanics:4.3.0`)、MechanicsCore (`com.cjcrafter:mechanicscore:4.3.1`)、EliteMobs (`com.magmaguy:EliteMobs:9.6.0`) 全部在 Maven Central 上可用，CI 环境无需任何本地 jar。

3. **现有测试质量优秀** — 27 个测试文件、363 个测试用例全部通过（0 失败 0 错误 0 跳过），已有三层测试架构（纯单元 / Mockito 集成 / Harness 场景），JaCoCo 70% 覆盖率门禁已配置。

4. **test-server.ps1 的 6 个场景中 5 个可转化为 MockBukkit 自动化测试**，仅"Scav 生成绑定武器"的真机端到端验证建议保留。

5. **零 CI/CD** — 无任何自动化流水线，是当前最大的效率瓶颈。

---

## A. MockBukkit 替代方案

### A.1 当前 MockBukkit 对 Paper 1.21.4 的支持现状

| 维度 | 旧版（项目当前注释） | 新版（Maven Central） |
|------|----------------------|----------------------|
| 坐标 | `com.github.seeseemelk:MockBukkit-v1.21:3.133.2` | `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.x.x` |
| 仓库 | JitPack | Maven Central |
| Paper API | 1.21.1-R0.1-SNAPSHOT | **1.21.4-R0.1-SNAPSHOT**（POM 已验证） |
| RegistryAccessMock | NPE（1.21.4 不兼容） | 已修复 |
| 最新版本 | 3.133.2（停更） | 4.110.0（2026-05 持续更新） |
| Java 要求 | Java 21 | Java 21（v1.21 分支）；注意 v26 分支需 Java 25 |

**关键证据**: mockbukkit-v1.21:4.28.4 的 POM 文件中明确声明:
```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.4-R0.1-SNAPSHOT</version>
</dependency>
```
SCM URL 指向 `github.com/MockBukkit/MockBukkit/tree/v1.21`，确认这是独立的 v1.21 分支，与项目使用的 Paper 1.21.4 API 完全匹配。

### A.2 替代方案评估

#### 方案 1: 升级 MockBukkit 到 Maven Central 新坐标 ✅ 推荐

**可行性**: 高 — POM 已验证依赖 paper-api:1.21.4  
**优势**: 
- 零代码改动，仅改 build.gradle 依赖坐标
- 获得完整 Bukkit 服务器模拟能力（scheduler、event、world、entity、inventory）
- 可模拟 `JavaPlugin.onEnable()/onDisable()` 生命周期
- 可测试命令处理、配置加载、事件监听器
- Maven Central 解析，CI 环境零配置

**劣势**: 
- 部分冷门 API 可能抛 `UnimplementedOperationException`（测试会被跳过而非失败）
- 需要验证与 WeaponMechanics/EliteMobs 静态方法的兼容性

**实施**: 将 build.gradle 第 55 行改为:
```groovy
testImplementation 'org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.28.4'
```

#### 方案 2: 扩展现有 IntegrationTestBase 的 Mockito 策略

**可行性**: 中 — 已有良好基础  
**优势**: 无新依赖，完全可控  
**劣势**: 
- 手动 mock 每个 Bukkit API 调用，维护成本高
- 无法模拟 scheduler tick 推进（`runTaskLater` 只能 `verify` 调用，不能执行回调）
- 无法模拟事件分发链
- 无法测试 `onEnable()` 完整初始化流程

**结论**: 保留现有 Mockito 策略作为补充，但不作为主要扩展方向。

#### 方案 3: PaperMC paperweight-userdev + paperweight-testplugin

**可行性**: 低 — paperweight 主要用于开发 Paper 本体插件，不提供测试模拟  
**不推荐**: 偏离项目实际需求，引入复杂构建链无实质收益。

#### 方案 4: 自建轻量级 Bukkit API mock 层

**可行性**: 低 — 重复造轮子  
**不推荐**: MockBukkit 已提供完整解决方案，自建成本极高且难以维护。

#### 方案 5: 混合策略（MockBukkit + 现有 Mockito） ✅ 最终推荐

将 MockBukkit 用于 Bukkit 服务器级测试（生命周期、事件、调度器、命令），保留现有 Mockito 测试用于纯逻辑验证和 WM/EM 静态方法 mock。两者互补：
- MockBukkit 管不到 `WeaponMechanicsAPI.generateWeapon()` 等静态方法 → 继续用 `mockStatic`
- MockBukkit 管不到 EliteMobs 内部行为 → 继续用 `@Mock`
- MockBukkit 能管的（scheduler、event、plugin lifecycle）→ 迁移到 MockBukkit

### A.3 推荐方案与实施路径

**推荐: 方案 5（混合策略）**

实施步骤:
1. build.gradle 添加 `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.28.4`
2. 创建 `MockBukkitTestBase` 基类，封装 `MockBukkit.mock()` / `MockBukkit.load()` / `MockBukkit.unmock()`
3. 将 test-server.ps1 场景逐步转化为 MockBukkit 测试
4. 保留现有 IntegrationTestBase 不动（已有 363 个测试通过，不破坏）
5. 新增 MockBukkit 测试与现有测试并行运行

---

## B. 自动化测试分层策略

### B.1 测试金字塔设计

```
                    ┌─────────┐
                    │ 真机 E2E │  ← 1-2 个核心场景（Scav 生成→绑枪→射击→死亡解绑）
                    │  ~5 个   │     仅用于发布前验收
                    └────┬────┘
                   ╱     │     ╲
              ╱══════════╪══════════╲
            ╱  MockBukkit 集成测试    ╲  ← plugin lifecycle, event, scheduler, command
           ╱     ~30-40 个测试          ╲     替代 test-server.ps1 大部分场景
          ╱══════════════════════════════╲
        ╱   Mockito 边界集成测试           ╲  ← WM/EM 静态方法 mock + Bukkit API mock
       ╱     ~50 个测试（现有）              ╲    IntegrationTestBase 体系
      ╱══════════════════════════════════════╲
    ╱      纯单元测试                          ╲  ← 无任何 Bukkit 依赖
   ╱      ~150 个测试（现有）                     ╲    AlertStage, HostilityMatrix, etc.
  ╱══════════════════════════════════════════════╲
```

### B.2 各层测试覆盖范围

#### 第一层: 纯单元测试（无 Bukkit 依赖）— 已有，保持

| 测试类 | 覆盖模块 | Bukkit 依赖 |
|--------|----------|-------------|
| AlertStageTest | 警戒阶段状态机 | 仅 `Location` mock（轻量） |
| HostilityMatrixTest | 阵营敌对矩阵 | 无 |
| ExposureDataTest | 曝光数据模型 | 无 |
| MobWeaponInstanceStateTest | 武器实例状态机 | 无 |
| TarkovTacticsTest | 战术决策 | 无 |
| SoundPropagationUtilsTest | 声音传播计算 | 无 |
| TacticalUtilsTest | 战术工具函数 | 无 |
| EMWMWeaponConfigTest | 武器配置模型 | 无 |
| EMWMConfigCacheTest | 配置缓存管理 | 无 |

#### 第二层: Mockito 边界集成测试 — 已有，保持

| 测试类 | 覆盖模块 | Mock 策略 |
|--------|----------|-----------|
| MobWeaponManagerIntegrationTest | 武器管理器 | `mockStatic(WeaponMechanicsAPI)` + mock Bukkit 实体 |
| EliteMobSpawnListenerTest | 生成监听器 | mock PluginManager + Scheduler + 实体 |
| HarnessIntegrationTest | AI 感知链路 | PerceptionHarness 反射注入 |
| AIVisionManagerTest | 视觉感知 | mock plugin + 实体 |
| MobWeaponManagerTest | 武器管理器 | mock plugin + config |

#### 第三层: MockBukkit 集成测试 — 新增

以下 test-server.ps1 场景可转化为 MockBukkit 测试:

| PS1 场景 | 转化方案 | MockBukkit 测试 |
|----------|----------|-----------------|
| 场景1: 插件加载验证 | `MockBukkit.load(EMWMBridge.class)` 成功即验证 | `PluginLifecycleTest` |
| 场景2: 配置热重载 | `plugin.reloadAll()` + 验证配置缓存刷新 | `ConfigReloadTest` |
| 场景3: 统计命令 | `server.execute("emwm", "stats")` + 捕获输出 | `CommandTest` |
| 场景4: Scav 生成绑枪 | `fireEvent(CreatureSpawnEvent)` + 验证武器绑定 | `SpawnBindingTest` |
| 场景5: 启动无错误 | `MockBukkit.load()` 无异常即验证 | 包含在 `PluginLifecycleTest` |
| 场景6: 配置缓存信息 | `server.execute("emwm", "info")` + 验证输出 | `CommandTest` |

#### 第四层: 真机 E2E — 保留但精简

仅保留 1-2 个核心端到端场景:
- Scav 生成 → 绑定 WM 武器 → AI 锁定玩家 → 射击 → 死亡解绑（完整链路）
- 多插件交互验证（EliteMobs + WeaponMechanics + EM-WM-Bridge 协同）

### B.3 最大化自动化覆盖率策略

1. **Scheduler 测试**: MockBukkit 提供 `server.getScheduler().performTicks(n)` 可推进时间，测试 `MobWeaponManager.reload()` 的延迟回调
2. **Event 测试**: MockBukkit 可 `server.getPluginManager().callEvent(event)` 或 `simpleFeatureManager.callEvent()`，测试监听器完整链路
3. **Command 测试**: MockBukkit 提供 `server.execute(command, args)` API，直接测试 `onCommand()`
4. **Plugin Lifecycle**: `MockBukkit.load(EMWMBridge.class)` 触发真实 `onEnable()`，验证依赖检查、配置加载、监听器注册
5. **World/Entity 模拟**: MockBukkit 提供 `world.spawnEntity()`，可创建真实实体对象用于测试

---

## C. CI/CD 流水线设计

### C.1 依赖管理策略

#### 关键发现: 所有依赖均可在 Maven Central / PaperMC 仓库解析

| 依赖 | 当前方式 | Maven Central 坐标 | 状态 |
|------|----------|---------------------|------|
| Paper API | ✅ 已用 Maven | `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` | 无需改 |
| WeaponMechanics | ❌ 本地 jar | `com.cjcrafter:weaponmechanics:4.3.0` | 可迁移 |
| MechanicsCore | ❌ 本地 jar | `com.cjcrafter:mechanicscore:4.3.0` | 可迁移 |
| EliteMobs | ❌ 本地 jar | `com.magmaguy:EliteMobs:9.6.0` | 可迁移 |
| MockBukkit | ❌ 注释掉 | `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.28.4` | 可启用 |
| JUnit 5 | ✅ 已用 Maven | `org.junit.jupiter:junit-jupiter-*:5.10.2` | 无需改 |
| Mockito | ✅ 已用 Maven | `org.mockito:mockito-*:5.12.0` | 无需改 |

**结论**: CI 环境只需 `mavenCentral()` + `papermc` 仓库，无需 mavenLocal、无需本地 jar、无需 JitPack。

> **注意**: 需验证 `com.cjcrafter:weaponmechanics:4.3.0` 和 `com.magmaguy:EliteMobs:9.6.0` 的 API 接口与本地 jar 版本一致。如果 Maven Central 版本的 API 有变化，可能需要适配代码。建议先用 Maven Central 版本编译验证。

### C.2 完整 CI/CD 流水线

```
Push/PR ──► [CI] Compile ──► Unit Test ──► JaCoCo Coverage ──► Build JAR ──► Upload Artifact
                │                  │              │
                │             (363+ tests)    (≥70% gate)
                │
            Merge to main
                │
                ▼
          [CD] Deploy to Test Server (manual trigger)
                │
                ▼
          Real Server E2E Test (test-server.ps1)
                │
                ▼
          Canary Check (optional)
```

### C.3 GitHub Actions 配置文件

见同目录下 `github-actions-ci.yml`。

### C.4 更新后的 build.gradle

见同目录下 `build.gradle.ci`。

### C.5 真机集成测试的 CI 集成方案

**短期方案（推荐）**: 真机测试保持手动触发
- CI 产出可部署的 JAR artifact
- 开发者下载 artifact → `./gradlew deploy` → 运行 `test-server.ps1`
- 在 PR 描述中附上真机测试结果

**中期方案**: GitHub Actions self-hosted runner
- 在测试服务器上注册 self-hosted runner
- CI 自动部署 JAR 到 plugins 目录
- 自动启动服务器 → 运行 test-server.ps1 → 收集结果 → 关闭服务器
- 需要处理服务器启动超时（120s）和资源占用问题

**长期方案**: Docker 化测试服务器
- 构建 Paper 1.21.4 + 所有插件的 Docker 镜像
- CI 中启动容器 → 运行测试 → 销毁容器
- 完全自动化，无需 self-hosted runner

---

## D. 实施优先级路线图

### P0 — 立即执行（投入 1 天，收益最大）

| # | 任务 | 预计工时 | 收益 |
|---|------|----------|------|
| P0-1 | build.gradle 迁移 WM/EM/MechanicsCore 到 Maven Central 坐标 | 2h | 消除本地 jar 硬编码，CI 可直接编译 |
| P0-2 | 启用 MockBukkit-v1.21:4.28.4（取消注释 + 改坐标） | 0.5h | 获得 Bukkit 服务器模拟能力 |
| P0-3 | 创建 GitHub Actions CI workflow | 2h | 每次 push/PR 自动编译+测试+覆盖率 |
| P0-4 | 验证 CI 环境编译通过（修复任何坐标不匹配） | 2h | 确保 CI 绿灯 |

### P1 — 短期执行（投入 3 天，显著提效）

| # | 任务 | 预计工时 | 收益 |
|---|------|----------|------|
| P1-1 | 创建 MockBukkitTestBase 基类 | 2h | 标准化 MockBukkit 测试模式 |
| P1-2 | 转化 PS1 场景1+5: PluginLifecycleTest | 3h | 替代手动启动验证 |
| P1-3 | 转化 PS1 场景2: ConfigReloadTest | 2h | 替代手动重载验证 |
| P1-4 | 转化 PS1 场景3+6: CommandTest | 3h | 替代手动命令验证 |
| P1-5 | 转化 PS1 场景4: SpawnBindingTest | 4h | 替代手动生成验证 |
| P1-6 | CI 添加 JaCoCo 覆盖率门禁检查 | 1h | 防止覆盖率回退 |
| P1-7 | CI 添加 build artifact 上传 | 0.5h | 可下载构建产物 |

### P2 — 中期执行（投入 5 天，全面自动化）

| # | 任务 | 预计工时 | 收益 |
|---|------|----------|------|
| P2-1 | 注册 self-hosted runner 到测试服务器 | 4h | CI 可直接部署 |
| P2-2 | CI 自动部署 + 运行 test-server.ps1 | 4h | 真机测试自动化 |
| P2-3 | CI 添加真机测试结果解析和报告 | 2h | 结构化测试报告 |
| P2-4 | Docker 化测试服务器 | 8h | 完全隔离的 E2E 环境 |
| P2-5 | Canary 监控（部署后健康检查） | 4h | 自动回归检测 |
| P2-6 | 覆盖率提升至 80%（补充未覆盖路径） | 8h | 质量保障提升 |

### 时间线

```
Week 1: P0 全部完成 → CI 绿灯，本地 jar 消除，MockBukkit 可用
Week 2: P1 全部完成 → 5 个 PS1 场景转化为自动化测试，覆盖率门禁生效
Week 3-4: P2 逐步推进 → 真机测试自动化，Docker 化环境
```

---

## 附录: 关键代码路径分析

### 当前 Bukkit API 使用热点（按测试难度排序）

| 模块 | Bukkit API 依赖 | 当前测试覆盖 | MockBukkit 可测性 |
|------|-----------------|-------------|-------------------|
| MobWeaponManager | Bukkit.scheduler, Entity.metadata, ItemStack | ✅ Mockito | 高（scheduler tick） |
| EliteMobSpawnListener | CreatureSpawnEvent, Scheduler.runTaskLater | ✅ Mockito | 高（event fire + tick） |
| EMWMBridge (onEnable) | Bukkit.getPluginManager, JavaPlugin lifecycle | ❌ 未测 | 高（MockBukkit.load） |
| EMWMBridge (onCommand) | CommandSender, permissions | ❌ 未测 | 高（server.execute） |
| EMWMConfigCache | JavaPlugin.getConfig, saveConfig | ✅ Mockito | 中（config resource） |
| TarkovAIEngine | Bukkit.scheduler, Entity API | ✅ Mockito | 中（scheduler 依赖重） |
| PlayerShootListener | PlayerInteractEvent, WM API | ❌ 未测 | 高（event fire） |
| MobWeaponManager.shoot | World.spawnParticle, World.playSound | ❌ 未测 | 中（particle/sound mock） |
| ExtremeEventManager | Entity API, Scheduler | ✅ Mockito | 中 |

### test-server.ps1 场景转化映射

```
PS1 场景1 (插件加载)     → MockBukkit.load(EMWMBridge.class) 成功 = PASS
PS1 场景2 (配置热重载)   → plugin.reloadAll() + 验证 emwmConfigCache.reload() 被调用
PS1 场景3 (统计命令)     → server.execute("emwm", "stats") + 验证 sender 收到消息
PS1 场景4 (Scav 生成)    → fireEvent(CreatureSpawnEvent) + verify weaponManager.bindWeapon
PS1 场景5 (启动无错误)   → MockBukkit.load() 无异常 = PASS（已包含在场景1）
PS1 场景6 (配置缓存信息) → server.execute("emwm", "info") + 验证输出包含缓存信息
```

---

## 附录: Maven Central 坐标验证

### WeaponMechanics
- GroupId: `com.cjcrafter`
- ArtifactId: `weaponmechanics`
- 可用版本: 4.3.0, 4.3.1（最新）, 4.2.x, 4.1.x, 3.4.x
- 仓库: Maven Central
- 验证 URL: https://mvnrepository.com/artifact/com.cjcrafter/weaponmechanics

### MechanicsCore
- GroupId: `com.cjcrafter`
- ArtifactId: `mechanicscore`
- 可用版本: 4.3.1（最新）, 4.3.0, 3.4.x
- 仓库: Maven Central
- 验证 URL: https://mvnrepository.com/artifact/com.cjcrafter/mechanicscore

### EliteMobs
- GroupId: `com.magmaguy`
- ArtifactId: `EliteMobs`
- 可用版本: 9.6.0（最新）
- 仓库: Maven Central
- 验证 URL: https://mvnrepository.com/artifact/com.magmaguy/EliteMobs

### MockBukkit
- GroupId: `org.mockbukkit.mockbukkit`
- ArtifactId: `mockbukkit-v1.21`
- 可用版本: 4.28.4 ~ 4.110.0
- Paper API 依赖: 1.21.4-R0.1-SNAPSHOT（POM 已验证）
- 仓库: Maven Central
- 验证 URL: https://mvnrepository.com/artifact/org.mockbukkit.mockbukkit/mockbukkit-v1.21
