package dev.aledlb.pulse.database

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.*

class DatabaseManager {
    private lateinit var database: Database
    private lateinit var dataSource: HikariDataSource

    fun initialize() {
        setupDatabase()
        createTables()
    }

    private fun setupDatabase() {
        val configManager = Pulse.getPlugin().configManager
        val dbConfig = configManager.getConfig("database.yml")

        if (dbConfig == null) {
            Logger.error("Database configuration not found!")
            return
        }

        val dbType = dbConfig.node("type").getString("SQLITE")?.uppercase() ?: "SQLITE"

        when (dbType) {
            "SQLITE" -> setupSQLite(dbConfig)
            "MYSQL" -> setupMySQL(dbConfig)
            "POSTGRESQL" -> setupPostgreSQL(dbConfig)
            else -> {
                Logger.error("Unsupported database type: $dbType")
                setupSQLite(dbConfig) // Fallback to SQLite
            }
        }

        Logger.info("Database connection established ($dbType)")
    }

    private fun setupSQLite(config: org.spongepowered.configurate.ConfigurationNode) {
        val fileName = config.node("sqlite").node("file").getString("pulse.db") ?: "pulse.db"
        val dbFile = File(Pulse.getPlugin().dataFolder, fileName)

        // Ensure parent directory exists
        dbFile.parentFile?.mkdirs()

        val hikariConfig = HikariConfig().apply {
            driverClassName = "org.sqlite.JDBC"
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            maximumPoolSize = 1 // SQLite only supports one connection
            connectionTestQuery = "SELECT 1"
        }

        dataSource = HikariDataSource(hikariConfig)
        database = Database.connect(dataSource)
    }

    private fun setupMySQL(config: org.spongepowered.configurate.ConfigurationNode) {
        val mysqlNode = config.node("mysql")
        val poolNode = config.node("pool")

        val host = mysqlNode.node("host").getString("localhost") ?: "localhost"
        val port = mysqlNode.node("port").getInt(3306)
        val database = mysqlNode.node("database").getString("pulse") ?: "pulse"
        val username = mysqlNode.node("username").getString("pulse") ?: "pulse"
        val password = mysqlNode.node("password").getString("password") ?: "password"

        val hikariConfig = HikariConfig().apply {
            driverClassName = "com.mysql.cj.jdbc.Driver"
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&allowPublicKeyRetrieval=true"
            this.username = username
            this.password = password
            maximumPoolSize = poolNode.node("maximum-pool-size").getInt(10)
            minimumIdle = poolNode.node("minimum-idle").getInt(2)
            connectionTimeout = poolNode.node("connection-timeout").getLong(30000)
            idleTimeout = poolNode.node("idle-timeout").getLong(600000)
            maxLifetime = poolNode.node("max-lifetime").getLong(1800000)
        }

        dataSource = HikariDataSource(hikariConfig)
        this.database = Database.connect(dataSource)
    }

    private fun setupPostgreSQL(config: org.spongepowered.configurate.ConfigurationNode) {
        val mysqlNode = config.node("mysql") // Reuse mysql config section for PostgreSQL
        val poolNode = config.node("pool")

        val host = mysqlNode.node("host").getString("localhost") ?: "localhost"
        val port = mysqlNode.node("port").getInt(5432)
        val database = mysqlNode.node("database").getString("pulse") ?: "pulse"
        val username = mysqlNode.node("username").getString("pulse") ?: "pulse"
        val password = mysqlNode.node("password").getString("password") ?: "password"

        val hikariConfig = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = "jdbc:postgresql://$host:$port/$database"
            this.username = username
            this.password = password
            maximumPoolSize = poolNode.node("maximum-pool-size").getInt(10)
            minimumIdle = poolNode.node("minimum-idle").getInt(2)
            connectionTimeout = poolNode.node("connection-timeout").getLong(30000)
            idleTimeout = poolNode.node("idle-timeout").getLong(600000)
            maxLifetime = poolNode.node("max-lifetime").getLong(1800000)
        }

