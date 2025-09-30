package dev.aledlb.pulse.tags

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.database.DatabaseManager
import dev.aledlb.pulse.tags.models.PlayerTagData
import dev.aledlb.pulse.tags.models.Tag
import dev.aledlb.pulse.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TagManager(private val databaseManager: DatabaseManager) {

    private val tags = ConcurrentHashMap<String, Tag>()
    private val playerTagData = ConcurrentHashMap<UUID, PlayerTagData>()

    fun initialize() {
        loadTagsFromConfig()
        loadPlayerTagDataFromDatabase()
        Logger.success("Tag system initialized with ${tags.size} tags")
    }

    private fun loadTagsFromConfig() {
        val configManager = Pulse.getPlugin().configManager
        val tagsConfig = configManager.getConfig("tags.yml")

        if (tagsConfig == null) {
            Logger.warn("Tags configuration not found")
            return
        }

        // Clear existing tags
        tags.clear()

        // Load tag definitions
        val definitionsNode = tagsConfig.node("definitions")
        for (tagKey in definitionsNode.childrenMap().keys) {
            try {
                val tagNode = definitionsNode.node(tagKey.toString())

                val name = tagNode.node("name").getString(tagKey.toString()) ?: tagKey.toString()
                val prefix = tagNode.node("prefix").getString("") ?: ""
                val suffix = tagNode.node("suffix").getString("") ?: ""
                val description = tagNode.node("description").getList(String::class.java) ?: emptyList()
                val materialName = tagNode.node("material").getString("NAME_TAG") ?: "NAME_TAG"
                val material = try {
                    Material.valueOf(materialName)
                } catch (e: IllegalArgumentException) {
                    Logger.warn("Invalid material '$materialName' for tag '$tagKey', using NAME_TAG")
                    Material.NAME_TAG
                }
                val price = tagNode.node("price").getDouble(0.0)
                val permission = tagNode.node("permission").getString()
                val enabled = tagNode.node("enabled").getBoolean(true)
                val purchasable = tagNode.node("purchasable").getBoolean(false)

                val tag = Tag(
                    id = tagKey.toString(),
                    name = name,
                    prefix = prefix,
                    suffix = suffix,
                    description = description,
                    material = material,
                    price = price,
                    permission = permission,
                    enabled = enabled,
                    purchasable = purchasable
                )

                tags[tagKey.toString()] = tag
                Logger.debug("Loaded tag: $tagKey")
            } catch (e: Exception) {
                Logger.error("Failed to load tag '$tagKey'", e)
            }
        }

        Logger.info("Loaded ${tags.size} tags from configuration")
    }

    private fun loadPlayerTagDataFromDatabase() {
        runBlocking {
            try {
                val playerTagRows = databaseManager.loadAllPlayerTagData()
                playerTagRows.forEach { row ->
                    val data = PlayerTagData(
                        uuid = row.uuid,
                        name = row.name,
                        ownedTags = row.ownedTags.toMutableSet(),
                        activeTags = row.activeTags.toMutableSet()
                    )
                    playerTagData[row.uuid] = data
                }
                Logger.info("Loaded ${playerTagRows.size} player tag data entries from database")
            } catch (e: Exception) {
                Logger.error("Failed to load player tag data from database", e)
            }
        }
    }

    // Tag Management
    fun createTag(
        id: String,
        name: String,
        prefix: String = "",
        suffix: String = "",
        description: List<String> = emptyList(),
        material: Material = Material.NAME_TAG,
        price: Double = 0.0,
        permission: String? = null,
        enabled: Boolean = true,
        purchasable: Boolean = false
    ): Boolean {
        if (tags.containsKey(id)) return false

        val tag = Tag(
            id = id,
            name = name,
            prefix = prefix,
            suffix = suffix,
            description = description,
            material = material,
            price = price,
            permission = permission,
            enabled = enabled,
            purchasable = purchasable
        )

        tags[id] = tag
        saveTagsToConfig()
        return true
    }

    fun editTag(
        id: String,
        name: String? = null,
        prefix: String? = null,
        suffix: String? = null,
        description: List<String>? = null,
        material: Material? = null,
        price: Double? = null,
        permission: String? = null,
        enabled: Boolean? = null,
        purchasable: Boolean? = null
    ): Boolean {
        val existingTag = tags[id] ?: return false

        val updatedTag = existingTag.copy(
            name = name ?: existingTag.name,
            prefix = prefix ?: existingTag.prefix,
            suffix = suffix ?: existingTag.suffix,
            description = description ?: existingTag.description,
            material = material ?: existingTag.material,
            price = price ?: existingTag.price,
            permission = permission ?: existingTag.permission,
            enabled = enabled ?: existingTag.enabled,
            purchasable = purchasable ?: existingTag.purchasable
        )

        tags[id] = updatedTag
        saveTagsToConfig()
        return true
    }

    fun deleteTag(id: String): Boolean {
        val removed = tags.remove(id) != null
        if (removed) {
            // Remove tag from all players
            playerTagData.values.forEach { data ->
                data.removeTag(id)
            }
            saveTagsToConfig()
        }
        return removed
    }

    fun getTag(id: String): Tag? = tags[id]

    fun getAllTags(): Collection<Tag> = tags.values.filter { it.enabled }

    fun getPurchasableTags(): Collection<Tag> = tags.values.filter { it.enabled && it.purchasable }

    // Player Tag Management
    fun getPlayerTagData(uuid: UUID): PlayerTagData? = playerTagData[uuid]

    fun getPlayerTagData(player: Player): PlayerTagData = getOrCreatePlayerTagData(player.uniqueId, player.name)

    fun getOrCreatePlayerTagData(uuid: UUID, name: String): PlayerTagData {
        return playerTagData.computeIfAbsent(uuid) {
            val newData = PlayerTagData(uuid = uuid, name = name)

            // Save new player to database asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    databaseManager.savePlayerTagData(uuid, name, emptySet(), emptySet())
                } catch (e: Exception) {
                    Logger.error("Failed to save new player tag data to database: $name", e)
                }
            }

            newData
        }
    }

    fun giveTagToPlayer(player: Player, tagId: String): Boolean {
        val tag = tags[tagId] ?: return false
        if (!tag.enabled) return false

        val playerData = getPlayerTagData(player)
        val success = playerData.addTag(tagId)

        if (success) {
            // Save to database asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    databaseManager.savePlayerTagData(
                        player.uniqueId,
                        player.name,
                        playerData.ownedTags,
                        playerData.activeTags
                    )
                } catch (e: Exception) {
                    Logger.error("Failed to save player tag data to database: ${player.name}", e)
                }
            }
        }

        return success
    }

    fun removeTagFromPlayer(player: Player, tagId: String): Boolean {
        val playerData = getPlayerTagData(player)
        val success = playerData.removeTag(tagId)

        if (success) {
            // Save to database asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    databaseManager.savePlayerTagData(
                        player.uniqueId,
                        player.name,
                        playerData.ownedTags,
                        playerData.activeTags
                    )
                } catch (e: Exception) {
                    Logger.error("Failed to save player tag data to database: ${player.name}", e)
                }
            }

            // Update formatting if chat manager is available
            if (Pulse.getPlugin().isPluginFullyLoaded()) {
                Pulse.getPlugin().chatManager.updatePlayerFormats(player)
            }
        }

        return success
    }

    fun activateTag(player: Player, tagId: String): Boolean {
        val playerData = getPlayerTagData(player)
        val success = playerData.activateTag(tagId)

        if (success) {
            // Save to database asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    databaseManager.savePlayerTagData(
                        player.uniqueId,
                        player.name,
                        playerData.ownedTags,
                        playerData.activeTags
                    )
                } catch (e: Exception) {
                    Logger.error("Failed to save player tag data to database: ${player.name}", e)
                }
            }

            // Update formatting
            if (Pulse.getPlugin().isPluginFullyLoaded()) {
                Pulse.getPlugin().chatManager.updatePlayerFormats(player)
            }
        }

        return success
    }

    fun deactivateTag(player: Player, tagId: String): Boolean {
        val playerData = getPlayerTagData(player)
        val success = playerData.deactivateTag(tagId)

        if (success) {
            // Save to database asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    databaseManager.savePlayerTagData(
                        player.uniqueId,
                        player.name,
                        playerData.ownedTags,
                        playerData.activeTags
                    )
                } catch (e: Exception) {
                    Logger.error("Failed to save player tag data to database: ${player.name}", e)
                }
            }

            // Update formatting
            if (Pulse.getPlugin().isPluginFullyLoaded()) {
                Pulse.getPlugin().chatManager.updatePlayerFormats(player)
            }
        }

        return success
    }

    // Formatting Support
    fun getActiveTagsForPlayer(player: Player): List<Tag> {
        val playerData = getPlayerTagData(player)
        return playerData.getActiveTagsList().mapNotNull { tagId ->
            getTag(tagId)
        }
    }

    fun getFormattedTagsForPlayer(player: Player, format: String): String {
        val activeTags = getActiveTagsForPlayer(player)

        return when (format.lowercase()) {
            "prefix" -> activeTags.joinToString("") { it.getFormattedPrefix() }
            "suffix" -> activeTags.joinToString("") { it.getFormattedSuffix() }
            "display" -> activeTags.joinToString("") { it.getFormattedPrefix() }
            else -> activeTags.joinToString("") { it.getFormattedPrefix() }
        }
    }

    private fun saveTagsToConfig() {
        val configManager = Pulse.getPlugin().configManager
        val tagsConfig = configManager.getConfig("tags.yml") ?: return

        // Clear existing definitions
        tagsConfig.node("definitions").set(null)

        // Save tag definitions
        val definitionsNode = tagsConfig.node("definitions")
        for (tag in tags.values) {
            val tagNode = definitionsNode.node(tag.id)
            tagNode.node("name").set(tag.name)
            tagNode.node("prefix").set(tag.prefix)
            tagNode.node("suffix").set(tag.suffix)
            tagNode.node("description").setList(String::class.java, tag.description)
            tagNode.node("material").set(tag.material.name)
            tagNode.node("price").set(tag.price)
            tagNode.node("permission").set(tag.permission)
            tagNode.node("enabled").set(tag.enabled)
            tagNode.node("purchasable").set(tag.purchasable)
        }

        configManager.saveConfig("tags.yml")
        Logger.info("Saved ${tags.size} tags to configuration")
    }

    fun reload() {
        loadTagsFromConfig()
    }

    fun saveAllData() {
        // Save all player tag data to database synchronously during shutdown
        runBlocking {
            playerTagData.forEach { (uuid, data) ->
                try {
                    databaseManager.savePlayerTagData(uuid, data.name, data.ownedTags, data.activeTags)
                } catch (e: Exception) {
                    Logger.error("Failed to save tag data for ${data.name}", e)
                }
            }
        }
    }
}