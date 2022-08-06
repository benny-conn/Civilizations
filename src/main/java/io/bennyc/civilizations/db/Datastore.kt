/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.db

import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.RandomUtil
import org.mineacademy.fo.SerializeUtil
import org.mineacademy.fo.Valid
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.collection.StrictMap
import org.mineacademy.fo.debug.Debugger
import org.mineacademy.fo.model.ConfigSerializable
import java.sql.*
import java.util.*
import java.util.concurrent.TimeUnit

abstract class Datastore {
    private var connection: Connection? = null
    private var lastCredentials: LastCredentials? = null
    private val sqlVariables = StrictMap<String, String>()
    private var batchUpdateGoingOn = false

    fun connect(
        host: String,
        port: Int,
        database: String,
        user: String,
        password: String,
        table: String,
        autoReconnect: Boolean = true
    ) {
        this.connect(
            "jdbc:mysql://$host:$port/$database?useSSL=false&useUnicode=yes&characterEncoding=UTF-8&autoReconnect=$autoReconnect",
            user,
            password,
            table
        )
    }

    fun connect(url: String, user: String, password: String, table: String) {
        close()
        try {
            lastCredentials = LastCredentials(url, user, password, table)
            connection = DriverManager.getConnection(url, user, password)
            createTablesIfNotExist()
        } catch (sqlException: SQLException) {
            if (Common.getOrEmpty(sqlException.message).contains("No suitable driver found")) {
                Common.logFramed(
                    true,
                    "Failed to look up MySQL driver",
                    "If you had MySQL disabled, then enabled it and reload,",
                    "this is normal - just restart.",
                    "",
                    "You have have access to your server machine, try installing",
                    "https://dev.mysql.com/downloads/connector/j/5.1.html#downloads",
                    "",
                    "If this problem persists after a restart, please contact",
                    "your hosting provider."
                )
            } else {
                Common.logFramed(
                    true,
                    "Failed to connect to MySQL database",
                    "URL: $url",
                    "Error: " + sqlException.message
                )
            }
        }
    }

    abstract fun createTablesIfNotExist()

    private fun connectUsingLastCredentials() {
        if (lastCredentials != null) {
            this.connect(
                lastCredentials!!.url,
                lastCredentials!!.user,
                lastCredentials!!.password,
                lastCredentials!!.table
            )
        }
    }

    fun close() {
        if (connection != null) {
            synchronized(connection!!)
            {
                try {
                    connection!!.close()
                } catch (var4: SQLException) {
                    Common.error(var4, "Error closing MySQL connection!")
                }
            }

        }
    }

    protected fun insert(map: SerializedMap) {
        val columns = Common.join(map.keySet())
        val values = Common.join(map.values(), ", ") { value: Any? -> parseValue(value) }
        if (io.bennyc.civilizations.settings.Settings.DB_TYPE.equals(
                "sqlite",
                ignoreCase = true
            )
        ) this.update("INSERT INTO " + replaceVariables("{table}") + " (" + columns + ") VALUES (" + values + ")" + " ON CONFLICT(uuid) DO UPDATE SET (" + columns + ")=(" + values + ");") else {
            val duplicateUpdate = Common.join(
                map.entrySet(),
                ", "
            ) { entry: Map.Entry<String, Any?> -> entry.key + "=VALUES(" + entry.value + ")" }
            this.update("INSERT INTO " + replaceVariables("{table}") + " (" + columns + ") VALUES (" + values + ") ON DUPLICATE KEY UPDATE " + duplicateUpdate + ";")
        }
    }

    protected fun insertBatch(maps: List<SerializedMap>) {
        val sqls: MutableList<String> = ArrayList()
        for (map in maps) {
            val columns = Common.join(map.keySet())
            val values = Common.join(map.values(), ", ") { value: Any? -> parseValue(value) }
            val duplicateUpdate = Common.join(
                map.entrySet(),
                ", "
            ) { entry: Map.Entry<String, Any?> -> entry.key + "=VALUES(" + entry.key + ")" }
            sqls.add("INSERT INTO {table} ($columns) VALUES ($values) ON DUPLICATE KEY UPDATE $duplicateUpdate;")
        }
        batchUpdate(sqls)
    }

    protected fun update(map: SerializedMap, uuid: UUID) {
        val columns = Common.join(map.keySet(), ", ")
        val values = Common.join(map.values(), ", ") { value: Any? -> parseValue(value) }
        this.update("UPDATE " + replaceVariables("{table}") + " SET (" + columns + ")=(" + values + ") WHERE uuid='" + uuid + "';")
    }

