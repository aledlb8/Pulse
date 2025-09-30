package dev.aledlb.pulse.util

import java.util.logging.Level
import java.util.logging.Logger as JavaLogger

object Logger {
    private var pluginLogger: JavaLogger? = null

    fun initialize(logger: JavaLogger) {
        pluginLogger = logger
    }

    private fun getLogger(): JavaLogger {
        return pluginLogger ?: JavaLogger.getLogger("Pulse")
    }

    fun info(message: String) {
        getLogger().info(message)
    }

    fun warn(message: String) {
        getLogger().warning(message)
    }

    fun error(message: String) {
        getLogger().severe(message)
    }

    fun error(message: String, throwable: Throwable) {
        getLogger().log(Level.SEVERE, message, throwable)
    }

    fun debug(message: String) {
        if (isDebugEnabled()) {
            getLogger().info("[DEBUG] $message")
        }
    }

    fun success(message: String) {
        getLogger().info(message)
    }

    private fun isDebugEnabled(): Boolean {
        return System.getProperty("pulse.debug", "false").toBoolean()
    }

    fun logStartup(version: String) {
        info("")
        info("╔═══════════════════════════════════╗")
        info("║           PULSE CORE            ║")
        info("║         Version $version         ║")
        info("║     Professional Server Core     ║")
        info("╚═══════════════════════════════════╝")
        info("")
    }

    fun logShutdown() {
        info("")
        info("╔═══════════════════════════════════╗")
        info("║        PULSE SHUTDOWN         ║")
        info("║      Thanks for using Pulse!      ║")
        info("╚═══════════════════════════════════╝")
        info("")
    }
}