package dev.aledlb.pulse.placeholders

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.economy.EconomyManager
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

class PulsePlaceholderProvider(
    private val rankManager: RankManager,
    private val permissionManager: PermissionManager,
    private val economyManager: EconomyManager,
    private val playtimeManager: dev.aledlb.pulse.playtime.PlaytimeManager
) : PlaceholderProvider {

    override fun onPlaceholderRequest(player: Player?, placeholder: String): String? {
        if (player == null) return null
        return processPlaceholder(player, placeholder, true)
    }

    override fun onOfflinePlaceholderRequest(player: OfflinePlayer?, placeholder: String): String? {
        if (player == null) return null
        return processPlaceholder(player, placeholder, false)
    }

    private fun processPlaceholder(player: OfflinePlayer, placeholder: String, isOnline: Boolean): String? {
        return when (placeholder.lowercase()) {
            // Basic rank info
            "rank" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return rankManager.getDefaultRank()
                playerData.rank
            }
            "rank_name" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return rankManager.getDefaultRank()
                val rank = rankManager.getRank(playerData.rank)
                rank?.name ?: playerData.rank
            }
            "rank_prefix" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return ""
                val rank = rankManager.getRank(playerData.rank)
                rank?.prefix ?: ""
            }
            "rank_suffix" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return ""
                val rank = rankManager.getRank(playerData.rank)
                rank?.suffix ?: ""
            }
            "rank_weight" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return "0"
                val rank = rankManager.getRank(playerData.rank)
                rank?.weight?.toString() ?: "0"
            }
            "rank_is_default" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return "true"
                val rank = rankManager.getRank(playerData.rank)
                (rank?.isDefault == true).toString()
            }

            // Player display formatting
            "player_formatted", "player_display" -> {
                if (!isOnline || player !is Player) return player.name
                permissionManager.getPlayerDisplayName(player)
            }
            "player_prefix" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return ""
                val rank = rankManager.getRank(playerData.rank)
                rank?.prefix ?: ""
            }
            "player_suffix" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return ""
                val rank = rankManager.getRank(playerData.rank)
                rank?.suffix ?: ""
            }
            "player_name_formatted" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return player.name ?: "Unknown"
                val rank = rankManager.getRank(playerData.rank)
                if (rank != null) {
                    "${rank.prefix}${player.name}${rank.suffix}"
                } else {
                    player.name ?: "Unknown"
                }
            }

            // Permission counts
            "permissions_count", "permissions_total" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return "0"
                playerData.getAllPermissions(rankManager).size.toString()
            }
            "permissions_player_count" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return "0"
                playerData.permissions.size.toString()
            }
            "permissions_rank_count" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return "0"
                val rank = rankManager.getRank(playerData.rank)
                rank?.permissions?.size?.toString() ?: "0"
            }
            "permissions_denied_count" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return "0"
                playerData.deniedPermissions.size.toString()
            }

            // Economy
            "balance", "money" -> {
                val balance = economyManager.getBalance(player)
                economyManager.formatBalance(balance)
            }
            "balance_raw" -> {
                val balance = economyManager.getBalance(player)
                if (balance == balance.toLong().toDouble()) {
                    balance.toLong().toString()
                } else {
                    "%.2f".format(balance)
                }
            }
            "currency_name" -> economyManager.getCurrencyName()
            "currency_name_plural" -> economyManager.getCurrencyNamePlural()
            "currency_symbol" -> economyManager.getCurrencySymbol()

            // Server stats
            "players_total" -> Bukkit.getOfflinePlayers().size.toString()
            "players_online" -> Bukkit.getOnlinePlayers().size.toString()
            "ranks_total" -> rankManager.getAllRanks().size.toString()
            "default_rank" -> rankManager.getDefaultRank()

            // Last seen (for offline players)
            "last_seen" -> {
                val playerData = rankManager.getPlayerData(player.uniqueId) ?: return "Never"
                if (isOnline && player is Player) {
                    "Online"
                } else {
                    val lastSeen = playerData.lastSeen
                    if (lastSeen > 0) {
                        val diff = System.currentTimeMillis() - lastSeen
                        formatTimeDifference(diff)
                    } else {
                        "Never"
                    }
                }
            }

            // Playtime
            "playtime" -> playtimeManager.getFormattedPlaytime(player.uniqueId)
            "playtime_raw" -> playtimeManager.getPlaytime(player.uniqueId).toString()
            "playtime_hours" -> "%.2f".format(playtimeManager.getPlaytimeHours(player.uniqueId))
            "playtime_minutes" -> playtimeManager.getPlaytimeMinutes(player.uniqueId).toString()
            "playtime_seconds" -> playtimeManager.getPlaytimeSeconds(player.uniqueId).toString()

            else -> {
                // Handle dynamic placeholders
                when {
                    placeholder.startsWith("has_permission_") -> {
                        val permission = placeholder.substring("has_permission_".length)
                        if (isOnline && player is Player) {
                            permissionManager.hasPermission(player, permission).toString()
                        } else {
                            val playerData = rankManager.getPlayerData(player.uniqueId)
                            playerData?.hasPermission(permission, rankManager)?.toString() ?: "false"
                        }
                    }
                    placeholder.startsWith("rank_players_online_") -> {
                        val rankName = placeholder.substring("rank_players_online_".length)
                        rankManager.getOnlinePlayersByRank(rankName).size.toString()
                    }
                    placeholder.startsWith("rank_players_total_") -> {
                        val rankName = placeholder.substring("rank_players_total_".length)
                        rankManager.getPlayersByRank(rankName).size.toString()
                    }
                    placeholder.startsWith("rank_weight_") -> {
                        val rankName = placeholder.substring("rank_weight_".length)
                        val rank = rankManager.getRank(rankName)
                        rank?.weight?.toString() ?: "0"
                    }
                    placeholder.startsWith("rank_prefix_") -> {
                        val rankName = placeholder.substring("rank_prefix_".length)
                        val rank = rankManager.getRank(rankName)
                        rank?.prefix ?: ""
                    }
                    placeholder.startsWith("rank_suffix_") -> {
                        val rankName = placeholder.substring("rank_suffix_".length)
                        val rank = rankManager.getRank(rankName)
                        rank?.suffix ?: ""
                    }
                    placeholder.startsWith("rank_display_") -> {
                        val rankName = placeholder.substring("rank_display_".length)
                        val rank = rankManager.getRank(rankName)
                        rank?.name ?: rankName
                    }
                    placeholder.startsWith("has_balance_") -> {
                        val amountStr = placeholder.substring("has_balance_".length)
                        val amount = amountStr.toDoubleOrNull() ?: return "false"
                        economyManager.hasBalance(player, amount).toString()
                    }
                    placeholder.startsWith("balance_formatted_") -> {
                        val amountStr = placeholder.substring("balance_formatted_".length)
                        val amount = amountStr.toDoubleOrNull() ?: return "Invalid"
                        economyManager.formatBalance(amount)
                    }
                    else -> null
                }
            }
        }
    }

    private fun formatTimeDifference(diff: Long): String {
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h ago"
            hours > 0 -> "${hours}h ${minutes % 60}m ago"
            minutes > 0 -> "${minutes}m ${seconds % 60}s ago"
            else -> "${seconds}s ago"
        }
    }

    override fun getIdentifier(): String = "pulse"

    override fun getPlaceholders(): List<String> {
        return listOf(
            // Basic rank info
            "rank", "rank_name",
            "rank_prefix", "rank_suffix", "rank_weight", "rank_is_default",

            // Player display
            "player_formatted", "player_display", "player_prefix", "player_suffix",
            "player_name_formatted",

            // Permission counts
            "permissions_count", "permissions_total", "permissions_player_count",
            "permissions_rank_count", "permissions_denied_count",

            // Economy
            "balance", "money", "balance_raw", "currency_name", "currency_name_plural", "currency_symbol",

            // Server stats
            "players_total", "players_online", "ranks_total", "default_rank",

            // Time
            "last_seen",

            // Playtime
            "playtime", "playtime_raw", "playtime_hours", "playtime_minutes", "playtime_seconds",

            // Dynamic placeholders (examples)
            "has_permission_<permission>",
            "rank_players_online_<rank>",
            "rank_players_total_<rank>",
            "rank_weight_<rank>",
            "rank_prefix_<rank>",
            "rank_suffix_<rank>",
            "rank_display_<rank>",
            "has_balance_<amount>",
            "balance_formatted_<amount>"
        )
    }
}