# GreyZone 需求 × EM-WM-Bridge 可行性评审报告

**日期**：2026-07-09
**场景**：产品评审 + 代码核查 + 安全评审 + QA 验证策略 + 配置 Schema 设计（多成员协作）
**参与成员**：产品官（gstack-product-reviewer）· 排障手（gstack-investigator）· 质量门神（gstack-qa-lead）· 设计师（gstack-designer）· 安全卫士（gstack-security-officer）
**主理人**：沽思航（gstack-lead）

---

## 📌 TL;DR（执行摘要）

- **整体结论**：🟢 **Go** —— 8 项需求全部可在 EM-WM-Bridge 上实现，无技术不可行项。
- **重大修正**：规格书《EMWM-Bridge_底层支撑需求规格.md》对现状有 **3 处重大认知偏差**，导致工作量被高估、风险被错配：
  1. **需求5（AI 弹药隔离）已不是缺口** —— 代码已用自有 `emwm_ammo` 计数器 + WM `shoot()` 无弹药参数实现隔离。应移出 P0 风险清单，改为健壮性校验。
  2. **“当前无 faction 概念”不成立** —— `FactionManager`/`HostilityMatrix`/`TarkovFaction` 已存在，仅未接入 AI 目标选择。需求1 是「接线扩展」而非「从零搭建」。
  3. **squad 系统、retreat 字段已存在** —— 需求2/4 是「扩展/改造」而非从零。
- **真正从零要写的**：需求3（GUARD 守卫行为）、需求6（死亡掉货币弹绑定）、需求7（护甲集成 P1）、需求8（Boss 协同 P2）。其余为有框架基础的扩展。
- **实际 P0 必交付项**：需求1（接线）+ 需求6（掉落）+ 需求5（健壮性校验，非实现）+ 需求2/3/4（扩展）。规格书「六项全从零」叙事应修正为「一项接线 + 一项绑定 + 一项健壮性 + 三项扩展」。
- **阻塞项数量**：3（需求1 目标选择落点未决 / 需求6 掉落结算权未约定 / WM 是否扣 NBT 弹匣需实机确认）。
- **下一步**：先修订规格书认知偏差 → 排期实现需求1+6+5校验（战争+经济地基）→ 并行 2/3/4 → 7 占位 → 8 末段。

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🟢 **Go**（全部可实现；工作量被规格书高估） |
| 严重度分布 | 🔴 3 / 🟠 6 / 🟡 6 / 🟢 1 |
| 关键行动项 | 8 条（见行动清单） |
| 建议负责人 | 插件团队（实现）+ 设计侧（schema 消歧 + 数值校准）+ 主理人（修订规格书） |
| 真实新增工作量评估 | 比规格书预估 **低约 40–50%**（需求5/1/2/4 部分已具备） |

---

## 1. 各成员核心结论

### 🔍 产品官（产品评审）
- 核心判断：8 项技术上均可实现，**本里程碑应条件 Go** —— P0 六项须同里程碑，但应重新定义「P0 六项」内容（去掉需求5 的实现负担）。
- 关键建议：实现顺序 **1+6+5(校验) → 2/3/4 → 7 占位 → 8**；需求3 首版可用占位半径、需求2 首版先出 4 人小队、需求7 首版只穿戴不掉（均不阻断赛季一）；关系矩阵需运行时写回接口（PVE→PVP 据点易主）。

### 🔧 排障手（代码核查，已主理人核实）
- 核心判断：规格书多处「缺口」判断与 v1.3.0 代码事实不符 —— faction 框架已存在（仅 Tarkov 6 阵营、未接 AI 目标）、AI 弹药已隔离（`MobWeaponManager.shoot` L489 调 `WeaponMechanicsAPI.shoot(entity,title,loc)` 无弹药参，L493 用自有 `currentAmmo`）、`retreatHpThreshold` 已逐实体（EMWMWeaponConfig L51）、SquadManager 已存在（max-size=5，L183）。
- 关键建议：需求1 仅需在 `TarkovAIEngine.tickEntity`（L259 现仅 `getPlayers()`）扩展候选集为 AI 实体并调 `FactionManager.getRelation`；需求5 改做「防 WM 扣 NBT 弹匣」健壮性校验；需求6 为真实缺口（无 `lootAmmoType`、掉落钩子未约定）。

