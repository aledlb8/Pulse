package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import dev.aledlb.pulse.ranks.models.RankManager
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import net.kyori.adventure.text.Component
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import net.kyori.adventure.text.format.NamedTextColor
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import net.kyori.adventure.text.format.TextDecoration
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.Bukkit
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.Material
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.command.CommandSender
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.entity.Player
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.event.EventHandler
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.event.Listener
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.event.inventory.ClickType
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.event.inventory.InventoryClickEvent
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.event.inventory.InventoryCloseEvent
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.event.inventory.InventoryDragEvent
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.event.player.PlayerQuitEvent
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.inventory.Inventory
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.inventory.ItemStack
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import java.util.UUID
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import java.util.concurrent.TimeUnit
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage

class GrantCommand(
    private val rankManager: RankManager,
    private val permissionManager: PermissionManager
) : BaseCommand(), Listener {

    private val messagesManager get() = Pulse.getPlugin().messagesManager
    private val activeGuis = mutableMapOf<UUID, GuiSession>()
    private val switchingMenu = mutableSetOf<UUID>()

    override val name = "grant"
    override val permission = "pulse.grant"
    override val description = "Grant ranks to players with a GUI"
    override val usage = "/grant <player>"

    init {
        Bukkit.getPluginManager().registerEvents(this, Pulse.getPlugin())
    }

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-only"))
            return
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return
        }

        val targetPlayer = Bukkit.getPlayer(args[0])
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to args[0]))
            return
        }

        openMainMenu(sender, targetPlayer)
    }

    // ----- GUI OPEN HELPERS -----

    private fun openGuiSafely(viewer: Player, inv: Inventory, session: GuiSession) {
        val uuid = viewer.uniqueId
        switchingMenu.add(uuid)
        // This call synchronously fires InventoryCloseEvent for the previous view.
        viewer.openInventory(inv)
        // After openInventory returns, it's safe to install the new session.
        activeGuis[uuid] = session
        switchingMenu.remove(uuid)
    }

    private fun openMainMenu(viewer: Player, target: Player) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Grant Ranks - ${target.name}")
            .color(NamedTextColor.DARK_PURPLE)
            .decorate(TextDecoration.BOLD))

        // Give Rank button
        inv.setItem(11, ItemStack(Material.EMERALD).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("Give Rank")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false))
                lore(listOf(
                    Component.text("Click to select a rank").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("to give to this player").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ))
            }
        })

        // See Ranks button
        val playerData = rankManager.getPlayerData(target)
        val defaultRank = rankManager.getDefaultRank()
        val activeRanks = playerData.getRanks().filter { it.rankName.lowercase() != defaultRank.lowercase() }
        val seeLore = buildList {
            add("§7Click to view and manage")
            add("§7current ranks")
            add("")
            if (activeRanks.isEmpty()) {
                add("§7No additional ranks")
            } else {
                add("§7Active Ranks: §e${activeRanks.size}")
                val primaryRank = rankManager.getRank(playerData.rank)
                if (primaryRank != null) add("§7Primary: §e${primaryRank.name}")
            }
        }

        inv.setItem(15, ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("See Ranks")
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false))
                lore(seeLore.map { line ->
                    Component.text(line.replace("§7", "").replace("§e", "").replace("§a", ""))
                        .color(when {
                            line.contains("§e") -> NamedTextColor.YELLOW
                            line.contains("§a") -> NamedTextColor.GREEN
                            else -> NamedTextColor.GRAY
                        })
                        .decoration(TextDecoration.ITALIC, false)
                })
            }
        })

        openGuiSafely(
            viewer,
            inv,
            GuiSession(
                type = GuiType.MAIN_MENU,
                target = target.uniqueId
            )
        )
    }

    private fun openSeeRanksMenu(viewer: Player, target: Player) {
        val inv = Bukkit.createInventory(null, 54, Component.text("Current Ranks - ${target.name}")
            .color(NamedTextColor.DARK_PURPLE)
            .decorate(TextDecoration.BOLD))

        val playerData = rankManager.getPlayerData(target)
        val defaultRank = rankManager.getDefaultRank()
        val activeRanks = playerData.getRanks().filter { it.rankName.lowercase() != defaultRank.lowercase() }

        if (activeRanks.isEmpty()) {
            inv.setItem(22, ItemStack(Material.BARRIER).apply {
                itemMeta = itemMeta.apply {
                    displayName(Component.text("No Ranks")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false))
                    lore(listOf(
                        Component.text("This player has no additional ranks")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                    ))
                }
            })
        } else {
            activeRanks.forEachIndexed { index, rankEntry ->
                if (index >= 45) return@forEachIndexed // Max 45 ranks
                val rank = rankManager.getRank(rankEntry.rankName) ?: return@forEachIndexed
                val isPrimary = playerData.rank.lowercase() == rankEntry.rankName.lowercase()

                inv.setItem(index, ItemStack(if (isPrimary) Material.DIAMOND else Material.NAME_TAG).apply {
                    itemMeta = itemMeta.apply {
                        displayName(Component.text(rank.name.replace("§[0-9a-fk-or]".toRegex(), ""))
                            .decoration(TextDecoration.ITALIC, false))
                        val componentLore = mutableListOf<Component>()
                        componentLore.add(Component.text("Weight: ").color(NamedTextColor.GRAY)
                            .append(Component.text("${rank.weight}").color(NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false))
                        if (isPrimary) componentLore.add(Component.text("Primary Rank")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false))
                        if (rankEntry.expiration != null) {
                            val remaining = (rankEntry.expiration!! - System.currentTimeMillis())
                            if (remaining > 0) {
                                componentLore.add(Component.text("Duration: ").color(NamedTextColor.GRAY)
                                    .append(Component.text(formatDuration(remaining)).color(NamedTextColor.YELLOW))
                                    .decoration(TextDecoration.ITALIC, false))
                            } else {
                                componentLore.add(Component.text("Expired").color(NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false))
                            }
                        } else {
                            componentLore.add(Component.text("Duration: ").color(NamedTextColor.GRAY)
                                .append(Component.text("Permanent").color(NamedTextColor.GREEN))
                                .decoration(TextDecoration.ITALIC, false))
                        }
                        componentLore.add(Component.empty())
                        componentLore.add(Component.text("Click to remove this rank")
                            .color(NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false))
                        lore(componentLore)
                    }
                })
            }
        }

        // Back
        inv.setItem(49, ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("« Back")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false))
            }
        })

        openGuiSafely(
            viewer,
            inv,
            GuiSession(
                type = GuiType.SEE_RANKS,
                target = target.uniqueId
            )
        )
    }

    private fun openGiveRankMenu(viewer: Player, target: Player) {
        val defaultRank = rankManager.getDefaultRank()
        val ranks = rankManager.getRanksSorted().filter { it.name.lowercase() != defaultRank.lowercase() }
        val inv = Bukkit.createInventory(null, 54, Component.text("Select Rank - ${target.name}")
            .color(NamedTextColor.DARK_PURPLE)
            .decorate(TextDecoration.BOLD))

        ranks.forEachIndexed { index, rank ->
            if (index >= 45) return@forEachIndexed
            inv.setItem(index, ItemStack(Material.DIAMOND).apply {
                itemMeta = itemMeta.apply {
                    displayName(Component.text(rank.name.replace("§[0-9a-fk-or]".toRegex(), ""))
                        .decoration(TextDecoration.ITALIC, false))
                    lore(listOf(
                        Component.text("Weight: ").color(NamedTextColor.GRAY)
                            .append(Component.text("${rank.weight}").color(NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false),
                        Component.text("Permissions: ").color(NamedTextColor.GRAY)
                            .append(Component.text("${rank.permissions.size}").color(NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Click to select this rank")
                            .color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
                    ))
                }
            })
        }

        inv.setItem(49, ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("« Back")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false))
            }
        })

        openGuiSafely(
            viewer,
            inv,
            GuiSession(
                type = GuiType.GIVE_RANK,
                target = target.uniqueId
            )
        )
    }

    private fun openDurationMenu(viewer: Player, target: Player, selectedRank: String) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Select Duration")
            .color(NamedTextColor.DARK_PURPLE)
            .decorate(TextDecoration.BOLD))

        // Permanent
        inv.setItem(10, ItemStack(Material.NETHER_STAR).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("Permanent")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false))
                lore(listOf(
                    Component.text("This rank will never expire")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                ))
            }
        })

        // Durations
        val durations = mapOf(
            12 to Pair("1 Hour", TimeUnit.HOURS.toMillis(1)),
            13 to Pair("1 Day", TimeUnit.DAYS.toMillis(1)),
            14 to Pair("7 Days", TimeUnit.DAYS.toMillis(7)),
            15 to Pair("30 Days", TimeUnit.DAYS.toMillis(30)),
            16 to Pair("90 Days", TimeUnit.DAYS.toMillis(90))
        )
        durations.forEach { (slot, data) ->
            inv.setItem(slot, ItemStack(Material.CLOCK).apply {
                itemMeta = itemMeta.apply {
                    displayName(Component.text(data.first)
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false))
                    lore(listOf(
                        Component.text("Rank will expire after this time")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                    ))
                }
            })
        }

        // Back
        inv.setItem(22, ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("« Back")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false))
            }
        })

        openGuiSafely(
            viewer,
            inv,
            GuiSession(
                type = GuiType.DURATION,
                target = target.uniqueId,
                selectedRank = selectedRank
            )
        )
    }

    private fun openConfirmationMenu(viewer: Player, target: Player, selectedRank: String, duration: Long?) {
        val inv = Bukkit.createInventory(null, 27, Component.text("Confirm Grant")
            .color(NamedTextColor.DARK_PURPLE)
            .decorate(TextDecoration.BOLD))

        val rank = rankManager.getRank(selectedRank) ?: run {
            viewer.closeInventory()
            return
        }

        val durationComponent = if (duration == null) {
            Component.text("Permanent").color(NamedTextColor.GREEN)
        } else {
            Component.text(formatDuration(duration)).color(NamedTextColor.YELLOW)
        }

        // Summary
        inv.setItem(13, ItemStack(Material.PAPER).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("Grant Summary")
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false))
                lore(listOf(
                    Component.text("Player: ").color(NamedTextColor.GRAY)
                        .append(Component.text(target.name).color(NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("Rank: ").color(NamedTextColor.GRAY)
                        .append(Component.text(rank.name.replace("§[0-9a-fk-or]".toRegex(), "")))
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("Duration: ").color(NamedTextColor.GRAY)
                        .append(durationComponent)
                        .decoration(TextDecoration.ITALIC, false)
                ))
            }
        })

        // Confirm / Cancel
        inv.setItem(11, ItemStack(Material.LIME_WOOL).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("CONFIRM")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false))
                lore(listOf(
                    Component.text("Click to grant this rank")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                ))
            }
        })
        inv.setItem(15, ItemStack(Material.RED_WOOL).apply {
            itemMeta = itemMeta.apply {
                displayName(Component.text("CANCEL")
                    .color(NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false))
                lore(listOf(
                    Component.text("Click to cancel")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                ))
            }
        })

        openGuiSafely(
            viewer,
            inv,
            GuiSession(
                type = GuiType.CONFIRMATION,
                target = target.uniqueId,
                selectedRank = selectedRank,
                selectedDuration = duration
            )
        )
    }

    // ----- EVENTS -----

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = activeGuis[player.uniqueId] ?: return

        val topInv = event.view.topInventory
        val clickedInv = event.clickedInventory ?: return
        val clickedTop = clickedInv == topInv

        if (clickedTop) {
            // Block interactions with the GUI itself
            event.isCancelled = true

            val clickedItem = event.currentItem ?: return
            if (clickedItem.type == Material.AIR) return

            val target = Bukkit.getPlayer(session.target)
            if (target == null) {
                player.closeInventory()
                player.sendMiniMessage(messagesManager.getFormattedMessage("general.player-not-online", "player" to "target"))
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
        } else {
            // Player inventory: allow normal clicks, but block attempts to push into GUI
            when (event.click) {
                ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.NUMBER_KEY -> event.isCancelled = true
                else -> Unit
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (activeGuis[player.uniqueId] == null) return
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
        activeGuis.remove(uuid)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        switchingMenu.remove(uuid)
        activeGuis.remove(uuid)
    }

    // ----- CLICK HANDLERS -----

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
                    val rankName = item.itemMeta?.displayName()?.let {
                        (it as? net.kyori.adventure.text.TextComponent)?.content()
                    } ?: return
                    if (rankManager.removePlayerRank(target, rankName)) {
                        permissionManager.updatePlayerPermissions(target)
                        permissionManager.updatePlayerDisplayNames()

                        viewer.sendMiniMessage(
                            messagesManager.getFormattedMessage(
                                "rank.remove-success",
                                "player" to target.name,
                                "rank" to rankName
                            )
                        )
                        target.sendMiniMessage(
                            messagesManager.getFormattedMessage(
                                "rank.remove-notification",
                                "rank" to rankName
                            )
                        )
                        openSeeRanksMenu(viewer, target)
                    } else {
                        sendUsage(viewer)
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
                    val rankName = item.itemMeta?.displayName()?.let {
                        (it as? net.kyori.adventure.text.TextComponent)?.content()
                    } ?: return
                    openDurationMenu(viewer, target, rankName)
                }
            }
        }
    }

    private fun handleDurationClick(viewer: Player, target: Player, slot: Int, session: GuiSession) {
        val selectedRank = session.selectedRank ?: return
        when (slot) {
            10 -> openConfirmationMenu(viewer, target, selectedRank, null)
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
            11 -> {
                val expiration = session.selectedDuration?.let { System.currentTimeMillis() + it }
                if (rankManager.setPlayerRank(target, selectedRank, expiration)) {
                    permissionManager.updatePlayerPermissions(target)
                    permissionManager.updatePlayerDisplayNames()

                    val rank = rankManager.getRank(selectedRank)
                    viewer.sendMiniMessage(
                        messagesManager.getFormattedMessage(
                            "rank.set-success",
                            "player" to target.name,
                            "rank" to rank?.name!!
                        )
                    )
                    target.sendMiniMessage(
                        messagesManager.getFormattedMessage(
                            "rank.set-notification",
                            "rank" to rank.name
                        )
                    )

                    viewer.closeInventory()
                    activeGuis.remove(viewer.uniqueId)
                } else {
                    sendUsage(viewer)
                    viewer.closeInventory()
                    activeGuis.remove(viewer.uniqueId)
                }
            }
            15 -> {
                viewer.closeInventory()
                activeGuis.remove(viewer.uniqueId)
            }
        }
    }

    // ----- UTILS -----

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