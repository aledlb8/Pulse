package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.util.*

// Base punishment command
abstract class BasePunishmentCommand(
    commandName: String,
    commandPermission: String,
    private val requiresReason: Boolean = true
) : BaseCommand() {

    override val name = commandName
    override val permission = commandPermission
    override val description = "Punishment command"
    override val usage = "/$commandName <player> [reason...]"

    protected abstract fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>)

    override fun execute(sender: CommandSender, args: Array<out String>) {
        val messagesManager = Pulse.getPlugin().messagesManager

        if (args.isEmpty()) {
            sendMessage(sender, messagesManager.getFormattedMessage("punishment.usage", "usage" to usage))
            return
        }

        val targetName = args[0]
        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)

        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            sendMessage(sender, messagesManager.getFormattedMessage("punishment.player-not-found-name", "player" to targetName))
            return
        }

        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"

        if (requiresReason && args.size < 2) {
            sendMessage(sender, messagesManager.getFormattedMessage("punishment.usage", "usage" to usage))
            return
        }

        val target = Bukkit.getPlayer(offlinePlayer.uniqueId)
        executePunishment(sender, target, offlinePlayer.uniqueId, offlinePlayer.name ?: targetName, args)
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return if (args.size == 1) {
            Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[0].lowercase()) }
        } else {
            emptyList()
        }
    }

    protected fun getPunisherInfo(sender: CommandSender): Pair<UUID, String> {
        return if (sender is Player) {
            sender.uniqueId to sender.name
        } else {
            UUID.fromString("00000000-0000-0000-0000-000000000000") to "Console"
        }
    }

    protected fun parseDuration(input: String): Long? {
        val regex = "(\\d+)([smhd])".toRegex()
        val matches = regex.findAll(input.lowercase())

        var totalSeconds = 0L
        for (match in matches) {
            val value = match.groupValues[1].toLongOrNull() ?: continue
            val unit = match.groupValues[2]

            totalSeconds += when (unit) {
                "s" -> value
                "m" -> value * 60
                "h" -> value * 3600
                "d" -> value * 86400
                else -> 0
            }
        }

        return if (totalSeconds > 0) totalSeconds else null
    }
}

// Kick command
class KickCommand : BasePunishmentCommand("kick", "pulse.punishment.kick") {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (target == null || !target.isOnline) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.player-not-online",
                "player" to targetName
            )
            sendMessage(sender, msg)
            return
        }

        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.kick(target, reason, punisherUuid, punisherName)
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.kick-success",
            "player" to targetName,
            "reason" to reason
        )
        sendMessage(sender, msg)
    }
}

// Ban command
class BanCommand : BasePunishmentCommand("ban", "pulse.punishment.ban") {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.ban(targetOffline, targetName, reason, punisherUuid, punisherName)
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.ban-success",
            "player" to targetName,
            "reason" to reason
        )
        sendMessage(sender, msg)
    }
}

// Tempban command
class TempbanCommand : BasePunishmentCommand("tempban", "pulse.punishment.tempban") {
    override val usage = "/tempban <player> <duration> [reason...]"

    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (args.size < 2) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("punishment.usage-tempban"))
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("punishment.duration-example"))
            return
        }

        val duration = parseDuration(args[1])
        if (duration == null) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("punishment.invalid-duration"))
            return
        }

        val reason = if (args.size > 2) args.slice(2 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.tempban(targetOffline, targetName, reason, punisherUuid, punisherName, duration)
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.tempban-success",
            "player" to targetName,
            "duration" to formatDuration(duration),
            "reason" to reason
        )
        sendMessage(sender, msg)
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

// IPban command
class IpbanCommand : BasePunishmentCommand("ipban", "pulse.punishment.ipban") {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (target == null || !target.isOnline) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.player-not-online",
                "player" to targetName
            )
            sendMessage(sender, msg)
            return
        }

        val ip = target.address?.address?.hostAddress ?: run {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("punishment.ipban-cannot-determine-ip"))
            return
        }

        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.ipban(targetOffline, targetName, ip, reason, punisherUuid, punisherName)
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.ipban-success",
            "player" to targetName,
            "reason" to reason
        )
        sendMessage(sender, msg)
    }
}

// TempIPban command
class TempipbanCommand : BasePunishmentCommand("tempipban", "pulse.punishment.tempipban") {
    override val usage = "/tempipban <player> <duration> [reason...]"

    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (args.size < 2) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("punishment.usage-tempipban"))
            return
        }

        if (target == null || !target.isOnline) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.player-not-online",
                "player" to targetName
            )
            sendMessage(sender, msg)
            return
        }

        val ip = target.address?.address?.hostAddress ?: run {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("punishment.ipban-cannot-determine-ip"))
            return
        }

        val duration = parseDuration(args[1]) ?: run {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("punishment.invalid-duration"))
            return
        }

        val reason = if (args.size > 2) args.slice(2 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.tempipban(targetOffline, targetName, ip, reason, punisherUuid, punisherName, duration)
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.tempipban-success",
            "player" to targetName,
            "duration" to formatDuration(duration)
        )
        sendMessage(sender, msg)
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

