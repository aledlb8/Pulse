package dev.aledlb.pulse.profile

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.database.PunishmentRow
import dev.aledlb.pulse.database.ReportRow
import dev.aledlb.pulse.util.AsyncHelper
import dev.aledlb.pulse.util.GUIHelper
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class to hold history information
 */
private data class HistoryData(
    val punishments: List<PunishmentRow>,
    val reports: List<ReportRow>
)

class PunishmentHistoryGUI : Listener {

    private val openGuis = mutableMapOf<UUID, HistorySession>()
    private val switchingMenu = mutableSetOf<UUID>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm")

    fun openHistory(viewer: Player, target: OfflinePlayer) {
        val serializer = LegacyComponentSerializer.legacySection()
        val targetName = target.name ?: "Unknown"

        AsyncHelper.loadAsync(
            entityName = "punishment history for $targetName",
            operation = {
                val plugin = Pulse.getPlugin()
                HistoryData(
                    punishments = plugin.databaseManager.getPlayerPunishments(target.uniqueId),
                    reports = plugin.databaseManager.getPlayerReports(target.uniqueId)
                )
            },
            onSuccess = { data ->
                val plugin = Pulse.getPlugin()
                
                // Build GUI on main thread
                Bukkit.getGlobalRegionScheduler().run(plugin, { _ ->
                    val inventory = GUIHelper.createInventory("§c§l$targetName's History", 54)

                // Summary item (centered at slot 13)
                val summaryItem = GUIHelper.createItem(
                    Material.BOOK,
                    "§6§lSummary",
                    listOf(
                        "§7Player: §e$targetName",
                        "",
                        "§c§lPunishments",
                        "§7Total: §f${data.punishments.size}",
                        "§7Active Bans: §c${data.punishments.count { it.type.contains("BAN") && it.active }}",
                        "§7Active Mutes: §c${data.punishments.count { it.type == "MUTE" && it.active }}",
                        "§7Warnings: §e${data.punishments.count { it.type == "WARN" }}",
                        "",
                        "§e§lReports",
                        "§7Total: §f${data.reports.size}",
                        "§7Pending: §e${data.reports.count { it.status == "PENDING" }}"
                    )
                )
                inventory.setItem(13, summaryItem)

                // Section labels
                val punishmentsToShow = data.punishments.take(12)
                val punishmentLabel = GUIHelper.createItem(
                    Material.RED_CONCRETE,
                    "§c§lPunishments",
                    listOf("§7Showing ${punishmentsToShow.size}/${data.punishments.size}")
                )
                inventory.setItem(19, punishmentLabel)

                val reportsToShow = data.reports.take(12)
                val reportLabel = GUIHelper.createItem(
                    Material.YELLOW_CONCRETE,
                    "§e§lReports",
                    listOf("§7Showing ${reportsToShow.size}/${data.reports.size}")
                )
                inventory.setItem(25, reportLabel)

                // Punishments column (left side: slots 28, 29, 30, 37, 38, 39, etc.)
                val punishmentSlots = listOf(
                    28, 29, 30,  // Row 3
                    37, 38, 39,  // Row 4
                    46, 47, 48   // Row 5 (first 3 slots)
                )
                punishmentsToShow.forEachIndexed { index, punishment ->
                    if (index < punishmentSlots.size) {
                        val pItem = createPunishmentItem(punishment)
                        inventory.setItem(punishmentSlots[index], pItem)
                    }
                }

                // Reports column (right side: slots 32, 33, 34, 41, 42, 43, etc.)
                val reportSlots = listOf(
                    32, 33, 34,  // Row 3
                    41, 42, 43,  // Row 4
                    50, 51, 52   // Row 5 (last 3 slots)
                )
                reportsToShow.forEachIndexed { index, report ->
                    if (index < reportSlots.size) {
                        val rItem = createReportItem(report)
                        inventory.setItem(reportSlots[index], rItem)
                    }
                }

                // Back button
                inventory.setItem(45, GUIHelper.createBackButton())

                // Add decorative glass
                GUIHelper.fillBorders(inventory)

                openGuiSafely(viewer, inventory, HistorySession(target, data.punishments, data.reports))
                })
            }
        )
    }

