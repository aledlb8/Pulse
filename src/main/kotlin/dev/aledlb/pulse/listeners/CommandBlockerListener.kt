package dev.aledlb.pulse.listeners

import dev.aledlb.pulse.Pulse
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.TabCompleteEvent

/**
 * Blocks specific commands at the lowest priority to prevent them from being executed.
 * This intercepts commands before Bukkit can register them.
 */
class CommandBlockerListener : Listener {

    private val miniMessage = MiniMessage.miniMessage()

    private val blockedCommands: List<String>
        get() {
            val config = Pulse.getPlugin().configManager.getConfig("config.yml") ?: return emptyList()
            return config.node("command-blocker", "blocked-commands")
                .getList(String::class.java) ?: emptyList()
        }

    private val blockedMessage: String
        get() {
            val config = Pulse.getPlugin().configManager.getConfig("config.yml") ?: return "<red>Unknown command. Type \"/help\" for help."
            return config.node("command-blocker", "blocked-message")
                .getString("<red>Unknown command. Type \"/help\" for help.")
        }

    private val helpEnabled: Boolean
        get() {
            val config = Pulse.getPlugin().configManager.getConfig("config.yml") ?: return true
            return config.node("help-command", "enabled").getBoolean(true)
        }

    private val helpMessage: List<String>
        get() {
            val config = Pulse.getPlugin().configManager.getConfig("config.yml") ?: return emptyList()
            return config.node("help-command", "message")
                .getList(String::class.java) ?: emptyList()
        }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val message = event.message.lowercase()
        val command = message.split(" ")[0].removePrefix("/")
        val normalizedBlocked = blockedCommands.map { it.lowercase() }

        // Handle blocked commands
        if (normalizedBlocked.any { it == command }) {
            event.isCancelled = true
            event.player.sendMessage(miniMessage.deserialize(blockedMessage))
            return
        }

        // Handle custom help command
        if (helpEnabled && (command == "help" || command == "?")) {
            event.isCancelled = true
            sendHelpMessage(event.player)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onTabComplete(event: TabCompleteEvent) {
        val buffer = event.buffer.lowercase()
        val command = buffer.removePrefix("/").split(" ")[0]
        val normalizedBlocked = blockedCommands.map { it.lowercase() }

        // Remove blocked commands from tab completions
        event.completions.removeIf { completion ->
            normalizedBlocked.any { blocked ->
                blocked == command || blocked == completion.lowercase()
            }
        }
    }

    private fun sendHelpMessage(player: org.bukkit.entity.Player) {
        helpMessage.forEach { line ->
            player.sendMessage(miniMessage.deserialize(line))
        }
    }
}
