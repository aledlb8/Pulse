package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
        if (!requirePermission(sender, "pulse.permission.add")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

        permissionManager.addPlayerPermission(targetPlayer, permission)
        sendMessage(sender, messagesManager.getFormattedMessage("permission.add-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.add-notification", "permission" to permission))
    }

    private fun handleRemove(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.permission.remove")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

        permissionManager.removePlayerPermission(targetPlayer, permission)
        sendMessage(sender, messagesManager.getFormattedMessage("permission.remove-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.remove-notification", "permission" to permission))
    }

    private fun handleDeny(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.permission.deny")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

        permissionManager.denyPlayerPermission(targetPlayer, permission)
        sendMessage(sender, messagesManager.getFormattedMessage("permission.deny-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.deny-notification", "permission" to permission))
    }

    private fun handleUndeny(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.permission.deny")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

        val playerData = rankManager.getOrCreatePlayerData(targetPlayer.uniqueId, targetPlayer.name)
        playerData.undenyPermission(permission)
        permissionManager.updatePlayerPermissions(targetPlayer)

        sendMessage(sender, messagesManager.getFormattedMessage("permission.undeny-success", "permission" to permission, "player" to targetPlayer.name))
        sendMessage(targetPlayer, messagesManager.getFormattedMessage("permission.undeny-notification", "permission" to permission))
    }

    private fun handleCheck(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.permission.check")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val permission = args[2]

        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

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
            sendMessage(sender, messagesManager.getMessage("permission.source-player-specific"))
        } else if (playerData.deniedPermissions.contains(permission)) {
            sendMessage(sender, messagesManager.getMessage("permission.source-player-denial"))
        } else if (rank?.hasPermission(permission) == true) {
            sendMessage(sender, messagesManager.getFormattedMessage("permission.source-rank", "rank" to rank.name))
        } else {
            sendMessage(sender, messagesManager.getMessage("permission.source-not-granted"))
        }
    }

    private fun handleList(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.permission.list")) return

        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val playerName = args[1]
        val targetPlayer = getOnlinePlayer(sender, playerName) ?: return

        val playerData = rankManager.getPlayerData(targetPlayer)
        val rank = rankManager.getRank(playerData.rank)
        val allPermissions = playerData.getAllPermissions(rankManager)

        sender.sendMessage(Component.empty())
        sendMessage(sender, messagesManager.getMessage("permission.list-header"))
        sendMessage(sender, messagesManager.getFormattedMessage("permission.list-player", "player" to targetPlayer.name))
        sendMessage(sender, messagesManager.getFormattedMessage("permission.list-rank", "rank" to (rank?.name ?: "Unknown")))
        sendMessage(sender, messagesManager.getFormattedMessage("permission.list-total", "count" to allPermissions.size.toString()))
        sendMessage(sender, messagesManager.getMessage("permission.list-footer"))

        if (playerData.permissions.isNotEmpty()) {
            sendMessage(sender, messagesManager.getMessage("permission.list-player-specific"))
            for (permission in playerData.permissions.sorted()) {
                sendMessage(sender, messagesManager.getFormattedMessage("permission.list-entry-allowed", "permission" to permission))
            }
        }

        if (playerData.deniedPermissions.isNotEmpty()) {
            sendMessage(sender, messagesManager.getMessage("permission.list-denied"))
            for (permission in playerData.deniedPermissions.sorted()) {
                sendMessage(sender, messagesManager.getFormattedMessage("permission.list-entry-denied", "permission" to permission))
            }
        }

        if (rank != null && rank.permissions.isNotEmpty()) {
            sendMessage(sender, messagesManager.getFormattedMessage("permission.list-rank-perms", "rank" to rank.name))
            for (permission in rank.permissions.sorted()) {
                val isDenied = playerData.deniedPermissions.contains(permission)
                sender.sendMessage(
                    Component.text(if (isDenied) "~ " else "+ ", if (isDenied) NamedTextColor.RED else NamedTextColor.YELLOW)
                        .append(Component.text(permission, NamedTextColor.GRAY))
                )
            }
        }

        sender.sendMessage(Component.empty())
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("Permission Commands:").color(NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/perm add <player> <permission> ", NamedTextColor.GRAY).append(Component.text("- Add permission", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/perm remove <player> <permission> ", NamedTextColor.GRAY).append(Component.text("- Remove permission", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/perm deny <player> <permission> ", NamedTextColor.GRAY).append(Component.text("- Deny permission", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/perm undeny <player> <permission> ", NamedTextColor.GRAY).append(Component.text("- Undeny permission", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/perm check <player> <permission> ", NamedTextColor.GRAY).append(Component.text("- Check permission", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/perm list <player> ", NamedTextColor.GRAY).append(Component.text("- List all permissions", NamedTextColor.WHITE)))
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