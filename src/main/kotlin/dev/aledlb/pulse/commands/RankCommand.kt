package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class RankCommand(
    private val rankManager: RankManager,
    private val permissionManager: PermissionManager
) : BaseCommand() {

    private val messagesManager get() = Pulse.getPlugin().messagesManager

    override val name = "rank"
    override val permission = "pulse.rank"
    override val description = "Manage player ranks"
    override val usage = "/rank <create|delete|set|remove|info|list|addperm|removeperm|addparent|removeparent> [args...]\n  Duration formats: 1d, 7d, 1w, 1m (month), 3m, 6m, 1y (minimum: 1 day)"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            showHelp(sender)
            return
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "delete" -> handleDelete(sender, args)
            "set" -> handleSet(sender, args)
            "remove" -> handleRemove(sender, args)
            "info" -> handleInfo(sender, args)
            "list" -> handleList(sender)
            "addperm", "addpermission" -> handleAddPermission(sender, args)
            "removeperm", "removepermission" -> handleRemovePermission(sender, args)
            "addparent", "setparent" -> handleAddParent(sender, args)
            "removeparent", "delparent" -> handleRemoveParent(sender, args)
            "reload" -> handleReload(sender)
            "help" -> showHelp(sender)
            else -> {
                sendMessage(sender, messagesManager.getFormattedMessage("general.unknown-subcommand", "subcommand" to args[0]))
                showHelp(sender)
            }
        }
    }

    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.create")) return

        if (args.size < 5) {
            sendUsage(sender)
            return
        }

        val name = args[1]
        val prefix = args[2].replace("_", " ").replace("&", "§")
        val suffix = args[3].replace("_", " ").replace("&", "§")
        val weight = args[4].toIntOrNull() ?: run {
            sendUsage(sender)
            return
        }

        if (rankManager.createRank(name, prefix, suffix, weight)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.create-success", "rank" to name))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-already-exists", "rank" to name))
        }
    }

    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.delete")) return

        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val name = args[1]
        if (rankManager.deleteRank(name)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.delete-success", "rank" to name))
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to name))
        }
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.set")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val rankName = args[2]
        val durationStr = if (args.size >= 4) args[3] else null

        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to rankName))
            return
        }

        // Parse duration if provided
        val expiration = if (durationStr != null) {
            val duration = parseDuration(durationStr)
            if (duration == null) {
                sender.sendMessage(Component.text("Invalid duration format! Use: 1d, 7d, 1w, 1m, 3m, 6m, 1y, etc. (minimum: 1 day)").color(NamedTextColor.RED))
                return
            }
            System.currentTimeMillis() + duration
        } else {
            null
        }

        if (rankManager.setPlayerRank(targetPlayer, rankName, expiration)) {
            val durationMsg = if (expiration != null) {
                " for ${formatDuration(expiration - System.currentTimeMillis())}"
            } else {
                " permanently"
            }
            sendMessage(sender, messagesManager.getFormattedMessage("rank.set-success", "player" to targetPlayer.name, "rank" to rank.name) + durationMsg)
            sendMessage(targetPlayer, messagesManager.getFormattedMessage("rank.set-notification", "rank" to rank.name) + durationMsg)
            permissionManager.updatePlayerPermissions(targetPlayer)
            permissionManager.updatePlayerDisplayNames()
        } else {
            sendUsage(sender)
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.remove")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val rankName = args[2]

        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

        val playerData = rankManager.getPlayerData(targetPlayer)
        if (playerData.rank.lowercase() != rankName.lowercase()) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.player-doesnt-have-rank", "player" to targetPlayer.name, "rank" to rankName))
            return
        }

        val defaultRank = rankManager.getDefaultRank()
        val rank = rankManager.getRank(defaultRank)

        if (permissionManager.setPlayerRank(targetPlayer, defaultRank)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.remove-success", "player" to targetPlayer.name, "rank" to rankName))
            sendMessage(targetPlayer, messagesManager.getFormattedMessage("rank.remove-notification", "rank" to rankName))
            permissionManager.updatePlayerDisplayNames()
        } else {
            sendUsage(sender)
        }
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.info")) return

        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val rankName = args[1]
        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to rankName))
            return
        }

        sender.sendMessage(Component.empty())
        sendMessage(sender, messagesManager.getMessage("rank.info-header"))
        sendMessage(sender, messagesManager.getFormattedMessage("rank.info-name", "name" to rank.name))
        sendMessage(sender, messagesManager.getFormattedMessage("rank.info-prefix", "prefix" to rank.prefix))
        sendMessage(sender, messagesManager.getFormattedMessage("rank.info-suffix", "suffix" to rank.suffix))
        sendMessage(sender, messagesManager.getFormattedMessage("rank.info-weight", "weight" to rank.weight.toString()))
        val defaultStatus = if (rank.isDefault) messagesManager.getMessage("rank.info-default-yes") else messagesManager.getMessage("rank.info-default-no")
        sendMessage(sender, messagesManager.getFormattedMessage("rank.info-default", "status" to defaultStatus))
        sendMessage(sender, messagesManager.getFormattedMessage("rank.info-permissions-count", "count" to rank.permissions.size.toString()))
        if (rank.parents.isNotEmpty()) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.info-parents", "parents" to rank.parents.joinToString(", ")))
        }
        sendMessage(sender, messagesManager.getMessage("rank.info-footer"))

        if (rank.permissions.isNotEmpty()) {
            sendMessage(sender, messagesManager.getMessage("rank.info-permissions-header"))
            for (permission in rank.permissions.sorted()) {
                sendMessage(sender, messagesManager.getFormattedMessage("rank.info-permission-entry", "permission" to permission))
            }
        }

        val playersWithRank = rankManager.getOnlinePlayersByRank(rank.name)
        if (playersWithRank.isNotEmpty()) {
            sendMessage(sender, messagesManager.getMessage("rank.info-online-players"))
            for (player in playersWithRank) {
                sendMessage(sender, messagesManager.getFormattedMessage("rank.info-player-entry", "player" to player.name))
            }
        }
        sender.sendMessage(Component.empty())
    }

    private fun handleList(sender: CommandSender) {
        if (!requirePermission(sender, "pulse.rank.list")) return

        val ranks = rankManager.getRanksSorted()
        if (ranks.isEmpty()) {
            sendUsage(sender)
            return
        }

        sender.sendMessage(Component.empty())
        sendMessage(sender, messagesManager.getMessage("rank.list-header"))

        for (rank in ranks) {
            val defaultTag = if (rank.isDefault) messagesManager.getMessage("rank.list-default-tag") else ""
            val onlineCount = rankManager.getOnlinePlayersByRank(rank.name).size
            val parentsTag = if (rank.parents.isNotEmpty()) messagesManager.getFormattedMessage("rank.list-parents-tag", "parents" to rank.parents.joinToString(", ")) else ""
            sendMessage(sender, messagesManager.getFormattedMessage("rank.list-entry", "rank" to rank.name, "default_tag" to defaultTag, "online_count" to onlineCount.toString(), "parents_tag" to parentsTag))
        }
        sender.sendMessage(Component.empty())
    }

    private fun handleAddPermission(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.permission")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val rankName = args[1]
        val permission = args[2]

        if (rankManager.addRankPermission(rankName, permission)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.addperm-success", "permission" to permission, "rank" to rankName))
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to rankName))
        }
    }

    private fun handleRemovePermission(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.permission")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val rankName = args[1]
        val permission = args[2]

        if (rankManager.removeRankPermission(rankName, permission)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.removeperm-success", "permission" to permission, "rank" to rankName))
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to rankName))
        }
    }

    private fun handleAddParent(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.parent")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val rankName = args[1]
        val parentName = args[2]

        if (rankName.lowercase() == parentName.lowercase()) {
            sendUsage(sender)
            return
        }

        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to rankName))
            return
        }

        val parent = rankManager.getRank(parentName)
        if (parent == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to parentName))
            return
        }

        if (rankManager.addRankParent(rankName, parentName)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.addparent-success", "parent" to parent.name, "rank" to rank.name))
            sendMessage(sender, messagesManager.getFormattedMessage("rank.addparent-inheritance", "rank" to rank.name, "parent" to parent.name))
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.addparent-circular"))
        }
    }

    private fun handleRemoveParent(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.rank.parent")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val rankName = args[1]
        val parentName = args[2]

        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to rankName))
            return
        }

        if (rankManager.removeRankParent(rankName, parentName)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.removeparent-success", "parent" to parentName, "rank" to rank.name))
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendUsage(sender)
        }
    }

    private fun handleReload(sender: CommandSender) {
        if (!requirePermission(sender, "pulse.rank.reload")) return

        permissionManager.reloadPermissions()
        sendMessage(sender, messagesManager.getFormattedMessage("rank.reload-success"))
    }

    private fun parseDuration(input: String): Long? {
        val regex = "(\\d+)([dwy])".toRegex()
        val match = regex.matchEntire(input.lowercase()) ?: return null
        
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2]
        
        return when (unit) {
            "d" -> amount * 24 * 60 * 60 * 1000
            "w" -> amount * 7 * 24 * 60 * 60 * 1000
            "y" -> amount * 365 * 24 * 60 * 60 * 1000
            else -> null
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            years > 0 -> "${years}y"
            months > 0 -> "${months}m"
            weeks > 0 -> "${weeks}w"
            days > 0 -> "${days}d"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("Rank Commands:").color(NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/rank create <name> <prefix> <suffix> <weight> ", NamedTextColor.GRAY).append(Component.text("- Create new rank", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank delete <rank> ", NamedTextColor.GRAY).append(Component.text("- Delete a rank", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank set <player> <rank> [duration] ", NamedTextColor.GRAY).append(Component.text("- Set player rank", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank remove <player> <rank> ", NamedTextColor.GRAY).append(Component.text("- Remove player rank", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank info <rank> ", NamedTextColor.GRAY).append(Component.text("- View rank info", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank list ", NamedTextColor.GRAY).append(Component.text("- List all ranks", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank addperm <rank> <perm> ", NamedTextColor.GRAY).append(Component.text("- Add permission", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank removeperm <rank> <perm> ", NamedTextColor.GRAY).append(Component.text("- Remove permission", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank addparent <rank> <parent> ", NamedTextColor.GRAY).append(Component.text("- Set parent rank", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank removeparent <rank> <parent> ", NamedTextColor.GRAY).append(Component.text("- Remove parent", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/rank reload ", NamedTextColor.GRAY).append(Component.text("- Reload rank system", NamedTextColor.WHITE)))
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("create", "delete", "set", "remove", "info", "list", "addperm", "removeperm", "addparent", "removeparent", "reload", "help")
                .filter { it.startsWith(args[0].lowercase()) }

            2 -> when (args[0].lowercase()) {
                "delete", "info", "addperm", "removeperm", "addparent", "removeparent" -> rankManager.getRankNames()
                    .filter { it.startsWith(args[1].lowercase()) }
                "set", "remove" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[1], true) }
                else -> emptyList()
            }

            3 -> when (args[0].lowercase()) {
                "set" -> {
                    val defaultRank = rankManager.getDefaultRank()
                    rankManager.getRankNames()
                        .filter { it.lowercase() != defaultRank.lowercase() && it.startsWith(args[2].lowercase()) }
                }
                "remove" -> {
                    // Show only the rank the player currently has
                    val targetPlayer = Bukkit.getPlayer(args[1])
                    if (targetPlayer != null) {
                        val playerData = rankManager.getPlayerData(targetPlayer)
                        listOf(playerData.rank).filter { it.startsWith(args[2].lowercase()) }
                    } else {
                        emptyList()
                    }
                }
                "addperm", "removeperm" -> {
                    val rank = rankManager.getRank(args[1])
                    if (args[0].lowercase() == "removeperm" && rank != null) {
                        // Show permissions the rank currently has
                        rank.permissions.filter { it.startsWith(args[2].lowercase()) }
                    } else {
                        // Get all registered permissions from Bukkit
                        val allPermissions = mutableListOf<String>()

                        // Add all registered permissions
                        Bukkit.getPluginManager().permissions.forEach { perm ->
                            allPermissions.add(perm.name)
                        }

                        // Add common wildcard patterns
                        val wildcards = listOf(
                            "pulse.*", "pulse.admin", "pulse.vip", "pulse.staff",
                            "minecraft.command.*", "bukkit.command.*",
                            "essentials.*", "worldedit.*", "worldguard.*"
                        )
                        allPermissions.addAll(wildcards)

                        // Filter by what user is typing
                        allPermissions.filter { it.startsWith(args[2].lowercase()) }
                            .distinct()
                            .sorted()
                            .take(50) // Limit to 50 suggestions to avoid spam
                    }
                }
                "addparent" -> {
                    // Show all ranks except the rank itself
                    rankManager.getRankNames().filter {
                        it != args[1].lowercase() && it.startsWith(args[2].lowercase())
                    }
                }
                "removeparent" -> {
                    // Show only the parents of this rank
                    val rank = rankManager.getRank(args[1])
                    rank?.parents?.filter { it.startsWith(args[2].lowercase()) }?.toList() ?: emptyList()
                }
                else -> emptyList()
            }

            4 -> when (args[0].lowercase()) {
                "set" -> {
                    val input = args[3].lowercase()
                    // If user is typing a custom duration (starts with a digit), don't suggest anything
                    if (input.isNotEmpty() && input[0].isDigit()) {
                        emptyList()
                    } else {
                        // Suggest duration formats only if not typing a custom one
                        listOf("1d", "7d", "1w", "1m", "3m", "6m", "1y", "permanent")
                            .filter { it.startsWith(input) }
                    }
                }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}