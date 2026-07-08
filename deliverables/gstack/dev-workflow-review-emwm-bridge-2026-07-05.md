# EM-WM-Bridge 开发流程完善与验收保障报告

**日期**：2026-07-05
**场景**：全流程交付评估（产品评审 + 代码健康检查 + QA测试审计）
**参与成员**：产品官 × 2 + 排障手 + 质量门神

---

## 📌 TL;DR（执行摘要）

- **整体结论**：🔴 **当前不可用于黑盒生产环境**。项目架构设计优秀，但工程管理存在系统性缺陷——大量功能"写了但没接入"，测试覆盖严重不足，核心 AI 主循环零覆盖，5 个致命问题会导致生产环境崩溃或静默失败。
- **问题统计**：35 个代码隐患（5 致命 / 14 严重 / 10 警告 / 6 建议），14 个功能声称可用但实际是死代码或有缺陷，61% 的源文件无测试覆盖，AI 引擎核心方法零测试。
- **最短路径**：P0 修复约 19 小时（3 天），从"不可信"到"黑盒可验收"约 6 周。
- **下一步**：先修 11 个 P0 致命问题（统一射击源 + 接入极限事件 + 修复 Folia 线程 + 持久化），再补测试覆盖，最后建立 CI/CD 和验收流程。

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🔴 **No-Go** — 不可用于黑盒生产环境 |
| 严重度分布 | 🔴 5 / 🟠 14 / 🟡 10 / 🟢 6（代码隐患）+ 14 个功能虚假标记 |
| 功能真实可用率 | 38/52（73%），黑盒可用率约 50% |
| 测试覆盖率 | 有测试 19/49（38.8%），AI 核心零覆盖 |
| 关键行动项 | 11 条 P0（约 19h）+ 7 条 P1 + 流程改进 |
| 建议版本降级 | v1.3.0 → **v0.3.0-alpha** |
| 预估达到"黑盒可验收" | 约 6 周（4 个阶段） |

---

## 1. 各成员核心结论

### 🔍 产品官（产品评审）× 2

**核心判断**：FeatureManager 声称 52 个功能 98% 可用，实际只有 38/52（73%）可信，8 个是完全死代码。用户说"不可信"完全准确。项目处于"铺得太广但不可信"状态，版本号 v1.3.0 严重虚高，应降为 0.3.0-alpha。

**关键建议**：
- 立即修正 FeatureManager 标记，将未集成的功能从 ✅ 改为 ⬜
- 执行 MoSCoW 优先级：11 个 Must Have（P0）→ 7 个 Should Have（P1）→ 7 个 Could Have（P2）
- 建立"功能完成定义"（DoD）：代码完成 + 集成完成 + 单元测试 + 集成测试 + 黑盒可观测 + 文档更新 + 性能验证 + 边界处理 + 验收标准达成
- 分 4 阶段交付：Phase 0 紧急修复(3天) → Phase 1 功能对齐(1周) → Phase 2 黑盒可用(2周) → Phase 3 正式发布(3周)

### 🔧 排障手（代码健康检查）

**核心判断**：逐类审读 42 个 Java 源文件，发现 35 个问题。最致命的是双射击系统冲突（两套射击逻辑同时运行）、ExtremeEventManager 全模块死代码、Folia 线程安全（实体操作在错误线程执行）、CombatListener 射击任务永不取消（内存泄漏）。这些问题在 Paper 开发环境不会暴露，但在 Folia 生产环境会直接崩溃。

**关键建议**：
- F1+F2：移除 EliteMobCombatListener.scheduleShootingTask()，射击完全由 AI 引擎统一管理
- F3+F4：在 TarkovAIEngine.tickEntity() 中调用 extremeEventManager.checkExtremeEvents()，修复 updateState() 的伤害追踪逻辑
- F5：Folia 下使用 entity.getScheduler() 或 RegionScheduler 而非 GlobalScheduler
- S11+S12：热重载和重启后扫描已存在实体重新注册 AI，使用 PersistentDataContainer 持久化

### ✅ 质量门神（QA测试审计）

**核心判断**：398 个测试全部通过，但通过率高不代表质量好——61.2% 的源文件无测试覆盖，AI 引擎核心方法 tickEntity()/executeTacticalAction() 零覆盖，3 个测试文件是"伪测试"（CoverMovementTest 只测了配置重载和枚举值，14 个移动方法全部未测试）。JaCoCo 70% 门禁配置了但从未实际执行过。

