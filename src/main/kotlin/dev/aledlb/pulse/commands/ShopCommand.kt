package dev.aledlb.pulse.commands

import dev.aledlb.pulse.shop.ShopGUI
import dev.aledlb.pulse.shop.ShopManager
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ShopCommand(
    private val shopManager: ShopManager,
    private val shopGUI: ShopGUI
) : BaseCommand() {

    override val name = "shop"
    override val permission = "pulse.shop"
    override val description = "Open the server shop"
    override val usage = "/shop [category|reload]"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!shopManager.isEnabled()) {
            val pulse = dev.aledlb.pulse.Pulse.getPlugin()
            sendMessage(sender, "§cShop is disabled (Economy system requires Vault)")
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
                    val pulse = dev.aledlb.pulse.Pulse.getPlugin()
                    sendMessage(sender, "§cCategory §e${args[0]}§c not found!")
                    showHelp(sender)
                }
            }
        }
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("pulse.shop.reload")) {
            val pulse = dev.aledlb.pulse.Pulse.getPlugin()
            sendMessage(sender, "§cYou don't have permission to reload the shop!")
            return
        }

        shopManager.reload()
        val pulse = dev.aledlb.pulse.Pulse.getPlugin()
        sendMessage(sender, "§aShop configuration reloaded successfully!")
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("pulse.shop.list")) {
            val pulse = dev.aledlb.pulse.Pulse.getPlugin()
            sendMessage(sender, "§cYou don't have permission to list shop items!")
            return
        }

        val categories = shopManager.getCategories()
        val totalItems = shopManager.getShopItems().size
        val pulse = dev.aledlb.pulse.Pulse.getPlugin()

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║         §fSHOP INFO§5            ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §fTotal Items: §a$totalItems")
        sendMessage(sender, "§5║ §fCategories: §a${categories.size}")
        sendMessage(sender, "§5╚════════════════════════════════╝")

        for (category in categories) {
            val itemCount = shopManager.getItemsByCategory(category.id).size
            sendMessage(sender, "§e${category.displayName} §7- §a$itemCount items")
        }

        sendMessage(sender, "§f")
    }

    private fun showHelp(sender: CommandSender) {
        val pulse = dev.aledlb.pulse.Pulse.getPlugin()
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