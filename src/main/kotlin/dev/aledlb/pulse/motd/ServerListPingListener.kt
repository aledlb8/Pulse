package dev.aledlb.pulse.motd

import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import dev.aledlb.pulse.Pulse
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent

class ServerListPingListener(private val motdManager: MOTDManager) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onServerListPing(event: PaperServerListPingEvent) {
        if (!motdManager.isEnabled()) {
            return
        }

        // Set MOTD (2 lines)
        val line1 = motdManager.getMOTDLine1()
        val line2 = motdManager.getMOTDLine2()
        val motd = line1.append(Component.newline()).append(line2)
        event.motd(motd)

        // Set max players
        val maxPlayers = motdManager.getMaxPlayers()
        if (maxPlayers != null) {
            event.maxPlayers = maxPlayers
        }

        // Set server icon
        val icon = motdManager.getServerIcon()
        if (icon != null) {
            event.serverIcon = icon
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onAsyncPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (motdManager.isMaintenanceMode) {
            val bypassPermission = motdManager.getMaintenanceBypassPermission()

            // Check if player has bypass permission by UUID
            val rankManager = Pulse.getPlugin().rankManager
            val playerData = rankManager.getPlayerData(event.uniqueId)

            val hasPermission = if (playerData != null) {
                playerData.getAllPermissions(rankManager).contains(bypassPermission) ||
                playerData.getAllPermissions(rankManager).contains("pulse.*")
            } else {
                false
            }

            if (!hasPermission) {
                val kickMessage = motdManager.getMaintenanceKickMessage()
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickMessage)
            }
        }
    }
}
