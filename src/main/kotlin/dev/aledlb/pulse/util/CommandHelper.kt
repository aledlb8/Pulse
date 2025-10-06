package dev.aledlb.pulse.util

import dev.aledlb.pulse.Pulse
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Helper object for common command operations.
 * Reduces duplication in command classes.
 */
object CommandHelper {
    
    private val messagesManager get() = Pulse.getPlugin().messagesManager
    
    /**
     * Require sender to be a player
     */
    fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-only"))
            return null
        }
        return sender
    }
    
    /**
     * Check if sender has permission
     */
    fun requirePermission(sender: CommandSender, permission: String): Boolean {
        if (!sender.hasPermission(permission)) {
            sendMessage(sender, messagesManager.noPermission())
            return false
        }
        return true
    }
    
    /**
     * Get an online player by name
     */
    fun getOnlinePlayer(sender: CommandSender, playerName: String): Player? {
        val player = Bukkit.getPlayer(playerName)
        if (player == null) {
            sender.sendMessage(
                messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName)
            )
        }
        return player
    }
    
    /**
     * Get an offline player by name
     */
    fun getOfflinePlayer(playerName: String): org.bukkit.OfflinePlayer? {
        return try {
            Bukkit.getOfflinePlayer(playerName)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Send a formatted message to sender
     */
    fun sendMessage(sender: CommandSender, message: String) {
        MessageUtil.run { sender.sendMiniMessage(message) }
    }
    
    /**
     * Send usage message to sender
     */
    fun sendUsage(sender: CommandSender, usage: String) {
        sendMessage(sender, messagesManager.getFormattedMessage("general.usage", "usage" to usage))
    }
    
    /**
     * Parse duration string to seconds
     * Supports: 1d, 7d, 1w, 1m (month), 3m, 6m, 1y
     */
    fun parseDuration(durationStr: String): Long? {
        val regex = Regex("(\\d+)([smhdwy])")
        val match = regex.matchEntire(durationStr.lowercase()) ?: return null
        
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2]
        
        return when (unit) {
            "s" -> amount
            "m" -> amount * 60
            "h" -> amount * 3600
            "d" -> amount * 86400
            "w" -> amount * 604800
            "y" -> amount * 31536000
            else -> null
        }
    }
    
    /**
     * Format duration in seconds to readable string
     */
    fun formatDuration(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (secs > 0 || isEmpty()) append("${secs}s")
        }.trim()
    }
    
    /**
     * Parse integer argument with error handling
     */
    fun parseInt(sender: CommandSender, value: String, paramName: String = "value"): Int? {
        return value.toIntOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage(
                "general.invalid-number",
                "value" to value,
                "param" to paramName
            ))
            null
        }
    }
    
    /**
     * Parse double argument with error handling
     */
    fun parseDouble(sender: CommandSender, value: String, paramName: String = "value"): Double? {
        return value.toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage(
                "general.invalid-number",
                "value" to value,
                "param" to paramName
            ))
            null
        }
    }
}
