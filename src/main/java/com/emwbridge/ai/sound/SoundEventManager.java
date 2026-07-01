package com.emwbridge.ai.sound;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.perception.AIVisionManager;
import com.emwbridge.ai.perception.AuditoryPerception;
import com.emwbridge.ai.perception.SoundSource;
import com.emwbridge.ai.perception.SoundType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

/**
 * 全局声音事件管理器 — 监听 MC 事件，生成 SoundSource 并广播至附近 AI
 * 
 * 使用 SoundType 分级 + AIVisionManager 统一处理
 */
public class SoundEventManager implements Listener {

    private final EMWMBridge plugin;
    private final AIVisionManager aiVisionManager;
    private final AuditoryPerception auditoryPerception;

    private final Map<UUID, Long> lastFootstepTime = new HashMap<>();

    public SoundEventManager(EMWMBridge plugin, AIVisionManager aiVisionManager) {
        this.plugin = plugin;
        this.aiVisionManager = aiVisionManager;
        this.auditoryPerception = aiVisionManager.getAuditory();
    }

    public void registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ==================== MC 事件监听 ====================

    @EventHandler
    public void onExplosion(ExplosionPrimeEvent event) {
        Location loc = event.getEntity().getLocation();
        SoundSource source = new SoundSource(loc, SoundType.EXPLOSION, 1.0);
        broadcastSound(source, loc);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player) return;
        Location loc = event.getEntity().getLocation();
        // 伤害声源 — 使用 THROWABLE 作为近似（30格、不可穿墙）
        SoundSource source = new SoundSource(loc, SoundType.THROWABLE, 0.5);
        broadcastSound(source, loc);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        // 方块破坏 — 不可穿墙、中距
        SoundSource source = new SoundSource(loc, SoundType.THROWABLE, 0.4);
        broadcastSound(source, loc);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        // 门交互视为 DOOR 声源
        var mat = event.getClickedBlock().getType();
        if (mat.name().contains("DOOR") || mat.name().contains("GATE")
                || mat.name().contains("TRAPDOOR")) {
            Location loc = event.getClickedBlock().getLocation();
            SoundSource source = new SoundSource(loc, SoundType.DOOR, 0.8);
            broadcastSound(source, loc);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // 玩家脚步 — 使用 tick 计数冷却避免高频
        UUID puid = event.getPlayer().getUniqueId();
        long currentTick = Bukkit.getCurrentTick();

        if (!auditoryPerception.canHearFootstep(puid, currentTick)) return;
        auditoryPerception.recordFootstep(puid, currentTick);

        SoundType footstepType;
        if (event.getPlayer().isSneaking()) {
            footstepType = SoundType.FOOTSTEP_SNEAK;
        } else if (event.getPlayer().isSprinting()) {
            footstepType = SoundType.FOOTSTEP_SPRINT;
        } else {
            footstepType = SoundType.FOOTSTEP_WALK;
        }

        Location loc = event.getPlayer().getLocation();
        double loudness = event.getPlayer().isSprinting() ? 0.8 : 0.5;
        SoundSource source = new SoundSource(loc, footstepType, loudness);
        broadcastSound(source, loc);
    }

    // ==================== 外部触发（枪声等） ====================

    /**
     * 枪声事件 — 由 WeaponMechanics 或射击系统触发
     */
    public void onGunshot(Location source, boolean suppressed) {
        SoundType type = suppressed
                ? SoundType.GUNSHOT_SUPPRESSED_SUPERSONIC
                : SoundType.GUNSHOT_UNSUPPRESSED;
        SoundSource soundSource = new SoundSource(source, type, 0.9);
        broadcastSound(soundSource, source);

        // 无消音枪声对所有听到的 AI 施加少量视觉警觉
        if (!suppressed) {
            // 无需额外操作 — AuditoryPerception 已将听觉暴露值加入 ExposureData
        }
    }

    /**
     * 投掷物落地声
     */
    public void onThrowableLand(Location source) {
        SoundSource soundSource = new SoundSource(source, SoundType.THROWABLE, 0.7);
        broadcastSound(soundSource, source);
    }

    /**
     * 自定义声源广播 — 供其他模块直接调用
     */
    public void broadcastCustomSound(Location source, SoundType type, double loudness) {
        SoundSource soundSource = new SoundSource(source, type, loudness);
        broadcastSound(soundSource, source);
    }

    // ==================== 广播 ====================

    private void broadcastSound(SoundSource source, Location origin) {
        if (!aiVisionManager.isAuditoryEnabled()) return;

        var world = origin.getWorld();
        if (world == null) return;

        // 收集附近 AI 实体
        List<LivingEntity> nearbyAI = world.getEntitiesByClass(LivingEntity.class).stream()
                .filter(e -> e.hasMetadata("emwm_ai_enabled"))
                .toList();

        if (nearbyAI.isEmpty()) return;

        // 委托 AIVisionManager 统一广播
        aiVisionManager.broadcastSound(source, nearbyAI);
    }

    // ==================== 生命周期 ====================

    public void shutdown() {
        lastFootstepTime.clear();
    }
}
