package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.engine.TarkovAIEngine;
import com.emwbridge.ai.squad.SquadManager;
import com.magmaguy.elitemobs.api.ElitePhaseSwitchEvent;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import com.magmaguy.elitemobs.mobconstructor.custombosses.PhaseBossEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 需求8 Boss 协同召唤 — 微型服务端 Harness（无 MockBukkit 外部依赖）。
 *
 * <p>模拟 weaponfx 的 MockBukkit 微服务端测试思路，但用 Mockito 自建"微型服务端"：
 * 1) 模拟 Bukkit {@link Server}/{@link PluginManager}/{@link BukkitScheduler}（含延迟任务捕获）；
 * 2) 内置极简事件总线，按 {@link EventPriority} 派发 {@link EventHandler} 监听器（等价于服务端触发事件）；
 * 3) {@link MockedStatic} 屏蔽 EliteMobs 静态工厂 {@code CustomBossEntity.createCustomBossEntity}，
 *    使召唤波次可在无真实 EliteMobs 运行时被观测。
 *
 * <p>以此端到端驱动 {@link BossCoordinationManager}：阶段切换事件 → 规则解析 → 阈值/冷却判定 →
 * 召唤波次 → 延迟编队（经需求2 编制 API）。等价于微服务端集成测试，可随 {@code ./gradlew test} 运行。
 *
 * <p>沙箱 Maven 镜像未收录 MockBukkit（org/mockbukkit/* 404），故采用本依赖无关 Harness；
 * 如需真·MockBukkit，可在能访问 Maven Central 的机器上启用 build.gradle 注释中的 testMockBukkit sourceSet。
 */
public class BossCoordinationHarness {

    // ==================== 极简事件总线 ====================
    private static final class ListenerEntry {
        final Listener listener;
        final Method method;
        final EventPriority priority;
        ListenerEntry(Listener l, Method m, EventPriority p) {
            this.listener = l;
            this.method = m;
            this.priority = p;
        }
    }

    private final List<ListenerEntry> busListeners = new ArrayList<>();

    /** 注册一个监听器：扫描其 @EventHandler 方法。 */
    void registerListener(Listener l) {
        for (Method m : l.getClass().getDeclaredMethods()) {
            EventHandler ann = m.getAnnotation(EventHandler.class);
            if (ann == null) continue;
            m.setAccessible(true);
            busListeners.add(new ListenerEntry(l, m, ann.priority()));
        }
    }

    /** 派发事件到所有匹配监听器（按优先级升序，MONITOR 最后）。 */
    void dispatchEvent(Event event) {
        busListeners.stream()
                .filter(e -> e.method.getParameterCount() == 1
                        && e.method.getParameterTypes()[0].isAssignableFrom(event.getClass()))
                .sorted(Comparator.comparingInt(e -> e.priority.ordinal()))
                .forEach(e -> {
                    try {
                        e.method.invoke(e.listener, event);
                    } catch (Exception ex) {
                        throw new RuntimeException("事件派发失败: " + event.getClass().getSimpleName(), ex);
                    }
                });
    }

    // ==================== 测试状态 ====================
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<CustomBossEntity> spawnedMinions = new ArrayList<>();

    private EMWMBridge plugin;
    private SquadManager squadManager;
    private BossCoordinationManager mgr;
    private Server mockServer;
    private PluginManager mockPluginManager;
    private MockedStatic<CustomBossEntity> customBossStatic;
    private AutoCloseable mocks;

