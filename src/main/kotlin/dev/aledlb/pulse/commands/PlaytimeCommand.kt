package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.playtime.PlaytimeManager
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PlaytimeCommand(private val playtimeManager: PlaytimeManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val messagesManager = Pulse.getPlugin().messagesManager

        when (args.size) {
            0 -> {
                // Show own playtime
                if (sender !is Player) {
                    sender.sendMiniMessage(messagesManager.getMessage("general.player-only"))
                    return true
                }

                val playtime = playtimeManager.getFormattedPlaytime(sender.uniqueId)
                val playtimeHours = "%.2f".format(playtimeManager.getPlaytimeHours(sender.uniqueId))

                sender.sendMiniMessage(
                    messagesManager.getMessage("playtime.self")
                        .replace("{playtime}", playtime)
                        .replace("{hours}", playtimeHours)
                )
                return true
            }

            1 -> {
                // Show target player's playtime
                if (!sender.hasPermission("pulse.playtime.others")) {
                    sender.sendMiniMessage(messagesManager.getMessage("general.no-permission"))
                    return true
                }

                val targetName = args[0]
                val targetPlayer = Bukkit.getPlayerExact(targetName)

                if (targetPlayer != null) {
                    // Player is online
                    val playtime = playtimeManager.getFormattedPlaytime(targetPlayer.uniqueId)
                    val playtimeHours = "%.2f".format(playtimeManager.getPlaytimeHours(targetPlayer.uniqueId))

                    sender.sendMiniMessage(
                        messagesManager.getMessage("playtime.other")
                            .replace("{player}", targetPlayer.name)
                            .replace("{playtime}", playtime)
                            .replace("{hours}", playtimeHours)
                    )
                } else {
                    // Try offline player
                    @Suppress("DEPRECATION")
                    val offlinePlayer = Bukkit.getOfflinePlayer(targetName)

                    if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
                        sender.sendMiniMessage(
                            messagesManager.getMessage("general.player-not-found")
                                .replace("{player}", targetName)
                        )
                        return true
                    }

                    val playtime = playtimeManager.getFormattedPlaytime(offlinePlayer.uniqueId)
                    val playtimeHours = "%.2f".format(playtimeManager.getPlaytimeHours(offlinePlayer.uniqueId))

                    sender.sendMiniMessage(
                        messagesManager.getMessage("playtime.other")
                            .replace("{player}", offlinePlayer.name ?: targetName)
                            .replace("{playtime}", playtime)
                            .replace("{hours}", playtimeHours)
                    )
                }
                return true
            }

            else -> {
                sender.sendMiniMessage(messagesManager.getMessage("playtime.usage"))
                return true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            // Suggest online player names
            if (sender.hasPermission("pulse.playtime.others")) {
                return Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[0].lowercase()) }
                    .sorted()
            }
        }
        return emptyList()
    }
}