        dataSource = HikariDataSource(hikariConfig)
        this.database = Database.connect(dataSource)
    }

    private fun createTables() {
        transaction(database) {
            SchemaUtils.create(PlayerDataTable, PlayerBalanceTable, PlayerTagDataTable)
        }
        Logger.debug("Database tables created/verified")
    }

    // Player Data operations
    suspend fun savePlayerData(uuid: UUID, name: String, rank: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            // Try to update first, if no rows affected then insert
            val updated = PlayerDataTable.update({ PlayerDataTable.uuid eq uuid.toString() }) {
                it[PlayerDataTable.name] = name
                it[PlayerDataTable.rank] = rank
                it[PlayerDataTable.lastSeen] = System.currentTimeMillis()
            }

            if (updated == 0) {
                PlayerDataTable.insert {
                    it[PlayerDataTable.uuid] = uuid.toString()
                    it[PlayerDataTable.name] = name
                    it[PlayerDataTable.rank] = rank
                    it[PlayerDataTable.lastSeen] = System.currentTimeMillis()
                }
            }
        }
    }

    suspend fun loadPlayerData(uuid: UUID): PlayerDataRow? {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            PlayerDataTable.select { PlayerDataTable.uuid eq uuid.toString() }
                .map {
                    PlayerDataRow(
                        uuid = UUID.fromString(it[PlayerDataTable.uuid]),
                        name = it[PlayerDataTable.name],
                        rank = it[PlayerDataTable.rank],
                        lastSeen = it[PlayerDataTable.lastSeen]
                    )
                }
                .singleOrNull()
        }
    }

    // Player Balance operations
    suspend fun savePlayerBalance(uuid: UUID, balance: Double) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            // Try to update first, if no rows affected then insert
            val updated = PlayerBalanceTable.update({ PlayerBalanceTable.uuid eq uuid.toString() }) {
                it[PlayerBalanceTable.balance] = balance
                it[PlayerBalanceTable.lastUpdated] = System.currentTimeMillis()
            }

            if (updated == 0) {
                PlayerBalanceTable.insert {
                    it[PlayerBalanceTable.uuid] = uuid.toString()
                    it[PlayerBalanceTable.balance] = balance
                    it[PlayerBalanceTable.lastUpdated] = System.currentTimeMillis()
                }
            }
        }
    }

    suspend fun loadPlayerBalance(uuid: UUID): Double? {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            PlayerBalanceTable.select { PlayerBalanceTable.uuid eq uuid.toString() }
                .map { it[PlayerBalanceTable.balance] }
                .singleOrNull()
        }
    }

    suspend fun loadAllPlayerData(): List<PlayerDataRow> {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            PlayerDataTable.selectAll()
                .map {
                    PlayerDataRow(
                        uuid = UUID.fromString(it[PlayerDataTable.uuid]),
                        name = it[PlayerDataTable.name],
                        rank = it[PlayerDataTable.rank],
                        lastSeen = it[PlayerDataTable.lastSeen]
                    )
                }
        }
    }

    suspend fun loadAllPlayerBalances(): Map<UUID, Double> {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            PlayerBalanceTable.selectAll()
                .associate {
                    UUID.fromString(it[PlayerBalanceTable.uuid]) to it[PlayerBalanceTable.balance]
                }
        }
    }

    // Player Tag Data operations
    suspend fun savePlayerTagData(uuid: UUID, name: String, ownedTags: Set<String>, activeTags: Set<String>) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            // Try to update first, if no rows affected then insert
            val updated = PlayerTagDataTable.update({ PlayerTagDataTable.uuid eq uuid.toString() }) {
                it[PlayerTagDataTable.name] = name
                it[PlayerTagDataTable.ownedTags] = ownedTags.joinToString(",")
                it[PlayerTagDataTable.activeTags] = activeTags.joinToString(",")
                it[PlayerTagDataTable.lastUpdated] = System.currentTimeMillis()
            }

            if (updated == 0) {
                PlayerTagDataTable.insert {
                    it[PlayerTagDataTable.uuid] = uuid.toString()
                    it[PlayerTagDataTable.name] = name
                    it[PlayerTagDataTable.ownedTags] = ownedTags.joinToString(",")
                    it[PlayerTagDataTable.activeTags] = activeTags.joinToString(",")
                    it[PlayerTagDataTable.lastUpdated] = System.currentTimeMillis()
                }
            }
        }
    }

    suspend fun loadAllPlayerTagData(): List<PlayerTagDataRow> {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            PlayerTagDataTable.selectAll()
                .map { row ->
                    PlayerTagDataRow(
                        uuid = UUID.fromString(row[PlayerTagDataTable.uuid]),
                        name = row[PlayerTagDataTable.name],
                        ownedTags = if (row[PlayerTagDataTable.ownedTags].isBlank()) {
                            emptySet()
                        } else {
                            row[PlayerTagDataTable.ownedTags].split(",").toSet()
                        },
                        activeTags = if (row[PlayerTagDataTable.activeTags].isBlank()) {
                            emptySet()
                        } else {
                            row[PlayerTagDataTable.activeTags].split(",").toSet()
                        }
                    )
                }
        }
    }

    fun shutdown() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            Logger.info("Database connection closed")
        }
    }
}

// Database Tables
object PlayerDataTable : Table("player_data") {
    val uuid = varchar("uuid", 36)
    val name = varchar("name", 16)
    val rank = varchar("rank", 32)
    val lastSeen = long("last_seen")

    override val primaryKey = PrimaryKey(uuid)
}

object PlayerBalanceTable : Table("player_balances") {
    val uuid = varchar("uuid", 36)
    val balance = double("balance")
    val lastUpdated = long("last_updated")

    override val primaryKey = PrimaryKey(uuid)
}

object PlayerTagDataTable : Table("player_tag_data") {
    val uuid = varchar("uuid", 36)
    val name = varchar("name", 16)
    val ownedTags = text("owned_tags")
    val activeTags = text("active_tags")
    val lastUpdated = long("last_updated")

    override val primaryKey = PrimaryKey(uuid)
}

// Data classes
data class PlayerDataRow(
    val uuid: UUID,
    val name: String,
    val rank: String,
    val lastSeen: Long
)

data class PlayerTagDataRow(
    val uuid: UUID,
    val name: String,
    val ownedTags: Set<String>,
    val activeTags: Set<String>
)