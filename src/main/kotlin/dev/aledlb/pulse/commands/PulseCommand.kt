package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender

class PulseCommand : BaseCommand() {

    private val messagesManager get() = Pulse.getPlugin().messagesManager

    override val name = "pulse"
    override val permission = "pulse.admin"
    override val description = "Main Pulse command"
    override val usage = "/pulse <reload|info|version>"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            showHelp(sender)
            return
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "info", "version" -> handleInfo(sender)
            "help" -> showHelp(sender)
            else -> {
                sendMessage(sender, messagesManager.getFormattedMessage("general.unknown-subcommand", "subcommand" to args[0]))
                showHelp(sender)
            }
        }
    }

    private fun handleReload(sender: CommandSender) {
        sendMessage(sender, messagesManager.getFormattedMessage("pulse.reload-started"))

        try {
            val plugin = Pulse.getPlugin()

            // Reload all config files from disk
            plugin.configManager.reloadAllConfigs()

            // Reload managers to apply changes
            plugin.rankManager.reload()
            plugin.shopManager.reload()
            plugin.tagManager.reload()
            plugin.chatManager.reload()
            plugin.messagesManager.reload()
            plugin.punishmentManager.reload()

            sendMessage(sender, messagesManager.getFormattedMessage("pulse.reload-success"))
            sendMessage(sender, messagesManager.getFormattedMessage("pulse.reload-managers-refreshed"))
        } catch (e: Exception) {
            sendMessage(sender, messagesManager.getFormattedMessage("pulse.reload-failed", "error" to (e.message ?: "Unknown error")))
            e.printStackTrace()
        }
    }

    private fun handleInfo(sender: CommandSender) {
        val plugin = Pulse.getPlugin()
        val version = plugin.pluginMeta.version
        val isLoaded = plugin.isPluginFullyLoaded()

        sender.sendMessage(Component.text("Pulse Info:").color(NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Version: ", NamedTextColor.GRAY).append(Component.text(version, NamedTextColor.GREEN)))
        sender.sendMessage(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(if (isLoaded) "Loaded" else "Loading...", if (isLoaded) NamedTextColor.GREEN else NamedTextColor.RED)))
        sender.sendMessage(Component.text("Developer: ", NamedTextColor.GRAY).append(Component.text("aledlb", NamedTextColor.GREEN)))
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("Pulse Commands:").color(NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/pulse info ", NamedTextColor.GRAY).append(Component.text("- Show plugin info", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/pulse reload ", NamedTextColor.GRAY).append(Component.text("- Reload configs", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/pulse help ", NamedTextColor.GRAY).append(Component.text("- Show this help", NamedTextColor.WHITE)))
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("reload", "info", "version", "help").filter {
                it.startsWith(args[0].lowercase())
            }
            else -> emptyList()
        }
    }
}