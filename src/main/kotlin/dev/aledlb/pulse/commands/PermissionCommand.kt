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
                sendMessage(sender, messagesManager.getFormattedMessage("general.unknown-subcommand", "subcommand" to args[0]))
                showHelp(sender)
            }
        }
    }

    private fun handleAdd(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.add")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
            return
        }

        permissionManager.addPlayerPermission(targetPlayer, permission)
        sendMessage(sender, messagesManager.getFormattedMessage("permission.add-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.add-notification", "permission" to permission))
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.remove")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
            return
        }

        permissionManager.removePlayerPermission(targetPlayer, permission)
        sendMessage(sender, messagesManager.getFormattedMessage("permission.remove-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.remove-notification", "permission" to permission))
    }

    private fun handleDeny(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.deny")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
            return
        }

        permissionManager.denyPlayerPermission(targetPlayer, permission)
        sendMessage(sender, messagesManager.getFormattedMessage("permission.deny-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.deny-notification", "permission" to permission))
    }

    private fun handleUndeny(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.deny")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
            return
        }

        val playerData = rankManager.getOrCreatePlayerData(targetPlayer.uniqueId, targetPlayer.name)
        playerData.undenyPermission(permission)
        permissionManager.updatePlayerPermissions(targetPlayer)

        sendMessage(sender, messagesManager.getFormattedMessage("permission.undeny-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.undeny-notification", "permission" to permission))
    }

    private fun handleCheck(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.permission.check")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
            return
        }

        val hasPermission = permissionManager.hasPermission(targetPlayer, permission)

        if (hasPermission) {
            sendMessage(sender, messagesManager.getFormattedMessage("permission.check-has", "player" to targetPlayer.name, "permission" to permission))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("permission.check-not-has", "player" to targetPlayer.name, "permission" to permission))
        }

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
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to playerName))
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