    private fun createPunishmentItem(punishment: PunishmentRow): ItemStack {
        val serializer = LegacyComponentSerializer.legacySection()
        val material = when (punishment.type) {
            "BAN", "TEMPBAN", "IPBAN", "TEMPIPBAN" -> Material.IRON_BARS
            "MUTE" -> Material.BOOK
            "KICK" -> Material.IRON_DOOR
            "WARN" -> Material.PAPER
            else -> Material.BARRIER
        }

        return ItemStack(material).apply {
            val meta = itemMeta
            val typeColor = if (punishment.active) "§c" else "§7"
            meta.displayName(serializer.deserialize("$typeColor§l${punishment.type}"))

            val loreList = mutableListOf<Component>()
            loreList.add(serializer.deserialize("§7Reason: §f${punishment.reason}"))
            loreList.add(serializer.deserialize("§7Punisher: §e${punishment.punisherName}"))
            loreList.add(serializer.deserialize("§7Date: §f${dateFormat.format(Date(punishment.timestamp))}"))

            if (punishment.duration != null) {
                loreList.add(serializer.deserialize("§7Duration: §f${formatDuration(punishment.duration)}"))
            }

            loreList.add(Component.empty())
            loreList.add(serializer.deserialize("§7Status: ${if (punishment.active) "§aActive" else "§cInactive"}"))

            if (punishment.removedBy != null) {
                loreList.add(serializer.deserialize("§7Removed by: §e${Bukkit.getOfflinePlayer(punishment.removedBy).name}"))
                if (punishment.removedAt != null) {
                    loreList.add(serializer.deserialize("§7Removed at: §f${dateFormat.format(Date(punishment.removedAt))}"))
                }
            }

            meta.lore(loreList)
            itemMeta = meta
        }
    }

    private fun createReportItem(report: ReportRow): ItemStack {
        val serializer = LegacyComponentSerializer.legacySection()
        val material = when (report.status) {
            "PENDING" -> Material.YELLOW_DYE
            "REVIEWED" -> Material.ORANGE_DYE
            "CLOSED" -> Material.GRAY_DYE
            else -> Material.WHITE_DYE
        }

        return ItemStack(material).apply {
            val meta = itemMeta
            meta.displayName(serializer.deserialize("§e§lReport #${report.id}"))

            val loreList = mutableListOf<Component>()
            loreList.add(serializer.deserialize("§7Reason: §f${report.reason}"))
            loreList.add(serializer.deserialize("§7Reporter: §e${report.reporterName}"))
            loreList.add(serializer.deserialize("§7Date: §f${dateFormat.format(Date(report.timestamp))}"))
            loreList.add(Component.empty())

            val statusColor = when (report.status) {
                "PENDING" -> "§e"
                "REVIEWED" -> "§6"
                "CLOSED" -> "§7"
                else -> "§f"
            }
            loreList.add(serializer.deserialize("§7Status: $statusColor${report.status}"))

            if (report.handledBy != null) {
                loreList.add(serializer.deserialize("§7Handled by: §e${Bukkit.getOfflinePlayer(report.handledBy).name}"))
                if (report.handledAt != null) {
                    loreList.add(serializer.deserialize("§7Handled at: §f${dateFormat.format(Date(report.handledAt))}"))
                }
            }

            if (report.notes != null) {
                loreList.add(Component.empty())
                loreList.add(serializer.deserialize("§7Notes: §f${report.notes}"))
            }

            meta.lore(loreList)
            itemMeta = meta
        }
    }

    private fun openGuiSafely(player: Player, inv: Inventory, session: HistorySession) {
        val uuid = player.uniqueId
        switchingMenu.add(uuid)
        player.openInventory(inv)
        openGuis[uuid] = session
        switchingMenu.remove(uuid)
    }

    private fun addGlassDecoration(inventory: Inventory) {
        val glass = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            val meta = itemMeta
            meta.displayName(Component.text(" "))
            itemMeta = meta
        }

        for (i in inventory.contents.indices) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass)
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (secs > 0 || isEmpty()) append("${secs}s")
        }.trim()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = openGuis[player.uniqueId] ?: return

        val topInv = event.view.topInventory
        val clickedInv = event.clickedInventory ?: return
        val clickedTop = clickedInv == topInv

        if (clickedTop) {
            event.isCancelled = true

            val clickedItem = event.currentItem ?: return
            if (clickedItem.type == Material.AIR || clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return

            // Back button
            if (clickedItem.type == Material.ARROW) {
                Pulse.getPlugin().profileGUI.openProfile(player, session.target)
            }
        } else {
            when (event.click) {
                ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY -> event.isCancelled = true
                else -> Unit
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (openGuis[player.uniqueId] == null) return

        val topSize = event.view.topInventory.size
        if (event.rawSlots.any { it < topSize }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId
        if (uuid in switchingMenu) return
        openGuis.remove(uuid)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        switchingMenu.remove(uuid)
        openGuis.remove(uuid)
    }

    data class HistorySession(
        val target: OfflinePlayer,
        val punishments: List<PunishmentRow>,
        val reports: List<ReportRow>
    )
}