### ✅ 质量门神（QA 验证策略）
- 核心判断：现有 420 单测 **不覆盖本 8 项**（HostilityMatrix 是 Tarkov 模型非 GreyZone 6 阵营；无 squad 编制/行为状态机/弹药计数/掉落绑定测试）。**8 项当前均无法在 CI 自动验证**，须先落地接口契约与可测试钩子。
- 关键建议：必埋钩子 —— `FactionRegistry.getRelation`/`getFaction()`、`squadId`+角色清单返回、`getAmmoConsumed()` 计数、`onEntityDeath` 携带 `lootAmmoType/amount/droppedGear`、`onPhase(phase)` 事件；验收分单测/集成(MockBukkit)/黑盒(RCON)三类。

### 🎨 设计师（配置 Schema 评审）
- 核心判断：草案存在 **命名双义/撞车** —— `behavior` 与现有 `settings.patrol` 同名、`squad` 一词双义（全局 max-size vs 编制段）、`loot` 与现有 `drop-ammo` 语义冲突、`personalityPreset` vs `tier-weights` 覆盖关系未明。
- 关键建议：偏好**独立 `emwm_factions.yml`**（registry+relations 全局共享，跨 30+ 模板复用）；`behavior:{mode,params}` 嵌套消歧；`relations` 用对称对表省冗余；`squad-templates.<name>.roles` 重命名；`loot:{use-currency,ammo-type-key,amount,gear}` 统一块；`consumeAmmo` 默认必须 `false`。

### 🛡️ 安全卫士（安全与防滥用）
- 核心判断：经济命脉在需求5+6 —— 须保证「AI 射击零消耗货币弹药 + 掉落走受控命名空间」。最高危威胁为 **Tampering**（AI 走 WM 原生消耗路径穿底 / dropGear 全阵营刷甲）与 **Spoofing**（篡改 faction 伪装友方）。
- 关键建议：faction 标签用服务端只读元数据禁玩家改 NBT；掉落类型白名单命中 greyzone_ammos 7 种；`lootAmmoAmount` 区间硬截断并对齐经济 GDD §5 通胀熔断；生成入口权限门禁（仅 EliteMobs 刷怪组+OP）+ 节流防 DoS。

---

## 2. 综合审查发现（去重合并，按严重度排序）

