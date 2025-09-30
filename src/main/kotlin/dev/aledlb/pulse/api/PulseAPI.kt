package dev.aledlb.pulse.api

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.placeholders.PlaceholderProvider
import dev.aledlb.pulse.ranks.models.PlayerData
import dev.aledlb.pulse.ranks.models.Rank
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

/**
 * Public API for Pulse Core
 * This class provides access to all core functionality for other plugins
 */
object PulseAPI {

    /**
     * Get the Pulse plugin instance
     */
    fun getPlugin(): Pulse = Pulse.getPlugin()

    // ==================== RANK MANAGEMENT ====================

    /**
     * Get a player's rank
     */
    fun getPlayerRank(player: Player): Rank? {
        return getPlugin().rankManager.getPlayerRank(player)
    }

    /**
     * Get a player's rank by UUID
     */
    fun getPlayerRank(uuid: UUID): Rank? {
        return getPlugin().rankManager.getPlayerRank(uuid)
    }

    /**
     * Get a player's rank name
     */
    fun getPlayerRankName(player: Player): String {
        val playerData = getPlugin().rankManager.getPlayerData(player)
        return playerData.rank
    }

    /**
     * Get a player's rank name by UUID
     */
    fun getPlayerRankName(uuid: UUID): String? {
        val playerData = getPlugin().rankManager.getPlayerData(uuid)
        return playerData?.rank
    }

    /**
     * Set a player's rank
     */
    fun setPlayerRank(player: Player, rankName: String): Boolean {
        return getPlugin().permissionManager.setPlayerRank(player, rankName)
    }

    /**
     * Get a rank by name
     */
    fun getRank(name: String): Rank? {
        return getPlugin().rankManager.getRank(name)
    }

    /**
     * Get all ranks
     */
    fun getAllRanks(): Collection<Rank> {
        return getPlugin().rankManager.getAllRanks()
    }

    /**
     * Get all ranks sorted by weight
     */
    fun getRanksSorted(): List<Rank> {
        return getPlugin().rankManager.getRanksSorted()
    }

    /**
     * Get the default rank name
     */
    fun getDefaultRank(): String {
        return getPlugin().rankManager.getDefaultRank()
    }

    /**
     * Create a new rank
     */
    fun createRank(name: String, prefix: String, suffix: String, weight: Int): Boolean {
        return getPlugin().rankManager.createRank(name, prefix, suffix, weight)
    }

    /**
     * Delete a rank
     */
    fun deleteRank(name: String): Boolean {
        return getPlugin().rankManager.deleteRank(name)
    }

    // ==================== PERMISSION MANAGEMENT ====================

    /**
     * Check if a player has a permission
     */
    fun hasPermission(player: Player, permission: String): Boolean {
        return getPlugin().permissionManager.hasPermission(player, permission)
    }

    /**
     * Add a permission to a player
     */
    fun addPlayerPermission(player: Player, permission: String) {
        getPlugin().permissionManager.addPlayerPermission(player, permission)
    }

    /**
     * Remove a permission from a player
     */
    fun removePlayerPermission(player: Player, permission: String) {
        getPlugin().permissionManager.removePlayerPermission(player, permission)
    }

    /**
     * Deny a permission for a player
     */
    fun denyPlayerPermission(player: Player, permission: String) {
        getPlugin().permissionManager.denyPlayerPermission(player, permission)
    }

    /**
     * Add a permission to a rank
     */
    fun addRankPermission(rankName: String, permission: String): Boolean {
        return getPlugin().rankManager.addRankPermission(rankName, permission)
    }

    /**
     * Remove a permission from a rank
     */
    fun removeRankPermission(rankName: String, permission: String): Boolean {
        return getPlugin().rankManager.removeRankPermission(rankName, permission)
    }

    /**
     * Get all permissions for a player (including rank permissions)
     */
    fun getPlayerPermissions(player: Player): Set<String> {
        val playerData = getPlugin().rankManager.getPlayerData(player)
        return playerData.getAllPermissions(getPlugin().rankManager)
    }

    /**
     * Get player-specific permissions (excluding rank permissions)
     */
    fun getPlayerSpecificPermissions(player: Player): Set<String> {
        val playerData = getPlugin().rankManager.getPlayerData(player)
        return playerData.permissions.toSet()
    }

    /**
     * Get denied permissions for a player
     */
    fun getPlayerDeniedPermissions(player: Player): Set<String> {
        val playerData = getPlugin().rankManager.getPlayerData(player)
        return playerData.deniedPermissions.toSet()
    }

    // ==================== PLAYER DATA ====================

    /**
     * Get player data
     */
    fun getPlayerData(player: Player): PlayerData {
        return getPlugin().rankManager.getPlayerData(player)
    }

    /**
     * Get player data by UUID
     */
    fun getPlayerData(uuid: UUID): PlayerData? {
        return getPlugin().rankManager.getPlayerData(uuid)
    }

    /**
     * Get player display name with rank formatting
     */
    fun getPlayerDisplayName(player: Player): String {
        return getPlugin().permissionManager.getPlayerDisplayName(player)
    }

    /**
     * Get players by rank
     */
    fun getPlayersByRank(rankName: String): List<PlayerData> {
        return getPlugin().rankManager.getPlayersByRank(rankName)
    }

    /**
     * Get online players by rank
     */
    fun getOnlinePlayersByRank(rankName: String): List<Player> {
        return getPlugin().rankManager.getOnlinePlayersByRank(rankName)
    }

    // ==================== PLACEHOLDER SYSTEM ====================

    /**
     * Process placeholders in a string for a player
     */
    fun processPlaceholders(player: Player?, text: String): String {
        return getPlugin().placeholderManager.processPlaceholders(player, text)
    }

    /**
     * Process placeholders in a string for an offline player
     */
    fun processOfflinePlaceholders(player: OfflinePlayer?, text: String): String {
        return getPlugin().placeholderManager.processOfflinePlaceholders(player, text)
    }

    /**
     * Process a single placeholder
     */
    fun processPlaceholder(player: Player?, placeholder: String): String? {
        return getPlugin().placeholderManager.processPlaceholder(player, placeholder)
    }

    /**
     * Register a custom placeholder provider
     */
    fun registerPlaceholderProvider(provider: PlaceholderProvider) {
        getPlugin().placeholderManager.registerProvider(provider)
    }

    /**
     * Unregister a placeholder provider
     */
    fun unregisterPlaceholderProvider(identifier: String) {
        getPlugin().placeholderManager.unregisterProvider(identifier)
    }

    /**
     * Get all available placeholders
     */
    fun getAllPlaceholders(): Map<String, List<String>> {
        return getPlugin().placeholderManager.getAllPlaceholders()
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Reload the permission system
     */
    fun reloadPermissions() {
        getPlugin().permissionManager.reloadPermissions()
    }

    /**
     * Update display names for all online players
     */
    fun updateDisplayNames() {
        getPlugin().permissionManager.updatePlayerDisplayNames()
    }

    /**
     * Update permissions for all online players
     */
    fun updateAllPlayerPermissions() {
        getPlugin().permissionManager.updateAllOnlinePlayersPermissions()
    }

    /**
     * Check if Pulse is fully loaded
     */
    fun isLoaded(): Boolean {
        return getPlugin().isPluginFullyLoaded()
    }

    /**
     * Get the plugin version
     */
    fun getVersion(): String {
        return getPlugin().pluginMeta.version
    }
}