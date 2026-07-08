# P0 致命问题修复报告

**日期**：2026-07-05
**场景**：P0 致命问题修复（代码实现 + 编译验证）
**执行方**：主理人直接执行

---

## 📌 TL;DR

- **整体结论**：🟢 全部 11 项 P0 致命问题已修复，编译通过
- **阻塞项数量**：0
- **下一步**：P1 严重问题修复 → 黑盒测试 → 版本降级标记

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🟢 Go（编译层面） |
| P0 修复完成 | 11/11 (100%) |
| 修改文件数 | 11 个 Java 源文件 |
| 编译状态 | ✅ BUILD SUCCESSFUL |
| 遗留风险 | 运行时测试未覆盖；P1 严重问题尚待处理 |

---

## 1. 修复清单

### P0-1 / P0-2：双射击系统冲突 + 任务泄漏

**问题**：`EliteMobCombatListener` 内部 `scheduleShootingTask()` 以 `runTaskTimer` 独立运行射击循环，与 `TarkovAIEngine.executeTacticalAction()` 形成双射击系统——同一怪物每 tick 可能被射击两次，且 `runTaskTimer` 从不取消导致任务泄漏。

**修复**：完全删除 `scheduleShootingTask()` 方法及其 `runTaskTimer`，删除 `lastShootTime` 缓存。射击统一由 `TarkovAIEngine` 控制。`currentTargets` 仅保留用于信息查询。

**文件**：`EliteMobCombatListener.java`（143→93 行）

---

### P0-3：ExtremeEventManager 死代码

**问题**：`ExtremeEventManager`（恐慌模式 / 肾上腺素 / 幸运射击 / 战术失误）从未被接入 AI 主循环，全部功能处于死代码状态。

**修复**：在 `TarkovAIEngine.tickEntity()` 中调用 `extremeEventManager.checkExtremeEvents(entity, primaryTarget, state.tier)`，并将速度修饰符应用到实体移动、射速修饰符应用到射击概率。

**文件**：`TarkovAIEngine.java`

---

### P0-4：updateState() 语义错误

**问题**：`ExtremeEventManager.updateState()` 将伤害值（double）直接存为时间戳（`state.lastDamageTime = (long) lastDamage`），导致后续所有基于时间的判断全部错误。

**修复**：拆分为两个字段：`lastDamageValue`（double，记录伤害值）和 `lastDamageTimestamp`（long，记录时间戳）。

**文件**：`ExtremeEventManager.java`

---

### P0-5：Folia 线程安全

**问题**：多处使用 `Bukkit.getScheduler().runTaskLater()` 和 `BukkitRunnable.runTaskTimer()` 直接操作实体状态，在 Folia 环境下会抛异常或导致跨区域线程操作。

**修复**：
- `TarkovAIEngine.startScheduler()`：tickEntity() 调用包裹 `entity.getScheduler().execute()` 实现区域线程调度
- `MobWeaponManager.reload()`：换弹完成回调改用 EntityScheduler（Folia）/ runTaskLater（Paper）
- `ThrowableManager`：三种手雷延迟爆炸改用 `scheduleDelayedEntityTask()` 辅助方法；投掷物轨迹在 Folia 下改用递归 EntityScheduler 调用

**文件**：`TarkovAIEngine.java`、`MobWeaponManager.java`、`ThrowableManager.java`

---

### P0-6：faceTarget() 不生效

**问题**：`CoverMovement.faceTarget()` 修改了 Location 克隆体的 yaw/pitch，但从未将修改后的值应用回实体。

**修复**：`loc.setYaw(yaw); loc.setPitch(pitch);` → `entity.setRotation(yaw, pitch);`

**文件**：`CoverMovement.java`

---

### P0-7：热重载幽灵怪 + 无持久化

**问题**：`/emwm reload` 调用 `restart()` 只做 `stop() + start()`，不扫描已有实体——所有已注册 AI 的怪物变成"幽灵怪"（PDC 有标记但 AI 引擎不认识）。服务器重启后 AI 状态完全丢失。

**修复**：
- `registerMob()` 中将 tier 写入 PersistentDataContainer（`emwm_tier_pdc` 键）
- 新增 `recoverMobs()` 方法：扫描所有世界的活体实体，对有 PDC 标记的实体重新注册 AI
- `TarkovAIManager.restart()` 在 stop+start 后调用 `recoverMobs()`

