package dev.aledlb.pulse.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Helper object for GUI creation and management.
 * Provides common GUI patterns and utilities.
 */
object GUIHelper {
    
    private val serializer = LegacyComponentSerializer.legacySection()
    
    /**
     * Create an inventory with a formatted title
     */
    fun createInventory(title: String, size: Int = 54): Inventory {
        return Bukkit.createInventory(null, size, serializer.deserialize(title))
    }
    
    /**
     * Create a decorative glass pane item
     */
    fun createGlassPane(color: Material = Material.GRAY_STAINED_GLASS_PANE, name: String = " "): ItemStack {
        return ItemStack(color).apply {
            val meta = itemMeta
            meta.displayName(Component.text(name))
            itemMeta = meta
        }
    }
    
    /**
     * Fill inventory borders with glass panes
     */
    fun fillBorders(inventory: Inventory, material: Material = Material.GRAY_STAINED_GLASS_PANE) {
        val size = inventory.size
        val glassPane = createGlassPane(material)
        
        // Top and bottom rows
        for (i in 0 until 9) {
            inventory.setItem(i, glassPane)
            inventory.setItem(size - 9 + i, glassPane)
        }
        
        // Left and right columns
        for (row in 1 until (size / 9) - 1) {
            inventory.setItem(row * 9, glassPane)
            inventory.setItem(row * 9 + 8, glassPane)
        }
    }
    
    /**
     * Create an item with display name and lore
     */
    fun createItem(
        material: Material,
        name: String,
        lore: List<String> = emptyList(),
        amount: Int = 1
    ): ItemStack {
        return ItemStack(material, amount).apply {
            val meta = itemMeta
            meta.displayName(serializer.deserialize(name))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { serializer.deserialize(it) })
            }
            itemMeta = meta
        }
    }
    
    /**
     * Create a player head item
     */
    fun createPlayerHead(
        playerName: String,
        uuid: UUID,
        lore: List<String> = emptyList()
    ): ItemStack {
        return ItemStack(Material.PLAYER_HEAD).apply {
            val meta = itemMeta as org.bukkit.inventory.meta.SkullMeta
            meta.displayName(serializer.deserialize("§e§l$playerName"))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { serializer.deserialize(it) })
            }
            itemMeta = meta
        }
    }
    
    /**
     * Calculate centered slots for items in a row
     */
    fun getCenteredSlots(rowIndex: Int, itemCount: Int): List<Int> {
        val startSlot = rowIndex * 9
        val centerOffset = (9 - itemCount) / 2
        return (0 until itemCount).map { startSlot + centerOffset + it }
    }
    
    /**
     * Get slots for a grid layout with borders
     */
    fun getGridSlots(startRow: Int, endRow: Int, startCol: Int = 1, endCol: Int = 7): List<Int> {
        val slots = mutableListOf<Int>()
        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                slots.add(row * 9 + col)
            }
        }
        return slots
    }
    
    /**
     * Create a back button item
     */
    fun createBackButton(): ItemStack {
        return createItem(
            Material.ARROW,
            "§c§lBack",
            listOf("§7Click to go back")
        )
    }
    
    /**
     * Create a close button item
     */
    fun createCloseButton(): ItemStack {
        return createItem(
            Material.BARRIER,
            "§c§lClose",
            listOf("§7Click to close")
        )
    }
    
    /**
     * Create a next page button
     */
    fun createNextPageButton(): ItemStack {
        return createItem(
            Material.ARROW,
            "§a§lNext Page",
            listOf("§7Click for next page")
        )
    }
    
    /**
     * Create a previous page button
     */
    fun createPreviousPageButton(): ItemStack {
        return createItem(
            Material.ARROW,
            "§a§lPrevious Page",
            listOf("§7Click for previous page")
        )
    }
}
