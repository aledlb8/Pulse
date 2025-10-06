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

        val invSize = categoryObj?.size ?: 54
        val inventory = Bukkit.createInventory(null, invSize, serializer.deserialize("§5§l${categoryObj?.displayName ?: category}"))

        // Map slots to item IDs for easy lookup
        val slotToItemId = mutableMapOf<Int, String>()
        val itemsWithSlots = items.filter { it.slot != null && it.slot in 0 until invSize }
        val itemsWithoutSlots = items.filter { it.slot == null || it.slot !in 0 until invSize }

        // Place items with custom slots
        itemsWithSlots.forEach { item ->
            inventory.setItem(item.slot!!, item.createItemStack())
            slotToItemId[item.slot!!] = item.id
        }

        // Calculate slots for items without custom positions
        if (itemsWithoutSlots.isNotEmpty()) {
            val usedSlots = itemsWithSlots.map { it.slot!! }.toMutableSet()
            usedSlots.add(invSize - 9) // Reserve bottom left for back button
            usedSlots.add(invSize - 1) // Reserve bottom right for player info

            // Find available slots (excluding bottom row and already used slots)
            val availableSlots = (9 until invSize - 9).filter { it !in usedSlots }
            itemsWithoutSlots.take(availableSlots.size).forEachIndexed { index, item ->
                val slot = availableSlots[index]
                inventory.setItem(slot, item.createItemStack())
                slotToItemId[slot] = item.id
            }
        }

        // Back button (bottom-left)
        val backButton = ItemStack(Material.ARROW).apply {
            val meta = itemMeta
            meta.displayName(serializer.deserialize("§cBack to Main Shop"))
            meta.lore(listOf(serializer.deserialize("§7Click to go back")))
            itemMeta = meta
        }
        inventory.setItem(invSize - 9, backButton)

        // Player info (bottom-right)
        val playerInfo = createPlayerInfoItem(player)
        inventory.setItem(invSize - 1, playerInfo)

        // Add fill material or glass decoration
        if (categoryObj?.fillMaterial != null) {
            applyFillMaterial(inventory, categoryObj.fillMaterial, categoryObj.fillSlots, invSize)
        } else {
            addGlassDecoration(inventory)
        }

        openGuiSafely(
            player,
            inventory,
            ShopSession(ShopView.CATEGORY, category, items.associateBy { it.id }, slotToItemId = slotToItemId)
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

    private fun applyFillMaterial(inventory: Inventory, fillMaterial: Material, fillSlots: List<Int>?, invSize: Int) {
        val filler = ItemStack(fillMaterial)
        val fillerMeta = filler.itemMeta
        fillerMeta.displayName(Component.text(" "))
        filler.itemMeta = fillerMeta

        if (fillSlots != null) {
            // Fill specific slots
            fillSlots.filter { it in 0 until invSize }.forEach { slot ->
                if (inventory.getItem(slot) == null) {
                    inventory.setItem(slot, filler)
                }
            }
        } else {
            // Fill all empty slots
            for (i in inventory.contents.indices) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler)
                }
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
                ShopView.QUANTITY -> handleQuantitySelectionClick(player, session, clickedItem, event.slot)
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
        if (clickedItem.type == Material.BLACK_STAINED_GLASS_PANE) return
        if (clickedItem.type == Material.PURPLE_STAINED_GLASS_PANE) return
        if (clickedItem.type == Material.PLAYER_HEAD) return

        // Back button
        if (clickedItem.type == Material.ARROW) {
            openMainShop(player)
            return
        }

        // Find clicked item using slot-to-item mapping
        val itemId = session.slotToItemId?.get(slot) ?: return
        val shopItem = session.items?.get(itemId) ?: return

        // Check if item has multiple quantities
        if (shopItem.quantities.size > 1) {
            openQuantitySelection(player, shopItem, session.category ?: "")
        } else {
            handlePurchase(player, shopItem, shopItem.quantities.firstOrNull() ?: 1, keepOpen = true)
        }
    }

    fun openQuantitySelection(player: Player, item: ShopItem, category: String) {
        val serializer = LegacyComponentSerializer.legacySection()
        val inventory = Bukkit.createInventory(null, 27, serializer.deserialize("§5§lSelect Quantity"))

        // Center item display
        val centerItem = item.createItemStack()
        inventory.setItem(13, centerItem)

        // Quantity selection slots using green glass panes
        val slots = mutableListOf<Int>()
        val availableSlots = listOf(10, 11, 12, 14, 15, 16) // Around center, max 6 quantities

        item.quantities.take(availableSlots.size).forEachIndexed { index, quantity ->
            val slot = availableSlots[index]
            val quantityGlass = ItemStack(Material.LIME_STAINED_GLASS_PANE)
            val meta = quantityGlass.itemMeta

            val totalPrice = item.price * quantity
            val formattedPrice = economyManager.formatBalance(totalPrice)

            meta?.displayName(serializer.deserialize("§a§lBuy ${quantity}x"))
            meta?.lore(listOf(
                Component.empty(),
                serializer.deserialize("§7Total Price: §e$formattedPrice"),
                Component.empty(),
                serializer.deserialize("§aClick to purchase!")
            ))
            quantityGlass.itemMeta = meta

            inventory.setItem(slot, quantityGlass)
            slots.add(slot)
        }

        // Back button
        val backButton = ItemStack(Material.ARROW).apply {
            val meta = itemMeta
            meta.displayName(serializer.deserialize("§c§lBack"))
            meta.lore(listOf(serializer.deserialize("§7Return to shop")))
            itemMeta = meta
        }
        inventory.setItem(22, backButton)

        // Fill with black glass
        val blackGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val glassMeta = blackGlass.itemMeta
        glassMeta.displayName(Component.text(" "))
        blackGlass.itemMeta = glassMeta

        for (i in inventory.contents.indices) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, blackGlass)
            }
        }

        openGuiSafely(
            player,
            inventory,
            ShopSession(ShopView.QUANTITY, category, mapOf(item.id to item), slots, item.id)
        )
    }

    private fun handlePurchase(player: Player, item: ShopItem, quantity: Int = 1, keepOpen: Boolean = false) {
        val result = shopManager.purchaseItem(player, item.id, quantity)

        when (result) {
            ShopManager.PurchaseResult.SUCCESS -> {
                val itemNameStripped = item.name.replace("§[0-9a-fk-or]".toRegex(), "")
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.purchase-success", "item" to itemNameStripped))
                if (!keepOpen) {
                    player.closeInventory()
                }
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
            ShopManager.PurchaseResult.INVALID_QUANTITY -> {
                player.sendMiniMessage("<red>Invalid quantity selected!")
            }
            ShopManager.PurchaseResult.INVALID_ITEM -> {
                player.sendMiniMessage("<red>This item is incorrectly configured. Please contact an administrator.")
            }
            ShopManager.PurchaseResult.TRANSACTION_FAILED -> {
                player.sendMiniMessage("<red>Transaction failed. Your balance was not charged.")
            }
            ShopManager.PurchaseResult.EXECUTION_FAILED -> {
                player.sendMiniMessage("<red>Failed to deliver the item. Your coins have been refunded.")
            }
            else -> {
                player.sendMiniMessage(messagesManager.getFormattedMessage("shop.purchase-failed"))
            }
        }
    }

    // ---------- Model ----------

    private fun handleQuantitySelectionClick(player: Player, session: ShopSession, clickedItem: ItemStack, slot: Int) {
        if (clickedItem.type == Material.BLACK_STAINED_GLASS_PANE) return

        // Center item - do nothing
        if (slot == 13) return

        // Back button
        if (clickedItem.type == Material.ARROW) {
            openCategoryShop(player, session.category ?: return)
            return
        }

        // Check if it's a quantity glass pane
        if (clickedItem.type != Material.LIME_STAINED_GLASS_PANE) return

        val slots = session.slots ?: return
        val quantityIndex = slots.indexOf(slot)
        if (quantityIndex < 0) return

        val itemId = session.selectedItemId ?: return
        val item = session.items?.get(itemId) ?: return

        val quantity = item.quantities.getOrNull(quantityIndex) ?: return
        handlePurchase(player, item, quantity, keepOpen = true)
    }

    data class ShopSession(
        val view: ShopView,
        val category: String? = null,
        val items: Map<String, ShopItem>? = null,
        val slots: List<Int>? = null,
        val selectedItemId: String? = null,
        val slotToItemId: Map<Int, String>? = null
    )

    enum class ShopView {
        MAIN,
        CATEGORY,
        QUANTITY
    }
}