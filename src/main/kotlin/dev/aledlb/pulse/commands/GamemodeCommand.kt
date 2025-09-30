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
                sendMessage(sender, "§cUsage: /gamemode <mode> [player]")
                sendMessage(sender, "§cModes: §7creative, survival, adventure, spectator")
                return
            }
            val parsed = parseGameMode(args[0])
            if (parsed == null) {
                sendMessage(sender, "§cInvalid gamemode: §7${args[0]}")
                return
            }
            parsed
        }

        // Determine target player
        val targetArg = if (mode == null) args.getOrNull(1) else args.getOrNull(0)
        val target = if (targetArg != null) {
            if (!sender.hasPermission("pulse.gamemode.others")) {
                sendMessage(sender, "§cYou don't have permission to change others' gamemode!")
                return
            }

            val player = Bukkit.getPlayer(targetArg)
            if (player == null) {
                sendMessage(sender, "§cPlayer not found: §7$targetArg")
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
            sendMessage(sender, "§aGamemode changed to §7$displayName")
        } else {
            sendMessage(sender, "§aSet §7${target.name}§a's gamemode to §7$displayName")
            target.sendMessage("${Pulse.getPlugin().messagesManager.getPrefix()}§aYour gamemode has been set to §7$displayName")
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