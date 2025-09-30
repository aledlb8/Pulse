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
                sendMessage(sender, "§cUnknown subcommand: ${args[0]}")
                showHelp(sender)
            }
        }
    }

    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.create")) {
            sendMessage(sender, "§cYou don't have permission to create ranks!")
            return
        }

        if (args.size < 5) {
            sendMessage(sender, "§cUsage: /rank create <name> <prefix> <suffix> <weight>")
            return
        }

        val name = args[1]
        val prefix = args[2].replace("_", " ").replace("&", "§")
        val suffix = args[3].replace("_", " ").replace("&", "§")
        val weight = args[4].toIntOrNull() ?: run {
            sendMessage(sender, "§cWeight must be a number!")
            return
        }

        if (rankManager.createRank(name, prefix, suffix, weight)) {
            sendMessage(sender, "§aSuccessfully created rank §e$name§a!")
        } else {
            sendMessage(sender, "§cRank §e$name§c already exists!")
        }
    }

    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.delete")) {
            sendMessage(sender, "§cYou don't have permission to delete ranks!")
            return
        }

        if (args.size < 2) {
            sendMessage(sender, "§cUsage: /rank delete <name>")
            return
        }

        val name = args[1]
        if (rankManager.deleteRank(name)) {
            sendMessage(sender, "§aSuccessfully deleted rank §e$name§a!")
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, "§cRank §e$name§c doesn't exist or cannot be deleted!")
        }
    }

    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.set")) {
            sendMessage(sender, "§cYou don't have permission to set ranks!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /rank set <player> <rank>")
            return
        }

        val playerName = args[1]
        val rankName = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, "§cRank §e$rankName§c doesn't exist!")
            return
        }

        if (permissionManager.setPlayerRank(targetPlayer, rankName)) {
            sendMessage(sender, "§aSet §e${targetPlayer.name}§a's rank to §e${rank.name}§a!")
            sendMessage(targetPlayer, "§aYour rank has been set to §e${rank.name}§a!")
            permissionManager.updatePlayerDisplayNames()
        } else {
            sendMessage(sender, "§cFailed to set rank!")
        }
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.remove")) {
            sendMessage(sender, "§cYou don't have permission to remove ranks from players!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /rank remove <player> <rank>")
            return
        }

        val playerName = args[1]
        val rankName = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        val playerData = rankManager.getPlayerData(targetPlayer)
        if (playerData.rank.lowercase() != rankName.lowercase()) {
            sendMessage(sender, "§c${targetPlayer.name} doesn't have the rank §e$rankName§c!")
            return
        }

        val defaultRank = rankManager.getDefaultRank()
        val rank = rankManager.getRank(defaultRank)

        if (permissionManager.setPlayerRank(targetPlayer, defaultRank)) {
            sendMessage(sender, "§aRemoved rank §e$rankName§a from §e${targetPlayer.name}§a!")
            sendMessage(targetPlayer, "§cYour rank §e$rankName§c has been removed!")
            permissionManager.updatePlayerDisplayNames()
        } else {
            sendMessage(sender, "§cFailed to remove rank!")
        }
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.info")) {
            sendMessage(sender, "§cYou don't have permission to view rank info!")
            return
        }

        if (args.size < 2) {
            sendMessage(sender, "§cUsage: /rank info <rank>")
            return
        }

        val rankName = args[1]
        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, "§cRank §e$rankName§c doesn't exist!")
            return
        }

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║         §fRANK INFO§5            ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §fName: §e${rank.name}")
        sendMessage(sender, "§5║ §fPrefix: ${rank.prefix}")
        sendMessage(sender, "§5║ §fSuffix: ${rank.suffix}")
        sendMessage(sender, "§5║ §fWeight: §a${rank.weight}")
        sendMessage(sender, "§5║ §fDefault: ${if (rank.isDefault) "§aYes" else "§cNo"}")
        sendMessage(sender, "§5║ §fPermissions: §7${rank.permissions.size}")
        if (rank.parents.isNotEmpty()) {
            sendMessage(sender, "§5║ §fParents: §7${rank.parents.joinToString(", ")}")
        }
        sendMessage(sender, "§5╚════════════════════════════════╝")

        if (rank.permissions.isNotEmpty()) {
            sendMessage(sender, "§7Permissions:")
            for (permission in rank.permissions.sorted()) {
                sendMessage(sender, "§7- §f$permission")
            }
        }

        val playersWithRank = rankManager.getOnlinePlayersByRank(rank.name)
        if (playersWithRank.isNotEmpty()) {
            sendMessage(sender, "§7Online players with this rank:")
            for (player in playersWithRank) {
                sendMessage(sender, "§7- §e${player.name}")
            }
        }
        sendMessage(sender, "§f")
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("pulse.rank.list")) {
            sendMessage(sender, "§cYou don't have permission to list ranks!")
            return
        }

        val ranks = rankManager.getRanksSorted()
        if (ranks.isEmpty()) {
            sendMessage(sender, "§cNo ranks found!")
            return
        }

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║         §fRANK LIST§5            ║")
        sendMessage(sender, "§5╚════════════════════════════════╝")

        for (rank in ranks) {
            val defaultTag = if (rank.isDefault) " §7(default)" else ""
            val onlineCount = rankManager.getOnlinePlayersByRank(rank.name).size
            val parentsTag = if (rank.parents.isNotEmpty()) " §7parents: §e[${rank.parents.joinToString(", ")}]" else ""
            sendMessage(sender, "§e${rank.name}$defaultTag §7(§a$onlineCount§7 online)$parentsTag")
        }
        sendMessage(sender, "§f")
    }

    private fun handleAddPermission(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.permission")) {
            sendMessage(sender, "§cYou don't have permission to manage rank permissions!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /rank addperm <rank> <permission>")
            return
        }

        val rankName = args[1]
        val permission = args[2]

        if (rankManager.addRankPermission(rankName, permission)) {
            sendMessage(sender, "§aAdded permission §e$permission§a to rank §e$rankName§a!")
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, "§cRank §e$rankName§c doesn't exist!")
        }
    }

    private fun handleRemovePermission(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.permission")) {
            sendMessage(sender, "§cYou don't have permission to manage rank permissions!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /rank removeperm <rank> <permission>")
            return
        }

        val rankName = args[1]
        val permission = args[2]

        if (rankManager.removeRankPermission(rankName, permission)) {
            sendMessage(sender, "§aRemoved permission §e$permission§a from rank §e$rankName§a!")
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, "§cRank §e$rankName§c doesn't exist!")
        }
    }

    private fun handleAddParent(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.parent")) {
            sendMessage(sender, "§cYou don't have permission to manage rank parents!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /rank addparent <rank> <parent>")
            return
        }

        val rankName = args[1]
        val parentName = args[2]

        if (rankName.lowercase() == parentName.lowercase()) {
            sendMessage(sender, "§cA rank cannot be its own parent!")
            return
        }

        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, "§cRank §e$rankName§c doesn't exist!")
            return
        }

        val parent = rankManager.getRank(parentName)
        if (parent == null) {
            sendMessage(sender, "§cParent rank §e$parentName§c doesn't exist!")
            return
        }

        if (rankManager.addRankParent(rankName, parentName)) {
            sendMessage(sender, "§aAdded parent §e${parent.name}§a to rank §e${rank.name}§a!")
            sendMessage(sender, "§7Rank §e${rank.name}§7 will now inherit permissions from §e${parent.name}§7.")
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, "§cFailed to add parent! This would create a circular inheritance.")
        }
    }

    private fun handleRemoveParent(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.rank.parent")) {
            sendMessage(sender, "§cYou don't have permission to manage rank parents!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /rank removeparent <rank> <parent>")
            return
        }

        val rankName = args[1]
        val parentName = args[2]

        val rank = rankManager.getRank(rankName)
        if (rank == null) {
            sendMessage(sender, "§cRank §e$rankName§c doesn't exist!")
            return
        }

        if (rankManager.removeRankParent(rankName, parentName)) {
            sendMessage(sender, "§aRemoved parent §e$parentName§a from rank §e${rank.name}§a!")
            permissionManager.updateAllOnlinePlayersPermissions()
        } else {
            sendMessage(sender, "§cFailed to remove parent!")
        }
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("pulse.rank.reload")) {
            sendMessage(sender, "§cYou don't have permission to reload ranks!")
            return
        }

        permissionManager.reloadPermissions()
        sendMessage(sender, "§aRank system reloaded successfully!")
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