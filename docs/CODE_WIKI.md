# EM-WM-Bridge Code Wiki

> **版本**: 1.3.0 | **更新日期**: 2026-07-02 | **环境**: Paper 1.21.4 / Folia | **JDK**: 21

---

## 目录

1. [项目概述](#1-项目概述)
2. [项目整体架构](#2-项目整体架构)
3. [模块职责详解](#3-模块职责详解)
   - [3.1 主插件入口](#31-主插件入口)
   - [3.2 管理器层 (managers)](#32-管理器层-managers)
   - [3.3 AI 子系统 (ai)](#33-ai-子系统-ai)
   - [3.4 配置系统 (config)](#34-配置系统-config)
   - [3.5 事件监听器 (listeners)](#35-事件监听器-listeners)
   - [3.6 自定义事件 (events)](#36-自定义事件-events)
   - [3.7 MechanicsCore 集成 (mechanics)](#37-mechanicscore-集成-mechanics)
   - [3.8 工具类 (utils)](#38-工具类-utils)
4. [关键类与函数说明](#4-关键类与函数说明)
5. [依赖关系](#5-依赖关系)
6. [项目运行方式](#6-项目运行方式)
7. [配置体系](#7-配置体系)
8. [测试体系](#8-测试体系)

---

## 1. 项目概述

**EM-WM-Bridge** 是一个面向 Paper 1.21.4+ / Folia 的 Minecraft 服务端插件，作为 **EliteMobs** 与 **WeaponMechanics** 之间的桥接层，核心目标是为 EliteMobs 精英怪物赋予基于 WeaponMechanics 的火器武器系统，并集成完整的塔科夫式 AI 战斗行为。

### 核心功能

| 功能域 | 说明 |
|--------|------|
| 武器绑定 | 自动检测 EliteMobs 怪物生成，从 WeaponMechanics 生成武器并绑定到怪物手上 |
| 武器射击 | 调用 WeaponMechanics API 实现怪物开火，含弹药消耗、自动换弹、耐久度系统 |
| AI 决策系统 | 8 种战斗状态 + 7 种战术模式，基于塔科夫 AI 行为特征 |
| 双重感知系统 | 视觉曝光条（身体部位射线检测）+ 听觉分级（墙体穿透衰减） |
| 战术行为 | 投掷物（破片/闪光/烟雾）、压制射击、侧翼包抄、战术撤退、掩体寻找 |
| 性格系统 | 8 种塔科夫 AI 性格（COWARD/RECKLESS/CAUTIOUS/AMBUSH/LOOTER/CAPTAIN/FLANKER/SUPPRESSOR） |
| 小队系统 | 自动组队、情报共享、角色分配 |
| Tier 差异化 | 4 种兵种等级（scav/pmc/boss/sniper），独立参数配置 |
| 极限事件 | 恐慌模式、肾上腺素、幸运一击、战术失误等随机事件 |
| 配置系统 | 三级参数继承（怪物配置 → 全局模板 → WM 原生），自动迁移与补全 |

---

## 2. 项目整体架构

### 2.1 包结构

```
com.emwbridge
├── EMWMBridge.java               # 主插件入口
├── ai/                           # AI 子系统
│   ├── AIDecision.java           # AI 决策枚举
│   ├── engine/
│   │   └── TarkovAIEngine.java   # AI 引擎主循环
│   ├── combat/
│   │   ├── AimConvergenceManager.java  # 瞄准收敛管理
│   │   ├── CoverMovement.java          # 战术移动控制
│   │   ├── TarkovTactics.java          # 战术行为决策
│   │   └── ThrowableManager.java       # 投掷物管理
│   ├── perception/
│   │   ├── AIVisionManager.java        # 视觉+听觉综合感知
│   │   ├── VisualPerception.java       # 视觉感知引擎
│   │   ├── AuditoryPerception.java     # 听觉感知引擎
│   │   ├── AlertStage.java             # 三阶段警戒状态机
│   │   ├── ExposureData.java           # 曝光数据模型
│   │   ├── SoundSource.java            # 声源模型
│   │   ├── SoundType.java              # 声音类型枚举
│   │   └── PostureType.java            # 姿态类型枚举
│   ├── events/
│   │   └── AIEventDispatcher.java      # AI 事件分发器
│   ├── faction/
│   │   ├── FactionManager.java         # 阵营管理
│   │   ├── HostilityMatrix.java        # 敌意矩阵
│   │   └── TarkovFaction.java          # 阵营枚举
│   ├── personality/
│   │   ├── PersonalityManager.java     # 性格管理
│   │   └── PersonalityType.java        # 性格类型枚举
│   ├── sound/
│   │   └── SoundEventManager.java      # 声音事件管理
│   └── squad/
│       ├── SquadManager.java           # 小队管理
│       └── SquadRole.java              # 小队角色枚举
├── config/
│   ├── EMWMConfigCache.java            # 配置缓存管理器
│   ├── EMWMWeaponConfig.java           # 武器配置数据模型
│   ├── LuaPowerParser.java             # LuaPower 解析器
│   └── WeaponMetaCache.java            # 武器元数据预缓存
├── events/
│   ├── MobWeaponShootEvent.java        # 怪物射击事件
│   └── TarkovEvent.java                # 极限事件
├── listeners/
│   ├── EliteMobSpawnListener.java      # 怪物生成监听
│   ├── EliteMobCombatListener.java     # 怪物战斗监听
│   ├── PlayerShootListener.java        # 玩家射击监听
│   ├── EMCommandListener.java          # 命令监听
│   └── EMWMReloadListener.java         # 热重载监听
├── managers/
│   ├── ConfigManager.java              # 配置管理
│   ├── FeatureManager.java             # 功能清单管理
│   ├── MobWeaponManager.java           # 怪物武器管理
│   ├── TarkovAIManager.java            # AI 桥接管理
│   └── ExtremeEventManager.java        # 极限事件管理
├── mechanics/
│   ├── EMWMechanics.java               # MechanicsCore 注册器
│   ├── AIAlertMechanic.java            # AI 警戒 Mechanic
│   ├── AIAttackMechanic.java           # AI 攻击 Mechanic
│   ├── AISearchMechanic.java           # AI 搜索 Mechanic
│   └── targeters/
│       ├── EntitiesInVisionTargeter.java   # 视野内实体 Targeter
│       └── EntitiesInHearingTargeter.java  # 听觉范围内实体 Targeter
└── utils/
    ├── DebugManager.java               # 调试管理
    ├── SoundPropagationUtils.java      # 声音传播工具
    └── TacticalUtils.java              # 战术工具
```

### 2.2 分层架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Listeners 层                          │
│  EliteMobSpawnListener / EliteMobCombatListener          │
│  PlayerShootListener / EMCommandListener                 │
│  职责：权限校验、参数校验、事件分发                          │
├─────────────────────────────────────────────────────────┤
│                    Managers 层                           │
│  MobWeaponManager / TarkovAIManager                      │
│  ExtremeEventManager / ConfigManager / FeatureManager    │
│  职责：核心业务逻辑、武器生命周期、AI 控制                  │
├─────────────────────────────────────────────────────────┤
│                    AI 子系统                             │
│  TarkovAIEngine ── 主循环调度                            │
│  ├── AIVisionManager (感知)                              │
│  ├── TarkovTactics (战术决策)                             │
│  ├── AimConvergenceManager (瞄准)                         │
│  ├── CoverMovement (移动)                                │
│  ├── ThrowableManager (投掷物)                           │
│  ├── FactionManager (阵营)                               │
│  ├── PersonalityManager (性格)                           │
│  └── SquadManager (小队)                                 │
├─────────────────────────────────────────────────────────┤
│                    Config 层                             │
│  EMWMConfigCache / EMWMWeaponConfig / WeaponMetaCache    │
│  职责：配置加载、缓存、三级参数继承、校验                    │
├─────────────────────────────────────────────────────────┤
│                    Mechanics 层                          │
│  EMWMechanics / AIAttackMechanic / AIAlertMechanic        │
│  职责：MechanicsCore 自定义 Mechanic/Targeter 注册         │
├─────────────────────────────────────────────────────────┤
│                    Utils 层                              │
│  DebugManager / SoundPropagationUtils / TacticalUtils    │
│  职责：静态工具方法、调试支持                               │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 模块职责详解

### 3.1 主插件入口

#### `EMWMBridge` [主类](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/EMWMBridge.java)

| 属性 | 说明 |
|------|------|
| 继承 | `JavaPlugin` |
| 版本 | 1.3.0 |
| 依赖 | EliteMobs (hard), WeaponMechanics (hard), MechanicsCore (soft) |

**生命周期方法**:

| 方法 | 职责 |
|------|------|
| `onEnable()` | 检测 Folia 环境 → 初始化 ConfigManager → 检查依赖 → 注册 Mechanics → 初始化各 Manager → 注册监听器 → 打印功能清单 |
| `onDisable()` | 停止 TarkovAIManager → 关闭 MobWeaponManager → 关闭 ExtremeEventManager |
| `checkDependencies()` | 验证 EliteMobs 和 WeaponMechanics 已加载 |
| `reloadAll()` | 重新加载配置 → 刷新各 Manager → 重启 AI 引擎 |
| `detectFolia()` | 反射检测 `RegionizedServer` 类，判断是否 Folia 环境 |

**命令处理** (`/emwm`):

| 子命令 | 功能 |
|--------|------|
| `reload` | 重新加载所有配置 |
| `debug [level]` | 设置全局 DEBUG 等级 (OFF/BASIC/DETAILED/TRACE) |
| `debug entity <player> [level]` | 设置实体级 DEBUG |
| `stats` | 显示活动 AI 数、武器绑定数、射击次数等统计 |
| `version` | 显示版本和配置信息 |
| `track [level]` | 跟踪玩家附近实体 |
| `info [怪物ID]` | 查看怪物 EMWM 配置缓存详情 |

---

### 3.2 管理器层 (managers)

#### `MobWeaponManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/managers/MobWeaponManager.java)

怪物武器生命周期管理的核心类，维护 `ConcurrentHashMap<UUID, MobWeaponInstance>` 武器缓存。

**核心方法**:

| 方法 | 职责 |
|------|------|
| `reload()` | 从 config.yml 加载武器池（scav/pmc/boss）、耐久度配置 |
| `bindWeapon(entity, weaponTitle)` | 旧版武器绑定（从 WeaponMechanics 生成武器物品） |
| `bindWeaponWithConfig(entity, weaponTitle, config)` | **新版武器绑定**，支持三级参数优先级（EMWM 配置 → 模板 → WM 原生） |
| `shoot(entity, target)` | 调用 `WeaponMechanicsAPI.shoot()` 执行射击，消耗弹药与耐久 |
| `shoot(entity, target, ads)` | 带 ADS 标记的射击 |
| `reload(entity)` | 异步换弹，延迟后恢复弹药 |
| `weaponExists(weaponTitle)` | 校验 WM 中是否存在该武器 |
| `validateGrenadeTypesAgainstWM()` | 过滤无效 WM 手雷类型 |
| `getRandomWeaponForTier(tier)` | 按 tier 从配置池随机抽取武器 |
| `shutdown()` | 清空武器缓存 |

**内部类 `MobWeaponInstance`**:

| 字段 | 说明 |
|------|------|
| `weaponTitle` | 武器 ID |
| `currentDurability / maxDurability` | 当前/最大耐久度 |
| `broken` | 武器是否损坏 |
| `magazineSize / currentAmmo` | 弹匣容量 / 当前弹药 |
| `reloadTicks` | 换弹所需 tick |
| `fireRateMs` | 射击间隔（毫秒） |
| `baseSpread / adsSpreadMultiplier` | 基础散布 / ADS 散布倍率 |
| `canShoot()` | 综合判断：未损坏、弹药充足、未换弹、冷却已过 |

#### `TarkovAIManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/managers/TarkovAIManager.java)

AI 子系统桥接层，对外暴露 API，内部委托给 `TarkovAIEngine`。

**核心方法**: `start()`, `stop()`, `restart()`, `registerMob()`, `unregisterMob()`, `isActive()`, `getActiveCount()`

**枚举定义**:
- `CombatState`: IDLE / SEARCHING / APPROACHING / ENGAGING / CLOSING_IN / TACTICAL_RETREAT / FLEEING
- `Tactic`: BERSERKER / SUPPRESSING / BARRAGE / PRECISE / PEEKING / STALKING / SNIPING
- `FireMode`: SINGLE / BURST / AUTO

#### `ExtremeEventManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/managers/ExtremeEventManager.java)

极限事件管理，维护每个实体的 `ExtremeState`（受伤次数、恐慌等级、暴露时间等）。

**事件类型**:

| 事件 | 触发条件 | 效果 |
|------|----------|------|
| 恐慌模式 (Panic) | 连续受伤 3 次 + 恐慌等级 > 0.3 | 攻速 ×2.0，速度 ×0.8 |
| 肾上腺素 (Adrenaline) | 血量 < 阈值 (25%) | 攻速 +50%，速度 +30%，持续 3s |
| 战术失误 (Mistake) | 暴露 > 5s 或移动过多 | 速度 ×0.6，持续 3s |
| 幸运一击 (Luck Shot) | 射击时随机触发 | 精度加成 |

**速度/射速修正**: `getSpeedModifier()` / `getFireRateModifier()`

#### `ConfigManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/managers/ConfigManager.java)

配置加载、迁移、校验。

| 方法 | 职责 |
|------|------|
| `loadAndMigrate()` | 保存默认配置 → 版本检查 → 迁移 |
| `migrateConfig(fromVersion)` | 备份旧配置 → 合并新键 → 保存 |
| `validateConfig()` | 补全缺失的必要字段（debug、tier-settings、extreme-events） |

#### `FeatureManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/managers/FeatureManager.java)

功能清单管理，启动时以表格形式打印 9 大功能域的 ✅/⬜ 状态。

**功能分类**（总计 50+ 功能项）:

| 分类 | 关键功能 |
|------|----------|
| 武器系统 | 武器绑定、射击、弹药系统、自动换弹、耐久度、武器故障、WM 参数读取、武器池 |
| AI 决策系统 | 目标选择、视线检测、反应延迟、8 种战斗状态、决策优先级、智能寻敌 |
| 战术行为 | BERSERKER/SUPPRESSING/BARRAGE/PRECISE/PEEKING/STALKING 等 7 种战术 + 掩体寻找、撤退换弹、侧翼移动 |
| Tier 差异化 | Scav/PMC/Boss 各 13 个独立参数（射速/精度/反应延迟/射程/开镜/开火模式/激进度等） |
| 极限事件 | Panic Mode、Adrenaline、Luck Shot、Tactical Mistake |
| 配置系统 | 版本控制、自动迁移、默认值补全、配置备份 |
| DEBUG 系统 | 4 级分级、实体级调试、计数器系统、防刷屏 |
| 命令系统 | `/emwm reload/debug/stats/version/track/info` |
| 事件系统 | MobWeaponShootEvent、TarkovEvent、Metadata 通信、Lua 元数据支持 |

---

### 3.3 AI 子系统 (ai)

#### `TarkovAIEngine` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/engine/TarkovAIEngine.java)

AI 引擎主循环，是 AI 子系统的核心调度器。

**架构**:

```
TarkovAIEngine (主循环, 每 aiTickRate tick 执行)
├── activeMobs: Map<UUID, AIState>      # 活动怪物缓存
├── aiVisionManager: AIVisionManager    # 感知系统
├── aimConvergenceManager               # 瞄准收敛
├── tactics: TarkovTactics              # 战术决策
├── coverMovement: CoverMovement         # 移动控制
├── throwableManager: ThrowableManager  # 投掷物
├── factionManager: FactionManager      # 阵营
├── personalityManager: PersonalityManager # 性格
├── squadManager: SquadManager          # 小队
└── soundEventManager: SoundEventManager # 声音事件
```

**主循环 `tickEntity()` 流程**:

1. 禁用原版 AI 目标（距离 ≥ 近战阈值时 `setTarget(null)`）
2. 更新视觉曝光 (`aiVisionManager.tickExposure()`)
3. 获取主目标 (`getPrimaryTarget()`)
4. 仇恨目标回退 (`AlertStage.getHatredTarget()`)
5. 无目标时：搜索 → 巡逻
6. 有目标时：阵营关系检查 → 性格决策 → 瞄准收敛 → 战术决策 → 执行战术动作
7. 开阔地掩体寻找

**模式切换**:
- **独立 AI 模式** (默认): 使用 Bukkit/Folia 定时器 `runTaskTimer`，每 `aiTickRate` tick 执行
- **EliteMobs 事件驱动模式**: 由 EliteMobs 事件触发，不启动定时器

#### `AIVisionManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/perception/AIVisionManager.java)

视觉+听觉综合感知管理器，核心数据结构：

```
exposureCache:     Map<AI_UUID, Map<Player_UUID, ExposureData>>
alertStages:       Map<AI_UUID, Map<Player_UUID, AlertStage>>
primaryTargetMap:  Map<AI_UUID, Player_UUID>
```

**核心方法**:

| 方法 | 职责 |
|------|------|
| `tickExposure(ai, target)` | 每 tick 更新视觉曝光值 → 状态流转 → 更新主目标 |
| `flashBlind(ai)` | 闪光弹致盲：清空所有曝光缓存 + 警戒状态 + 仇恨 |
| `receiveSoundEvent(source, listener, squad)` | 处理听觉事件，传播到小队成员 |
| `getAlertStage(aiUuid, playerUuid)` | 获取当前警戒阶段 |
| `getPrimaryTarget(aiUuid)` | 获取首要目标 UUID |

#### `VisualPerception` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/perception/VisualPerception.java)

视觉感知引擎，基于身体部位射线检测的局部暴露计算。

**`calculate()` 曝光计算流程** (8 个步骤):

| 步骤 | 因子 | 说明 |
|------|------|------|
| 1 | 距离衰减 | `1/(1 + distance/(maxRange*0.3))`，最小 0.15 |
| 2 | FOV 扇形 | 全角 140°，目标在视野外返回 0 |
| 3 | 身体部位射线 | 三点检测（头/躯干/腿），植被方块不阻断但衰减 |
| 4 | 姿态倍率 | 站立 1.0 / 潜行 0.6 / 蹲伏 0.5 / 游泳 1.2 |
| 5 | 动作倍率 | 静止 0.5 / 行走 0.8 / 冲刺 2.0 |
| 6 | 环境倍率 | 晴天 1.0 / 夜晚 0.5 / 下雨 0.7 / 雷暴 0.6 / 雾 0.4 |
| 7 | 光照倍率 | 基于目标方块光照等级线性插值，最低 0.35 |
| 8 | 发光加成 | 手电筒 ×2.0 / 激光 ×1.5 |

**射线检测**: 头/躯干/腿三点射线，`FOLIAGE_MATERIALS`（树叶、草、藤蔓等）不阻断但累计衰减，固体方块阻断。

#### `AuditoryPerception` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/perception/AuditoryPerception.java)

听觉感知引擎，基于 SoundType 分级传播 + 方块材质衰减。

**特性**:
- 墙体穿透：`pow(0.7, solidBlockCount)`，不可穿透声源遇墙归零
- 耳机加成：`emwm_headset` metadata ×1.4 全局听觉范围
- 耳鸣状态：`emwm_tinnitus` metadata，听觉倍率 0.2，持续 5s (100 tick)
- 方向一致性加成：3 秒内同方向连续声音 +5.0 曝光
- 脚步冷却：800ms 去重

#### `AlertStage` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/perception/AlertStage.java)

三阶段警戒状态机：

| 阶段 | 曝光阈值 | 行为 |
|------|----------|------|
| YELLOW (黄) | > 0 | 原地警戒、转头、不主动开火 |
| ORANGE (橙) | ≥ 20 | 缓慢推进、找掩体架枪 |
| RED (红) | ≥ 35 | 锁定目标、开火、持续追踪、15s 锁头窗口 |

**全局仇恨缓存**: RED 状态进入时记录目标 UUID + 最后坐标 + 进入时间，脱离 RED 时清除。

#### `TarkovTactics` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/combat/TarkovTactics.java)

战术行为控制器，参照 EFT 0.16 AI 特征实现。

**核心方法**:

| 方法 | 职责 |
|------|------|
| `shouldShoot(uuid, hpRatio, exposure)` | 连发节奏控制：burstSize 发后强制冷却 burstCooldownMs |
| `recordShot(uuid)` | 记录射击，更新连发计数 |
| `decideTacticalAction(...)` | 综合战术决策，返回 TacticalAction 枚举 |
| `enterSuppress(uuid)` / `exitSuppress(uuid)` | 压制模式开关 |
| `isGrenadeTypeAllowed(uuid, action)` | 检查实体手雷白名单 |

**战术决策优先级** (从高到低):

| 条件 | 动作 |
|------|------|
| 血量 < 撤退阈值 | RETREAT + 烟雾 |
| 高血量 + 近距离 | 闪光 + RUSH |
| 目标掩体后 + 多人 | THROW_FRAG |
| 目标掩体后 | THROW_FRAG (低概率) |
| 跨越开阔地 | THROW_SMOKE |
| 长时间对峙 | FLANK |
| 默认 | HOLD (保持位置射击) |

**`TacticalAction` 枚举**: HOLD / THROW_FRAG / THROW_FLASH / THROW_SMOKE / FLANK / RETREAT / RUSH

#### `AimConvergenceManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/combat/AimConvergenceManager.java)

瞄准收敛管理，控制 AI 射击精度随时间提升。

**收敛机制**: `spreadMultiplier = pow(convergenceRate, lockSeconds)`，最小倍率 0.2

**爆头窗口**: 锁定目标后前 `headshotWindowSeconds` 秒内瞄准头部 (eyeLocation)，之后瞄准身体中心

**tier 初始延迟**: scav 1.5s / pmc 0.9s / boss 0.5s / cultist 1.2s

#### `CoverMovement` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/combat/CoverMovement.java)

战术移动控制器。

**移动类型**:

| 方法 | 说明 |
|------|------|
| `standAndAim(entity, target)` | 站立瞄准，停止移动，面向目标 |
| `isBehindCover(entity, target)` | 检测是否在掩体后（左右 1.5 格 + 后方 1 格检测固体方块） |
| `strafeBehindCover(entity, target)` | 掩体后左右探头横移 |
| `moveFlanking(entity, target, progress)` | 侧翼包抄：以目标为圆心大弧绕行，0→90 度 |
| `retreatTowardCover(entity, target)` | 战术撤退：搜索背后 25 格内最佳掩体 |
| `rushToward(entity, target)` | 闪光后冲刺 |
| `moveToNearestCover(entity, target, maxDist)` | 寻找最近掩体并移动 |
| `repositionAfterBurst(entity, target)` | 连发后走位：掩体后探头横移，无掩体沿枪线移动 |

#### `ThrowableManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/combat/ThrowableManager.java)

投掷物管理器，**不破坏地形**，纯粒子 + 直接伤害实现。

**投掷物类型**:

| 类型 | 引信(s) | 半径 | 效果 |
|------|---------|------|------|
| 破片雷 (FRAG) | 3.0 | 5.0 | 距离衰减伤害 (16→4)，粒子爆炸 |
| 闪光弹 (FLASH) | 2.0 | 8.0 | 致盲 + 减速 + 反胃，距离衰减 |
| 烟雾弹 (SMOKE) | 1.0 | 6.0 | 持续 8s 粒子云遮挡视线 |

**实现细节**:
- 使用隐形盔甲架 (ArmorStand) 作为可见投掷物，抛物线飞行
- 碰到方块立即引爆
- 预判目标位置（根据速度偏移 1.5s）
- 冷却时间：破片 15s / 闪光 20s / 烟雾 25s

#### `FactionManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/faction/FactionManager.java)

阵营管理，使用 `HostilityMatrix` 判定实体间敌友关系。

**阵营**: `AI_SCAV` / `AI_PMC` / `AI_BOSS` / `AI_CULTIST` / `PLAYER_SCAV` / `PLAYER_PMC`

**中立转敌对条件**: 距离 < 3 格或被直接攻击

#### `HostilityMatrix` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/faction/HostilityMatrix.java)

6×6 阵营敌意矩阵，使用 `EnumMap<TarkovFaction, Map<TarkovFaction, Relation>>` 实现。

**Relation** 枚举: `HOSTILE` / `NEUTRAL` / `FRIENDLY`

**阵营关系表** (行=自身, 列=目标):

| 自身 \\ 目标 | PLAYER_PMC | PLAYER_SCAV | AI_SCAV | AI_PMC | BOSS | CULTIST |
|-------------|-----------|-----------|-------|--------|-------|---------|
| **AI_SCAV** | HOSTILE | NEUTRAL | FRIENDLY | **80% HOSTILE** | HOSTILE | HOSTILE |
| **AI_PMC** | HOSTILE | NEUTRAL | **80% HOSTILE** | FRIENDLY | HOSTILE | HOSTILE |
| **BOSS** | HOSTILE | HOSTILE | HOSTILE | HOSTILE | FRIENDLY | HOSTILE |
| **CULTIST** | HOSTILE | HOSTILE | HOSTILE | HOSTILE | HOSTILE | FRIENDLY |

**特殊机制**: AI_SCAV ↔ AI_PMC 之间 80% 概率为 HOSTILE, 20% 概率为 NEUTRAL（模拟塔科夫中 scav 与 pmc 的不确定敌对关系）。

#### `PersonalityType` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/personality/PersonalityType.java)

8 种塔科夫 AI 性格，每种有独立的 (aggressiveness, coverPreference, retreatHpThreshold) 三元组：

| 性格 | aggressiveness | coverPreference | retreatHp |
|------|---------------|-----------------|-----------|
| COWARD | 0.1 | 1.0 | 0.5 |
| RECKLESS | 0.9 | 0.1 | 0.15 |
| CAUTIOUS | 0.4 | 0.8 | 0.35 |
| AMBUSH | 0.2 | 0.9 | 0.3 |
| LOOTER | 0.3 | 0.4 | 0.4 |
| CAPTAIN | 0.6 | 0.5 | 0.3 |
| FLANKER | 0.7 | 0.4 | 0.25 |
| SUPPRESSOR | 0.5 | 0.5 | 0.3 |

**决策权重**: `getAttackWeight()` / `getDefendWeight()` / `getAmbushWeight()` / `getRetreatWeight()`

#### `PersonalityManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/personality/PersonalityManager.java)

性格分配与决策引擎，维护 `ConcurrentHashMap<UUID, PersonalityType>` 实体→性格映射。

**核心方法**:
| 方法 | 职责 |
|------|------|
| `reload(config)` | 从 config.yml 加载 `personality.tier-weights` 权重表 |
| `rollByTier(tier)` | 按 tier 加权随机分配性格（scav=胆小, pmc=平衡, boss=激进, cultist=埋伏） |
| `assignPersonality(uuid, type)` | 手动指定实体性格 |
| `decide(uuid, hpRatio, exposure)` | **性格决策入口**：综合 hpRatio + exposure 计算攻击/防守/埋伏/撤退权重 |

**决策逻辑**:
1. 血量 < 15% → 强制 RETREAT
2. 比较 4 种决策权重的最大值，选择最高的动作
3. 血量极低时撤退权重压倒一切

**tier 性格分布参考**:
| tier | 主力性格 (≥30%) | 次要性格 |
|------|----------------|----------|
| scav | COWARD(35%), CAUTIOUS(30%) | LOOTER(25%) |
| pmc | CAUTIOUS(30%), FLANKER(20%) | CAPTAIN(15%), SUPPRESSOR(15%) |
| boss | CAPTAIN(40%), RECKLESS(25%) | SUPPRESSOR(20%), FLANKER(15%) |
| cultist | AMBUSH(40%), FLANKER(30%) | CAUTIOUS(30%) |

#### `SquadManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/squad/SquadManager.java)

小队系统，最大 5 人，20 格内自动组队。

**情报共享**:
- 视觉情报：`shareIntel()` — 共享目标玩家
- 声音情报：`shareSoundIntel()` — 共享声源位置
- 闪光致盲：同小队成员也受闪光影响

**角色分配**: `SquadRole` 枚举（ASSAULT / SUPPORT / SNIPER / RECON / MEDIC），CAPTAIN 性格自动担任队长。

#### `AIEventDispatcher` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/events/AIEventDispatcher.java)

AI 事件总线，基于观察者模式实现跨模块事件广播。

**事件类型** (`EventType` 枚举):
| 类型 | 触发时机 | 数据载荷 |
|------|----------|----------|
| `SIGHT` | 视觉发现目标玩家 | aiEntityUuid, targetPlayerUuid, exposure 值 |
| `SOUND` | 听觉检测到声源 | aiEntityUuid, sourcePlayerUuid, soundLoc, loudness |
| `HOSTILE_LOCK` | 进入 HOSTILE 锁定状态 | aiEntityUuid, targetPlayerUuid, targetLoc |
| `FLASH_BLIND` | 被闪光弹致盲 | aiEntityUuid（无目标信息） |

**注册机制**: `register(EventType, Consumer<AIEvent>)` 为每种事件类型注册消费者列表，`dispatch(AIEvent)` 按类型分发。

**AIEvent 内部类**:
- `type()` — 事件类型
- `aiEntityUuid()` — 触发事件的 AI 实体 UUID
- `targetPlayerUuid()` — 关联目标玩家 UUID
- `location()` — 事件位置
- `value()` — 数值载荷（exposure / loudness 等）

#### `SoundEventManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/sound/SoundEventManager.java)

全局声音事件管理器，实现 `Listener` 接口监听 MC 原生事件，生成 `SoundSource` 并广播至附近 AI。

**监听的事件**:
| MC 事件 | 映射为 SoundType | 说明 |
|---------|-----------------|------|
| `ExplosionPrimeEvent` | EXPLOSION | 爆炸声源, loudness=1.0 |
| `EntityDamageEvent` | THROWABLE | 实体受伤声, loudness=0.5, 不可穿墙 |
| `BlockBreakEvent` | THROWABLE | 方块破坏声, loudness=0.4 |
| `PlayerInteractEvent` | DOOR | 门/活板门交互, loudness=0.8 |
| `PlayerMoveEvent` | FOOTSTEP_WALK/SNEAK/SPRINT | 玩家脚步, 冷却 800ms 去重 |

**外部触发接口**:
- `onGunshot(Location, boolean suppressed)` — 武器射击声（外部系统调用，如 WeaponMechanics）
- `onThrowableLand(Location)` — 投掷物落地声
- `broadcastCustomSound(Location, SoundType, double)` — 通用声源广播

**广播流程**: 收集附近 `emwm_ai_enabled` metadata 的 LivingEntity → 委托 `AIVisionManager.broadcastSound()` 统一处理。

#### `AIDecision` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/ai/AIDecision.java)

AI 顶层决策枚举，4 种状态：
- `ENGAGE` — 主动进攻
- `DEFEND` — 防守/架枪
- `AMBUSH` — 埋伏/等待
- `RETREAT` — 战术撤退

---

### 3.4 配置系统 (config)

#### 三级参数优先级

```
┌──────────────────────────────────────────┐
│ 1. 怪物 EMWM 配置 (显式填写的非 null 值)     │  最高优先级
├──────────────────────────────────────────┤
│ 2. 全局兵种模板 (emwm_mob_templates.yml)   │
├──────────────────────────────────────────┤
│ 3. WM 武器原生参数 (WeaponMetaCache 缓存)   │
├──────────────────────────────────────────┤
│ 4. 安全兜底默认值                           │  最低优先级
└──────────────────────────────────────────┘
```

#### `EMWMConfigCache` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/config/EMWMConfigCache.java)

配置缓存管理器，核心数据结构：

```
mobConfigs:         Map<文件名, EMWMWeaponConfig>    # 怪物配置
globalTemplates:    Map<模板名, EMWMWeaponConfig>    # 全局模板
templateInheritance: Map<文件名, 模板名>              # 继承关系
```

**加载流程**:
1. `loadGlobalTemplates()` — 加载 `emwm_mob_templates.yml`
2. `loadMobConfigs()` — 递归遍历 EliteMobs `custombosses/` 目录
3. `preloadWeaponsFromConfigs()` — 收集所有武器 ID，预加载 WM 元数据

**配置获取 `getConfig(mobFileName)`**:
1. 怪物自身有 emwm 配置段 → 直接返回
2. 有继承模板 → 返回模板配置
3. LuaPower 兜底 → 从 powers 脚本解析
4. 都无 → 返回 null

**模板字段映射** (扁平结构兼容):
- `weapon` → `weapon-pool`（单武器）
- `fireRateTicks` → `shooting.fire-rate`（反向换算）
- `maxRange` / `spread` / `damageMultiplier` 等

#### `EMWMWeaponConfig` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/config/EMWMWeaponConfig.java)

武器配置数据模型，所有字段使用包装类型（Integer/Double/Boolean），null 表示未设置。

**字段分类**:

| 分类 | 字段 |
|------|------|
| 武器池 | weaponPool, weaponWeights |
| 弹药 | magazineSize, reloadDuration, autoReload, ammoType, reserveAmmo |
| 射击 | fireRate, spread, adsSpreadMultiplier, effectiveRange, maxRange, adsRangeThreshold |
| 战术 | meleeRange, standAndShoot, suppressHpThreshold, retreatHpThreshold |
| 行为 | aggressiveness, coverUsage, searchDuration |
| 投掷物 | fragInterval, flashInterval, smokeInterval, throwIfCover, throwMinRange, throwMaxRange |
| 特殊能力 | callReinforcements, squadLeader, preferLongRange, preferRush |
| 扩展射击 | fireMode, projectileSpeed, bulletPenetration, suppressed, onlyAimShoot, recoil |
| 扩展动作 | equipDelay, aimDelay, equipAnimation, aimAnimation, reloadAnimation, shootAnimation |
| 扩展耐久 | durabilityPerShot, breakOnZeroDurability, attachments |
| 扩展投掷物 AI | enableGrenadeAI, grenadeType, grenadeMaxRange, grenadeCooldownTicks, allowedGrenadeTypes |

**核心方法**:
- `getRandomWeapon()` — 加权随机选择武器
- `validate()` — 校验并修正非法值
- `mergeWithTemplate(template)` — null 字段从模板继承
- `isFieldExplicitlySet(fieldName)` — 判断字段是否显式设置（用于三级优先级）

#### `WeaponMetaCache` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/config/WeaponMetaCache.java)

全局武器元数据预缓存，启动时从 WM 读取所有武器参数到内存。

**缓存字段** (NativeWeaponData):
`fireRateMs`, `magazineSize`, `reloadDuration`, `baseSpread`, `adsSpreadMultiplier`, `projectileSpeed`, `bulletPenetration`, `recoilPitch`, `recoilYaw`, `maxRange`, `suppressed`, `equipDelay`, `aimDelay`, `fireMode`

**延迟重试**: 首轮加载失败的武器，60 tick 后重试（等待 WM 就绪）。

#### `LuaPowerParser` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/config/LuaPowerParser.java)

当怪物既无 EMWM 显式配置也无全局模板时，作为**最后降级策略**，从 EliteMobs 的 LuaPower 脚本中扫描 WM 武器参数。

**工作流程**:
1. 从怪物 yml 的 `powers:` 列表获取所有 Lua 脚本名
2. 尝试 6 种路径模式查找 `.lua` / `.yml` 文件（`powers/`, `content/powers/`, `custombosses/powers/`）
3. 正则解析 Lua 中的 `weapon=`, `fire_rate=`, `range=`, `spread=`, `damage=` 等参数
4. 解析结果写入 `EMWMWeaponConfig` 并加入 `luaCache` 缓存

**正则模式**:
- `weapon\s*=\s*["']([^"']+)["']` — 提取武器名称
- `fire_rate\s*=\s*(\d+\.?\d*)` — 射速
- `range\s*=\s*(\d+)` — 射程（`maxRange = range + 15` 兜底）
- `spread\s*=\s*(\d+\.?\d*)` — 散布精度
- `damage\s*=\s*(\d+\.?\d*)` — 伤害 → 映射为 `aggressiveness`（`min(1.0, damage/20)`）

**限制**: 仅能提取基础参数，不支持投掷物/特殊能力等复杂配置。

---

### 3.5 事件监听器 (listeners)

#### `EliteMobSpawnListener` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/listeners/EliteMobSpawnListener.java)

怪物生成监听，核心入口。

**绑定决策流程** (3 阶段):
1. **阶段 1**: 优先从 EMWM 配置缓存匹配（按自定义名称/配置文件名/tier）
2. **阶段 2**: 回退 tier-based 逻辑（检测 `tarkov_tier` metadata 或名称关键词）
3. **阶段 3**: 延迟重试（5 tick 后，适用于 EliteMobs 异步设名）

**Tier 检测规则** (`detectTarkovMobTier`):
- 包含 "boss"/"legendary"/"raid" → boss
- 包含 "pmc"/"elite"/"tier" → pmc
- 包含 "scav"/"raider"/"tarkov" → scav
- 有 `elitemobs` metadata → scav（默认）

**防覆盖机制**: 绑定后 20 tick 检查主手，若被 EliteMobs 覆盖则重新绑定。

#### `EliteMobCombatListener` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/listeners/EliteMobCombatListener.java)

怪物战斗监听，处理 `EntityTargetLivingEntityEvent`。

**事件驱动射击**: 每 4 tick 检查目标，有视线时按射速射击，弹匣空时自动换弹。

#### `PlayerShootListener` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/listeners/PlayerShootListener.java)

玩家射击监听，通过反射注册 WeaponMechanics `PlayerShootEvent`，触发 AI 听觉仇恨。

**消音检测**: 武器 ID 包含 "suppress"/"silent"/"quiet" 判定为消音。

#### `EMCommandListener` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/listeners/EMCommandListener.java)

监听 `/elitemobs reload` 和 `/em reload`，触发 EMWM 配置缓存热重载（延迟 10 tick）。

#### `EMWMReloadListener` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/listeners/EMWMReloadListener.java)

通过反射监听 EliteMobs 重载事件，触发配置缓存刷新。

---

### 3.6 自定义事件 (events)

| 事件类 | 说明 |
|--------|------|
| `MobWeaponShootEvent` | 怪物射击事件，携带实体、目标、武器 ID、ADS 状态 |
| `TarkovEvent` | 极限事件（PANIC_MODE / ADRENALINE / LUCK_SHOT / MALFUNCTION / TACTICAL_MISTAKE），可被取消 |

---

### 3.7 MechanicsCore 集成 (mechanics)

#### `EMWMechanics` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/mechanics/EMWMechanics.java)

MechanicsCore 自定义 Mechanic/Targeter 注册器。

**注册的 Mechanic**:
- `AI_ATTACK` (AIAttackMechanic) — AI 攻击机制
- `AI_SEARCH` (AISearchMechanic) — AI 搜索机制
- `AI_ALERT` (AIAlertMechanic) — AI 警戒机制

**注册的 Targeter**:
- `ENTITIES_IN_VISION` (EntitiesInVisionTargeter) — 视野内实体
- `ENTITIES_IN_HEARING` (EntitiesInHearingTargeter) — 听觉范围内实体

---

### 3.8 工具类 (utils)

#### `DebugManager` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/utils/DebugManager.java)

4 级调试管理器：OFF(0) / BASIC(1) / DETAILED(2) / TRACE(3)

**特性**:
- 防刷屏：相同消息 500ms 内只输出一次
- 实体级调试：可针对特定实体设置独立 DEBUG 等级
- 计数器系统：`incrementCounter()` / `getCounter()` 统计事件次数
- 战术/事件/性能专项调试方法

#### `SoundPropagationUtils` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/utils/SoundPropagationUtils.java)

声学传播计算工具类（纯静态方法，禁止实例化）。

| 方法 | 公式 | 说明 |
|------|------|------|
| `calculateSoundRadius(baseRadius, suppressor)` | 消音器时 `×0.3` | 计算有效听觉半径 |
| `getAwarenessChance(distance, radius, awareness)` | `(1 - dist/radius) × awareness` | 距离衰减的察觉概率 |
| `getAlertLevel(distance, radius)` | 三级分段 | 返回 `CHARGE`(<30%) / `ALERT`(<60%) / `SEARCH`(≥60%) |
| `calculateSoundDirection(player, source)` | `source - player.eye` 归一化 | 计算声源方向向量 |

**AlertLevel 枚举**:
| 等级 | 距离比 | 响应倍率 |
|------|--------|----------|
| CHARGE | < 30% | 1.0（立即反应） |
| ALERT | 30%~60% | 0.6 |
| SEARCH | > 60% | 0.3（仅搜索方向） |

#### `TacticalUtils` [源码](file:///f:/LOAXXjavaproject/EM-WM-Bridge/src/main/java/com/emwbridge/utils/TacticalUtils.java)

战术辅助工具类（纯静态方法，禁止实例化）。

| 方法 | 说明 |
|------|------|
| `findCoverLocation(entity, target, minDist, maxDist)` | 8 方向扫描掩体位置：远离目标方向 ±45° 辐射搜索，优先找有实体方块的掩体 |
| `hasLineOfSight(from, to)` | Bresenham 步进射线检测，步长 0.5 格，遇固体方块阻断 |
| `isInWorldBorder(loc, world)` | 世界边界检测（边界 + 16 格缓冲区） |

**掩体搜索算法**:
1. 计算远离目标的方向向量
2. 8 方向（每 45°）辐射扫描，距离从 minDist 递增至 maxDist（步长 2 格）
3. 检查候选点：脚部和头部必须是空气、脚下方块必须为实体、必须遮挡目标视线
4. 未找到时安全兜底：直接远离目标 minDist 格

---

## 4. 关键类与函数说明

### 4.1 核心调用链

```
怪物生成 (CreatureSpawnEvent)
  → EliteMobSpawnListener.onCreatureSpawn()
    → EMWMConfigCache.getConfig() / matchConfigByName()
    → MobWeaponManager.bindWeaponWithConfig() / bindWeapon()
    → TarkovAIManager.registerMob()
      → TarkovAIEngine.registerMob()
        → AIVisionManager.registerMob()
        → FactionManager.assignByTier()
        → PersonalityManager.rollByTier()
        → SquadManager.tryJoin()
        → AimConvergenceManager.registerMob()
        → TarkovTactics.registerMob()

AI 主循环 (每 aiTickRate tick)
  → TarkovAIEngine.tickEntity()
    → AIVisionManager.tickExposure()
      → VisualPerception.calculate()
    → AIVisionManager.getPrimaryTarget()
    → PersonalityManager.decide() → AIDecision
    → AimConvergenceManager.update() → AimResult
    → TarkovTactics.decideTacticalAction() → TacticalAction
    → executeTacticalAction()
      → CoverMovement (移动)
      → ThrowableManager (投掷物)
      → MobWeaponManager.shoot() (射击)

怪物死亡 (EntityDeathEvent)
  → EliteMobSpawnListener.onEntityDeath()
    → MobWeaponManager.unbindWeapon()
    → TarkovAIManager.unregisterMob()
```

### 4.2 数据流图

```
config.yml ──────► ConfigManager ──► 各 Manager 配置
EliteMobs yml ───► EMWMConfigCache ──► EMWMWeaponConfig
WeaponMechanics ─► WeaponMetaCache ──► NativeWeaponData
                                        │
                    三级参数解析 ◄────────┘
                         │
                    MobWeaponManager
                         │
                    MobWeaponInstance
                         │
                    WeaponMechanicsAPI.shoot()
```

### 4.3 关键设计模式

| 模式 | 应用场景 |
|------|----------|
| 单例 (Singleton) | `EMWMBridge.getInstance()` 全局访问 |
| 桥接 (Bridge) | `TarkovAIManager` → `TarkovAIEngine` 委托 |
| 策略 (Strategy) | `PersonalityType` 不同性格的决策权重 |
| 状态机 (State) | `AlertStage` 三阶段警戒 + `CombatState` 7 种战斗状态 |
| 观察者 (Observer) | `AIEventDispatcher` 事件总线 |
| 缓存 (Cache) | `WeaponMetaCache` 预缓存 + `EMWMConfigCache` 配置缓存 |
| 模板方法 | `VisualPerception.calculate()` 8 步固定流程 |
| 外观 (Facade) | `TarkovAIManager` 统一封装 AI 子系统所有能力 |
| 组件 (Component) | `TarkovAIEngine` 组合 8 个子模块（感知/战术/瞄准/移动等） |
| 工厂 (Factory) | `EMWMConfigCache` 根据配置类型创建 EMWMWeaponConfig |

---

## 5. 依赖关系

### 5.1 外部依赖

| 依赖 | 类型 | 版本 | 说明 |
|------|------|------|------|
| Paper API | compileOnly | 1.21.4-R0.1-SNAPSHOT | 服务端 API |
| WeaponMechanics | compileOnly | 4.3.0 | 武器系统核心 |
| MechanicsCore | compileOnly | 4.3.0 | 自定义 Mechanic 框架 |
| EliteMobs | compileOnly | (本地) | 精英怪物框架 |
| JetBrains Annotations | compileOnly | 24.0.1 | 代码注解 |
| JUnit Jupiter | testImplementation | 5.10.2 | 单元测试框架 |
| Mockito | testImplementation | 5.12.0 | Mock 框架 |
| JaCoCo | gradle plugin | 0.8.12 | 代码覆盖率 |

### 5.2 插件依赖关系

```
EM-WM-Bridge
├── [hard] EliteMobs        # 必须安装
├── [hard] WeaponMechanics   # 必须安装
└── [soft] MechanicsCore     # 可选，提供自定义 Mechanic/Targeter
```

### 5.3 内部模块依赖

```
EMWMBridge (主类)
├── ConfigManager              # 配置加载
├── EMWMConfigCache            # 配置缓存
│   ├── WeaponMetaCache        # 武器元数据
│   └── LuaPowerParser         # Lua 解析
├── MobWeaponManager           # 武器管理
├── TarkovAIManager            # AI 桥接
│   └── TarkovAIEngine         # AI 引擎
│       ├── AIVisionManager    # 感知 (依赖 VisualPerception + AuditoryPerception)
│       ├── TarkovTactics      # 战术 (依赖 MobWeaponManager)
│       ├── CoverMovement      # 移动
│       ├── ThrowableManager   # 投掷物
│       ├── AimConvergenceManager # 瞄准
│       ├── FactionManager     # 阵营
│       ├── PersonalityManager # 性格
│       ├── SquadManager       # 小队
│       └── SoundEventManager  # 声音事件
├── ExtremeEventManager        # 极限事件
├── FeatureManager             # 功能清单
├── DebugManager               # 调试
└── EMWMechanics               # MechanicsCore 注册
```

---

## 6. 项目运行方式

### 6.1 构建配置

| 文件 | 说明 |
|------|------|
| [build.gradle](file:///f:/LOAXXjavaproject/EM-WM-Bridge/build.gradle) | Groovy DSL 构建脚本，JDK 21, JaCoCo 覆盖率 ≥ 70%, deploy 任务 |
| [settings.gradle](file:///f:/LOAXXjavaproject/EM-WM-Bridge/settings.gradle) | 项目名称 `EM-WM-Bridge` |
| [gradle/libs.versions.toml](file:///f:/LOAXXjavaproject/EM-WM-Bridge/gradle/libs.versions.toml) | 版本目录（Paper API, JUnit, Mockito 等版本管理） |

**本地依赖**（compileOnly + testImplementation）:
- `libs/WeaponMechanics-4.3.0.jar`
- `libs/MechanicsCore-4.3.0.jar`
- `libs/EliteMobs.jar`

### 6.2 构建

```bash
# 项目根目录
cd EM-WM-Bridge

# 使用 Gradle Wrapper 构建
./gradlew build

# 构建产物
# build/libs/EM-WM-Bridge-1.3.0.jar

# 运行全部测试 + 生成覆盖率报告
./gradlew test

# 查看覆盖率报告
# build/reports/jacoco/test/html/index.html

# 覆盖率门禁（指令覆盖率 ≥ 70%）
./gradlew jacocoTestCoverageVerification
```

### 6.3 部署

```bash
# 编译 + 自动部署到测试服务器 plugins 目录
./gradlew deploy

# 编译 + 部署 + 提示启动测试命令
./gradlew deployAndTest
```

**手动部署步骤**:
1. 将 `build/libs/EM-WM-Bridge-1.3.0.jar` 放入服务端 `plugins/` 目录
2. 确保已安装 `EliteMobs` 和 `WeaponMechanics` 插件
3. 启动服务器，插件自动生成 `config.yml` 到 `plugins/EM-WM-Bridge/`
4. （可选）创建 `plugins/EM-WM-Bridge/emwm_mob_templates.yml` 配置全局兵种模板
5. 在 EliteMobs 怪物配置中添加 `emwm:` 配置段

### 6.4 真机黑盒测试

[test-server.ps1](file:///f:/LOAXXjavaproject/EM-WM-Bridge/test-server.ps1) 提供完整的 PowerShell 黑盒测试脚本：

```powershell
# 先部署插件
./gradlew deploy

# 运行真机测试（自动启动 Paper 服务器、执行测试场景、生成报告、关闭服务器）
./test-server.ps1
```

**测试场景**:
| 场景 | 验证内容 |
|------|----------|
| 插件加载 | `emwm version` 返回版本信息 |
| 配置热重载 | `emwm reload` 返回成功消息 |
| 统计命令 | `emwm stats` 返回统计信息 |
| 配置缓存 | `emwm info` 返回缓存详情 |
| 启动无错误 | 日志中无 EM-WM 相关 ERROR/Exception |
| Scav 武器绑定 | 生成怪物后日志检测武器绑定信息 |

**测试目录结构**:
```
src/test/java/com/emwbridge/
├── ai/combat/     # AimConvergenceManager / CoverMovement / TarkovTactics / ThrowableManager 测试
├── ai/engine/     # TarkovAIEngine 测试
├── ai/events/     # AIEventDispatcher 测试
├── ai/faction/    # HostilityMatrix 测试
├── ai/perception/ # AIVisionManager / AlertStage / ExposureData 测试
├── ai/sound/      # SoundEventManager 测试
├── config/        # EMWMConfigCache / EMWMWeaponConfig 测试
├── events/        # MobWeaponShootEvent 测试
├── harness/       # HarnessIntegrationTest / PerceptionHarness / ScenarioHarness
├── listeners/     # EliteMobSpawnListener 测试
├── managers/      # ConfigManager / ExtremeEventManager / MobWeaponManager 测试
└── utils/         # DebugManager / SoundPropagationUtils / TacticalUtils 测试
```

### 6.4 命令参考

| 命令 | 权限 | 说明 |
|------|------|------|
| `/emwm` | - | 显示帮助 |
| `/emwm reload` | `emwm.admin` | 重新加载配置 |
| `/emwm debug [level]` | `emwm.admin` | 设置 DEBUG 等级 |
| `/emwm debug entity <player> [level]` | `emwm.admin` | 实体级 DEBUG |
| `/emwm stats` | - | 显示统计信息 |
| `/emwm version` | - | 显示版本 |
| `/emwm track [level]` | - | 跟踪实体 |
| `/emwm info [怪物ID]` | `emwm.admin` | 查看怪物配置 |

---

## 7. 配置体系

### 7.1 配置文件清单

| 文件 | 位置 | 说明 |
|------|------|------|
| `config.yml` | `plugins/EM-WM-Bridge/` | 主配置文件 |
| `emwm_mob_templates.yml` | `plugins/EM-WM-Bridge/` | 全局兵种模板（可选） |
| `plugin.yml` | jar 内 | 插件元数据 |
| EliteMobs 怪物 yml | `plugins/EliteMobs/custombosses/` | 怪物配置中的 `emwm:` 段 |

### 7.2 config.yml 主要配置段

| 配置段 | 关键参数 |
|--------|----------|
| `settings` | debug, ai-tick-rate(4), far-distance-threshold(40), use-elitemobs-events |
| `tactical` | stand-and-shoot, restrict-movement, hipfire-range(15), reposition-between-bursts(0.35) |
| `extreme-events` | panic-mode-chance(0.02), luck-shot-chance(0.05), malfunction-chance(0.03), tactical-mistake-chance(0.08), adrenaline-chance(0.10) |
| `perception` | 视觉 8 因子 + 听觉 6 因子 |
| `squad` | enabled, max-size(5), intel-share-range(50) |
| `personality` | 4 个 tier 各 8 种性格权重分布 |
| `aim` | headshot-window-seconds(15), convergence-rate(0.85), min-spread-multiplier(0.2), vision-loss-reset-seconds(2.0) |
| `tier-settings` | scav/pmc/boss/sniper 各 13 个独立参数 |
| `weapons` | default-weapon, scav-pool, pmc-pool, boss-pool, base-durability(100) |
| `durability` | enabled, decay-per-shot(1), malfunction-chance-threshold(0.2), accuracy-penalty-per-10-percent(0.02) |
| `tactics` | burst-size(5), burst-cooldown-ms(1200), 投掷物决策, 压制/侧翼/撤退/冲锋参数 |
| `throwables` | frag/flash/smoke 参数 + 冷却时间 |

### 7.3 EMWM 怪物配置段示例

```yaml
# EliteMobs 怪物 yml 中
emwm:
  weapon-pool:
    - AK_47
    - M4A1
  weapon-weight:
    AK_47: 0.7
    M4A1: 0.3
  shooting:
    fire-rate: 4.0        # shots/second
    spread: 3.0           # 度
    max-range: 45         # 格
    effective-range: 30   # 格
  ammo:
    magazine-size: 30
    reload-duration: 60   # tick
  tactics:
    melee-range: 3.0
    stand-and-shoot: true
    suppress-hp-threshold: 0.5
    retreat-hp-threshold: 0.3
  behavior:
    aggressiveness: 0.6
    cover-usage: 0.4
  special:
    call-reinforcements: false
    squad-leader: false
```

### 7.4 全局模板示例

```yaml
# emwm_mob_templates.yml
templates:
  scav_rifle:
    weapon: AUG
    fireRateTicks: 6
    maxRange: 38
    spread: 0.16
    damageMultiplier: 1.0
    cancelReloadOnMove: false
    meleeSwitchHealthPercent: 0.3
    enableGrenadeAI: false
    allowedGrenadeTypes:
      - frag
      - flashbang
```

---

## 8. 测试体系

### 8.1 测试框架

- **JUnit 5** (Jupiter): 测试框架
- **Mockito 5**: Mock 依赖
- **JaCoCo 0.8.12**: 代码覆盖率
- **MockBukkit**: (待集成) Bukkit 插件测试 Harness

### 8.2 覆盖率要求

| 指标 | 门禁 |
|------|------|
| 指令覆盖率 | ≥ 70% |
| 排除类 | Config / Constants / Exception / Event 类 |

### 8.3 测试覆盖范围

| 测试类 | 覆盖模块 |
|--------|----------|
| `AimConvergenceManagerTest` | 瞄准收敛逻辑 |
| `CoverMovementTest` | 战术移动 |
| `TarkovTacticsTest` | 战术决策、连发节奏 |
| `ThrowableManagerTest` | 投掷物管理 |
| `TarkovAIEngineTest` | AI 引擎主循环 |
| `AIEventDispatcherTest` | 事件分发 |
| `HostilityMatrixTest` | 敌意矩阵 |
| `AIVisionManagerTest` | 感知管理 |
| `AlertStageTest` | 警戒状态机 |
| `ExposureDataTest` | 曝光数据模型 |
| `SoundEventManagerTest` | 声音事件 |
| `EMWMConfigCacheTest` | 配置缓存 |
| `EMWMWeaponConfigTest` | 武器配置模型 |
| `MobWeaponShootEventTest` | 射击事件 |
| `EliteMobSpawnListenerTest` | 生成监听 |
| `ConfigManagerTest` | 配置管理 |
| `ExtremeEventManagerTest` | 极限事件 |
| `MobWeaponInstanceTest` | 武器实例 |
| `MobWeaponManagerTest` | 武器管理（含武器绑定参数动态传递） |
| `MobWeaponManagerIntegrationTest` | 武器管理器集成测试（完整生命周期） |
| `MobWeaponInstanceStateTest` | 武器实例状态迁移测试 |
| `IntegrationTestBase` | 集成测试基类（公共初始化） |
| `DebugManagerTest` | 调试管理 |
| `SoundPropagationUtilsTest` | 声音传播 |
| `TacticalUtilsTest` | 战术工具 |
| `HarnessIntegrationTest` | 集成测试 Harness |
| `PerceptionHarness` | 感知系统 Harness（视觉+听觉场景测试） |
| `ScenarioHarness` | 场景 Harness（多情境组合测试） |

---

> **文档版本**: 2.0 | **最后更新**: 2026-07-02 | **对应插件版本**: 1.3.0