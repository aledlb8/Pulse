package dev.aledlb.pulse

import dev.aledlb.pulse.chat.ChatManager
import dev.aledlb.pulse.commands.*
import dev.aledlb.pulse.config.ConfigManager
import dev.aledlb.pulse.database.DatabaseManager
import dev.aledlb.pulse.database.RedisManager
import dev.aledlb.pulse.economy.EconomyManager
import dev.aledlb.pulse.listeners.CommandBlockerListener
import dev.aledlb.pulse.messages.MessagesManager
import dev.aledlb.pulse.motd.MOTDManager
import dev.aledlb.pulse.motd.ServerListPingListener
import dev.aledlb.pulse.placeholders.PlaceholderAPIHook
import dev.aledlb.pulse.placeholders.PlaceholderManager
import dev.aledlb.pulse.placeholders.PulsePlaceholderProvider
import dev.aledlb.pulse.playtime.PlaytimeManager
import dev.aledlb.pulse.punishment.PunishmentListener
import dev.aledlb.pulse.punishment.PunishmentManager
import dev.aledlb.pulse.ranks.PermissionManager
import dev.aledlb.pulse.ranks.models.RankManager
import dev.aledlb.pulse.shop.ShopGUI
import dev.aledlb.pulse.shop.ShopManager
import dev.aledlb.pulse.tags.TagManager
import dev.aledlb.pulse.tasks.RankExpirationTask
import dev.aledlb.pulse.util.Logger
import dev.aledlb.pulse.util.UpdateChecker
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import kotlin.system.measureTimeMillis

class Pulse : JavaPlugin() {

    companion object {
        @JvmStatic
        lateinit var instance: Pulse
            private set

        @JvmStatic
        fun getPlugin(): Pulse = instance
    }

    // ------------------------
    // Managers (kept as lateinit to preserve public API)
    // ------------------------
    lateinit var configManager: ConfigManager;              private set
    lateinit var databaseManager: DatabaseManager;          private set
    lateinit var redisManager: RedisManager;                private set
    lateinit var messagesManager: MessagesManager;          private set
    lateinit var chatManager: ChatManager;                  private set
    lateinit var punishmentManager: PunishmentManager;      private set
    lateinit var rankManager: RankManager;                  private set
    lateinit var permissionManager: PermissionManager;      private set
    lateinit var placeholderManager: PlaceholderManager;    private set
    lateinit var economyManager: EconomyManager;            private set
    lateinit var tagManager: TagManager;                    private set
    lateinit var shopManager: ShopManager;                  private set
    lateinit var shopGUI: ShopGUI;                          private set
    lateinit var playtimeManager: PlaytimeManager;          private set
    lateinit var profileGUI: dev.aledlb.pulse.profile.ProfileGUI; private set
    lateinit var punishmentHistoryGUI: dev.aledlb.pulse.profile.PunishmentHistoryGUI; private set
    lateinit var motdManager: MOTDManager;                  private set

    private var placeholderAPIHook: PlaceholderAPIHook? = null
    private var updateChecker: UpdateChecker? = null

    private var startupTime: Long = 0L
    @Volatile private var isFullyLoaded = false
    fun isPluginFullyLoaded(): Boolean = isFullyLoaded

    // Config file list centralized
    private val CONFIG_FILES = listOf(
        "config.yml",
        "database.yml",
        "messages.yml",
        "punishment.yml",
        "ranks.yml",
        "shop.yml",
        "tags.yml",
        "motd.yml"
    )

