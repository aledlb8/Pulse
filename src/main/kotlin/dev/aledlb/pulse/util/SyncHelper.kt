package dev.aledlb.pulse.util

import dev.aledlb.pulse.Pulse
import java.util.*

/**
 * Helper object for Redis synchronization operations.
 * Provides a centralized way to sync data across servers.
 */
object SyncHelper {
    
    /**
     * Sync a rank addition to all servers
     */
    fun syncRankAdd(uuid: UUID, rankName: String, expiration: Long? = null) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncRankAdd(uuid, rankName, expiration)
        }
    }
    
    /**
     * Sync a rank removal to all servers
     */
    fun syncRankRemove(uuid: UUID, rankName: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncRankRemove(uuid, rankName)
        }
    }
    
    /**
     * Sync a permission addition to all servers
     */
    fun syncPermissionAdd(uuid: UUID, permission: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncPermissionAdd(uuid, permission)
        }
    }
    
    /**
     * Sync a permission removal to all servers
     */
    fun syncPermissionRemove(uuid: UUID, permission: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncPermissionRemove(uuid, permission)
        }
    }
    
    /**
     * Sync a permission denial to all servers
     */
    fun syncPermissionDeny(uuid: UUID, permission: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncPermissionDeny(uuid, permission)
        }
    }
    
    /**
     * Sync a tag update to all servers
     */
    fun syncTagUpdate(uuid: UUID, tagId: String, action: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncTagUpdate(uuid, tagId, action)
        }
    }
    
    /**
     * Sync a tag activation to all servers
     */
    fun syncTagActivate(uuid: UUID, tagId: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncTagActivate(uuid, tagId)
        }
    }
    
    /**
     * Sync a tag deactivation to all servers
     */
    fun syncTagDeactivate(uuid: UUID, tagId: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncTagDeactivate(uuid, tagId)
        }
    }
    
    /**
     * Sync a balance update to all servers
     */
    fun syncBalance(uuid: UUID, balance: Double) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncBalance(uuid, balance)
        }
    }
    
    /**
     * Sync a ban to all servers
     */
    fun syncBan(uuid: UUID, punishmentId: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncBan(uuid, punishmentId)
        }
    }
    
    /**
     * Sync an unban to all servers
     */
    fun syncUnban(uuid: UUID) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncUnban(uuid)
        }
    }
    
    /**
     * Sync a mute to all servers
     */
    fun syncMute(uuid: UUID, punishmentId: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncMute(uuid, punishmentId)
        }
    }
    
    /**
     * Sync an unmute to all servers
     */
    fun syncUnmute(uuid: UUID) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncUnmute(uuid)
        }
    }
    
    /**
     * Sync a warn to all servers
     */
    fun syncWarn(uuid: UUID, punishmentId: String) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncWarn(uuid, punishmentId)
        }
    }
    
    /**
     * Sync a freeze to all servers
     */
    fun syncFreeze(uuid: UUID) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncFreeze(uuid)
        }
    }
    
    /**
     * Sync an unfreeze to all servers
     */
    fun syncUnfreeze(uuid: UUID) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncUnfreeze(uuid)
        }
    }
    
    /**
     * Sync playtime to all servers
     */
    fun syncPlaytime(uuid: UUID, playtime: Long) {
        val redisManager = Pulse.getPlugin().redisManager
        if (redisManager.isEnabled()) {
            redisManager.syncPlaytime(uuid, playtime)
        }
    }
}
