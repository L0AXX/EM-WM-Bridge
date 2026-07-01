# Tarkov AI 重构设计文档（Minecraft 1.21.4 适配版）

> 基于 Escape from Tarkov 0.16 AI 机制，映射到 Minecraft Paper 1.21.4 可实现的能力边界。

---

## 零、Minecraft 能力边界（塔科夫概念 → MC 等价物）

| 塔科夫概念 | MC 1.21.4 等价物 | 可实现 |
|-----------|-----------------|--------|
| 站立/蹲姿/卧倒 | 站立 / 潜行(sneaking) / 无卧倒 | 潜行=降低曝光 |
| 奔跑/慢走/静步 | 冲刺(sprinting) / 步行 / 潜行 | 三者可区分 |
| 雨天/大雾/黑夜 | 下雨/雷暴 hasStorm() / world.getTime()判断夜间 / 无雾 | 雨+夜可检测 |
| 植被遮挡 | ❌ MC草地/树叶不阻挡 hasLineOfSight | 不可实现，跳过 |
| 全身/半身/四肢暴露 | ❌ MC玩家统一碰撞箱 | 不可实现，跳过 |
| 闪光弹致盲 | ❌ 无原生闪光弹，但可用药水效果 | 后续考虑 |
| 消音器/亚音速弹 | WeaponMechanics 枪械附件 | WM API 可查询 |
| 耳机 | metadata 标记(emwm_headset) | 元数据标记 |
| 手术/止痛药 | ❌ MC无对应 | 跳过 |
| 锁头 | 瞄准 eyeLocation（头部）vs 身体中心 | 可区分两种瞄准点 |
| 可见部位瞄准 | rayTrace 检查 eyeLocation 和身体 center 的命中 | 可简化实现 |
| 开门/翻物资 | PlayerInteractEvent / ContainerOpenEvent | 可监听 |
| 手雷/爆炸 | ExplosionPrimeEvent / TNTPrimed | 可监听 |

---

## 一、目标与范围

将现有耦合在 `TarkovAIManager` 中的单体 AI 逻辑，拆分为 6 个独立子系统，每个子系统职责单一、可独立测试，通过 `TarkovAIEngine` 协调运行。

**不做的事：**
- 不修改 EliteMobs/WeaponMechanics 桥接逻辑（武器绑定、弹药、事件监听不变）
- 不修改现有 config.yml 中已有配置项的 key 名称
- 不改变对外 API（registerMob/unregisterMob/isActive）

---

## 二、包结构

```
com.emwbridge.ai/                        ← 新建独立 AI 框架包
├── engine/
│   └── TarkovAIEngine.java              ← 顶层协调器，替代processSmartAI
├── perception/
│   ├── PerceptionManager.java           ← 双感知入口(视觉+听觉→曝光值→警戒阶段)
│   ├── VisualPerception.java            ← 视觉曝光条(潜行/冲刺/距离/角度/光照/天气)
│   ├── AuditoryPerception.java          ← 听觉事件(枪声/跑步/开门/爆炸→声源追踪)
│   └── AlertStage.java                  ← 警戒三阶段(黄SUSPICIOUS/橙ALERT/红HOSTILE)
├── faction/
│   ├── FactionManager.java              ← 阵营判定入口
│   ├── TarkovFaction.java               ← 6阵营枚举
│   └── HostilityMatrix.java             ← 仇恨矩阵(含NEUTRAL→HOSTILE条件切换)
├── personality/
│   ├── PersonalityManager.java          ← 性格行为控制器(权重决策)
│   └── PersonalityType.java             ← 8性格枚举
├── squad/
│   ├── SquadManager.java                ← 小队同步(情报共享/队长/分工)
│   └── SquadRole.java                   ← 4分工枚举
├── combat/
│   ├── AimConvergenceManager.java       ← 瞄准预热+收敛+锁头窗口+掩体记忆
│   ├── TarkovTactics.java               ← 战术行为(压制/弹幕/精准/探头/站立射击)
│   └── CoverMovement.java               ← 掩体走位(横移/枪线方向)
└── sound/
    └── SoundEventManager.java           ← 声音事件接收+衰减分发

com.emwbridge.managers/
└── TarkovAIManager.java                 ← 缩减为EM桥接层，委托给TarkovAIEngine
```

---

## 三、数据流