    override fun onEnable() {
        instance = this
        startupTime = System.currentTimeMillis()
        Logger.initialize(logger)

        try {
            Logger.logStartup(pluginMeta.version)
            initializeConfig()                 // Sync: file I/O is fine here and simple
            initializeCoreAsyncThenWireSync()  // Async heavy init, then sync Bukkit bindings
        } catch (t: Throwable) {
            Logger.error("Failed during early startup", t)
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        Logger.logShutdown()

        // Unregister external hooks
        runCatching { placeholderAPIHook?.unregisterExpansion() }
            .onFailure { Logger.warn("Error unregistering PlaceholderAPI: ${it.message}") }

        // Shut down subsystems if they were initialized
        runCatching { updateChecker?.shutdown() }
        if (::chatManager.isInitialized)       runCatching { chatManager.shutdown() }
        if (::rankManager.isInitialized)       runCatching { rankManager.saveAllData() }
        if (::economyManager.isInitialized)    runCatching { economyManager.saveAllData() }
        if (::tagManager.isInitialized)        runCatching { tagManager.saveAllData() }
        if (::playtimeManager.isInitialized)   runCatching { playtimeManager.shutdown() }
        if (::redisManager.isInitialized)      runCatching { redisManager.shutdown() }
        if (::databaseManager.isInitialized)   runCatching { databaseManager.shutdown() }

        Logger.info("Pulse disabled successfully")
    }

    // ------------------------
    // Startup Phases
    // ------------------------

    private fun initializeConfig() {
        val took = measureTimeMillis {
            configManager = ConfigManager(dataFolder)
            CONFIG_FILES.forEach { configManager.loadConfig(it) }
        }
        Logger.success("Loaded ${CONFIG_FILES.size} configuration files in ${took}ms")
    }

    /**
     * Initializes heavy services asynchronously using Folia/Paper async scheduler,
     * then hops back to the global (main) scheduler to bind Bukkit listeners/commands.
     */
    private fun initializeCoreAsyncThenWireSync() {
        // Async phase
        Bukkit.getAsyncScheduler().runNow(this) { _ ->
            val tookAsync = measureTimeMillis {
                try {
                    initializeCoreAsyncOnly()
                } catch (e: Exception) {
                    Logger.error("Async core initialization failed", e)
                    // Hop back to global/main to disable safely
                    Bukkit.getGlobalRegionScheduler().execute(this) {
                        server.pluginManager.disablePlugin(this)
                    }
                    return@runNow
                }
            }
            Logger.success("Initialized core services (async) in ${tookAsync}ms")

            // Sync/global phase
            Bukkit.getGlobalRegionScheduler().execute(this) {
                try {
                    val tookSync = measureTimeMillis {
                        wireBukkitAndHooksSync()
                        startBackgroundTasksSync()
                    }
                    isFullyLoaded = true
                    val total = System.currentTimeMillis() - startupTime
                    Logger.success("Pulse fully loaded in ${total}ms (sync wire-up ${tookSync}ms)")
                } catch (e: Exception) {
                    Logger.error("Failed during sync wire-up", e)
                    server.pluginManager.disablePlugin(this)
                }
            }
        }
    }

    /**
     * Only non-Bukkit, heavy I/O work here.
     * DO NOT access Bukkit API in this method.
     */
    private fun initializeCoreAsyncOnly() {
        databaseManager = DatabaseManager().also { it.initialize() }
        redisManager = RedisManager().also { it.initialize() }

        messagesManager = MessagesManager().also { it.initialize() }
        punishmentManager = PunishmentManager().also { it.initialize() }

        rankManager = RankManager(databaseManager).also { it.initialize() }
        permissionManager = PermissionManager(rankManager).also { it.initialize() }

        economyManager = EconomyManager(databaseManager).also { it.initialize() }
        tagManager = TagManager(databaseManager).also { it.initialize() }

        playtimeManager = PlaytimeManager(databaseManager).also { it.initialize() }

        // Pure-core constructions; any Bukkit-touching init happens later on the global thread
        chatManager = ChatManager()
        shopManager = ShopManager(economyManager, rankManager, permissionManager).also { it.initialize() }
        shopGUI = ShopGUI(shopManager, economyManager)

        placeholderManager = PlaceholderManager().also { it.initialize() }

        profileGUI = dev.aledlb.pulse.profile.ProfileGUI()
        punishmentHistoryGUI = dev.aledlb.pulse.profile.PunishmentHistoryGUI()

        motdManager = MOTDManager(this).also { it.initialize() }
    }

    /**
     * Global/main-thread work: Bukkit events, commands, hooks that must be registered on the server thread.
     */
    private fun wireBukkitAndHooksSync() {
        chatManager.initialize()

        val pulsePlaceholderProvider = PulsePlaceholderProvider(
            rankManager, permissionManager, economyManager, playtimeManager
        )
        placeholderManager.registerProvider(pulsePlaceholderProvider)

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            runCatching {
                placeholderAPIHook = PlaceholderAPIHook().also { it.registerExpansion() }
            }.onFailure {
                Logger.warn("PlaceholderAPI found but failed to initialize: ${it.message}")
            }
        }

        // Events & Commands
        registerEvents(
            permissionManager,
            shopGUI,
            playtimeManager,
            profileGUI,
            punishmentHistoryGUI,
            PunishmentListener(),
            CommandBlockerListener(),
            ServerListPingListener(motdManager)
        )
        Logger.success("Registered event listeners")

        registerCommands()
    }

