package dev.aledlb.pulse.database

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import dev.aledlb.pulse.util.AsyncHelper
import dev.aledlb.pulse.util.SchedulerHelper
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import com.google.gson.Gson
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
        try {
            val uuid = UUID.fromString(message.uuid)
            val plugin = Pulse.getPlugin()

            when (message.type) {
                SyncType.BALANCE_UPDATE -> {
                    val balance = message.data.toDoubleOrNull() ?: return
                    plugin.economyManager.setBalance(uuid, balance)
                    Logger.debug("Synced balance for ${message.uuid}: $balance")
                }
                
                SyncType.RANK_UPDATE -> {
                    // Data format: "rankName:expiration" or "rankName"
                    val parts = message.data.split(":")
                    val rankName = parts[0]
                    val expiration = parts.getOrNull(1)?.toLongOrNull()
                    
                    plugin.rankManager.addPlayerRank(uuid, rankName, expiration)
                    
                    // Update permissions for online player
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        plugin.permissionManager.updatePlayerPermissions(player)
                        plugin.chatManager.updatePlayerFormats(player)
                    }
                    Logger.debug("Synced rank add for ${message.uuid}: $rankName")
                }
                
                SyncType.RANK_REMOVE -> {
                    val rankName = message.data
                    plugin.rankManager.removePlayerRank(uuid, rankName)
                    
                    // Update permissions for online player
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        plugin.permissionManager.updatePlayerPermissions(player)
                        plugin.chatManager.updatePlayerFormats(player)
                    }
                    Logger.debug("Synced rank remove for ${message.uuid}: $rankName")
                }
                
                SyncType.TAG_UPDATE -> {
                    // Data format: "tagId:action" where action is "give" or "remove"
                    val parts = message.data.split(":")
                    val tagId = parts[0]
                    val action = parts.getOrNull(1) ?: "give"
                    
                    val playerData = plugin.tagManager.getPlayerTagData(uuid)
                    if (playerData != null) {
                        if (action == "give") {
                            playerData.addTag(tagId)
                        } else {
                            playerData.removeTag(tagId)
                        }
                    }
                    Logger.debug("Synced tag $action for ${message.uuid}: $tagId")
                }
                
                SyncType.TAG_ACTIVATE -> {
                    val tagId = message.data
                    val playerData = plugin.tagManager.getPlayerTagData(uuid)
                    playerData?.activateTag(tagId)
                    
                    // Update formatting for online player
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        plugin.chatManager.updatePlayerFormats(player)
                    }
                    Logger.debug("Synced tag activate for ${message.uuid}: $tagId")
                }
                
                SyncType.TAG_DEACTIVATE -> {
                    val tagId = message.data
                    val playerData = plugin.tagManager.getPlayerTagData(uuid)
                    playerData?.deactivateTag(tagId)
                    
                    // Update formatting for online player
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        plugin.chatManager.updatePlayerFormats(player)
                    }
                    Logger.debug("Synced tag deactivate for ${message.uuid}: $tagId")
                }
                
                SyncType.PERMISSION_ADD -> {
                    val permission = message.data
                    plugin.rankManager.addPlayerPermission(uuid, permission)
                    
                    // Update permissions for online player
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        plugin.permissionManager.updatePlayerPermissions(player)
                    }
                    Logger.debug("Synced permission add for ${message.uuid}: $permission")
                }
                
                SyncType.PERMISSION_REMOVE -> {
                    val permission = message.data
                    plugin.rankManager.removePlayerPermission(uuid, permission)
                    
                    // Update permissions for online player
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        plugin.permissionManager.updatePlayerPermissions(player)
                    }
                    Logger.debug("Synced permission remove for ${message.uuid}: $permission")
                }
                
                SyncType.PERMISSION_DENY -> {
                    val permission = message.data
                    plugin.rankManager.denyPlayerPermission(uuid, permission)
                    
                    // Update permissions for online player
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        plugin.permissionManager.updatePlayerPermissions(player)
                    }
                    Logger.debug("Synced permission deny for ${message.uuid}: $permission")
                }
                
                SyncType.PUNISHMENT_BAN -> {
                    // Data format: "punishmentId"
                    // The ban is already in the database, just need to kick if online
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        val kickMsg = plugin.messagesManager.getMessage("punishment.ban-screen")
                        SchedulerHelper.kickPlayer(player, kickMsg)
                    }
                    Logger.debug("Synced ban for ${message.uuid}")
                }
                
                SyncType.PUNISHMENT_UNBAN -> {
                    // Ban removed from database, no action needed for offline players
                    Logger.debug("Synced unban for ${message.uuid}")
                }
                
                SyncType.PUNISHMENT_MUTE -> {
                    // Data format: "punishmentId"
                    // The mute is already in the database
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        val muteMsg = plugin.messagesManager.getMessage("punishment.mute-screen")
                        SchedulerHelper.sendMessage(player, muteMsg)
                    }
                    Logger.debug("Synced mute for ${message.uuid}")
                }
                
                SyncType.PUNISHMENT_UNMUTE -> {
                    // Mute removed from database
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        val unmuteMsg = plugin.messagesManager.getMessage("punishment.unmute-screen")
                        SchedulerHelper.sendMessage(player, unmuteMsg)
                    }
                    Logger.debug("Synced unmute for ${message.uuid}")
                }
                
                SyncType.PUNISHMENT_WARN -> {
                    // Warn added to database
                    val player = plugin.server.getPlayer(uuid)
                    if (player != null && player.isOnline) {
                        val warnMsg = plugin.messagesManager.getMessage("punishment.warn-screen")
                        SchedulerHelper.sendMessage(player, warnMsg)
                    }
                    Logger.debug("Synced warn for ${message.uuid}")
                }
                
                SyncType.PUNISHMENT_FREEZE -> {
                    plugin.punishmentManager.service.freeze(uuid)
                    Logger.debug("Synced freeze for ${message.uuid}")
                }
                
                SyncType.PUNISHMENT_UNFREEZE -> {
                    plugin.punishmentManager.service.unfreeze(uuid)
                    Logger.debug("Synced unfreeze for ${message.uuid}")
                }
                
                SyncType.PLAYTIME_UPDATE -> {
                    val playtime = message.data.toLongOrNull() ?: return
                    plugin.playtimeManager.setPlaytime(uuid, playtime)
                    Logger.debug("Synced playtime for ${message.uuid}: $playtime")
                }
            }
        } catch (e: Exception) {
            Logger.error("Error handling sync message: ${e.message}", e)
        }
    }

    /**
     * Publish a sync message to all connected servers
     */
    fun publishSync(type: SyncType, uuid: UUID, data: String) {
        if (!isEnabled || jedisPool == null) return

        AsyncHelper.executeAsync(
            operation = {
                val message = SyncMessage(
                    type = type,
                    uuid = uuid.toString(),
                    data = data,
                    timestamp = System.currentTimeMillis()
                )

                jedisPool?.resource?.use { jedis ->
                    jedis.publish(channel, gson.toJson(message))
                }
            },
            errorMessage = "Failed to publish Redis sync message"
        )
    }

    /**
     * Sync player balance across all servers
     */
    fun syncBalance(uuid: UUID, balance: Double) {
        publishSync(SyncType.BALANCE_UPDATE, uuid, balance.toString())
    }

    /**
     * Sync player rank add across all servers
     */
    fun syncRankAdd(uuid: UUID, rankName: String, expiration: Long? = null) {
        val data = if (expiration != null) "$rankName:$expiration" else rankName
        publishSync(SyncType.RANK_UPDATE, uuid, data)
    }

    /**
     * Sync player rank remove across all servers
     */
    fun syncRankRemove(uuid: UUID, rankName: String) {
        publishSync(SyncType.RANK_REMOVE, uuid, rankName)
    }

    /**
     * Sync tag give/remove across all servers
     */
    fun syncTagUpdate(uuid: UUID, tagId: String, action: String) {
        publishSync(SyncType.TAG_UPDATE, uuid, "$tagId:$action")
    }

    /**
     * Sync tag activation across all servers
     */
    fun syncTagActivate(uuid: UUID, tagId: String) {
        publishSync(SyncType.TAG_ACTIVATE, uuid, tagId)
    }

    /**
     * Sync tag deactivation across all servers
     */
    fun syncTagDeactivate(uuid: UUID, tagId: String) {
        publishSync(SyncType.TAG_DEACTIVATE, uuid, tagId)
    }

    /**
     * Sync permission add across all servers
     */
    fun syncPermissionAdd(uuid: UUID, permission: String) {
        publishSync(SyncType.PERMISSION_ADD, uuid, permission)
    }

    /**
     * Sync permission remove across all servers
     */
    fun syncPermissionRemove(uuid: UUID, permission: String) {
        publishSync(SyncType.PERMISSION_REMOVE, uuid, permission)
    }

    /**
     * Sync permission deny across all servers
     */
    fun syncPermissionDeny(uuid: UUID, permission: String) {
        publishSync(SyncType.PERMISSION_DENY, uuid, permission)
    }

    /**
     * Sync ban across all servers
     */
    fun syncBan(uuid: UUID, punishmentId: String) {
        publishSync(SyncType.PUNISHMENT_BAN, uuid, punishmentId)
    }

    /**
     * Sync unban across all servers
     */
    fun syncUnban(uuid: UUID) {
        publishSync(SyncType.PUNISHMENT_UNBAN, uuid, "")
    }

    /**
     * Sync mute across all servers
     */
    fun syncMute(uuid: UUID, punishmentId: String) {
        publishSync(SyncType.PUNISHMENT_MUTE, uuid, punishmentId)
    }

    /**
     * Sync unmute across all servers
     */
    fun syncUnmute(uuid: UUID) {
        publishSync(SyncType.PUNISHMENT_UNMUTE, uuid, "")
    }

    /**
     * Sync warn across all servers
     */
    fun syncWarn(uuid: UUID, punishmentId: String) {
        publishSync(SyncType.PUNISHMENT_WARN, uuid, punishmentId)
    }

    /**
     * Sync freeze across all servers
     */
    fun syncFreeze(uuid: UUID) {
        publishSync(SyncType.PUNISHMENT_FREEZE, uuid, "")
    }

    /**
     * Sync unfreeze across all servers
     */
    fun syncUnfreeze(uuid: UUID) {
        publishSync(SyncType.PUNISHMENT_UNFREEZE, uuid, "")
    }

    /**
     * Sync playtime across all servers
     */
    fun syncPlaytime(uuid: UUID, playtime: Long) {
        publishSync(SyncType.PLAYTIME_UPDATE, uuid, playtime.toString())
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
    RANK_REMOVE,
    TAG_UPDATE,
    TAG_ACTIVATE,
    TAG_DEACTIVATE,
    PERMISSION_ADD,
    PERMISSION_REMOVE,
    PERMISSION_DENY,
    PUNISHMENT_BAN,
    PUNISHMENT_UNBAN,
    PUNISHMENT_MUTE,
    PUNISHMENT_UNMUTE,
    PUNISHMENT_WARN,
    PUNISHMENT_FREEZE,
    PUNISHMENT_UNFREEZE,
    PLAYTIME_UPDATE
}

data class SyncMessage(
    val type: SyncType,
    val uuid: String,
    val data: String,
    val timestamp: Long
)