```
每 AI tick:
┌──────────────────────────────────────────────────────┐
│ 1. PerceptionManager.updateExposure()                │
│    ├── VisualPerception.calc(                        │
│    │     hasLineOfSight, 是否潜行, 是否冲刺,          │
│    │     距离, 角度(正面/侧面/后方),                   │
│    │     当前时间(白天/夜间), 天气(晴/雨/雷暴),        │
│    │     玩家是否持发光物品(火把/发光箭)               │
│    │   )                                             │
│    ├── AuditoryPerception.processPending(声音事件队列) │
│    └── 输出: AlertStage(黄/橙/红), 曝光值(0-100)      │
├──────────────────────────────────────────────────────┤
│ 2. FactionManager.getRelation(我方阵营, 目标阵营)       │
│    ├── 查HostilityMatrix                             │
│    ├── NEUTRAL时检查切换条件(贴脸/被攻击/先开火)        │
│    └── 输出: HOSTILE / NEUTRAL / FRIENDLY            │
├──────────────────────────────────────────────────────┤
│ 3. PersonalityManager.decide(性格, 警戒阶段, HP)       │
│    └── 输出: 行为权重(进攻/防守/伏击/撤退)              │
├──────────────────────────────────────────────────────┤
│ 4. SquadManager.shareIntel(小队ID, 目标坐标, AlertStage)│
│    └── 全队同步, 队长分配SquadRole                      │
├──────────────────────────────────────────────────────┤
│ 5. AimConvergenceManager.update(锁定时长, 目标可视部位)  │
│    ├── 锁定<15s + 视线通畅 → eyeLocation(锁头)        │
│    ├── 锁定>15s or 视线受阻 → bodyCenter(躯干)         │
│    ├── 掩体反复露身 → 保留命中率记忆(散布快速收敛)      │
│    └── 输出: 瞄准点Location + 散布半径                  │
├──────────────────────────────────────────────────────┤
│ 6. 执行: TarkovTactics 根据决策执行开火/换弹/走位      │
└──────────────────────────────────────────────────────┘
```

---

## 四、子系统规格（MC适配版）

### 4.1 PerceptionManager（双感知系统）

**职责：** 统一管理视觉+听觉感知，计算警戒阶段

**视觉曝光条算法（MC可实现）：**
```
exposureIncrement = baseRate (5.0/tick，可配置)
  × postureMultiplier    (站立1.0, 潜行0.6)          ← 无卧倒
  × motionMultiplier     (静止0.5, 步行0.8, 冲刺2.0)
  × angleMultiplier      (正面1.0, 侧面0.6, 后方0.3)
  × distanceMultiplier   (1.0 / (1 + distance/10))
  × environmentMultiplier(昼晴1.0, 夜间0.5, 雨天0.7, 雷暴0.6)
  × lightMultiplier       (光等级15→1.0, 光等级0→0.4) ← 替代植被/暴露
```

**玩家姿态检测（MC方式）：**
- `player.isSneaking()` → 潜行, posture=0.6
- `player.isSprinting()` → 冲刺, motion=2.0
- `player.getVelocity().length() < 0.01` → 静止, motion=0.5
- 否则 → 步行, motion=0.8

**环境检测：**
- `world.isThundering()` → 雷暴, env=0.6
- `world.hasStorm()` && !thundering → 雨天, env=0.7
- `world.getTime() ∈ [13000,23000)` → 夜间, env=0.5
- 否则 → 昼晴, env=1.0

**光源暴露检测：**
- `player.getLocation().getBlock().getLightLevel()` → 光等级
- 光等级15(最亮) → 1.0, 光等级0(全黑) → 0.4, 线性插值
- 玩家手持火把/发光物品 → +0.3 加成

**不再包含的塔科夫概念：** 卧倒、植被遮挡、身体部位暴露、闪光弹致盲

**警戒阶段转换：**
| 阶段 | 曝光值 | 行为 |
|------|--------|------|
| SUSPICIOUS(黄) | 0-40 | 原地警戒、转头、不主动攻击 |
| ALERT(橙) | 40-80 | 缓慢向可疑方向推进、找掩体架枪 |
| HOSTILE(红) | 80-100 | 锁定目标、开火、持续追踪 |

**听觉事件分级（MC适配）：**

