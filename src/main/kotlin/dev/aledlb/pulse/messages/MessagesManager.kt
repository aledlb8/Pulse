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

        loadAllMessages(messagesConfig)

        Logger.info("Loaded ${messages.size} messages from configuration")
    }

    private fun loadAllMessages(node: org.spongepowered.configurate.ConfigurationNode, prefix: String = "") {
        val children = node.childrenMap()
        if (children.isEmpty()) {
            val value = node.getString()
            if (value != null && prefix.isNotEmpty()) {
                messages[prefix] = value
            }
            return
        }

        for ((key, child) in children) {
            val keyStr = key.toString()
            val fullKey = if (prefix.isEmpty()) keyStr else "$prefix.$keyStr"
            // Recurse to support arbitrarily nested sections; leaf string nodes get added
            loadAllMessages(child, fullKey)
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