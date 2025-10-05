package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import dev.aledlb.pulse.util.AsyncHelper
import dev.aledlb.pulse.util.SchedulerHelper
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ReportCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players.")
            return true
        }

        if (!sender.hasPermission("pulse.report")) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /report <player> <reason>")
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
                sender.sendMessage("§cPlayer '$targetName' not found.")
                return true
            }
        }

        val targetUuid = target?.uniqueId ?: targetOffline!!.uniqueId
        val targetDisplayName = target?.name ?: targetOffline!!.name ?: targetName
        if (targetUuid == sender.uniqueId) {
            sender.sendMessage("§cYou cannot report yourself.")
            return true
        }


        val reason = args.drop(1).joinToString(" ")

        if (reason.length < 5) {
            sender.sendMessage("§cReport reason must be at least 5 characters long.")
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
                    sender.sendMessage("§aYour report has been submitted successfully.")
                    sender.sendMessage("§7Report ID: §e#$reportId")
                    sender.sendMessage("§7Staff will review it shortly.")
                }

                // Notify staff
                Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                    Bukkit.getOnlinePlayers()
                        .filter { it.hasPermission("pulse.reports.notify") }
                        .forEach { staff ->
                            staff.sendMessage("§7[§6Reports§7] §e${sender.name} §7reported §c$targetDisplayName")
                            staff.sendMessage("§7Reason: §f$reason")
                            staff.sendMessage("§7Report ID: §e#$reportId")
                        }
                })

                Logger.info("${sender.name} reported $targetDisplayName: $reason")
            },
            onError = {
                SchedulerHelper.runForPlayer(sender) {
                    sender.sendMessage("§cAn error occurred while submitting your report.")
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