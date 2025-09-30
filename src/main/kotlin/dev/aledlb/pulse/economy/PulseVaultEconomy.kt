package dev.aledlb.pulse.economy

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.OfflinePlayer
import java.util.*

/**
 * Vault Economy implementation that bridges Vault API to Pulse's economy system.
 *
 * This allows other plugins to interact with Pulse's economy through Vault.
 */
class PulseVaultEconomy(private val economyManager: EconomyManager) : Economy {

    override fun isEnabled(): Boolean = true

    override fun getName(): String = "Pulse"

    override fun hasBankSupport(): Boolean = false

    override fun fractionalDigits(): Int = 2

    override fun format(amount: Double): String = economyManager.formatBalance(amount)

    override fun currencyNamePlural(): String = economyManager.getCurrencyNamePlural()

    override fun currencyNameSingular(): String = economyManager.getCurrencyName()

    // ==================== PLAYER BALANCE ====================

    override fun hasAccount(playerName: String): Boolean = true

    override fun hasAccount(player: OfflinePlayer): Boolean = true

    override fun hasAccount(playerName: String, worldName: String): Boolean = true

    override fun hasAccount(player: OfflinePlayer, worldName: String): Boolean = true

    override fun getBalance(playerName: String): Double {
        return try {
            val uuid = UUID.fromString(playerName)
            economyManager.getBalance(uuid)
        } catch (e: IllegalArgumentException) {
            0.0
        }
    }

    override fun getBalance(player: OfflinePlayer): Double {
        return economyManager.getBalance(player)
    }

    override fun getBalance(playerName: String, world: String): Double {
        return getBalance(playerName)
    }

    override fun getBalance(player: OfflinePlayer, world: String): Double {
        return getBalance(player)
    }

    override fun has(playerName: String, amount: Double): Boolean {
        return getBalance(playerName) >= amount
    }

    override fun has(player: OfflinePlayer, amount: Double): Boolean {
        return economyManager.hasBalance(player, amount)
    }

    override fun has(playerName: String, worldName: String, amount: Double): Boolean {
        return has(playerName, amount)
    }

    override fun has(player: OfflinePlayer, worldName: String, amount: Double): Boolean {
        return has(player, amount)
    }

    // ==================== ACCOUNT MANAGEMENT ====================

    override fun createPlayerAccount(playerName: String): Boolean = true

    override fun createPlayerAccount(player: OfflinePlayer): Boolean = true

    override fun createPlayerAccount(playerName: String, worldName: String): Boolean = true

    override fun createPlayerAccount(player: OfflinePlayer, worldName: String): Boolean = true

    // ==================== WITHDRAW ====================

    override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse {
        return try {
            val uuid = UUID.fromString(playerName)
            val player = org.bukkit.Bukkit.getOfflinePlayer(uuid)
            withdrawPlayer(player, amount)
        } catch (e: IllegalArgumentException) {
            EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Invalid player")
        }
    }

    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        if (amount < 0) {
            return EconomyResponse(0.0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount")
        }

        val balance = getBalance(player)
        if (balance < amount) {
            return EconomyResponse(0.0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds")
        }

        val success = economyManager.removeBalance(player, amount)
        val newBalance = getBalance(player)

        return if (success) {
            EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null)
        } else {
            EconomyResponse(0.0, balance, EconomyResponse.ResponseType.FAILURE, "Transaction failed")
        }
    }

    override fun withdrawPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
        return withdrawPlayer(playerName, amount)
    }

    override fun withdrawPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
        return withdrawPlayer(player, amount)
    }

    // ==================== DEPOSIT ====================

    override fun depositPlayer(playerName: String, amount: Double): EconomyResponse {
        return try {
            val uuid = UUID.fromString(playerName)
            val player = org.bukkit.Bukkit.getOfflinePlayer(uuid)
            depositPlayer(player, amount)
        } catch (e: IllegalArgumentException) {
            EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Invalid player")
        }
    }

    override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        if (amount < 0) {
            return EconomyResponse(0.0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount")
        }

        val balance = getBalance(player)
        val success = economyManager.addBalance(player, amount)
        val newBalance = getBalance(player)

        return if (success) {
            EconomyResponse(amount, newBalance, EconomyResponse.ResponseType.SUCCESS, null)
        } else {
            EconomyResponse(0.0, balance, EconomyResponse.ResponseType.FAILURE, "Transaction failed")
        }
    }

    override fun depositPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
        return depositPlayer(playerName, amount)
    }

    override fun depositPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
        return depositPlayer(player, amount)
    }

    // ==================== BANK (NOT SUPPORTED) ====================

    override fun createBank(name: String, player: String): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun createBank(name: String, player: OfflinePlayer): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun deleteBank(name: String): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun bankBalance(name: String): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun bankHas(name: String, amount: Double): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun bankWithdraw(name: String, amount: Double): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun bankDeposit(name: String, amount: Double): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun isBankOwner(name: String, playerName: String): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun isBankOwner(name: String, player: OfflinePlayer): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun isBankMember(name: String, playerName: String): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun isBankMember(name: String, player: OfflinePlayer): EconomyResponse {
        return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")
    }

    override fun getBanks(): MutableList<String> {
        return mutableListOf()
    }
}