**关键建议**：
- P0：补全 TarkovAIEngine.tickEntity()、VisualPerception.calculate()、AuditoryPerception.processSound() 的单元测试
- 建立 20 个黑盒验收测试用例（5 基础 + 8 AI 行为 + 3 多实体 + 4 边界条件）
- 修复 JaCoCo 编译问题，CI 中强制执行覆盖率门禁
- 建立"单元→集成→E2E"三层测试架构

---

## 2. 综合审查发现（去重合并后按严重度排序）

### 🔴 致命问题（P0，必须修复才能上线）

| # | 问题 | 位置 | 影响 | 修复方向 | 预估 |
|---|------|------|------|---------|------|
| P0-1 | **双射击系统冲突** | EliteMobCombatListener.scheduleShootingTask():58 + TarkovAIEngine.executeTacticalAction() | 两套射击逻辑同时运行，射速翻倍，弹药消耗翻倍 | 移除 CombatListener 的独立射击逻辑，统一由 AI 引擎控制 | 2h |
| P0-2 | **射击任务永不取消** | EliteMobCombatListener.scheduleShootingTask():58 | runTaskTimer 返回值未存储，每次目标切换创建新定时器，永不销毁 → 内存泄漏 | 存储 BukkitTask 引用，在目标丢失/实体死亡时 cancel() | 1h |
| P0-3 | **ExtremeEventManager 全模块死代码** | TarkovAIEngine.tickEntity() 缺少调用 | 恐慌/肾上腺素/幸运一击/战术失误 4 个功能完全失效，配置项被加载但永远不触发 | 在 tickEntity() 中调用 checkExtremeEvents()，将 getSpeedModifier/getFireRateModifier 集成到移动和射击 | 4h |
| P0-4 | **updateState() 语义错误** | ExtremeEventManager.updateState():95 | getLastDamage() 返回伤害数值，被当时间戳比较，连续伤害计数器永远不正确递增 | 改用 EntityDamageEvent 监听记录受伤时间戳 | 2h |
| P0-5 | **Folia 线程安全** | TarkovAIEngine.startScheduler():178 + MobWeaponManager.reload():499 | AI 主循环在全局线程执行实体操作，Folia 下抛 ThreadMismatchException 或静默失败 | 使用 entity.getScheduler() 或 RegionScheduler | 4h |
| P0-6 | **热重载后幽灵怪** | TarkovAIManager.restart() | /emwm reload 后已生成怪物有武器无 AI，变成木桩 | restart() 时遍历当前 activeMobs 重新注册 | 2h |
| P0-7 | **服务器重启后 AI 丢失** | 全项目无持久化 | 重启后精英怪持武器但无 AI 行为 | 使用 PersistentDataContainer 持久化 tier 和 AI 状态，onEnable 时扫描恢复 | 4h |
| P0-8 | **faceTarget() 不生效** | CoverMovement.faceTarget():40-51 | 修改了 Location 克隆但从未应用回实体，AI 站立瞄准时不面向目标 | 使用 entity.setRotation(yaw, pitch) | 0.5h |
| P0-9 | **跨世界 distance() 异常** | VisualPerception:160 等 8 处 | 玩家跨世界传送时 IllegalArgumentException | 统一使用 safeDistance() | 1h |
| P0-10 | **getMaxHealth() 除零** | TarkovAIEngine.tickEntity():211 | maxHealth=0 → NaN 传播 → 所有基于 hpRatio 的判断失效 | 添加 maxHealth > 0 防御 | 0.5h |
| P0-11 | **EMWMReloadListener 未注册** | EMWMBridge.registerListeners() | EliteMobs 热重载后 EMWM 配置缓存不刷新 | 在 registerListeners() 中注册 | 0.5h |

### 🟠 严重问题（P1，严重影响体验）

