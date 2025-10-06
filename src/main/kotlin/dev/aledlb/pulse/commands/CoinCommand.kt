package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import dev.aledlb.pulse.economy.EconomyManager
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import net.kyori.adventure.text.Component
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import net.kyori.adventure.text.format.NamedTextColor
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.Bukkit
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.command.CommandSender
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage
import org.bukkit.entity.Player
import dev.aledlb.pulse.util.MessageUtil.sendMiniMessage

class CoinCommand(private val economyManager: EconomyManager) : BaseCommand() {

    private val messagesManager get() = Pulse.getPlugin().messagesManager

    override val name = "coin"
    override val permission = "pulse.coin"
    override val description = "Manage player coins"
    override val usage = "/coin <balance|add|remove|set|pay|top> [args...]"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!economyManager.isEnabled()) {
            sendMessage(sender, messagesManager.getMessage("economy.system-disabled"))
            return
        }

        if (args.isEmpty()) {
            // Show balance for player, or help for console
            if (sender is Player) {
                handleBalance(sender, arrayOf(sender.name))
            } else {
                showHelp(sender)
            }
            return
        }

        when (args[0].lowercase()) {
            "balance", "bal" -> handleBalance(sender, args)
            "add", "give" -> handleAdd(sender, args)
            "remove", "take" -> handleRemove(sender, args)
            "set" -> handleSet(sender, args)
            "pay", "send" -> handlePay(sender, args)
            "top", "baltop" -> handleTop(sender, args)
            "help" -> showHelp(sender)
            else -> {
                sendMessage(sender, messagesManager.getFormattedMessage("general.unknown-subcommand", "subcommand" to args[0]))
                showHelp(sender)
            }
        }
    }

    private fun handleBalance(sender: CommandSender, args: Array<out String>) {
        val targetName = if (args.size > 1) args[1] else {
            if (sender is Player) {
                sender.name
            } else {
                sendUsage(sender)
                return
            }
        }

        val targetPlayer = if (sender.name.equals(targetName, true)) {
            sender as? Player
        } else {
            if (!requirePermission(sender, "pulse.coin.others")) return
            Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        }

        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.playerNotFound())
            return
        }

        val balance = economyManager.getBalance(targetPlayer)
        val formattedBalance = economyManager.formatBalance(balance)

        if (sender.name.equals(targetName, true)) {
            sendMessage(sender, messagesManager.getFormattedMessage("coin.balance-self", "balance" to formattedBalance))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("coin.balance-other", "player" to targetPlayer.name!!, "balance" to formattedBalance))
        }
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.coin.add")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount <= 0) {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.amount-must-be-positive"))
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        if (targetPlayer.name == null) {
            sendMessage(sender, messagesManager.playerNotFound())
            return
        }

        val success = economyManager.addBalance(targetPlayer, amount)
        if (success) {
            val formattedAmount = economyManager.formatBalance(amount)
            val newBalance = economyManager.formatBalance(economyManager.getBalance(targetPlayer))

            sendMessage(sender, messagesManager.getFormattedMessage("coin.add-success", "amount" to formattedAmount, "player" to targetPlayer.name!!))
            sendMessage(sender, messagesManager.getFormattedMessage("coin.new-balance", "balance" to newBalance))

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMiniMessage(messagesManager.getFormattedMessage("coin.add-notification", "amount" to formattedAmount))
            }
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.transaction-failed"))
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.coin.remove")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount <= 0) {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.amount-must-be-positive"))
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        if (targetPlayer.name == null) {
            sendMessage(sender, messagesManager.playerNotFound())
            return
        }

        val success = economyManager.removeBalance(targetPlayer, amount)
        if (success) {
            val formattedAmount = economyManager.formatBalance(amount)
            val newBalance = economyManager.formatBalance(economyManager.getBalance(targetPlayer))

            sendMessage(sender, messagesManager.getFormattedMessage("coin.remove-success", "amount" to formattedAmount, "player" to targetPlayer.name!!))
            sendMessage(sender, messagesManager.getFormattedMessage("coin.new-balance", "balance" to newBalance))

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMiniMessage(messagesManager.getFormattedMessage("coin.remove-notification", "amount" to formattedAmount))
            }
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.not-enough-coins"))
        }
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.coin.set")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount < 0) {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.amount-cannot-be-negative"))
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        if (targetPlayer.name == null) {
            sendMessage(sender, messagesManager.playerNotFound())
            return
        }

        val success = economyManager.setBalance(targetPlayer, amount)
        if (success) {
            val formattedAmount = economyManager.formatBalance(amount)

            sendMessage(sender, messagesManager.getFormattedMessage("coin.set-success", "player" to targetPlayer.name!!, "amount" to formattedAmount))

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMiniMessage(messagesManager.getFormattedMessage("coin.set-notification", "amount" to formattedAmount))
            }
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.transaction-failed"))
        }
    }

    private fun handlePay(sender: CommandSender, args: Array<out String>) {
        val player = requirePlayer(sender) ?: return
        if (!requirePermission(sender, "pulse.coin.pay")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount <= 0) {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.amount-must-be-positive"))
            return
        }

        val targetPlayer = getOnlinePlayer(sender, targetName) ?: return

        if (targetPlayer.uniqueId == player.uniqueId) {
            sendMessage(sender, messagesManager.getFormattedMessage("economy.cannot-pay-yourself"))
            return
        }

        val result = economyManager.transfer(player, targetPlayer, amount)
        when (result) {
            EconomyManager.TransactionResult.SUCCESS -> {
                val formattedAmount = economyManager.formatBalance(amount)
                val senderBalance = economyManager.formatBalance(economyManager.getBalance(player))
                val targetBalance = economyManager.formatBalance(economyManager.getBalance(targetPlayer))

                sendMessage(sender, messagesManager.getFormattedMessage("coin.pay-success-sender", "amount" to formattedAmount, "player" to targetPlayer.name))
                sendMessage(sender, messagesManager.getFormattedMessage("coin.new-balance", "balance" to senderBalance))

                sendMessage(targetPlayer, messagesManager.getFormattedMessage("coin.pay-success-receiver", "amount" to formattedAmount, "player" to player.name))
                sendMessage(targetPlayer, messagesManager.getFormattedMessage("coin.new-balance", "balance" to targetBalance))
            }
            EconomyManager.TransactionResult.INSUFFICIENT_FUNDS -> {
                val yourBalance = economyManager.formatBalance(economyManager.getBalance(player))
                sendMessage(sender, messagesManager.getFormattedMessage("economy.not-enough-coins"))
                sendMessage(sender, messagesManager.getFormattedMessage("coin.new-balance", "balance" to yourBalance))
            }
            EconomyManager.TransactionResult.INVALID_AMOUNT -> {
                sendMessage(sender, messagesManager.getFormattedMessage("economy.invalid-amount", "amount" to amount.toString()))
            }
            EconomyManager.TransactionResult.FAILED -> {
                sendMessage(sender, messagesManager.getFormattedMessage("economy.transaction-failed"))
            }
        }
    }

    private fun handleTop(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.coin.top")) return

        val limit = if (args.size > 1) {
            args[1].toIntOrNull()?.coerceIn(1, 25) ?: 10
        } else {
            10
        }

        val topBalances = economyManager.getTopBalances(limit)

        sender.sendMessage(Component.empty())
        sendMessage(sender, messagesManager.getMessage("coin.top-header"))

        if (topBalances.isEmpty()) {
            sendMessage(sender, messagesManager.getMessage("coin.top-no-balances"))
        } else {
            topBalances.forEachIndexed { index, (player, balance) ->
                val rank = index + 1
                val formattedBalance = economyManager.formatBalance(balance)
                val rankColor = when (rank) {
                    1 -> NamedTextColor.GOLD
                    2 -> NamedTextColor.GRAY
                    3 -> NamedTextColor.RED
                    else -> NamedTextColor.YELLOW
                }
                sender.sendMessage(
                    Component.text("#$rank ", rankColor)
                        .append(Component.text(player.name!!, NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(formattedBalance, NamedTextColor.GREEN))
                )
            }
        }

        sender.sendMessage(Component.empty())
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("Coin Commands:").color(NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/coin ", NamedTextColor.GRAY).append(Component.text("- Show your balance", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/coin balance [player] ", NamedTextColor.GRAY).append(Component.text("- Check balance", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/coin add <player> <amount> ", NamedTextColor.GRAY).append(Component.text("- Add coins", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/coin remove <player> <amount> ", NamedTextColor.GRAY).append(Component.text("- Remove coins", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/coin set <player> <amount> ", NamedTextColor.GRAY).append(Component.text("- Set balance", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/coin pay <player> <amount> ", NamedTextColor.GRAY).append(Component.text("- Pay player", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/coin top [limit] ", NamedTextColor.GRAY).append(Component.text("- Top balances", NamedTextColor.WHITE)))
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("balance", "add", "remove", "set", "pay", "top", "help")
                .filter { it.startsWith(args[0].lowercase()) }

            2 -> when (args[0].lowercase()) {
                "balance", "add", "remove", "set", "pay" -> {
                    Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.startsWith(args[1], true) }
                }
                "top" -> listOf("10", "15", "20", "25")
                    .filter { it.startsWith(args[1]) }
                else -> emptyList()
            }

            3 -> when (args[0].lowercase()) {
                "add", "remove", "set", "pay" -> {
                    // Suggest common amounts
                    listOf("10", "50", "100", "500", "1000")
                        .filter { it.startsWith(args[2]) }
                }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}