package dev.aledlb.pulse.tasks

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import dev.aledlb.pulse.util.Logger
import org.bukkit.Bukkit
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import java.util.function.Consumer

object RankExpirationTask {

    fun start(
        plugin: Pulse,
        rankManager: RankManager,
        permissionManager: PermissionManager
    ): ScheduledTask {
        val initial = 1200L // 60s
        val period  = 1200L // 60s

        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            {
                // Heavy work off-thread
                Bukkit.getAsyncScheduler().runNow(plugin) { _ ->
                    try {
                        rankManager.checkExpiredRanks()
                    } catch (t: Throwable) {
                        Logger.warn("Rank check failed: ${t.message}")
                    }

                    for (player in Bukkit.getOnlinePlayers()) {
                        player.scheduler.run(
                            plugin,
                            {
                                try {
                                    permissionManager.updatePlayerPermissions(player)
                                } catch (t: Throwable) {
                                    Logger.warn("Perm update failed for ${player.name}: ${t.message}")
                                }
                            },
                            null
                        )
                    }
                }
            },
            initial,
            period
        )
    }
}