| # | 问题 | 位置 | 影响 | 修复方向 |
|---|------|------|------|---------|
| P1-1 | 武器故障(shouldMalfunction)未被调用 | MobWeaponManager.shoot() | 耐久低时武器不会卡壳，配置存在但不生效 | 在 shoot() 开头调用 shouldMalfunction() |
| P1-2 | 精度惩罚未应用 | MobWeaponManager.shoot() | getAccuracyModifier() 从未被调用，耐久低不影响精度 | 在 shoot() 中应用 |
| P1-3 | 弹药掉落系统缺失 | 全项目 | config.yml 有 loot.drop-ammo 配置但无实现 | 实现 EntityDeathEvent 掉落或移除配置 |
| P1-4 | 烟雾弹不遮挡视线 | ThrowableManager.deploySmoke() + VisualPerception | 烟雾弹纯装饰，对 AI 视觉无影响 | 在 calculate() 中检测烟雾粒子区域 |
| P1-5 | Sniper/Cultist tier 无武器池 | MobWeaponManager.getRandomWeaponForTier() | config 有 tier 设置但无武器池，fallback 到 AK_47 | 添加 sniper-pool/cultist-pool |
| P1-6 | 特殊能力字段未消费 | TarkovAIEngine 不读 metadata | callReinforcements/squadLeader/preferLongRange/preferRush 配置解析了但 AI 不用 | 在 AI 引擎中读取并影响行为，或移除字段 |
| P1-7 | HostilityMatrix 随机敌对不稳定 | HostilityMatrix.getRelation():53-55 | Math.random() 每次调用结果不同，同 tick 内反复切换敌对/中立 | 缓存随机结果到实体 metadata，每 N tick 重新判定 |
| P1-8 | FeatureManager 虚假标记 | FeatureManager | 8 个 ❌ 功能标 ✅，误导用户和开发者 | 修正标记与实际对齐 |
| P1-9 | EliteMobSpawnListener 日志爆炸 | EliteMobSpawnListener.onCreatureSpawn():41 | 每次 CreatureSpawnEvent 都打 INFO 日志，非精英怪也打 | 改为 plugin.debug() |
| P1-10 | 非线程安全 HashMap（多处） | SquadManager/TarkovTactics/ThrowableManager 等 7 处 | Folia 多线程下 HashMap 损坏 | 替换为 ConcurrentHashMap |
| P1-11 | AlertStage.GLOBAL_HATRED 静态 Map 泄漏 | AlertStage:22 | 仇恨记录在实体区块卸载/非正常死亡时不清除，跨 reload 不清 | 使用弱引用或定期清理 |
| P1-12 | EM 事件驱动模式空实现 | settings.use-elitemobs-events=true 时 | AI 完全停止运行，无任何 EliteMobs 事件驱动逻辑 | 实现或移除配置 |
| P1-13 | registerEMEvents() 从未调用 | EliteMobCombatListener.registerEMEvents():111 | EliteMobs 伤害事件监听是死代码 | 在 onEnable 中调用或删除 |
| P1-14 | shoot() 异常吞噬不记录堆栈 | MobWeaponManager.shoot():471 | catch(Exception) 只记录 getMessage()，生产环境无法定位 | 记录完整堆栈 |

### 🟡 警告级问题（P2，后续迭代）

| # | 问题 | 修复方向 |
|---|------|---------|
| P2-1 | VisualPerception 射线检测性能（20 AI × 10 玩家 = 10万次方块查询/tick） | 降频/缓存/空间分区 |
| P2-2 | 覆盖率门禁未绑定到 check/build | build.dependsOn(jacocoTestCoverageVerification) |
| P2-3 | compileOnly 硬编码绝对路径 | 改用 libs/ 相对路径 |
| P2-4 | 无 CI/CD | GitHub Actions: push → build + test |
| P2-5 | 无 CHANGELOG / Git Tag | 建立版本管理策略 |
| P2-6 | ConfigManager 版本比较用字符串 equals | 改用语义版本比较 |
| P2-7 | SoundEventManager.broadcastSound() 全世界扫描 | 限制扫描范围 |
| P2-8 | DebugManager 500ms 防刷屏可能隐藏关键信息 | 分级防刷策略 |
| P2-9 | ConfigManager.copyMissingKeys() 列表值处理 | 修复列表类型复制逻辑 |
| P2-10 | ConfigManager 验证不完整 | 增加子配置值范围验证 |

---

## 3. 功能完整度盘点（文档 vs 代码核对结果）

