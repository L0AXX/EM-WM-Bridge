# EM-WM-Bridge × GreyZone 实施计划（颗粒度对齐版）

> 受众：插件团队（执行）｜ 对齐对象：设计团队 `engineering/EMWM-Bridge_底层支撑需求规格.md` v0.1
> 目的：把设计侧 8 项需求**逐字段**对齐到代码任务（文件:方法级），锁定 5 个未决项，给出里程碑与验收点。
> 依据：`feasibility-greyzone-emwm-2026-07-09.md` + 本轮代码探查（gstack-code-mapper）。

---

## 0. 五个未决项 —— 已锁定决策

| # | 未决项（规格书 §7） | 锁定决策 | 理由 |
|---|----------------------|----------|------|
| U1 | 目标选择实现路径（桥接层 vs EliteMobs 事件） | **桥接层独立实现**，`use-elitemobs-events` 保持 false | 默认即 false；关系矩阵在桥接层实现，最可控 |
| U2 | 阵营标签载体（emwm: 内联 vs 独立文件） | **独立 `emwm_factions.yml`**（registry+relations）+ 逐实体 `emwm_faction` 元数据覆盖 | 设计侧倾向独立文件，利于跨实体维护 |
| U3 | WM 弹药隔离 API 是否存在 | **不需要 WM API**；`WeaponMechanicsAPI.shoot(entity,title,loc)` 无弹药参，AI 用自有 `emwm_ammo` 计数 → 已隔离。仅加 `consumeAmmo` 开关做语义化 | 已核实 |
| U4 | 死亡掉落结算权（EM vs 桥接层） | **桥接层拦截死亡注入货币弹 ItemStack** | EMWM 掌握"怪持哪把枪"，由它算掉落最准 |
| U5 | 数值 `[PLACEHOLDER]`（半径/数量） | **首版用规格书 §3 默认值**：aggroRadius=35 / guardRadius=12 / leashDistance=45 / lootAmmoAmount=[8,24]；测试服实机校准后回填 | 先跑通逻辑，再调参 |

---

## 1. 颗粒度映射总表（需求 → 子任务 → 代码落点 → 验收）

### 需求 5 ｜ AI 弹药隔离【P0】—— ✅ 已交付（consumeAmmo 字段 + 单测，2026-07-09）
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 5.1 `consumeAmmo` 字段加入 `EMWMWeaponConfig`（默认 false） | `config/EMWMWeaponConfig.java` | 旧模板缺省=false 不报错 |
| 5.2 shoot 弹药判定前置 `if(!consumeAmmo)` 跳过递减 | `managers/MobWeaponManager.shoot` L460/L493 | AI 开火不扣 emwm_ammo |
| 5.3 单测：连射 100 发，玩家货币弹药(WM)库存 0 变化；emwm_ammo 不递减 | 新增 `MobWeaponManagerAmmoTest` | 断言货币弹药不变 |

### 需求 1 ｜ 阵营归属与跨阵营目标选择【P0】
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 1.1 新建 `emwm_factions.yml`（registry+relations，取自规格书 §3） | `src/main/resources/emwm_factions.yml` | 8 阵营+关系矩阵完整 |
| 1.2 `FactionManager.load(config)` 加载 registry/relations | `managers/FactionManager` / `ai/faction/*` | 启动加载无报错 |
| 1.3 生成时打标签：读 metadata `emwm_faction` 覆盖 `assignByTier` | `listeners/EliteMobSpawnListener.bindWithEMWMConfig` L148 末尾 | 实体带 faction 标签 |
| 1.4 目标候选扩展：扫 `getLivingEntities()`，按 `getRelation==HOSTILE` 过滤 | `ai/TarkovAIEngine.tickEntity` L259（现仅 getPlayers） | 白狼只打敌对阵营怪，不打友方/中立 |
| 1.5 neutral 被攻击才反击（shouldTurnHostile 已有 L47，接线） | `FactionManager.shouldTurnHostile` | 车站镇被误伤不还手 |

