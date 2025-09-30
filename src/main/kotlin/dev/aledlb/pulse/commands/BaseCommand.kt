package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
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
            sender.sendMessage(Pulse.getPlugin().messagesManager.noPermission())
            return true
        }

        try {
            execute(sender, args)
        } catch (e: Exception) {
            sender.sendMessage("${Pulse.getPlugin().messagesManager.getPrefix()}§cAn error occurred while executing this command!")
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
        return getTabCompletions(sender, args)
    }

    protected open fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return emptyList()
    }

    protected fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sender.sendMessage("${Pulse.getPlugin().messagesManager.getPrefix()}§cThis command can only be used by players!")
            return null
        }
        return sender
    }

    protected fun sendMessage(sender: CommandSender, message: String) {
        sender.sendMessage("${Pulse.getPlugin().messagesManager.getPrefix()}$message")
    }
}