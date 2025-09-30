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
                Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                    val player = Bukkit.getPlayer(target)
                    player?.kick(Component.text("§c§lBANNED\n\n§7Reason: §f$reason\n§7By: §f$punisherName"))
                })

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    Bukkit.getServer().sendMessage(Component.text("§c$targetName has been banned by $punisherName"))
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

                Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                    val player = Bukkit.getPlayer(target)
                    player?.kick(Component.text("§c§lTEMPORARILY BANNED\n\n§7Reason: §f$reason\n§7Duration: §f${formatDuration(duration)}\n§7By: §f$punisherName"))
                })

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    Bukkit.getServer().sendMessage(Component.text("§c$targetName has been temporarily banned by $punisherName for ${formatDuration(duration)}"))
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

                Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                    Bukkit.getIPBans().add(ip)
                    val player = Bukkit.getPlayer(target)
                    player?.kick(Component.text("§c§lIP BANNED\n\n§7Reason: §f$reason\n§7By: §f$punisherName"))
                })

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    Bukkit.getServer().sendMessage(Component.text("§c$targetName has been IP banned by $punisherName"))
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

                Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                    Bukkit.getIPBans().add(ip)
                    val player = Bukkit.getPlayer(target)
                    player?.kick(Component.text("§c§lTEMPORARILY IP BANNED\n\n§7Reason: §f$reason\n§7Duration: §f${formatDuration(duration)}\n§7By: §f$punisherName"))
                })

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastBans()) {
                    Bukkit.getServer().sendMessage(Component.text("§c$targetName has been temporarily IP banned by $punisherName for ${formatDuration(duration)}"))
                }
            } catch (e: Exception) {
                Logger.error("Failed to temp IP ban player $targetName", e)
            }
        }
    }

    fun unban(target: UUID, targetName: String, removedBy: UUID, removedByName: String): Boolean {
        return runBlocking {
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
                        Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                            Bukkit.getIPBans().remove(punishment.ip)
                        })
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Logger.error("Failed to unban player $targetName", e)
                false
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

                Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                    val player = Bukkit.getPlayer(target)
                    player?.sendMessage(Component.text("§c§lMUTED\n\n§7Reason: §f$reason\n§7Duration: §f${duration?.let { formatDuration(it) } ?: "Permanent"}\n§7By: §f$punisherName"))
                })

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastMutes()) {
                    Bukkit.getServer().sendMessage(Component.text("§e$targetName has been muted by $punisherName"))
                }
            } catch (e: Exception) {
                Logger.error("Failed to mute player $targetName", e)
            }
        }
    }

    fun unmute(target: UUID, targetName: String, removedBy: UUID): Boolean {
        return runBlocking {
            try {
                val db = Pulse.getPlugin().databaseManager
                val active = db.getActivePunishment(target, "MUTE")
                if (active != null) {
                    db.deactivatePunishment(active.punishmentId, removedBy)
                    db.removeActivePunishment(target, "MUTE")

                    Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                        val player = Bukkit.getPlayer(target)
                        player?.sendMessage(Component.text("§a§lUNMUTED\n\n§7You have been unmuted."))
                    })
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Logger.error("Failed to unmute player $targetName", e)
                false
            }
        }
    }

    fun isMuted(target: UUID): Boolean {
        return runBlocking {
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

                Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                    val player = Bukkit.getPlayer(target)
                    player?.sendMessage(Component.text("§e§lWARNED\n\n§7Reason: §f$reason\n§7By: §f$punisherName"))
                })

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastWarns()) {
                    Bukkit.getServer().sendMessage(Component.text("§e$targetName has been warned by $punisherName"))
                }
            } catch (e: Exception) {
                Logger.error("Failed to warn player $targetName", e)
            }
        }
    }

    fun getWarns(target: UUID): List<PunishmentRow> {
        return runBlocking {
            try {
                val db = Pulse.getPlugin().databaseManager
                db.getPlayerPunishments(target).filter { it.type == "WARN" && it.active }
            } catch (e: Exception) {
                Logger.error("Failed to get warns for player", e)
                emptyList()
            }
        }
    }

    fun getPunishments(target: UUID): List<PunishmentRow> {
        return runBlocking {
            try {
                Pulse.getPlugin().databaseManager.getPlayerPunishments(target)
            } catch (e: Exception) {
                Logger.error("Failed to get punishments for player", e)
                emptyList()
            }
        }
    }

    // Kick operation
    fun kick(target: Player, reason: String, punisher: UUID, punisherName: String, broadcast: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = Pulse.getPlugin().databaseManager
                db.savePunishment(target.uniqueId, "KICK", reason, punisher, punisherName, null, null)

                Bukkit.getScheduler().runTask(Pulse.getPlugin(), Runnable {
                    target.kick(Component.text("§c§lKICKED\n\n§7Reason: §f$reason\n§7By: §f$punisherName"))
                })

                if (broadcast && Pulse.getPlugin().punishmentManager.shouldBroadcastKicks()) {
                    Bukkit.getServer().sendMessage(Component.text("§e${target.name} has been kicked by $punisherName"))
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