// Unban command
class UnbanCommand : BasePunishmentCommand("unban", "pulse.punishment.unban", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        val success = Pulse.getPlugin().punishmentManager.service.unban(targetOffline, targetName, punisherUuid, punisherName)
        if (success) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.unban-success",
                "player" to targetName
            )
            sendMessage(sender, msg)
        } else {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.unban-not-banned",
                "player" to targetName
            )
            sendMessage(sender, msg)
        }
    }
}

// Mute command
class MuteCommand : BasePunishmentCommand("mute", "pulse.punishment.mute") {
    override val usage = "/mute <player> [duration] [reason...]"

    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val duration = if (args.size > 1) parseDuration(args[1]) else null
        val reasonStart = if (duration != null) 2 else 1
        val reason = if (args.size > reasonStart) args.slice(reasonStart until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.mute(targetOffline, targetName, reason, punisherUuid, punisherName, duration)
        val durationText = duration?.let { "for ${formatDuration(it)}" } ?: "permanently"
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.mute-success",
            "player" to targetName,
            "duration" to durationText
        )
        sendMessage(sender, msg)
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

// Unmute command
class UnmuteCommand : BasePunishmentCommand("unmute", "pulse.punishment.unmute", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val (punisherUuid, _) = getPunisherInfo(sender)

        val success = Pulse.getPlugin().punishmentManager.service.unmute(targetOffline, targetName, punisherUuid)
        if (success) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.unmute-success",
                "player" to targetName
            )
            sendMessage(sender, msg)
        } else {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.unmute-not-muted",
                "player" to targetName
            )
            sendMessage(sender, msg)
        }
    }
}

// Freeze command
class FreezeCommand : BasePunishmentCommand("freeze", "pulse.punishment.freeze", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (target == null || !target.isOnline) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.player-not-online",
                "player" to targetName
            )
            sendMessage(sender, msg)
            return
        }

        Pulse.getPlugin().punishmentManager.service.freeze(targetOffline)
        target.sendMessage(net.kyori.adventure.text.Component.text(
            Pulse.getPlugin().messagesManager.getMessage("punishment.freeze-screen")
        ))
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.freeze-success",
            "player" to targetName
        )
        sendMessage(sender, msg)
    }
}

// Unfreeze command
class UnfreezeCommand : BasePunishmentCommand("unfreeze", "pulse.punishment.freeze", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        Pulse.getPlugin().punishmentManager.service.unfreeze(targetOffline)
        target?.sendMessage(net.kyori.adventure.text.Component.text(
            Pulse.getPlugin().messagesManager.getMessage("punishment.unfreeze-screen")
        ))
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.unfreeze-success",
            "player" to targetName
        )
        sendMessage(sender, msg)
    }
}

// Warn command
class WarnCommand : BasePunishmentCommand("warn", "pulse.punishment.warn") {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.warn(targetOffline, targetName, reason, punisherUuid, punisherName)
        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.warn-success",
            "player" to targetName,
            "reason" to reason
        )
        sendMessage(sender, msg)
    }
}

// Warns command (view warnings)
class WarnsCommand : BasePunishmentCommand("warns", "pulse.punishment.warns", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val warns = Pulse.getPlugin().punishmentManager.service.getWarns(targetOffline)

        if (warns.isEmpty()) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.warns-none",
                "player" to targetName
            )
            sendMessage(sender, msg)
            return
        }

        val header = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.warns-header",
            "player" to targetName,
            "count" to warns.size.toString()
        )
        sendMessage(sender, header)
        warns.take(5).forEach { warn ->
            val entry = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.warns-entry",
                "reason" to warn.reason,
                "punisher" to warn.punisherName
            )
            sendMessage(sender, entry)
        }
    }
}

// Unwarn command (remove latest warning)
class UnwarnCommand : BasePunishmentCommand("unwarn", "pulse.punishment.unwarn", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val warns = Pulse.getPlugin().punishmentManager.service.getWarns(targetOffline)

        if (warns.isEmpty()) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "punishment.unwarn-no-warnings",
                "player" to targetName
            )
            sendMessage(sender, msg)
            return
        }

        val (punisherUuid, _) = getPunisherInfo(sender)
        val latestWarn = warns.first()
        Pulse.getPlugin().punishmentManager.service.getPunishments(targetOffline)

        // Deactivate the latest warn
        kotlinx.coroutines.runBlocking {
            Pulse.getPlugin().databaseManager.deactivatePunishment(latestWarn.id, punisherUuid)
        }

        val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
            "punishment.unwarn-success",
            "player" to targetName
        )
        sendMessage(sender, msg)
    }
}