    protected fun update(sql: String) {
        checkEstablished()
        synchronized(connection!!) {
            if (!isConnected) {
                connectUsingLastCredentials()
            }
            val s = replaceVariables(sql)
            Valid.checkBoolean(
                !s.contains("{table}"),
                "Table not set! Either use connect() method that specifies it or call addVariable(table, 'yourtablename') in your constructor!"
            )
            Debugger.debug("mysql", "Updating MySQL with: $s")
            try {
                connection!!.createStatement().run { executeUpdate(s) }
            } catch (var5: SQLException) {
                Common.error(var5, "Error on updating MySQL with: $s")
            }
        }
    }

    protected fun query(sql: String): ResultSet? {
        checkEstablished()
        synchronized(connection!!) {
            if (!isConnected) {
                connectUsingLastCredentials()
            }
            val sql = replaceVariables(sql)
            Debugger.debug("mysql", "Querying MySQL with: $sql")
            return try {
                connection!!.createStatement().run { executeQuery(sql) }
            } catch (var6: SQLException) {
                var6.printStackTrace()
                Common.throwError(var6, "Error on querying MySQL with: $sql")
                return null
            }
        }
    }

    private fun batchUpdate(sqls: List<String>) {
        if (sqls.isNotEmpty()) {
            try {
                val batchStatement =
                    connection!!.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)
                val processedCount = sqls.size
                connection!!.autoCommit = false
                for (sql in sqls) {
                    batchStatement.addBatch(replaceVariables(sql))
                }
                if (processedCount > 10000) {
                    Common.log("Updating your database (" + processedCount + " entries)... PLEASE BE PATIENT THIS WILL TAKE " + (if (processedCount > 50000) "10-20 MINUTES" else "5-10 MINUTES") + " - If server will print a crash report, ignore it, update will proceed.")
                }
                batchUpdateGoingOn = true
                Timer().scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        if (batchUpdateGoingOn) {
                            Common.log(
                                "Still executing, " + RandomUtil.nextItem(
                                    "keep calm",
                                    "stand by",
                                    "watch the show",
                                    "check your db",
                                    "drink water",
                                    "call your friend"
                                ) as String + " and DO NOT SHUTDOWN YOUR SERVER."
                            )
                        } else {
                            cancel()
                        }
                    }
                }, 30000L, 30000L)
                batchStatement.executeBatch()
                connection!!.commit()
            } catch (var14: Throwable) {
                var14.printStackTrace()
            } finally {
                try {
                    connection!!.autoCommit = true
                } catch (var13: SQLException) {
                    var13.printStackTrace()
                }
                batchUpdateGoingOn = false
            }
        }
    }

    fun delete(uuid: UUID) {
        update("DELETE FROM {table} WHERE uuid='$uuid'")
    }

    private fun parseValue(value: Any?): String {
        if (value == null || value == "NULL") return "NULL"
        return if (value is ConfigSerializable) "'" + value.serialize()
            .toJson() + "'" else "'" + SerializeUtil.serialize(value).toString() + "'"
    }

    fun removeOldEntries() {
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(expirationDays.toLong())
        this.update("DELETE FROM {table} WHERE Updated < $threshold")
    }

    protected open val expirationDays: Int
        get() = Settings.DELETE_AFTER

    @Throws(SQLException::class)
    fun isStored(uuid: UUID): Boolean {
        return run {
            val resultSet = query("SELECT * FROM {table} WHERE uuid= '$uuid'")
            when {
                resultSet == null -> {
                    false
                }

                resultSet.next() -> {
                    resultSet.getString("uuid") != null
                }

                else -> {
                    false
                }
            }
        }
    }

    @Throws(SQLException::class)
    protected fun prepareStatement(sql: String): PreparedStatement {
        var sql = sql
        checkEstablished()
        synchronized(connection!!) {
            if (!isConnected) {
                connectUsingLastCredentials()
            }
            sql = replaceVariables(sql)
            Debugger.debug("mysql", "Preparing statement: $sql")
            return connection!!.prepareStatement(sql)
        }
    }

    private val isConnected: Boolean
        get() = if (!isLoaded) {
            false
        } else {
            synchronized(connection!!) {
                return try {
                    connection != null && !connection!!.isClosed && connection!!.isValid(0)
                } catch (var4: SQLException) {
                    return false
                }
            }
        }
    private val table: String
        get() {
            checkEstablished()
            return Common.getOrEmpty(lastCredentials!!.table)
        }

    private fun checkEstablished() {
        Valid.checkBoolean(isLoaded, "Connection was never established")
    }

    private val isLoaded: Boolean
        get() = connection != null

    protected fun addVariable(name: String, value: String) {
        sqlVariables.put(name, value)
    }

    private fun replaceVariables(sql: String): String {
        var sql = sql
        var entry: Map.Entry<String, String>
        val var2: Iterator<Map.Entry<String, String>> = sqlVariables.entrySet().iterator()
        while (var2.hasNext()) {
            entry = var2.next()
            sql = sql.replace("{" + entry.key + "}", entry.value)
        }
        return sql.replace("{table}", table)
    }

    class LastCredentials(val url: String, val user: String, val password: String, val table: String)
}