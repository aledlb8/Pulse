package dev.aledlb.pulse.shop

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.economy.EconomyManager
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import dev.aledlb.pulse.util.Logger
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

class ShopManager(
    private val economyManager: EconomyManager,
    private val rankManager: RankManager,
    private val permissionManager: PermissionManager
) {
    private val shopItems = mutableMapOf<String, ShopItem>()
    private val categories = mutableMapOf<String, ShopCategory>()
    private var shopEnabled = true

    fun initialize() {
        if (!economyManager.isEnabled()) {
            Logger.warn("Shop system disabled (Economy requires Vault)")
            return
        }

        loadShopConfig()

        if (!shopEnabled) {
            Logger.warn("Shop system disabled in configuration")
            return
        }

        Logger.success("Shop system initialized with ${shopItems.size} items")
    }

    fun isEnabled(): Boolean = economyManager.isEnabled() && shopEnabled

    private fun loadShopConfig() {
        val configManager = Pulse.getPlugin().configManager
        val shopConfig = configManager.getConfig("shop.yml")

        if (shopConfig == null) {
            Logger.warn("Shop configuration not found")
            shopEnabled = false
            return
        }

        // Check if shop is enabled
        shopEnabled = shopConfig.node("enabled").getBoolean(true)

        if (!shopEnabled) {
            Logger.info("Shop system is disabled in config")
            return
        }

        // Clear existing items
        shopItems.clear()
        categories.clear()

        // Load categories
        val categoriesNode = shopConfig.node("categories")
        for (categoryKey in categoriesNode.childrenMap().keys) {
            try {
                val categoryNode = categoriesNode.node(categoryKey.toString())
                val displayName = categoryNode.node("display-name").getString(categoryKey.toString()) ?: categoryKey.toString()
                val iconMaterialName = categoryNode.node("icon").getString("CHEST") ?: "CHEST"
                val iconMaterial = try {
                    Material.valueOf(iconMaterialName)
                } catch (e: IllegalArgumentException) {
                    Logger.warn("Invalid material '$iconMaterialName' for category '$categoryKey', using CHEST")
                    Material.CHEST
                }

                val size = categoryNode.node("size").getInt(54).let {
                    when {
                        it <= 9 -> 9
                        it <= 18 -> 18
                        it <= 27 -> 27
                        it <= 36 -> 36
                        it <= 45 -> 45
                        else -> 54
                    }
                }

                val fillMaterialName = categoryNode.node("fill-material").getString()
                val fillMaterial = fillMaterialName?.let {
                    try {
                        Material.valueOf(it)
                    } catch (e: IllegalArgumentException) {
                        Logger.warn("Invalid fill material '$it' for category '$categoryKey', using null")
                        null
                    }
                }

                val fillSlots = try {
                    categoryNode.node("fill-slots").getList(Integer::class.java)?.map { it.toInt() }
                } catch (e: Exception) {
                    null
                }

                categories[categoryKey.toString()] = ShopCategory(
                    id = categoryKey.toString(),
                    displayName = displayName,
                    icon = iconMaterial,
                    size = size,
                    fillMaterial = fillMaterial,
                    fillSlots = fillSlots
                )
                Logger.debug("Loaded category: $categoryKey")
            } catch (e: Exception) {
                Logger.error("Failed to load category '$categoryKey'", e)
            }
        }

        // Load items
        val itemsNode = shopConfig.node("items")
        for (itemKey in itemsNode.childrenMap().keys) {
            try {
                val itemNode = itemsNode.node(itemKey.toString())

                val category = itemNode.node("category").getString("misc") ?: "misc"
                val materialName = itemNode.node("material").getString("STONE") ?: "STONE"
                val material = try {
                    Material.valueOf(materialName)
                } catch (e: IllegalArgumentException) {
                    Logger.warn("Invalid material '$materialName' for item '$itemKey', using STONE")
                    Material.STONE
                }

                val name = itemNode.node("name").getString(itemKey.toString()) ?: itemKey.toString()
                val price = itemNode.node("price").getDouble(0.0)
                val description = itemNode.node("description").getList(String::class.java) ?: emptyList()
                val permission = itemNode.node("permission").getString()
                val enabled = itemNode.node("enabled").getBoolean(true)

                // Determine item type
                val type = when {
                    itemNode.node("rank").getString() != null -> ShopItemType.RANK
                    itemNode.node("tag").getString() != null -> ShopItemType.TAG
                    itemNode.node("command").getString() != null -> ShopItemType.COMMAND
                    itemNode.node("give-permission").getString() != null -> ShopItemType.PERMISSION
                    else -> ShopItemType.ITEM
                }

                // Build data map
                val data = mutableMapOf<String, Any>()
                itemNode.node("rank").getString()?.let { data["rank"] = it }
                itemNode.node("tag").getString()?.let { data["tag"] = it }
                itemNode.node("command").getString()?.let { data["command"] = it }
                itemNode.node("give-permission").getString()?.let { data["give-permission"] = it }
                itemNode.node("amount").getInt(1).let { data["amount"] = it }

                val slot = itemNode.node("slot").getInt(-1).takeIf { it >= 0 }
                val quantities = try {
                    val rawList = itemNode.node("quantities").getList(Integer::class.java)?.map { it.toInt() }?.filter { it > 0 }
                    if (rawList.isNullOrEmpty()) listOf(1) else rawList
                } catch (e: Exception) {
                    listOf(1)
                }

                val shopItem = ShopItem(
                    id = itemKey.toString(),
                    name = name,
                    description = description,
                    price = price,
                    material = material,
                    category = category,
                    type = type,
                    data = data,
                    permission = permission,
                    enabled = enabled,
                    slot = slot,
                    quantities = quantities
                )

                shopItems[itemKey.toString()] = shopItem
                Logger.debug("Loaded shop item: $itemKey (type: $type, price: $price)")
            } catch (e: Exception) {
                Logger.error("Failed to load shop item '$itemKey'", e)
            }
        }

        Logger.info("Loaded ${shopItems.size} shop items and ${categories.size} categories")
    }

    fun getShopItems(): Collection<ShopItem> = shopItems.values.filter { it.enabled }

    fun getShopItem(id: String): ShopItem? = shopItems[id]

    fun getItemsByCategory(category: String): List<ShopItem> {
        return shopItems.values.filter { it.category == category && it.enabled }
    }

    fun getCategories(): Collection<ShopCategory> = categories.values

    fun getCategory(id: String): ShopCategory? = categories[id]

    fun purchaseItem(player: Player, itemId: String, quantity: Int = 1): PurchaseResult {
        val item = shopItems[itemId] ?: return PurchaseResult.ITEM_NOT_FOUND

        if (!item.enabled) {
            return PurchaseResult.ITEM_DISABLED
        }

        // Validate quantity (if no quantities configured, allow quantity 1)
        val allowedQuantities = if (item.quantities.isEmpty()) listOf(1) else item.quantities
        if (!allowedQuantities.contains(quantity)) {
            return PurchaseResult.INVALID_QUANTITY
        }

        // Check permission
        if (item.permission != null && !player.hasPermission(item.permission)) {
            return PurchaseResult.NO_PERMISSION
        }

        // Check if player already has this item (for ranks and tags)
        if (item.type == ShopItemType.RANK) {
            val targetRank = item.data["rank"] as? String ?: return PurchaseResult.INVALID_ITEM
            val playerRank = rankManager.getPlayerRank(player)

            if (playerRank?.name?.equals(targetRank, true) == true) {
                return PurchaseResult.ALREADY_OWNED
            }

            // Check if target rank exists
            if (rankManager.getRank(targetRank) == null) {
                return PurchaseResult.INVALID_ITEM
            }
        }

        if (item.type == ShopItemType.TAG) {
            val targetTag = item.data["tag"] as? String ?: return PurchaseResult.INVALID_ITEM
            val tagManager = Pulse.getPlugin().tagManager

            // Check if tag exists
            if (tagManager.getTag(targetTag) == null) {
                return PurchaseResult.INVALID_ITEM
            }

            // Check if player already owns this tag
            val playerTagData = tagManager.getPlayerTagData(player)
            if (playerTagData.hasTag(targetTag)) {
                return PurchaseResult.ALREADY_OWNED
            }
        }

        // Calculate total price
        val totalPrice = item.price * quantity

        // Check balance
        if (!economyManager.hasBalance(player, totalPrice)) {
            return PurchaseResult.INSUFFICIENT_FUNDS
        }

        // Process purchase
        val success = economyManager.removeBalance(player, totalPrice)
        if (!success) {
            return PurchaseResult.TRANSACTION_FAILED
        }

        // Execute item effect
        val executeResult = executeItemEffect(player, item, quantity)
        if (!executeResult) {
            // Refund on failure
            economyManager.addBalance(player, totalPrice)
            Logger.warn("Failed to execute item effect for ${item.id} (type: ${item.type}) for player ${player.name}")
            return PurchaseResult.EXECUTION_FAILED
        }

        // Log purchase
        Logger.info("${player.name} purchased ${item.name} x$quantity for ${economyManager.formatBalance(totalPrice)}")

        return PurchaseResult.SUCCESS
    }

    private fun executeItemEffect(player: Player, item: ShopItem, quantity: Int = 1): Boolean {
        return when (item.type) {
            ShopItemType.RANK -> {
                val targetRank = item.data["rank"] as? String ?: return false
                permissionManager.setPlayerRank(player, targetRank)
            }

            ShopItemType.TAG -> {
                val targetTag = item.data["tag"] as? String ?: return false
                val tagManager = Pulse.getPlugin().tagManager
                tagManager.giveTagToPlayer(player, targetTag)
            }

            ShopItemType.ITEM -> {
                val amount = (item.data["amount"] as? Int ?: 1) * quantity
                val itemStack = ItemStack(item.material, amount)

                // Try to add to inventory, drop if full
                val leftover = player.inventory.addItem(itemStack)
                if (leftover.isNotEmpty()) {
                    leftover.values.forEach { leftoverItem ->
                        player.world.dropItem(player.location, leftoverItem)
                    }
                }
                true
            }

            ShopItemType.COMMAND -> {
                val command = item.data["command"] as? String ?: return false
                val processedCommand = command
                    .replace("{player}", player.name)
                    .replace("{uuid}", player.uniqueId.toString())
                    .replace("{quantity}", quantity.toString())

                // Execute command on global region scheduler for Folia compatibility
                Bukkit.getGlobalRegionScheduler().run(Pulse.getPlugin(), { _ ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
                })
                true
            }

            ShopItemType.PERMISSION -> {
                val permission = item.data["give-permission"] as? String ?: return false
                permissionManager.addPlayerPermission(player, permission)
                true
            }
        }
    }

    fun reload() {
        loadShopConfig()
    }

    enum class PurchaseResult {
        SUCCESS,
        ITEM_NOT_FOUND,
        ITEM_DISABLED,
        NO_PERMISSION,
        INSUFFICIENT_FUNDS,
        ALREADY_OWNED,
        INVALID_ITEM,
        INVALID_QUANTITY,
        TRANSACTION_FAILED,
        EXECUTION_FAILED
    }
}

data class ShopCategory(
    val id: String,
    val displayName: String,
    val icon: Material,
    val size: Int = 54,
    val fillMaterial: Material? = null,
    val fillSlots: List<Int>? = null
)