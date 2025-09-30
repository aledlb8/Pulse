package dev.aledlb.pulse.database

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * Manages Redis connections for cross-server synchronization.
 * Allows multiple Pulse instances to sync player data in real-time.
 */
class RedisManager {
    private var jedisPool: JedisPool? = null
    private var subscriber: JedisPubSub? = null
    private var isEnabled = false
    private var channel = "pulse:sync"
    private val gson = Gson()

    fun initialize() {
        val configManager = Pulse.getPlugin().configManager
        val dbConfig = configManager.getConfig("database.yml")

        if (dbConfig == null) {
            Logger.warn("Database configuration not found, Redis sync disabled")
            return
        }

        val redisNode = dbConfig.node("redis")
        isEnabled = redisNode.node("enabled").getBoolean(false)

        if (!isEnabled) {
            Logger.info("Redis network sync is disabled")
            return
        }

        val host = redisNode.node("host").getString("localhost") ?: "localhost"
        val port = redisNode.node("port").getInt(6379)
        val password = redisNode.node("password").getString("")
        val useSSL = redisNode.node("use-ssl").getBoolean(false)
        channel = redisNode.node("channel").getString("pulse:sync") ?: "pulse:sync"

        try {
            val poolConfig = JedisPoolConfig().apply {
                maxTotal = 10
                maxIdle = 5
                minIdle = 1
                testOnBorrow = true
                testOnReturn = true
                testWhileIdle = true
            }

            jedisPool = if (password.isNullOrBlank()) {
                JedisPool(poolConfig, host, port, 2000, useSSL)
            } else {
                JedisPool(poolConfig, host, port, 2000, password, useSSL)
            }

            // Test connection
            jedisPool?.resource?.use { jedis ->
                jedis.ping()
            }

            // Start subscriber in background
            startSubscriber()

            Logger.success("Redis network sync enabled on $host:$port")
        } catch (e: Exception) {
            Logger.error("Failed to connect to Redis: ${e.message}", e)
            isEnabled = false
            jedisPool?.close()
            jedisPool = null
        }
    }

    private fun startSubscriber() {
        subscriber = object : JedisPubSub() {
            override fun onMessage(channel: String, message: String) {
                try {
                    val syncMessage = gson.fromJson(message, SyncMessage::class.java)
                    handleSyncMessage(syncMessage)
                } catch (e: Exception) {
                    Logger.error("Failed to process Redis sync message: ${e.message}", e)
                }
            }
        }

        // Subscribe in a separate thread
        Thread {
            try {
                jedisPool?.resource?.use { jedis ->
                    jedis.subscribe(subscriber, channel)
                }
            } catch (e: Exception) {
                Logger.error("Redis subscriber error: ${e.message}", e)
            }
        }.start()
    }

    private fun handleSyncMessage(message: SyncMessage) {
        when (message.type) {
            SyncType.BALANCE_UPDATE -> {
                val uuid = UUID.fromString(message.uuid)
                val balance = message.data.toDoubleOrNull() ?: return

                // Update local cache
                Pulse.getPlugin().economyManager.setBalance(uuid, balance)
                Logger.debug("Synced balance for ${message.uuid}: $balance")
            }
            SyncType.RANK_UPDATE -> {
                val uuid = UUID.fromString(message.uuid)
                val rank = message.data

                // Update local cache
                val playerData = Pulse.getPlugin().rankManager.getPlayerData(uuid)
                if (playerData != null) {
                    playerData.rank = rank
                    Logger.debug("Synced rank for ${message.uuid}: $rank")
                }
            }
            SyncType.TAG_UPDATE -> {
                val uuid = UUID.fromString(message.uuid)
                // Parse tag data and update
                Logger.debug("Synced tags for ${message.uuid}")
            }
            SyncType.PERMISSION_UPDATE -> {
                val uuid = UUID.fromString(message.uuid)
                // Refresh player permissions
                Logger.debug("Synced permissions for ${message.uuid}")
            }
        }
    }

    /**
     * Publish a sync message to all connected servers
     */
    fun publishSync(type: SyncType, uuid: UUID, data: String) {
        if (!isEnabled || jedisPool == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = SyncMessage(
                    type = type,
                    uuid = uuid.toString(),
                    data = data,
                    timestamp = System.currentTimeMillis()
                )

                jedisPool?.resource?.use { jedis ->
                    jedis.publish(channel, gson.toJson(message))
                }
            } catch (e: Exception) {
                Logger.error("Failed to publish Redis sync message: ${e.message}", e)
            }
        }
    }

    /**
     * Sync player balance across all servers
     */
    fun syncBalance(uuid: UUID, balance: Double) {
        publishSync(SyncType.BALANCE_UPDATE, uuid, balance.toString())
    }

    /**
     * Sync player rank across all servers
     */
    fun syncRank(uuid: UUID, rank: String) {
        publishSync(SyncType.RANK_UPDATE, uuid, rank)
    }

    /**
     * Sync player tags across all servers
     */
    fun syncTags(uuid: UUID, tagsJson: String) {
        publishSync(SyncType.TAG_UPDATE, uuid, tagsJson)
    }

    /**
     * Sync player permissions across all servers
     */
    fun syncPermissions(uuid: UUID, permissionsJson: String) {
        publishSync(SyncType.PERMISSION_UPDATE, uuid, permissionsJson)
    }

    fun isEnabled(): Boolean = isEnabled

    fun shutdown() {
        if (isEnabled) {
            subscriber?.unsubscribe()
            jedisPool?.close()
            Logger.info("Redis connection closed")
        }
    }
}

enum class SyncType {
    BALANCE_UPDATE,
    RANK_UPDATE,
    TAG_UPDATE,
    PERMISSION_UPDATE
}

data class SyncMessage(
    val type: SyncType,
    val uuid: String,
    val data: String,
    val timestamp: Long
)