/**
 * 任务：耳鸣猎手
 * 描述：在精英怪耳鸣状态下击杀 2 个精英怪
 * 难度：★★★☆☆
 *
 * 机制说明：精英怪被闪光弹致盲后会进入耳鸣状态，
 * 此时它视野受限、反应迟钝，是击杀良机
 *
 * 完成条件：在 emwm_tinnitus=true 状态下击杀 2 个精英怪
 *
 * 核心接口：EMWMBridgeAPI.hasTinnitus()
 */

import com.emwbridge.api.EMWMBridgeAPI
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.entity.LivingEntity

quest("tinnitus_hunter") {
    description {
        name = "耳鸣猎手"
        lore = listOf(
            "§7先用 §e闪光弹 §7致盲精英怪",
            "§7在其耳鸣状态下完成击杀",
            "",
            "§d进度: §f${progress}/2"
        )
    }

    onStart {
        player.sendMessage("§e[任务] §f耳鸣猎手 §7- 在精英怪耳鸣时击杀 2 个")
        player.sendMessage("§7提示: 对精英怪投掷闪光弹，致盲后迅速击杀")
    }

    on<EntityDeathEvent> {
        val entity = event.entity as? LivingEntity ?: return@on
        val killer = entity.killer ?: return@on

        if (killer.uniqueId != player.uniqueId) return@on
        if (!EMWMBridgeAPI.isEMWMMob(entity)) return@on

        if (EMWMBridgeAPI.hasTinnitus(entity)) {
            progress += 1
            player.sendMessage("§a[任务] §f耳鸣击杀! §7($progress/2)")

            if (progress >= 2) {
                complete()
            }
        }
    }

    // 检测附近耳鸣中的精英怪
    on<PlayerMoveEvent> {
        if (cooldown("tinnitus_detect", 20)) {
            val tinnitusMobs = player.location.getNearbyLivingEntities(25.0)
                .filter { EMWMBridgeAPI.isEMWMMob(it) && EMWMBridgeAPI.hasTinnitus(it) }

            if (tinnitusMobs.isNotEmpty()) {
                player.sendActionBar("§e⚡ 附近有耳鸣中的精英怪，出击!")
            }
        }
    }

    onComplete {
        player.sendMessage("§a[任务] §f耳鸣猎手 §e完成!")
        player.sendMessage("§7奖励: §6经验 600 §7| §b金币 300")
        player.giveExp(600)
    }
}
