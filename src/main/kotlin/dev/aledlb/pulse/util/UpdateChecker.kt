package dev.aledlb.pulse.util

import dev.aledlb.pulse.Pulse
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UpdateChecker(private val plugin: Pulse) : Listener {
    @Volatile
    private var latestVersion: String? = null
    @Volatile
    private var updateDownloaded = false

    private val resourceId = 129191
    private val resourceUrl = "https://www.spigotmc.org/resources/$resourceId/"
    private val apiUrl = "https://api.spiget.org/v2/resources/$resourceId/versions/latest"
    private val downloadUrl = "https://api.spiget.org/v2/resources/$resourceId/download"

    private var scheduledTask: ScheduledTask? = null

    fun initialize() {
        val config = plugin.configManager.getConfig("config.yml") ?: return
        val enabled = config.node("update-checker", "enabled").getBoolean(true)

        if (!enabled) {
            Logger.debug("Update checker is disabled")
            return
        }

        // Register listener for join notifications
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Check once on startup
        performUpdateCheck()

        // Schedule periodic checks if configured
        val checkInterval = config.node("update-checker", "check-interval").getInt(6)
        if (checkInterval > 0) {
            val tickInterval = checkInterval * 60 * 60 * 20L // Convert hours to ticks
            scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { _ ->
                performUpdateCheck()
            }, tickInterval, tickInterval)
            Logger.debug("Scheduled update checks every $checkInterval hour(s)")
        }
    }

    fun shutdown() {
        scheduledTask?.cancel()
    }

    private fun performUpdateCheck() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val latest = fetchLatestVersion()
                latestVersion = latest
                val current = plugin.pluginMeta.version

                if (isNewerVersion(latest, current) && !updateDownloaded) {
                    Logger.warn("A new Pulse version is available: $latest (current: $current)")

                    val config = plugin.configManager.getConfig("config.yml")
                    val autoUpdate = config?.node("update-checker", "auto-update")?.getBoolean(false) ?: false
                    if (autoUpdate) {
                        Logger.info("Auto-update is enabled, downloading version $latest...")
                        downloadUpdate(latest)
                    } else {
                        Logger.warn("Download: $resourceUrl")
                    }
                } else if (!isNewerVersion(latest, current)) {
                    Logger.debug("Pulse is up to date (version $current)")
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

    private fun downloadUpdate(version: String) {
        try {
            // Create update directory if it doesn't exist
            val updateFolder = File(plugin.server.updateFolderFile.absolutePath)
            if (!updateFolder.exists()) {
                updateFolder.mkdirs()
            }

            // Download the new version
            Logger.info("Downloading Pulse v$version from Spigot...")
            val url = URI.create(downloadUrl).toURL()
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Pulse-UpdateChecker")
                instanceFollowRedirects = true
            }

            // Save to update folder
            val outputFile = File(updateFolder, "Pulse-$version.jar")
            connection.inputStream.use { input ->
                Files.copy(input, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            updateDownloaded = true
            Logger.success("Successfully downloaded Pulse v$version")
            Logger.success("The update will be applied on the next server restart")
        } catch (e: Exception) {
            Logger.error("Failed to download update: ${e.message}")
            Logger.warn("You can manually download from: $resourceUrl")
        }
    }

    private fun fetchLatestVersion(): String {
        val url = URI.create(apiUrl).toURL()
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