| 状态 | 数量 | 占比 | 说明 |
|------|------|------|------|
| ✅ 真正可用 | 38 | 73% | 核心链路可工作 |
| ⚠️ 部分可用/有缺陷 | 6 | 12% | 射击冲突/faceTarget失效/随机敌对不稳定等 |
| ❌ 死代码/未实现 | 8 | 15% | 极限事件4项 + 武器故障 + 弹药掉落 + 烟雾遮蔽 + EM事件驱动 |
| **FeatureManager 声称** | **52/52 ✅** | **100%** | **严重失实** |

### 死代码清单（声称 ✅ 但实际完全不可用）

| 功能 | 原因 |
|------|------|
| 恐慌模式 | checkExtremeEvents() 从未被调用 |
| 肾上腺素 | 同上 |
| 幸运一击 | getLuckShotBonus() 从未被调用 |
| 战术失误 | checkTacticalMistake() 从未被调用 |
| 武器故障 | shouldMalfunction() 从未被 shoot() 调用 |
| 弹药掉落 | config 有配置但无代码实现 |
| 烟雾弹视线遮挡 | deploySmoke() 只生成粒子，VisualPerception 不检测 |
| EM 事件驱动模式 | 配置存在但 true 时 AI 完全停止 |

---

## 4. 测试覆盖率审计

| 指标 | 数值 | 评价 |
|------|------|------|
| 总测试用例 | 398 | 全部通过 |
| 有测试的源文件 | 19/49（38.8%） | 🔴 严重不足 |
| 无测试的源文件 | 30/49（61.2%） | 🔴 |
| AI 引擎核心方法覆盖 | 0% | 🔴 tickEntity/executeTacticalAction 零覆盖 |
| 感知系统覆盖 | 0% | 🔴 VisualPerception/AuditoryPerception 零覆盖 |
| 伪测试文件 | 3 个 | CoverMovementTest/TacticalUtilsTest/TarkovAIEngineTest |
| JaCoCo 门禁 | 配置≥70% | ⚠️ 从未实际执行过 |

### 测试盲区 Top 5

| 源文件 | 行数 | 风险 |
|--------|------|------|
| TarkovAIEngine | 661 | 🔴 AI 主循环零覆盖，最复杂方法未测试 |
| VisualPerception | 367 | 🔴 视觉感知引擎零覆盖 |
| AuditoryPerception | 254 | 🔴 听觉感知引擎零覆盖 |
| EMWMBridge | 414 | 🔴 主插件类零覆盖 |
| CoverMovement | 329 | 🔴 14 个移动方法全部未测试 |

---

## 5. 分阶段交付计划

### Phase 0：紧急修复（"不可信" → "不崩溃"）— 3 天，约 19h

| 任务 | 预估 | 验收标准 |
|------|------|---------|
| P0-1 消除双射击冲突 | 2h | 怪物射速正常，无重复射击 |
| P0-2 取消射击任务泄漏 | 1h | 长时间运行无内存增长 |
| P0-3 接入 ExtremeEventManager | 4h | /emwm stats 有极限事件计数 |
| P0-4 修复 updateState 语义 | 2h | 连续伤害计数器正确递增 |
| P0-5 修复 Folia 线程安全 | 4h | Folia 服务器无 ThreadMismatchException |
| P0-6 修复热重载幽灵怪 | 2h | /emwm reload 后怪物仍战斗 |
| P0-7 修复重启 AI 丢失 | 4h | 重启后精英怪恢复 AI |
| P0-8 修复 faceTarget | 0.5h | AI 站立瞄准时面向目标 |
| P0-9 修复跨世界 distance | 1h | 跨世界传送无异常 |
| P0-10 修复除零 | 0.5h | maxHealth=0 不产生 NaN |
| P0-11 注册 ReloadListener | 0.5h | EM reload 后缓存刷新 |

**Phase 0 验收**：20 AI + 10 玩家同时战斗 10 分钟，TPS > 18，无 Exception

### Phase 1：功能对齐（"不崩溃" → "功能与声称一致"）— 1 周

- 修正 FeatureManager 标记
- 更新 CODE_WIKI 文档
- 实现 P1-1 到 P1-6（武器故障/精度惩罚/弹药掉落/烟雾遮蔽/Sniper tier/特殊能力）
- 修复 P1-7 到 P1-14

### Phase 2：黑盒可用（"功能一致" → "黑盒可运维"）— 2 周

