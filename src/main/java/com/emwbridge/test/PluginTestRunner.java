package com.emwbridge.test;

import com.emwbridge.EMWMBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件内置黑盒测试运行器。
 *
 * 通过 /emwm test 命令触发，执行 11 项核心功能验证，
 * 输出结构化 JSON 供 RCON 外部脚本解析。
 *
 * 测试场景覆盖：
 *  1. 插件健康检查     — 所有管理器已初始化
 *  2. 配置缓存检查     — EMWMConfigCache 已加载怪物配置和模板
 *  3. 配置重载         — 配置参数可读取，管理器可重载
 *  4. 武器绑定         — 生成实体、绑定武器、验证 weaponCache
 *  5. 元数据检查       — 验证 emwm_weapon / emwm_ammo 等 metadata
 *  6. 弹药初始值检查   — 绑定后弹药 = 弹匣容量
 *  7. 射速合理性校验   — 射速从 WM 配置正确读取，不是 300ms 兜底
 *  8. 武器射击         — shoot() 返回 true，无异常
 *  9. 弹药递减验证     — 射击后弹药减少 1
 * 10. 武器解绑         — unbindWeapon() 后 weaponCache 清空
 * 11. AI 引擎状态      — TarkovAIManager 运行中
 *
 * JSON 输出格式：
 * {"status":"complete","total":10,"passed":9,"failed":1,"duration_ms":123,
 *  "version":"0.3.0-alpha","tests":[{"name":"plugin_health","passed":true,
 *  "duration_ms":1,"detail":"all_managers_ok"},...]}
 */
public class PluginTestRunner {

    private final EMWMBridge plugin;
    private final List<TestResult> results = new ArrayList<>();
    private LivingEntity testEntity = null;

    public PluginTestRunner(EMWMBridge plugin) {
        this.plugin = plugin;
    }

    /**
     * 执行全部测试，返回 JSON 字符串（供 RCON 解析）。
     * 同时通过 plugin.getLogger() 输出可读格式到控制台。
     * 前缀 [EMWM_TEST_RESULT] 供 RCON 脚本定位。
     */
    public String run() {
        long startTime = System.currentTimeMillis();

        runTest("plugin_health", this::testPluginHealth);
        runTest("config_cache", this::testConfigCache);
        runTest("config_reload", this::testConfigReload);
        runTest("weapon_bind", this::testWeaponBind);
        runTest("metadata_check", this::testMetadata);
        runTest("weapon_ammo_check", this::testWeaponAmmoCheck);
        runTest("weapon_fire_rate", this::testWeaponFireRate);
        runTest("weapon_shoot", this::testWeaponShoot);
        runTest("weapon_ammo_decrement", this::testWeaponAmmoDecrement);
        runTest("weapon_unbind", this::testWeaponUnbind);
        runTest("ai_engine", this::testAIEngine);

        cleanup();

        long totalDuration = System.currentTimeMillis() - startTime;
        String json = "[EMWM_TEST_RESULT]" + buildJson(totalDuration);

        // 输出可读格式到控制台
        int passed = 0;
        for (TestResult r : results) {
            if (r.passed) passed++;
        }
        int failed = results.size() - passed;

        plugin.getLogger().info("§e=== EMWM 黑盒测试结果 ===");
        plugin.getLogger().info("§f总计: " + results.size() + " | §a通过: " + passed + " | §c失败: " + failed + " | §7耗时: " + totalDuration + "ms");
        for (TestResult r : results) {
            String status = r.passed ? "§a✓ PASS" : "§c✗ FAIL";
            String detail = r.passed ? " §7(" + r.detail + ")" : " §c(" + r.detail + ")";
            plugin.getLogger().info(status + " §f" + r.name + detail + " §7[" + r.durationMs + "ms]");
        }

        return json;
    }

    // ================================================================
    // 测试框架
    // ================================================================

    @FunctionalInterface
    private interface Test {
        String execute() throws Exception;
    }

    private void runTest(String name, Test test) {
        long start = System.currentTimeMillis();
        try {
            String detail = test.execute();
            long duration = System.currentTimeMillis() - start;
            results.add(new TestResult(name, true, detail != null ? detail : "", duration));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            results.add(new TestResult(name, false, detail, duration));
        }
    }

    // ================================================================
    // 测试场景
    // ================================================================

    /** 1. 验证所有核心管理器已初始化 */
    private String testPluginHealth() throws Exception {
        if (plugin.getMobWeaponManager() == null)
            throw new Exception("MobWeaponManager is null");
        if (plugin.getTarkovAIManager() == null)
            throw new Exception("TarkovAIManager is null");
        if (plugin.getExtremeEventManager() == null)
            throw new Exception("ExtremeEventManager is null");
        if (plugin.getEMWMConfigCache() == null)
            throw new Exception("EMWMConfigCache is null");
        if (plugin.getDebugManager() == null)
            throw new Exception("DebugManager is null");
        return "all_managers_ok";
    }