### 需求 4 ｜ 永不撤退 + 性格预设【P0】
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 4.1 `neverRetreat` 字段 + 撤退阈值改读 `tacticalRetreatHp`（现死字段 L123 未被引用） | `config/EMWMWeaponConfig` + `PersonalityManager.decide` L102 | 持 neverRetreat 血量 1% 不撤退 |
| 4.2 `personalityPreset` 命名预设（config `personality.presets.*` 引用） | `ai/personality/PersonalityType` + `assignPersonality` L83 | 模板可强制指定性格 |
| 4.3 单测：neverRetreat 实体始终 ENGAGE | 新增测试 | 断言不进入 RETREAT |

### 需求 2 ｜ 角色编制小队【P0】
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 2.1 全局 `squad.max-size` 放开 ≥8（或按编制覆盖） | `managers/SquadManager.reload` L29 | 编制可超 5 |
| 2.2 `squads:` 段 + `tryJoin(entity,tier,personality,squadName)` 重载 | `managers/SquadManager` L33/L61 | 指定编制生成 |
| 2.3 `assignRole` 按 `roles:` 配置定角色（修 captain 写死 bug L54） | `SquadManager.assignRole` | 2LMG+3AR+1精确+1替补 精确生成 |
| 2.4 生成入口传 `emwm_squad` 元数据 | `registerMob` L676 → bindWithEMWMConfig | EliteMobs 刷怪组可指定编制 |

### 需求 3 ｜ 守卫据点行为【P0】
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 3.1 `behavior: GUARD` 枚举 + `AIDecision` 加 GUARD | `ai/AIDecision` L3-8 | 枚举存在 |
| 3.2 `AIState` 加 `homeLocation`/`leashDistance`/`aggroRadius` | `ai/AIState` L747 | 字段存在 |
| 3.3 `executeGuardState`：驻守 home，敌入 aggroRadius 猎杀，超 leashDistance 回防，禁用撤退 | 新增方法 + tick 路由 L414 | 45 格内追击、超则回防 |
| 3.4 模板字段 `behavior/guardPoint/guardRadius/aggroRadius/leashDistance` | `EMWMWeaponConfig` | 缺省不报错 |

### 需求 6 ｜ 死亡掉货币弹【P0】
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 6.1 `lootAmmoType`（per-template，对应 greyzone_ammos.yml key）+ `lootAmmoAmount:[min,max]` | `EMWMWeaponConfig` | 字段存在 |
| 6.2 死亡注入：`onEntityDeath` L283 加 `event.getDrops().add(buildAmmoItem)` | `listeners/EliteMobSpawnListener.onEntityDeath` L280 | 掉落 7 种货币之一 |
| 6.3 由 `lootAmmoType` 构建 WM 货币弹 ItemStack（从 WM ammo API 或 greyzone_ammos.yml 物品定义） | 新增 `buildAmmoItem(entity)` | 掉 greyzone_rifle_improv 8–24，不出现 WM 原生 RifleAmmo |
| 6.4 白名单校验 + 区间熔断（防刷/通胀） | 6.3 内 | 数量受控 |

### 需求 7 ｜ 护甲混用 ArmorMechanics【P1】
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 7.1 `gear:` 块（4 槽 + dropGear） | `EMWMWeaponConfig` | 字段存在 |
| 7.2 生成时 `entity.getEquipment().setXxx` 套 greyzone_armors.yml 装备 | `EliteMobSpawnListener` bind 末尾 | NPC 穿同款护甲 |
| 7.3 `dropGear:true` 时死亡移交护甲掉落 | `onEntityDeath` | 掉落对应护甲 |
| 7.4 **测试服验证**：AM 对非玩家实体实际减伤+加血 | 实验 | 验证通过则成本≈0 |

