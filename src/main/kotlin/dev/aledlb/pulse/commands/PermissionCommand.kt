package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class PermissionCommand(
    private val rankManager: RankManager,
    private val permissionManager: PermissionManager
) : BaseCommand() {

    private val messagesManager get() = Pulse.getPlugin().messagesManager

    override val name = "permission"
    override val permission = "pulse.permission"
    override val description = "Manage player permissions"
    override val usage = "/permission <add|remove|deny|undeny|check|list> <player> [permission]"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            showHelp(sender)
            return
        }

        when (args[0].lowercase()) {
            "add" -> handleAdd(sender, args)
            "remove" -> handleRemove(sender, args)
            "deny" -> handleDeny(sender, args)
            "undeny" -> handleUndeny(sender, args)
            "check" -> handleCheck(sender, args)
            "list" -> handleList(sender, args)
            "help" -> showHelp(sender)
            else -> {
                sendMessage(sender, "§cUnknown subcommand: ${args[0]}")
                showHelp(sender)
            }
        }
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.add")) {
            sendMessage(sender, "§cYou don't have permission to add permissions!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /permission add <player> <permission>")
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        permissionManager.addPlayerPermission(targetPlayer, permission)
        sendMessage(sender, "§aAdded permission §e$permission§a to §e${targetPlayer.name}§a!")
        sendMessage(targetPlayer, "§aYou have been granted permission: §e$permission")
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.remove")) {
            sendMessage(sender, "§cYou don't have permission to remove permissions!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /permission remove <player> <permission>")
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        permissionManager.removePlayerPermission(targetPlayer, permission)
        sendMessage(sender, "§aRemoved permission §e$permission§a from §e${targetPlayer.name}§a!")
        sendMessage(targetPlayer, "§cYour permission has been removed: §e$permission")
    }

    private fun handleDeny(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.deny")) {
            sendMessage(sender, "§cYou don't have permission to deny permissions!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /permission deny <player> <permission>")
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        permissionManager.denyPlayerPermission(targetPlayer, permission)
        sendMessage(sender, "§aDenied permission §e$permission§a for §e${targetPlayer.name}§a!")
        sendMessage(targetPlayer, "§cYou have been denied permission: §e$permission")
    }

    private fun handleUndeny(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.deny")) {
            sendMessage(sender, "§cYou don't have permission to manage denied permissions!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /permission undeny <player> <permission>")
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        val playerData = rankManager.getOrCreatePlayerData(targetPlayer.uniqueId, targetPlayer.name)
        playerData.undenyPermission(permission)
        permissionManager.updatePlayerPermissions(targetPlayer)

        sendMessage(sender, "§aUndenied permission §e$permission§a for §e${targetPlayer.name}§a!")
        sendMessage(targetPlayer, "§aPermission denial removed: §e$permission")
    }

    private fun handleCheck(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.check")) {
            sendMessage(sender, "§cYou don't have permission to check permissions!")
            return
        }

        if (args.size < 3) {
            sendMessage(sender, "§cUsage: /permission check <player> <permission>")
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        val hasPermission = permissionManager.hasPermission(targetPlayer, permission)
        val status = if (hasPermission) "§ahas" else "§cdoes not have"

        sendMessage(sender, "§e${targetPlayer.name}§f $status §fthe permission §e$permission§f!")

        // Show more detailed info
        val playerData = rankManager.getPlayerData(targetPlayer)
        val rank = rankManager.getRank(playerData.rank)

        if (playerData.permissions.contains(permission)) {
            sendMessage(sender, "§7Source: Player-specific permission")
        } else if (playerData.deniedPermissions.contains(permission)) {
            sendMessage(sender, "§7Source: Player-specific denial")
        } else if (rank?.hasPermission(permission) == true) {
            sendMessage(sender, "§7Source: Rank §e${rank.name}§7 permission")
        } else {
            sendMessage(sender, "§7Source: Not granted")
        }
    }

    private fun handleList(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.list")) {
            sendMessage(sender, "§cYou don't have permission to list permissions!")
            return
        }

        if (args.size < 2) {
            sendMessage(sender, "§cUsage: /permission list <player>")
            return
        }

        val playerName = args[1]
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, "§cPlayer §e$playerName§c is not online!")
            return
        }

        val playerData = rankManager.getPlayerData(targetPlayer)
        val rank = rankManager.getRank(playerData.rank)
        val allPermissions = playerData.getAllPermissions(rankManager)

        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║      §fPLAYER PERMISSIONS§5       ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §fPlayer: §e${targetPlayer.name}")
        sendMessage(sender, "§5║ §fRank: §e${rank?.name ?: "Unknown"}")
        sendMessage(sender, "§5║ §fTotal Permissions: §a${allPermissions.size}")
        sendMessage(sender, "§5╚════════════════════════════════╝")

        if (playerData.permissions.isNotEmpty()) {
            sendMessage(sender, "§aPlayer-specific permissions:")
            for (permission in playerData.permissions.sorted()) {
                sendMessage(sender, "§a+ §f$permission")
            }
        }

        if (playerData.deniedPermissions.isNotEmpty()) {
            sendMessage(sender, "§cDenied permissions:")
            for (permission in playerData.deniedPermissions.sorted()) {
                sendMessage(sender, "§c- §f$permission")
            }
        }

        if (rank != null && rank.permissions.isNotEmpty()) {
            sendMessage(sender, "§eRank permissions (from §e${rank.name}§e):")
            for (permission in rank.permissions.sorted()) {
                val isDenied = playerData.deniedPermissions.contains(permission)
                val color = if (isDenied) "§7" else "§e"
                val prefix = if (isDenied) "§c~ " else "§e+ "
                sendMessage(sender, "$prefix§f$permission")
            }
        }

        sendMessage(sender, "§f")
    }

    private fun showHelp(sender: CommandSender) {
        sendMessage(sender, "§f")
        sendMessage(sender, "§5╔════════════════════════════════╗")
        sendMessage(sender, "§5║      §fPERMISSION COMMANDS§5      ║")
        sendMessage(sender, "§5╠════════════════════════════════╣")
        sendMessage(sender, "§5║ §f/perm add <player> <permission> §7- Add permission")
        sendMessage(sender, "§5║ §f/perm remove <player> <permission> §7- Remove permission")
        sendMessage(sender, "§5║ §f/perm deny <player> <permission> §7- Deny permission")
        sendMessage(sender, "§5║ §f/perm undeny <player> <permission> §7- Undeny permission")
        sendMessage(sender, "§5║ §f/perm check <player> <permission> §7- Check permission")
        sendMessage(sender, "§5║ §f/perm list <player> §7- List all permissions")
        sendMessage(sender, "§5╚════════════════════════════════╝")
        sendMessage(sender, "§f")
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        return when (args.size) {
            1 -> listOf("add", "remove", "deny", "undeny", "check", "list", "help")
                .filter { it.startsWith(args[0].lowercase()) }

            2 -> when (args[0].lowercase()) {
                "add", "remove", "deny", "undeny", "check", "list" ->
                    Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.startsWith(args[1], true) }
                else -> emptyList()
            }

            3 -> when (args[0].lowercase()) {
                "add", "deny" -> {
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
                "remove", "undeny", "check" -> {
                    val targetPlayer = Bukkit.getPlayer(args[1])
                    if (targetPlayer != null) {
                        val playerData = rankManager.getPlayerData(targetPlayer)
                        when (args[0].lowercase()) {
                            "remove" -> playerData.permissions.filter { it.startsWith(args[2].lowercase()) }
                            "undeny" -> playerData.deniedPermissions.filter { it.startsWith(args[2].lowercase()) }
                            "check" -> {
                                val allPerms = playerData.getAllPermissions(rankManager)
                                allPerms.filter { it.startsWith(args[2].lowercase()) }
                            }
                            else -> emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }
}