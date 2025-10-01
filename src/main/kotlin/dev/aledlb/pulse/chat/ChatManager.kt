package dev.aledlb.pulse.chat

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import org.bukkit.Bukkit
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextReplacementConfig
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

class ChatManager : Listener {

    // Configuration
    private var chatEnabled = true
    private var chatFormat = "{prefix}{player}{suffix}: {message}"
    private var tabFormat = "{prefix}{player}{suffix}"
    private var nametagFormat = "{prefix}{player}{suffix}"
    private var enableRankColors = true
    private var enableChatColors = false
    private var chatColorPermission = "pulse.chat.color"
    private var enablePlayerHover = true

    // Tab list settings
    private var tabEnabled = true
    private var tabHeader = ""
    private var tabFooter = ""
    private var updateTabInterval = 20L // ticks

    // Nametag settings
    private var nametagEnabled = true
    private var nametagDistance = 64.0

    // Internal systems
    private val playerTeams = ConcurrentHashMap<String, Team>()
    private var tabUpdateTask: Int = -1

    fun initialize() {
        loadConfig()
        setupScoreboard()
        startTabUpdateTask()

        // Register events
        Bukkit.getPluginManager().registerEvents(this, Pulse.getPlugin())
    }

    private fun loadConfig() {
        val configManager = Pulse.getPlugin().configManager
        val config = configManager.getConfig("config.yml")

        if (config == null) {
            Logger.warn("Config not found for chat system, using defaults")
            return
        }

        val chatNode = config.node("chat")

        // Chat settings
        chatEnabled = chatNode.node("enabled").getBoolean(true)
        chatFormat = chatNode.node("format").getString("{prefix}{player}{suffix}: {message}") ?: "{prefix}{player}{suffix}: {message}"
        enableRankColors = chatNode.node("enable-rank-colors").getBoolean(true)
        enableChatColors = chatNode.node("enable-chat-colors").getBoolean(false)
        chatColorPermission = chatNode.node("chat-color-permission").getString("pulse.chat.color") ?: "pulse.chat.color"
        enablePlayerHover = chatNode.node("enable-player-hover").getBoolean(true)

        // Tab list settings
        val tabNode = chatNode.node("tab")
        tabEnabled = tabNode.node("enabled").getBoolean(true)
        tabFormat = tabNode.node("format").getString("{prefix}{player}{suffix}") ?: "{prefix}{player}{suffix}"
        tabHeader = tabNode.node("header").getString("") ?: ""
        tabFooter = tabNode.node("footer").getString("") ?: ""
        updateTabInterval = tabNode.node("update-interval").getLong(20L)

        // Nametag settings
        val nametagNode = chatNode.node("nametag")
        nametagEnabled = nametagNode.node("enabled").getBoolean(true)
        nametagFormat = nametagNode.node("format").getString("{prefix}{player}{suffix}") ?: "{prefix}{player}{suffix}"
        nametagDistance = nametagNode.node("distance").getDouble(64.0)

    }

    private fun setupScoreboard() {
        if (!nametagEnabled && !tabEnabled) return

        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return

        // Clean up existing teams
        scoreboard.teams.forEach { team ->
            if (team.name.startsWith("pulse_")) {
                team.unregister()
            }
        }

        playerTeams.clear()
    }

    private fun startTabUpdateTask() {
        if (!tabEnabled || updateTabInterval <= 0) return

        tabUpdateTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            Pulse.getPlugin(),
            { _ -> updateAllTabLists() },
            updateTabInterval,
            updateTabInterval
        ).hashCode()
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerChat(event: AsyncChatEvent) {
        if (!chatEnabled) return

        val player = event.player
        val rankManager = Pulse.getPlugin().rankManager
        val tagManager = Pulse.getPlugin().tagManager
        val economyManager = Pulse.getPlugin().economyManager
        val playtimeManager = Pulse.getPlugin().playtimeManager
        val playerData = rankManager.getPlayerData(player)

        // Get rank information
        val rank = rankManager.getRank(playerData.rank)
        val prefix = rank?.prefix ?: ""
        val suffix = rank?.suffix ?: ""

        // Get active tags
        val tags = tagManager.getFormattedTagsForPlayer(player, "display")

        // Get message as plain text
        val messageSerializer = LegacyComponentSerializer.legacySection()
        var message = messageSerializer.serialize(event.message())

        // Process message colors if enabled
        if (enableChatColors && player.hasPermission(chatColorPermission)) {
            message = translateColors(message)
        }

        // Set the renderer to use our custom format with hover
        event.renderer { _, _, _, _ ->
            buildChatMessage(player, prefix, suffix, tags, rank?.name ?: "", message,
                rankManager, economyManager, playtimeManager, messageSerializer)
        }
    }

