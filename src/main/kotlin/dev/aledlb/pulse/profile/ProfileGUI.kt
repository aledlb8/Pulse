package dev.aledlb.pulse.profile

import dev.aledlb.pulse.Pulse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.util.*

class ProfileGUI : Listener {

    private val openGuis = mutableMapOf<UUID, ProfileSession>()
    private val switchingMenu = mutableSetOf<UUID>()

    fun openProfile(viewer: Player, target: OfflinePlayer) {
        val serializer = LegacyComponentSerializer.legacySection()
        val targetName = target.name ?: "Unknown"

        CoroutineScope(Dispatchers.IO).launch {
            val plugin = Pulse.getPlugin()

            // Fetch all data
            val playerData = plugin.rankManager.getPlayerData(target.uniqueId)
            val balance = plugin.economyManager.getBalance(target.uniqueId)
            val playtime = plugin.playtimeManager.getPlaytime(target.uniqueId)
            val tagData = plugin.tagManager.getPlayerTagData(target.uniqueId)
            val rank = playerData?.rank ?: "Default"

            // Build GUI on main thread
            Bukkit.getGlobalRegionScheduler().run(plugin, { _ ->
                val inventory = Bukkit.createInventory(null, 54, serializer.deserialize("§5§l$targetName's Profile"))

                // Player head (centered at slot 13)
                val playerHead = ItemStack(Material.PLAYER_HEAD).apply {
                    val meta = itemMeta
                    meta.displayName(serializer.deserialize("§e§l$targetName"))
                    meta.lore(listOf(
                        serializer.deserialize("§7UUID: §f${target.uniqueId}"),
                        Component.empty(),
                        serializer.deserialize("§7Status: ${if (target.isOnline) "§aOnline" else "§cOffline"}")
                    ))
                    itemMeta = meta
                }
                inventory.setItem(13, playerHead)

                // Row 3: Centered stats items
                // Rank info (slot 28)
                val rankItem = ItemStack(Material.DIAMOND).apply {
                    val meta = itemMeta
                    meta.displayName(serializer.deserialize("§6§lRank"))
                    meta.lore(listOf(
                        serializer.deserialize("§7Current Rank:"),
                        serializer.deserialize("§e$rank")
                    ))
                    itemMeta = meta
                }
                inventory.setItem(28, rankItem)

                // Balance info (slot 30)
                val balanceItem = ItemStack(Material.GOLD_INGOT).apply {
                    val meta = itemMeta
                    meta.displayName(serializer.deserialize("§6§lBalance"))
                    val formattedBalance = plugin.economyManager.formatBalance(balance)
                    meta.lore(listOf(
                        serializer.deserialize("§7Balance:"),
                        serializer.deserialize("§a$formattedBalance"),
                        Component.empty(),
                        serializer.deserialize("§7Currency: ${plugin.economyManager.getCurrencyName()}")
                    ))
                    itemMeta = meta
                }
                inventory.setItem(30, balanceItem)

                // Playtime info (slot 32)
                val playtimeItem = ItemStack(Material.CLOCK).apply {
                    val meta = itemMeta
                    meta.displayName(serializer.deserialize("§6§lPlaytime"))
                    val formattedPlaytime = formatPlaytime(playtime)
                    meta.lore(listOf(
                        serializer.deserialize("§7Total Playtime:"),
                        serializer.deserialize("§e$formattedPlaytime")
                    ))
                    itemMeta = meta
                }
                inventory.setItem(32, playtimeItem)

                // Tags info (slot 34)
                val tagsItem = ItemStack(Material.NAME_TAG).apply {
                    val meta = itemMeta
                    meta.displayName(serializer.deserialize("§6§lTags"))
                    val activeTags = tagData?.activeTags ?: emptySet()
                    val ownedTags = tagData?.ownedTags ?: emptySet()
                    val loreList = mutableListOf<Component>()
                    loreList.add(serializer.deserialize("§7Active: §e${activeTags.size}"))
                    loreList.add(serializer.deserialize("§7Owned: §e${ownedTags.size}"))

                    if (activeTags.isNotEmpty()) {
                        loreList.add(Component.empty())
                        loreList.add(serializer.deserialize("§7Active Tags:"))
                        activeTags.take(5).forEach { tag ->
                            loreList.add(serializer.deserialize("§8▪ §f$tag"))
                        }
                        if (activeTags.size > 5) {
                            loreList.add(serializer.deserialize("§7... and ${activeTags.size - 5} more"))
                        }
                    }

                    meta.lore(loreList)
                    itemMeta = meta
                }
                inventory.setItem(34, tagsItem)

                // Staff button (top right corner - slot 8) - only if viewer has permission
                if (viewer.hasPermission("pulse.profile.staff")) {
                    val staffButton = ItemStack(Material.REDSTONE_TORCH).apply {
                        val meta = itemMeta
                        meta.displayName(serializer.deserialize("§c§lStaff View"))
                        meta.lore(listOf(
                            serializer.deserialize("§7View punishments & reports"),
                            Component.empty(),
                            serializer.deserialize("§aClick to open!")
                        ))
                        itemMeta = meta
                    }
                    inventory.setItem(8, staffButton)
                }

                // Add decorative glass
                addGlassDecoration(inventory)

                openGuiSafely(viewer, inventory, ProfileSession(ProfileView.MAIN, target))
            })
        }
    }

    private fun openGuiSafely(player: Player, inv: Inventory, session: ProfileSession) {
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

    private fun formatPlaytime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0 || isEmpty()) append("${minutes}m")
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

            when (session.view) {
                ProfileView.MAIN -> handleMainClick(player, session, clickedItem, event.slot)
            }
        } else {
            // Block shift-clicking into GUI
            when (event.click) {
                ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY -> event.isCancelled = true
                else -> Unit
            }
        }
    }

    private fun handleMainClick(player: Player, session: ProfileSession, clickedItem: ItemStack, slot: Int) {
        // Staff button at slot 8
        if (slot == 8 && player.hasPermission("pulse.profile.staff")) {
            Pulse.getPlugin().punishmentHistoryGUI.openHistory(player, session.target)
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

    data class ProfileSession(
        val view: ProfileView,
        val target: OfflinePlayer
    )

    enum class ProfileView {
        MAIN
    }
}
