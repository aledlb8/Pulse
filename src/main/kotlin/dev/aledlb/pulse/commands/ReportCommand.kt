package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import dev.aledlb.pulse.util.Logger
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import dev.aledlb.pulse.util.AsyncHelper
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import dev.aledlb.pulse.util.SchedulerHelper
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.Bukkit
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.command.Command
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.command.CommandExecutor
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.command.CommandSender
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.command.TabCompleter
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.entity.Player
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage

class ReportCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMiniMessage("<red>This command can only be used by players.")
            return true
        }

        if (!sender.hasPermission("pulse.report")) {
            sender.sendMiniMessage("<red>You don't have permission to use this command.")
            return true
        }

        if (args.size < 2) {
            sender.sendMiniMessage("<red>Usage: /report <player> <reason>")
            return true
        }

        val targetName = args[0]

        // Try to find online player first
        var target = Bukkit.getPlayerExact(targetName)
        var targetOffline: org.bukkit.OfflinePlayer? = null

        if (target == null) {
            // Try offline player
            targetOffline = Bukkit.getOfflinePlayerIfCached(targetName)

            if (targetOffline == null || !targetOffline.hasPlayedBefore()) {
                sender.sendMiniMessage("<red>Player '$targetName' not found.")
                return true
            }
        }

        val targetUuid = target?.uniqueId ?: targetOffline!!.uniqueId
        val targetDisplayName = target?.name ?: targetOffline!!.name ?: targetName
        if (targetUuid == sender.uniqueId) {
            sender.sendMiniMessage("<red>You cannot report yourself.")
            return true
        }


        val reason = args.drop(1).joinToString(" ")

        if (reason.length < 5) {
            sender.sendMiniMessage("<red>Report reason must be at least 5 characters long.")
            return true
        }

        // Save report asynchronously
        AsyncHelper.loadAsync(
            entityName = "report",
            operation = {
                val db = Pulse.getPlugin().databaseManager
                db.saveReport(
                    reportedUuid = targetUuid,
                    reportedName = targetDisplayName,
                    reporterUuid = sender.uniqueId,
                    reporterName = sender.name,
                    reason = reason
                )
            },
            onSuccess = { reportId ->
                // Notify player
                SchedulerHelper.runForPlayer(sender) {
                    sender.sendMiniMessage("<green>Your report has been submitted successfully.")
                    sender.sendMiniMessage("<gray>Report ID: <yellow>#$reportId")
                    sender.sendMiniMessage("<gray>Staff will review it shortly.")
                }

                // Notify staff
                Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                    Bukkit.getOnlinePlayers()
                        .filter { it.hasPermission("pulse.reports.notify") }
                        .forEach { staff ->
                            staff.sendMiniMessage("<gray>[<gold>Reports<gray>] <yellow>${sender.name} <gray>reported <red>$targetDisplayName")
                            staff.sendMiniMessage("<gray>Reason: <white>$reason")
                            staff.sendMiniMessage("<gray>Report ID: <yellow>#$reportId")
                        }
                })

                Logger.info("${sender.name} reported $targetDisplayName: $reason")
            },
            onError = {
                SchedulerHelper.runForPlayer(sender) {
                    sender.sendMiniMessage("<red>An error occurred while submitting your report.")
                }
            }
        )

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            // Show online players in tab complete
            return Bukkit.getOnlinePlayers()
                .filter { it.uniqueId != (sender as? Player)?.uniqueId }
                .map { it.name }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}