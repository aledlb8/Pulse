package dev.aledlb.pulse.placeholders

import dev.aledlb.pulse.util.Logger
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class PlaceholderManager {
    private val providers = ConcurrentHashMap<String, PlaceholderProvider>()
    private val placeholderPattern = Pattern.compile("%([^%]+)%")

    fun initialize() {
        // Ready for provider registration
    }

    /**
     * Register a placeholder provider
     */
    fun registerProvider(provider: PlaceholderProvider) {
        providers[provider.getIdentifier().lowercase()] = provider
        Logger.info("Registered placeholder provider: ${provider.getIdentifier()}")
    }

    /**
     * Unregister a placeholder provider
     */
    fun unregisterProvider(identifier: String) {
        providers.remove(identifier.lowercase())
        Logger.info("Unregistered placeholder provider: $identifier")
    }

    /**
     * Process a single placeholder
     */
    fun processPlaceholder(player: Player?, placeholder: String): String? {
        val parts = placeholder.split("_", limit = 2)
        if (parts.size < 2) return null

        val providerName = parts[0].lowercase()
        val placeholderName = parts[1]

        val provider = providers[providerName] ?: return null
        return provider.onPlaceholderRequest(player, placeholderName)
    }

    /**
     * Process a single placeholder for offline player
     */
    fun processOfflinePlaceholder(player: OfflinePlayer?, placeholder: String): String? {
        val parts = placeholder.split("_", limit = 2)
        if (parts.size < 2) return null

        val providerName = parts[0].lowercase()
        val placeholderName = parts[1]

        val provider = providers[providerName] ?: return null
        return provider.onOfflinePlaceholderRequest(player, placeholderName)
    }

    /**
     * Process all placeholders in a string
     */
    fun processPlaceholders(player: Player?, text: String): String {
        var result = text
        val matcher = placeholderPattern.matcher(text)

        while (matcher.find()) {
            val fullPlaceholder = matcher.group(0) // %placeholder%
            val placeholder = matcher.group(1) // placeholder

            val value = processPlaceholder(player, placeholder)
            if (value != null) {
                result = result.replace(fullPlaceholder, value)
            }
        }

        return result
    }

    /**
     * Process all placeholders in a string for offline player
     */
    fun processOfflinePlaceholders(player: OfflinePlayer?, text: String): String {
        var result = text
        val matcher = placeholderPattern.matcher(text)

        while (matcher.find()) {
            val fullPlaceholder = matcher.group(0) // %placeholder%
            val placeholder = matcher.group(1) // placeholder

            val value = processOfflinePlaceholder(player, placeholder)
            if (value != null) {
                result = result.replace(fullPlaceholder, value)
            }
        }

        return result
    }

    /**
     * Get all registered providers
     */
    fun getProviders(): Map<String, PlaceholderProvider> = providers.toMap()

    /**
     * Get all available placeholders
     */
    fun getAllPlaceholders(): Map<String, List<String>> {
        return providers.mapValues { it.value.getPlaceholders() }
    }

    /**
     * Check if a provider is registered
     */
    fun hasProvider(identifier: String): Boolean {
        return providers.containsKey(identifier.lowercase())
    }

    /**
     * Get a specific provider
     */
    fun getProvider(identifier: String): PlaceholderProvider? {
        return providers[identifier.lowercase()]
    }
}