    /** 2. 验证配置缓存已加载怪物配置和全局模板 */
    private String testConfigCache() throws Exception {
        var cache = plugin.getEMWMConfigCache();
        int mobFiles = cache.getLoadedMobFiles().size();
        int templates = cache.getGlobalTemplateNames().size();
        if (mobFiles == 0 && templates == 0)
            throw new Exception("No mob configs or templates loaded");
        return "mobFiles=" + mobFiles + ",templates=" + templates;
    }

    /** 3. 验证配置参数可读取且管理器可重载 */
    private String testConfigReload() throws Exception {
        var config = plugin.getConfig();
        String defaultWeapon = config.getString("weapons.default-weapon");
        if (defaultWeapon == null || defaultWeapon.isEmpty())
            throw new Exception("weapons.default-weapon not configured");

        List<?> scavPool = config.getStringList("weapons.scav-pool");
        if (scavPool.isEmpty())
            throw new Exception("weapons.scav-pool is empty");

        // 验证管理器可安全重载
        plugin.getMobWeaponManager().reload();
        return "default=" + defaultWeapon + ",scavPool=" + scavPool.size();
    }

    /** 4. 生成测试实体并绑定武器 */
    private String testWeaponBind() throws Exception {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty())
            throw new Exception("No worlds loaded");

        World world = worlds.get(0);
        Location loc = world.getSpawnLocation().clone().add(0, 10, 0);

        testEntity = (LivingEntity) world.spawnEntity(loc, EntityType.ZOMBIE);
        testEntity.setAI(false);
        testEntity.setCustomName("EMWM_TEST_ENTITY");
        testEntity.setCustomNameVisible(false);

        String weapon = plugin.getConfig().getString("weapons.default-weapon", "AK_47");
        boolean bound = plugin.getMobWeaponManager().bindWeapon(testEntity, weapon);
        if (!bound)
            throw new Exception("bindWeapon returned false for " + weapon);

        if (!plugin.getMobWeaponManager().hasWeapon(testEntity))
            throw new Exception("hasWeapon=false after successful bind");

