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
        if (args.isEmpty()) {
            sendMessage(sender, "§cUsage: $usage")
            return
        }

        val targetName = args[0]
        val offlinePlayer = Bukkit.getOfflinePlayer(targetName)

        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            sendMessage(sender, "§cPlayer not found: §7$targetName")
            return
        }

        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"

        if (requiresReason && args.size < 2) {
            sendMessage(sender, "§cUsage: $usage")
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
            sendMessage(sender, "§cPlayer §7$targetName §cis not online!")
            return
        }

        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.kick(target, reason, punisherUuid, punisherName)
        sendMessage(sender, "§aKicked §7$targetName §afor: §7$reason")
    }
}

// Ban command
class BanCommand : BasePunishmentCommand("ban", "pulse.punishment.ban") {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.ban(targetOffline, targetName, reason, punisherUuid, punisherName)
        sendMessage(sender, "§aBanned §7$targetName §afor: §7$reason")
    }
}

// Tempban command
class TempbanCommand : BasePunishmentCommand("tempban", "pulse.punishment.tempban") {
    override val usage = "/tempban <player> <duration> [reason...]"

    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (args.size < 2) {
            sendMessage(sender, "§cUsage: /tempban <player> <duration> [reason...]")
            sendMessage(sender, "§cExample: /tempban Player123 1d Griefing")
            sendMessage(sender, "§cDuration format: 1s, 5m, 2h, 7d")
            return
        }

        val duration = parseDuration(args[1])
        if (duration == null) {
            sendMessage(sender, "§cInvalid duration format. Use: 1s, 5m, 2h, 7d")
            return
        }

        val reason = if (args.size > 2) args.slice(2 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.tempban(targetOffline, targetName, reason, punisherUuid, punisherName, duration)
        sendMessage(sender, "§aTemporarily banned §7$targetName §afor §7${formatDuration(duration)} §aReason: §7$reason")
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
            sendMessage(sender, "§cPlayer must be online to IP ban!")
            return
        }

        val ip = target.address?.address?.hostAddress ?: run {
            sendMessage(sender, "§cCould not determine player's IP address!")
            return
        }

        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.ipban(targetOffline, targetName, ip, reason, punisherUuid, punisherName)
        sendMessage(sender, "§aIP banned §7$targetName §afor: §7$reason")
    }
}

// TempIPban command
class TempipbanCommand : BasePunishmentCommand("tempipban", "pulse.punishment.tempipban") {
    override val usage = "/tempipban <player> <duration> [reason...]"

    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (args.size < 2) {
            sendMessage(sender, "§cUsage: /tempipban <player> <duration> [reason...]")
            return
        }

        if (target == null || !target.isOnline) {
            sendMessage(sender, "§cPlayer must be online to temp IP ban!")
            return
        }

        val ip = target.address?.address?.hostAddress ?: run {
            sendMessage(sender, "§cCould not determine player's IP address!")
            return
        }

        val duration = parseDuration(args[1]) ?: run {
            sendMessage(sender, "§cInvalid duration format. Use: 1s, 5m, 2h, 7d")
            return
        }

        val reason = if (args.size > 2) args.slice(2 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.tempipban(targetOffline, targetName, ip, reason, punisherUuid, punisherName, duration)
        sendMessage(sender, "§aTemporarily IP banned §7$targetName")
    }
}

// Unban command
class UnbanCommand : BasePunishmentCommand("unban", "pulse.punishment.unban", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        val success = Pulse.getPlugin().punishmentManager.service.unban(targetOffline, targetName, punisherUuid, punisherName)
        if (success) {
            sendMessage(sender, "§aUnbanned §7$targetName")
        } else {
            sendMessage(sender, "§c$targetName §7is not banned!")
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
        sendMessage(sender, "§aMuted §7$targetName §a${duration?.let { "for ${formatDuration(it)}" } ?: "permanently"}")
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
            sendMessage(sender, "§aUnmuted §7$targetName")
        } else {
            sendMessage(sender, "§c$targetName §7is not muted!")
        }
    }
}

// Freeze command
class FreezeCommand : BasePunishmentCommand("freeze", "pulse.punishment.freeze", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        if (target == null || !target.isOnline) {
            sendMessage(sender, "§cPlayer §7$targetName §cis not online!")
            return
        }

        Pulse.getPlugin().punishmentManager.service.freeze(targetOffline)
        target.sendMessage(net.kyori.adventure.text.Component.text("§c§lFROZEN\n§7You have been frozen by staff. Do not log out!"))
        sendMessage(sender, "§aFroze §7$targetName")
    }
}

// Unfreeze command
class UnfreezeCommand : BasePunishmentCommand("unfreeze", "pulse.punishment.freeze", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        Pulse.getPlugin().punishmentManager.service.unfreeze(targetOffline)
        target?.sendMessage(net.kyori.adventure.text.Component.text("§a§lUNFROZEN\n§7You have been unfrozen."))
        sendMessage(sender, "§aUnfroze §7$targetName")
    }
}

// Warn command
class WarnCommand : BasePunishmentCommand("warn", "pulse.punishment.warn") {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val reason = if (args.size > 1) args.slice(1 until args.size).joinToString(" ") else "No reason specified"
        val (punisherUuid, punisherName) = getPunisherInfo(sender)

        Pulse.getPlugin().punishmentManager.service.warn(targetOffline, targetName, reason, punisherUuid, punisherName)
        sendMessage(sender, "§aWarned §7$targetName §afor: §7$reason")
    }
}

// Warns command (view warnings)
class WarnsCommand : BasePunishmentCommand("warns", "pulse.punishment.warns", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val warns = Pulse.getPlugin().punishmentManager.service.getWarns(targetOffline)

        if (warns.isEmpty()) {
            sendMessage(sender, "§7$targetName §ahas no active warnings.")
            return
        }

        sendMessage(sender, "§e§lWarnings for $targetName §7(${warns.size})")
        warns.take(5).forEach { warn ->
            sendMessage(sender, "§7- §f${warn.reason} §8(by ${warn.punisherName})")
        }
    }
}

// Unwarn command (remove latest warning)
class UnwarnCommand : BasePunishmentCommand("unwarn", "pulse.punishment.unwarn", requiresReason = false) {
    override fun executePunishment(sender: CommandSender, target: Player?, targetOffline: UUID, targetName: String, args: Array<out String>) {
        val warns = Pulse.getPlugin().punishmentManager.service.getWarns(targetOffline)

        if (warns.isEmpty()) {
            sendMessage(sender, "§c$targetName §7has no warnings to remove!")
            return
        }

        val (punisherUuid, _) = getPunisherInfo(sender)
        val latestWarn = warns.first()
        Pulse.getPlugin().punishmentManager.service.getPunishments(targetOffline)

        // Deactivate the latest warn
        kotlinx.coroutines.runBlocking {
            Pulse.getPlugin().databaseManager.deactivatePunishment(latestWarn.id, punisherUuid)
        }

        sendMessage(sender, "§aRemoved latest warning from §7$targetName")
    }
}