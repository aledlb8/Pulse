package dev.aledlb.pulse.ranks.models

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.database.DatabaseManager
import dev.aledlb.pulse.util.Logger
import dev.aledlb.pulse.util.SyncHelper
import dev.aledlb.pulse.util.AsyncHelper
import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RankManager(private val databaseManager: DatabaseManager) {
    private val ranks = ConcurrentHashMap<String, Rank>()
    private val playerData = ConcurrentHashMap<UUID, PlayerData>()
    private var defaultRank: String = "member"

    fun initialize() {
        loadRanksFromConfig()
        loadPlayerDataFromDatabase()
        Logger.success("Rank system initialized with ${ranks.size} ranks")
    }

    fun loadRanksFromConfig() {
        val configManager = Pulse.getPlugin().configManager
        val ranksConfig = configManager.getConfig("ranks.yml") ?: return

        // Clear existing ranks
        ranks.clear()

        // Load default rank
        defaultRank = ranksConfig.node("default").getString("member") ?: "member"

        // Load rank definitions
        val definitionsNode = ranksConfig.node("definitions")
        for (rankName in definitionsNode.childrenMap().keys) {
            val rankNode = definitionsNode.node(rankName.toString())

            val prefix = rankNode.node("prefix").getString("") ?: ""
            val suffix = rankNode.node("suffix").getString("") ?: ""
            val weight = rankNode.node("weight").getInt(0)
            val permissions = rankNode.node("permissions").getList(String::class.java)?.toMutableSet() ?: mutableSetOf()
            val parents = rankNode.node("parents").getList(String::class.java)?.toMutableSet() ?: mutableSetOf()
            val isDefault = rankName.toString() == defaultRank

            val rank = Rank(
                name = rankName.toString(),
                prefix = prefix,
                suffix = suffix,
                permissions = permissions,
                weight = weight,
                isDefault = isDefault,
                parents = parents
            )

            ranks[rankName.toString().lowercase()] = rank
        }

        Logger.info("Loaded ${ranks.size} ranks from configuration")
    }

    private fun loadPlayerDataFromDatabase() {
        runBlocking {
            try {
                val playerDataRows = databaseManager.loadAllPlayerData()
                playerDataRows.forEach { row ->
                    // Load player ranks from PlayerRanksTable
                    val playerRanks = try {
                        databaseManager.loadPlayerRanks(row.uuid)
                    } catch (e: Exception) {
                        Logger.error("Failed to load ranks for player ${row.name}", e)
                        emptyList()
                    }

                    // Convert PlayerRankRow to PlayerRankEntry
                    val ranksSet = playerRanks.map {
                        PlayerRankEntry(it.rankName, it.expiration)
                    }.toMutableSet()

                    val data = PlayerData(
                        uuid = row.uuid,
                        name = row.name,
                        rank = row.rank,
                        rankExpiration = row.rankExpiration,
                        ranks = ranksSet
                    )
                    playerData[row.uuid] = data
                }
                Logger.info("Loaded ${playerDataRows.size} player data entries from database")
            } catch (e: Exception) {
                Logger.error("Failed to load player data from database", e)
            }
        }
    }

    fun saveRanksToConfig() {
        val configManager = Pulse.getPlugin().configManager
        val ranksConfig = configManager.getConfig("ranks.yml") ?: return

        // Clear existing definitions
        ranksConfig.node("definitions").set(null)

        // Save default rank
        ranksConfig.node("default").set(defaultRank)

        // Save rank definitions
        val definitionsNode = ranksConfig.node("definitions")
        for (rank in ranks.values) {
            val rankNode = definitionsNode.node(rank.name)
            rankNode.node("prefix").set(rank.prefix)
            rankNode.node("suffix").set(rank.suffix)
            rankNode.node("weight").set(rank.weight)
            rankNode.node("permissions").setList(String::class.java, rank.permissions.toList())
            rankNode.node("parents").setList(String::class.java, rank.parents.toList())
        }

        configManager.saveConfig("ranks.yml")
        Logger.info("Saved ${ranks.size} ranks to configuration")
    }

    // Rank Management
    fun createRank(name: String, prefix: String, suffix: String, weight: Int): Boolean {
        if (ranks.containsKey(name.lowercase())) return false

        val rank = Rank(
            name = name,
            prefix = prefix,
            suffix = suffix,
            permissions = mutableSetOf(),
            weight = weight
        )

        ranks[name.lowercase()] = rank
        saveRanksToConfig()
        return true
    }

    fun deleteRank(name: String): Boolean {
        if (!ranks.containsKey(name.lowercase())) return false
        if (name.lowercase() == defaultRank.lowercase()) return false // Cannot delete default rank

        ranks.remove(name.lowercase())

        // Reset players with this rank to default
        playerData.values.filter { it.rank.lowercase() == name.lowercase() }
            .forEach { it.updateRank(defaultRank) }

        saveRanksToConfig()
        return true
    }

    fun getRank(name: String): Rank? = ranks[name.lowercase()]

    fun getAllRanks(): Collection<Rank> = ranks.values

    fun getRanksSorted(): List<Rank> = ranks.values.sortedByDescending { it.weight }

    fun getDefaultRank(): String = defaultRank

    fun setDefaultRank(rankName: String): Boolean {
        if (!ranks.containsKey(rankName.lowercase())) return false
        defaultRank = rankName
        saveRanksToConfig()
        return true
    }

    // Player Management
    fun getPlayerData(uuid: UUID): PlayerData? = playerData[uuid]

    fun getPlayerData(player: Player): PlayerData = getOrCreatePlayerData(player.uniqueId, player.name)

    fun getOrCreatePlayerData(uuid: UUID, name: String): PlayerData {
        return playerData.computeIfAbsent(uuid) {
            val newData = PlayerData(
                uuid = uuid,
                name = name,
                rank = defaultRank,
                ranks = mutableSetOf(PlayerRankEntry(defaultRank, null))
            )

            // Save new player to database asynchronously
            AsyncHelper.saveAsync("new player data for $name") {
                databaseManager.savePlayerData(uuid, name, defaultRank)
                databaseManager.savePlayerRanks(uuid, newData.ranks)
            }

            newData
        }
    }

    fun setPlayerRank(uuid: UUID, rankName: String, expiration: Long? = null): Boolean {
        return addPlayerRank(uuid, rankName, expiration)
    }

    fun setPlayerRank(player: Player, rankName: String, expiration: Long? = null): Boolean {
        return addPlayerRank(player, rankName, expiration)
    }

    fun addPlayerRank(uuid: UUID, rankName: String, expiration: Long? = null): Boolean {
        if (!ranks.containsKey(rankName.lowercase())) return false

        val data = playerData[uuid] ?: return false
        data.addRank(rankName, expiration)
        updatePlayerPrimaryRank(data)

        // Save to database asynchronously
        AsyncHelper.saveAsync("player ranks for ${data.name}") {
            databaseManager.savePlayerRanks(uuid, data.ranks)
            databaseManager.savePlayerData(uuid, data.name, data.rank, data.rankExpiration)
        }

        // Sync to Redis
        SyncHelper.syncRankAdd(uuid, rankName, expiration)

        return true
    }

    fun addPlayerRank(player: Player, rankName: String, expiration: Long? = null): Boolean {
        if (!ranks.containsKey(rankName.lowercase())) return false

        val data = getOrCreatePlayerData(player.uniqueId, player.name)
        data.addRank(rankName, expiration)
        updatePlayerPrimaryRank(data)

        // Save to database asynchronously
        AsyncHelper.saveAsync("player ranks for ${player.name}") {
            databaseManager.savePlayerRanks(player.uniqueId, data.ranks)
            databaseManager.savePlayerData(player.uniqueId, player.name, data.rank, data.rankExpiration)
        }

        // Sync to Redis
        SyncHelper.syncRankAdd(player.uniqueId, rankName, expiration)

        return true
    }

    fun removePlayerRank(uuid: UUID, rankName: String): Boolean {
        val data = playerData[uuid] ?: return false
        val removed = data.removeRank(rankName)

        if (removed) {
            updatePlayerPrimaryRank(data)

            // Save to database asynchronously
            AsyncHelper.saveAsync("player ranks for ${data.name}") {
                databaseManager.savePlayerRanks(uuid, data.ranks)
                databaseManager.savePlayerData(uuid, data.name, data.rank, data.rankExpiration)
            }

            // Sync to Redis
            SyncHelper.syncRankRemove(uuid, rankName)
        }

        return removed
    }

    fun removePlayerRank(player: Player, rankName: String): Boolean {
        val data = getOrCreatePlayerData(player.uniqueId, player.name)
        val removed = data.removeRank(rankName)

        if (removed) {
            updatePlayerPrimaryRank(data)

            // Save to database asynchronously
            AsyncHelper.saveAsync("player ranks for ${player.name}") {
                databaseManager.savePlayerRanks(player.uniqueId, data.ranks)
                databaseManager.savePlayerData(player.uniqueId, player.name, data.rank, data.rankExpiration)
            }

            // Sync to Redis
            SyncHelper.syncRankRemove(player.uniqueId, rankName)
        }

        return removed
    }

    fun updatePlayerPrimaryRank(playerData: PlayerData) {
        val currentTime = System.currentTimeMillis()

        // Get all non-expired ranks
        val activeRanks = playerData.ranks.filter {
            it.expiration == null || it.expiration!! > currentTime
        }

        if (activeRanks.isEmpty()) {
            // No active ranks, set to default rank
            playerData.updateRank(defaultRank)
            playerData.rankExpiration = null
            playerData.ranks.clear()
            playerData.ranks.add(PlayerRankEntry(defaultRank, null))
            return
        }

        // Find the highest weight rank
        var highestRank: PlayerRankEntry? = null
        var highestWeight = Int.MIN_VALUE

        for (rankEntry in activeRanks) {
            val rank = getRank(rankEntry.rankName)
            if (rank != null && rank.weight > highestWeight) {
                highestWeight = rank.weight
                highestRank = rankEntry
            }
        }

        if (highestRank != null) {
            playerData.updateRank(highestRank.rankName)
            playerData.rankExpiration = highestRank.expiration
        } else {
            // Fallback to default if no valid rank found
            playerData.updateRank(defaultRank)
            playerData.rankExpiration = null
            playerData.ranks.clear()
            playerData.ranks.add(PlayerRankEntry(defaultRank, null))
        }
    }

    // Permission Checking
    fun hasPermission(player: Player, permission: String): Boolean {
        val data = getPlayerData(player)
        return data.hasPermission(permission, this)
    }

    fun hasPermission(uuid: UUID, permission: String): Boolean {
        val data = playerData[uuid] ?: return false
        return data.hasPermission(permission, this)
    }

    // Player Permission Management
    fun addPlayerPermission(uuid: UUID, permission: String): Boolean {
        val data = playerData[uuid] ?: return false
        data.addPermission(permission)
        SyncHelper.syncPermissionAdd(uuid, permission)
        return true
    }

    fun removePlayerPermission(uuid: UUID, permission: String): Boolean {
        val data = playerData[uuid] ?: return false
        data.removePermission(permission)
        SyncHelper.syncPermissionRemove(uuid, permission)
        return true
    }

    fun denyPlayerPermission(uuid: UUID, permission: String): Boolean {
        val data = playerData[uuid] ?: return false
        data.denyPermission(permission)
        SyncHelper.syncPermissionDeny(uuid, permission)
        return true
    }

    // Rank Permission Management
    fun addRankPermission(rankName: String, permission: String): Boolean {
        val rank = ranks[rankName.lowercase()] ?: return false
        rank.addPermission(permission)
        saveRanksToConfig()
        return true
    }

    fun removeRankPermission(rankName: String, permission: String): Boolean {
        val rank = ranks[rankName.lowercase()] ?: return false
        rank.removePermission(permission)
        saveRanksToConfig()
        return true
    }

    // Rank Parent Management
    fun addRankParent(rankName: String, parentName: String): Boolean {
        val rank = ranks[rankName.lowercase()] ?: return false
        val parent = ranks[parentName.lowercase()] ?: return false

        // Prevent circular inheritance
        if (hasCircularInheritance(rankName, parentName)) {
            return false
        }

        rank.addParent(parentName.lowercase())
        saveRanksToConfig()
        return true
    }

    fun removeRankParent(rankName: String, parentName: String): Boolean {
        val rank = ranks[rankName.lowercase()] ?: return false
        rank.removeParent(parentName.lowercase())
        saveRanksToConfig()
        return true
    }

    private fun hasCircularInheritance(rankName: String, parentName: String): Boolean {
        // Check if adding this parent would create a circular reference
        val visited = mutableSetOf<String>()
        return checkCircular(parentName.lowercase(), rankName.lowercase(), visited)
    }

    private fun checkCircular(current: String, target: String, visited: MutableSet<String>): Boolean {
        if (current == target) return true
        if (visited.contains(current)) return false
        visited.add(current)

        val rank = ranks[current] ?: return false
        for (parent in rank.parents) {
            if (checkCircular(parent.lowercase(), target, visited)) {
                return true
            }
        }
        return false
    }

    // Get all permissions including inherited from parents
    fun getAllPermissions(rankName: String): Set<String> {
        val rank = ranks[rankName.lowercase()] ?: return emptySet()
        val allPermissions = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        collectPermissions(rank, allPermissions, visited)
        return allPermissions
    }

    private fun collectPermissions(rank: Rank, permissions: MutableSet<String>, visited: MutableSet<String>) {
        if (visited.contains(rank.name.lowercase())) return
        visited.add(rank.name.lowercase())

        // Add this rank's permissions
        permissions.addAll(rank.permissions)

        // Add parent permissions recursively
        for (parentName in rank.parents) {
            val parent = ranks[parentName.lowercase()]
            if (parent != null) {
                collectPermissions(parent, permissions, visited)
            }
        }
    }

    // Utility Methods
    fun getPlayersByRank(rankName: String): List<PlayerData> {
        return playerData.values.filter { it.rank.lowercase() == rankName.lowercase() }
    }

    fun getOnlinePlayersByRank(rankName: String): List<Player> {
        return Pulse.getPlugin().server.onlinePlayers.filter {
            getPlayerData(it).rank.lowercase() == rankName.lowercase()
        }
    }

    fun getPlayerRank(player: Player): Rank? {
        val data = getPlayerData(player)
        return getRank(data.rank)
    }

    fun getPlayerRank(uuid: UUID): Rank? {
        val data = playerData[uuid] ?: return null
        return getRank(data.rank)
    }

    fun getRankNames(): List<String> = ranks.keys.toList()

    fun reload() {
        loadRanksFromConfig()
    }

    fun saveAllData() {
        // Save all player data to database synchronously during shutdown
        runBlocking {
            playerData.forEach { (uuid, data) ->
                try {
                    databaseManager.savePlayerData(uuid, data.name, data.rank, data.rankExpiration)
                    databaseManager.savePlayerRanks(uuid, data.ranks)
                } catch (e: Exception) {
                    Logger.error("Failed to save player data for ${data.name}", e)
                }
            }
        }
    }

    fun checkExpiredRanks() {
        playerData.values.forEach { data ->
            // Get expired ranks from the ranks set
            val expiredRanks = data.getExpiredRanks()

            if (expiredRanks.isNotEmpty()) {
                Logger.info("Found ${expiredRanks.size} expired rank(s) for player ${data.name}")

                // Remove expired ranks from the set
                expiredRanks.forEach { expiredRank ->
                    data.ranks.removeIf {
                        it.rankName.lowercase() == expiredRank.rankName.lowercase() &&
                        it.expiration == expiredRank.expiration
                    }
                }

                // Update primary rank
                updatePlayerPrimaryRank(data)

                // Save to database
                AsyncHelper.saveAsync("expired rank changes for ${data.name}") {
                    databaseManager.savePlayerRanks(data.uuid, data.ranks)
                    databaseManager.savePlayerData(data.uuid, data.name, data.rank, data.rankExpiration)
                }
            }
        }
    }
}