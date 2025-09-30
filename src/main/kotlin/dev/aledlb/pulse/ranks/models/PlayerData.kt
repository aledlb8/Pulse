package dev.aledlb.pulse.ranks.models

import java.util.*

data class PlayerData(
    val uuid: UUID,
    val name: String,
    var rank: String,
    val permissions: MutableSet<String> = mutableSetOf(),
    val deniedPermissions: MutableSet<String> = mutableSetOf(),
    var lastSeen: Long = System.currentTimeMillis()
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

        // Check rank permissions (including inherited from parents)
        val allRankPermissions = rankSystem.getAllPermissions(rank)
        if (allRankPermissions.contains("*")) return true
        if (allRankPermissions.contains(permission)) return true

        // Check wildcard permissions from rank hierarchy
        for (i in parts.indices) {
            val wildcard = parts.take(i + 1).joinToString(".") + ".*"
            if (allRankPermissions.contains(wildcard)) return true
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

    fun getAllPermissions(rankSystem: RankManager): Set<String> {
        val allPermissions = mutableSetOf<String>()

        // Add player-specific permissions
        allPermissions.addAll(permissions)

        // Add rank permissions (including inherited from parents)
        allPermissions.addAll(rankSystem.getAllPermissions(rank))

        // Remove denied permissions
        allPermissions.removeAll(deniedPermissions)

        return allPermissions
    }
}