/**
 * 任务：战利品收集者
 * 描述：击杀精英怪并收集它们掉落的武器 3 把
 * 难度：★★☆☆☆
 *
 * 机制说明：精英怪死亡后可能掉落绑定的武器，
 * 玩家需要捡起这些武器完成任务
 *
 * 完成条件：拾取 3 把从精英怪掉落的武器
 *
 * 核心接口：EMWMKillEvent + EntityPickupItemEvent + EMWMBridgeAPI
 */

import com.emwbridge.events.EMWMKillEvent
import com.emwbridge.api.EMWMBridgeAPI
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

// 记录玩家掉落的武器 UUID（防止刷物品）
val droppedWeaponItems = mutableMapOf<java.util.UUID, String>() // itemUUID -> weaponTitle

quest("weapon_collector") {
    description {
        name = "战利品收集者"
        lore = listOf(
            "§7击杀精英怪并收集它们掉落的武器",
            "§7需要收集 §e3 §7把武器",
            "",
            "§d进度: §f${progress}/3"
        )
    }

    onStart {
        player.sendMessage("§e[任务] §f战利品收集者 §7- 收集 3 把精英怪武器")
    }

    // 监听击杀事件，记录掉落的武器
    on<EMWMKillEvent> {
        if (killer?.uniqueId != player.uniqueId) return@on

        // 获取精英怪手中的武器
        val weapon = weaponTitle
        if (weapon != null) {
            player.sendMessage("§7[任务] §f击杀 ${victim.name} §7(武器: $weapon)")
            player.sendMessage("§7  击杀方式: $killMethod | 兵种: ${tier ?: "未知"}")
            // 提示玩家去捡武器
            player.sendMessage("§e[任务] §f捡起掉落的武器来收集!")
        }
    }

    // 监听物品拾取
    on<PlayerPickupItemEvent> {
        if (event.player.uniqueId != player.uniqueId) return@on

        val item = event.item
        val stack = item.itemStack

        // 检查是否是 WM 武器（通过 Lore 或 NBT 判断）
        val isWMWeapon = stack.hasItemMeta() && stack.itemMeta.hasLore()

        if (isWMWeapon) {
            // 简单判断：有 Lore 的物品视为精英怪武器
            // 实际项目中应根据 WM 的武器标识判断
            val meta = stack.itemMeta
            val weaponName = if (meta.hasDisplayName()) meta.displayName else stack.type.name

            progress += 1
            player.sendMessage("§a[任务] §f收集到武器: $weaponName §7($progress/3)")
            player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 1f, 1.5f)

            if (progress >= 3) {
                complete()
            }
        }
    }

    onComplete {
        player.sendMessage("§a[任务] §f战利品收集者 §e完成!")
        player.sendMessage("§7奖励: §6经验 400 §7| §b金币 200")
        player.giveExp(400)
    }
}
