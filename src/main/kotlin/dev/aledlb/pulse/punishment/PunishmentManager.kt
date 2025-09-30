package dev.aledlb.pulse.punishment

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger

class PunishmentManager {
    private var enabled = true
    private var broadcastBans = true
    private var broadcastKicks = true
    private var broadcastMutes = false
    private var broadcastWarns = false
    private var autoEscalationEnabled = true
    private var warnLimit = 3
    private var muteDuration = 3600L // seconds
    private var banDuration = 86400L // seconds
    private var appealsEnabled = true
    private var appealsWebsite = "https://yourserver.com/appeals"

    fun initialize() {
        loadConfig()
    }

    private fun loadConfig() {
        val configManager = Pulse.getPlugin().configManager
        val punishmentConfig = configManager.getConfig("punishment.yml")

        if (punishmentConfig == null) {
            Logger.warn("Punishment configuration not found, using defaults")
            return
        }

        // Load main settings
        enabled = punishmentConfig.node("enabled").getBoolean(true)

        // Load broadcast settings
        val broadcastNode = punishmentConfig.node("broadcast")
        broadcastBans = broadcastNode.node("bans").getBoolean(true)
        broadcastKicks = broadcastNode.node("kicks").getBoolean(true)
        broadcastMutes = broadcastNode.node("mutes").getBoolean(false)
        broadcastWarns = broadcastNode.node("warns").getBoolean(false)

        // Load auto-escalation settings
        val escalationNode = punishmentConfig.node("auto-escalation")
        autoEscalationEnabled = escalationNode.node("enabled").getBoolean(true)
        warnLimit = escalationNode.node("warn-limit").getInt(3)
        muteDuration = escalationNode.node("mute-duration").getLong(3600)
        banDuration = escalationNode.node("ban-duration").getLong(86400)

        // Load appeals settings
        val appealsNode = punishmentConfig.node("appeals")
        appealsEnabled = appealsNode.node("enabled").getBoolean(true)
        appealsWebsite = appealsNode.node("website").getString("https://yourserver.com/appeals") ?: "https://yourserver.com/appeals"

        Logger.info("Punishment configuration loaded successfully")
    }

    // Getters for configuration values
    fun isEnabled(): Boolean = enabled
    fun shouldBroadcastBans(): Boolean = broadcastBans
    fun shouldBroadcastKicks(): Boolean = broadcastKicks
    fun shouldBroadcastMutes(): Boolean = broadcastMutes
    fun shouldBroadcastWarns(): Boolean = broadcastWarns
    fun isAutoEscalationEnabled(): Boolean = autoEscalationEnabled
    fun getWarnLimit(): Int = warnLimit
    fun getMuteDuration(): Long = muteDuration
    fun getBanDuration(): Long = banDuration
    fun areAppealsEnabled(): Boolean = appealsEnabled
    fun getAppealsWebsite(): String = appealsWebsite

    fun reload() {
        loadConfig()
    }
}