| # | 严重度 | 类别 | 位置 | 问题描述 | 建议 | 来源 |
|---|--------|------|------|---------|------|------|
| 1 | 🔴 | 规格偏差 | 规格书 §3 需求5 | 误判「AI 弹药隔离」为缺口；代码已隔离（自有计数器+WM.shoot 无弹药参） | 移出 P0 风险，改健壮性校验 | 排障手+主理人核实 |
| 2 | 🔴 | 规格偏差 | 规格书 §2/"当前无 faction" | 误判无 faction；FactionManager/HostilityMatrix 已存在，仅未接 AI 目标 | 需求1 标注「接线扩展」 | 排障手+主理人核实 |
| 3 | 🔴 | 架构未决 | TarkovAIEngine L259 | 目标选择落点未决（use-elitemobs-events 默认 false）→ 须在桥接层实现；现仅扫玩家 | 扩展候选集为 AI 实体+玩家并调 getRelation | 排障手/产品官 |
| 4 | 🟠 | 新功能 | AIState/行为机 | 需求3 GUARD 行为从零：无 GUARD/guardPoint/aggroRadius/leashDistance | 新增 GUARD 状态机+追击截断 | 排障手 |
| 5 | 🟠 | 新功能 | onEntityDeath L280 | 需求6 死亡掉货币弹从零：loot.drop-ammo 配置存在但 EMWM 未实现货币绑定（无 lootAmmoType） | onEntityDeath 按武器→greyzone_ammos 映射写掉落 | 排障手/产品官 |
| 6 | 🟠 | 扩展 | SquadManager L29/L61 | 需求2 精确编制：max-size=5 + assignRole 随机 | 放宽≥8 + 命名编制 + 角色槽位生成 | 排障手 |
| 7 | 🟠 | 扩展 | PersonalityType/EMWMWeaponConfig | 需求4 永不撤退：retreatHpThreshold 已逐实体，缺 neverRetreat 别名+personalityPreset+去硬地板 | 别名+命名预设+去 0.15 硬地板 | 排障手 |
| 8 | 🟡 | 集成简化 | ArmorMechanics + TarkovAIEngine 已操作 equipment | **需求7 可"混用"ArmorMechanics**：生成时给 NPC 套同款护甲，AM 自动算 Bullet_Resistance/MAX_HEALTH，玩家NPC同套 → 复杂度骤降（非从零）。**前置：须验证 AM 对 mob 生效** | 生成时 setEquipment 4 槽 + 复用 AM + 死亡掉落走需求6 | 主理人(2026-07-09 核查) |
| 17 | 🟡 | 设计决策 | 需求7（护甲） | "玩家与NPC 共用 ArmorMechanics 一套护甲"是正确架构（GDD 部署本就只进 ArmorMechanics）。**验证门**：测试服确认 mob 穿戴 gz_* 护甲后实际减伤+加血；集成雷区：EM最大HP vs AM MAX_HEALTH 可能叠加、WM枪伤须被 AM Bullet_Resistance 拦截 | 测试服 RCON 实验(10min) + 2 项校验 | 主理人 |
| 9 | 🟠 | 新功能 | （无 phase 钩子） | 需求8 Boss 协同(P2)从零 | 监听 EliteMobs phase 事件拉起编制 | 排障手 |
| 10 | 🟡 | Schema | 草案 §3 | behavior 与 settings.patrol 同名；squad 双义；loot 重叠；personalityPreset vs tier-weights | 设计师 Top5 改进（嵌套/重命名/对称矩阵） | 设计师 |
| 11 | 🟡 | 安全 | 需求5+6 | 经济防崩校验须随 P0 落地：货币弹药库存Δ=0 + 掉落白名单 + 区间熔断 | 单测断言+白名单+通胀熔断 | 安全卫士 |
| 12 | 🟡 | 安全 | 需求1 | faction 标签防篡改：禁玩家改 NBT | 服务端只读元数据 | 安全卫士 |
| 13 | 🟡 | 测试 | 全 8 项 | 当前 CI 无法自动验证，缺可测试钩子 | 埋 getRelation/getFaction/getAmmoConsumed/掉落事件/Boss phase | 质量门神 |
| 14 | 🟡 | 数值 | 规格书 §7 | 数值全 [PLACEHOLDER]（aggroRadius/guardRadius/leashDistance/lootAmmoAmount）须测试服反算 | 禁止空占位发布，对齐经济 GDD §5 | 产品官 |
| 15 | 🟡 | 玩法 | GDD 枪械 | 酸液喷射器 DoT 缺命中挂毒逻辑（WM 原生不挂） | 先定服务器侧「命中挂 poison」方案 | 产品官 |
| 16 | 🟢 | 复用 | 已存在 | faction 框架 / ammo 隔离 / retreat 字段 / squad 框架 / 感知性格战术系统均可复用 | 勿重造，仅扩展 | 排障手 |

---

## ✅ 行动清单

