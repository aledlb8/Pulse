package dev.aledlb.pulse.messages

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class MessagesManager {
    private val messages = mutableMapOf<String, String>()
    private val serializer = LegacyComponentSerializer.legacyAmpersand()

    fun initialize() {
        loadMessages()
    }

    private fun loadMessages() {
        val configManager = Pulse.getPlugin().configManager
        val messagesConfig = configManager.getConfig("messages.yml")

        if (messagesConfig == null) {
            Logger.warn("Messages configuration not found, using defaults")
            return
        }

        // Load general messages
        loadMessageSection("general", messagesConfig)
        loadMessageSection("punishment", messagesConfig)
        loadMessageSection("economy", messagesConfig)
        loadMessageSection("coin", messagesConfig)
        loadMessageSection("rank", messagesConfig)
        loadMessageSection("permission", messagesConfig)
        loadMessageSection("shop", messagesConfig)
        loadMessageSection("tag", messagesConfig)
        loadMessageSection("pulse", messagesConfig)
        loadMessageSection("gamemode", messagesConfig)

        Logger.info("Loaded ${messages.size} messages from configuration")
    }

    private fun loadMessageSection(section: String, config: org.spongepowered.configurate.ConfigurationNode) {
        val sectionNode = config.node(section)
        for (key in sectionNode.childrenMap().keys) {
            val message = sectionNode.node(key.toString()).getString() ?: ""
            messages["$section.$key"] = message
        }
    }

    fun getMessage(key: String): String {
        return messages[key] ?: "Message not found: $key"
    }

    fun getFormattedMessage(key: String, vararg replacements: Pair<String, String>): String {
        var message = getMessage(key)

        // Apply replacements
        replacements.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value)
        }

        // Apply color codes using Adventure API
        return serializer.serialize(serializer.deserialize(message))
    }

    // Convenience methods for common messages
    fun noPermission(): String = getFormattedMessage("general.no-permission")
    fun playerNotFound(): String = getFormattedMessage("general.player-not-found")
    fun invalidCommand(): String = getFormattedMessage("general.invalid-command")
    fun commandDisabled(): String = getFormattedMessage("general.command-disabled")

    fun insufficientFunds(currency: String): String =
        getFormattedMessage("economy.insufficient-funds", "currency" to currency)
    fun transactionComplete(): String = getFormattedMessage("economy.transaction-complete")

    fun reload() {
        messages.clear()
        loadMessages()
    }
}