package dev.aledlb.pulse.punishment

import dev.aledlb.pulse.Pulse
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

class PunishmentListener : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        val db = Pulse.getPlugin().databaseManager

        runBlocking {
            try {
                val activeBan = db.getActivePunishment(event.uniqueId, "BAN")
                if (activeBan != null) {
                    // Check if temp ban expired
                    if (activeBan.expires != null && System.currentTimeMillis() > activeBan.expires) {
                        // Ban expired, remove it
                        db.removeActivePunishment(event.uniqueId, "BAN")
                        db.deactivatePunishment(activeBan.punishmentId, java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"))
                    } else {
                        // Get ban details
                        val punishments = db.getPlayerPunishments(event.uniqueId)
                        val punishment = punishments.find { it.id == activeBan.punishmentId }

                        if (punishment != null) {
                            val message = if (activeBan.expires != null) {
                                val remaining = (activeBan.expires - System.currentTimeMillis()) / 1000
                                Pulse.getPlugin().messagesManager.getFormattedMessage(
                                    "punishment.tempban-remaining",
                                    "reason" to punishment.reason,
                                    "duration" to formatDuration(remaining),
                                    "punisher" to punishment.punisherName
                                )
                            } else {
                                Pulse.getPlugin().messagesManager.getFormattedMessage(
                                    "punishment.ban-screen",
                                    "reason" to punishment.reason,
                                    "punisher" to punishment.punisherName
                                )
                            }
                            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(message))
                        }
                    }
                }
            } catch (e: Exception) {
                // Don't block login if there's a database error
                e.printStackTrace()
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncChatEvent) {
        val service = Pulse.getPlugin().punishmentManager.service

        if (service.isMuted(event.player.uniqueId)) {
            event.isCancelled = true
            val muteMsg = Pulse.getPlugin().messagesManager.getMessage("punishment.mute-chat-blocked")
            event.player.sendMessage(Component.text(muteMsg))
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
            val freezeMsg = Pulse.getPlugin().messagesManager.getMessage("punishment.freeze-movement-blocked")
            event.player.sendMessage(Component.text(freezeMsg))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val service = Pulse.getPlugin().punishmentManager.service
        val manager = Pulse.getPlugin().punishmentManager

        if (service.isFrozen(event.player.uniqueId)) {
            val consoleUuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")
            val freezeEvasionReason = Pulse.getPlugin().messagesManager.getMessage("punishment.freeze-evasion")
            val actions = manager.getFreezeEvasionActions()
            val duration = manager.getFreezeEvasionDuration()
            val broadcast = manager.shouldBroadcastFreezeEvasion()
            val playerIp = event.player.address?.address?.hostAddress

            // Execute all configured actions
            for (action in actions) {
                when (action) {
                    "BAN" -> {
                        service.ban(
                            event.player.uniqueId,
                            event.player.name,
                            freezeEvasionReason,
                            consoleUuid,
                            "CONSOLE",
                            broadcast = broadcast
                        )
                    }
                    "TEMPBAN" -> {
                        service.tempban(
                            event.player.uniqueId,
                            event.player.name,
                            freezeEvasionReason,
                            consoleUuid,
                            "CONSOLE",
                            duration,
                            broadcast = broadcast
                        )
                    }
                    "IPBAN" -> {
                        if (playerIp != null) {
                            service.ipban(
                                event.player.uniqueId,
                                event.player.name,
                                playerIp,
                                freezeEvasionReason,
                                consoleUuid,
                                "CONSOLE",
                                broadcast = broadcast
                            )
                        }
                    }
                    "TEMPIPBAN" -> {
                        if (playerIp != null) {
                            service.tempipban(
                                event.player.uniqueId,
                                event.player.name,
                                playerIp,
                                freezeEvasionReason,
                                consoleUuid,
                                "CONSOLE",
                                duration,
                                broadcast = broadcast
                            )
                        }
                    }
                    "MUTE" -> {
                        service.mute(
                            event.player.uniqueId,
                            event.player.name,
                            freezeEvasionReason,
                            consoleUuid,
                            "CONSOLE",
                            duration,
                            broadcast = broadcast
                        )
                    }
                    "WARN" -> {
                        service.warn(
                            event.player.uniqueId,
                            event.player.name,
                            freezeEvasionReason,
                            consoleUuid,
                            "CONSOLE",
                            broadcast = broadcast
                        )
                    }
                    "KICK" -> {
                        // Player already quit, can't kick them, but we can record it
                        CoroutineScope(Dispatchers.IO).launch {
                            Pulse.getPlugin().databaseManager.savePunishment(
                                event.player.uniqueId,
                                "KICK",
                                freezeEvasionReason,
                                consoleUuid,
                                "CONSOLE",
                                null,
                                null
                            )
                        }
                    }
                    "NONE" -> {
                        // Do nothing
                    }
                }
            }

            service.unfreeze(event.player.uniqueId)
        }
    }

    private fun formatDuration(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (secs > 0 || isEmpty()) append("${secs}s")
        }.trim()
    }
}