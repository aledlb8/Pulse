package dev.aledlb.pulse.tasks

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import org.bukkit.scheduler.BukkitRunnable

class RankExpirationTask(
    private val rankManager: RankManager,
    private val permissionManager: PermissionManager
) : BukkitRunnable() {

    override fun run() {
        // Check for expired ranks
        rankManager.checkExpiredRanks()

        // Update permissions for online players
        permissionManager.updateAllOnlinePlayersPermissions()
    }

    companion object {
        fun start(plugin: Pulse, rankManager: RankManager, permissionManager: PermissionManager) {
            // Run every minute (20 ticks * 60 seconds = 1200 ticks)
            RankExpirationTask(rankManager, permissionManager)
                .runTaskTimer(plugin, 1200L, 1200L)
        }
    }
}
