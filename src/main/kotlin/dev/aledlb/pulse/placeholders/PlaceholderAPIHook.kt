package dev.aledlb.pulse.placeholders

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

/**
 * PlaceholderAPI integration for Pulse
 * This class hooks into PlaceholderAPI to provide Pulse placeholders to other plugins
 */
class PlaceholderAPIHook : PlaceholderExpansion() {

    private val plugin: Pulse = Pulse.getPlugin()

    override fun getIdentifier(): String = "pulse"

    override fun getAuthor(): String = "aledlb"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null

        return if (player.isOnline) {
            plugin.placeholderManager.processPlaceholder(player.player, "pulse_$params")
        } else {
            plugin.placeholderManager.processOfflinePlaceholder(player, "pulse_$params")
        }
    }

    // Remove the override since it conflicts with newer versions

    /**
     * Register this expansion with PlaceholderAPI
     */
    fun registerExpansion(): Boolean {
        return try {
            val success = register()
            if (success) {
                Logger.success("Successfully registered PlaceholderAPI expansion")
            } else {
                Logger.warn("Failed to register PlaceholderAPI expansion")
            }
            success
        } catch (e: Exception) {
            Logger.error("Error registering PlaceholderAPI expansion", e)
            false
        }
    }

    /**
     * Unregister this expansion from PlaceholderAPI
     */
    fun unregisterExpansion() {
        try {
            unregister()
            Logger.info("Unregistered PlaceholderAPI expansion")
        } catch (e: Exception) {
            Logger.error("Error unregistering PlaceholderAPI expansion", e)
        }
    }
}