> **状态（2026-07-10）：骨架已实现，7.4 验证门未解。** 用户选择「还没测/不确定」→ 按薄胶水路径落地代码结构（7.1–7.3 全做），护甲实际减伤依赖 AM 对非玩家实体的支持（验证门 7.4）待测试服确认。
> - 若 AM 对非玩家生效 → 当前骨架即为全部所需（成本≈0）。
> - 若 AM 仅限玩家 → 通过模板 `gear.maxHealthBoost > 0` 由桥接层补 NPC 最大生命（薄垫片，已预留 `applyMaxHealthBoost`）；严格不另建 NPC 护甲模型。
> - `greyzone_armors.yml` 材质（IRON_*/DIAMOND_*）与显示名为占位，待测试服替换为真实 GreyZone 货币护甲（沿用「禁止空占位发布」：非空临时值 + 明确注释）。

### 需求 8 ｜ Boss 协同召唤【P2】
| 子任务 | 代码落点 | 验收点 |
|--------|----------|--------|
| 8.1 监听 Boss 阶段事件 → 调编制生成 API | 新增 listener | 阶段 2 拉起 4 名协同 |
| 8.2 协同单位继承需求 1 阵营判定 | 复用 FactionManager | 继承敌我 |

---

## 2. 里程碑顺序（依赖驱动）

- **M1（地基 + 经济护栏）**：需求5（5.1–5.3）+ 需求1 基础设施（1.1–1.3）
- **M2（阵营战争成立）**：需求1.4–1.5（目标选择接线）+ 需求4（4.1–4.3）
- **M3（编制 + 据点）**：需求2（2.1–2.4）+ 需求3（3.1–3.4）
- **M4（经济闭环）**：需求6（6.1–6.4）
- **M5（装备 + 终战）**：需求7（7.1–7.4）+ 需求8（8.1–8.2）

> 依赖：无 M1 则 M2 目标判定无意义；无 M4 则经济穿底。故 M1→M4 为硬序，M2/M3 可并行。

---

## 3. 第一刀：需求 5（已开工）

见 `EMWMWeaponConfig` + `MobWeaponManager` 改动 + `MobWeaponManagerAmmoTest`。
验收：连射 100 发，玩家货币弹药库存 0 变化（经济护栏落地）。

---

## 4. 进展记录（逐需求）

### ✅ 需求 5（AI 弹药隔离语义化）— 已完成
- `EMWMWeaponConfig.consumeAmmo`（默认 true=有限，GreyZone 模板设 false=无限）
- `MobWeaponInstance.consumesAmmo` + `MobWeaponManager.shoot` 门控；`EMWMConfigCache` 解析+合并
- 4 测试 PASS（连射100发 emwm_ammo 不递减）

### ✅ 需求 1 基础设施（数据 + 加载 + 打标签）— 已完成 2026-07-09
- 新增 `emwm_factions.yml`（GreyZone 8 阵营 + player，对称关系矩阵）
- 新增 `FactionProfile`；`FactionManager` 重写为「字符串阵营系统」+ 旧 Tarkov 枚举回退（零破坏）
- `TarkovAIEngine` 构造 `factionManager.load(plugin)`；`registerMob` 读 `emwm_faction` 标签打标签
- `EMWMWeaponConfig.faction` + `EMWMConfigCache` 解析/合并；`EliteMobSpawnListener` 写 `emwm_faction` 元数据
- 7 测试 PASS（白狼vs玩家=FRIENDLY，vs噬体教=HOSTILE，vs拾荒者=NEUTRAL，未配置回退枚举）
- **关键修复**：`FactionManager.load()` 改为异常安全（try/catch），避免 mock 环境 `saveResource` 抛错拖垮引擎构造
- 全套 402 测试 0 失败，覆盖率 33.3%（门禁✅）

