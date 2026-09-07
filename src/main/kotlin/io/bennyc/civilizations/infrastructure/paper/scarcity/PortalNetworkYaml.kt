package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class PortalNetworkYaml(private val directory: Path) {
    fun read(name: String): PortalNetwork {
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,64}\\.yml"))) { "file: expected simple .yml filename" }
        val root=directory.toRealPath()
        val file=root.resolve(name).toRealPath()
        require(file.startsWith(root)) { "file: must stay inside portals directory" }
        return parse(Files.newInputStream(file).use { it.readNBytes(1_048_577) })
    }
    companion object {
        fun parse(bytes: ByteArray): PortalNetwork {
            require(bytes.size <= 1_048_576) { "file: maximum size 1 MiB" }
            val options=LoaderOptions().apply { isAllowDuplicateKeys=false;maxAliasesForCollections=0;nestingDepthLimit=24;codePointLimit=1_048_576 }
            val root=try { map(Yaml(SafeConstructor(options)).load<Any?>(bytes.toString(Charsets.UTF_8)), "network") }
            catch (e: org.yaml.snakeyaml.error.YAMLException) { throw IllegalArgumentException("YAML: ${e.message}",e) }
            keys(root,setOf("format","season","pairs"),"network")
            require(root["format"] == 1) { "format: expected integer 1" }
            val pairs=root["pairs"] as? List<*> ?: throw IllegalArgumentException("pairs: expected list")
            require(pairs.size in 1..16) { "pairs: expected 1..16" }
            val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return PortalNetwork(SeasonId(uuid(root["season"],"season")), pairs.mapIndexed { i,value ->
                val p=map(value,"pairs[$i]");keys(p,setOf("id","first","second"),"pairs[$i]")
                PortalPair(text(p["id"],"pairs[$i].id"),site(p["first"],"pairs[$i].first"),site(p["second"],"pairs[$i].second"))
            },hash,"validation",Instant.EPOCH)
        }
        private fun site(value: Any?, path: String): PortalSite {
            val m=map(value,path);keys(m,setOf("world","uuid","min-x","min-y","min-z","max-x","max-y","max-z"),path)
            val coords=listOf("min-x","min-y","min-z","max-x","max-y","max-z").map {
                m[it] as? Int ?: throw IllegalArgumentException("$path.$it: expected 32-bit integer")
            }
            return PortalSite(WorldId(text(m["world"],"$path.world")),uuid(m["uuid"],"$path.uuid"),ResourceBounds(coords[0],coords[1],coords[2],coords[3],coords[4],coords[5]))
        }
        private fun map(v: Any?,p: String)=v as? Map<*,*> ?: throw IllegalArgumentException("$p: expected mapping")
        private fun text(v: Any?,p: String)=v as? String ?: throw IllegalArgumentException("$p: expected string")
        private fun keys(m: Map<*,*>, expected: Set<String>,p: String) { require(m.keys==expected) { "$p: expected keys $expected" } }
        private fun uuid(v: Any?,p: String): UUID {
            val raw=text(v,p)
            return try { UUID.fromString(raw).also { require(it.toString()==raw) } } catch (_: IllegalArgumentException) { throw IllegalArgumentException("$p: expected canonical UUID") }
        }
    }
}
