# RCON 黑盒测试方案 — 落地概览

## 完成内容

### 1. 插件内置测试命令 `/emwm test`
- **文件**: `src/main/java/com/emwbridge/test/PluginTestRunner.java`
- **10 个测试场景**: 插件健康 → 配置缓存 → 配置重载 → 武器绑定 → 元数据检查 → 弹药检查 → 射击 → 弹药递减 → 武器解绑 → AI引擎
- **输出**: `[EMWM_TEST_RESULT]{json}` 格式，供 RCON 脚本解析
- **特性**: 同步执行、自动清理测试实体、每项测试带耗时统计

### 2. Python RCON 黑盒测试脚本
- **文件**: `tests/rcon_blackbox_test.py`
- **零外部依赖**: 纯 Python 实现 RCON 协议
- **用法**: `python tests/rcon_blackbox_test.py --password <密码>`
- **退出码**: 0=全通过, 1=有失败, 2=连接错误

### 3. CI Docker 集成测试
- **文件**: `.github/workflows/ci.yml` — 新增 `integration-test` job
- **触发**: `workflow_dispatch`（手动）
- **流程**: Docker Paper 1.21.4 → 部署插件 → 等待就绪 → RCON 执行测试 → 收集结果

### 4. 生产代码修复
- `MobWeaponManager.shootEffect()`: 添加 `muzzle == null || target == null` 防御性检查

## 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/java/com/emwbridge/test/PluginTestRunner.java` | 新增 | 10 场景测试运行器 |
| `src/main/java/com/emwbridge/EMWMBridge.java` | 修改 | 添加 test 命令 |
| `src/main/resources/plugin.yml` | 修改 | usage 更新 |
| `src/main/java/com/emwbridge/managers/MobWeaponManager.java` | 修改 | shootEffect null 检查 |
| `tests/rcon_blackbox_test.py` | 新增 | RCON 测试脚本 |
| `.github/workflows/ci.yml` | 修改 | 添加 integration-test job |
| `.gitignore` | 修改 | 排除 server-plugins/ |

## 快速使用

### 本地测试
```bash
# 1. 部署插件到测试服务器
./gradlew deploy

# 2. 启动服务器，确保 server.properties 中启用了 RCON:
#    enable-rcon=true
#    rcon.password=你的密码
#    rcon.port=25575

# 3. 运行黑盒测试
python tests/rcon_blackbox_test.py --host localhost --port 25575 --password 你的密码
```

### CI 集成测试
1. 在仓库根目录创建 `server-plugins/` 目录
2. 放入 EliteMobs.jar、WeaponMechanics.jar、MechanicsCore.jar
3. GitHub Actions → Integration Test → Run workflow
