package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.UUID
import java.util.concurrent.TimeUnit

class GrantCommand(
    private val rankManager: RankManager,
    private val permissionManager: PermissionManager
) : BaseCommand(), Listener {

    private val messagesManager get() = Pulse.getPlugin().messagesManager
    private val activeGuis = mutableMapOf<UUID, GuiSession>()

    override val name = "grant"
    override val permission = "pulse.grant"
    override val description = "Grant ranks to players with a GUI"
    override val usage = "/grant <player>"

    init {
        Bukkit.getPluginManager().registerEvents(this, Pulse.getPlugin())
    }

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(messagesManager.getFormattedMessage("general.player-only"))
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage(messagesManager.invalidCommand())
            sendUsage(sender)
            return
        }

        val targetPlayer = Bukkit.getPlayer(args[0])
        if (targetPlayer == null) {
            sender.sendMessage(messagesManager.getFormattedMessage("general.player-not-online", "player" to args[0]))
            return
        }

        openMainMenu(sender, targetPlayer)
    }

    private fun openMainMenu(viewer: Player, target: Player) {
        val inv = Bukkit.createInventory(null, 27, "§5§lGrant Ranks - ${target.name}")

        // Create Give Rank button (slot 11 - left center)
        val giveRankItem = ItemStack(Material.EMERALD)
        val giveRankMeta = giveRankItem.itemMeta!!
        giveRankMeta.setDisplayName("§a§lGive Rank")
        giveRankMeta.lore = listOf(
            "§7Click to select a rank",
            "§7to give to this player"
        )
        giveRankItem.itemMeta = giveRankMeta
        inv.setItem(11, giveRankItem)

        // Create See Ranks button (slot 15 - right center)
        val seeRanksItem = ItemStack(Material.BOOK)
        val seeRanksMeta = seeRanksItem.itemMeta!!
        seeRanksMeta.setDisplayName("§e§lSee Ranks")
        val playerData = rankManager.getPlayerData(target)
        val defaultRank = rankManager.getDefaultRank()
        val activeRanks = playerData.getRanks().filter { it.rankName.lowercase() != defaultRank.lowercase() }

        val lore = mutableListOf<String>()
        lore.add("§7Click to view and manage")
        lore.add("§7current ranks")
        lore.add("")

        if (activeRanks.isEmpty()) {
            lore.add("§7No additional ranks")
        } else {
            lore.add("§7Active Ranks: §e${activeRanks.size}")
            val primaryRank = rankManager.getRank(playerData.rank)
            if (primaryRank != null) {
                lore.add("§7Primary: §e${primaryRank.name}")
            }
        }

        seeRanksMeta.lore = lore
        seeRanksItem.itemMeta = seeRanksMeta
        inv.setItem(15, seeRanksItem)

        activeGuis[viewer.uniqueId] = GuiSession(
            type = GuiType.MAIN_MENU,
            target = target.uniqueId
        )

        viewer.openInventory(inv)
    }

    private fun openSeeRanksMenu(viewer: Player, target: Player) {
        val inv = Bukkit.createInventory(null, 54, "§5§lCurrent Ranks - ${target.name}")

        val playerData = rankManager.getPlayerData(target)
        val defaultRank = rankManager.getDefaultRank()
        val activeRanks = playerData.getRanks().filter { it.rankName.lowercase() != defaultRank.lowercase() }

        if (activeRanks.isEmpty()) {
            val noRankItem = ItemStack(Material.BARRIER)
            val noRankMeta = noRankItem.itemMeta!!
            noRankMeta.setDisplayName("§cNo Ranks")
            noRankMeta.lore = listOf("§7This player has no additional ranks")
            noRankItem.itemMeta = noRankMeta
            inv.setItem(22, noRankItem)
        } else {
            activeRanks.forEachIndexed { index, rankEntry ->
                if (index >= 45) return@forEachIndexed // Max 45 ranks

                val rank = rankManager.getRank(rankEntry.rankName) ?: return@forEachIndexed
                val isPrimary = playerData.rank.lowercase() == rankEntry.rankName.lowercase()

                val rankItem = ItemStack(if (isPrimary) Material.DIAMOND else Material.NAME_TAG)
                val rankMeta = rankItem.itemMeta!!
                rankMeta.setDisplayName("${rank.name}")

                val lore = mutableListOf<String>()
                lore.add("§7Weight: §e${rank.weight}")

                if (isPrimary) {
                    lore.add("§aPrimary Rank")
                }

                if (rankEntry.expiration != null) {
                    val remaining = (rankEntry.expiration!! - System.currentTimeMillis())
                    if (remaining > 0) {
                        lore.add("§7Duration: §e${formatDuration(remaining)}")
                    } else {
                        lore.add("§cExpired")
                    }
                } else {
                    lore.add("§7Duration: §aPermanent")
                }

                lore.add("")
                lore.add("§cClick to remove this rank")

                rankMeta.lore = lore
                rankItem.itemMeta = rankMeta
                inv.setItem(index, rankItem)
            }
        }

        // Back button
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta!!
        backMeta.setDisplayName("§7« Back")
        backItem.itemMeta = backMeta
        inv.setItem(49, backItem)

        activeGuis[viewer.uniqueId] = GuiSession(
            type = GuiType.SEE_RANKS,
            target = target.uniqueId
        )

        viewer.openInventory(inv)
    }

    private fun openGiveRankMenu(viewer: Player, target: Player) {
        val defaultRank = rankManager.getDefaultRank()
        val ranks = rankManager.getRanksSorted().filter { it.name.lowercase() != defaultRank.lowercase() }
        val inv = Bukkit.createInventory(null, 54, "§5§lSelect Rank - ${target.name}")

        ranks.forEachIndexed { index, rank ->
            if (index >= 45) return@forEachIndexed // Max 45 ranks

            val rankItem = ItemStack(Material.DIAMOND)
            val rankMeta = rankItem.itemMeta!!
            rankMeta.setDisplayName("${rank.name}")
            rankMeta.lore = listOf(
                "§7Weight: §e${rank.weight}",
                "§7Permissions: §e${rank.permissions.size}",
                "",
                "§aClick to select this rank"
            )
            rankItem.itemMeta = rankMeta
            inv.setItem(index, rankItem)
        }

        // Back button
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta!!
        backMeta.setDisplayName("§7« Back")
        backItem.itemMeta = backMeta
        inv.setItem(49, backItem)

        activeGuis[viewer.uniqueId] = GuiSession(
            type = GuiType.GIVE_RANK,
            target = target.uniqueId
        )

        viewer.openInventory(inv)
    }

    private fun openDurationMenu(viewer: Player, target: Player, selectedRank: String) {
        val inv = Bukkit.createInventory(null, 27, "§5§lSelect Duration")

        // Permanent option
        val permanentItem = ItemStack(Material.NETHER_STAR)
        val permanentMeta = permanentItem.itemMeta!!
        permanentMeta.setDisplayName("§a§lPermanent")
        permanentMeta.lore = listOf("§7This rank will never expire")
        permanentItem.itemMeta = permanentMeta
        inv.setItem(10, permanentItem)

        // Duration options
        val durations = mapOf(
            12 to Pair("1 Hour", TimeUnit.HOURS.toMillis(1)),
            13 to Pair("1 Day", TimeUnit.DAYS.toMillis(1)),
            14 to Pair("7 Days", TimeUnit.DAYS.toMillis(7)),
            15 to Pair("30 Days", TimeUnit.DAYS.toMillis(30)),
            16 to Pair("90 Days", TimeUnit.DAYS.toMillis(90))
        )

        durations.forEach { (slot, data) ->
            val durationItem = ItemStack(Material.CLOCK)
            val durationMeta = durationItem.itemMeta!!
            durationMeta.setDisplayName("§e§l${data.first}")
            durationMeta.lore = listOf("§7Rank will expire after this time")
            durationItem.itemMeta = durationMeta
            inv.setItem(slot, durationItem)
        }

        // Back button
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta!!
        backMeta.setDisplayName("§7« Back")
        backItem.itemMeta = backMeta
        inv.setItem(22, backItem)

        activeGuis[viewer.uniqueId] = GuiSession(
            type = GuiType.DURATION,
            target = target.uniqueId,
            selectedRank = selectedRank
        )

        viewer.openInventory(inv)
    }

    private fun openConfirmationMenu(viewer: Player, target: Player, selectedRank: String, duration: Long?) {
        val inv = Bukkit.createInventory(null, 27, "§5§lConfirm Grant")

        val rank = rankManager.getRank(selectedRank)
        if (rank == null) {
            viewer.closeInventory()
            return
        }

        // Information display
        val infoItem = ItemStack(Material.PAPER)
        val infoMeta = infoItem.itemMeta!!
        infoMeta.setDisplayName("§e§lGrant Summary")
        val durationText = if (duration == null) "§aPermanent" else "§e${formatDuration(duration)}"
        infoMeta.lore = listOf(
            "§7Player: §e${target.name}",
            "§7Rank: ${rank.name}",
            "§7Duration: $durationText"
        )
        infoItem.itemMeta = infoMeta
        inv.setItem(13, infoItem)

        // Confirm button
        val confirmItem = ItemStack(Material.LIME_WOOL)
        val confirmMeta = confirmItem.itemMeta!!
        confirmMeta.setDisplayName("§a§lCONFIRM")
        confirmMeta.lore = listOf("§7Click to grant this rank")
        confirmItem.itemMeta = confirmMeta
        inv.setItem(11, confirmItem)

        // Cancel button
        val cancelItem = ItemStack(Material.RED_WOOL)
        val cancelMeta = cancelItem.itemMeta!!
        cancelMeta.setDisplayName("§c§lCANCEL")
        cancelMeta.lore = listOf("§7Click to cancel")
        cancelItem.itemMeta = cancelMeta
        inv.setItem(15, cancelItem)

        activeGuis[viewer.uniqueId] = GuiSession(
            type = GuiType.CONFIRMATION,
            target = target.uniqueId,
            selectedRank = selectedRank,
            selectedDuration = duration
        )

        viewer.openInventory(inv)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = activeGuis[player.uniqueId] ?: return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR) return

        val target = Bukkit.getPlayer(session.target)
        if (target == null) {
            player.closeInventory()
            player.sendMessage(messagesManager.getFormattedMessage("general.player-not-online", "player" to "target"))
            activeGuis.remove(player.uniqueId)
            return
        }

        when (session.type) {
            GuiType.MAIN_MENU -> handleMainMenuClick(player, target, event.slot)
            GuiType.SEE_RANKS -> handleSeeRanksClick(player, target, event.slot, clickedItem)
            GuiType.GIVE_RANK -> handleGiveRankClick(player, target, event.slot, clickedItem)
            GuiType.DURATION -> handleDurationClick(player, target, event.slot, session)
            GuiType.CONFIRMATION -> handleConfirmationClick(player, target, event.slot, session)
        }
    }

    private fun handleMainMenuClick(viewer: Player, target: Player, slot: Int) {
        when (slot) {
            11 -> openGiveRankMenu(viewer, target)
            15 -> openSeeRanksMenu(viewer, target)
        }
    }

    private fun handleSeeRanksClick(viewer: Player, target: Player, slot: Int, item: ItemStack) {
        when (slot) {
            49 -> openMainMenu(viewer, target)
            in 0..44 -> {
                if (item.type == Material.NAME_TAG || item.type == Material.DIAMOND) {
                    // Remove rank - extract rank name from display name
                    val rankName = item.itemMeta?.displayName ?: return

                    if (rankManager.removePlayerRank(target, rankName)) {
                        permissionManager.updatePlayerPermissions(target)
                        permissionManager.updatePlayerDisplayNames()

                        viewer.sendMessage(messagesManager.getFormattedMessage("rank.remove-success", "player" to target.name, "rank" to rankName))
                        target.sendMessage(messagesManager.getFormattedMessage("rank.remove-notification", "rank" to rankName))

                        // Refresh the GUI
                        openSeeRanksMenu(viewer, target)
                    } else {
                        viewer.sendMessage(messagesManager.invalidCommand())
                    }
                }
            }
        }
    }

    private fun handleGiveRankClick(viewer: Player, target: Player, slot: Int, item: ItemStack) {
        when (slot) {
            49 -> openMainMenu(viewer, target)
            in 0..44 -> {
                if (item.type == Material.DIAMOND) {
                    val rankName = item.itemMeta?.displayName ?: return
                    openDurationMenu(viewer, target, rankName)
                }
            }
        }
    }

    private fun handleDurationClick(viewer: Player, target: Player, slot: Int, session: GuiSession) {
        val selectedRank = session.selectedRank ?: return

        when (slot) {
            10 -> openConfirmationMenu(viewer, target, selectedRank, null) // Permanent
            12 -> openConfirmationMenu(viewer, target, selectedRank, TimeUnit.HOURS.toMillis(1))
            13 -> openConfirmationMenu(viewer, target, selectedRank, TimeUnit.DAYS.toMillis(1))
            14 -> openConfirmationMenu(viewer, target, selectedRank, TimeUnit.DAYS.toMillis(7))
            15 -> openConfirmationMenu(viewer, target, selectedRank, TimeUnit.DAYS.toMillis(30))
            16 -> openConfirmationMenu(viewer, target, selectedRank, TimeUnit.DAYS.toMillis(90))
            22 -> openGiveRankMenu(viewer, target)
        }
    }

    private fun handleConfirmationClick(viewer: Player, target: Player, slot: Int, session: GuiSession) {
        val selectedRank = session.selectedRank ?: return

        when (slot) {
            11 -> { // Confirm
                val expiration = session.selectedDuration?.let { System.currentTimeMillis() + it }

                if (rankManager.setPlayerRank(target, selectedRank, expiration)) {
                    permissionManager.updatePlayerPermissions(target)
                    permissionManager.updatePlayerDisplayNames()

                    val rank = rankManager.getRank(selectedRank)
                    val durationText = if (expiration == null) "permanently" else "for ${formatDuration(session.selectedDuration)}"

                    viewer.sendMessage(messagesManager.getFormattedMessage("rank.set-success", "player" to target.name, "rank" to rank?.name!!))
                    target.sendMessage(messagesManager.getFormattedMessage("rank.set-notification", "rank" to rank.name))

                    viewer.closeInventory()
                    activeGuis.remove(viewer.uniqueId)
                } else {
                    viewer.sendMessage(messagesManager.invalidCommand())
                    viewer.closeInventory()
                    activeGuis.remove(viewer.uniqueId)
                }
            }
            15 -> { // Cancel
                viewer.closeInventory()
                activeGuis.remove(viewer.uniqueId)
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[0], true) }
            else -> emptyList()
        }
    }

    data class GuiSession(
        val type: GuiType,
        val target: UUID,
        val selectedRank: String? = null,
        val selectedDuration: Long? = null
    )

    enum class GuiType {
        MAIN_MENU,
        SEE_RANKS,
        GIVE_RANK,
        DURATION,
        CONFIRMATION
    }
}
