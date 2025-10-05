package dev.aledlb.pulse.punishment

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.database.PunishmentRow
import dev.aledlb.pulse.util.Logger
import dev.aledlb.pulse.util.SyncHelper
import dev.aledlb.pulse.util.AsyncHelper
import dev.aledlb.pulse.util.SchedulerHelper
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PunishmentService {
    private val frozenPlayers = ConcurrentHashMap<UUID, Boolean>()

    // Ban operations
    fun ban(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = true) {
        AsyncHelper.executeAsync(
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val punishmentId = db.savePunishment(target, "BAN", reason, punisher, punisherName, null, null)
                db.setActivePunishment(target, "BAN", null, punishmentId)

                // Sync to Redis
                SyncHelper.syncBan(target, punishmentId.toString())

                // Kick player if online
                val player = Bukkit.getPlayer(target)
                if (player != null) {
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ban-screen",
                        "reason" to reason,
                        "punisher" to punisherName
                    )
                    SchedulerHelper.kickPlayer(player, kickMsg)
                }

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            },
            errorMessage = "Failed to ban player $targetName"
        )
    }

    fun tempban(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, duration: Long, broadcast: Boolean = true) {
        AsyncHelper.executeAsync(
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val expires = System.currentTimeMillis() + (duration * 1000)
                val punishmentId = db.savePunishment(target, "TEMPBAN", reason, punisher, punisherName, duration, null)
                db.setActivePunishment(target, "BAN", expires, punishmentId)

                // Sync to Redis
                SyncHelper.syncBan(target, punishmentId.toString())

                val player = Bukkit.getPlayer(target)
                if (player != null) {
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempban-screen",
                        "reason" to reason,
                        "duration" to formatDuration(duration),
                        "punisher" to punisherName
                    )
                    SchedulerHelper.kickPlayer(player, kickMsg)
                }

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName,
                        "duration" to formatDuration(duration)
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            },
            errorMessage = "Failed to tempban player $targetName"
        )
    }

    fun ipban(target: UUID, targetName: String, ip: String, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = true) {
        AsyncHelper.executeAsync(
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val punishmentId = db.savePunishment(target, "IPBAN", reason, punisher, punisherName, null, ip)
                db.setActivePunishment(target, "BAN", null, punishmentId)

                // Sync to Redis
                SyncHelper.syncBan(target, punishmentId.toString())

                Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                    Bukkit.getIPBans().add(ip)
                })
                
                val player = Bukkit.getPlayer(target)
                if (player != null) {
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ipban-screen",
                        "reason" to reason,
                        "punisher" to punisherName
                    )
                    SchedulerHelper.kickPlayer(player, kickMsg)
                }

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.ipban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            },
            errorMessage = "Failed to IP ban player $targetName"
        )
    }

    fun tempipban(target: UUID, targetName: String, ip: String, reason: String, punisher: UUID, punisherName: String, duration: Long, broadcast: Boolean = true) {
        AsyncHelper.executeAsync(
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val expires = System.currentTimeMillis() + (duration * 1000)
                val punishmentId = db.savePunishment(target, "TEMPIPBAN", reason, punisher, punisherName, duration, ip)
                db.setActivePunishment(target, "BAN", expires, punishmentId)

                // Sync to Redis
                SyncHelper.syncBan(target, punishmentId.toString())

                Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                    Bukkit.getIPBans().add(ip)
                })
                
                val player = Bukkit.getPlayer(target)
                if (player != null) {
                    val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempipban-screen",
                        "reason" to reason,
                        "duration" to formatDuration(duration),
                        "punisher" to punisherName
                    )
                    SchedulerHelper.kickPlayer(player, kickMsg)
                }

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.tempipban-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName,
                        "duration" to formatDuration(duration)
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            },
            errorMessage = "Failed to temp IP ban player $targetName"
        )
    }

    fun unban(target: UUID, targetName: String, removedBy: UUID, removedByName: String, callback: (Boolean) -> Unit) {
        AsyncHelper.loadAsync(
            entityName = "ban status for $targetName",
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val active = db.getActivePunishment(target, "BAN")
                if (active != null) {
                    db.deactivatePunishment(active.punishmentId, removedBy)
                    db.removeActivePunishment(target, "BAN")

                    // Sync to Redis
                    SyncHelper.syncUnban(target)

                    // Get punishment details to remove IP ban if needed
                    val punishments = db.getPlayerPunishments(target)
                    val punishment = punishments.find { it.id == active.punishmentId }
                    if (punishment?.ip != null) {
                        Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                            Bukkit.getIPBans().remove(punishment.ip)
                        })
                    }
                    true
                } else {
                    false
                }
            },
            onSuccess = callback,
            onError = { callback(false) }
        )
    }

    // Mute operations
    fun mute(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, duration: Long?, broadcast: Boolean = false) {
        AsyncHelper.executeAsync(
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val expires = duration?.let { System.currentTimeMillis() + (it * 1000) }
                val punishmentId = db.savePunishment(target, "MUTE", reason, punisher, punisherName, duration, null)
                db.setActivePunishment(target, "MUTE", expires, punishmentId)

                // Sync to Redis
                SyncHelper.syncMute(target, punishmentId.toString())

                val player = Bukkit.getPlayer(target)
                if (player != null) {
                    val durationText = duration?.let { formatDuration(it) } ?: "Permanent"
                    val muteMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.mute-screen",
                        "reason" to reason,
                        "duration" to durationText,
                        "punisher" to punisherName
                    )
                    SchedulerHelper.sendMessage(player, muteMsg)
                }

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastMutes()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.mute-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            },
            errorMessage = "Failed to mute player $targetName"
        )
    }

    fun unmute(target: UUID, targetName: String, removedBy: UUID, callback: (Boolean) -> Unit) {
        AsyncHelper.loadAsync(
            entityName = "mute status for $targetName",
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val active = db.getActivePunishment(target, "MUTE")
                if (active != null) {
                    db.deactivatePunishment(active.punishmentId, removedBy)
                    db.removeActivePunishment(target, "MUTE")

                    // Sync to Redis
                    SyncHelper.syncUnmute(target)

                    val player = Bukkit.getPlayer(target)
                    if (player != null) {
                        val unmuteMsg = Pulse.getPlugin().messagesManager.getMessage("punishment.unmute-screen")
                        SchedulerHelper.sendMessage(player, unmuteMsg)
                    }
                    true
                } else {
                    false
                }
            },
            onSuccess = callback,
            onError = { callback(false) }
        )
    }

    fun isMuted(target: UUID, callback: (Boolean) -> Unit) {
        AsyncHelper.loadAsync(
            entityName = "mute check",
            operation = {
                val db = Pulse.getPlugin().databaseManager
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
            },
            onSuccess = callback,
            onError = { callback(false) }
        )
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
        SyncHelper.syncFreeze(target)
    }

    fun unfreeze(target: UUID) {
        frozenPlayers.remove(target)
        SyncHelper.syncUnfreeze(target)
    }

    fun isFrozen(target: UUID): Boolean = frozenPlayers.containsKey(target)

    // Warn operations
    fun warn(target: UUID, targetName: String, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = false) {
        AsyncHelper.executeAsync(
            operation = {
                val db = Pulse.getPlugin().databaseManager
                val punishmentId = db.savePunishment(target, "WARN", reason, punisher, punisherName, null, null)

                // Sync to Redis
                SyncHelper.syncWarn(target, punishmentId.toString())

                val player = Bukkit.getPlayer(target)
                if (player != null) {
                    val warnMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.warn-screen",
                        "reason" to reason,
                        "punisher" to punisherName
                    )
                    SchedulerHelper.sendMessage(player, warnMsg)
                }

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastWarns()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.warn-broadcast",
                        "player" to targetName,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            },
            errorMessage = "Failed to warn player $targetName"
        )
    }

    fun getWarns(target: UUID, callback: (List<PunishmentRow>) -> Unit) {
        AsyncHelper.loadAsync(
            entityName = "warns",
            operation = {
                val db = Pulse.getPlugin().databaseManager
                db.getPlayerPunishments(target).filter { it.type == "WARN" && it.active }
            },
            onSuccess = callback,
            onError = { callback(emptyList()) }
        )
    }

    fun getPunishments(target: UUID, callback: (List<PunishmentRow>) -> Unit) {
        AsyncHelper.loadAsync(
            entityName = "punishments",
            operation = {
                Pulse.getPlugin().databaseManager.getPlayerPunishments(target)
            },
            onSuccess = callback,
            onError = { callback(emptyList()) }
        )
    }

    // Kick operation
    fun kick(target: Player, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = true) {
        AsyncHelper.executeAsync(
            operation = {
                val db = Pulse.getPlugin().databaseManager
                db.savePunishment(target.uniqueId, "KICK", reason, punisher, punisherName, null, null)

                val kickMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                    "punishment.kick-screen",
                    "reason" to reason,
                    "punisher" to punisherName
                )
                SchedulerHelper.kickPlayer(target, kickMsg)

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastKicks()) {
                    val broadcastMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                        "punishment.kick-broadcast",
                        "player" to target.name,
                        "punisher" to punisherName
                    )
                    Bukkit.getServer().sendMessage(Component.text(broadcastMsg))
                }
            },
            errorMessage = "Failed to kick player ${target.name}"
        )
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