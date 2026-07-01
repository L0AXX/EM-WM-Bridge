# Tasks

## 前置依赖
- JDK21 编译环境
- 现有 v1.3.0 代码基线（当前源码已含大部分 P0 完成代码）
- EM/WM 前置插件 API 依赖（WeaponMechanics 4.3.1, EliteMobs 5.0.4, Paper 1.21.4）

---

## P0 核心必做任务（阻塞所有功能，最高优先级）

### Task 1：EMWMWeaponConfig 配置实体改造【已完成】

- [x] `fireRate`, `magazineSize`, `reloadDuration`, `spread`, `recoil`, `maxRange`, `damageMultiplier` 等射击/弹匣/战术/行为字段改为 `Integer`/`Double`/`Boolean` 包装类型，默认初始值 `null`
- [x] 新增 `Set<String> explicitlySetFields` 集合，记录 YAML 显式配置字段
- [x] 封装 `getXxxOrDefault()` 安全取值方法族（~25 个字段），避免空指针
- [x] 重写 `mergeWithTemplate()`：仅当前字段为 `null` 时继承模板参数，用户显式字段强制优先
- [x] 所有 Setter 方法写入显式字段标记逻辑（`explicitlySetFields.add(fieldName)`）
- [x] 新增 `List<String> allowedGrenadeTypes` 字段 + getter/setter/merge/兜底 `["frag", "flashbang"]`

### Task 2：EMWMConfigCache 模板加载 & 解析重构【已完成】

- [x] 彻底删除 `configs/elitemobs/` 旧模板回退路径，唯一加载路径：`plugins/EM-WM-Bridge/emwm_mob_templates.yml`
- [x] 新增 `parseTemplateSection()` 解析方法，直接读取 YAML 根节点 `templates:`
- [x] 实现驼峰键名自动映射 WM 横杠嵌套配置：`fireRateTicks`→`fire-rate`(20/tick)、`grenadeThrowRange`→`grenade-ai.max-range`、`grenadeCooldown`→`grenade-ai.cooldown-ticks`
- [x] YAML 解析增加 `contains()` 字段存在性判断，不存在字段保留 `null`
- [x] 实现 `weapon:` 单武器 ID 自动转换为武器池列表
- [x] 全字段双读模式：驼峰优先，横杠回退

### Task 3：MobWeaponManager 三级参数逻辑重构【已完成】

- [x] 在 `bindWeaponWithConfig` 执行前完成「怪物配置 + 模板配置」参数合并
- [x] 实现参数三级优先级解析：本地配置 > 模板 > WM 原生 API 拉取
- [x] 复用 `getFireRateMs()` 等原生方法读取武器原厂射速、弹匣、弹道参数
- [x] `generateWeapon()` null/AIR 防御 + `weaponExists()` 双保险
- [x] 拦截重复武器绑定，实体元数据标记防止重复创建实例

---

## P1 待完成任务（手雷功能闭环，当前阻塞 WMPlus 战术 AI）

### Task 4：allowedGrenadeTypes 字段全链路开发

#### 子任务 4.1 实体类扩展【已完成】
- [x] `EMWMWeaponConfig.java` 新增 `private List<String> allowedGrenadeTypes`，默认 `null`
- [x] getter/setter + 显式字段标记

#### 子任务 4.2 模板解析扩展【已完成】
- [x] `parseTemplateSection()` 增加 `allowedGrenadeTypes` 列表解析逻辑（`section.isList()`）
- [x] `parseEMWMConfig()` grenade-ai 段增加 `allowedGrenadeTypes` 解析

#### 子任务 4.3 模板合并逻辑适配【已完成】
- [x] `mergeWithTemplate()` 内：当前配置集合为 `null` 时继承模板手雷类型列表
- [x] 本地配置优先覆盖，非 null 不覆盖

#### 子任务 4.4 WM 参数兜底 + 合法性校验【已完成】
- [x] 怪物 + 模板均未配置时，默认兜底 `["frag", "flashbang"]`（`getAllowedGrenadeTypesOrDefault()`）
- [x] 过滤 WM 不存在的手雷 ID，`validateGrenadeTypesAgainstWM()` 调用 `WeaponMechanicsAPI.generateWeapon()` 校验 + 打印无效参数警告日志
- [x] 手雷类型参数合法性校验逻辑（`EMWMWeaponConfig.validateAllowedGrenadeTypes()` 通用校验 + `MobWeaponManager.validateGrenadeTypesAgainstWM()` WM存在性校验）

#### 子任务 4.5 武器绑定参数透传【已完成】
- [x] 将解析完成的手雷可用类型传入 WM 投掷物 AI 调用逻辑（写入实体元数据 `emwm_allowed_grenade_types`）
- [x] 在 `bindWeaponWithConfig()` 中将 `allowedGrenadeTypes` 写入实体元数据（先经WM校验过滤）
- [x] WMPlus GrenadeAI 模块读取实体元数据中的手雷白名单（通过 `entity.getMetadata("emwm_allowed_grenade_types")`）

---

## P2 优化 & 清理任务（代码规范 + 线上稳定性）

### Task 5：过期冗余代码清理【已完成】

- [x] 删除所有硬编码默认数值（固定 `fireRate=4.0` 等硬编码常量——保留 `validate()` 非法值回退及 `getXxxOrDefault()` 安全兜底，为四级安全网）
- [x] 确认 `configs/elitemobs/` 回退路径代码已全部清除（Grep 确认无残留引用）
- [x] 移除废弃调试分支、未使用的旧路径变量、重复工具方法
- [x] 收拢全局异常捕获，统一参数校验逻辑
- [x] 移除 MobWeaponManager 中独立的 WM 原生参数读取方法（6 个 resolve 方法收拢到 WeaponMetaCache 统一入口）

### Task 6：日志 & 调试能力优化【已完成】

- [x] 模板加载时打印完整物理路径、加载成功模板数量（`EMWMConfigCache.loadGlobalTemplates()` 已实现）
- [x] 每个参数输出来源日志（mob config / global template / WM native），支持 `debug-mode` 总开关控制（`bindWeaponWithConfig()` 紧凑参数来源日志）
- [x] 新增无效武器 ID、非法数值参数的警告日志（`validateGrenadeTypesAgainstWM()` 无效手雷ID警告）
- [x] 支持 `/emwmbridge info 怪物ID` 显示参数取值层级（通过元数据 `emwm_*` 键存取）

---

## P3 交付 & 部署任务

### Task 7：编译、打包、服务端部署验证【已完成】

- [x] Gradle 执行 `build -x test` 编译成功，仅保留过时 API 警告，无编译错误
- [x] 25 个测试类全部通过（java-plugin-test-engineer 自动修复 3 个失败）
- [x] JAR 包已生成于 `build/libs/EM-WM-Bridge-1.3.0.jar`
- [ ] 替换服务端 `plugins` 目录下旧 jar 包（需用户手动部署）
- [ ] 重启服务端执行重载、多场景功能验证（需用户手动操作）

---

## 任务依赖关系

| 任务 | 依赖 | 说明 |
|------|------|------|
| Task 1 | — | 基础实体改造，无前置依赖 |
| Task 2 | Task 1 | 缓存器需依赖实体类类型定义 |
| Task 3 | Task 2 | 武器管理层依赖配置缓存层就绪 |
| Task 4 | Task 1, Task 2 | 实体 + 模板解析就绪后才能全链路开发 |
| Task 5 | — | 可与 Task 4 并行执行 |
| Task 6 | — | 可与 Task 4、Task 5 并行执行 |
| Task 7 | Task 1~6 全部 | 所有编码任务完成后执行部署验收 |
