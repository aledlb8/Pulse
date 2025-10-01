package dev.aledlb.pulse.util

import dev.aledlb.pulse.Pulse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val plugin: Pulse) : Listener {
    @Volatile
    private var latestVersion: String? = null

    private val resourceId = 129191
    private val resourceUrl = "https://www.spigotmc.org/resources/$resourceId/"
    private val apiUrl = "https://api.spiget.org/v2/resources/$resourceId/versions/latest"

    fun initialize() {
        // Register listener for join notifications
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Check once on startup asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latest = fetchLatestVersion()
                latestVersion = latest
                val current = plugin.pluginMeta.version
                if (isNewerVersion(latest, current)) {
                    Logger.warn("A new Pulse version is available: $latest (current: $current)")
                    Logger.warn("Download: $resourceUrl")
                } else {
                    Logger.info("Pulse is up to date (version $current)")
                }
            } catch (e: Exception) {
                Logger.debug("Update check failed: ${e.message}")
            }
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val latest = latestVersion ?: return
        val current = plugin.pluginMeta.version
        if (player.hasPermission("pulse.update.notify") && isNewerVersion(latest, current)) {
            player.sendMessage("§6[Pulse] §eA new version is available: §a$latest§e (current: §c$current§e)")
            player.sendMessage("§6[Pulse] §eDownload: §b$resourceUrl")
        }
    }

    private fun fetchLatestVersion(): String {
        val url = URL(apiUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "Pulse-UpdateChecker")
        }

        connection.inputStream.use { input ->
            val text = BufferedReader(InputStreamReader(input)).readText()
            // Spiget latest version returns JSON with a "name" like "1.2.3"
            // Extract the value of "name" without adding a JSON dependency
            val regex = Regex("""\"name\"\s*:\s*\"([^\"]+)\"""")
            val match = regex.find(text) ?: error("Could not parse latest version from API response")
            return match.groupValues[1]
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        // Basic semantic-ish comparison: split by non-digits and compare integers
        val latestParts = latest.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }

        val max = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until max) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }
}