package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
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
                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("general.unknown-subcommand", "subcommand" to args[0]))
                showHelp(sender)
            }
        }
    }

    private fun handleReload(sender: CommandSender) {
        sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("pulse.reload-started"))

        try {
            val plugin = Pulse.getPlugin()
            val configManager = plugin.configManager

            // Reload config files from disk
            configManager.reloadConfig("config.yml")
            configManager.reloadConfig("database.yml")
            configManager.reloadConfig("messages.yml")
            configManager.reloadConfig("punishment.yml")
            configManager.reloadConfig("ranks.yml")
            configManager.reloadConfig("shop.yml")
            configManager.reloadConfig("tags.yml")

            // Reload managers to apply changes
            plugin.rankManager.reload()
            plugin.shopManager.reload()
            plugin.tagManager.reload()
            plugin.chatManager.reload()
            plugin.messagesManager.reload()
            plugin.punishmentManager.reload()

            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("pulse.reload-success"))
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("pulse.reload-managers-refreshed"))
        } catch (e: Exception) {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("pulse.reload-failed", "error" to (e.message ?: "Unknown error")))
            e.printStackTrace()
        }
    }

    private fun handleInfo(sender: CommandSender) {
        val plugin = Pulse.getPlugin()
        val version = plugin.pluginMeta.version
        val isLoaded = plugin.isPluginFullyLoaded()

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║          §fPULSE INFO§5            ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §fVersion: §a$version")
        sendMessage(sender, "§5║ §fStatus: ${if (isLoaded) "§aLoaded" else "§cLoading..."}")
        sendMessage(sender, "§5║ §fDeveloper: §aaledlb")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")
    }

    private fun showHelp(sender: CommandSender) {
        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║         §fPULSE COMMANDS§5         ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §f/pulse info §7- Show plugin info")
        sendMessage(sender, "§5║ §f/pulse reload §7- Reload configs")
        sendMessage(sender, "§5║ §f/pulse help §7- Show this help")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")
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