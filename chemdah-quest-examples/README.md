# EM-WM-Bridge × Chemdah 任务脚本示例集

本目录提供 5 个 Chemdah 任务脚本示例，展示如何利用 EM-WM-Bridge 的 API、事件和 Metadata 来构建塔科夫式任务。

## 前置条件

- EM-WM-Bridge 0.3.0+ 已安装
- Chemdah 已安装
- 两个插件在同一服务端

## 可用接口一览

### 方式1：EMWMBridgeAPI（推荐）

```kotlin
import com.emwbridge.api.EMWMBridgeAPI

// 读取精英怪状态
val tier = EMWMBridgeAPI.getTier(entity)           // "scav" / "pmc" / "boss" / "raider" / "sniper"
val state = EMWMBridgeAPI.getCombatState(entity)    // "patrol" / "combat" / "suppressing" / "search" / "retreat"
val weapon = EMWMBridgeAPI.getWeapon(entity)        // WM 武器标题
val ammo = EMWMBridgeAPI.getAmmo(entity)            // 剩余弹药
val reloading = EMWMBridgeAPI.isReloading(entity)   // 是否换弹中
val ads = EMWMBridgeAPI.isADS(entity)               // 是否瞄准
val tinnitus = EMWMBridgeAPI.hasTinnitus(entity)    // 是否耳鸣
val aggressive = EMWMBridgeAPI.getAggressiveness(entity) // 攻击性 0-1

// 便捷判断
EMWMBridgeAPI.isBoss(entity)        // 是否 Boss
EMWMBridgeAPI.isInCombat(entity)    // 是否战斗中
EMWMBridgeAPI.isSuppressing(entity) // 是否压制状态
EMWMBridgeAPI.isEMWMMob(entity)     // 是否 EMWM 管理的精英怪
```

### 方式2：EMWMKillEvent（击杀事件）

```kotlin
import com.emwbridge.events.EMWMKillEvent
import com.emwbridge.events.EMWMKillEvent.KillMethod

// 监听精英怪击杀事件
listen<EMWMKillEvent> {
    val killer = it.killer       // 击杀者（Player，可能为 null）
    val victim = it.victim       // 被击杀的精英怪
    val method = it.killMethod   // GUN / GRENADE / MELEE / EXPLOSION / OTHER
    val tier = it.tier           // 兵种
    val weapon = it.weaponTitle  // 武器名
    val state = it.combatState   // 死亡时的战斗状态

    // 便捷方法
    it.isGrenadeKill()  // 手雷/爆炸击杀
    it.isGunKill()      // 枪械击杀
    it.isBossKill()     // Boss 击杀
}
```

### 方式3：MobWeaponShootEvent（射击事件）

```kotlin
import com.emwbridge.events.MobWeaponShootEvent

// 监听精英怪射击
listen<MobWeaponShootEvent> {
    val shooter = it.shooter     // 射击的精英怪
    val target = it.target       // 被射击的玩家
    val weapon = it.weaponTitle  // 武器名
    val distance = it.distance   // 射击距离
    val ads = it.isAds           // 是否瞄准射击
}
```

### 方式4：直接读 Metadata

```kotlin
// 不依赖 API 类，直接读 Bukkit Metadata
val tier = entity.getMetadata("emwm_tier").firstOrNull()?.asString()
val reloading = entity.getMetadata("emwm_reloading").firstOrNull()?.asBoolean() ?: false
val ammo = entity.getMetadata("emwm_ammo").firstOrNull()?.asString()?.toIntOrNull() ?: 0
```

## 示例文件

| 文件 | 任务描述 | 用到的接口 |
|------|---------|-----------|
| `01_grenade_kill_quest.kts` | 用手雷击杀 5 个精英怪 | EMWMKillEvent |
| `02_boss_hunt_quest.kts` | 击杀 3 个 Boss 级精英 | EMWMKillEvent + EMWMBridgeAPI |
| `03_reload_kill_quest.kts` | 在精英怪换弹时击杀它 | EMWMBridgeAPI + EntityDeathEvent |
| `04_tinnitus_survival_quest.kts` | 在耳鸣状态下击杀精英 | EMWMBridgeAPI |
| `05_weapon_collect_quest.kts` | 收集精英怪掉落的武器 | EMWMKillEvent + 实体检测 |

## KillMethod 枚举说明

| 枚举值 | 触发条件 |
|--------|---------|
| `GUN` | WM 子弹/投射物击杀 |
| `GRENADE` | 手雷（破片雷/闪光弹/烟雾弹/任何爆炸）击杀 |
| `MELEE` | 近战攻击击杀 |
| `EXPLOSION` | 非手雷爆炸（TNT/苦力怕）击杀 |
| `OTHER` | 掉落/火焰/药水等其他原因 |

## 注意事项

1. EMWMKillEvent 在 `EntityDeathEvent` 中触发，此时实体还未被清除，可以读取 metadata
2. `entity.getKiller()` 返回最后造成伤害的玩家，可能为 null（非玩家击杀）
3. 伤害类型检测在 `EntityDamageByEntityEvent` 中完成，写入 `emwm_last_damage_type` metadata
4. WM 手雷如果产生 `ENTITY_EXPLOSION` 伤害，会被分类为 `GRENADE`
5. 如果 WM 使用自定义伤害类型（`CUSTOM`），可能需要额外适配
