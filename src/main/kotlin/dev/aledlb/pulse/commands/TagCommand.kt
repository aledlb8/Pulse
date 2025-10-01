package dev.aledlb.pulse.commands

import dev.aledlb.pulse.Pulse
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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

        sendMessage(sender, messagesManager.getFormattedMessage("tag.list-available-header", "count" to allTags.size.toString()))
        allTags.forEach { tag ->
            val purchasableText = if (tag.purchasable) messagesManager.getMessage("tag.list-purchasable-yes") else messagesManager.getMessage("tag.list-purchasable-no")
            sendMessage(sender, messagesManager.getFormattedMessage("tag.list-available-entry", "id" to tag.id, "name" to tag.name, "status" to purchasableText))
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

        val headerMessage = if (targetPlayer == player) {
            messagesManager.getFormattedMessage("tag.list-owned-header-self", "count" to playerData.getOwnedTagsList().size.toString())
        } else {
            messagesManager.getFormattedMessage("tag.list-owned-header-other", "player" to targetPlayer.name, "count" to playerData.getOwnedTagsList().size.toString())
        }
        sendMessage(sender, headerMessage)

        playerData.getOwnedTagsList().forEach { tagId ->
            val tag = tagManager.getTag(tagId)
            if (tag != null) {
                val activeStatus = if (playerData.isTagActive(tagId)) messagesManager.getMessage("tag.list-active-status-yes") else messagesManager.getMessage("tag.list-active-status-no")
                sendMessage(sender, messagesManager.getFormattedMessage("tag.list-owned-entry", "id" to tagId, "name" to tag.name, "status" to activeStatus))
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

        val headerMessage = if (targetPlayer == player) {
            messagesManager.getFormattedMessage("tag.list-active-header-self", "count" to activeTags.size.toString())
        } else {
            messagesManager.getFormattedMessage("tag.list-active-header-other", "player" to targetPlayer.name, "count" to activeTags.size.toString())
        }
        sendMessage(sender, headerMessage)
        activeTags.forEach { tag ->
            sendMessage(sender, messagesManager.getFormattedMessage("tag.list-active-entry", "id" to tag.id, "name" to tag.name))
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
        if (!requirePermission(sender, "pulse.tag.give")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetPlayer = getOnlinePlayer(sender, args[1]) ?: return

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
        if (!requirePermission(sender, "pulse.tag.remove")) return

        if (args.size < 3) {
            sendUsage(sender)
            return
        }

        val targetPlayer = getOnlinePlayer(sender, args[1]) ?: return

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
        if (!requirePermission(sender, "pulse.tag.create")) return

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
            sendMessage(sender, messagesManager.getFormattedMessage("tag.create-success", "tag" to "$id ($name)"))
        } else {
            sendMessage(sender, messagesManager.getFormattedMessage("tag.tag-already-exists", "tag" to id))
        }
    }

    private fun editTag(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.tag.edit")) return

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
                    sendMessage(sender, messagesManager.getFormattedMessage("tag.edit-invalid-price", "value" to value))
                    return
                }
            }
            "purchasable" -> {
                val purchasable = value.toBooleanStrictOrNull()
                if (purchasable != null) {
                    tagManager.editTag(id, purchasable = purchasable)
                } else {
                    sendMessage(sender, messagesManager.getFormattedMessage("tag.edit-invalid-boolean", "value" to value))
                    return
                }
            }
            "enabled" -> {
                val enabled = value.toBooleanStrictOrNull()
                if (enabled != null) {
                    tagManager.editTag(id, enabled = enabled)
                } else {
                    sendMessage(sender, messagesManager.getFormattedMessage("tag.edit-invalid-boolean", "value" to value))
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
        if (!requirePermission(sender, "pulse.tag.delete")) return

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

        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-header", "id" to tag.id))
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-name", "name" to tag.name))
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-prefix", "prefix" to tag.getFormattedPrefix()))
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-suffix", "suffix" to tag.getFormattedSuffix()))
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-material", "material" to tag.material.toString()))
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-price", "price" to tag.price.toString()))
        val permissionText = tag.permission ?: messagesManager.getMessage("tag.info-permission-none")
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-permission", "permission" to permissionText))
        val enabledStatus = if (tag.enabled) messagesManager.getMessage("tag.info-yes") else messagesManager.getMessage("tag.info-no")
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-enabled", "status" to enabledStatus))
        val purchasableStatus = if (tag.purchasable) messagesManager.getMessage("tag.info-yes") else messagesManager.getMessage("tag.info-no")
        sendMessage(sender, messagesManager.getFormattedMessage("tag.info-purchasable", "status" to purchasableStatus))

        if (tag.description.isNotEmpty()) {
            sendMessage(sender, messagesManager.getMessage("tag.info-description-header"))
            tag.getFormattedDescription().forEach { line ->
                sendMessage(sender, messagesManager.getFormattedMessage("tag.info-description-line", "line" to line))
            }
        }
    }

    private fun reloadTags(sender: CommandSender, args: Array<out String>) {
        if (!requirePermission(sender, "pulse.tag.reload")) return

        val tagManager = Pulse.getPlugin().tagManager
        tagManager.reload()
        sendMessage(sender, messagesManager.getFormattedMessage("tag.reload-success"))
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("Tag Commands:").color(NamedTextColor.GOLD))
        sender.sendMessage(Component.text("/tag list ", NamedTextColor.GRAY).append(Component.text("- List all available tags", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/tag owned [player] ", NamedTextColor.GRAY).append(Component.text("- List owned tags", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/tag active [player] ", NamedTextColor.GRAY).append(Component.text("- List active tags", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/tag activate <id> ", NamedTextColor.GRAY).append(Component.text("- Activate a tag", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/tag deactivate <id> ", NamedTextColor.GRAY).append(Component.text("- Deactivate a tag", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("/tag info <id> ", NamedTextColor.GRAY).append(Component.text("- View tag information", NamedTextColor.WHITE)))

        if (sender.hasPermission("pulse.tag.give")) {
            sender.sendMessage(Component.text("/tag give <player> <id> ", NamedTextColor.GRAY).append(Component.text("- Give a tag to player", NamedTextColor.WHITE)))
            sender.sendMessage(Component.text("/tag remove <player> <id> ", NamedTextColor.GRAY).append(Component.text("- Remove tag from player", NamedTextColor.WHITE)))
        }

        if (sender.hasPermission("pulse.tag.create")) {
            sender.sendMessage(Component.text("/tag create <id> <name> <display> ", NamedTextColor.GRAY).append(Component.text("- Create new tag", NamedTextColor.WHITE)))
            sender.sendMessage(Component.text("/tag edit <id> <property> <value> ", NamedTextColor.GRAY).append(Component.text("- Edit tag", NamedTextColor.WHITE)))
            sender.sendMessage(Component.text("/tag delete <id> ", NamedTextColor.GRAY).append(Component.text("- Delete tag", NamedTextColor.WHITE)))
        }

        if (sender.hasPermission("pulse.tag.reload")) {
            sender.sendMessage(Component.text("/tag reload ", NamedTextColor.GRAY).append(Component.text("- Reload tag system", NamedTextColor.WHITE)))
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