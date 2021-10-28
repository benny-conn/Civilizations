package net.tolmikarc.civilizations.adapter


import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.mineacademy.fo.Common
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.set

internal class UUIDFetcher(private val names: MutableList<String>) {
    private val gson: Gson = Gson()

    fun call() {
        Common.log("UUID conversion process started.  Please be patient - this may take a while.")
        Common.log("Mining your local world data to save calls to Mojang...")
        val players: Array<OfflinePlayer> = Bukkit.getOfflinePlayers()
        for (player in players) {
            if (player.name != null) {
                lookupCache[player.name!!] = player.uniqueId
                lookupCache[player.name!!.lowercase(Locale.getDefault())] = player.uniqueId
                correctedNames[player.name!!.lowercase(Locale.getDefault())] = player.name!!
            }
        }

        //try to get correct casing from local data
        Common.log("Checking local server data to get correct casing for player names...")
        var namePointer = 0
        while (namePointer < names.size) {
            val name = names[namePointer]
            val correctCasingName = correctedNames[name]
            if (correctCasingName != null && name != correctCasingName) {
                Common.log("$name --> $correctCasingName")
                names[namePointer] = correctCasingName
            }
            namePointer++
        }

        //look for local uuid's first
        Common.log("Checking local server data for UUIDs already seen...")
        var secondNamePointer = 0
        while (secondNamePointer < names.size) {
            val name = names[secondNamePointer]
            val uuid = lookupCache[name]
            if (uuid != null) {
                Common.log("$name --> $uuid")
                names.removeAt(secondNamePointer--)
            }
            secondNamePointer++
        }

        //for online mode, call Mojang to resolve the rest
        if (Bukkit.getServer().onlineMode) {
            val validNamePattern: Pattern = Pattern.compile("^\\w+$")

            // Don't bother requesting UUIDs for invalid names from Mojang.
            names.removeIf { name ->
                if (name.length in 3..16 && validNamePattern.matcher(name).find())
                    return@removeIf false
                Common.log(java.lang.String.format("Cannot convert invalid name: %s", name))

                true
            }
            Common.log("Calling Mojang to get UUIDs for remaining unresolved players (this is the slowest step)...")
            var i = 0
            while (i * PROFILES_PER_REQUEST < names.size) {
                var retry: Boolean
                var array: JsonArray?
                do {
                    val connection: HttpURLConnection = createConnection()
                    val body: String = gson.toJson(
                        names.subList(
                            i * PROFILES_PER_REQUEST,
                            ((i + 1) * PROFILES_PER_REQUEST).coerceAtMost(names.size)
                        )
                    )
                    writeBody(connection, body)
                    retry = false
                    array = null
                    try {
                        array = gson.fromJson(InputStreamReader(connection.inputStream), JsonArray::class.java)
                    } catch (e: Exception) {
                        //in case of error 429 too many requests, pause and then retry later
                        if (e.message!!.contains("429")) {
                            retry = true

                            //if this is the first time we're sending anything, the batch size must be too big
                            //try reducing it
                            if (i == 0 && PROFILES_PER_REQUEST > 1) {
                                Common.log("Batch size $PROFILES_PER_REQUEST seems too large.  Looking for a workable batch size...")
                                PROFILES_PER_REQUEST = (PROFILES_PER_REQUEST - 5).coerceAtLeast(1)
                            } else {
                                Common.log("Mojang says we're sending requests too fast.  Will retry every 30 seconds until we succeed...")
                                Thread.sleep(3000)
                            }
                        } else {
                            throw e
                        }
                    }
                } while (retry)

                if (array != null) {
                    for (profile in array) {
                        val jsonProfile: JsonObject = profile.asJsonObject
                        val id: String = jsonProfile.get("id").asString
                        val name: String = jsonProfile.get("name").asString
                        val uuid = getUUID(id)
                        Common.log("$name --> $uuid")
                        lookupCache[name] = uuid
                        lookupCache[name.lowercase(Locale.getDefault())] = uuid
                    }
                }
                i++
            }
        } else {
            Common.log("Generating offline mode UUIDs for remaining unresolved players...")
            for (name in names) {
                val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
                Common.log("$name --> $uuid")
                lookupCache[name] = uuid
                lookupCache[name.lowercase(Locale.getDefault())] = uuid
            }
        }
    }

    companion object {
        private var PROFILES_PER_REQUEST = 100
        private const val PROFILE_URL = "https://api.mojang.com/profiles/minecraft"

        //cache for username -> uuid lookups
        var lookupCache: MutableMap<String, UUID> = HashMap()

        //record of username -> proper casing updates
        var correctedNames: MutableMap<String, String> = HashMap()

        private fun writeBody(connection: HttpURLConnection, body: String) {
            val stream: OutputStream = connection.outputStream
            stream.write(body.toByteArray())
            stream.flush()
            stream.close()
        }

        private fun createConnection(): HttpURLConnection {
            val url = URL(PROFILE_URL)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.useCaches = false
            connection.doInput = true
            connection.doOutput = true
            return connection
        }

        private fun getUUID(id: String): UUID {
            return UUID.fromString(
                id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(
                    12,
                    16
                ) + "-" + id.substring(16, 20) + "-" + id.substring(20, 32)
            )
        }

        @Throws(Exception::class)
        fun getUUIDOf(name: String): UUID? {
            return lookupCache[name]
        }
    }
}