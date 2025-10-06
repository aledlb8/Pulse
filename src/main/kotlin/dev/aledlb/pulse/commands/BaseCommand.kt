package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.MessageUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

abstract class BaseCommand : CommandExecutor, TabCompleter {

    abstract val name: String
    abstract val permission: String?
    abstract val description: String
    abstract val usage: String

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (permission != null && !sender.hasPermission(permission!!)) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.noPermission())
            return true
        }

        try {
            execute(sender, args)
        } catch (e: Exception) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("general.command-error"))
            e.printStackTrace()
        }

        return true
    }

    abstract fun execute(sender: CommandSender, args: Array<out String>)

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String>? {
        // Check permission for tab completion
        if (permission != null && !sender.hasPermission(permission!!)) {
            return emptyList()
        }
        return getTabCompletions(sender, args)
    }

    protected open fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return emptyList()
    }

    protected fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getFormattedMessage("general.player-only"))
            return null
        }
        return sender
    }

    protected fun requirePermission(sender: CommandSender, permission: String): Boolean {
        if (!sender.hasPermission(permission)) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.noPermission())
            return false
        }
        return true
    }

    protected fun getOnlinePlayer(sender: CommandSender, playerName: String): Player? {
        val player = org.bukkit.Bukkit.getPlayer(playerName)
        if (player == null) {
            sendMessage(sender, Pulse.getPlugin().messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
        }
        return player
    }

    protected fun sendMessage(sender: CommandSender, message: String) {
        MessageUtil.run { sender.sendMiniMessage(message) }
    }

    protected fun sendUsage(sender: CommandSender) {
        sendMessage(sender, Pulse.getPlugin().messagesManager.getFormattedMessage("general.usage", "usage" to usage))
    }
}