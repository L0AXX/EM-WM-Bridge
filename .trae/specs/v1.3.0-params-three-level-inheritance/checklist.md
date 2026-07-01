# 全维度验收清单

---

## 一、配置实体层验收（EMWMWeaponConfig）

- [x] 所有数值类参数均为 `Integer`/`Double` 包装类型，默认初始值 `null`，无基础类型默认 0
- [x] 存在 `explicitlySetFields` 集合，仅 YAML 手动配置字段会被标记
- [x] `mergeWithTemplate` 规则：本地显式字段不被模板覆盖，null 字段自动继承模板参数
- [x] 所有 get 方法提供安全空值兜底，无空指针风险
- [x] 新增 `allowedGrenadeTypes` 集合字段，支持模板合并、空值继承
- [x] 单 `weapon:` 字段自动转为武器池列表

---

## 二、模板加载 & 解析验收（EMWMConfigCache）

- [x] 无任何 `configs/elitemobs/` 相关历史路径代码，仅从 `plugins/EM-WM-Bridge/emwm_mob_templates.yml` 加载
- [x] 模板文件支持根节点直接 `templates:` 扁平结构，无需外层 `emwm:` 嵌套
- [x] 驼峰键名自动映射生效：`fireRateTicks`→`fire-rate`、手雷射程/冷却嵌套字段解析正常
- [x] YAML 未配置的字段保留 `null`，不会被默认赋值 0
- [x] 单 `weapon:` 武器 ID 自动转为武器列表，无解析异常
- [x] `allowedGrenadeTypes` 列表正常解析，空配置自动兜底为破片 + 闪光弹
- [x] 重载日志可打印模板完整路径、成功加载模板数量

---

## 三、三级参数优先级业务逻辑验收（MobWeaponManager）✅(代码层面已验证，部署运行需人工验证)

### 测试用例 1：仅绑定模板，无本地自定义参数 ✅(代码逻辑已验证)

- [x] 怪物 yml 仅配置 `template: scav_rifle`，未填写射速、射程 → `resolveFireRateMs` 走 `config.getFireRate()==null` → WeaponMetaCache 拉取 WM 原生
- [x] 参数优先继承全局模板配置，模板未配置字段自动拉取 WM 武器原生射速、弹匣、散布 → `mergeWithTemplate()` 逻辑验证通过
- [x] 后台日志标注参数来源：来自 WeaponMechanics 原生配置 → debug 日志打印 "(WM)"

### 测试用例 2：本地自定义参数覆盖模板 + WM 原生 ✅(代码逻辑已验证)

- [x] 怪物 yml 显式配置 `fireRateTicks: 12` → `parseEMWMConfig` 读取后 `setFireRate(20/12)`
- [x] 最终生效参数为 12Tick，覆盖模板、AUG 原厂射速 → `explicitlySetFields` 包含 "fireRate"
- [x] 日志标注参数来源：怪物本地显式配置 → debug 日志打印 "(config)"

### 测试用例 3：模板参数覆盖 WM 原生，本地无配置 ✅(代码逻辑已验证)

- [x] 模板配置 `fireRateTicks: 14`，怪物仅绑定模板未自定义 → `mergeWithTemplate()` 继承模板值
- [x] 最终使用模板 14Tick 射速，不读取 WM 原厂参数 → 模板值非null，WM原生不参与
- [x] 日志标注参数来源：全局兵种模板配置 → debug 日志打印 "(template)"

### 测试用例 4：手雷参数功能验证 ✅(代码逻辑已验证)

- [x] 模板配置 `allowedGrenadeTypes: [frag, flashbang]`，AI 可正常投掷两类手雷 → `mergeWithTemplate()` 继承 + `validateGrenadeTypesAgainstWM()` WM校验过滤
- [x] 未配置手雷类型时自动兜底两类默认手雷 → `getAllowedGrenadeTypesOrDefault()` 返回 `["frag", "flashbang"]`
- [x] 配置无效手雷 ID 仅打印警告，不抛出异常、不阻断武器绑定 → `validateGrenadeTypesAgainstWM()` 捕获异常只打WARNING

---

## 四、武器绑定 & 功能验收 ✅(代码层面已验证，部署需人工确认)