### ✅ 需求 1.4–1.5（跨阵营目标选择接线）— 已完成 2026-07-09
- `TarkovAIEngine.tickEntity`：无玩家目标时，扫描敌对 AI 实体（emwm_ai_enabled + 阵营ID）经 `FactionManager.isHostile` 过滤，最近者交战
- 采用最小侵入方案：玩家交战路径（executeTacticalAction）100% 不变；AI-vs-AI 为轻量分支（复用瞄准收敛 + shoot，暂不含掩体/投掷/极限事件战术）
- `FactionManager.isHostile(self,target)` 统一门控（HOSTILE 即敌对；NEUTRAL 仅被误伤才反击）；`shouldTurnHostile` 改为防御性空安全
- 验收：白狼主动杀噬体教/变异体/渡鸦，不杀友方(车站镇)/中立(拾荒者)；isHostile 单测 PASS
- 全套 403 测试 0 失败，覆盖率 33.0%（门禁✅）
- 注：AI-vs-AI 的详细 LOS/掩体/投掷战术为后续增强项（M2 收尾）

### ✅ 需求 4（永不撤退 + 性格预设）— 已完成
- `EMWMWeaponConfig` 新增 `neverRetreat` + `personalityPreset` 字段；`EMWMConfigCache` 解析（`tactics.neverRetreat` / `personalityPreset`）+ 合并
- `PersonalityManager`：per-entity `neverRetreat`/`retreatHpThreshold` 覆盖 + `personality.presets.<name>` 解析（`reload`）
- `decide()`：`neverRetreat=true` 永不进入 RETREAT；撤退阈值 = per-entity 覆盖 ?? 历史 0.15 兜底（**零回归**：未配置撤退阈值的服务器行为不变）
- 接线：沿用 `emwm_faction` 元数据模式，监听器写入 `emwm_never_retreat`/`emwm_retreat_hp`/`emwm_personality_preset`，引擎 `registerMob` 读取并注入
- `config.yml` 增加 `personality.presets` 示例（fanatic/guardians/ambusher/cowardly）
- 玩家交战路径（`executeTacticalAction`）保持 100% 不变
- 验收单测：`PersonalityManagerTest`（7 项：neverRetreat 不撤退、阈值覆盖、0.15 零回归、预设解析/强制、无效名安全忽略、removeEntity 清理）+ `EMWMWeaponConfigTest`（5 项合并/默认值）
- 全套 415 测试 0 失败，覆盖率门禁✅

### ✅ 需求 2（角色编制小队）— 已完成
- `SquadManager`：`tryJoin(entity,tier,personality,squadName)` 重载（需求2.2），命名编制无视距离直接编队；`squad.squads.<name>.max-size` 覆盖全局上限（需求2.1，可放开 ≥8）；`assignRole` 按 `roles` 配额定角色，配额耗尽随机（需求2.3）；修复 `newSquad.captainUuid` 写死 bug（两分支恒等）
- `EMWMWeaponConfig` 新增 `squad` 字段（含合并继承）；`EMWMConfigCache` 解析 `squad` 段
- 接线：监听器写 `emwm_squad` 元数据，引擎 `registerMob` 读取并走命名编队（需求2.4）
- `config.yml` 增加 `squad.squads.iron_legion_fireteam` 示例（max-size=8 + roles 配额）
- 单测 `SquadManagerTest`（5 项）：同名编队/满员拒绝/角色配额/captain/全局回退

### ✅ 需求 3（据点守卫行为）— 已完成
- 新增 `AiBehavior` 枚举（FREE/GUARD）+ `AIDecision.GUARD`（需求3.1）
- `AIState` 增加 `behavior`/`homeLocation`/`leashDistance`/`aggroRadius`（需求3.2）
- 引擎 `tickEntity`：GUARD 实体走独立 `executeGuardState` —— 驻守 home、aggroRadius 内猎杀、超 leashDistance 回防、禁用撤退（需求3.3）；**玩家交战路径完全不受影响**
- `EMWMWeaponConfig` 新增 `behavior`/`guardRadius`/`aggroRadius`/`leashDistance`（含默认值 12/35/45 与模板继承）；`EMWMConfigCache` 解析顶层 `behavior` 与 `guard` 子块（需求3.4）
- 接线：监听器写 `emwm_behavior`/`emwm_aggro_radius`/`emwm_leash_distance`，引擎 `registerMob` 捕获 home 与半径
- 单测：`EMWMWeaponConfigTest`（3 项默认/继承）+ `EMWMConfigCacheTest`（2 项 guard 子块/顶层解析）

