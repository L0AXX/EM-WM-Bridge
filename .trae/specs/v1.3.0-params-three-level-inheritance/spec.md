# EM-WM-Bridge v1.3.0 Spec：参数三级继承 + 全局兵种模板重构

## Why

当前 EM-WM-Bridge 存在核心缺陷：

- **bindWeaponWithConfig 强制硬编码默认射速 fireRate=4.0**，无法读取 WeaponMechanics 武器原生参数，所有武器射速统一，破坏武器平衡性；
- **配置字段使用基础值类型 int/double**，无法为 null，不能实现「怪物配置→全局模板→WM 原生参数」三级优先级继承；
- **全局兵种模板加载路径、YAML 解析结构、字段命名不统一**，模板功能无法正常启用；
- **手雷战术配置 allowedGrenadeTypes 字段未实现解析/合并/兜底逻辑**，WMPlus 投掷物 AI 功能缺失；
- **MobWeaponManager 参数解析未复用 WeaponMetaCache 的统一解析入口**，存在两套独立 WM 参数读取逻辑，代码重复且不一致。

本次重构目标：基于 Paper 1.21.4 + EliteMobs + WeaponMechanics 官方 API，实现三级参数优先级配置架构，落地全局兵种模板能力，让 AI 完整复用 WM 全武器原生属性，支持塔科夫多兵种差异化战术配置，同时清理历史过期冗余代码、收拢参数解析入口、提升插件性能与可维护性。

## What Changes

### 1. 配置实体层 — EMWMWeaponConfig.java

| 变更 | 详情 |
|------|------|
| 基础类型→包装类型 | 将 `int`/`double`/`boolean` 改为 `Integer`/`Double`/`Boolean`，支持 null 空值 |
| 显式字段标记集合 | `Set<String> explicitlySetFields`，区分「用户手动配置」与「未配置需继承上级」 |
| mergeWithTemplate() 重写 | 仅当前字段为 null 时继承模板参数；用户显式配置优先不被覆盖；全量字段（射击/弹药/战术/行为/投掷物/动画/耐久/配件/桥接控制）统一合并 |
| 安全取值方法 | `getXxxOrDefault()` 方法族提供三级参数兜底（配置值→默认值） |
| allowedGrenadeTypes 字段 | 新增 `List<String>` 类型字段，含 getter/setter/merge/兜底默认值 `["frag", "flashbang"]` |
| 武器池单武器自动转换 | 支持单 `weapon` 字段自动转为武器池列表 |

### 2. 配置缓存层 — EMWMConfigCache.java

| 变更 | 详情 |
|------|------|
| 模板路径修正 | 唯一路径 `plugins/EM-WM-Bridge/emwm_mob_templates.yml`，**彻底删除** `configs/elitemobs/` 历史回退路径 |
| parseTemplateSection() 新增 | 解析根节点 `templates:` 扁平 YAML 结构，支持驼峰命名字段自动映射（`fireRateTicks`→`fire-rate` 反向换算等） |
| 字段映射表 | `weapon`→`weaponPool`、`fireRateTicks`→`fireRate`(20/ticks)、`grenadeThrowRange`→`grenadeMaxRange`、`grenadeCooldown`→`grenadeCooldownTicks` 等 |
| 配置解析仅赋值存在字段 | 不存在 YAML 字段时保留 null，不覆盖默认值，用于参数继承判断 |
| 单武器自动转池 | `section.contains("weapon")` 时自动 `config.setWeaponPool(Collections.singletonList(weapon))` |
| 向后兼容双读模式 | 驼峰优先，横杠回退；`emwmTemplate`→`emwm_template`、`weaponPool`→`weapon-pool`、`fireRateTicks`→`fire-rate` 等 40+ 字段 |
| allowedGrenadeTypes 解析 | grenade-ai 段 `section.isList("allowedGrenadeTypes")` + 模板段 `section.isList("allowedGrenadeTypes")` 双路径解析 |

### 3. 武器管理层 — MobWeaponManager.java

| 变更 | 详情 |
|------|------|
| bindWeaponWithConfig 三级参数 | 怪物 emwm 显式配置 > 全局兵种模板配置 > WM 原生参数（WeaponMechanics API 读取） |
| 私有解析方法重构 | `resolveMagazineSize()`、`resolveReloadDuration()`、`resolveFireRateMs()`、`resolveBaseSpread()`、`resolveAdsSpreadMultiplier()`、`resolveMaxRange()`、`resolveEffectiveRange()` — 均走配置 null 检查→WM 原生兜底 |
| 武器生成 null 防御 | `generateWeapon()` 返回 null 或 AIR 时跳过绑定并日志警告 |
| weaponExists 双保险 | 先 `config.getString()` 查 WM 配置，失败后 `generateWeapon()` 二次验证 |
| 参数来源日志埋点 | `plugin.debug()` 输出每项解析后参数的取值与来源 |

### 4. 武器元数据缓存 — WeaponMetaCache.java

| 变更 | 详情 |
|------|------|
| 三级参数统一解析入口 | `resolveFireRateMs(config, template, weaponId)`、`resolveMagazineSize()` 等 9 个方法，统一封装三级优先级逻辑 |
| 延迟重试机制 | 首轮预加载失败武器保存到 `pendingWeaponIds`，60tick 后自动重试 |
| NativeWeaponData 全参预读 | 射速/弹匣/换弹/散布/ADS倍率/弹速/穿透/后坐力/射程/消音/动作延迟共 13 项 WM 原生参数 |
| 存在性校验 | `WeaponMechanicsAPI.generateWeapon()` 验证武器可用性 |

### 5. 清理优化范围

