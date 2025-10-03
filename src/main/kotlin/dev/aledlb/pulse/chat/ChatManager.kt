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
    private var updateTabInterval = 20L // ticks
    private var headerFooterInterval = 40L // ticks
    private var showPing = true
    private var pingPosition = "after"
    private var pingFormat = " &8[{ping_color}{ping}ms&8]"
    private val pingColors = mutableMapOf(
        "excellent" to "&a",
        "good" to "&2",
        "okay" to "&e",
        "poor" to "&6",
        "bad" to "&c",
        "terrible" to "&4"
    )
    private var headerLines = mutableListOf<String>()
    private var footerLines = mutableListOf<String>()
    private var sortingEnabled = true
    private var sortBy = "rank"
    private var reverseRankOrder = false
    private var headerEnabled = true
    private var footerEnabled = true

    // Nametag settings
    private var nametagEnabled = true
    private var nametagDistance = 64.0

    // Join/Quit message settings
    private var joinMessageEnabled = true
    private var joinMessage = "&8[&a+&8] &a%player%"
    private var quitMessageEnabled = true
    private var quitMessage = "&8[&c-&8] &c%player%"

    // Internal systems
    private val playerTeams = ConcurrentHashMap<String, Team>()
    private var tabUpdateTask: Int = -1
    private var headerFooterTask: Int = -1
    private val serverStartTime = System.currentTimeMillis()

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

        // Ping settings
        showPing = tabNode.node("show-ping").getBoolean(true)
        pingPosition = tabNode.node("ping-position").getString("after") ?: "after"
        pingFormat = tabNode.node("ping-format").getString(" &8[{ping_color}{ping}ms&8]") ?: " &8[{ping_color}{ping}ms&8]"

        val pingColorsNode = tabNode.node("ping-colors")
        if (!pingColorsNode.virtual()) {
            pingColors["excellent"] = pingColorsNode.node("excellent").getString("&a") ?: "&a"
            pingColors["good"] = pingColorsNode.node("good").getString("&2") ?: "&2"
            pingColors["okay"] = pingColorsNode.node("okay").getString("&e") ?: "&e"
            pingColors["poor"] = pingColorsNode.node("poor").getString("&6") ?: "&6"
            pingColors["bad"] = pingColorsNode.node("bad").getString("&c") ?: "&c"
            pingColors["terrible"] = pingColorsNode.node("terrible").getString("&4") ?: "&4"
        }

        // Header settings
        val headerNode = tabNode.node("header")
        headerEnabled = headerNode.node("enabled").getBoolean(true)
        headerLines.clear()
        headerLines.addAll(headerNode.node("lines").getList(String::class.java) ?: emptyList())

        // Footer settings
        val footerNode = tabNode.node("footer")
        footerEnabled = footerNode.node("enabled").getBoolean(true)
        footerLines.clear()
        footerLines.addAll(footerNode.node("lines").getList(String::class.java) ?: emptyList())

        // Sorting settings
        val sortingNode = tabNode.node("sorting")
        sortingEnabled = sortingNode.node("enabled").getBoolean(true)
        sortBy = sortingNode.node("sort-by").getString("rank") ?: "rank"
        reverseRankOrder = sortingNode.node("reverse-rank-order").getBoolean(false)

        updateTabInterval = tabNode.node("update-interval").getLong(20L)
        headerFooterInterval = tabNode.node("header-footer-interval").getLong(40L)
        if (headerFooterInterval <= 0) headerFooterInterval = updateTabInterval

        // Nametag settings
        val nametagNode = chatNode.node("nametag")
        nametagEnabled = nametagNode.node("enabled").getBoolean(true)
        nametagFormat = nametagNode.node("format").getString("{prefix}{player}{suffix}") ?: "{prefix}{player}{suffix}"
        nametagDistance = nametagNode.node("distance").getDouble(64.0)

        // Join/Quit message settings
        val messagesNode = chatNode.node("messages")
        val joinNode = messagesNode.node("join")
        joinMessageEnabled = joinNode.node("enabled").getBoolean(true)
        joinMessage = joinNode.node("message").getString("&8[&a+&8] &a%player%") ?: "&8[&a+&8] &a%player%"

        val quitNode = messagesNode.node("quit")
        quitMessageEnabled = quitNode.node("enabled").getBoolean(true)
        quitMessage = quitNode.node("message").getString("&8[&c-&8] &c%player%") ?: "&8[&c-&8] &c%player%"

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

        // Start separate header/footer update task if configured
        if (headerFooterInterval != updateTabInterval && headerFooterInterval > 0) {
            headerFooterTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                Pulse.getPlugin(),
                { _ -> updateAllHeaders() },
                headerFooterInterval,
                headerFooterInterval
            ).hashCode()
        }
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

        // Extract color before {player} placeholder to apply to player name
        val translatedFormat = translateColors(chatFormat)
        val playerColorMatch = Regex("((?:[§&][0-9a-fk-orA-FK-OR])+)[^§&]*\\{player}").find(translatedFormat)
        val playerColor = playerColorMatch?.groupValues?.get(1)?.replace("&", "§") ?: ""

        // Build player name component with hover and color
        val playerNameComponent = if (enablePlayerHover) {
            messageSerializer.deserialize(playerColor + player.name)
                .hoverEvent(createPlayerHoverCard(player, rankName, rankManager, economyManager, playtimeManager))
        } else {
            messageSerializer.deserialize(playerColor + player.name)
        }

        // Build suffix component
        val suffixComponent = if (suffix.isNotEmpty()) {
            messageSerializer.deserialize(translateColors(suffix))
        } else {
            Component.empty()
        }

        // Extract the color before {message} placeholder to apply to message
        val messageColorMatch = Regex("((?:[§&][0-9a-fk-orA-FK-OR])+)[^§&]*\\{message}").find(translatedFormat)
        val messageColor = messageColorMatch?.groupValues?.get(1) ?: ""

        // Build message component with inherited color if needed
        val messageWithColor = if (messageColor.isNotEmpty() && !message.contains('§') && !message.contains('&')) {
            messageColor + message
        } else {
            message
        }
        val messageComponent = messageSerializer.deserialize(messageWithColor)

        // Build component by directly replacing placeholders
        var result: Component = Component.empty()

        // Split format into parts and build component
        var currentFormat = translatedFormat

        // Process each placeholder in order
        val parts = mutableListOf<Component>()
        var remaining = currentFormat

        while (remaining.isNotEmpty()) {
            when {
                remaining.startsWith("{prefix}") -> {
                    parts.add(prefixComponent)
                    remaining = remaining.removePrefix("{prefix}")
                }
                remaining.startsWith("{tags}") -> {
                    parts.add(tagsComponent)
                    remaining = remaining.removePrefix("{tags}")
                }
                remaining.startsWith("{player}") -> {
                    parts.add(playerNameComponent)
                    remaining = remaining.removePrefix("{player}")
                }
                remaining.startsWith("{suffix}") -> {
                    parts.add(suffixComponent)
                    remaining = remaining.removePrefix("{suffix}")
                }
                remaining.startsWith("{message}") -> {
                    parts.add(messageComponent)
                    remaining = remaining.removePrefix("{message}")
                }
                else -> {
                    // Find next placeholder or end of string
                    val nextPlaceholder = listOf(
                        remaining.indexOf("{prefix}"),
                        remaining.indexOf("{tags}"),
                        remaining.indexOf("{player}"),
                        remaining.indexOf("{suffix}"),
                        remaining.indexOf("{message}")
                    ).filter { it > 0 }.minOrNull() ?: remaining.length

                    val text = remaining.substring(0, nextPlaceholder)
                    if (text.isNotEmpty()) {
                        parts.add(messageSerializer.deserialize(text))
                    }
                    remaining = remaining.substring(nextPlaceholder)
                }
            }
        }

        // Combine all parts
        result = parts.firstOrNull() ?: Component.empty()
        parts.drop(1).forEach { part ->
            result = result.append(part)
        }

        return result
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

        // Set join message
        if (joinMessageEnabled) {
            val message = joinMessage.replace("%player%", player.name)
            val serializer = LegacyComponentSerializer.legacySection()
            event.joinMessage(serializer.deserialize(translateColors(message)))
        } else {
            event.joinMessage(null)
        }

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

        // Set quit message
        if (quitMessageEnabled) {
            val message = quitMessage.replace("%player%", player.name)
            val serializer = LegacyComponentSerializer.legacySection()
            event.quitMessage(serializer.deserialize(translateColors(message)))
        } else {
            event.quitMessage(null)
        }

        cleanupPlayer(player)
    }

    private fun updatePlayerNametag(player: Player) {
        if (!nametagEnabled) return

        // Folia doesn't support scoreboard teams, skip silently
        try {
            // Try to detect if we're on Folia by checking if newScoreboard throws UnsupportedOperationException
            Bukkit.getScoreboardManager()?.newScoreboard
        } catch (e: UnsupportedOperationException) {
            // Running on Folia, nametags are not supported
            return
        }

        // Schedule on player's entity scheduler for Folia compatibility
        player.scheduler.run(Pulse.getPlugin(), { _ ->
            try {
                val rankManager = Pulse.getPlugin().rankManager
                val tagManager = Pulse.getPlugin().tagManager
                val playerData = rankManager.getPlayerData(player)
                val rank = rankManager.getRank(playerData.rank)

                // Create team name based on rank weight (for proper sorting)
                val weight = rank?.weight ?: 0
                val teamName = "pulse_${String.format("%03d", 999 - weight)}_${rank?.name ?: "default"}"

                // Get or create player's own scoreboard (required for Folia)
                var scoreboard = player.scoreboard
                if (scoreboard == Bukkit.getScoreboardManager()?.mainScoreboard) {
                    scoreboard = Bukkit.getScoreboardManager()?.newScoreboard ?: return@run
                    player.scoreboard = scoreboard
                }

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
                    // Configure team settings
                    team.setAllowFriendlyFire(true)
                    team.setCanSeeFriendlyInvisibles(true)
                }

                // Always update team prefix and suffix (in case format or rank changed)
                val prefix = rank?.prefix ?: ""
                val suffix = rank?.suffix ?: ""
                val tags = tagManager.getFormattedTagsForPlayer(player, "display")

                // Process the format string
                val translatedFormat = nametagFormat
                    .replace("{prefix}", translateColors(prefix))
                    .replace("{tags}", tags)
                    .replace("{suffix}", translateColors(suffix))
                    .replace("{rank}", rank?.name ?: "")

                // Split at {player} placeholder
                val playerIndex = translatedFormat.indexOf("{player}")
                val formattedPrefix = if (playerIndex >= 0) {
                    translatedFormat.substring(0, playerIndex)
                } else {
                    translatedFormat
                }

                val formattedSuffix = if (playerIndex >= 0) {
                    translatedFormat.substring(playerIndex + "{player}".length)
                } else {
                    ""
                }

                // Set team prefix/suffix using Adventure API
                val serializer = LegacyComponentSerializer.legacySection()
                team.prefix(serializer.deserialize(formattedPrefix.take(64)))
                team.suffix(serializer.deserialize(formattedSuffix.take(64)))

                // Add player to team
                team.addEntry(player.name)
                playerTeams[player.name] = team
            } catch (e: Exception) {
                Logger.error("Failed to update nametag for ${player.name}: ${e.message}", e)
            }
        }, null)
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

        // Get ping
        val ping = player.ping
        val pingColor = getPingColor(ping)
        val pingBars = getPingBars(ping)

        // Format ping display
        val pingDisplay = if (showPing && pingPosition != "none") {
            pingFormat
                .replace("{ping}", ping.toString())
                .replace("{ping_color}", pingColor)
                .replace("{ping_bars}", pingBars)
        } else ""

        var formattedName = translateColors(tabFormat)
            .replace("{prefix}", translateColors(prefix))
            .replace("{tags}", tags)
            .replace("{player}", player.name)
            .replace("{suffix}", translateColors(suffix))
            .replace("{rank}", rank?.name ?: "")
            .replace("{world}", player.world.name)
            .replace("{ping}", ping.toString())
            .replace("{ping_color}", translateColors(pingColor))
            .replace("{ping_bars}", pingBars)

        // Add ping display based on position
        formattedName = when (pingPosition.lowercase()) {
            "before" -> translateColors(pingDisplay) + formattedName
            "after" -> formattedName + translateColors(pingDisplay)
            else -> formattedName
        }

        // Update player list name using Adventure API
        val serializer = LegacyComponentSerializer.legacySection()
        player.playerListName(serializer.deserialize(formattedName))
    }

    private fun updateTabHeader(player: Player) {
        if (!tabEnabled) return

        // Build header
        val headerText = if (headerEnabled && headerLines.isNotEmpty()) {
            buildHeaderFooter(headerLines, player)
        } else ""

        // Build footer
        val footerText = if (footerEnabled && footerLines.isNotEmpty()) {
            buildHeaderFooter(footerLines, player)
        } else ""

        val serializer = LegacyComponentSerializer.legacySection()
        player.sendPlayerListHeaderAndFooter(
            serializer.deserialize(translateColors(headerText)),
            serializer.deserialize(translateColors(footerText))
        )
    }

    private fun buildHeaderFooter(lines: List<String>, player: Player): String {
        return lines.joinToString("\n") { line ->
            val processed = processPlaceholders(line, player)
            processed
        }
    }

    private fun centerText(text: String, width: Int): String {
        // Remove both & and § color codes for length calculation
        val cleanText = text.replace(Regex("[&§][0-9a-fk-orA-FK-OR]"), "")

        // Calculate pixel width for better centering (Minecraft default font)
        val pixelWidth = calculatePixelWidth(cleanText)
        val maxPixels = width * 4 // Approximate: ~4 pixels per character for average
        val pixelPadding = ((maxPixels - pixelWidth) / 2).coerceAtLeast(0)
        val spaces = (pixelPadding / 4).coerceAtLeast(0) // Convert pixels to spaces

        return " ".repeat(spaces) + text
    }

    private fun calculatePixelWidth(text: String): Int {
        // Approximate pixel widths for Minecraft's default font
        var pixels = 0
        for (char in text) {
            pixels += when (char) {
                'i', 'I', '!', '|', '.', ',', ':', ';', '\'' -> 2
                'l', 't', 'f', 'k' -> 3
                ' ' -> 3
                'I' -> 4
                in "abcdefghjmnopqrsuvwxyz" -> 5
                in "ABCDEFGHJKLMNOPQRSTUVWXYZ" -> 5
                in "0123456789" -> 5
                '@' -> 6
                else -> 5
            }
            pixels += 1 // Character spacing
        }
        return pixels
    }

    private fun updateAllTabLists() {
        if (!tabEnabled) return

        Bukkit.getOnlinePlayers().forEach { player ->
            updatePlayerTab(player)
        }
    }

    private fun updateAllHeaders() {
        if (!tabEnabled) return

        Bukkit.getOnlinePlayers().forEach { player ->
            updateTabHeader(player)
        }
    }

    private fun processPlaceholders(text: String, player: Player): String {
        val rankManager = Pulse.getPlugin().rankManager
        val tagManager = Pulse.getPlugin().tagManager
        val economyManager = Pulse.getPlugin().economyManager
        val playtimeManager = Pulse.getPlugin().playtimeManager
        val playerData = rankManager.getPlayerData(player)
        val rank = rankManager.getRank(playerData.rank)
        val tags = tagManager.getFormattedTagsForPlayer(player, "display")

        // Get ping
        val ping = player.ping
        val pingColor = getPingColor(ping)

        // Get TPS
        val tps = String.format("%.2f", Bukkit.getTPS()[0])

        // Get server uptime
        val uptime = formatDuration((System.currentTimeMillis() - serverStartTime) / 1000)

        // Get current time and date
        val now = java.time.LocalDateTime.now()
        val time = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val date = now.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"))

        // Get playtime
        val playtime = playtimeManager.getFormattedPlaytime(player.uniqueId)

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
            .replace("{ping}", ping.toString())
            .replace("{ping_color}", pingColor)
            .replace("{tps}", tps)
            .replace("{server_uptime}", uptime)
            .replace("{time}", time)
            .replace("{date}", date)
            .replace("{playtime}", playtime)
            .replace("\\n", "\n")
    }

    private fun formatDuration(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m")
            if (isEmpty()) append("0m")
        }.trim()
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

    private fun getPingColor(ping: Int): String {
        return when {
            ping < 50 -> pingColors["excellent"] ?: "&a"
            ping < 100 -> pingColors["good"] ?: "&2"
            ping < 150 -> pingColors["okay"] ?: "&e"
            ping < 200 -> pingColors["poor"] ?: "&6"
            ping < 300 -> pingColors["bad"] ?: "&c"
            else -> pingColors["terrible"] ?: "&4"
        }
    }

    private fun getPingBars(ping: Int): String {
        val bars = when {
            ping < 50 -> "▌▌▌▌▌"
            ping < 100 -> "▌▌▌▌"
            ping < 150 -> "▌▌▌"
            ping < 200 -> "▌▌"
            else -> "▌"
        }
        val color = getPingColor(ping)
        return "$color$bars"
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
        headerFooterTask = -1

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
        headerFooterTask = -1

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