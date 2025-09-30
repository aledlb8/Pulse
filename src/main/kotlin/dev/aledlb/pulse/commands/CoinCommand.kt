package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.economy.EconomyManager
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CoinCommand(private val economyManager: EconomyManager) : BaseCommand() {

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
                sendMessage(sender, "§cUnknown subcommand: ${args[0]}")
                showHelp(sender)
            }
        }
    }

    private fun handleBalance(sender: CommandSender, args: Array<out String>) {
        val targetName = if (args.size > 1) args[1] else {
            if (sender is Player) {
                sender.name
            } else {
                sendMessage(sender, "§cUsage: /coin balance <player>")
                return
            }
        }

        val targetPlayer = if (sender.name.equals(targetName, true)) {
            sender as? Player
        } else {
            if (!sender.hasPermission("pulse.coin.others")) {
                sendMessage(sender, "§cYou don't have permission to check other players' balances!")
                return
            }
            Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        }

        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$targetName§c not found!")
            return
        }

        val balance = economyManager.getBalance(targetPlayer)
        val formattedBalance = economyManager.formatBalance(balance)

        if (sender.name.equals(targetName, true)) {
            sendMessage(sender, "§aYour balance: §e$formattedBalance")
        } else {
            sendMessage(sender, "§e${targetPlayer.name}§a's balance: §e$formattedBalance")
        }
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.add")) {
            sendMessage(sender, "§cYou don't have permission to add coins!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /coin add <player> <amount>")
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, "§cInvalid amount: §e${args[2]}")
            return
        }

        if (amount <= 0) {
            sendMessage(sender, "§cAmount must be positive!")
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        if (targetPlayer.name == null) {
            sendMessage(sender, "§cPlayer §e$targetName§c not found!")
            return
        }

        val success = economyManager.addBalance(targetPlayer, amount)
        if (success) {
            val formattedAmount = economyManager.formatBalance(amount)
            val newBalance = economyManager.formatBalance(economyManager.getBalance(targetPlayer))

            sendMessage(sender, "§aAdded §e$formattedAmount§a to §e${targetPlayer.name}§a's account!")
            sendMessage(sender, "§7New balance: §e$newBalance")

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMessage("§d[§5Pulse§d]§r §aYou received §e$formattedAmount§a!")
            }
        } else {
            sendMessage(sender, "§cFailed to add coins!")
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.remove")) {
            sendMessage(sender, "§cYou don't have permission to remove coins!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /coin remove <player> <amount>")
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, "§cInvalid amount: §e${args[2]}")
            return
        }

        if (amount <= 0) {
            sendMessage(sender, "§cAmount must be positive!")
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        if (targetPlayer.name == null) {
            sendMessage(sender, "§cPlayer §e$targetName§c not found!")
            return
        }

        val success = economyManager.removeBalance(targetPlayer, amount)
        if (success) {
            val formattedAmount = economyManager.formatBalance(amount)
            val newBalance = economyManager.formatBalance(economyManager.getBalance(targetPlayer))

            sendMessage(sender, "§aRemoved §e$formattedAmount§a from §e${targetPlayer.name}§a's account!")
            sendMessage(sender, "§7New balance: §e$newBalance")

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMessage("§d[§5Pulse§d]§r §c$formattedAmount§c was removed from your account!")
            }
        } else {
            sendMessage(sender, "§cFailed to remove coins! (Insufficient funds)")
        }
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.set")) {
            sendMessage(sender, "§cYou don't have permission to set coin balances!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /coin set <player> <amount>")
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, "§cInvalid amount: §e${args[2]}")
            return
        }

        if (amount < 0) {
            sendMessage(sender, "§cAmount cannot be negative!")
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: Bukkit.getOfflinePlayer(targetName)
        if (targetPlayer.name == null) {
            sendMessage(sender, "§cPlayer §e$targetName§c not found!")
            return
        }

        val success = economyManager.setBalance(targetPlayer, amount)
        if (success) {
            val formattedAmount = economyManager.formatBalance(amount)

            sendMessage(sender, "§aSet §e${targetPlayer.name}§a's balance to §e$formattedAmount§a!")

            // Notify the target player if they're online
            if (targetPlayer is Player && targetPlayer.isOnline) {
                targetPlayer.sendMessage("§d[§5Pulse§d]§r §aYour balance has been set to §e$formattedAmount§a!")
            }
        } else {
            sendMessage(sender, "§cFailed to set balance!")
        }
    }

    private fun handlePay(sender: CommandSender, args: Array<out String>) {
        val player = requirePlayer(sender) ?: return

        if (!sender.hasPermission("pulse.coin.pay")) {
            sendMessage(sender, "§cYou don't have permission to pay other players!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /coin pay <player> <amount>")
            return
        }

        val targetName = args[1]
        val amount = args[2].toDoubleOrNull() ?: run {
            sendMessage(sender, "§cInvalid amount: §e${args[2]}")
            return
        }

        if (amount <= 0) {
            sendMessage(sender, "§cAmount must be positive!")
            return
        }

        val targetPlayer = Bukkit.getPlayer(targetName) ?: run {
            sendMessage(sender, "§cPlayer §e$targetName§c is not online!")
            return
        }

        if (targetPlayer.uniqueId == player.uniqueId) {
            sendMessage(sender, "§cYou cannot pay yourself!")
            return
        }

        val result = economyManager.transfer(player, targetPlayer, amount)
        when (result) {
            EconomyManager.TransactionResult.SUCCESS -> {
                val formattedAmount = economyManager.formatBalance(amount)
                val senderBalance = economyManager.formatBalance(economyManager.getBalance(player))
                val targetBalance = economyManager.formatBalance(economyManager.getBalance(targetPlayer))

                sendMessage(sender, "§aYou paid §e$formattedAmount§a to §e${targetPlayer.name}§a!")
                sendMessage(sender, "§7Your balance: §e$senderBalance")

                sendMessage(targetPlayer, "§aYou received §e$formattedAmount§a from §e${player.name}§a!")
                sendMessage(targetPlayer, "§7Your balance: §e$targetBalance")
            }
            EconomyManager.TransactionResult.INSUFFICIENT_FUNDS -> {
                val yourBalance = economyManager.formatBalance(economyManager.getBalance(player))
                sendMessage(sender, "§cYou don't have enough coins!")
                sendMessage(sender, "§7Your balance: §e$yourBalance")
            }
            EconomyManager.TransactionResult.INVALID_AMOUNT -> {
                sendMessage(sender, "§cInvalid amount!")
            }
            EconomyManager.TransactionResult.FAILED -> {
                sendMessage(sender, "§cTransaction failed!")
            }
        }
    }

    private fun handleTop(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.coin.top")) {
            sendMessage(sender, "§cYou don't have permission to view the balance leaderboard!")
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