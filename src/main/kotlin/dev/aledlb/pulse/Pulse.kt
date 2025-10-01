package dev.aledlb.pulse

import dev.aledlb.pulse.commands.*
import dev.aledlb.pulse.config.ConfigManager
import dev.aledlb.pulse.chat.ChatManager
import dev.aledlb.pulse.database.DatabaseManager
import dev.aledlb.pulse.economy.EconomyManager
import dev.aledlb.pulse.messages.MessagesManager
import dev.aledlb.pulse.placeholders.PlaceholderAPIHook
import dev.aledlb.pulse.placeholders.PlaceholderManager
import dev.aledlb.pulse.placeholders.PulsePlaceholderProvider
import dev.aledlb.pulse.punishment.PunishmentManager
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import dev.aledlb.pulse.shop.ShopGUI
import dev.aledlb.pulse.shop.ShopManager
import dev.aledlb.pulse.tags.TagManager
import dev.aledlb.pulse.util.Logger
import dev.aledlb.pulse.util.UpdateChecker
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CompletableFuture

class Pulse : JavaPlugin() {

    companion object {
        @JvmStatic
        lateinit var instance: Pulse
            private set

        @JvmStatic
        fun getPlugin(): Pulse = instance
    }

    lateinit var configManager: ConfigManager
        private set

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var redisManager: dev.aledlb.pulse.database.RedisManager
        private set

    lateinit var messagesManager: MessagesManager
        private set

    lateinit var chatManager: ChatManager
        private set

    lateinit var punishmentManager: PunishmentManager
        private set

    lateinit var rankManager: RankManager
        private set

    lateinit var permissionManager: PermissionManager
        private set

    lateinit var placeholderManager: PlaceholderManager
        private set

    lateinit var economyManager: EconomyManager
        private set

    lateinit var tagManager: TagManager
        private set

    lateinit var shopManager: ShopManager
        private set

    lateinit var shopGUI: ShopGUI
        private set

    lateinit var playtimeManager: dev.aledlb.pulse.playtime.PlaytimeManager
        private set

    private var placeholderAPIHook: PlaceholderAPIHook? = null
    private var updateChecker: UpdateChecker? = null

    private var startupTime: Long = 0
    private var isFullyLoaded = false

    override fun onEnable() {
        instance = this
        startupTime = System.currentTimeMillis()

        Logger.initialize(logger)

        CompletableFuture.runAsync {
            try {
                initializePlugin()
                isFullyLoaded = true
                val loadTime = System.currentTimeMillis() - startupTime
                Logger.success("Pulse fully loaded in ${loadTime}ms")
            } catch (e: Exception) {
                Logger.error("Failed to initialize plugin", e)
                server.pluginManager.disablePlugin(this)
            }
        }
    }

    override fun onDisable() {
        Logger.logShutdown()

        placeholderAPIHook?.unregisterExpansion()

        if (::chatManager.isInitialized) {
            chatManager.shutdown()
        }

        if (::rankManager.isInitialized) {
            rankManager.saveAllData()
        }

        if (::economyManager.isInitialized) {
            economyManager.saveAllData()
        }

        if (::tagManager.isInitialized) {
            tagManager.saveAllData()
        }

        if (::playtimeManager.isInitialized) {
            playtimeManager.shutdown()
        }

        if (::redisManager.isInitialized) {
            redisManager.shutdown()
        }

        if (::databaseManager.isInitialized) {
            databaseManager.shutdown()
        }

        Logger.info("Pulse disabled successfully")
    }

    private fun initializePlugin() {
        Logger.logStartup(pluginMeta.version)

        initializeConfig()
        initializeModules()
        registerEvents()
        registerCommands()
    }

    private fun initializeConfig() {
        configManager = ConfigManager(dataFolder)

        val configs = listOf(
            "config.yml",
            "database.yml",
            "messages.yml",
            "punishment.yml",
            "ranks.yml",
            "shop.yml",
            "tags.yml"
        )

        configs.forEach { config ->
            configManager.loadConfig(config)
        }

        Logger.success("Loaded ${configs.size} configuration files")
    }

