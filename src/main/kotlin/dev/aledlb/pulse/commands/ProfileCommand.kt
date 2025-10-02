package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.profile.ProfileGUI
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ProfileCommand(private val profileGUI: ProfileGUI) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players.")
            return true
        }

        val messagesManager = Pulse.getPlugin().messagesManager

        val target = if (args.isEmpty()) {
            // View own profile
            sender
        } else {
            // View another player's profile
            if (!sender.hasPermission("pulse.profile.others")) {
                sender.sendMessage(messagesManager.getFormattedMessage("no-permission"))
                return true
            }

            Bukkit.getOfflinePlayer(args[0])
        }

        if (target.name == null) {
            sender.sendMessage("§cPlayer '${args[0]}' not found.")
            return true
        }

        profileGUI.openProfile(sender, target)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (args.size == 1 && sender.hasPermission("pulse.profile.others")) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