| # | 行动 | 负责方 | 紧急度 | 期望完成 |
|---|------|--------|--------|---------|
| 1 | 修订规格书认知：需求5 改「已满足+健壮性校验」；需求1 改「接线扩展」；重算 P0 六项范围 | 主理人+设计侧 | P0 | 本周 |
| 2 | 实现需求1（阵营 AI 目标选择）：扩展 `TarkovAIEngine.tickEntity` 候选集为 AI 实体+玩家，对非玩家目标调 `FactionManager.getRelation`，按 enemy/ally/neutral 判定 | 插件团队 | P0 | 里程碑 M1 |
| 3 | 实现需求6（死亡掉货币弹）：`onEntityDeath` 按武器→`greyzone_ammos` 映射写掉落，白名单校验 7 种货币弹，区间截断对齐通胀 | 插件团队 | P0 | 里程碑 M1 |
| 4 | 需求5 健壮性：实机验证 WM 不扣 AI 武器 NBT 弹匣，否则注入满弹匣/禁自扣；加单测断言货币弹药库存恒定 | 插件团队 | P0 | 里程碑 M1 |
| 5 | 实现需求2/3/4：放宽 squad max-size≥8+命名编制+角色生成；新增 GUARD 行为状态机+截断；neverRetreat 别名+personalityPreset+去硬地板 | 插件团队 | P0/P1 | 里程碑 M1-M2 |
| 6 | 埋可测试钩子（getRelation/getFaction/getAmmoConsumed/Boss phase/掉落事件）+ 补 8 项单测与 MockBukkit 集成测 | 插件团队+QA | P0 同步 | 随实现 |
| 7 | 需求7 护甲集成（P1）：采用「混用 ArmorMechanics」——生成时 setEquipment 4 槽套同款护甲，AM 自动算属性，死亡掉落走需求6；**先测试服验证 AM 对 mob 生效**（RCON 实验）；顺便校验 EM最大HP vs AM MAX_HEALTH 不叠加冲突、WM枪伤被 AM Bullet_Resistance 拦截。需求8 Boss 协同（P2） | 插件团队 | P1/P2 | 里程碑 M2-M3 |
| 8 | 设计侧：消歧 schema（独立 emwm_factions.yml + squad-templates 重命名 + behavior.mode 枚举 + loot 统一块）；faction 标签服务端只读 | 设计侧+插件团队 | P0 同步 | 里程碑 M1 |

---

## ⚠️ 待完善 / 已知局限

- **环境限制**：本次团队成员（general-purpose 子代理）运行环境无 `SendMessage` 工具，结论由主理人直接汇编转发，未走正式 team 消息总线；原始产出为会话内结构化文本，未落盘成员独立文件。
- **实机未验证项**：WM 4.3.1 是否对 AI 持有武器 item 的 NBT 弹匣扣减（影响需求5 健壮性，非经济穿透）；需测试服实机确认。
- **数值全 [PLACEHOLDER]**：aggroRadius/guardRadius/leashDistance/lootAmmoAmount 等需测试服反算，禁止空占位发布。
- **酸液 DoT 缺命中逻辑**：T4 酸液喷射器伤害可能落空，需先定服务器侧方案。
- **需求7 混用 ArmorMechanics 的验证门（2026-07-09 新增）**：须测试服确认 ArmorMechanics 的 Bullet_Resistance/MAX_HEALTH 对非玩家实体生效（MechanicsCore 机制通常实体无关，但 AM 伤害监听器个别版本可能仅限玩家）。若生效 → 需求7 塌缩为「生成套甲+掉落」薄胶水；若仅限玩家 → 需配置项或薄垫片，勿另建 NPC 护甲模型。
- **关系矩阵动态性**：PVE→PVP 据点易主/声望会变动阵营关系，当前设计为静态 yml，需运行时写回接口。

---

## 📚 成员产出索引

- gstack-product-reviewer（产品官）原始产出：可行性总判(Go/条件Go) + 逐项评审表 + 交付排序(1+6+5→2/3/4→7→8) + 赛季一风险 + 3 条设计侧追问。
- gstack-investigator（排障手）原始产出：8 项代码现状核查表（含类名/行号）+ 关键技术风险 Top3（弹药/目标路径/squad上限）+ 未决项阻塞判断 + 现状vs规格书差异清单（已主理人 grep 核实）。
- gstack-qa-lead（质量门神）原始产出：验证策略总览(单测/集成/黑盒) + 逐项验收映射表 + 验收难点 Top3 + 可测试钩子建议 + 当前框架覆盖度(420 测不覆盖本 8 项)。
- gstack-designer（设计师）原始产出：Schema 一致性审查表 + 向后兼容评估 + 独立 emwm_factions.yml 偏好 + Top5 改进 + 推荐最小 config 骨架(yaml)。
- gstack-security-officer（安全卫士）原始产出：STRIDE 风险清单(6 类) + 经济防崩专项(3 校验点) + 阵营标签完整性 + DoS/性能滥用 + 落地优先建议。

> 本报告由软件工坊 AI 协作生成，关键决策（尤其规格书认知修订与里程碑排期）请由工程负责人复核。
