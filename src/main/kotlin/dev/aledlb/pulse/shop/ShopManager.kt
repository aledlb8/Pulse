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

    fun initialize() {
        if (!economyManager.isEnabled()) {
            Logger.warn("Shop system disabled (Economy requires Vault)")
            return
        }

        loadShopConfig()
        Logger.success("Shop system initialized with ${shopItems.size} items")
    }

    fun isEnabled(): Boolean = economyManager.isEnabled()

    private fun loadShopConfig() {
        val configManager = Pulse.getPlugin().configManager
        val shopConfig = configManager.getConfig("shop.yml")

        if (shopConfig == null) {
            Logger.warn("Shop configuration not found")
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

                categories[categoryKey.toString()] = ShopCategory(
                    id = categoryKey.toString(),
                    displayName = displayName,
                    icon = iconMaterial
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
                    enabled = enabled
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

    fun purchaseItem(player: Player, itemId: String): PurchaseResult {
        val item = shopItems[itemId] ?: return PurchaseResult.ITEM_NOT_FOUND

        if (!item.enabled) {
            return PurchaseResult.ITEM_DISABLED
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

        // Check balance
        if (!economyManager.hasBalance(player, item.price)) {
            return PurchaseResult.INSUFFICIENT_FUNDS
        }

        // Process purchase
        val success = economyManager.removeBalance(player, item.price)
        if (!success) {
            return PurchaseResult.TRANSACTION_FAILED
        }

        // Execute item effect
        val executeResult = executeItemEffect(player, item)
        if (!executeResult) {
            // Refund on failure
            economyManager.addBalance(player, item.price)
            return PurchaseResult.EXECUTION_FAILED
        }

        // Log purchase
        Logger.info("${player.name} purchased ${item.name} for ${economyManager.formatBalance(item.price)}")

        return PurchaseResult.SUCCESS
    }

    private fun executeItemEffect(player: Player, item: ShopItem): Boolean {
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
                val amount = item.data["amount"] as? Int ?: 1
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
        TRANSACTION_FAILED,
        EXECUTION_FAILED
    }
}

data class ShopCategory(
    val id: String,
    val displayName: String,
    val icon: Material
)