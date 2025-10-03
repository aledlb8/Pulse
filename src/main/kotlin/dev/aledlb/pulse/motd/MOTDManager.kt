package dev.aledlb.pulse.motd

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.util.CachedServerIcon
import java.io.File
import javax.imageio.ImageIO

class MOTDManager(private val plugin: Pulse) {
    private var config: YamlConfiguration? = null
    private val configFile = File(plugin.dataFolder, "motd.yml")
    private val miniMessage = MiniMessage.miniMessage()

    private var normalIcon: CachedServerIcon? = null
    private var maintenanceIcon: CachedServerIcon? = null

    var isMaintenanceMode = false
        private set

    fun initialize() {
        // Create config if it doesn't exist
        if (!configFile.exists()) {
            plugin.saveResource("motd.yml", false)
        }

        reload()
        Logger.success("MOTD system initialized")
    }

    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
        isMaintenanceMode = config?.getBoolean("maintenance.enabled", false) ?: false

        // Load icons asynchronously to avoid blocking
        org.bukkit.Bukkit.getAsyncScheduler().runNow(plugin) { _ ->
            loadIcons()
        }

        Logger.success("MOTD configuration reloaded")
    }

    private fun loadIcons() {
        // Read image files (blocking I/O, safe on async thread)
        val normalImage = if (config?.getBoolean("icon.enabled") == true) {
            val iconPath = config?.getString("icon.path") ?: "server-icon.png"
            val iconFile = File(plugin.server.worldContainer, iconPath)
            if (iconFile.exists()) {
                try {
                    ImageIO.read(iconFile)
                } catch (e: Exception) {
                    Logger.error("Failed to read normal icon: ${e.message}")
                    null
                }
            } else null
        } else null

        val maintenanceImage = config?.getString("maintenance.icon")?.let { iconPath ->
            val iconFile = File(plugin.server.worldContainer, iconPath)
            if (iconFile.exists()) {
                try {
                    ImageIO.read(iconFile)
                } catch (e: Exception) {
                    Logger.error("Failed to read maintenance icon: ${e.message}")
                    null
                }
            } else null
        }

        // Convert images to CachedServerIcon on global region scheduler (Folia-safe)
        org.bukkit.Bukkit.getGlobalRegionScheduler().execute(plugin) {
            normalIcon = normalImage?.let {
                try {
                    plugin.server.loadServerIcon(it).also {
                        Logger.debug("Loaded normal server icon")
                    }
                } catch (e: Exception) {
                    Logger.error("Failed to load normal icon: ${e.message}")
                    null
                }
            }

            maintenanceIcon = maintenanceImage?.let {
                try {
                    plugin.server.loadServerIcon(it).also {
                        Logger.debug("Loaded maintenance server icon")
                    }
                } catch (e: Exception) {
                    Logger.error("Failed to load maintenance icon: ${e.message}")
                    null
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        return config?.getBoolean("enabled", true) ?: true
    }

    fun getMaintenanceBypassPermission(): String {
        return config?.getString("maintenance.bypass-permission") ?: "pulse.maintenance.bypass"
    }

    fun getMaintenanceKickMessage(): Component {
        val message = config?.getString("maintenance.kick-message")
            ?: "<red><bold>SERVER MAINTENANCE</bold></red>\n<gray>We're currently performing maintenance."
        return miniMessage.deserialize(message)
    }

    fun getMOTDLine1(): Component {
        val path = if (isMaintenanceMode) "maintenance.motd.line1" else "motd.line1"
        val line = config?.getString(path) ?: "<gray>A Minecraft Server</gray>"
        return miniMessage.deserialize(line)
    }

    fun getMOTDLine2(): Component {
        val path = if (isMaintenanceMode) "maintenance.motd.line2" else "motd.line2"
        val line = config?.getString(path) ?: ""
        return miniMessage.deserialize(line)
    }

    fun getMaxPlayers(): Int? {
        if (config?.getBoolean("max-players.enabled") == true) {
            return config?.getInt("max-players.amount")
        }
        return null
    }

    fun getFakePlayerCount(): Int {
        if (config?.getBoolean("fake-players.enabled") == true) {
            return config?.getInt("fake-players.amount") ?: 0
        }
        return 0
    }

    fun getServerIcon(): CachedServerIcon? {
        return if (isMaintenanceMode) {
            maintenanceIcon
        } else {
            normalIcon
        }
    }

    fun setMaintenanceMode(enabled: Boolean) {
        isMaintenanceMode = enabled
        config?.set("maintenance.enabled", enabled)

        try {
            config?.save(configFile)
            Logger.info("Maintenance mode ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Logger.error("Failed to save maintenance mode: ${e.message}")
        }
    }

    fun toggleMaintenanceMode(): Boolean {
        setMaintenanceMode(!isMaintenanceMode)
        return isMaintenanceMode
    }
}