### ✅ 需求 6（死亡掉货币弹）— 已完成 2026-07-10
- 新增 `LootManager`（com.emwbridge.loot）：reload 载入 `greyzone_ammos.yml`（ammos 物品定义 + gun-ammo-map 映射）；`resolveAmmoType` = 模板 lootAmmoType ?? gun→ammo 映射；`buildAmmoItem` 白名单校验 + 区间随机 + 硬上限64 防通胀（computeLootAmount 纯逻辑可单测）
- 新增 `greyzone_ammos.yml`（7 种货币弹定义 + gun→ammo 映射），材质/显示名需测试服替换为真实货币物品（已在文件头注释）
- `EMWMWeaponConfig` 新增 lootAmmoType/lootAmmoMin/lootAmmoMax（默认 [8,24] = 规格 §3，待校准）；`EMWMConfigCache` 解析 `loot` 子块（模板路径 parseTemplateSection）+ 合并
- `EMWMBridge` 装配 LootManager（onEnable + reloadAll）
- `EliteMobSpawnListener`：bind 时解析掉落参数写 `emwm_loot_ammo_*` 元数据；onEntityDeath 读取并经 `event.getDrops().add()` 注入货币弹（仅 EMWM 控制怪、玩家交战路径不受影响）；lootManager null 守卫
- 单测：LootManagerTest（9：白名单/硬上限/min钳制/区间随机/resolve 优先级）+ EMWMWeaponConfigTest（2：默认/继承）+ EMWMConfigCacheTest（1：loot 子块解析）
- 全套 438 测试 0 失败，覆盖率门禁✅

### ✅ 需求 7（护甲混用 ArmorMechanics，M5 P1）— 骨架已完成 2026-07-10
- `EMWMWeaponConfig` 新增 gear 块字段（gearHelmet/Chestplate/Leggings/Boots + gearDropGear + gearMaxHealthBoost）+ 模板继承 + defaults（dropGear=true / maxHealthBoost=0 信赖 AM）
- `EMWMConfigCache` 解析 `gear` 子块（模板路径 parseTemplateSection）+ 合并
- 新增 `greyzone_armors.yml`（护甲物品定义，材质占位 IRON_*/DIAMOND_*，注释待测试服替换）
- 新增 `GearManager`（com.emwbridge.loot）：reload 加载护甲定义；resolveSlotKey 槽位映射；buildArmorItem 白名单；equipGear 套甲；applyMaxHealthBoost（7.4 垫片）；shouldDropGear
- `EMWMBridge` 装配 GearManager（onEnable + reloadAll + getter）
- `EliteMobSpawnListener`：bind 套甲 + 写 `emwm_gear_*` 元数据（null 守卫，玩家交战路径不受影响）；onEntityDeath 按槽注入护甲掉落；新增 `getMetaBoolean`
- 单测：GearManagerTest（10）+ EMWMWeaponConfigTest（3：默认/继承/覆盖）+ EMWMConfigCacheTest（2：gear 块解析/dropGear 缺省）
- 全套测试 0 失败，覆盖率门禁✅
- **待办**：7.4 测试服验证 AM 对非玩家实体减伤/加血 → 决定成本是否≈0 或启用 maxHealthBoost 垫片；greyzone_armors.yml 替换为真实护甲

### ⏳ 需求 8 — 按 M5 推进
- 需求8（Boss 协同召唤，M5 P2）：监听 Boss 阶段事件 → 调 SquadManager 生成 API，继承需求1 阵营

---

> ⚠️ **本地测试坑（团队必读）**：本沙箱 Gradle **守护进程**环境过大，fork 测试 JVM 会 `xargs: environment is too large for exec` → 瞬时退出。跑测试必须加 `--no-daemon`：`./gradlew test --no-daemon`。GitHub Actions CI（Linux runner）不受影响，作为权威测试裁判。

*对齐日期：2026-07-09 ｜ 下一步：逐里程碑交付，每需求带单测 + 测试服黑盒验证。*
