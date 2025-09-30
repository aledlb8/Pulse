package dev.aledlb.pulse.placeholders

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

interface PlaceholderProvider {
    /**
     * Process a placeholder for the given player
     * @param player The player to process placeholders for
     * @param placeholder The placeholder string (without % symbols)
     * @return The processed value or null if placeholder not handled
     */
    fun onPlaceholderRequest(player: Player?, placeholder: String): String?

    /**
     * Process a placeholder for the given offline player
     * @param player The offline player to process placeholders for
     * @param placeholder The placeholder string (without % symbols)
     * @return The processed value or null if placeholder not handled
     */
    fun onOfflinePlaceholderRequest(player: OfflinePlayer?, placeholder: String): String?

    /**
     * Get the identifier for this placeholder provider
     * @return The plugin identifier
     */
    fun getIdentifier(): String

    /**
     * Get all available placeholders from this provider
     * @return List of placeholder names
     */
    fun getPlaceholders(): List<String>
}