    /** 启动微型服务端：注入 mock 插件/服务器/调度器，注册监听器，屏蔽 EliteMobs 静态工厂。 */
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);

        // 插件 → TarkovAIManager → TarkovAIEngine → SquadManager（编制 API mock）
        plugin = mock(EMWMBridge.class);
        TarkovAIManager ai = mock(TarkovAIManager.class);
        TarkovAIEngine engine = mock(TarkovAIEngine.class);
        squadManager = mock(SquadManager.class);
        when(plugin.getTarkovAIManager()).thenReturn(ai);
        when(ai.getEngine()).thenReturn(engine);
        when(engine.getSquadManager()).thenReturn(squadManager);
        // 微型服务端需提供插件 Logger，否则 BossCoordinationManager.spawnWave 的日志调用 NPE
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("BossCoordinationHarness"));

        // Bukkit Server / PluginManager / Scheduler
        mockServer = mock(Server.class);
        mockPluginManager = mock(PluginManager.class);
        when(mockServer.getPluginManager()).thenReturn(mockPluginManager);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(mockServer.getScheduler()).thenReturn(scheduler);
        // 捕获延迟任务（BossCoordinationManager 用 runTaskLater 1 tick 编队）
        when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenAnswer(inv -> {
            scheduledTasks.add(inv.getArgument(1));
            return mock(BukkitTask.class);
        });
        setBukkitServer(mockServer);

        // 被测对象 + 注册到微型事件总线
        mgr = new BossCoordinationManager(plugin);
        registerListener(mgr);

        // 屏蔽 EliteMobs 静态工厂：createCustomBossEntity 返回可观测的 mock minion
        customBossStatic = Mockito.mockStatic(CustomBossEntity.class);
        customBossStatic.when(() -> CustomBossEntity.createCustomBossEntity(anyString()))
                .thenAnswer(inv -> {
                    CustomBossEntity minion = mock(CustomBossEntity.class);
                    LivingEntity me = mock(LivingEntity.class);
                    when(me.isValid()).thenReturn(true);
                    when(me.getUniqueId()).thenReturn(UUID.randomUUID());
                    when(me.getLocation()).thenReturn(new Location(null, 0, 64, 0));
                    when(minion.getLivingEntity()).thenReturn(me);
                    spawnedMinions.add(minion);
                    return minion;
                });
    }

    /** 停止微型服务端并清理静态状态（Bukkit.server / 静态 mock），避免污染其他测试。 */
    public void cleanup() {
        try {
            if (customBossStatic != null) customBossStatic.close();
        } catch (Exception ignored) {
        }
        try {
            if (mocks != null) mocks.close();
        } catch (Exception ignored) {
        }
        clearBukkitServer();
        busListeners.clear();
        scheduledTasks.clear();
        spawnedMinions.clear();
    }

    // ==================== 配置注入 ====================

    /** 注入一条协同规则（包级可见字段，与 BossCoordinationManagerTest 同包访问）。 */
    public void injectRule(String boss, List<Double> triggers, int cooldown,
                           String squad, int count, String minion) {
        mgr.rules.put(boss.toLowerCase(),
                new BossCoordinationManager.CoordinationRule(boss, minion, squad, count, triggers, cooldown));
    }

    // ==================== 触发 ====================

    /**
     * 构造（真实或兜底 mock 的）ElitePhaseSwitchEvent 并经由微型事件总线派发，
     * 等价于服务端触发 Boss 阶段切换。
     */
    public void firePhaseSwitch(LivingEntity bossEntity, String bossName, double hpFraction) {
        CustomBossEntity boss = mock(CustomBossEntity.class);
        when(boss.getName()).thenReturn(bossName);
        when(boss.getLivingEntity()).thenReturn(bossEntity);
        PhaseBossEntity phase = mock(PhaseBossEntity.class);
        ElitePhaseSwitchEvent event;
        try {
            event = new ElitePhaseSwitchEvent(boss, phase);
        } catch (Throwable t) {
            // 兜底：个别 EliteMobs 版本事件构造依赖服务端上下文时，用 mock 事件替代
            event = mock(ElitePhaseSwitchEvent.class);
            when(event.getCustomBossEntity()).thenReturn(boss);
        }
        dispatchEvent(event);
    }

    /** 便捷构造 Boss 实体（hp = hpFraction × maxHealth）。同一实例可复用于冷却测试。 */
    public LivingEntity makeBossEntity(String name, double hpFraction) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        when(e.isValid()).thenReturn(true);
        when(e.getHealth()).thenReturn(hpFraction * 100.0);
        when(e.getMaxHealth()).thenReturn(100.0);
        when(e.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        return e;
    }

    /** 推进调度器：执行所有被延迟的任务（BossCoordinationManager 的编队调用在一次 tick 后）。 */
    public void flushScheduler() {
        List<Runnable> tasks = new ArrayList<>(scheduledTasks);
        scheduledTasks.clear();
        for (Runnable r : tasks) r.run();
    }

    // ==================== 断言辅助 ====================

    public int spawnedMinionCount() {
        return spawnedMinions.size();
    }

    public SquadManager getSquadManager() {
        return squadManager;
    }

    // ==================== Bukkit 静态注入 ====================

    private void setBukkitServer(Server server) {
        try {
            Field f = Bukkit.class.getDeclaredField("server");
            f.setAccessible(true);
            f.set(null, server);
        } catch (Exception e) {
            throw new RuntimeException("setBukkitServer failed", e);
        }
    }

    private void clearBukkitServer() {
        try {
            Field f = Bukkit.class.getDeclaredField("server");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception ignored) {
        }
    }
}
