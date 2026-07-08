/**
 * 任务：Boss 猎人
 * 描述：击杀 3 个 Boss 级精英怪
 * 难度：★★★★★
 *
 * 触发条件：玩家等级达到要求后可接取
 * 完成条件：击杀 3 个 Boss 级精英（任意击杀方式）
 * 失败条件：无
 *
 * 核心接口：EMWMKillEvent.isBossKill() + EMWMBridgeAPI
 */

import com.emwbridge.events.EMWMKillEvent
import com.emwbridge.api.EMWMBridgeAPI

quest("boss_hunter") {
    description {
        name = "Boss 猎人"
        lore = listOf(
            "§c§l危险任务",
            "",
            "§7击杀 §c3 §7个 Boss 级精英怪",
            "§7它们装备精良，火力凶猛",
            "",
            "§d进度: §f${progress}/3"
        )
    }

    onStart {
        player.sendMessage("§c[任务] §fBoss 猎人 §7- 击杀 3 个 Boss 级精英")
        player.sendMessage("§7提示: Boss 通常有高攻击性和压制能力，建议组队")
    }

    on<EMWMKillEvent> {
        if (killer?.uniqueId != player.uniqueId) return@on

        // 方式1：用事件便捷方法
        if (isBossKill()) {
            progress += 1
            player.sendMessage("§a[任务] §fBoss 击杀! §7($progress/3)")

            // 记录击杀详情
            player.sendMessage("§7  击杀方式: $killMethod")
            player.sendMessage("§7  武器: ${weaponTitle ?: "未知"}")

            if (progress >= 3) {
                player.sendMessage("§a[任务] §fBoss 猎人 §e完成!")
                complete()
            }
        }
    }

    // 也可以用 EMWMBridgeAPI 在其他场景判断
    // 例如：检测附近是否有 Boss
    on<PlayerMoveEvent> {
        // 检测附近 20 格内是否有 Boss
        val nearbyBosses = player.location.getNearbyLivingEntities(20.0)
            .filter { EMWMBridgeAPI.isBoss(it) && EMWMBridgeAPI.isInCombat(it) }

        if (nearbyBosses.isNotEmpty() && cooldown("boss_warning", 30)) {
            player.sendMessage("§c⚠ §7附近检测到 ${nearbyBosses.size} 个战斗中的 Boss!")
        }
    }

    onComplete {
        player.sendMessage("§a[任务] §fBoss 猎人 §e完成!")
        player.sendMessage("§7奖励: §6经验 2000 §7| §b金币 1000 §7| §e稀有武器箱 x1")

        player.giveExp(2000)
        // 给稀有武器箱
        // player.inventory.addItem(ItemStack(Material.CHEST))
    }
}
