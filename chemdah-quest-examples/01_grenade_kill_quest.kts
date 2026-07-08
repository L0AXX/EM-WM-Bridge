/**
 * 任务：爆破专家
 * 描述：用手雷击杀 5 个 EMWM 精英怪
 * 难度：★★★☆☆
 *
 * 触发条件：玩家接受任务后开始计数
 * 完成条件：用手雷击杀 5 个精英怪
 * 失败条件：无
 *
 * 核心接口：EMWMKillEvent + KillMethod.GRENADE
 */

import com.emwbridge.events.EMWMKillEvent
import org.bukkit.entity.Player

// 任务定义
quest("grenade_expert") {
    // 任务描述
    description {
        name = "爆破专家"
        lore = listOf(
            "§7用手雷击杀 §e5 §7个精英怪",
            "§7证明你的爆破能力",
            "",
            "§d进度: §f${progress}/5"
        )
    }

    // 接受任务时的提示
    onStart {
        player.sendMessage("§e[任务] §f爆破专家 §7- 用手雷击杀 5 个精英怪")
    }

    // 监听击杀事件
    on<EMWMKillEvent> {
        // 检查击杀者是否是任务玩家
        if (killer?.uniqueId != player.uniqueId) return@on

        // 检查是否是手雷击杀
        if (killMethod == EMWMKillEvent.KillMethod.GRENADE
            || killMethod == EMWMKillEvent.KillMethod.EXPLOSION) {

            progress += 1
            player.sendMessage("§a[任务] §f手雷击杀! §7($progress/5)")

            // 进度提示
            when (progress) {
                1 -> player.sendMessage("§7[任务] §f好的开始，继续!")
                3 -> player.sendMessage("§7[任务] §f已经过半了!")
                5 -> {
                    player.sendMessage("§a[任务] §f爆破专家 §e完成!")
                    complete()
                }
            }
        } else {
            // 非手雷击杀不计数，但给提示
            player.sendMessage("§7[任务] §c需要用手雷击杀! §7(当前: $killMethod)")
        }
    }

    // 完成奖励
    onComplete {
        player.sendMessage("§a[任务] §f爆破专家 §e完成!")
        player.sendMessage("§7奖励: §6经验 500 §7| §b金币 200")

        // 给奖励
        player.giveExp(500)
        // 经济插件给金币（如果安装了）
        // Vault: econ.depositPlayer(player, 200)
    }
}