- 补全 AI 引擎/感知系统/移动系统单元测试
- 执行 20 个黑盒验收测试用例
- 修复 JaCoCo 门禁并绑定 CI
- 添加 /emwm diagnose 命令
- VisualPerception 性能优化
- 版本降为 0.9.0-beta

### Phase 3：正式发布（"黑盒可用" → "可上线"）— 3 周

- CI/CD 自动化
- 性能压测报告（50 AI + 30 玩家）
- 完整 DoD + 验收清单
- 版本升至 1.0.0

---

## ✅ 行动清单

| # | 行动 | 负责方 | 紧急度 | 期望完成 |
|---|------|--------|--------|---------|
| 1 | 移除 EliteMobCombatListener.scheduleShootingTask()，统一由 TarkovAIEngine 控制射击 | 开发 | P0 | 3天内 |
| 2 | 在 TarkovAIEngine.tickEntity() 中接入 ExtremeEventManager.checkExtremeEvents()，修复 updateState() 语义 | 开发 | P0 | 3天内 |
| 3 | 修复 Folia 线程安全：AI 主循环改用 entity.getScheduler()，MobWeaponManager.reload() 改用 Folia-aware 调度 | 开发 | P0 | 3天内 |
| 4 | 实现 AI 状态持久化：PersistentDataContainer 存储 tier，onEnable 扫描恢复已存在实体 | 开发 | P0 | 3天内 |
| 5 | 修复热重载幽灵怪：restart() 时遍历 activeMobs 重新注册 | 开发 | P0 | 3天内 |
| 6 | 修正 FeatureManager 标记：将 8 个死代码功能从 ✅ 改为 ⬜，更新 CODE_WIKI | 开发 | P1 | 1周内 |
| 7 | 补全 TarkovAIEngine.tickEntity() / VisualPerception.calculate() / AuditoryPerception.processSound() 单元测试 | QA | P1 | 2周内 |
| 8 | 修复 JaCoCo 编译问题，绑定 jacocoTestCoverageVerification 到 check 任务 | 工程 | P1 | 2周内 |
| 9 | 执行 20 个黑盒验收测试用例（TC-001 ~ TC-020） | QA | P2 | 3周内 |
| 10 | 版本降级 v1.3.0 → 0.3.0-alpha，编写 CHANGELOG，建立 Git Tag 策略 | 工程 | P2 | 3周内 |
| 11 | 添加 GitHub Actions CI：push → compile + test + coverage-verify | 工程 | P2 | 3周内 |
| 12 | build.gradle compileOnly 路径从绝对路径改为 libs/ 相对路径 | 工程 | P2 | 3周内 |

---

## ⚠️ 待完善 / 已知局限

- **MockBukkit 不可用**：Paper 1.21.4 兼容版本未发布，无法进行 Bukkit API 级别的自动化集成测试，E2E 测试暂需手动执行
- **Folia 测试环境缺失**：当前只有 Paper 测试服务器，P0-5 修复后需要 Folia 环境验证
- **性能基准未建立**：无 TPS 监控基线，VisualPerception 性能优化的效果难以量化
- **单人开发**：无代码审查流程，建议至少引入 1 人 review 机制
- **Git 历史极少**（仅 3 次提交）：无法追溯问题引入时间，后续应建立规范提交习惯

---

## 📚 成员产出索引

- **gstack-investigator（排障手）**：逐类审读 42 个 Java 源文件，输出 35 个问题（5 致命 / 14 严重 / 10 警告 / 6 建议），含每项的文件路径+行号+根因分析+黑盒表现
- **gstack-product-reviewer（产品官 #1）**：功能完整度盘点（52 项逐一核对）+ 开发流程成熟度评估（2/10）+ MoSCoW 优先级 + 4 阶段交付计划 + DoD 标准 + 黑盒可用性评估（4/10）
- **gstack-product-reviewer-2（产品官 #2）**：功能盘点（73% 可用 / 15% 死代码）+ 流程断点识别 + P0/P1/P2 优先级 + 发布前检查清单 + 验收标准模板 + 版本降级建议
- **gstack-qa-lead（质量门神）**：测试覆盖率审计（19/49 有测试，AI 核心零覆盖）+ 伪测试分析 + 20 个黑盒验收用例 + 三层测试架构方案 + CI 自动化方案 + 灰度发布计划

---

> 本报告由软件工坊 AI 协作生成（4 位成员并行分析），关键决策请由工程负责人复核。
