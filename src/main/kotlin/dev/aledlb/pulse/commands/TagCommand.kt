package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TagCommand : BaseCommand() {

    private val messagesManager get() = Pulse.getPlugin().messagesManager

    override val name = "tag"
    override val permission = "pulse.tag"
    override val description = "Manage player tags"
    override val usage = "/tag <subcommand>"

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            showHelp(sender)
            return
        }

        when (args[0].lowercase()) {
            "list" -> listTags(sender, args)
            "owned" -> listOwnedTags(sender, args)
            "active" -> listActiveTags(sender, args)
            "activate" -> activateTag(sender, args)
            "deactivate" -> deactivateTag(sender, args)
            "give" -> giveTag(sender, args)
            "remove" -> removeTag(sender, args)
            "create" -> createTag(sender, args)
            "edit" -> editTag(sender, args)
            "delete" -> deleteTag(sender, args)
            "info" -> tagInfo(sender, args)
            "reload" -> reloadTags(sender, args)
            else -> showHelp(sender)
        }
    }

    private fun listTags(sender: CommandSender, args: Array<out String>) {
        val tagManager = Pulse.getPlugin().tagManager
        val allTags = tagManager.getAllTags()

        if (allTags.isEmpty()) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.no-tags-available"))
            return
        }

        sendMessage(sender, "§6Available Tags (${allTags.size}):")
        allTags.forEach { tag ->
            val purchasableText = if (tag.purchasable) "§a[Shop]" else "§7[Not for sale]"
            sendMessage(sender, "§7- §e${tag.id} §7(${tag.name}§7) $purchasableText")
        }
    }

    private fun listOwnedTags(sender: CommandSender, args: Array<out String>) {
        val player = requirePlayer(sender) ?: return

        val targetPlayer = if (args.size > 1 && sender.hasPermission("pulse.tag.others")) {
            Bukkit.getPlayer(args[1]) ?: run {
                sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to args[1]))
                return
            }
        } else {
            player
        }

        val tagManager = Pulse.getPlugin().tagManager
        val playerData = tagManager.getPlayerTagData(targetPlayer)

        if (playerData.getOwnedTagsList().isEmpty()) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.no-owned-tags"))
            return
        }

        val playerName = if (targetPlayer == player) "Your" else "${targetPlayer.name}'s"
        sendMessage(sender, "§6$playerName Owned Tags (${playerData.getOwnedTagsList().size}):")

        playerData.getOwnedTagsList().forEach { tagId ->
            val tag = tagManager.getTag(tagId)
            if (tag != null) {
                val activeStatus = if (playerData.isTagActive(tagId)) "§a[Active]" else "§7[Inactive]"
                sendMessage(sender, "§7- §e$tagId §7(${tag.name}§7) $activeStatus")
            }
        }
    }

    private fun listActiveTags(sender: CommandSender, args: Array<out String>) {
        val player = requirePlayer(sender) ?: return

        val targetPlayer = if (args.size > 1 && sender.hasPermission("pulse.tag.others")) {
            Bukkit.getPlayer(args[1]) ?: run {
                sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to args[1]))
                return
            }
        } else {
            player
        }

        val tagManager = Pulse.getPlugin().tagManager
        val activeTags = tagManager.getActiveTagsForPlayer(targetPlayer)

        if (activeTags.isEmpty()) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.no-active-tags"))
            return
        }

        val playerName = if (targetPlayer == player) "Your" else "${targetPlayer.name}'s"
        sendMessage(sender, "§6$playerName Active Tags (${activeTags.size}):")
        activeTags.forEach { tag ->
            sendMessage(sender, "§7- §e${tag.id} §7(${tag.name}§7)")
        }
    }

    private fun activateTag(sender: CommandSender, args: Array<out String>) {
        val player = requirePlayer(sender) ?: return

        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val tagId = args[1]
        val tagManager = Pulse.getPlugin().tagManager

        if (tagManager.getTag(tagId) == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-not-exist", "tag" to tagId))
            return
        }

        if (tagManager.activateTag(player, tagId)) {
            val tag = tagManager.getTag(tagId)!!
            sendMessage(sender, messagesManager.getFormattedMessage("tag.activate-success", "tag" to tag.name))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.activate-failed", "tag" to tagId))
        }
    }

    private fun deactivateTag(sender: CommandSender, args: Array<out String>) {
        val player = requirePlayer(sender) ?: return

        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val tagId = args[1]
        val tagManager = Pulse.getPlugin().tagManager

        if (tagManager.getTag(tagId) == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-not-exist", "tag" to tagId))
            return
        }

        if (tagManager.deactivateTag(player, tagId)) {
            val tag = tagManager.getTag(tagId)!!
            sendMessage(sender, messagesManager.getFormattedMessage("tag.deactivate-success", "tag" to tag.name))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.deactivate-failed", "tag" to tagId))
        }
    }

    private fun giveTag(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.tag.give")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetPlayer = Bukkit.getPlayer(args[1]) ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to args[1]))
            return
        }

        val tagId = args[2]
        val tagManager = Pulse.getPlugin().tagManager
        val tag = tagManager.getTag(tagId)

        if (tag == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-not-exist", "tag" to tagId))
            return
        }

        if (tagManager.giveTagToPlayer(targetPlayer, tagId)) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.give-success", "tag" to tag.name, "player" to targetPlayer.name))
            sendMessage(targetPlayer, messagesManager.getFormattedMessage("tag.give-notification", "tag" to tag.name))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.player-already-has-tag", "player" to targetPlayer.name))
        }
    }

    private fun removeTag(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.tag.remove")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetPlayer = Bukkit.getPlayer(args[1]) ?: run {
            sendMessage(sender, messagesManager.getFormattedMessage("general.player-not-online", "player" to args[1]))
            return
        }

        val tagId = args[2]
        val tagManager = Pulse.getPlugin().tagManager
        val tag = tagManager.getTag(tagId)

        if (tag == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-not-exist", "tag" to tagId))
            return
        }

        if (tagManager.removeTagFromPlayer(targetPlayer, tagId)) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.remove-success", "tag" to tag.name, "player" to targetPlayer.name))
            sendMessage(targetPlayer, messagesManager.getFormattedMessage("tag.remove-notification", "tag" to tag.name))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.player-doesnt-have-tag", "player" to targetPlayer.name))
        }
    }

    private fun createTag(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.tag.create")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val id = args[1]
        val name = args[2]
        val prefix = if (args.size > 3) args[3] else ""
        val suffix = if (args.size > 4) args[4] else ""

        val tagManager = Pulse.getPlugin().tagManager

        if (tagManager.createTag(id, name, prefix, suffix)) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.create-success", "tag" to "$id §7($name§7)"))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-already-exists", "tag" to id))
        }
    }

    private fun editTag(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.tag.edit")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 4) {
            sendUsage(sender)
            return
        }

        val id = args[1]
        val property = args[2].lowercase()
        val value = args[3]

        val tagManager = Pulse.getPlugin().tagManager

        if (tagManager.getTag(id) == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-not-exist", "tag" to id))
            return
        }

        val success = when (property) {
            "name" -> tagManager.editTag(id, name = value)
            "prefix" -> tagManager.editTag(id, prefix = value)
            "suffix" -> tagManager.editTag(id, suffix = value)
            "price" -> {
                val price = value.toDoubleOrNull()
                if (price != null) {
                    tagManager.editTag(id, price = price)
                } else {
                    sendMessage(sender, "§cInvalid price: $value")
                    return
                }
            }
            "purchasable" -> {
                val purchasable = value.toBooleanStrictOrNull()
                if (purchasable != null) {
                    tagManager.editTag(id, purchasable = purchasable)
                } else {
                    sendMessage(sender, "§cInvalid boolean: $value (use true/false)")
                    return
                }
            }
            "enabled" -> {
                val enabled = value.toBooleanStrictOrNull()
                if (enabled != null) {
                    tagManager.editTag(id, enabled = enabled)
                } else {
                    sendMessage(sender, "§cInvalid boolean: $value (use true/false)")
                    return
                }
            }
            else -> {
                sendMessage(sender, messagesManager.getFormattedMessage("general.unknown-subcommand", "subcommand" to property))
                return
            }
        }

        if (success) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.edit-success", "tag" to id))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.edit-success", "tag" to id))
        }
    }

    private fun deleteTag(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.tag.delete")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val id = args[1]
        val tagManager = Pulse.getPlugin().tagManager
        val tag = tagManager.getTag(id)

        if (tag == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-not-exist", "tag" to id))
            return
        }

        if (tagManager.deleteTag(id)) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.delete-success", "tag" to id))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.delete-success", "tag" to id))
        }
    }

    private fun tagInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sendUsage(sender)
            return
        }

        val id = args[1]
        val tagManager = Pulse.getPlugin().tagManager
        val tag = tagManager.getTag(id)

        if (tag == null) {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-not-exist", "tag" to id))
            return
        }

        sendMessage(sender, "§6Tag Information: §e${tag.id}")
        sendMessage(sender, "§7Name: §f${tag.name}")
        sendMessage(sender, "§7Prefix: '${tag.getFormattedPrefix()}§7'")
        sendMessage(sender, "§7Suffix: '${tag.getFormattedSuffix()}§7'")
        sendMessage(sender, "§7Material: §f${tag.material}")
        sendMessage(sender, "§7Price: §f${tag.price}")
        sendMessage(sender, "§7Permission: §f${tag.permission ?: "None"}")
        sendMessage(sender, "§7Enabled: ${if (tag.enabled) "§aYes" else "§cNo"}")
        sendMessage(sender, "§7Purchasable: ${if (tag.purchasable) "§aYes" else "§cNo"}")

        if (tag.description.isNotEmpty()) {
            sendMessage(sender, "§7Description:")
            tag.getFormattedDescription().forEach { line ->
                sendMessage(sender, "§7  $line")
            }
        }
    }

    private fun reloadTags(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pulse.tag.reload")) {
            sendMessage(sender, messagesManager.noPermission())
            return
        }

        val tagManager = Pulse.getPlugin().tagManager
        tagManager.reload()
        sendMessage(sender, messagesManager.getFormattedMessage("tag.reload-success"))
    }

    private fun showHelp(sender: CommandSender) {
        sendMessage(sender, "§6Tag Commands:")
        sendMessage(sender, "§7/tag list §f- List all available tags")
        sendMessage(sender, "§7/tag owned [player] §f- List owned tags")
        sendMessage(sender, "§7/tag active [player] §f- List active tags")
        sendMessage(sender, "§7/tag activate <id> §f- Activate a tag")
        sendMessage(sender, "§7/tag deactivate <id> §f- Deactivate a tag")
        sendMessage(sender, "§7/tag info <id> §f- View tag information")

        if (sender.hasPermission("pulse.tag.give")) {
            sendMessage(sender, "§7/tag give <player> <id> §f- Give a tag to player")
            sendMessage(sender, "§7/tag remove <player> <id> §f- Remove tag from player")
        }

        if (sender.hasPermission("pulse.tag.create")) {
            sendMessage(sender, "§7/tag create <id> <name> <display> §f- Create new tag")
            sendMessage(sender, "§7/tag edit <id> <property> <value> §f- Edit tag")
            sendMessage(sender, "§7/tag delete <id> §f- Delete tag")
        }

        if (sender.hasPermission("pulse.tag.reload")) {
            sendMessage(sender, "§7/tag reload §f- Reload tag system")
        }
    }

    override fun getTabCompletions(sender: CommandSender, args: Array<out String>): List<String> {
        val tagManager = if (Pulse.getPlugin().isPluginFullyLoaded()) {
            Pulse.getPlugin().tagManager
        } else {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                val subcommands = mutableListOf("list", "owned", "active", "activate", "deactivate", "info")
                if (sender.hasPermission("pulse.tag.give")) {
                    subcommands.addAll(listOf("give", "remove"))
                }
                if (sender.hasPermission("pulse.tag.create")) {
                    subcommands.addAll(listOf("create", "edit", "delete"))
                }
                if (sender.hasPermission("pulse.tag.reload")) {
                    subcommands.add("reload")
                }
                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> when (args[0].lowercase()) {
                "owned", "active" -> if (sender.hasPermission("pulse.tag.others")) {
                    Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1]) }
                } else emptyList()
                "activate" -> {
                    // Only show tags that the player owns
                    if (sender is Player) {
                        val playerData = tagManager.getPlayerTagData(sender)
                        playerData.ownedTags.filter { it.startsWith(args[1]) }.toList()
                    } else {
                        emptyList()
                    }
                }
                "deactivate" -> {
                    // Only show tags that the player has active
                    if (sender is Player) {
                        val playerData = tagManager.getPlayerTagData(sender)
                        playerData.activeTags.filter { it.startsWith(args[1]) }.toList()
                    } else {
                        emptyList()
                    }
                }
                "info", "edit", "delete" -> {
                    tagManager.getAllTags().map { it.id }.filter { it.startsWith(args[1]) }
                }
                "give", "remove" -> if (sender.hasPermission("pulse.tag.give")) {
                    Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1]) }
                } else emptyList()
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "give", "remove" -> if (sender.hasPermission("pulse.tag.give")) {
                    tagManager.getAllTags().map { it.id }.filter { it.startsWith(args[2]) }
                } else emptyList()
                "edit" -> listOf("name", "prefix", "suffix", "price", "purchasable", "enabled")
                    .filter { it.startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}