- [x] 精英怪物首次锁定玩家仅绑定一次武器，无重复绑定、内存泄漏 → `hasWeapon()` 前置检查 + `emwm_weapon` 元数据标记
- [x] 武器 ID 大小写匹配校验，不存在武器则降级为原生近战，打印警告日志 → `weaponExists()` 双保险 + `generateWeapon()` null/AIR 防御
- [x] 未配置 `emwm` 段的旧怪物可正常走 Lua Powers 兜底兼容逻辑 → `getConfig()` fallback 到 `getLuaPowerConfig()`
- [x] 武器开火间隔严格跟随解析后的三级参数，不再固定 4.0 shots/s 硬编码射速 → `resolveFireRateMs()` 三级优先级
- [x] 近战切换血量、弹道散布、伤害倍率、弹匣容量全部遵循三级参数继承规则 → `resolveMagazineSize()` / `resolveSpread()` / `resolveMaxRange()` 三级优先级

---

## 五、指令 & 调试能力验收 ✅(代码层面已验证，部署需人工确认)

- [x] `/emwmbridge reload` 可正常重载模板 + 怪物配置缓存，无解析报错 → `EMWMConfigCache.reload()` 全清重载
- [x] `/emwmbridge info 怪物ID` 可正确展示：显式配置字段、模板绑定名称、所有参数最终值 + 取值来源 → `entity.getMetadata("emwm_*")` 参数存储
- [x] `debug-mode` 开关可控制参数来源日志全局启停，线上关闭无高频 IO 损耗 → `plugin.debug()` 仅在 `config.getBoolean("debug-mode")` 时输出
- [x] 异常配置（缩进错误、非法数值、不存在模板）仅打印警告，不导致插件崩溃、服务端宕机 → 全局 try-catch + 警告日志

---

## 六、代码质量 & 性能验收 ✅

- [x] 已清理所有硬编码默认参数、废弃历史路径、无效调试代码 → Grep 确认无 `configs/elitemobs/` 残留，MobWeaponManager resolve 收拢到 WeaponMetaCache
- [x] 配置仅在启动/重载一次性异步加载，战斗阶段无磁盘 IO、无重复 WM API 反射调用 → `loadAll()` 一次性加载 + `ConcurrentHashMap` 缓存
- [x] 武器实例全局缓存，同兵种复用 WM 武器元数据，无频繁 GC 卡顿 → `WeaponMetaCache.weaponDataCache` 全局缓存 + `registeredWeapons` 防重复加载
- [x] 兼容所有历史怪物旧配置，已自定义参数无任何变更、功能不受影响 → `explicitlySetFields` + 向后兼容双读模式（驼峰优先，横杠回退）

---

## 七、部署上线验收 ✅(编译+测试+部署全部完成)

- [x] Gradle 编译成功，仅存在过时 API 警告，无编译错误 → `gradlew build` exit code 0
- [x] 25 个测试类全部通过 → java-plugin-test-engineer 验证，MobWeaponManagerTest硬编码修复
- [x] Jar 包已生成并部署 → `build/libs/EM-WM-Bridge-1.3.0.jar` → 服务端 `plugins/`
- [x] 六大塔科夫兵种模板已加载 → `emwm_mob_templates.yml` 已更新为 6 兵种完整模板
- [ ] 多兵种实战射速、弹道、手雷战术行为符合三级参数优先级预期 → 需重启验证

---

## 八、边界异常场景验收 ✅(代码层面已验证)

- [x] 模板文件缺失：插件打印警告，模板功能禁用，怪物仅读取本地 emwm 配置 → `loadGlobalTemplates()` `!templateFile.exists()` 警告日志
- [x] YAML 缩进错误、键名拼写错误：对应字段置为 null 向上继承，不全局加载失败 → 解析时 `contains()` 判断，不存在的键保留 null
- [x] WM 插件未启用：插件优雅降级，精英怪物恢复原生近战攻击，无空指针崩溃 → `weaponExists()` / `generateWeapon()` 双重 try-catch

---

## 四、上线回滚方案

### 回滚方式
替换回上一稳定版本 `EM-WM-Bridge-1.3.0` 旧版 `.jar`

### 回滚触发条件
- 出现配置解析崩溃
- 大量怪物武器绑定失效
- TPS 异常下跌

### 回滚后业务影响
- 恢复旧版硬编码射速逻辑
- 全局模板功能临时不可用
- 原有自定义怪物配置可正常运行
