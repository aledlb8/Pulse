package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

// Base class for all gamemode commands
abstract class BaseGamemodeCommand(
    private val mode: GameMode?,
    private val modeName: String,
    commandName: String,
    commandPermission: String
) : BaseCommand() {

    override val name = commandName
    override val permission = commandPermission
    override val description = "Change to $modeName mode"
    override val usage = "/$commandName [player]"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        // For /gamemode command, mode is null and needs to be parsed
        val targetMode = mode ?: run {
            if (args.isEmpty()) {
                sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("gamemode.usage"))
                sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("gamemode.modes-list"))
                return
            }
            val parsed = parseGameMode(args[0])
            if (parsed == null) {
                val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                    "gamemode.invalid-mode",
                    "mode" to args[0]
                )
                sendMessage(sender, msg)
                return
            }
            parsed
        }

        // Determine target player
        val targetArg = if (mode == null) args.getOrNull(1) else args.getOrNull(0)
        val target = if (targetArg != null) {
            if (!sender.hasPermission("pulse.gamemode.others")) {
                sendMessage(sender, Pulse.getPlugin().messagesManager.getMessage("gamemode.no-permission-others"))
                return
            }

            val player = Bukkit.getPlayer(targetArg)
            if (player == null) {
                val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                    "gamemode.player-not-online",
                    "player" to targetArg
                )
                sendMessage(sender, msg)
                return
            }
            player
        } else {
            requirePlayer(sender) ?: return
        }

        // Set gamemode
        target.gameMode = targetMode
        val displayName = targetMode.name.lowercase().replaceFirstChar { it.uppercase() }

        // Send messages
        if (target == sender) {
            val msg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "gamemode.changed-self",
                "mode" to displayName
            )
            sendMessage(sender, msg)
        } else {
            val senderMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "gamemode.changed-other",
                "player" to target.name,
                "mode" to displayName
            )
            sendMessage(sender, senderMsg)

            val targetMsg = Pulse.getPlugin().messagesManager.getFormattedMessage(
                "gamemode.changed-notification",
                "mode" to displayName
            )
            target.sendMessage(targetMsg)
        }
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when {
            mode == null && args.size == 1 -> {
                // /gamemode <tab> - show modes
                listOf("creative", "survival", "adventure", "spectator")
                    .filter { it.startsWith(args[0].lowercase()) }
            }
            mode == null && args.size == 2 -> {
                // /gamemode <mode> <tab> - show players
                getPlayerCompletions(sender, args[1])
            }
            args.size == 1 -> {
                // /gmc <tab> - show players
                getPlayerCompletions(sender, args[0])
            }
            else -> emptyList()
        }
    }

    private fun getPlayerCompletions(sender: CommandSender, input: String): List<String> {
        return if (sender.hasPermission("pulse.gamemode.others")) {
            Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.lowercase().startsWith(input.lowercase()) }
        } else {
            emptyList()
        }
    }

    private fun parseGameMode(input: String): GameMode? {
        return when (input.lowercase()) {
            "0", "s", "survival" -> GameMode.SURVIVAL
            "1", "c", "creative" -> GameMode.CREATIVE
            "2", "a", "adventure" -> GameMode.ADVENTURE
            "3", "sp", "spectator" -> GameMode.SPECTATOR
            else -> null
        }
    }
}

// Specific command implementations
class GamemodeCommand : BaseGamemodeCommand(null, "gamemode", "gamemode", "pulse.gamemode")
class GamemodeCreativeCommand : BaseGamemodeCommand(GameMode.CREATIVE, "Creative", "gmc", "pulse.gamemode.creative")
class GamemodeSurvivalCommand : BaseGamemodeCommand(GameMode.SURVIVAL, "Survival", "gms", "pulse.gamemode.survival")
class GamemodeAdventureCommand : BaseGamemodeCommand(GameMode.ADVENTURE, "Adventure", "gma", "pulse.gamemode.adventure")
class GamemodeSpectatorCommand : BaseGamemodeCommand(GameMode.SPECTATOR, "Spectator", "gmsp", "pulse.gamemode.spectator")