        return "weapon=" + weapon + ",entity=" + testEntity.getUniqueId();
    }

    /** 5. 验证实体 metadata 已正确设置 */
    private String testMetadata() throws Exception {
        if (testEntity == null)
            throw new Exception("testEntity is null (bind test may have failed)");

        // 检查 emwm_weapon
        List<MetadataValue> weaponMeta = testEntity.getMetadata("emwm_weapon");
        if (weaponMeta.isEmpty())
            throw new Exception("emwm_weapon metadata not set");
        String weaponTitle = weaponMeta.get(0).asString();

        // 检查 emwm_ammo
        List<MetadataValue> ammoMeta = testEntity.getMetadata("emwm_ammo");
        if (ammoMeta.isEmpty())
            throw new Exception("emwm_ammo metadata not set");
        int ammo = ammoMeta.get(0).asInt();

        // 检查 emwm_durability
        List<MetadataValue> durMeta = testEntity.getMetadata("emwm_durability");
        if (durMeta.isEmpty())
            throw new Exception("emwm_durability metadata not set");

        return "weapon=" + weaponTitle + ",ammo=" + ammo + ",durability_set=true";
    }

    /** 6. 验证绑定后弹药 = 弹匣容量 */
    private String testWeaponAmmoCheck() throws Exception {
        if (testEntity == null)
            throw new Exception("testEntity is null");

        int ammo = plugin.getMobWeaponManager().getCurrentAmmo(testEntity);
        int magSize = plugin.getMobWeaponManager().getMagazineSize(testEntity);

        if (ammo <= 0)
            throw new Exception("ammo=" + ammo + " (expected >0 after bind)");
        if (magSize <= 0)
            throw new Exception("magSize=" + magSize + " (expected >0)");
        if (ammo != magSize)
            throw new Exception("ammo(" + ammo + ") != magSize(" + magSize + ") after fresh bind");

        return "ammo=" + ammo + ",magSize=" + magSize;
    }

    /** 7. 验证射速从 WM 配置正确读取（不是全部 300ms 兜底） */
    private String testWeaponFireRate() throws Exception {
        if (testEntity == null)
            throw new Exception("testEntity is null");

        long fireRateMs = plugin.getMobWeaponManager().getFireRateMs(testEntity);

        // 射速必须在合理范围内：50ms(20发/秒) ~ 3000ms(3秒1发)
        if (fireRateMs < 50)
            throw new Exception("fireRateMs=" + fireRateMs + " (too fast, <50ms, likely config parse error)");
        if (fireRateMs > 3000)
            throw new Exception("fireRateMs=" + fireRateMs + " (too slow, >3000ms, likely fallback)");

        // 关键校验：如果射速恰好是 300ms，极可能是配置路径读不到走了兜底
        // AK_47 实际应为 100ms（10发/秒），M4A1 应为 ~77ms（13发/秒）
        // 300ms 对应 3.33发/秒，在常见武器中几乎不会出现
        String weaponTitle = plugin.getMobWeaponManager().getWeaponTitle(testEntity);
        if (fireRateMs == 300L) {
            throw new Exception("fireRateMs=300 (SUSPICIOUS: likely fallback! weapon=" + weaponTitle
                    + " — check WM config path: Shoot.Fully_Automatic_Shots_Per_Second)");
        }

        return "fireRate=" + fireRateMs + "ms (" + String.format("%.1f", 1000.0 / fireRateMs) + " shots/s),weapon=" + weaponTitle;
    }

    /** 7. 执行射击并验证返回 true */
    private String testWeaponShoot() throws Exception {
        if (testEntity == null)
            throw new Exception("testEntity is null");

        Location target = testEntity.getLocation().clone().add(5, 1, 5);
        boolean shot = plugin.getMobWeaponManager().shoot(testEntity, target);

        if (!shot)
            throw new Exception("shoot() returned false (check WM dependency or cooldown)");

        return "shot_ok,target=" + target.getBlockX() + "," + target.getBlockY() + "," + target.getBlockZ();
    }

    /** 8. 验证射击后弹药减少 */
    private String testWeaponAmmoDecrement() throws Exception {
        if (testEntity == null)
            throw new Exception("testEntity is null");

        int ammoAfter = plugin.getMobWeaponManager().getCurrentAmmo(testEntity);
        int magSize = plugin.getMobWeaponManager().getMagazineSize(testEntity);

        if (ammoAfter >= magSize)
            throw new Exception("ammo not decremented: " + ammoAfter + " >= " + magSize);

        int expected = magSize - 1;
        if (ammoAfter != expected)
            throw new Exception("ammo=" + ammoAfter + " (expected " + expected + ")");

        return "ammo_after_shoot=" + ammoAfter + " (was " + magSize + ")";
    }

    /** 9. 解绑武器并验证清理 */
    private String testWeaponUnbind() throws Exception {
        if (testEntity == null)
            throw new Exception("testEntity is null");

        plugin.getMobWeaponManager().unbindWeapon(testEntity);

        if (plugin.getMobWeaponManager().hasWeapon(testEntity))
            throw new Exception("hasWeapon=true after unbind");

        // 验证 metadata 已清除
        if (!testEntity.getMetadata("emwm_weapon").isEmpty())
            throw new Exception("emwm_weapon metadata still present after unbind");

        return "unbind_ok,metadata_cleared=true";
    }

    /** 10. 验证 AI 引擎运行中 */
    private String testAIEngine() throws Exception {
        var aiManager = plugin.getTarkovAIManager();
        if (aiManager == null)
            throw new Exception("TarkovAIManager is null");

        int activeCount = aiManager.getActiveCount();
        if (activeCount < 0)
            throw new Exception("activeCount=" + activeCount + " (negative)");

        return "active_ai=" + activeCount;
    }

    // ================================================================
    // 清理与 JSON 输出
    // ================================================================

    private void cleanup() {
        if (testEntity != null && !testEntity.isDead()) {
            testEntity.remove();
        }
        testEntity = null;
    }

    private String buildJson(long totalDuration) {
        int passed = 0;
        for (TestResult r : results) {
            if (r.passed) passed++;
        }
        int failed = results.size() - passed;

        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"status\":\"complete\"");
        sb.append(",\"total\":").append(results.size());
        sb.append(",\"passed\":").append(passed);
        sb.append(",\"failed\":").append(failed);
        sb.append(",\"duration_ms\":").append(totalDuration);
        sb.append(",\"version\":\"").append(escapeJson(plugin.getDescription().getVersion())).append("\"");
        sb.append(",\"tests\":[");

        for (int i = 0; i < results.size(); i++) {
            TestResult r = results.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escapeJson(r.name)).append("\"");
            sb.append(",\"passed\":").append(r.passed);
            sb.append(",\"duration_ms\":").append(r.durationMs);
            sb.append(",\"detail\":\"").append(escapeJson(r.detail)).append("\"}");
        }

        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static class TestResult {
        final String name;
        final boolean passed;
        final String detail;
        final long durationMs;

        TestResult(String name, boolean passed, String detail, long durationMs) {
            this.name = name;
            this.passed = passed;
            this.detail = detail;
            this.durationMs = durationMs;
        }
    }
}