| MC声音事件 | 来源 | 最大距离 | 曝光增量 |
|-----------|------|---------|---------|
| WM枪声(无消音) | WeaponShootEvent | 150m | +30 |
| WM枪声(有消音) | WeaponShootEvent.attachment | 60m | +20 |
| 玩家冲刺 | PlayerMoveEvent.isSprinting | 50m | +15 |
| 玩家步行 | PlayerMoveEvent | 20m | +5 |
| 玩家潜行 | PlayerMoveEvent.isSneaking | 8m | +2 |
| TNT/苦力怕爆炸 | ExplosionPrimeEvent | 80m | +40 |
| 方块破坏 | BlockBreakEvent | 25m | +10 |
| 打开容器 | ContainerOpenEvent | 20m | +10 |
| 实体受伤 | EntityDamageEvent | 30m | +12 |

**不再包含：** 手术/止痛药/翻物资（MC无对应）

**听觉衰减：**
- 距离线性衰减：`exposure = base × (1 - distance/maxDistance)`
- 方块穿透衰减：每穿透一个实心方块 ×0.7
- 耳机AI：听觉范围+40%，曝光值×1.4（通过 metadata `emwm_headset` 标记）

**听觉触发逻辑（MC方式）：**
- 声音事件不直接锁定，AI转向声源方向
- 连续同方向声音快速拉高曝光值（每次+5额外增量）
- 小队AI共享听觉情报

---

### 4.2 TarkovFaction + HostilityMatrix（阵营系统）

**6 大阵营：**

| 阵营 | 说明 | MC对应 |
|------|------|--------|
| PLAYER_PMC | 玩家PMC | 普通玩家(默认) |
| PLAYER_SCAV | 玩家Scav | 玩家通过权限/metadata标记 |
| AI_SCAV | 普通AI Scav | EliteMobs scav tier怪物 |
| AI_PMC | AI PMC | EliteMobs pmc tier怪物 |
| BOSS | Boss男团 | EliteMobs boss tier怪物 |
| CULTIST | 邪教徒/游击队 | 特殊EliteMobs怪物 |

**仇恨矩阵：**

| 我 \ 你 | PLAYER_PMC | PLAYER_SCAV | AI_SCAV | AI_PMC | BOSS | CULTIST |
|---------|------------|-------------|---------|--------|------|---------|
| AI_SCAV | HOSTILE | NEUTRAL* | FRIENDLY | 80%HOSTILE | HOSTILE | HOSTILE |
| AI_PMC | HOSTILE | NEUTRAL | 80%HOSTILE | 同阵营FRIENDLY | HOSTILE | HOSTILE |
| BOSS | HOSTILE | HOSTILE | HOSTILE | HOSTILE | 同派系FRIENDLY | HOSTILE |
| CULTIST | HOSTILE | HOSTILE | HOSTILE | HOSTILE | HOSTILE | 同派系FRIENDLY |

\*NEUTRAL→HOSTILE 条件切换：
- 距离 < 3m（贴脸）
- AI受到该目标伤害
- 目标对AI或其队友先开火

**阵营分配（MC方式）：**
- AI阵营通过 EliteMobs 怪物 tier 映射：scav→AI_SCAV, pmc→AI_PMC, boss→BOSS
- 可通过 metadata `emwm_faction` 覆盖
- 玩家默认 PLAYER_PMC，权限 `emwm.scav` → PLAYER_SCAV

---

### 4.3 PersonalityType（8类性格）

| 性格 | 行为特征 | 参数影响 |
|------|---------|---------|
| COWARD(胆小) | 听见枪声找掩体蹲守，极少推进，HP<50%就逃 | 激进0.1, 掩体偏好1.0, 撤退阈值0.5 |
| RECKLESS(鲁莽) | 直线冲锋，不找掩体，近距离火力拉满，HP<15%逃 | 激进0.9, 掩体偏好0.1, 撤退阈值0.15 |
| CAUTIOUS(谨慎) | 交替卡位，频繁探头，优先掩体安全 | 激进0.4, 掩体偏好0.8, 撤退阈值0.35 |
| AMBUSH(伏击型) | 静止卡点，等玩家露身再开火 | 激进0.2, 掩体偏好0.9, 撤退阈值0.3 |
| LOOTER(搜刮型) | 交战间隙暂停，容易被偷袭 | 激进0.3, 掩体偏好0.4, 撤退阈值0.4 |
| CAPTAIN(队长型) | 报点指挥走位，全队战术核心 | 激进0.6, 掩体偏好0.5, 撤退阈值0.3 |
| FLANKER(绕后型) | 偏好侧翼包抄，绕到玩家后方 | 激进0.7, 掩体偏好0.4, 撤退阈值0.25 |
| SUPPRESSOR(压制型) | 持续开火压制，弹幕消耗 | 激进0.5, 掩体偏好0.5, 撤退阈值0.3 |

