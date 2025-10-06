package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.shop.ShopGUI
import dev.aledlb.pulse.shop.ShopManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ShopCommand(
    private val shopManager: ShopManager,
    private val shopGUI: ShopGUI
) : BaseCommand() {

    private val messagesManager get() = Pulse.getPlugin().messagesManager

    override val name = "shop"
    override val permission = "pulse.shop"
    override val description = "Open the server shop"
    override val usage = "/shop [category|reload]"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            val player = requirePlayer(sender) ?: return
            shopGUI.openMainShop(player)
            return
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "list" -> handleList(sender)
            "help" -> showHelp(sender)
            else -> {
                // Try to open specific category
                val player = requirePlayer(sender) ?: return
                val category = shopManager.getCategory(args[0])

                if (category != null) {
                    shopGUI.openCategoryShop(player, category.id)
                } else {
                    sendMessage(sender, messagesManager.getFormattedMessage("shop.category-not-found", "category" to args[0]))
                    showHelp(sender)
                }
            }
        }
    }

    private fun handleReload(sender: CommandSender) {
        if (!requirePermission(sender, "pulse.shop.reload")) return

        shopManager.reload()
        sendMessage(sender, messagesManager.getMessage("shop.reload-success"))
    }

    private fun handleList(sender: CommandSender) {
        if (!requirePermission(sender, "pulse.shop.list")) return

        val categories = shopManager.getCategories()
        val totalItems = shopManager.getShopItems().size

        sender.sendMessage(Component.empty())
        sendMessage(sender, messagesManager.getMessage("shop.list-header"))
        sendMessage(sender, messagesManager.getFormattedMessage("shop.list-total-items", "count" to totalItems.toString()))
        sendMessage(sender, messagesManager.getFormattedMessage("shop.list-categories", "count" to categories.size.toString()))
        sendMessage(sender, messagesManager.getMessage("shop.list-footer"))

        for (category in categories) {
            val itemCount = shopManager.getItemsByCategory(category.id).size
            sendMessage(sender, messagesManager.getFormattedMessage("shop.list-category-entry", "category" to category.displayName, "count" to itemCount.toString()))
        }

        sender.sendMessage(Component.empty())
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("Shop Commands:").color(NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/shop ", NamedTextColor.GRAY).append(Component.text("- Open main shop", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/shop <category> ", NamedTextColor.GRAY).append(Component.text("- Open category", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/shop list ", NamedTextColor.GRAY).append(Component.text("- List all items", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/shop reload ", NamedTextColor.GRAY).append(Component.text("- Reload shop", NamedTextColor.WHITE)))
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> {
                val suggestions = mutableListOf("list", "reload", "help")
                suggestions.addAll(shopManager.getCategories().map { it.id })
                suggestions.filter { it.startsWith(args[0].lowercase()) }
            }
            else -> emptyList()
        }
    }
}