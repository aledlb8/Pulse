package dev.aledlb.pulse.shop

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.economy.EconomyManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

class ShopGUI(
    private val shopManager: ShopManager,
    private val economyManager: EconomyManager
) : Listener {

    private val openGuis = mutableMapOf<UUID, ShopSession>()
    private val messagesManager get() = Pulse.getPlugin().messagesManager

    fun openMainShop(player: Player) {
        val categories = shopManager.getCategories().toList()
        val serializer = LegacyComponentSerializer.legacySection()

        val inventory = Bukkit.createInventory(null, 54, serializer.deserialize("§5§lShop"))

        // Calculate centered positions with 1 slot gap
        val slots = calculateCenteredSlots(categories.size, 9)

        // Add categories
        categories.forEachIndexed { index, category ->
            val categoryItem = createCategoryItem(category)
            inventory.setItem(slots[index], categoryItem)
        }

        // Add player info item
        val playerInfo = createPlayerInfoItem(player)
        inventory.setItem(49, playerInfo)

        // Add decorative glass
        addGlassDecoration(inventory)

        openGuis[player.uniqueId] = ShopSession(ShopView.MAIN, null, null, slots)
        player.openInventory(inventory)
    }

    fun openCategoryShop(player: Player, category: String) {
        val items = shopManager.getItemsByCategory(category)
        val categoryObj = shopManager.getCategory(category)

        if (items.isEmpty()) {
            player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.category-empty"))
            return
        }

        val serializer = LegacyComponentSerializer.legacySection()

        // Calculate inventory size (minimum 27, maximum 54)
        val maxItemsPerPage = 28 // Leave space for navigation
        val itemsToShow = items.take(maxItemsPerPage)
        val inventory = Bukkit.createInventory(null, 54, serializer.deserialize("§5§l${categoryObj?.displayName ?: category}"))

        // Calculate centered positions with 1 slot gap (use full 54 slots minus bottom row)
        val slots = calculateCenteredSlots(itemsToShow.size, 45)

        // Add items at calculated positions
        itemsToShow.forEachIndexed { index, item ->
            val shopItem = item.createItemStack()
            inventory.setItem(slots[index], shopItem)
        }

        // Add back button
        val backButton = ItemStack(Material.ARROW)
        val backMeta = backButton.itemMeta
        backMeta?.displayName(serializer.deserialize("§cBack to Main Shop"))
        backMeta?.lore(listOf(
            serializer.deserialize("§7Click to go back")
        ))
        backButton.itemMeta = backMeta
        inventory.setItem(45, backButton)

        // Add player info
        val playerInfo = createPlayerInfoItem(player)
        inventory.setItem(53, playerInfo)

        // Add decorative glass
        addGlassDecoration(inventory)

        openGuis[player.uniqueId] = ShopSession(ShopView.CATEGORY, category, items.associateBy { it.id }, slots)
        player.openInventory(inventory)
    }

    private fun createCategoryItem(category: ShopCategory): ItemStack {
        val item = ItemStack(category.icon)
        val meta = item.itemMeta
        val serializer = LegacyComponentSerializer.legacySection()

        meta?.displayName(serializer.deserialize("§e${category.displayName}"))

        val itemCount = shopManager.getItemsByCategory(category.id).size
        meta?.lore(listOf(
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

        meta?.displayName(serializer.deserialize("§e${player.name}"))

        val balance = economyManager.getBalance(player)
        val formattedBalance = economyManager.formatBalance(balance)

        meta?.lore(listOf(
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
        glassMeta?.displayName(Component.text(" "))
        glass.itemMeta = glassMeta

        // Fill empty slots with glass
        for (i in inventory.contents.indices) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass)
            }
        }
    }

    /**
     * Calculate centered slot positions with 1 empty slot gap between items
     * @param itemCount Number of items to place
     * @param maxSlots Maximum slots available (usually 9, 18, 27, 36, or 45)
     * @return List of slot positions
     */
    private fun calculateCenteredSlots(itemCount: Int, maxSlots: Int): List<Int> {
        if (itemCount == 0) return emptyList()

        val slotsPerRow = 9
        val maxRows = maxSlots / slotsPerRow

        // Calculate total width needed: items + gaps between them (item count - 1 gaps)
        val totalWidth = itemCount + (itemCount - 1) // Each item takes 1 slot + 1 gap

        val slots = mutableListOf<Int>()

        if (totalWidth <= slotsPerRow) {
            // All items fit in one row - center horizontally
            val startCol = (slotsPerRow - totalWidth) / 2
            val row = maxRows / 2 // Middle row

            for (i in 0 until itemCount) {
                val col = startCol + (i * 2) // i * 2 accounts for item + gap
                slots.add(row * slotsPerRow + col)
            }
        } else {
            // Items span multiple rows
            // Calculate how many items per row (with gaps)
            val itemsPerRow = (slotsPerRow + 1) / 2 // Max items that fit with gaps: floor((9 + 1) / 2) = 5

            var remainingItems = itemCount
            var currentRow = 0

            while (remainingItems > 0 && currentRow < maxRows) {
                val itemsInThisRow = minOf(remainingItems, itemsPerRow)
                val widthNeeded = itemsInThisRow + (itemsInThisRow - 1)
                val startCol = (slotsPerRow - widthNeeded) / 2

                for (i in 0 until itemsInThisRow) {
                    val col = startCol + (i * 2)
                    slots.add(currentRow * slotsPerRow + col)
                }

                remainingItems -= itemsInThisRow
                currentRow++
            }
        }

        return slots
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = openGuis[player.uniqueId] ?: return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val slot = event.slot

        when (session.view) {
            ShopView.MAIN -> handleMainShopClick(player, session, clickedItem, slot)
            ShopView.CATEGORY -> handleCategoryShopClick(player, session, clickedItem, slot)
        }
    }

    private fun handleMainShopClick(player: Player, session: ShopSession, clickedItem: ItemStack, slot: Int) {
        if (clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return
        if (clickedItem.type == Material.PLAYER_HEAD) return

        // Find which category was clicked based on slot positions
        val slots = session.slots ?: return
        val categoryIndex = slots.indexOf(slot)

        if (categoryIndex >= 0) {
            val categories = shopManager.getCategories().toList()
            if (categoryIndex < categories.size) {
                val category = categories[categoryIndex]
                openCategoryShop(player, category.id)
            }
        }
    }

    private fun handleCategoryShopClick(player: Player, session: ShopSession, clickedItem: ItemStack, slot: Int) {
        if (clickedItem.type == Material.GRAY_STAINED_GLASS_PANE) return

        // Check for back button
        if (clickedItem.type == Material.ARROW) {
            openMainShop(player)
            return
        }

        // Check for player info
        if (clickedItem.type == Material.PLAYER_HEAD) return

        // Find which item was clicked based on slot positions
        val slots = session.slots ?: return
        val itemIndex = slots.indexOf(slot)

        if (itemIndex >= 0) {
            val items = session.items ?: return
            val itemsList = items.values.toList()

            if (itemIndex < itemsList.size) {
                val shopItem = itemsList[itemIndex]
                handlePurchase(player, shopItem)
            }
        }
    }

    private fun handlePurchase(player: Player, item: ShopItem) {
        val result = shopManager.purchaseItem(player, item.id)

        when (result) {
            ShopManager.PurchaseResult.SUCCESS -> {
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.purchase-success", "item" to item.name))

                // Close GUI and show updated balance
                player.closeInventory()
                val newBalance = economyManager.formatBalance(economyManager.getBalance(player))
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.new-balance", "balance" to newBalance))
            }

            ShopManager.PurchaseResult.INSUFFICIENT_FUNDS -> {
                val playerBalance = economyManager.formatBalance(economyManager.getBalance(player))
                val itemPrice = economyManager.formatBalance(item.price)
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.insufficient-funds"))
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.insufficient-funds-detail",
                    "price" to itemPrice,
                    "balance" to playerBalance
                ))
            }

            ShopManager.PurchaseResult.ALREADY_OWNED -> {
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.already-owned"))
            }

            ShopManager.PurchaseResult.NO_PERMISSION -> {
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.no-permission"))
            }

            ShopManager.PurchaseResult.ITEM_NOT_FOUND -> {
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.item-not-found"))
            }

            ShopManager.PurchaseResult.ITEM_DISABLED -> {
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.item-disabled"))
            }

            else -> {
                player.sendMessage(messagesManager.getFormattedMessageWithPrefix("shop.purchase-failed"))
            }
        }
    }

    fun closeShop(player: Player) {
        openGuis.remove(player.uniqueId)
    }

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