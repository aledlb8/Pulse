package dev.aledlb.pulse.database

import dev.aledlb.pulse.Pulse
import dev.aledlb.pulse.util.Logger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
        val postgresNode = config.node("postgresql")
        val poolNode = config.node("pool")

        val host = postgresNode.node("host").getString("localhost") ?: "localhost"
        val port = postgresNode.node("port").getInt(5432)
        val database = postgresNode.node("database").getString("pulse") ?: "pulse"
        val username = postgresNode.node("username").getString("pulse") ?: "pulse"
        val password = postgresNode.node("password").getString("password") ?: "password"

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
            SchemaUtils.create(PlayerDataTable, PlayerBalanceTable, PlayerTagDataTable, PunishmentTable, ActivePunishmentTable)
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

    // Punishment operations
    suspend fun savePunishment(
        uuid: UUID,
        type: String,
        reason: String,
        punisher: UUID,
        punisherName: String,
        duration: Long?,
        ip: String?
    ): Int {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            PunishmentTable.insert {
                it[PunishmentTable.uuid] = uuid.toString()
                it[PunishmentTable.type] = type
                it[PunishmentTable.reason] = reason
                it[PunishmentTable.punisher] = punisher.toString()
                it[PunishmentTable.punisherName] = punisherName
                it[PunishmentTable.timestamp] = System.currentTimeMillis()
                it[PunishmentTable.duration] = duration
                it[PunishmentTable.ip] = ip
                it[PunishmentTable.active] = true
            } get PunishmentTable.id
        }
    }

    suspend fun setActivePunishment(uuid: UUID, type: String, expires: Long?, punishmentId: Int) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            ActivePunishmentTable.deleteWhere {
                (ActivePunishmentTable.uuid eq uuid.toString()) and (ActivePunishmentTable.type eq type)
            }
            ActivePunishmentTable.insert {
                it[ActivePunishmentTable.uuid] = uuid.toString()
                it[ActivePunishmentTable.type] = type
                it[ActivePunishmentTable.expires] = expires
                it[ActivePunishmentTable.punishmentId] = punishmentId
            }
        }
    }

    suspend fun removeActivePunishment(uuid: UUID, type: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            ActivePunishmentTable.deleteWhere {
                (ActivePunishmentTable.uuid eq uuid.toString()) and (ActivePunishmentTable.type eq type)
            }
        }
    }

    suspend fun getActivePunishment(uuid: UUID, type: String): ActivePunishmentRow? {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            ActivePunishmentTable.select { (ActivePunishmentTable.uuid eq uuid.toString()) and (ActivePunishmentTable.type eq type) }
                .map {
                    ActivePunishmentRow(
                        uuid = UUID.fromString(it[ActivePunishmentTable.uuid]),
                        type = it[ActivePunishmentTable.type],
                        expires = it[ActivePunishmentTable.expires],
                        punishmentId = it[ActivePunishmentTable.punishmentId]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun getPlayerPunishments(uuid: UUID): List<PunishmentRow> {
        return newSuspendedTransaction(Dispatchers.IO, database) {
            PunishmentTable.select { PunishmentTable.uuid eq uuid.toString() }
                .orderBy(PunishmentTable.timestamp, SortOrder.DESC)
                .map {
                    PunishmentRow(
                        id = it[PunishmentTable.id],
                        uuid = UUID.fromString(it[PunishmentTable.uuid]),
                        type = it[PunishmentTable.type],
                        reason = it[PunishmentTable.reason],
                        punisher = UUID.fromString(it[PunishmentTable.punisher]),
                        punisherName = it[PunishmentTable.punisherName],
                        timestamp = it[PunishmentTable.timestamp],
                        duration = it[PunishmentTable.duration],
                        ip = it[PunishmentTable.ip],
                        active = it[PunishmentTable.active],
                        removedBy = it[PunishmentTable.removedBy]?.let { id -> UUID.fromString(id) },
                        removedAt = it[PunishmentTable.removedAt]
                    )
                }
        }
    }

    suspend fun deactivatePunishment(punishmentId: Int, removedBy: UUID) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            PunishmentTable.update({ PunishmentTable.id eq punishmentId }) {
                it[active] = false
                it[PunishmentTable.removedBy] = removedBy.toString()
                it[removedAt] = System.currentTimeMillis()
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

// Punishment Tables
object PunishmentTable : Table("punishments") {
    val id = integer("id").autoIncrement()
    val uuid = varchar("uuid", 36)
    val type = varchar("type", 32) // BAN, TEMPBAN, IPBAN, TEMPIPBAN, MUTE, KICK, WARN
    val reason = text("reason")
    val punisher = varchar("punisher", 36)
    val punisherName = varchar("punisher_name", 16)
    val timestamp = long("timestamp")
    val duration = long("duration").nullable() // null = permanent
    val ip = varchar("ip", 45).nullable()
    val active = bool("active")
    val removedBy = varchar("removed_by", 36).nullable()
    val removedAt = long("removed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object ActivePunishmentTable : Table("active_punishments") {
    val uuid = varchar("uuid", 36)
    val type = varchar("type", 32) // MUTE, FREEZE, BAN
    val expires = long("expires").nullable() // null = permanent
    val punishmentId = integer("punishment_id")

    override val primaryKey = PrimaryKey(uuid, type)
}

data class PunishmentRow(
    val id: Int,
    val uuid: UUID,
    val type: String,
    val reason: String,
    val punisher: UUID,
    val punisherName: String,
    val timestamp: Long,
    val duration: Long?,
    val ip: String?,
    val active: Boolean,
    val removedBy: UUID?,
    val removedAt: Long?
)

data class ActivePunishmentRow(
    val uuid: UUID,
    val type: String,
    val expires: Long?,
    val punishmentId: Int
)