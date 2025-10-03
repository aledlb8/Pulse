package dev.aledlb.pulse.playtime

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.database.DatabaseManager
import dev.aledlb.pulse.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages player playtime tracking
 */
class PlaytimeManager(private val databaseManager: DatabaseManager) : Listener {
    private val sessionStartTimes = ConcurrentHashMap<UUID, Long>()
    private val playtimeCache = ConcurrentHashMap<UUID, Long>()
    private var autoSaveTaskBukkit: BukkitTask? = null
    private var autoSaveTaskAsyncScheduler: Any? = null

    fun initialize() {
        // Load all playtime data from database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allPlaytimes = databaseManager.loadAllPlaytime()
                playtimeCache.putAll(allPlaytimes)
                Logger.info("Loaded playtime data for ${allPlaytimes.size} players")
            } catch (e: Exception) {
                Logger.error("Failed to load playtime data: ${e.message}", e)
            }
        }

        // Start auto-save task (every 5 minutes)
        scheduleAutoSave()

        Logger.success("PlaytimeManager initialized")
    }

    private fun scheduleAutoSave() {
        try {
            val asyncScheduler = Bukkit.getAsyncScheduler()
            autoSaveTaskAsyncScheduler = asyncScheduler.runAtFixedRate(
                Pulse.getPlugin(),
                { _ -> saveAllOnlinePlayers() },
                5L,
                5L,
                TimeUnit.MINUTES
            )
            Logger.debug("Playtime autosave scheduled via AsyncScheduler")
            return
        } catch (e: Throwable) {
            Logger.debug("AsyncScheduler not available (${e.message}), trying Bukkit scheduler")
        }

        // Fallback to Bukkit scheduler for older Paper versions or Spigot
        try {
            autoSaveTaskBukkit = Bukkit.getScheduler().runTaskTimerAsynchronously(
                Pulse.getPlugin(),
                Runnable { saveAllOnlinePlayers() },
                20L * 60 * 5, // 5 minutes initial delay
                20L * 60 * 5  // 5 minutes interval
            )
            Logger.debug("Playtime autosave scheduled via Bukkit async scheduler (non-Folia)")
        } catch (e: Exception) {
            Logger.error("Failed to schedule playtime autosave: ${e.message}", e)
            Logger.error("If you are using Folia, please ensure you are running Paper 1.20.5+ with AsyncScheduler support")
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // Start tracking session
        sessionStartTimes[uuid] = System.currentTimeMillis()

        // Load playtime from database if not in cache
        if (!playtimeCache.containsKey(uuid)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val playtime = databaseManager.loadPlaytime(uuid) ?: 0L
                    playtimeCache[uuid] = playtime
                } catch (e: Exception) {
                    Logger.error("Failed to load playtime for ${player.name}: ${e.message}", e)
                    playtimeCache[uuid] = 0L
                }
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // Calculate session time and add to total
        val sessionStart = sessionStartTimes.remove(uuid)
        if (sessionStart != null) {
            val sessionTime = System.currentTimeMillis() - sessionStart
            val currentPlaytime = playtimeCache.getOrDefault(uuid, 0L)
            val newPlaytime = currentPlaytime + sessionTime
            playtimeCache[uuid] = newPlaytime

            // Save to database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    databaseManager.savePlaytime(uuid, newPlaytime)
                } catch (e: Exception) {
                    Logger.error("Failed to save playtime for ${player.name}: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Get total playtime for a player in milliseconds
     */
    fun getPlaytime(uuid: UUID): Long {
        val basePlaytime = playtimeCache.getOrDefault(uuid, 0L)

        // Add current session time if player is online
        val sessionStart = sessionStartTimes[uuid]
        return if (sessionStart != null) {
            basePlaytime + (System.currentTimeMillis() - sessionStart)
        } else {
            basePlaytime
        }
    }

    /**
     * Get playtime for a player object
     */
    fun getPlaytime(player: Player): Long {
        return getPlaytime(player.uniqueId)
    }

    /**
     * Set playtime for a player in milliseconds
     */
    fun setPlaytime(uuid: UUID, playtime: Long) {
        playtimeCache[uuid] = playtime

        // Save to database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                databaseManager.savePlaytime(uuid, playtime)
            } catch (e: Exception) {
                Logger.error("Failed to save playtime: ${e.message}", e)
            }
        }
    }

    /**
     * Add playtime to a player in milliseconds
     */
    fun addPlaytime(uuid: UUID, amount: Long) {
        val currentPlaytime = getPlaytime(uuid)
        setPlaytime(uuid, currentPlaytime + amount)
    }

    /**
     * Reset playtime for a player
     */
    fun resetPlaytime(uuid: UUID) {
        setPlaytime(uuid, 0L)
    }

    /**
     * Format playtime in milliseconds to a readable string
     */
    fun formatPlaytime(playtimeMs: Long): String {
        val seconds = playtimeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Get formatted playtime for a player
     */
    fun getFormattedPlaytime(uuid: UUID): String {
        return formatPlaytime(getPlaytime(uuid))
    }

    /**
     * Get playtime in hours
     */
    fun getPlaytimeHours(uuid: UUID): Double {
        return getPlaytime(uuid) / (1000.0 * 60.0 * 60.0)
    }

    /**
     * Get playtime in minutes
     */
    fun getPlaytimeMinutes(uuid: UUID): Long {
        return getPlaytime(uuid) / (1000 * 60)
    }

    /**
     * Get playtime in seconds
     */
    fun getPlaytimeSeconds(uuid: UUID): Long {
        return getPlaytime(uuid) / 1000
    }

    /**
     * Save playtime for all online players
     */
    fun saveAllOnlinePlayers() {
        val onlinePlayers = Pulse.getPlugin().server.onlinePlayers

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (player in onlinePlayers) {
                    val uuid = player.uniqueId
                    val playtime = getPlaytime(uuid)
                    databaseManager.savePlaytime(uuid, playtime)
                }
                Logger.debug("Auto-saved playtime for ${onlinePlayers.size} online players")
            } catch (e: Exception) {
                Logger.error("Failed to auto-save playtime: ${e.message}", e)
            }
        }
    }

    /**
     * Save all playtime data
     */
    fun saveAllData() {
        // Update cache with current session times
        for ((uuid, sessionStart) in sessionStartTimes) {
            val sessionTime = System.currentTimeMillis() - sessionStart
            val currentPlaytime = playtimeCache.getOrDefault(uuid, 0L)
            playtimeCache[uuid] = currentPlaytime + sessionTime
        }

        // Save all to database synchronously on shutdown
        runBlocking(Dispatchers.IO) {
            try {
                for ((uuid, playtime) in playtimeCache) {
                    databaseManager.savePlaytime(uuid, playtime)
                }
                Logger.info("Saved playtime data for ${playtimeCache.size} players")
            } catch (e: Exception) {
                Logger.error("Failed to save all playtime data: ${e.message}", e)
            }
        }
    }

    /**
     * Gracefully stop background tasks and persist data before DB shutdown
     */
    fun shutdown() {
        try {
            autoSaveTaskBukkit?.cancel()
        } catch (_: Exception) { }
        try {
            val task = autoSaveTaskAsyncScheduler
            if (task != null) {
                val cancelMethod = task.javaClass.getMethod("cancel")
                cancelMethod.invoke(task)
            }
        } catch (_: Exception) { }
        saveAllData()
    }

    /**
     * Get top players by playtime
     */
    suspend fun getTopPlayers(limit: Int = 10): List<Pair<UUID, Long>> {
        return playtimeCache.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
}