**性格影响决策权重：**
- 进攻权重 = 激进值 × (曝光值/100)
- 防守权重 = (1-激进值) × (1 - 曝光值/100)
- 伏击权重 = (掩体偏好 > 0.8) × (曝光值 < 50)
- 撤退权重 = (HP < 撤退阈值) × 2.0

---

### 4.4 SquadManager（小队协同）

**职责：**
- 维护同区域刷出的同阵营怪物到一个小队
- 队长(第一个 spawn 或 CAPTAIN 性格的)分配分工
- 任意成员发现目标 → 全队同步目标坐标、AlertStage
- 队长阵亡 → 小队失去协同（角色随机决策，不再协调）
- 阵亡成员的最后标记位置保留，存活队友继续追击

**SquadRole 分工：**

| 角色 | 行为 | 理想距离 | 战术偏好 |
|------|------|---------|---------|
| ASSAULT(突击手) | 正面冲锋推进 | 3-10m | BERSERKER/SUPPRESSING |
| SNIPER(狙击手) | 远距离架枪卡点 | 20-50m | PRECISE/SNIPING |
| SUPPRESSOR(火力手) | 火力压制掩护 | 10-25m | BARRAGE/SUPPRESSING |
| FLANKER(绕后) | 侧翼包抄 | 5-15m | STALKING/FLANK |

**分配规则：**
- 队长(CAPTAIN性格)自动获得 CAPTAIN 角色
- 如有 SNIPING 武器配置 → SNIPER 角色
- 大容量弹匣武器 → SUPPRESSOR 角色
- 其余随机分配

---

### 4.5 AimConvergenceManager（瞄准收敛）

**职责：** 管理 AI 的瞄准精度随时间收敛

**核心规则：**

1. **初始瞄准延迟（反应时间）：**
   - AlertStage 从黄→红后，额外等待 delay 才首次开枪
   - 普通AI(scav/pmc): 0.5-1.2s, Boss: 0.2-0.5s
   - 锁头窗口从此延迟结束后开始计时

2. **散布收敛：**
   ```
   最终散布 = 基础散布 × convergenceRate^锁定秒数
   ```
   - 锁定每秒散布缩小 15%（convergenceRate=0.85）
   - 最小散布：基础散布 × 0.2（最长约10秒收敛到底）
   - 散布重置：目标脱离视线 > 2秒 → 散布重置为初始值

3. **15 秒锁头窗口：**
   - 清晰 hasLineOfSight + 连续锁定 < 15s → 瞄准 eyeLocation（头部）
   - 锁定 > 15s OR hasLineOfSight=阻断 → 切换瞄准 bodyCenter（躯干）
   - 重新建立视线 → 重新计时，散布不重置（保留记忆）

4. **掩体记忆：**
   - 在掩体处的 ±2 格范围内反复露身 → 散布不重置
   - 标记 `lastEngagePosition`，玩家在该位置附近再次出现 → 散布×0.7
   - 玩家换到新掩体（距离上次 > 5格）→ 散布重置

5. **MC 可见部位简化（替代 BodyPartTargeting）：**
   - `hasLineOfSight` 到 eyeLocation → 可锁头，瞄准点 = eyeLocation + 散布
   - `hasLineOfSight` 到 eyeLocation=false 但到 bodyCenter=true → 仅躯干，瞄准点 = bodyCenter + 散布
   - 两个都 false → 无视线，不射击

---

### 4.6 SoundEventManager（声音事件）

**职责：** 接收服务端事件，计算衰减后分发给范围内的 AI 实体

**事件监听：**
- `EntityDamageEvent` → 受伤声音
- `ExplosionPrimeEvent` → 爆炸声音
- `BlockBreakEvent` → 破坏方块声音
- `PlayerInteractEvent` → 开门/翻箱声音
- `WeaponShootEvent`（WM事件）→ 枪声
- 周期性 PlayerMoveEvent → 脚步声音（低频率采样）

**衰减模型：**
```
有效曝光 = 基础曝光增量 × max(0, 1 - distance / maxDistance) × 方块衰减
方块衰减 = 0.7^(穿透实心方块数)
```