    private fun startBackgroundTasksSync() {
        updateChecker = UpdateChecker(this).also { it.initialize() }

        // Rank expiration task
        RankExpirationTask.start(this, rankManager, permissionManager)
    }

    // ------------------------
    // Wiring Helpers
    // ------------------------

    private fun registerEvents(vararg listeners: Listener) {
        val pm = server.pluginManager
        listeners.forEach { pm.registerEvents(it, this) }
    }

    private fun bindCommand(name: String, executor: CommandExecutor, tab: TabCompleter? = executor as? TabCompleter) {
        val cmd = getCommand(name)
        if (cmd == null) {
            Logger.warn("Command '/$name' is not defined in plugin.yml")
            return
        }
        cmd.setExecutor(executor)
        cmd.tabCompleter = tab
    }

    private fun registerCommands() {
        bindCommand("pulse", PulseCommand())

        val rankCmd = RankCommand(rankManager, permissionManager)
        bindCommand("rank", rankCmd)

        val permCmd = PermissionCommand(rankManager, permissionManager)
        bindCommand("permission", permCmd)

        val coinCmd = CoinCommand(economyManager)
        bindCommand("coin", coinCmd)

        val shopCmd = ShopCommand(shopManager, shopGUI)
        bindCommand("shop", shopCmd)

        val tagCmd = TagCommand()
        bindCommand("tag", tagCmd)

        val gmCmd = GamemodeCommand()
        bindCommand("gamemode", gmCmd)

        bindCommand("gmc", GamemodeCreativeCommand())
        bindCommand("gms", GamemodeSurvivalCommand())
        bindCommand("gma", GamemodeAdventureCommand())
        bindCommand("gmsp", GamemodeSpectatorCommand())

        // Punishment
        bindCommand("kick",   KickCommand())
        bindCommand("ban",    BanCommand())
        bindCommand("tempban",TempbanCommand())
        bindCommand("ipban",  IpbanCommand())
        bindCommand("tempipban", TempipbanCommand())
        bindCommand("unban",  UnbanCommand())
        bindCommand("mute",   MuteCommand())
        bindCommand("unmute", UnmuteCommand())
        bindCommand("freeze", FreezeCommand())
        bindCommand("unfreeze", UnfreezeCommand())
        bindCommand("warn",   WarnCommand())
        bindCommand("warns",  WarnsCommand())
        bindCommand("unwarn", UnwarnCommand())

        bindCommand("grant",  GrantCommand(rankManager, permissionManager))

        val playtimeCmd = PlaytimeCommand(playtimeManager)
        bindCommand("playtime", playtimeCmd)

        val profileCmd = ProfileCommand(profileGUI)
        bindCommand("profile", profileCmd)

        bindCommand("report", ReportCommand())

        bindCommand("motd", MOTDCommand())
    }
}