package dev.aledlb.pulse

import dev.aledlb.pulse.commands.CoinCommand
import dev.aledlb.pulse.commands.PermissionCommand
import dev.aledlb.pulse.commands.PlaceholderCommand
import dev.aledlb.pulse.commands.PulseCommand
import dev.aledlb.pulse.commands.RankCommand
import dev.aledlb.pulse.commands.ShopCommand
import dev.aledlb.pulse.commands.TagCommand
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

    private var placeholderAPIHook: PlaceholderAPIHook? = null

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

        // Initialize after ranks, economy and tags for placeholder support
        chatManager = ChatManager()
        chatManager.initialize()

        shopManager = ShopManager(economyManager, rankManager, permissionManager)
        shopManager.initialize()

        shopGUI = ShopGUI(shopManager, economyManager)

        placeholderManager = PlaceholderManager()
        placeholderManager.initialize()

        val pulsePlaceholderProvider = PulsePlaceholderProvider(rankManager, permissionManager, economyManager)
        placeholderManager.registerProvider(pulsePlaceholderProvider)

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                placeholderAPIHook = PlaceholderAPIHook()
                placeholderAPIHook?.registerExpansion()
            } catch (e: Exception) {
                Logger.warn("PlaceholderAPI found but failed to initialize: ${e.message}")
            }
        }

        Logger.success("Initialized 9 core modules")
    }

    private fun registerEvents() {
        server.pluginManager.registerEvents(permissionManager, this)
        server.pluginManager.registerEvents(shopGUI, this)

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

        val placeholderCommand = PlaceholderCommand()
        getCommand("placeholder")?.setExecutor(placeholderCommand)
        getCommand("placeholder")?.tabCompleter = placeholderCommand

        val coinCommand = CoinCommand(economyManager)
        getCommand("coin")?.setExecutor(coinCommand)
        getCommand("coin")?.tabCompleter = coinCommand

        val shopCommand = ShopCommand(shopManager, shopGUI)
        getCommand("shop")?.setExecutor(shopCommand)
        getCommand("shop")?.tabCompleter = shopCommand

        val tagCommand = TagCommand()
        getCommand("tag")?.setExecutor(tagCommand)
        getCommand("tag")?.tabCompleter = tagCommand

        Logger.success("Registered 7 commands")
    }

    fun isPluginFullyLoaded(): Boolean = isFullyLoaded
}