**声音事件队列：**
- 每个事件入队后立即分发给范围内所有AI
- 每秒最多触发一次同类型声音的额外增量（防刷屏）
- 爆炸后 2 秒内该 AI 听觉输出 ×0（耳鸣效果）

---

## 五、与现有代码的关系

| 现有代码 | 处理方式 |
|---------|---------|
| `TarkovAIManager.java` | 缩减为桥接层，委托给 `TarkovAIEngine`，保留 register/unregister API |
| `TacticalUtils.java` | 保留，供 perception 和 combat 调用 LOS/掩体/世界边界检测 |
| `MobWeaponManager.java` | 不变 |
| `ExtremeEventManager.java` | 不变 |
| `EliteMobSpawnListener.java` | 不变（EM怪物生成→注册到AIEngine） |
| `config.yml` 现有段 | tier-settings/extreme-events/tactical 不变，新增 perception/faction/personality/aim 段 |
| 5 个射击战术方法 | 迁移到 `ai/combat/TarkovTactics.java`，站位射击+横移逻辑保持 |

---

## 六、实施顺序

| Phase | 内容 | 可独立测试 |
|-------|------|-----------|
| 1 | 搭骨架：创建所有新类空壳 + 枚举 + 接口 | 编译通过 |
| 2 | Perception 系统：视觉曝光+听觉事件+警戒阶段 | 打印曝光值 |
| 3 | Faction 系统：阵营枚举+仇恨矩阵 | 打印阵营关系 |
| 4 | Personality 系统：8性格+行为权重决策 | 替换简单决策 |
| 5 | Squad 系统：小队同步+分工 | 多怪协同 |
| 6 | Aim 系统：瞄准收敛+锁头窗口+掩体记忆 | 散布可视化 |
| 7 | SoundEventManager：事件监听+衰减分发 | 声音触发日志 |
| 8 | TarkovAIEngine 集成：串联所有子系统，替代 processSmartAI | 完整AI运行 |
| 9 | TarkovAIManager 缩减：变成桥接层，委托给 Engine | 回归兼容 |

---

## 七、配置新增

```yaml
# ==================== AI 感知系统 ====================
perception:
  visual:
    base-rate: 5.0
    # 姿态修正（MC只有站立和潜行）
    posture:
      standing: 1.0
      sneaking: 0.6
    # 移动修正
    motion:
      still: 0.5
      walking: 0.8
      sprinting: 2.0
    # 角度修正
    angle:
      front: 1.0
      side: 0.6
      back: 0.3
    # 环境修正
    environment:
      day-clear: 1.0
      night: 0.5
      rain: 0.7
      thunder: 0.6
    # 光源修正（光等级15=最亮，0=全黑）
    light:
      max-level: 15.0
      min-multiplier: 0.4
  auditory:
    enabled: true
    process-mc-events: true
    headset-multiplier: 1.4
    ear-ringing-ms: 2000
    same-direction-bonus: 5.0

# ==================== 阵营系统 ====================
factions:
  default-faction: AI_SCAV
  metadata-key: emwm_faction
  player-scav-permission: emwm.scav

# ==================== 性格系统 ====================
personality:
  assignment: TIER_BASED
  tier-weights:
    scav:
      COWARD: 0.35
      CAUTIOUS: 0.30
      LOOTER: 0.25
      RECKLESS: 0.10
    pmc:
      CAUTIOUS: 0.30
      FLANKER: 0.20
      CAPTAIN: 0.15
      SUPPRESSOR: 0.15
      AMBUSH: 0.10
      RECKLESS: 0.10
    boss:
      CAPTAIN: 0.40
      RECKLESS: 0.25
      SUPPRESSOR: 0.20
      FLANKER: 0.15

# ==================== 小队系统 ====================
squad:
  enabled: true
  max-size: 5
  intel-share-range: 50.0

# ==================== 瞄准系统 ====================
aim:
  initial-delay:
    scav: 1.0
    pmc: 0.8
    cultist: 0.5
    boss: 0.3
  headshot-window-seconds: 15.0
  convergence-rate: 0.85
  min-spread-multiplier: 0.2
  vision-loss-reset-seconds: 2.0
```

---

## 八、最终 AI 决策优先级链（TarkovAIEngine.tick）

