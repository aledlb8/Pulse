package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
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
    override val usage = "/rank <create|delete|set|remove|info|list|addperm|removeperm|addparent|removeparent> [args...]"

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
        if (!sender.hasPermission("pulse.rank.create")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

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
        if (!sender.hasPermission("pulse.rank.delete")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

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
        if (!sender.hasPermission("pulse.rank.set")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val rankName = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
            return
        }

        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.rank-not-exist", "rank" to rankName))
            return
        }

        if (permissionManager.setPlayerRank(targetPlayer, rankName)) {
            sendMessage(sender, messagesManager.getFormattedMessage("rank.set-success", "player" to targetPlayer.name, "rank" to rank.name))
            sendMessage(targetPlayer, messagesManager.getFormattedMessage("rank.set-notification", "rank" to rank.name))
            permissionManager.updatePlayerDisplayNames()
        } else {
            sendUsage(sender)
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.remove")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val rankName = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
            return
        }

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
        if (!sender.hasPermission("pulse.rank.info")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

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

        sendMessage(sender, "§f")
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
        sendMessage(sender, "§f")
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("pulse.rank.list")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        val ranks = rankManager.getRanksSorted()
        if (ranks.isEmpty()) {
            sendUsage(sender)
            return
        }

        sendMessage(sender, "§f")
        sendMessage(sender, messagesManager.getMessage("rank.list-header"))

        for (rank in ranks) {
            val defaultTag = if (rank.isDefault) messagesManager.getMessage("rank.list-default-tag") else ""
            val onlineCount = rankManager.getOnlinePlayersByRank(rank.name).size
            val parentsTag = if (rank.parents.isNotEmpty()) messagesManager.getFormattedMessage("rank.list-parents-tag", "parents" to rank.parents.joinToString(", ")) else ""
            sendMessage(sender, messagesManager.getFormattedMessage("rank.list-entry", "rank" to rank.name, "default_tag" to defaultTag, "online_count" to onlineCount.toString(), "parents_tag" to parentsTag))
        }
        sendMessage(sender, "§f")
    }

    private fun handleAddPermission(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.permission")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

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
        if (!sender.hasPermission("pulse.rank.permission")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

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
        if (!sender.hasPermission("pulse.rank.parent")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

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
        if (!sender.hasPermission("pulse.rank.parent")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

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
        if (!sender.hasPermission("pulse.rank.reload")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        permissionManager.reloadPermissions()
        sendMessage(sender, messagesManager.getFormattedMessage("rank.reload-success"))
    }

    private fun showHelp(sender: CommandSender) {
        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║         §fRANK COMMANDS§5        ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §f/rank create <name> <prefix> <suffix> <weight>")
        sendMessage(sender, "§5║ §f/rank delete <rank> §7- Delete a rank")
        sendMessage(sender, "§5║ §f/rank set <player> <rank> §7- Set player rank")
        sendMessage(sender, "§5║ §f/rank remove <player> <rank> §7- Remove player rank")
        sendMessage(sender, "§5║ §f/rank info <rank> §7- View rank info")
        sendMessage(sender, "§5║ §f/rank list §7- List all ranks")
        sendMessage(sender, "§5║ §f/rank addperm <rank> <perm> §7- Add permission")
        sendMessage(sender, "§5║ §f/rank removeperm <rank> <perm> §7- Remove permission")
        sendMessage(sender, "§5║ §f/rank addparent <rank> <parent> §7- Set parent rank")
        sendMessage(sender, "§5║ §f/rank removeparent <rank> <parent> §7- Remove parent")
        sendMessage(sender, "§5║ §f/rank reload §7- Reload rank system")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")
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
                "set" -> rankManager.getRankNames()
                    .filter { it.startsWith(args[2].lowercase()) }
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

            else -> emptyList()
        }
    }
}