- 删除所有硬编码默认参数（固定 4.0 射速等）
- 移除历史多路径模板兼容冗余代码（`configs/elitemobs/` 回退路径）
- 移除旧版嵌套 `emwm:` 模板结构兼容逻辑
- 收拢零散调试日志、重复实体查询工具方法

## Impact

### 正向影响
- 常规 SCAV 兵种不配置参数时自动使用 WM 原厂武器射速/弹道/弹匣属性，武器平衡性完全保留
- PMC 精英、BOSS 可通过模板/本地配置自定义属性，实现兵种强度差异化
- 全局模板提供一级中继层，避免重复编辑同类型怪物配置

### 兼容性
- 无反向兼容影响：历史所有自定义配置自动保留优先级，无需批量修改旧配置
- 全部旧横杠字段名保留向后兼容读取，仅新模板不兼容旧格式

### 扩展收益
- 后续 WM 弹药/配件/耐久/动画功能可基于当前三级配置架构无缝扩展
- 参数来源日志埋点直接支持调试 `/emwmbridge info` 查询取值层级

## ADDED Requirements

### Requirement: 三级参数优先级继承

系统 SHALL 实现「怪物 emwm 配置 > 全局兵种模板配置 > WM 武器原生参数 > 安全兜底默认值」四级优先级（实际三级用户配置 + WM 原生）。

#### Scenario: 怪物有显式配置
- **WHEN** 怪物 emwm 配置段中有 `shooting.spread: 2.0`
- **THEN** 最终 spread = 2.0（怪物配置优先），日志输出来源为 "mob config"

#### Scenario: 怪物未配置，模板有配置
- **WHEN** 怪物 emwm 配置段中无 `shooting.spread`，模板 `scav_rifle` 中有 `spread: 0.16`
- **THEN** 最终 spread = 0.16（模板配置），日志输出来源为 "global template"

#### Scenario: 怪物和模板均未配置
- **WHEN** 怪物 config 和模板 template 中 `spread` 均为 null
- **THEN** 最终 spread = WeaponMetaCache 中 WM 原生 `baseSpread` 值（或兜底 3.0）

### Requirement: 全局兵种模板加载

系统 SHALL 从唯一路径 `plugins/EM-WM-Bridge/emwm_mob_templates.yml` 加载全局兵种模板，格式为根节点 `templates:` 扁平 YAML 结构。

#### Scenario: 模板文件存在且格式正确
- **WHEN** `emwm_mob_templates.yml` 存在且包含 `templates:` 根节点
- **THEN** SHALL 逐条解析模板，每个模板名称映射为 EMWMWeaponConfig 对象
- **THEN** SHALL 输出日志 "成功加载全局兵种模板文件，共解析 N 套兵种模板"

#### Scenario: 模板文件不存在
- **WHEN** `emwm_mob_templates.yml` 不存在
- **THEN** SHALL 输出警告日志，跳过模板加载，不中断插件启动

### Requirement: allowedGrenadeTypes 字段

系统 SHALL 支持为怪物/模板/全局三级配置手雷类型白名单。

#### Scenario: 怪物显式配置手雷类型
- **WHEN** 怪物 emwm `special.grenade-ai.allowedGrenadeTypes: ["frag", "flashbang"]`
- **THEN** AI 仅使用 `frag` 和 `flashbang` 两种手雷

#### Scenario: 怪物未配置，模板有配置
- **WHEN** 怪物无 `allowedGrenadeTypes`，模板中有
- **THEN** mergeWithTemplate 从模板继承手雷类型列表

#### Scenario: 全部未配置
- **WHEN** 怪物和模板均无 `allowedGrenadeTypes`
- **THEN** `getAllowedGrenadeTypesOrDefault()` 返回 `["frag", "flashbang"]`

### Requirement: 字段命名驼峰统一

系统 SHALL 在怪物 emwm 配置中统一使用驼峰键名，同时保留横杠键名向后兼容。

#### Scenario: 驼峰读取优先
- **WHEN** YAML 中存在 `weaponPool` 键名
- **THEN** parseEMWMConfig 优先读取驼峰值

#### Scenario: 横杠回退读取
- **WHEN** YAML 中无 `weaponPool` 但存在 `weapon-pool`
- **THEN** parseEMWMConfig 回退读取横杠值

## MODIFIED Requirements

### Requirement: WeaponMetaCache 预加载

系统 SHALL 在服务端启动时预加载所有用到的 WM 武器元数据，采用延迟重试机制解决时序问题。

**原要求**：一次加载，失败不重试
**新要求**：首轮加载失败武器进入 `pendingWeaponIds` 集合，60tick 后自动重试

### Requirement: MobWeaponManager.bindWeaponWithConfig

系统 SHALL 复用 WeaponMetaCache 的 resolve 方法族进行参数解析，消除两套独立 WM 参数读取逻辑。

**原实现**：`resolveFireRateMs()` 内联调用 `getFireRateMs()`（直接读 WM config）
**新实现**：先查 config 非 null → 再查 template 非 null → 最后调 WM 原生（MobWeaponManager 私有 resolve 方法本身已是三级，但与 WeaponMetaCache 的 resolve 方法重复）

**BREAKING**：内部实现变化，外部调用 API 不变

## REMOVED Requirements

### Requirement: 旧版模板多路径加载

**Reason**：标准化为唯一路径 `plugins/EM-WM-Bridge/emwm_mob_templates.yml`，消除混乱
**Migration**：用户需将模板文件移动到新路径，格式改为根节点 `templates:` 扁平结构

### Requirement: 硬编码固定射速 4.0

**Reason**：三级参数继承实现后，未配置的武器直接从 WM 读取原生射速，不再需要硬编码默认值
**Migration**：自动生效，无需用户操作
