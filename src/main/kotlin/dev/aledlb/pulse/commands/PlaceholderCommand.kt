package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class PlaceholderCommand : BaseCommand() {

    override val name = "placeholder"
    override val permission = "pulse.placeholder"
    override val description = "Manage and test placeholders"
    override val usage = "/placeholder <list|test|providers> [args...]"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            showHelp(sender)
            return
        }

        when (args[0].lowercase()) {
            "list" -> handleList(sender, args)
            "test" -> handleTest(sender, args)
            "providers" -> handleProviders(sender)
            "help" -> showHelp(sender)
            else -> {
                sendMessage(sender, "§cUnknown subcommand: ${args[0]}")
                showHelp(sender)
            }
        }
    }

    private fun handleList(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.placeholder.list")) {
            sendMessage(sender, "§cYou don't have permission to list placeholders!")
            return
        }

        val placeholderManager = Pulse.getPlugin().placeholderManager
        val allPlaceholders = placeholderManager.getAllPlaceholders()

        if (args.size > 1) {
            // List placeholders for specific provider
            val provider = args[1].lowercase()
            val placeholders = allPlaceholders[provider]

            if (placeholders == null) {
                sendMessage(sender, "§cProvider §e$provider§c not found!")
                return
            }

            sendMessage(sender, "§f")
            sendMessage(sender, "§5╔════════════════════════════════╗")
            sendMessage(sender, "§5║    §fPLACEHOLDERS - §e${provider.uppercase()}§5     ║")
            sendMessage(sender, "§5╚════════════════════════════════╝")

            for (placeholder in placeholders.sorted()) {
                sendMessage(sender, "§7- §f%${provider}_${placeholder}%")
            }
            sendMessage(sender, "§f")
        } else {
            // List all providers and their placeholder counts
            sendMessage(sender, "§f")
            sendMessage(sender, "§5╔════════════════════════════════╗")
            sendMessage(sender, "§5║       §fALL PLACEHOLDERS§5       ║")
            sendMessage(sender, "§5╚════════════════════════════════╝")

            for ((provider, placeholders) in allPlaceholders) {
                sendMessage(sender, "§e$provider §7(${placeholders.size} placeholders)")
                sendMessage(sender, "§7  Use: §f/placeholder list $provider")
            }

            sendMessage(sender, "§f")
            sendMessage(sender, "§7Total providers: §a${allPlaceholders.size}")
            sendMessage(sender, "§7Total placeholders: §a${allPlaceholders.values.sumOf { it.size }}")
            sendMessage(sender, "§f")
        }
    }

    private fun handleTest(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.placeholder.test")) {
            sendMessage(sender, "§cYou don't have permission to test placeholders!")
            return
        }

        if (args.size < 2) {
            sendMessage(sender, "§cUsage: /placeholder test <placeholder> [player]")
            sendMessage(sender, "§7Example: /placeholder test pulse_rank")
            return
        }

        val placeholder = args[1]
        val targetPlayer = if (args.size > 2) {
            val playerName = args[2]
            Pulse.getPlugin().server.getPlayer(playerName) ?: run {
                sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
                return
            }
        } else {
            sender as? Player ?: run {
                sendMessage(sender, "§cYou must specify a player when using this command from console!")
                return
            }
        }

        val placeholderManager = Pulse.getPlugin().placeholderManager

        // Test the placeholder
        val result = placeholderManager.processPlaceholder(targetPlayer, placeholder)

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║      §fPLACEHOLDER TEST§5        ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §fPlayer: §e${targetPlayer.name}")
        sendMessage(sender, "§5║ §fPlaceholder: §e%$placeholder%")
        sendMessage(sender, "§5║ §fResult: §a${result ?: "§cnull"}")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")

        // Test with text replacement
        val testText = "Hello %$placeholder%!"
        val processedText = placeholderManager.processPlaceholders(targetPlayer, testText)
        sendMessage(sender, "§7Text test: §f$testText")
        sendMessage(sender, "§7Result: §f$processedText")
        sendMessage(sender, "§f")
    }

    private fun handleProviders(sender: CommandSender) {
        if (!sender.hasPermission("pulse.placeholder.providers")) {
            sendMessage(sender, "§cYou don't have permission to view providers!")
            return
        }

        val placeholderManager = Pulse.getPlugin().placeholderManager
        val providers = placeholderManager.getProviders()

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║    §fPLACEHOLDER PROVIDERS§5     ║")
        sendMessage(sender, "§5╚════════════════════════════════╝")

        for ((identifier, provider) in providers) {
            val placeholderCount = provider.getPlaceholders().size
            sendMessage(sender, "§e$identifier §7- $placeholderCount placeholders")
        }

        if (providers.isEmpty()) {
            sendMessage(sender, "§7No placeholder providers registered.")
        }

        sendMessage(sender, "§f")
    }

    private fun showHelp(sender: CommandSender) {
        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║    §fPLACEHOLDER COMMANDS§5      ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §f/placeholder list §7- List all providers")
        sendMessage(sender, "§5║ §f/placeholder list <provider> §7- List provider placeholders")
        sendMessage(sender, "§5║ §f/placeholder test <placeholder> [player] §7- Test placeholder")
        sendMessage(sender, "§5║ §f/placeholder providers §7- List all providers")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("list", "test", "providers", "help")
                .filter { it.startsWith(args[0].lowercase()) }

            2 -> when (args[0].lowercase()) {
                "list" -> {
                    val placeholderManager = Pulse.getPlugin().placeholderManager
                    placeholderManager.getProviders().keys.toList()
                        .filter { it.startsWith(args[1].lowercase()) }
                }
                "test" -> {
                    // Suggest common placeholders
                    listOf(
                        "pulse_rank", "pulse_rank_display", "pulse_rank_prefix",
                        "pulse_permissions_count", "pulse_player_formatted"
                    ).filter { it.startsWith(args[1].lowercase()) }
                }
                else -> emptyList()
            }

            3 -> when (args[0].lowercase()) {
                "test" -> {
                    // Suggest online players
                    Pulse.getPlugin().server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[2], true) }
                }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}