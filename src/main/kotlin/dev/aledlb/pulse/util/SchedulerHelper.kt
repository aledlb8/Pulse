package dev.aledlb.pulse.util

import dev.aledlb.pulse.Pulse
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component

/**
 * Helper object for Folia-compatible player scheduling.
 * Provides a consistent way to schedule tasks for players.
 */
object SchedulerHelper {
    
    /**
     * Execute an action on a player's scheduler (Folia-compatible)
     * 
     * @param player The player to execute the action for
     * @param action The action to execute
     */
    fun runForPlayer(player: Player, action: () -> Unit) {
        player.scheduler.run(Pulse.getPlugin(), { _ ->
            action()
        }, null)
    }
    
    /**
     * Kick a player with a message (Folia-compatible)
     * 
     * @param player The player to kick
     * @param message The kick message
     */
    fun kickPlayer(player: Player, message: String) {
        runForPlayer(player) {
            player.kick(Component.text(message))
        }
    }
    
    /**
     * Send a message to a player (Folia-compatible)
     * 
     * @param player The player to send the message to
     * @param message The message to send
     */
    fun sendMessage(player: Player, message: String) {
        runForPlayer(player) {
            player.sendMessage(Component.text(message))
        }
    }
    
    /**
     * Execute an action for a player if they are online
     * 
     * @param player The player (nullable)
     * @param action The action to execute
     */
    fun runIfOnline(player: Player?, action: () -> Unit) {
        player?.let {
            if (it.isOnline) {
                runForPlayer(it, action)
            }
        }
    }
}
