# EM-WM-Bridge

Minecraft Paper 1.21.4 / Folia 插件 — 桥接 EliteMobs 与 WeaponMechanics，实现塔科夫式 AI 战斗。

## 环境要求

- **JDK 21**（必须）
- **Gradle 9.5.1**（已包含 wrapper，无需单独安装）

## 构建

```bash
# Windows
./gradlew.bat build

# Linux / macOS
./gradlew build
```

构建产物：`build/libs/EM-WM-Bridge-0.3.0-alpha.jar`

## 测试

```bash
# 全部测试
./gradlew test

# 编译 + 测试 + 覆盖率门禁
./gradlew check
```

覆盖率报告：`build/reports/jacoco/html/index.html`

## 部署到测试服务器

```bash
# 方式 1: 命令行参数指定路径
./gradlew deploy -PserverPlugins=/path/to/server/plugins

# 方式 2: 环境变量
export EMWM_SERVER_PLUGINS=/path/to/server/plugins
./gradlew deploy

# 方式 3: 默认部署到 ./plugins-test/
./gradlew deploy
```

## 依赖

| 依赖 | 版本 | 来源 | 用途 |
|------|------|------|------|
| Paper API | 1.21.4 | Maven Central (papermc) | Bukkit API |
| WeaponMechanics | 4.3.0 | libs/ (本地 jar) | 武器系统 |
| MechanicsCore | 4.3.0 | libs/ (本地 jar) | WM 依赖 |
| EliteMobs | - | libs/ (本地 jar) | Elite 怪物系统 |

## 版本

当前：`0.3.0-alpha`（功能不完整，API 不稳定）

版本号定义在 `build.gradle` 中，`plugin.yml` 需同步更新。

## CI

GitHub Actions workflow 在 `.github/workflows/ci.yml`，每次 push 自动执行：
编译 → 测试 → 覆盖率门禁 → 构建 jar
