package dev.aledlb.pulse.messages

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

class MessagesManager {
    private var prefix = "§d[§5Pulse§d]§r "
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

        // Load prefix
        prefix = messagesConfig.node("prefix").getString("&d[&5Pulse&d]&r ") ?: "&d[&5Pulse&d]&r "

        // Load general messages
        loadMessageSection("general", messagesConfig)
        loadMessageSection("punishment", messagesConfig)
        loadMessageSection("economy", messagesConfig)
        loadMessageSection("coin", messagesConfig)
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

    fun getPrefix(): String {
        return serializer.serialize(serializer.deserialize(prefix))
    }

    fun getFormattedMessageWithPrefix(key: String, vararg replacements: Pair<String, String>): String {
        return getPrefix() + getFormattedMessage(key, *replacements)
    }

    // Convenience methods for common messages
    fun noPermission(): String = getFormattedMessageWithPrefix("general.no-permission")
    fun playerNotFound(): String = getFormattedMessageWithPrefix("general.player-not-found")
    fun invalidCommand(): String = getFormattedMessageWithPrefix("general.invalid-command")
    fun commandDisabled(): String = getFormattedMessageWithPrefix("general.command-disabled")

    fun insufficientFunds(currency: String): String =
        getFormattedMessageWithPrefix("economy.insufficient-funds", "currency" to currency)
    fun transactionComplete(): String = getFormattedMessageWithPrefix("economy.transaction-complete")

    fun reload() {
        messages.clear()
        loadMessages()
    }
}