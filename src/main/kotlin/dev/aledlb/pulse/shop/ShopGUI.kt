package dev.aledlb.pulse.shop

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.economy.EconomyManager
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
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

class ShopGUI(
    private val shopManager: ShopManager,
    private val economyManager: EconomyManager
) : Listener {

    private val openGuis = mutableMapOf<UUID, ShopSession>()
    private val switchingMenu = mutableSetOf<UUID>()
    private val messagesManager get() = Pulse.getPlugin().messagesManager

    // ---------- Public API ----------

    fun openMainShop(player: Player) {
        val categories = shopManager.getCategories().toList()
        val serializer = LegacyComponentSerializer.legacySection()

        val inventory = Bukkit.createInventory(null, 54, serializer.deserialize("§5§lShop"))

        // Center categories in rows 2-3 (slots 18-35)
        val slots = when {
            categories.size <= 5 -> {
                // Single row centered (row 2)
                val startSlot = 20 - (categories.size / 2)
                (0 until categories.size).map { startSlot + it }
            }
            categories.size <= 9 -> {
                // Single row (row 2)
                (0 until categories.size).map { 18 + it }
            }
            else -> {
                // Two rows (rows 2-3), up to 18 categories
                val firstRowCount = minOf(9, categories.size)
                val secondRowCount = categories.size - firstRowCount
                val firstRow = (0 until firstRowCount).map { 18 + it }
                val secondRow = (0 until secondRowCount).map { 27 + it }
                firstRow + secondRow
            }
        }

        // Add categories
        categories.forEachIndexed { index, category ->
            val categoryItem = createCategoryItem(category)
            inventory.setItem(slots[index], categoryItem)
        }

        // Add player info item (bottom right)
        val playerInfo = createPlayerInfoItem(player)
        inventory.setItem(53, playerInfo)

        // Add decorative glass
        addGlassDecoration(inventory)

        openGuiSafely(
            player,
            inventory,
            ShopSession(ShopView.MAIN, null, null, slots)
        )
    }

    fun openCategoryShop(player: Player, category: String) {
        val items = shopManager.getItemsByCategory(category)
        val categoryObj = shopManager.getCategory(category)

        if (items.isEmpty()) {
            player.sendMiniMessage(messagesManager.getFormattedMessage("shop.category-empty"))
            return
        }

        val serializer = LegacyComponentSerializer.legacySection()

        // Show up to 21 items (3 rows of 7, with 1-block border)
        val maxItemsPerPage = 21
        val itemsToShow = items.take(maxItemsPerPage)
        val inventory = Bukkit.createInventory(null, 54, serializer.deserialize("§5§l${categoryObj?.displayName ?: category}"))

        // Items in rows 2-4, columns 1-7 (with 1-block border)
        val slots = mutableListOf<Int>()
        for (row in 2..4) {
            for (col in 1..7) {
                slots.add(row * 9 + col)
            }
        }

        // Add items in grid layout
        itemsToShow.forEachIndexed { index, item ->
            val shopItem = item.createItemStack()
            inventory.setItem(slots[index], shopItem)
        }

        // Back button (bottom-left)
        val backButton = ItemStack(Material.ARROW).apply {
            val meta = itemMeta
            meta.displayName(serializer.deserialize("§cBack to Main Shop"))
            meta.lore(listOf(serializer.deserialize("§7Click to go back")))
            itemMeta = meta
        }
        inventory.setItem(45, backButton)

        // Player info (bottom-right)
        val playerInfo = createPlayerInfoItem(player)
        inventory.setItem(53, playerInfo)

        // Add decorative glass
        addGlassDecoration(inventory)

        openGuiSafely(
            player,
            inventory,
            ShopSession(ShopView.CATEGORY, category, items.associateBy { it.id }, slots)
        )
    }

    fun closeShop(player: Player) {
        openGuis.remove(player.uniqueId)
    }

    // ---------- Internal helpers ----------

    private fun openGuiSafely(player: Player, inv: Inventory, session: ShopSession) {
        val uuid = player.uniqueId
        switchingMenu.add(uuid)
        player.openInventory(inv)
        openGuis[uuid] = session
        switchingMenu.remove(uuid)
    }

    private fun createCategoryItem(category: ShopCategory): ItemStack {
        val item = ItemStack(category.icon)
        val meta = item.itemMeta
        val serializer = LegacyComponentSerializer.legacySection()

        meta.displayName(serializer.deserialize("§e${category.displayName}"))

        val itemCount = shopManager.getItemsByCategory(category.id).size
        meta.lore(listOf(
            serializer.deserialize("§7Browse ${category.displayName.lowercase()}"),
            serializer.deserialize("§7Items: §a$itemCount"),
            Component.empty(),
            serializer.deserialize("§aClick to browse!")
        ))

        item.itemMeta = meta
        return item
    }

    private fun createPlayerInfoItem(player: Player): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta
        val serializer = LegacyComponentSerializer.legacySection()

        meta.displayName(serializer.deserialize("§e${player.name}"))

        val balance = economyManager.getBalance(player)
        val formattedBalance = economyManager.formatBalance(balance)

        meta.lore(listOf(
            serializer.deserialize("§7Your Balance: §a$formattedBalance"),
            Component.empty(),
            serializer.deserialize("§7Currency: ${economyManager.getCurrencyName()}")
        ))

        item.itemMeta = meta
        return item
    }

    private fun addGlassDecoration(inventory: Inventory) {
        val glass = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val glassMeta = glass.itemMeta
        glassMeta.displayName(Component.text(" "))
        glass.itemMeta = glassMeta

        for (i in inventory.contents.indices) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass)
            }
        }
    }

    /**
     * Calculate centered slot positions with 1 empty slot gap between items
     * @param itemCount Number of items to place
     * @param maxSlots Maximum slots available for placement (9..45)
     */
    private fun calculateCenteredSlots(itemCount: Int, maxSlots: Int): List<Int> {
        if (itemCount == 0) return emptyList()

        val slotsPerRow = 9
        val maxRows = maxSlots / slotsPerRow

        // Each row with gaps can hold up to 5 items (positions 0,2,4,6,8)
        val itemsPerRow = (slotsPerRow + 1) / 2 // 5

        val slots = mutableListOf<Int>()
        var remaining = itemCount
        var row = 0

        while (remaining > 0 && row < maxRows) {
            val countThisRow = minOf(remaining, itemsPerRow)
            val widthWithGaps = countThisRow + (countThisRow - 1)
            val startCol = (slotsPerRow - widthWithGaps) / 2

            repeat(countThisRow) { i ->
                val col = startCol + (i * 2)
                slots.add(row * slotsPerRow + col)
            }

            remaining -= countThisRow
            row++
        }

        return slots
    }

    // ---------- Events ----------

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = openGuis[player.uniqueId] ?: return

        val topInv = event.view.topInventory
        val clickedInv = event.clickedInventory ?: return
        val clickedTop = clickedInv == topInv

        if (clickedTop) {
            // Interacting with our GUI
            event.isCancelled = true

            val clickedItem = event.currentItem ?: return
            if (clickedItem.type == Material.AIR) return

            when (session.view) {
                ShopView.MAIN -> handleMainShopClick(player, session, clickedItem, event.slot)
                ShopView.CATEGORY -> handleCategoryShopClick(player, session, clickedItem, event.slot)
            }
        } else {
            // Player inventory: allow normal clicks but block push attempts into GUI
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

    // ---------- Click handlers ----------

    private fun handleMainShopClick(player: Player, session: ShopSession, clickedItem: ItemStack, slot: Int) {
        if (clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return
        if (clickedItem.type == Material.PLAYER_HEAD) return

        val slots = session.slots ?: return
        val categoryIndex = slots.indexOf(slot)
        if (categoryIndex < 0) return

        val categories = shopManager.getCategories().toList()
        if (categoryIndex >= categories.size) return

        val category = categories[categoryIndex]
        openCategoryShop(player, category.id)
    }

    private fun handleCategoryShopClick(player: Player, session: ShopSession, clickedItem: ItemStack, slot: Int) {
        if (clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return
        if (clickedItem.type == Material.PLAYER_HEAD) return

        // Back button
        if (clickedItem.type == Material.ARROW) {
            openMainShop(player)
            return
        }

        val slots = session.slots ?: return
        val itemIndex = slots.indexOf(slot)
        if (itemIndex < 0) return

        val items = session.items ?: return
        val itemsList = items.values.toList()
        if (itemIndex >= itemsList.size) return

        val shopItem = itemsList[itemIndex]
        handlePurchase(player, shopItem)
    }

    private fun handlePurchase(player: Player, item: ShopItem) {
        val result = shopManager.purchaseItem(player, item.id)

        when (result) {
            ShopManager.PurchaseResult.SUCCESS -> {
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.purchase-success", "item" to item.name))
                player.closeInventory()
                val newBalance = economyManager.formatBalance(economyManager.getBalance(player))
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.new-balance", "balance" to newBalance))
            }
            ShopManager.PurchaseResult.INSUFFICIENT_FUNDS -> {
                val playerBalance = economyManager.formatBalance(economyManager.getBalance(player))
                val itemPrice = economyManager.formatBalance(item.price)
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.insufficient-funds"))
                player.sendMiniMessage(
                    messagesManager.getFormattedMessage(
                        "shop.insufficient-funds-detail",
                        "price" to itemPrice,
                        "balance" to playerBalance
                    )
                )
            }
            ShopManager.PurchaseResult.ALREADY_OWNED -> {
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.already-owned"))
            }
            ShopManager.PurchaseResult.NO_PERMISSION -> {
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.no-permission"))
            }
            ShopManager.PurchaseResult.ITEM_NOT_FOUND -> {
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.item-not-found"))
            }
            ShopManager.PurchaseResult.ITEM_DISABLED -> {
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.item-disabled"))
            }
            else -> {
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.purchase-failed"))
            }
        }
    }

    // ---------- Model ----------

    data class ShopSession(
        val view: ShopView,
        val category: String? = null,
        val items: Map<String, ShopItem>? = null,
        val slots: List<Int>? = null
    )

    enum class ShopView {
        MAIN,
        CATEGORY
    }
}