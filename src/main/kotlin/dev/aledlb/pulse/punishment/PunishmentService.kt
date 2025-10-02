package dev.aledlb.pulse.punishment

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.database.PunishmentRow
import dev.aledlb.pulse.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PunishmentService {
    private val frozenPlayers = ConcurrentHashMap<UUID, Boolean>()

    // Ban operations
    fun ban(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val punishmentId = db.savePunishment(target, "BAN", reason, punisher, punisherName, null, null)
                db.setActivePunishment(target, "BAN", null, punishmentId)

                // Kick player if online
                val player = Bukkit.getPlayer(target)
                player?.scheduler?.run(Pulse.getPlugin(), { _ ->
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ban-screen",
                        "reason" to reason,
                        "punisher" to punisherName
                    )
                    player.kick(Component.text(kickMsg))
                }, null)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            } catch (e: Exception) {
                Logger.error("Failed to ban player $targetName", e)
            }
        }
    }

    fun tempban(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, duration: Long, broadcast: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val expires = System.currentTimeMillis() + (duration * 1000)
                val punishmentId = db.savePunishment(target, "TEMPBAN", reason, punisher, punisherName, duration, null)
                db.setActivePunishment(target, "BAN", expires, punishmentId)

                val player = Bukkit.getPlayer(target)
                player?.scheduler?.run(Pulse.getPlugin(), { _ ->
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempban-screen",
                        "reason" to reason,
                        "duration" to formatDuration(duration),
                        "punisher" to punisherName
                    )
                    player.kick(Component.text(kickMsg))
                }, null)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName,
                        "duration" to formatDuration(duration)
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            } catch (e: Exception) {
                Logger.error("Failed to tempban player $targetName", e)
            }
        }
    }

    fun ipban(target: UUID, targetName: String, ip: String, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val punishmentId = db.savePunishment(target, "IPBAN", reason, punisher, punisherName, null, ip)
                db.setActivePunishment(target, "BAN", null, punishmentId)

                Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                    Bukkit.getIPBans().add(ip)
                })
                val player = Bukkit.getPlayer(target)
                player?.scheduler?.run(Pulse.getPlugin(), { _ ->
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ipban-screen",
                        "reason" to reason,
                        "punisher" to punisherName
                    )
                    player.kick(Component.text(kickMsg))
                }, null)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ipban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            } catch (e: Exception) {
                Logger.error("Failed to IP ban player $targetName", e)
            }
        }
    }

    fun tempipban(target: UUID, targetName: String, ip: String, reason: String, punisher: UUID, punisherName: String, duration: Long, broadcast: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val expires = System.currentTimeMillis() + (duration * 1000)
                val punishmentId = db.savePunishment(target, "TEMPIPBAN", reason, punisher, punisherName, duration, ip)
                db.setActivePunishment(target, "BAN", expires, punishmentId)

                Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                    Bukkit.getIPBans().add(ip)
                })
                val player = Bukkit.getPlayer(target)
                player?.scheduler?.run(Pulse.getPlugin(), { _ ->
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempipban-screen",
                        "reason" to reason,
                        "duration" to formatDuration(duration),
                        "punisher" to punisherName
                    )
                    player.kick(Component.text(kickMsg))
                }, null)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempipban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName,
                        "duration" to formatDuration(duration)
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            } catch (e: Exception) {
                Logger.error("Failed to temp IP ban player $targetName", e)
            }
        }
    }

    fun unban(target: UUID, targetName: String, removedBy: UUID, removedByName: String, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val active = db.getActivePunishment(target, "BAN")
                if (active != null) {
                    db.deactivatePunishment(active.punishmentId, removedBy)
                    db.removeActivePunishment(target, "BAN")

                    // Get punishment details to remove IP ban if needed
                    val punishments = db.getPlayerPunishments(target)
                    val punishment = punishments.find { it.id == active.punishmentId }
                    if (punishment?.ip != null) {
                        Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                            Bukkit.getIPBans().remove(punishment.ip)
                        })
                    }
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                Logger.error("Failed to unban player $targetName", e)
                callback(false)
            }
        }
    }

    // Mute operations
    fun mute(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, duration: Long?, broadcast: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val expires = duration?.let { System.currentTimeMillis() + (it * 1000) }
                val punishmentId = db.savePunishment(target, "MUTE", reason, punisher, punisherName, duration, null)
                db.setActivePunishment(target, "MUTE", expires, punishmentId)

                val player = Bukkit.getPlayer(target)
                player?.scheduler?.run(Pulse.getPlugin(), { _ ->
                    val durationText = duration?.let { formatDuration(it) } ?: "Permanent"
                    val muteMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.mute-screen",
                        "reason" to reason,
                        "duration" to durationText,
                        "punisher" to punisherName
                    )
                    player.sendMessage(Component.text(muteMsg))
                }, null)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastMutes()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.mute-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            } catch (e: Exception) {
                Logger.error("Failed to mute player $targetName", e)
            }
        }
    }

    fun unmute(target: UUID, targetName: String, removedBy: UUID, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val active = db.getActivePunishment(target, "MUTE")
                if (active != null) {
                    db.deactivatePunishment(active.punishmentId, removedBy)
                    db.removeActivePunishment(target, "MUTE")

                    val player = Bukkit.getPlayer(target)
                    player?.scheduler?.run(Pulse.getPlugin(), { _ ->
                        val unmuteMsg = Pulse.getPlugin().messagesManager.getMessage("punishment.unmute-screen")
                        player.sendMessage(Component.text(unmuteMsg))
                    }, null)
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                Logger.error("Failed to unmute player $targetName", e)
                callback(false)
            }
        }
    }

    fun isMuted(target: UUID, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = Pulse.getPlugin().databaseManager
            val active = db.getActivePunishment(target, "MUTE")
            if (active != null) {
                // Check if expired
                if (active.expires != null && System.currentTimeMillis() > active.expires) {
                    db.removeActivePunishment(target, "MUTE")
                    db.deactivatePunishment(active.punishmentId, UUID.fromString("00000000-0000-0000-0000-000000000000"))
                    callback(false)
                } else {
                    callback(true)
                }
            } else {
                callback(false)
            }
        }
    }

    // Synchronous version for event listeners - checks cached data
    fun isMutedSync(target: UUID): Boolean {
        return try {
            val db = Pulse.getPlugin().databaseManager
            runBlocking {
                val active = db.getActivePunishment(target, "MUTE")
                if (active != null) {
                    // Check if expired
                    if (active.expires != null && System.currentTimeMillis() > active.expires) {
                        db.removeActivePunishment(target, "MUTE")
                        db.deactivatePunishment(active.punishmentId, UUID.fromString("00000000-0000-0000-0000-000000000000"))
                        false
                    } else {
                        true
                    }
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // Freeze operations
    fun freeze(target: UUID) {
        frozenPlayers[target] = true
    }

    fun unfreeze(target: UUID) {
        frozenPlayers.remove(target)
    }

    fun isFrozen(target: UUID): Boolean = frozenPlayers.containsKey(target)

    // Warn operations
    fun warn(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                db.savePunishment(target, "WARN", reason, punisher, punisherName, null, null)

                val player = Bukkit.getPlayer(target)
                player?.scheduler?.run(Pulse.getPlugin(), { _ ->
                    val warnMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.warn-screen",
                        "reason" to reason,
                        "punisher" to punisherName
                    )
                    player.sendMessage(Component.text(warnMsg))
                }, null)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastWarns()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.warn-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            } catch (e: Exception) {
                Logger.error("Failed to warn player $targetName", e)
            }
        }
    }

    fun getWarns(target: UUID, callback: (List<PunishmentRow>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                val warns = db.getPlayerPunishments(target).filter { it.type == "WARN" && it.active }
                callback(warns)
            } catch (e: Exception) {
                Logger.error("Failed to get warns for player", e)
                callback(emptyList())
            }
        }
    }

    fun getPunishments(target: UUID, callback: (List<PunishmentRow>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val punishments = Pulse.getPlugin().databaseManager.getPlayerPunishments(target)
                callback(punishments)
            } catch (e: Exception) {
                Logger.error("Failed to get punishments for player", e)
                callback(emptyList())
            }
        }
    }

    // Kick operation
    fun kick(target: Player, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                db.savePunishment(target.uniqueId, "KICK", reason, punisher, punisherName, null, null)

                target.scheduler.run(Pulse.getPlugin(), { _ ->
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.kick-screen",
                        "reason" to reason,
                        "punisher" to punisherName
                    )
                    target.kick(Component.text(kickMsg))
                }, null)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastKicks()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.kick-broadcast",
                        "player" to target.name,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            } catch (e: Exception) {
                Logger.error("Failed to kick player ${target.name}", e)
            }
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