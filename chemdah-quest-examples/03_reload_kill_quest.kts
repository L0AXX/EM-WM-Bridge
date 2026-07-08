/**
 * 任务：致命窗口
 * 描述：在精英怪换弹时击杀 3 个精英怪
 * 难度：★★★★☆
 *
 * 机制说明：精英怪换弹时会暴露弱点，利用这个窗口击杀
 * 完成条件：在 emwm_reloading=true 状态下击杀 3 个精英怪
 *
 * 核心接口：EMWMBridgeAPI.isReloading() + EntityDeathEvent
 */

import com.emwbridge.api.EMWMBridgeAPI
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.entity.LivingEntity

quest("lethal_window") {
    description {
        name = "致命窗口"
        lore = listOf(
            "§7在精英怪 §e换弹 §7时击杀它们",
            "§7抓住那稍纵即逝的窗口",
            "",
            "§d进度: §f${progress}/3"
        )
    }

    onStart {
        player.sendMessage("§e[任务] §f致命窗口 §7- 在精英怪换弹时击杀 3 个")
        player.sendMessage("§7提示: 精英怪弹匣打空后会换弹，此时是最佳时机")
    }

    // 监听实体死亡事件
    on<EntityDeathEvent> {
        val entity = event.entity as? LivingEntity ?: return@on
        val killer = entity.killer ?: return@on

        if (killer.uniqueId != player.uniqueId) return@on

        // 检查是否是 EMWM 精英怪
        if (!EMWMBridgeAPI.isEMWMMob(entity)) return@on

        // 核心判断：是否在换弹状态
        if (EMWMBridgeAPI.isReloading(entity)) {
            progress += 1
            player.sendMessage("§a[任务] §f完美! 在换弹窗口击杀! §7($progress/3)")

            if (progress >= 3) {
                complete()
            }
        } else {
            // 不在换弹状态，不给进度但提示
            player.sendMessage("§7[任务] §c精英怪不在换弹状态，不算!")
        }
    }

    // 额外功能：当附近的精英怪开始换弹时提示玩家
    on<PlayerMoveEvent> {
        if (cooldown("reload_detect", 10)) {
            val reloadingMobs = player.location.getNearbyLivingEntities(30.0)
                .filter { EMWMBridgeAPI.isEMWMMob(it) && EMWMBridgeAPI.isReloading(it) }

            if (reloadingMobs.isNotEmpty()) {
                val mob = reloadingMobs.first()
                val dist = player.location.distance(mob.location).toInt()
                player.sendMessage("§e⚠ §7附近 ${dist}格 有精英怪正在换弹!")
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f)
            }
        }
    }

    onComplete {
        player.sendMessage("§a[任务] §f致命窗口 §e完成!")
        player.sendMessage("§7奖励: §6经验 800 §7| §b金币 400")
        player.giveExp(800)
    }
}