    private fun buildChatMessage(
        player: Player,
        prefix: String,
        suffix: String,
        tags: String,
        rankName: String,
        message: String,
        rankManager: dev.aledlb.pulse.ranks.models.RankManager,
        economyManager: dev.aledlb.pulse.economy.EconomyManager,
        playtimeManager: dev.aledlb.pulse.playtime.PlaytimeManager,
        messageSerializer: LegacyComponentSerializer
    ): Component {
        // Build prefix component
        val prefixComponent = if (prefix.isNotEmpty()) {
            messageSerializer.deserialize(translateColors(prefix))
        } else {
            Component.empty()
        }

        // Build tags component
        val tagsComponent = if (tags.isNotEmpty()) {
            messageSerializer.deserialize(tags)
        } else {
            Component.empty()
        }

        // Build player name component with hover
        val playerNameComponent = if (enablePlayerHover) {
            Component.text(player.name)
                .hoverEvent(createPlayerHoverCard(player, rankName, rankManager, economyManager, playtimeManager))
        } else {
            Component.text(player.name)
        }

        // Build suffix component
        val suffixComponent = if (suffix.isNotEmpty()) {
            messageSerializer.deserialize(translateColors(suffix))
        } else {
            Component.empty()
        }

        // Build message component
        val messageComponent = messageSerializer.deserialize(message)

        // Combine all components based on chat format
        return when {
            chatFormat.contains("{tags}") -> {
                prefixComponent
                    .append(tagsComponent)
                    .append(playerNameComponent)
                    .append(suffixComponent)
                    .append(Component.text(": "))
                    .append(messageComponent)
            }
            else -> {
                prefixComponent
                    .append(playerNameComponent)
                    .append(suffixComponent)
                    .append(Component.text(": "))
                    .append(messageComponent)
            }
        }
    }

