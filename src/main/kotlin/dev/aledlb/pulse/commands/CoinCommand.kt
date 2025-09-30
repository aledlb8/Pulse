package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.economy.EconomyManager
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CoinCommand(private val economyManager: EconomyManager) : BaseCommand() {

    private val messagesManager get() = Pulse.getPlugin().messagesManager

    override val name = "coin"
    override val permission = "pulse.coin"
    override val description = "Manage player coins"
    override val usage = "/coin <balance|add|remove|set|pay|top> [args...]"

    override fun execute(sender: CommandSender, args: Array<out String>) {
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
                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("general.unknown-subcommand", "subcommand" to args[0]))
                showHelp(sender)
            }
        }
    }

    private fun handleBalance(sender: CommandSender, args: Array<out String>) {
        val targetName = if (args.size > 1) args[1] else {
            if (sender is Player) {
                sender.name
            } else {
                sendMessage(sender, messagesManager.invalidCommand())
                return
            }
        }

        val targetPlayer = if (sender.name.equals(targetName, true)) {
            sender as? Player
        } else {
            if (!sender.hasPermission("pulse.coin.others")) {
                sendMessage(sender, messagesManager.noPermission())
                return
            }
            Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        }

        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.playerNotFound())
            return
        }

        val balance = economyManager.getBalance(targetPlayer)
        val formattedBalance = economyManager.formatBalance(balance)

        if (sender.name.equals(targetName, true)) {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.balance-self", "balance" to formattedBalance))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.balance-other", "player" to targetPlayer.name!!, "balance" to formattedBalance))
        }
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.add")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendMessage(sender, messagesManager.invalidCommand())
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount <= 0) {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.amount-must-be-positive"))
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

            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.add-success", "amount" to formattedAmount, "player" to targetPlayer.name!!))
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.new-balance", "balance" to newBalance))

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMessage(messagesManager.getFormattedMessageWithPrefix("coin.add-notification", "amount" to formattedAmount))
            }
        } else {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.transaction-failed"))
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.remove")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendMessage(sender, messagesManager.invalidCommand())
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount <= 0) {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.amount-must-be-positive"))
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

            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.remove-success", "amount" to formattedAmount, "player" to targetPlayer.name!!))
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.new-balance", "balance" to newBalance))

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMessage(messagesManager.getFormattedMessageWithPrefix("coin.remove-notification", "amount" to formattedAmount))
            }
        } else {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.not-enough-coins"))
        }
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.set")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendMessage(sender, messagesManager.invalidCommand())
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount < 0) {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.amount-cannot-be-negative"))
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

            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.set-success", "player" to targetPlayer.name!!, "amount" to formattedAmount))

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMessage(messagesManager.getFormattedMessageWithPrefix("coin.set-notification", "amount" to formattedAmount))
            }
        } else {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.transaction-failed"))
        }
    }

    private fun handlePay(sender: CommandSender, args: Array<out String>) {
        val player = requirePlayer(sender) ?: return

        if (!sender.hasPermission("pulse.coin.pay")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendMessage(sender, messagesManager.invalidCommand())
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.invalid-amount", "amount" to args[2]))
            return
        }

        if (amount <= 0) {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.amount-must-be-positive"))
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: run {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("general.player-not-online", "player" to targetName))
            return
        }

        if (targetPlayer.uniqueId == player.uniqueId) {
            sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.cannot-pay-yourself"))
            return
        }

        val result = economyManager.transfer(player, targetPlayer, amount)
        when (result) {
            EconomyManager.TransactionResult.SUCCESS -> {
                val formattedAmount = economyManager.formatBalance(amount)
                val senderBalance = economyManager.formatBalance(economyManager.getBalance(player))
                val targetBalance = economyManager.formatBalance(economyManager.getBalance(targetPlayer))

                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.pay-success-sender", "amount" to formattedAmount, "player" to targetPlayer.name))
                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.new-balance", "balance" to senderBalance))

                sendMessage(targetPlayer, messagesManager.getFormattedMessageWithPrefix("coin.pay-success-receiver", "amount" to formattedAmount, "player" to player.name))
                sendMessage(targetPlayer, messagesManager.getFormattedMessageWithPrefix("coin.new-balance", "balance" to targetBalance))
            }
            EconomyManager.TransactionResult.INSUFFICIENT_FUNDS -> {
                val yourBalance = economyManager.formatBalance(economyManager.getBalance(player))
                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.not-enough-coins"))
                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("coin.new-balance", "balance" to yourBalance))
            }
            EconomyManager.TransactionResult.INVALID_AMOUNT -> {
                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.invalid-amount", "amount" to amount.toString()))
            }
            EconomyManager.TransactionResult.FAILED -> {
                sendMessage(sender, messagesManager.getFormattedMessageWithPrefix("economy.transaction-failed"))
            }
        }
    }

    private fun handleTop(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.top")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        val limit = if (args.size > 1) {
            args[1].toIntOrNull()?.coerceIn(1, 25) ?: 10
        } else {
            10
        }

        val topBalances = economyManager.getTopBalances(limit)

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║       §fTOP BALANCES§5           ║")
        sendMessage(sender, "§5╚════════════════════════════════╝")

        if (topBalances.isEmpty()) {
            sendMessage(sender, "§7No balances to display.")
        } else {
            topBalances.forEachIndexed { index, (player, balance) ->
                val rank = index + 1
                val formattedBalance = economyManager.formatBalance(balance)
                val rankColor = when (rank) {
                    1 -> "§6"  // Gold
                    2 -> "§7"  // Silver
                    3 -> "§c"  // Bronze
                    else -> "§e"  // Yellow
                }
                sendMessage(sender, "$rankColor$rank. §f${player.name} §7- §a$formattedBalance")
            }
        }

        sendMessage(sender, "§f")
    }

    private fun showHelp(sender: CommandSender) {
        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║        §fCOIN COMMANDS§5         ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §f/coin §7- Show your balance")
        sendMessage(sender, "§5║ §f/coin balance [player] §7- Check balance")
        sendMessage(sender, "§5║ §f/coin add <player> <amount> §7- Add coins")
        sendMessage(sender, "§5║ §f/coin remove <player> <amount> §7- Remove coins")
        sendMessage(sender, "§5║ §f/coin set <player> <amount> §7- Set balance")
        sendMessage(sender, "§5║ §f/coin pay <player> <amount> §7- Pay player")
        sendMessage(sender, "§5║ §f/coin top [limit] §7- Top balances")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")
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