package dev.aledlb.pulse.shop

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

data class ShopItem(
    val id: String,
    val name: String,
    val description: List<String>,
    val price: Double,
    val material: Material,
    val category: String,
    val type: ShopItemType,
    val data: Map<String, Any> = emptyMap(),
    val permission: String? = null,
    val enabled: Boolean = true
) {
    fun createItemStack(): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        val serializer = LegacyComponentSerializer.legacySection()

        meta?.displayName(serializer.deserialize("§r$name"))

        val lore = mutableListOf<Component>()
        lore.addAll(description.map { serializer.deserialize("§7$it") })
        lore.add(Component.empty())
        lore.add(serializer.deserialize("§ePrice: §a${formatPrice(price)}"))

        if (permission != null) {
            lore.add(serializer.deserialize("§7Requires: §c$permission"))
        }

        lore.add(Component.empty())
        lore.add(serializer.deserialize("§aClick to purchase!"))

        meta?.lore(lore)
        item.itemMeta = meta

        return item
    }

    private fun formatPrice(price: Double): String {
        return if (price == price.toLong().toDouble()) {
            "⛁${price.toLong()}"
        } else {
            "⛁${"%.2f".format(price)}"
        }
    }
}

enum class ShopItemType {
    RANK,
    TAG,
    ITEM,
    COMMAND,
    PERMISSION
}