package dev.aledlb.pulse.ranks.models

import java.util.*

data class PlayerRankEntry(
    val rankName: String,
    var expiration: Long? = null // null = permanent
)

data class PlayerData(
    val uuid: UUID,
    val name: String,
    var rank: String, // Primary rank for backward compatibility
    val ranks: MutableSet<PlayerRankEntry> = mutableSetOf(), // All ranks with expiration
    val permissions: MutableSet<String> = mutableSetOf(),
    val deniedPermissions: MutableSet<String> = mutableSetOf(),
    var lastSeen: Long = System.currentTimeMillis(),
    var rankExpiration: Long? = null // Kept for backward compatibility
) {
    fun hasPermission(permission: String, rankSystem: RankManager): Boolean {
        // Check denied permissions first
        if (deniedPermissions.contains(permission)) return false
        if (deniedPermissions.contains("*")) return false

        // Check denied wildcard permissions
        val parts = permission.split(".")
        for (i in parts.indices) {
            val wildcard = parts.take(i + 1).joinToString(".") + ".*"
            if (deniedPermissions.contains(wildcard)) return false
        }

        // Check player-specific permissions
        if (permissions.contains("*")) return true
        if (permissions.contains(permission)) return true

        // Check player wildcard permissions
        for (i in parts.indices) {
            val wildcard = parts.take(i + 1).joinToString(".") + ".*"
            if (permissions.contains(wildcard)) return true
        }

        // Check all rank permissions (from all non-expired ranks)
        for (rankEntry in ranks) {
            if (rankEntry.expiration != null && rankEntry.expiration!! < System.currentTimeMillis()) {
                continue // Skip expired ranks
            }
            val allRankPermissions = rankSystem.getAllPermissions(rankEntry.rankName)
            if (allRankPermissions.contains("*")) return true
            if (allRankPermissions.contains(permission)) return true

            // Check wildcard permissions from rank hierarchy
            for (i in parts.indices) {
                val wildcard = parts.take(i + 1).joinToString(".") + ".*"
                if (allRankPermissions.contains(wildcard)) return true
            }
        }

        return false
    }

    fun addPermission(permission: String) {
        permissions.add(permission)
        deniedPermissions.remove(permission) // Remove from denied if exists
    }

    fun removePermission(permission: String) {
        permissions.remove(permission)
    }

    fun denyPermission(permission: String) {
        deniedPermissions.add(permission)
        permissions.remove(permission) // Remove from allowed if exists
    }

    fun undenyPermission(permission: String) {
        deniedPermissions.remove(permission)
    }

    fun updateRank(newRank: String) {
        rank = newRank
    }

    fun addRank(rankName: String, expiration: Long? = null) {
        // Remove existing entry for this rank if present
        ranks.removeIf { it.rankName.lowercase() == rankName.lowercase() }
        ranks.add(PlayerRankEntry(rankName, expiration))
        updatePrimaryRank()
    }

    fun removeRank(rankName: String): Boolean {
        val removed = ranks.removeIf { it.rankName.lowercase() == rankName.lowercase() }
        if (removed) {
            updatePrimaryRank()
        }
        return removed
    }

    fun getRanks(): List<PlayerRankEntry> {
        return ranks.filter {
            it.expiration == null || it.expiration!! > System.currentTimeMillis()
        }
    }

    fun getExpiredRanks(): List<PlayerRankEntry> {
        return ranks.filter {
            it.expiration != null && it.expiration!! < System.currentTimeMillis()
        }
    }

    fun hasExpired(): Boolean {
        return rankExpiration?.let { it < System.currentTimeMillis() } ?: false
    }

    fun isRankPermanent(): Boolean {
        return rankExpiration == null
    }

    fun getRemainingTime(): Long? {
        return rankExpiration?.let { it - System.currentTimeMillis() }
    }

    private fun updatePrimaryRank() {
        // Find the highest weight non-expired rank
        val activeRanks = getRanks()
        if (activeRanks.isEmpty()) {
            // Will be set to default rank by RankManager
            return
        }

        // This will be updated by RankManager with the highest weight rank
    }

    fun getAllPermissions(rankSystem: RankManager): Set<String> {
        val allPermissions = mutableSetOf<String>()

        // Add player-specific permissions
        allPermissions.addAll(permissions)

        // Add permissions from all non-expired ranks
        for (rankEntry in ranks) {
            if (rankEntry.expiration == null || rankEntry.expiration!! > System.currentTimeMillis()) {
                allPermissions.addAll(rankSystem.getAllPermissions(rankEntry.rankName))
            }
        }

        // Remove denied permissions
        allPermissions.removeAll(deniedPermissions)

        return allPermissions
    }
}