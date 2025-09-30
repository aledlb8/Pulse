package dev.aledlb.pulse.punishment

import dev.aledlb.pulse.Pulse
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class PunishmentListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncChatEvent) {
        val service = Pulse.getPlugin().punishmentManager.service

        if (service.isMuted(event.player.uniqueId)) {
            event.isCancelled = true
            event.player.sendMessage(Component.text("§c§lMUTED\n§7You are currently muted and cannot chat."))
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerMove(event: PlayerMoveEvent) {
        // Only cancel if player actually moved (not just head rotation)
        if (event.from.x == event.to?.x && event.from.y == event.to?.y && event.from.z == event.to?.z) {
            return
        }

        val service = Pulse.getPlugin().punishmentManager.service

        if (service.isFrozen(event.player.uniqueId)) {
            event.isCancelled = true
            event.player.sendMessage(Component.text("§c§lFROZEN\n§7You are frozen and cannot move."))
        }
    }
}