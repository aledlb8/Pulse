package dev.aledlb.pulse.economy

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.database.DatabaseManager
import dev.aledlb.pulse.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.RegisteredServiceProvider
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class EconomyManager(private val databaseManager: DatabaseManager) {
    private var vaultEconomy: Economy? = null
    private var useVault = false
    private val playerBalances = ConcurrentHashMap<UUID, Double>()
    private var currencyName = "Coins"
    private var currencySymbol = "⛁"
    private var startingBalance = 1000.0

    fun initialize() {
        loadConfig()
        setupVaultIntegration()
        loadPlayerBalancesFromDatabase()
    }

    private fun loadConfig() {
        val configManager = Pulse.getPlugin().configManager
        val config = configManager.getConfig("config.yml")

        if (config != null) {
            val currencyNode = config.node("currency")
            currencyName = currencyNode.node("name").getString("Coins") ?: "Coins"
            currencySymbol = currencyNode.node("symbol").getString("⛁") ?: "⛁"
            startingBalance = currencyNode.node("starting-balance").getDouble(1000.0)
        }
    }

    private fun setupVaultIntegration() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                val rsp: RegisteredServiceProvider<Economy>? = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java)
                if (rsp != null) {
                    vaultEconomy = rsp.provider
                    useVault = true
                    Logger.success("Vault economy integration enabled")
                } else {
                    Logger.info("Vault found but no economy provider available - using internal system")
                }
            } catch (e: Exception) {
                Logger.warn("Failed to setup Vault integration: ${e.message}")
            }
        } else {
            Logger.info("Vault not found - using internal economy system")
        }
    }

    private fun loadPlayerBalancesFromDatabase() {
        if (useVault) return // Don't load balances if using Vault

        runBlocking {
            try {
                val balances = databaseManager.loadAllPlayerBalances()
                playerBalances.putAll(balances)
                Logger.info("Loaded ${balances.size} player balances from database")
            } catch (e: Exception) {
                Logger.error("Failed to load player balances from database", e)
            }
        }
    }

    // ==================== BALANCE MANAGEMENT ====================

    fun getBalance(player: OfflinePlayer): Double {
        return if (useVault && vaultEconomy != null) {
            vaultEconomy!!.getBalance(player)
        } else {
            playerBalances.getOrDefault(player.uniqueId, startingBalance)
        }
    }

    fun getBalance(uuid: UUID): Double {
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return getBalance(offlinePlayer)
    }

    fun hasBalance(player: OfflinePlayer, amount: Double): Boolean {
        return getBalance(player) >= amount
    }

    fun hasBalance(uuid: UUID, amount: Double): Boolean {
        return getBalance(uuid) >= amount
    }

    fun setBalance(player: OfflinePlayer, amount: Double): Boolean {
        val clampedAmount = amount.coerceAtLeast(0.0)

        return if (useVault && vaultEconomy != null) {
            val currentBalance = vaultEconomy!!.getBalance(player)
            val difference = clampedAmount - currentBalance

            if (difference > 0) {
                vaultEconomy!!.depositPlayer(player, difference).transactionSuccess()
            } else if (difference < 0) {
                vaultEconomy!!.withdrawPlayer(player, -difference).transactionSuccess()
            } else {
                true
            }
        } else {
            playerBalances[player.uniqueId] = clampedAmount

            // Save to database asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    databaseManager.savePlayerBalance(player.uniqueId, clampedAmount)
                } catch (e: Exception) {
                    Logger.error("Failed to save player balance to database: ${player.name}", e)
                }
            }

            true
        }
    }

    fun setBalance(uuid: UUID, amount: Double): Boolean {
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return setBalance(offlinePlayer, amount)
    }

    fun addBalance(player: OfflinePlayer, amount: Double): Boolean {
        return if (useVault && vaultEconomy != null) {
            vaultEconomy!!.depositPlayer(player, amount).transactionSuccess()
        } else {
            val currentBalance = getBalance(player)
            setBalance(player, currentBalance + amount)
        }
    }

    fun addBalance(uuid: UUID, amount: Double): Boolean {
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return addBalance(offlinePlayer, amount)
    }

    fun removeBalance(player: OfflinePlayer, amount: Double): Boolean {
        return if (useVault && vaultEconomy != null) {
            vaultEconomy!!.withdrawPlayer(player, amount).transactionSuccess()
        } else {
            val currentBalance = getBalance(player)
            if (currentBalance >= amount) {
                setBalance(player, currentBalance - amount)
                true
            } else {
                false
            }
        }
    }

    fun removeBalance(uuid: UUID, amount: Double): Boolean {
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        return removeBalance(offlinePlayer, amount)
    }

    // ==================== TRANSACTIONS ====================

    fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: Double): TransactionResult {
        if (amount <= 0) {
            return TransactionResult.INVALID_AMOUNT
        }

        if (!hasBalance(from, amount)) {
            return TransactionResult.INSUFFICIENT_FUNDS
        }

        val withdrawSuccess = removeBalance(from, amount)
        if (!withdrawSuccess) {
            return TransactionResult.FAILED
        }

        val depositSuccess = addBalance(to, amount)
        if (!depositSuccess) {
            // Rollback the withdrawal
            addBalance(from, amount)
            return TransactionResult.FAILED
        }

        return TransactionResult.SUCCESS
    }

    fun transfer(fromUuid: UUID, toUuid: UUID, amount: Double): TransactionResult {
        val fromPlayer = Bukkit.getOfflinePlayer(fromUuid)
        val toPlayer = Bukkit.getOfflinePlayer(toUuid)
        return transfer(fromPlayer, toPlayer, amount)
    }

    // ==================== UTILITY METHODS ====================

    fun formatBalance(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            "$currencySymbol${amount.toLong()}"
        } else {
            "$currencySymbol${"%.2f".format(amount)}"
        }
    }

    fun getCurrencyName(): String = currencyName

    fun getCurrencySymbol(): String = currencySymbol

    fun getStartingBalance(): Double = startingBalance

    fun isUsingVault(): Boolean = useVault

    fun getTopBalances(limit: Int = 10): List<Pair<OfflinePlayer, Double>> {
        return if (useVault && vaultEconomy != null) {
            // For Vault, we'd need to iterate through all known players
            // This is a simplified implementation
            Bukkit.getOfflinePlayers()
                .map { it to getBalance(it) }
                .sortedByDescending { it.second }
                .take(limit)
        } else {
            playerBalances
                .map { (uuid, balance) -> Bukkit.getOfflinePlayer(uuid) to balance }
                .sortedByDescending { it.second }
                .take(limit)
        }
    }

    // ==================== DATA MANAGEMENT ====================

    fun loadPlayerData(player: Player) {
        if (!useVault) {
            // For internal system, ensure player has starting balance if they're new
            if (!playerBalances.containsKey(player.uniqueId)) {
                playerBalances[player.uniqueId] = startingBalance
            }
        }
    }

    fun savePlayerData(player: Player) {
        // For Vault systems, data is handled by the economy plugin
        // For internal system, data is already in memory and will be saved by database system
    }

    fun saveAllData() {
        // This would integrate with the database system when implemented
        Logger.debug("Economy data saved (${playerBalances.size} players)")
    }

    enum class TransactionResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INVALID_AMOUNT,
        FAILED
    }
}