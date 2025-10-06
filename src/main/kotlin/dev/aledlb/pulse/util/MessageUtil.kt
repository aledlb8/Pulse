package dev.aledlb.pulse.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Centralized utility for sending MiniMessage-formatted messages
 */
object MessageUtil {
    private val miniMessage = MiniMessage.miniMessage()

    /**
     * Send a MiniMessage-formatted message to a CommandSender
     */
    fun CommandSender.sendMiniMessage(message: String) {
        this.sendMessage(miniMessage.deserialize(message))
    }

    /**
     * Send a MiniMessage-formatted message to a Player
     */
    fun Player.sendMiniMessage(message: String) {
        this.sendMessage(miniMessage.deserialize(message))
    }

    /**
     * Deserialize a MiniMessage string to a Component
     */
    fun deserialize(message: String): Component {
        return miniMessage.deserialize(message)
    }
}