**文件**：`TarkovAIEngine.java`、`TarkovAIManager.java`

---

### P0-8：跨世界 distance() 异常

**问题**：全项目 12 处 `Location.distance()` 调用未检查世界是否相同，跨世界时直接抛 `IllegalArgumentException` 导致 AI tick 崩溃。

**修复**：逐处添加世界相等检查或使用 `safeDistance()` 方法：
- `TarkovAIEngine`：safeDistance() 辅助方法（nearbyPlayers 过滤、countNearbyEnemies、flashNearbyAI）
- `ThrowableManager`：新增 safeDistance() + explodeFrag/explodeFlash 使用
- `MobWeaponManager`：shootEffect() 添加世界检查
- `VisualPerception`：calculate() 添加世界检查
- `AuditoryPerception`：4 处 distance() 添加世界检查
- `CoverMovement`：moveAlongLineOfFire() 添加世界检查
- `FactionManager`：shouldTurnHostile() 添加世界检查

**文件**：7 个 Java 源文件

---

### P0-9：除零防御

**问题**：`entity.getMaxHealth()` 可能返回 0（未初始化的实体），导致 `entity.getHealth() / maxHealth` 产生 NaN/Infinity 传播到所有 AI 判断。

**修复**：
- `TarkovAIEngine.tickEntity()`：`double hpRatio = maxHealth > 0 ? entity.getHealth() / maxHealth : 1.0;`
- `ExtremeEventManager.checkAdrenaline()`：添加 `if (maxHealth <= 0) return false;`

**文件**：`TarkovAIEngine.java`、`ExtremeEventManager.java`

---

### P0-10：HostilityMatrix 随机阵营不稳定

**问题**：SCAV↔PMC 随机敌对关系每个 tick 重新随机，导致同一对怪物在连续 tick 中一会儿敌对一会儿和平，行为闪烁。

**修复**：添加 TTL 缓存：
- 全局 SCAV↔PMC 关系缓存 5 秒
- 实体对关系缓存 10 秒（`CachedRelation` record + `ConcurrentHashMap`）

**文件**：`HostilityMatrix.java`（59→85 行）

---

### P0-11：EMWMReloadListener 未注册

**问题**：`EMWMReloadListener` 类存在且被 import，`EMWMBridge` 中有 `reloadListener` 字段，但 `registerListeners()` 从未实例化和注册它。

**修复**：在 `registerListeners()` 中添加：
```java
reloadListener = new EMWMReloadListener(this);
pm.registerEvents(reloadListener, this);
```

**文件**：`EMWMBridge.java`

---

## 2. 编译验证

```
./gradlew.bat compileJava --no-daemon
> Task :compileJava
注: 某些输入文件使用或覆盖了已过时的 API。
BUILD SUCCESSFUL in 26s
```

- 编译状态：✅ 通过
- 警告：1 个（@Deprecated 方法，预期中）
- 错误：0

---

## ✅ 行动清单

| # | 行动 | 紧急度 | 状态 |
|---|------|--------|------|
| 1 | P0 致命问题全部修复 | P0 | ✅ 完成 |
| 2 | 在测试服务器部署并运行冒烟测试 | P1 | 待执行 |
| 3 | 修复 P1 严重问题（14 项） | P1 | 待启动 |
| 4 | 编写 AI 引擎核心方法单元测试 | P1 | 待启动 |
| 5 | 版本号降级标记（v1.3.0 → 0.3.0-alpha） | P2 | 待执行 |

---

## ⚠️ 待完善 / 已知局限

- **运行时测试未覆盖**：编译通过不代表运行时正确，需要在 Paper/Folia 测试服务器上实际部署验证
- **P1 严重问题尚待处理**：14 项严重问题（如 FeatureManager 标记失实、AI 引擎零测试覆盖等）仍需修复
- **ThrowableManager 烟雾弹**：`deploySmoke()` 的 BukkitRunnable 在 Folia 上运行于全局区域线程，粒子效果安全但非最优
- **MobWeaponManager 旧 API**：多个 @Deprecated 方法仍被旧 `bindWeapon()` 调用，建议后续清理

---

> 本报告由软件工坊 AI 协作生成，关键决策请由工程负责人复核。