    private fun initializeModules() {
        databaseManager = DatabaseManager()
        databaseManager.initialize()

        redisManager = dev.aledlb.pulse.database.RedisManager()
        redisManager.initialize()

        messagesManager = MessagesManager()
        messagesManager.initialize()

        punishmentManager = PunishmentManager()
        punishmentManager.initialize()

        rankManager = RankManager(databaseManager)
        rankManager.initialize()

        permissionManager = PermissionManager(rankManager)
        permissionManager.initialize()

        economyManager = EconomyManager(databaseManager)
        economyManager.initialize()

        tagManager = TagManager(databaseManager)
        tagManager.initialize()

        playtimeManager = dev.aledlb.pulse.playtime.PlaytimeManager(databaseManager)
        playtimeManager.initialize()

        // Initialize after ranks, economy and tags for placeholder support
        chatManager = ChatManager()
        chatManager.initialize()

        shopManager = ShopManager(economyManager, rankManager, permissionManager)
        shopManager.initialize()

        shopGUI = ShopGUI(shopManager, economyManager)

        placeholderManager = PlaceholderManager()
        placeholderManager.initialize()

        val pulsePlaceholderProvider = PulsePlaceholderProvider(rankManager, permissionManager, economyManager, playtimeManager)
        placeholderManager.registerProvider(pulsePlaceholderProvider)

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                placeholderAPIHook = PlaceholderAPIHook()
                placeholderAPIHook?.registerExpansion()
            } catch (e: Exception) {
                Logger.warn("PlaceholderAPI found but failed to initialize: ${e.message}")
            }
        }

        // Update checker
        updateChecker = UpdateChecker(this)
        updateChecker?.initialize()

        // Start rank expiration task
        dev.aledlb.pulse.tasks.RankExpirationTask.start(this, rankManager, permissionManager)

        Logger.success("Initialized 11 core modules")
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(permissionManager, this)
        server.pluginManager.registerEvents(shopGUI, this)
        server.pluginManager.registerEvents(playtimeManager, this)
        server.pluginManager.registerEvents(dev.aledlb.pulse.punishment.PunishmentListener(), this)

        Logger.success("Registered event listeners")
    }

    private fun registerCommands() {
        val pulseCommand = PulseCommand()
        getCommand("pulse")?.setExecutor(pulseCommand)
        getCommand("pulse")?.tabCompleter = pulseCommand

        val rankCommand = RankCommand(rankManager, permissionManager)
        getCommand("rank")?.setExecutor(rankCommand)
        getCommand("rank")?.tabCompleter = rankCommand

        val permissionCommand = PermissionCommand(rankManager, permissionManager)
        getCommand("permission")?.setExecutor(permissionCommand)
        getCommand("permission")?.tabCompleter = permissionCommand

        val coinCommand = CoinCommand(economyManager)
        getCommand("coin")?.setExecutor(coinCommand)
        getCommand("coin")?.tabCompleter = coinCommand

        val shopCommand = ShopCommand(shopManager, shopGUI)
        getCommand("shop")?.setExecutor(shopCommand)
        getCommand("shop")?.tabCompleter = shopCommand

        val tagCommand = TagCommand()
        getCommand("tag")?.setExecutor(tagCommand)
        getCommand("tag")?.tabCompleter = tagCommand

        val gamemodeCommand = GamemodeCommand()
        getCommand("gamemode")?.setExecutor(gamemodeCommand)
        getCommand("gamemode")?.tabCompleter = gamemodeCommand

        val gmcCommand = GamemodeCreativeCommand()
        getCommand("gmc")?.setExecutor(gmcCommand)
        getCommand("gmc")?.tabCompleter = gmcCommand

        val gmsCommand = GamemodeSurvivalCommand()
        getCommand("gms")?.setExecutor(gmsCommand)
        getCommand("gms")?.tabCompleter = gmsCommand

        val gmaCommand = GamemodeAdventureCommand()
        getCommand("gma")?.setExecutor(gmaCommand)
        getCommand("gma")?.tabCompleter = gmaCommand

        val gmspCommand = GamemodeSpectatorCommand()
        getCommand("gmsp")?.setExecutor(gmspCommand)
        getCommand("gmsp")?.tabCompleter = gmspCommand

        // Punishment commands
        val kickCommand = KickCommand()
        getCommand("kick")?.setExecutor(kickCommand)
        getCommand("kick")?.tabCompleter = kickCommand

        val banCommand = BanCommand()
        getCommand("ban")?.setExecutor(banCommand)
        getCommand("ban")?.tabCompleter = banCommand

        val tempbanCommand = TempbanCommand()
        getCommand("tempban")?.setExecutor(tempbanCommand)
        getCommand("tempban")?.tabCompleter = tempbanCommand

        val ipbanCommand = IpbanCommand()
        getCommand("ipban")?.setExecutor(ipbanCommand)
        getCommand("ipban")?.tabCompleter = ipbanCommand

        val tempipbanCommand = TempipbanCommand()
        getCommand("tempipban")?.setExecutor(tempipbanCommand)
        getCommand("tempipban")?.tabCompleter = tempipbanCommand

        val unbanCommand = UnbanCommand()
        getCommand("unban")?.setExecutor(unbanCommand)
        getCommand("unban")?.tabCompleter = unbanCommand

        val muteCommand = MuteCommand()
        getCommand("mute")?.setExecutor(muteCommand)
        getCommand("mute")?.tabCompleter = muteCommand

        val unmuteCommand = UnmuteCommand()
        getCommand("unmute")?.setExecutor(unmuteCommand)
        getCommand("unmute")?.tabCompleter = unmuteCommand

        val freezeCommand = FreezeCommand()
        getCommand("freeze")?.setExecutor(freezeCommand)
        getCommand("freeze")?.tabCompleter = freezeCommand

        val unfreezeCommand = UnfreezeCommand()
        getCommand("unfreeze")?.setExecutor(unfreezeCommand)
        getCommand("unfreeze")?.tabCompleter = unfreezeCommand

        val warnCommand = WarnCommand()
        getCommand("warn")?.setExecutor(warnCommand)
        getCommand("warn")?.tabCompleter = warnCommand

        val warnsCommand = WarnsCommand()
        getCommand("warns")?.setExecutor(warnsCommand)
        getCommand("warns")?.tabCompleter = warnsCommand

        val unwarnCommand = UnwarnCommand()
        getCommand("unwarn")?.setExecutor(unwarnCommand)
        getCommand("unwarn")?.tabCompleter = unwarnCommand

        val grantCommand = GrantCommand(rankManager, permissionManager)
        getCommand("grant")?.setExecutor(grantCommand)
        getCommand("grant")?.tabCompleter = grantCommand

        val playtimeCommand = PlaytimeCommand(playtimeManager)
        getCommand("playtime")?.setExecutor(playtimeCommand)
        getCommand("playtime")?.tabCompleter = playtimeCommand

        Logger.success("Registered 27 commands")
    }

    fun isPluginFullyLoaded(): Boolean = isFullyLoaded
}