# EM-WM-Bridge 功能说明（中文）

> 一份「这个插件实际干什么」的人话说明，写给服主、策划、对接开发者看。
> 代码级文档请看 `docs/CODE_WIKI.md`；本文件不重复贴代码，只讲能力与边界。
>
> 版本：`0.3.0-alpha`（以 `build.gradle` 为准） · 运行环境：Paper 1.21.4 / Folia · JDK 21

---

## 1. 一句话定位

**EM-WM-Bridge 是一个「让怪物会用人形枪械战术」的桥接插件。**
它把 [EliteMobs](https://github.com/MagmaGuy/EliteMobs)（精英怪/Boss 系统）和 [WeaponMechanics](https://github.com/WeaponMechanics/WeaponMechanics)（枪械系统）+ [ArmorMechanics](https://github.com/WeaponMechanics/ArmorMechanics)（护甲减伤）粘在一起，并给这些怪物套上一层**塔科夫式（Tarkov）的 AI 战斗大脑**——会索敌、会拉近拉远、会压制、会换弹、会撤退、会扔雷。

> 它**不是**一个独立的枪械包，也**不是**一个独立的怪物包。它是「胶水 + AI 引擎」。

---

## 2. 它已经能干什么（已落地能力）

下表是 v0.3.0 里**真实存在、已通过单测**的能力。每一项都对应具体的包/类，方便对接时定位。

| # | 能力 | 实际做了什么 | 代码落点 |
|---|------|--------------|----------|
| 1 | **统一武器绑定** | 把 WM 的枪「绑」到 EM 生成的怪物身上；从 `emwm_mob_templates.yml` / `config.yml` 三级继承拿参数；按怪物元数据/名字识别 tier（scav / raider / boss 等）并应用**耐久倍率**。一套绑定系统，不再互相抢。 | `managers/MobWeaponManager` · `listeners/EliteMobSpawnListener` · `config/EMWMWeaponConfig` |
| 2 | **塔科夫式 AI 引擎** | 每 4 tick 跑一轮主循环：警戒状态机 `YELLOW → ORANGE → RED`；`canShoot` 门控决定何时开火；有「压制」状态（被打会蹲/后撤）；目前**目标只锁定玩家**。 | `ai/TarkovAIEngine` 及 `ai/` 下 23 个类 |
| 3 | **AI 弹药隔离（经济保护）** | 怪物开火走自己的 `emwm_ammo` 计数器，调用 `WeaponMechanicsAPI.shoot(entity, weapon, loc)`（**无弹药参数**），**根本不碰玩家的货币弹药池**。 | `managers/MobWeaponManager.shoot()` |
| 4 | **小队系统** | 怪物可按小队生成、协同移动/接敌；支持角色分配（LMG/AR/精确/医疗等）。当前 `squad.max-size` 硬编码上限为 **5**。 | `managers/SquadManager` · `ai/squads/` |
| 5 | **阵营框架（已有未接线）** | `FactionManager` / `HostilityMatrix` / `TarkovFaction` **代码已存在**，`getRelation(A,B)` 已实现敌/友/中立判定；但目前**只用于玩家目标**，AI 之间没互相识别阵营。 | `ai/factions/` · `managers/FactionManager` |
| 6 | **性格 & 极端事件** | 每个怪有性格（胆小/谨慎/莽撞等，决定交不交战、怎么交战，不影响检测距离）；`ExtremeEventManager` 实现恐慌 / 肾上腺素 / 战术失误等临场状态。 | `ai/` · `managers/ExtremeEventManager` |
| 7 | **投掷物 / 手雷 AI** | 怪物会扔雷；`ThrowableManager` 给投掷体套装备并管理弹道；可开 `enableGrenadeAI`。 | `managers/ThrowableManager` |
| 8 | **公共 API（供任务系统对接）** | `EMWMBridgeAPI` 暴露静态元数据读取：`getTier / getCombatState / getWeapon / getAmmo / getAggressiveness / isReloading / isBoss / isEMWMMob / isInCombat / isSuppressing`。已用于 Chemdah 任务查询。 | `api/EMWMBridgeAPI` |
| 9 | **击杀事件** | 发出 Bukkit 事件 `EMWMKillEvent`，携带 `KillMethod`（GUN / GRENADE / MELEE / EXPLOSION / OTHER），方便其它插件做击杀统计、任务、掉落。 | `events/EMWMKillEvent` |
| 10 | **三级配置继承** | 单怪配置 → 全局模板 → WM 原生参数 → 兜底默认值，逐字段合并；缺字段自动回退。 | `config/EMWMWeaponConfig.mergeWithTemplate()` |
| 11 | **内置自测命令** | `/emwm test` 跑 10 个场景（健康/配置/绑定/弹药/射击/解绑/AI 引擎…），输出 `[EMWM_TEST_RESULT]{json}`，供 RCON 脚本解析。 | `test/PluginTestRunner` · `tests/rcon_blackbox_test.py` |

---

## 3. 它为 GreyZone（地铁末日）规划、但还没写的能力

这些来自 `GreyZone_Design/engineering/EMWM-Bridge_底层支撑需求规格.md`，详见 `deliverables/gstack/feasibility-greyzone-emwm-2026-07-09.md` 的可行性评审。**当前代码尚未实现，属于开发计划**：

| 需求 | 优先级 | 要补什么 | 现状判定（已核实） |
|------|--------|----------|--------------------|
| 1 阵营敌我目标选择 | P0 | 把 `FactionManager.getRelation` 接到 AI 候选目标上，让怪也会打「敌对阵营的怪」 | **接线**，非从零（框架已在） |
| 2 角色编制小队 | P0 | 按角色槽位精确编制，突破 `max-size=5` | **扩展**已有 SquadManager |
| 3 守卫 / 据点防御 | P0 | 新增 `behavior: GUARD` + 驻守点/半径/激怒半径/拴绳距离 | **从零**（新增行为） |
| 4 永不撤退覆盖 | P0 | `neverRetreat:true`（等效 `retreatHpThreshold=0`）+ 命名性格预设 | **扩展**（字段已存在） |
| 5 AI 弹药隔离 | P0 | 健壮性校验：确认 WM 不扣 AI 武器 NBT 弹匣 | **已基本满足**（见能力 #3），只需加断言 |
| 6 死亡掉货币弹 | P0 | 怪物死亡按武器映射到 7 种货币弹药掉落（白名单 + 区间熔断） | **从零**（接掉落结算） |
| 7 护甲混用 | P1 | 生成时给怪套 `ArmorMechanics` 同款护甲，玩家/NPC 同一套 | **薄胶水**（AM 机制同源，见下） |
| 8 Boss 协同召唤 | P2 | 监听 Boss 阶段事件，按阶段拉起命名编制 | **从零** |

---

## 4. 关键约定：护甲可以和 ArmorMechanics 混用吗？

**可以，而且推荐这么做，能大幅降低开发复杂度。**

- GreyZone 的护甲本就只进 ArmorMechanics（`greyzone_armors.yml` / `greyzone_sets.yml` 全是 AM 原生 schema：`Bullet_Resistance` / `MAX_HEALTH` / `Potion_Effects`）。
- ArmorMechanics 是 WeaponMechanics 的官方插件，基于 **MechanicsCore**——而 EM-WM-Bridge 已经在 `EMWMBridge` 里把自定义 Mechanic 注册进了同一个 MechanicsCore，框架同源。
- EM-WM-Bridge 早已会操作实体装备（`MobWeaponManager` 设手持武器、`ThrowableManager` 设头盔）。给怪套 4 件护甲只是几行 `entity.getEquipment().setXxx(item)`。
- 因此需求 #7 从「建一套 NPC 护甲系统」塌缩成「生成时套同款护甲 + 复用掉落系统」，玩家和 NPC **穿同一份配置、零数值漂移**。
- ⚠️ 唯一需测试服验证的点：确认 ArmorMechanics 对非玩家实体**实际生效**减伤/加血（个别版本监听器可能仅限玩家）。验证通过则需求 #7 几乎零成本；若有限制，最后手段是在自身伤害监听里按模板 `Bullet_Resistance` 薄垫片，**不要另建 NPC 护甲模型**。

---

## 5. 它不干什么（边界，避免误解）

- ❌ **不提供枪械本身**：枪的子弹、伤害、弹道由 WeaponMechanics 定义，本插件只「调用」。
- ❌ **不生成怪物模型/外观**：怪物由 EliteMobs 定义，本插件只「接管它的战斗行为」。
- ❌ **当前不做 AI 互殴**：v0.3.0 的 AI 目标**只认玩家**；怪打怪需要按计划接需求 #1。
- ❌ **不做经济/任务系统**：弹药货币、任务逻辑由 GreyZone 侧的独立系统（如 chemdah）负责；本插件只通过 API / 事件**暴露数据**供其读取。
- ❌ **不替代配方/合成/交易**：这些是 GreyZone 经济层的事。

---

## 6. 模块 / 包速查

```
com.emwbridge
├── ai/         塔科夫 AI 引擎 + 小队 + 阵营 + 极端事件（23 个类）
├── api/        EMWMBridgeAPI（对外静态查询接口，1 个类）
├── config/     武器配置模型 + 三级继承合并（EMWMWeaponConfig 等）
├── events/     EMWMKillEvent 等 Bukkit 事件
├── listeners/  怪物生成监听、战斗伤害分类监听等
├── managers/   MobWeaponManager（绑定/射击/弹药）、SquadManager、FactionManager、ExtremeEventManager、ThrowableManager
├── mechanics/  注册进 MechanicsCore 的自定义机制（6 个类）
├── test/       PluginTestRunner（/emwm test）
└── utils/      工具类
```

---

## 7. 配置与部署速览

- **构建**：`./gradlew build` → 产物 `build/libs/EM-WM-Bridge-0.3.0-alpha.jar`
- **测试**：`./gradlew test`（JUnit5 + Mockito，JaCoCo 门禁 ≥30%）；黑盒见 `tests/rcon_blackbox_test.py`
- **部署**：`./gradlew deploy -PserverPlugins="<服务器>/plugins"`（部署前需先关服，jar 会被锁）
- **核心配置**：
  - `config.yml`：tier-settings（耐久倍率等）、squad、感知、性格、AI 参数
  - `emwm_mob_templates.yml`：逐怪物覆盖（weapon / maxRange / spread / damageMultiplier / meleeSwitchHealthPercent / allowAutoReload / onlyAimShoot / enableGrenadeAI 等）
  - `plugin.yml`：插件元数据

---

## 8. 在 GreyZone（地铁末日）项目里的角色

EM-WM-Bridge 是「人形阵营 AI 引擎」：6 大阵营（铁卫军 / 车站镇 / 史前遗民 / 白狼 / 拾荒者 / 噬体教 / 变异体 / 渡鸦…）里**人形单位**的战斗大脑都由它驱动。它消费 GreyZone 侧的武器/护甲/弹药/阵营定义，输出「会战术交火的 NPC」。世界/经济/任务由 GreyZone 设计包独立负责。

---

## 9. 相关文档索引（好文档在哪）

**EM-WM-Bridge 自身**
- `README.md` — 构建/测试/部署速查
- `overview.md` — RCON 黑盒测试方案概览
- `docs/CODE_WIKI.md` — **代码级完整文档（1164 行）**，改代码前必读
- `EM-WM-Bridge-插件使用手册.docx` — 面向服主的可视化使用手册
- `deliverables/gstack/feasibility-greyzone-emwm-2026-07-09.md` — GreyZone 需求可行性评审（含对设计规格书的 3 处认知修正）

**GreyZone 设计需求（外部，`F:\LOAXX Devlop CLI\GreyZone S_1.21.4JDK21\GreyZone_Design`）**
- `README.md` / `设计总览.md` — 项目入口与总览
- `engineering/EMWM-Bridge_底层支撑需求规格.md` — **本插件必须支持的 8 项底层缺口契约（必读）**
- `GDD_世界观与核心玩法.md` · `GDD_世界地图与赛季内容.md` — 世界观
- `GDD_枪械系统.md` · `GDD_怪物系统.md` · `GDD_子弹货币经济系统.md` · `数值验证_战斗模型.md` — 数值基线
- `factions/` — `GDD_阵营总览.md` / `GDD_人形阵营与AI.md` / `greyzone_faction_templates.yml`
- `armor/` — `GDD_护甲系统.md` / `greyzone_armors.yml` / `greyzone_sets.yml`
- `weapons/` · `monsters/` · `dungeons/` · `quests_npc/` · `tools/` — 各系统配表与 GDD
- `AI_HANDOFF_交接简报.md` · `验证报告_逻辑与数值.md` — AI 交接与验证结论

---

*最后更新：2026-07-09*
