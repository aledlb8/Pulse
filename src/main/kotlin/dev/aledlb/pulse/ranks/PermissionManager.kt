package dev.aledlb.pulse.ranks

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.models.RankManager
import dev.aledlb.pulse.util.Logger
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.permissions.PermissionAttachment
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class PermissionManager(private val rankManager: RankManager) : Listener {
    private val attachments = ConcurrentHashMap<UUID, PermissionAttachment>()

    fun initialize() {
        // Ready for event handling
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        setupPlayerPermissions(player)
        updatePlayerPermissions(player)

        // Update last seen
        val playerData = rankManager.getOrCreatePlayerData(player.uniqueId, player.name)
        playerData.lastSeen = System.currentTimeMillis()

        Logger.debug("Set up permissions for player ${player.name}")
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        cleanupPlayerPermissions(player)

        // Update last seen
        val playerData = rankManager.getPlayerData(player.uniqueId)
        playerData?.lastSeen = System.currentTimeMillis()

        Logger.debug("Cleaned up permissions for player ${player.name}")
    }

    fun setupPlayerPermissions(player: Player) {
        if (attachments.containsKey(player.uniqueId)) {
            cleanupPlayerPermissions(player)
        }

        val attachment = player.addAttachment(dev.aledlb.pulse.Pulse.getPlugin())
        attachments[player.uniqueId] = attachment
    }

    fun cleanupPlayerPermissions(player: Player) {
        val attachment = attachments.remove(player.uniqueId)
        attachment?.remove()
    }

    fun updatePlayerPermissions(player: Player) {
        val attachment = attachments[player.uniqueId] ?: return
        val playerData = rankManager.getOrCreatePlayerData(player.uniqueId, player.name)

        // Clear existing permissions
        attachment.permissions.clear()

        // Get all permissions for the player (rank + player-specific)
        val allPermissions = playerData.getAllPermissions(rankManager)

        // Set permissions
        for (permission in allPermissions) {
            attachment.setPermission(permission, true)
        }

        // Set denied permissions
        for (deniedPermission in playerData.deniedPermissions) {
            attachment.setPermission(deniedPermission, false)
        }

        player.recalculatePermissions()
        Logger.debug("Updated permissions for ${player.name}: ${allPermissions.size} permissions")
    }

    fun updateAllOnlinePlayersPermissions() {
        for (player in dev.aledlb.pulse.Pulse.getPlugin().server.onlinePlayers) {
            updatePlayerPermissions(player)
        }
        Logger.info("Updated permissions for all online players")
    }

    fun hasPermission(player: Player, permission: String): Boolean {
        return rankManager.hasPermission(player, permission)
    }

    fun addPlayerPermission(player: Player, permission: String) {
        val playerData = rankManager.getOrCreatePlayerData(player.uniqueId, player.name)
        playerData.addPermission(permission)
        updatePlayerPermissions(player)
    }

    fun removePlayerPermission(player: Player, permission: String) {
        val playerData = rankManager.getOrCreatePlayerData(player.uniqueId, player.name)
        playerData.removePermission(permission)
        updatePlayerPermissions(player)
    }

    fun denyPlayerPermission(player: Player, permission: String) {
        val playerData = rankManager.getOrCreatePlayerData(player.uniqueId, player.name)
        playerData.denyPermission(permission)
        updatePlayerPermissions(player)
    }

    fun setPlayerRank(player: Player, rankName: String): Boolean {
        val success = rankManager.setPlayerRank(player, rankName)
        if (success) {
            updatePlayerPermissions(player)
            // Update chat formatting when rank changes
            if (Pulse.getPlugin().isPluginFullyLoaded()) {
                Pulse.getPlugin().chatManager.updatePlayerFormats(player)
            }
        }
        return success
    }

    fun getPlayerDisplayName(player: Player): String {
        val playerData = rankManager.getPlayerData(player)
        val rank = rankManager.getRank(playerData.rank)
        return if (rank != null) {
            "${rank.prefix}${player.name}${rank.suffix}"
        } else {
            player.name
        }
    }

    fun getPlayerListName(player: Player): String {
        return getPlayerDisplayName(player)
    }

    fun updatePlayerDisplayNames() {
        for (player in dev.aledlb.pulse.Pulse.getPlugin().server.onlinePlayers) {
            val displayName = getPlayerDisplayName(player)
            player.displayName(net.kyori.adventure.text.Component.text(displayName))
            player.playerListName(net.kyori.adventure.text.Component.text(displayName))
        }
    }

    fun reloadPermissions() {
        rankManager.reload()
        updateAllOnlinePlayersPermissions()
        updatePlayerDisplayNames()
        Logger.success("Reloaded permission system")
    }
}