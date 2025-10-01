package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.shop.ShopGUI
import dev.aledlb.pulse.shop.ShopManager
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
        if (!shopManager.isEnabled()) {
            sendMessage(sender, messagesManager.getMessage("shop.system-disabled"))
            return
        }

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
        if (!sender.hasPermission("pulse.shop.reload")) {
            sendMessage(sender, messagesManager.getMessage("shop.no-permission-reload"))
            return
        }

        shopManager.reload()
        sendMessage(sender, messagesManager.getMessage("shop.reload-success"))
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("pulse.shop.list")) {
            sendMessage(sender, messagesManager.getMessage("shop.no-permission-list"))
            return
        }

        val categories = shopManager.getCategories()
        val totalItems = shopManager.getShopItems().size

        sendMessage(sender, "§f")
        sendMessage(sender, messagesManager.getMessage("shop.list-header"))
        sendMessage(sender, messagesManager.getFormattedMessage("shop.list-total-items", "count" to totalItems.toString()))
        sendMessage(sender, messagesManager.getFormattedMessage("shop.list-categories", "count" to categories.size.toString()))
        sendMessage(sender, messagesManager.getMessage("shop.list-footer"))

        for (category in categories) {
            val itemCount = shopManager.getItemsByCategory(category.id).size
            sendMessage(sender, messagesManager.getFormattedMessage("shop.list-category-entry", "category" to category.displayName, "count" to itemCount.toString()))
        }

        sendMessage(sender, "§f")
    }

    private fun showHelp(sender: CommandSender) {
        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║        §fSHOP COMMANDS§5         ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §f/shop §7- Open main shop")
        sendMessage(sender, "§5║ §f/shop <category> §7- Open category")
        sendMessage(sender, "§5║ §f/shop list §7- List all items")
        sendMessage(sender, "§5║ §f/shop reload §7- Reload shop")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")
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