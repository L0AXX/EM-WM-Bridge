# EM-WM-Bridge 实机未验证 / 待校准项汇总

> 维护自用户状态同步（2026-07-10）。本文件汇总所有**未实机验证**、**需测试服反算**、**需验证门确认**的项，作为后续落地前的检查清单。
> 其中酸液 DoT 已单列文件夹：[acid-dot/](acid-dot/README.md)

---

## 1. NBT 弹药扣减（WM 4.3.1 AI 持有武器 item 的弹匣扣减）
- **状态：用户确认「重构前已实现，应该可以」→ 标记为已确认，仍待实机复核。**
- 影响范围：需求5（consumeAmmo）健壮性，**非经济穿透**。
- 实机复核点：AI 持 WM 武器开火后，NBT 弹匣计数是否递减；若重构后回归，需对照重构前实现补回。

## 2. 数值全 `[PLACEHOLDER]`（需测试服反算，禁止空占位发布）
- 涉及：`aggroRadius`(35) / `leashDistance`(45) / `guardRadius` 等据点守卫参数；`lootAmmoAmount`（需求6 未开工）等经济掉落量。
- **原则（用户硬性要求）：代码/配置不得保留空占位；未校准项以非零临时值 + 注释标注，待测试服反算后替换。**
- 已落实：源码 `TarkovAIEngine.java` 守卫默认值（45/35）已加注释「需测试服校准，禁止空占位」。
- 待办：M4（需求6）开工时，`lootAmmoAmount` 同样以非零临时值占位并注释，测试服反算后定稿。

## 3. 酸液 DoT 缺命中逻辑 → [acid-dot/](acid-dot/README.md)
- T4 酸液喷射器伤害可能落空，缺命中 → 施加 DoT 链路；需先定服务器侧方案（design-open-items.md），再测试服反算数值（test-plan.md）。

## 4. 需求7 混用 ArmorMechanics 验证门（2026-07-09 新增）
- **须测试服确认**：AM 的 `Bullet_Resistance` / `MAX_HEALTH` 是否对**非玩家实体**生效。
  - 依据：MechanicsCore 机制通常实体无关，但 AM 伤害监听器个别版本可能仅限玩家。
- 分支结论：
  - ✅ 若对非玩家生效 → 需求7 **塌缩为「生成套甲 + 掉落」薄胶水**（无需自建 NPC 护甲模型）。
  - ⚠️ 若仅限玩家 → 需提供**配置项或薄垫片**绕开，不要另建 NPC 护甲模型。
- 验证项已并入 acid-dot/test-plan.md 的 **T5**（酸液 DoT 与护甲交互强相关）。

## 5. 关系矩阵动态性（运行时写回接口）
- 当前 `emwm_factions.yml` 为**静态** yml；但 PVE→PVP 的据点易主 / 声望会变动阵营关系。
- ✅ **已实现（2026-07-11）**：`FactionManager` 增加 `setRelation(selfId, otherId, Relation)`（仅内存、未配置时 no-op 返回 false）+ `save(JavaPlugin)` / `saveToFile(File)` 持久化回 `emwm_factions.yml`（保留其他顶层键，仅重建 factions 段）。
  - `FactionProfile` 配套 `clearRelationTo` / `setRelationTo` / 关系 getter；`setRelationTo` 按 HOSTILE→hostile、FRIENDLY→ally、NEUTRAL→neutral 归位。
  - 设计约束满足：动态变更不破坏「未配置默认 HOSTILE」回退与枚举矩阵兜底（未配置时 setRelation 直接 no-op）。
  - 单测：FactionProfileTest（6）+ FactionManagerGreyZoneTest 追加（setRelation 生效 / 改 HOSTILE / 未知阵营 false / 未配置 no-op / saveToFile 往返 reload 校验）。

## 6. 环境 / 协作流程备注
- general-purpose 子代理运行环境**无 SendMessage 工具**；结论由主理人（主代理）直接汇编转发，未走正式 team 消息总线；原始产出为会话内结构化文本，未落盘成员独立文件。
- **测试记录约定**：用户在测试服实机验证后会在对应文件夹（如 `acid-dot/`）记录；主理人下次被问及酸液 / NPC 护甲 / 数值校准时，**先查对应 open-items 记录再落地实现**。

---
### 状态总览
| 项 | 状态 | 阻塞/依赖 |
|----|------|-----------|
| NBT 弹药扣减 | 已确认，待实机复核 | 需求5 健壮性 |
| 数值占位 | 临时值已标注释，待反算 | 测试服 |
| 酸液 DoT | 设计待定 + 测试待记录 | 服务器侧方案 |
| 需求7 护甲门 | 验证待测试服 | AM 版本 |
| 动态关系矩阵 | ✅ 已补 setRelation+save 接口（2026-07-11） | 无 |