    private fun createPlayerHoverCard(
        player: Player,
        rankName: String,
        rankManager: dev.aledlb.pulse.ranks.models.RankManager,
        economyManager: dev.aledlb.pulse.economy.EconomyManager,
        playtimeManager: dev.aledlb.pulse.playtime.PlaytimeManager
    ): HoverEvent<Component> {
        val balance = economyManager.formatBalance(economyManager.getBalance(player))
        val playtime = playtimeManager.getFormattedPlaytime(player.uniqueId)
        val rank = rankManager.getRank(rankName)
        val rankDisplay = rank?.name ?: rankName

        val hoverText = Component.text()
            .append(Component.text("Player: ", NamedTextColor.GRAY))
            .append(Component.text(player.name, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Rank: ", NamedTextColor.GRAY))
            .append(Component.text(rankDisplay, NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("Balance: ", NamedTextColor.GRAY))
            .append(Component.text(balance, NamedTextColor.GREEN))
            .append(Component.newline())
            .append(Component.text("Playtime: ", NamedTextColor.GRAY))
            .append(Component.text(playtime, NamedTextColor.AQUA))
            .build()

        return HoverEvent.showText(hoverText)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Update nametag
        if (nametagEnabled) {
            updatePlayerNametag(player)
        }

        // Update tab list
        if (tabEnabled) {
            player.scheduler.runDelayed(Pulse.getPlugin(), { _ ->
                updatePlayerTab(player)
                updateTabHeader(player)
            }, null, 5L) // Delay to ensure player is fully loaded
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        cleanupPlayer(player)
    }

    private fun updatePlayerNametag(player: Player) {
        if (!nametagEnabled) return

        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return
        val rankManager = Pulse.getPlugin().rankManager
        val tagManager = Pulse.getPlugin().tagManager
        val playerData = rankManager.getPlayerData(player)
        val rank = rankManager.getRank(playerData.rank)

        // Create team name based on rank weight (for proper sorting)
        val weight = rank?.weight ?: 0
        val teamName = "pulse_${String.format("%03d", 999 - weight)}_${rank?.name ?: "default"}"

        // Clean up old team
        playerTeams[player.name]?.let { oldTeam ->
            oldTeam.removeEntry(player.name)
            if (oldTeam.entries.isEmpty()) {
                oldTeam.unregister()
            }
        }

        // Get or create team
        var team = scoreboard.getTeam(teamName)
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName)

            // Set team prefix and suffix
            val prefix = rank?.prefix ?: ""
            val suffix = rank?.suffix ?: ""
            val tags = tagManager.getFormattedTagsForPlayer(player, "display")

            val formattedPrefix = nametagFormat
                .replace("{prefix}", translateColors(prefix))
                .replace("{tags}", tags)
                .replace("{player}", "")
                .replace("{suffix}", "")
                .replace("{rank}", rank?.name ?: "")

            val formattedSuffix = nametagFormat
                .replace("{prefix}", "")
                .replace("{tags}", "")
                .replace("{player}", "")
                .replace("{suffix}", translateColors(suffix))
                .replace("{rank}", "")

            // Set team prefix/suffix using Adventure API
            val serializer = LegacyComponentSerializer.legacySection()
            team.prefix(serializer.deserialize(formattedPrefix.take(64)))
            team.suffix(serializer.deserialize(formattedSuffix.take(64)))

            // Configure team settings
            team.setAllowFriendlyFire(true)
            team.setCanSeeFriendlyInvisibles(true)
        }

        // Add player to team
        team.addEntry(player.name)
        playerTeams[player.name] = team

        // Set player's scoreboard
        player.scoreboard = scoreboard
    }

    private fun updatePlayerTab(player: Player) {
        if (!tabEnabled) return

        val rankManager = Pulse.getPlugin().rankManager
        val tagManager = Pulse.getPlugin().tagManager
        val playerData = rankManager.getPlayerData(player)
        val rank = rankManager.getRank(playerData.rank)

        val prefix = rank?.prefix ?: ""
        val suffix = rank?.suffix ?: ""
        val tags = tagManager.getFormattedTagsForPlayer(player, "display")

        val formattedName = tabFormat
            .replace("{prefix}", translateColors(prefix))
            .replace("{tags}", tags)
            .replace("{player}", player.name)
            .replace("{suffix}", translateColors(suffix))
            .replace("{rank}", rank?.name ?: "")
            .replace("{world}", player.world.name)

        // Update player list name using Adventure API
        val serializer = LegacyComponentSerializer.legacySection()
        player.playerListName(serializer.deserialize(formattedName))
    }

    private fun updateTabHeader(player: Player) {
        if (!tabEnabled || (tabHeader.isEmpty() && tabFooter.isEmpty())) return

        val processedHeader = processPlaceholders(tabHeader, player)
        val processedFooter = processPlaceholders(tabFooter, player)

        val serializer = LegacyComponentSerializer.legacySection()
        player.sendPlayerListHeaderAndFooter(
            serializer.deserialize(translateColors(processedHeader)),
            serializer.deserialize(translateColors(processedFooter))
        )
    }

    private fun updateAllTabLists() {
        if (!tabEnabled) return

        Bukkit.getOnlinePlayers().forEach { player ->
            updatePlayerTab(player)
        }
    }

    private fun processPlaceholders(text: String, player: Player): String {
        val rankManager = Pulse.getPlugin().rankManager
        val tagManager = Pulse.getPlugin().tagManager
        val economyManager = Pulse.getPlugin().economyManager
        val playerData = rankManager.getPlayerData(player)
        val rank = rankManager.getRank(playerData.rank)
        val tags = tagManager.getFormattedTagsForPlayer(player, "display")

        return text
            .replace("{player}", player.name)
            .replace("{displayname}", player.name)
            .replace("{prefix}", rank?.prefix ?: "")
            .replace("{suffix}", rank?.suffix ?: "")
            .replace("{tags}", tags)
            .replace("{rank}", rank?.name ?: "")
            .replace("{world}", player.world.name)
            .replace("{balance}", economyManager.formatBalance(economyManager.getBalance(player)))
            .replace("{online}", Bukkit.getOnlinePlayers().size.toString())
            .replace("{max}", Bukkit.getMaxPlayers().toString())
            .replace("\\n", "\n")
    }

    private fun cleanupPlayer(player: Player) {
        playerTeams[player.name]?.let { team ->
            team.removeEntry(player.name)
            if (team.entries.isEmpty()) {
                team.unregister()
            }
        }
        playerTeams.remove(player.name)
    }

    private fun translateColors(text: String): String {
        return text.replace('&', '§')
    }

    fun updatePlayerFormats(player: Player) {
        if (nametagEnabled) {
            updatePlayerNametag(player)
        }
        if (tabEnabled) {
            updatePlayerTab(player)
            updateTabHeader(player)
        }
    }

    fun updateAllPlayerFormats() {
        Bukkit.getOnlinePlayers().forEach { player ->
            updatePlayerFormats(player)
        }
    }

    fun reload() {
        // Stop existing tasks
        // Note: Cannot directly cancel global region tasks by ID in Folia
        tabUpdateTask = -1

        // Cleanup
        setupScoreboard()

        // Reload config
        loadConfig()

        // Restart systems
        setupScoreboard()
        startTabUpdateTask()

        // Update all players
        updateAllPlayerFormats()
    }

    fun shutdown() {
        // Note: Cannot directly cancel global region tasks by ID in Folia
        // The task will be automatically cancelled when the plugin is disabled
        tabUpdateTask = -1

        // Clean up all teams
        val scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard
        scoreboard?.teams?.forEach { team ->
            if (team.name.startsWith("pulse_")) {
                team.unregister()
            }
        }

        playerTeams.clear()
    }
}