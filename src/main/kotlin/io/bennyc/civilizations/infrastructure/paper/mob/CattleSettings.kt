package io.bennyc.civilizations.infrastructure.paper.mob

import io.bennyc.civilizations.application.mob.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

object CattleSettings {
    fun read(config: ConfigurationSection): MobRules {
        require(!config.contains("scarcity.cattle") || config.isConfigurationSection("scarcity.cattle")) { "scarcity.cattle: expected section" }
        fun seconds(key: String, default: Long): Long {
            val path="scarcity.cattle.$key"
            val raw=config.get(path) ?: default
            require(raw is Int || raw is Long) { "$path: expected integer seconds" }
            val value=(raw as Number).toLong()
            require(value in 1..31_536_000) { "$path: expected 1..31536000 seconds" }
            return value*1000
        }
        return MobRules(seconds("maturity-seconds",43200),seconds("breeding-cooldown-seconds",21600))
    }
    fun seeds(directory: Path, name: String): List<CattleSeed> {
        require(name.matches(Regex("[a-zA-Z0-9_-]+\\.yml"))) { "file: expected a simple .yml filename" }
        val path=directory.resolve(name)
        require(Files.size(path)<=65_536) { "file: maximum 64 KiB" }
        require(path.toRealPath().parent==directory.toRealPath()) { "file: outside cattle directory" }
        val yaml=YamlConfiguration();yaml.load(path.toFile())
        require(yaml.getKeys(false)==setOf("seeds")) { "file: expected only seeds" }
        val raw=yaml.getList("seeds") ?: throw IllegalArgumentException("seeds: expected list")
        require(raw.size in 2..128) { "seeds: expected 2..128 entries" }
        return raw.mapIndexed { i, item ->
            val m=item as? Map<*,*> ?: throw IllegalArgumentException("seeds[$i]: expected mapping")
            require(m.keys==setOf("id","world-uuid","x","y","z")) { "seeds[$i]: expected id, world-uuid, x, y, z" }
            fun coordinate(k: String): Int { val n=m[k];require(n is Int) { "seeds[$i].$k: expected integer" };return n }
            val id=m["id"] as? String ?: throw IllegalArgumentException("seeds[$i].id: expected text")
            val uuid=try { UUID.fromString(m["world-uuid"] as? String) } catch(e: Exception) { throw IllegalArgumentException("seeds[$i].world-uuid: expected UUID") }
            CattleSeed(id,uuid,coordinate("x"),coordinate("y"),coordinate("z"))
        }.also { require(it.map { s -> s.id }.distinct().size==it.size) { "seeds.id: duplicates" } }
    }
}