这是所有子系统串联后的最终决策顺序，每一层可以提前 return 阻断后续流程：

```
每个 AI 实体每 tick 执行：

1. [PerceptionManager] 更新视觉曝光 + 处理本 tick 听觉事件
   ├── 输出: AlertStage, exposureValue
   └── 如果 AlertStage == SUSPICIOUS(黄): 转头警戒, return

2. [FactionManager] 计算与目标的阵营关系
   ├── NEUTRAL: 检查切换条件(贴脸/被打/先开火)
   └── 如果 NEUTRAL 且未触发切换: 无视目标, return

3. [PersonalityManager] 根据性格+警戒阶段+HP 计算行为权重
   ├── 输出: {进攻分, 防守分, 伏击分, 撤退分}
   └── 选最高分行为方向

4. [SquadManager] 同步目标坐标到全队
   ├── 如果我是队长 → 分配/调整 SquadRole
   └── 根据 SquadRole 修正行为方向(ASSAULT→偏向进攻, SNIPER→偏向卡点)

5. [AimConvergenceManager] 计算瞄准点
   ├── < 15s 锁头窗口 + 有视线到 eyeLocation → 锁头
   ├── > 15s 或 只有 bodyCenter 视线 → 躯干
   ├── 散布 = 基础散布 × convergenceRate^锁定秒数
   └── 输出: 瞄准点Location, 散布半径

6. [TarkovTactics] 执行行为
   ├── 进攻 → 按距离选战术(SUPPRESSING/BARRAGE/PRECISE), 站立开火
   ├── 防守 → 找掩体, PEEKING 探头射击
   ├── 伏击 → 静止卡点, 等目标进入射程
   ├── 撤退 → 战术撤退/换弹/逃跑
   └── burst结束后 → [CoverMovement] 走位(掩体后横移/无掩体枪线移动)

7. [ExtremeEventManager] 小概率事件覆盖(恐慌/肾上腺素/幸运/故障)
```

---

## 九、边界情况处理

| 场景 | 处理 |
|------|------|
| 无目标时 | AlertStage 回到 IDLE, 巡逻模式 |
| 目标死亡/离线 | 清除目标, AlertStage 保留 5s 再降 |
| 多目标 | 按威胁评分选主目标（距离×0.5 + 低血量×10），FactionManager 分别判定每个 |
| 小队全灭 | 最后一个存活成员获得 RECKLESS 性格（背水一战） |
| 队长阵亡 | SquadManager 清空角色分配, 成员各自按性格决策 |
| 实体被卸载(chunk卸载) | 从 activeMobs 移除, 不保留状态 |
| 插件热重载 | `TarkovAIEngine.shutdown()` 清除所有状态, 重新注册 |
| 跨世界 | SquadManager 只同步同世界内成员 |
| Folia 环境 | 每个子系统无状态共享, 天然适配多线程 |
| 性能：大量怪物 | 远距离(>40格)降频到每2 tick一次；听觉事件每 tick 最多处理5个/实体 |
| 配置缺失 | 所有子系统都有合理默认值, 不依赖配置项存在 |

---

## 十、对外 API 面（兼容现有调用）

```java
// TarkovAIEngine 主入口
void registerMob(LivingEntity entity, String tier);
void unregisterMob(LivingEntity entity);
boolean isActive(LivingEntity entity);
int getActiveCount();

// 高级注册(带覆盖)
void registerMob(LivingEntity entity, String tier, TarkovFaction faction, PersonalityType personality);

// 内部访问(供桥接层)
PerceptionManager getPerceptionManager();
FactionManager getFactionManager();
SquadManager getSquadManager();
void shutdown();
```

---

## 十一、与 EliteMobs 的具体桥接方式

```
EliteMobs 怪物生成
  → EliteMobSpawnListener.detect()
    → 判断 tier (scav/pmc/boss)
    → 分配武器(MobWeaponManager.bindWeapon)
    → TarkovAIManager.registerMob(entity, tier)     ← 现有API
      → TarkovAIEngine.registerMob(entity, tier)    ← 内部委托
        → FactionManager.assign(tier)                ← scav→AI_SCAV, pmc→AI_PMC, boss→BOSS
        → PersonalityManager.roll(tier)              ← 按tier权重随机性格
        → SquadManager.tryJoin(entity)               ← 加入附近小队或创建新小队
        → entity.setMetadata("emwm_ai_enabled", true)
```

