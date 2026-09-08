package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.infrastructure.runtime.*
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.nio.file.Path
import java.util.UUID

class CivilizationsWorldCommand(private val runtime: CivilizationsRuntime, directory: Path) : BasicCommand {
    private val parser = WorldManifestYaml(directory)
    override fun permission() = "civilizations.admin"
    override fun suggest(source: CommandSourceStack, args: Array<out String>): Collection<String> =
        if (args.size <= 1) listOf("diamond-status", "enable-diamonds", "cane-status", "enable-cane", "worlds", "validate", "import", "list", "inspect", "here").filter { it.startsWith(args.lastOrNull().orEmpty()) } else emptyList()

    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        val sender = source.sender
        if (!sender.hasPermission(permission())) return
        val ready = runtime.state as? CivilizationsRuntimeState.Ready ?: return tell(sender, "Civilizations is not ready")
        when (args.firstOrNull()) {
            "diamond-status" -> {
                if (args.size != 1) return help(sender)
                val d = ready.diamondActivation
                tell(sender, if (d == null) "diamond=OFF; reconstruction resource exclusions always apply" else
                    "diamond=ON season=${d.seasonId} equipment=${d.includeEquipment} audit=${d.auditSha256}; actor=${d.actor}; at=${d.activatedAt}; reason=${d.reason}")
            }
            "enable-diamonds" -> {
                if (args.size < 4 || args[1] !in setOf("raw-only", "equipment")) return tell(sender,
                    "/civworld enable-diamonds <raw-only|equipment> <audit-sha256> <reason>. Permanent server-wide loot/trade restriction; hash attests a reviewed unopened-world audit, not automatic release certification.")
                val season = ready.activeSeason?.season ?: return tell(sender, "No active season")
                val actor = (sender as? Player)?.uniqueId?.toString() ?: "console"
                val worlds = java.util.List.copyOf(sender.server.worlds.map { LoadedResourceWorld(WorldId(it.key.asString()), it.uid, it.minHeight, it.maxHeight) })
                runtime.submitMutation(operation = {
                    diamondActivation.enable(season.id, args[1] == "equipment", args[2], actor, args.drop(3).joinToString(" "), worlds)
                }, completion = { outcome ->
                    when (outcome) {
                        is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                            is ApplicationResult.Rejected -> tell(sender, "Rejected: ${result.failure.description}")
                            else -> tell(sender, "Diamond policy enabled; /civworld diamond-status")
                        }
                        is RuntimeMutationOutcome.Failed -> tell(sender, "Storage failed; see server log")
                        is RuntimeMutationOutcome.NotReady -> tell(sender, "Civilizations is not ready")
                    }
                })
            }
            "cane-status" -> {
                if (args.size != 1) return help(sender)
                tell(sender, status(ready))
                ready.caneActivation?.let { tell(sender, "actor=${it.actor}; at=${it.activatedAt}; reason=${it.reason}; manifests frozen; other dimensions deny cane creation") }
            }
            "enable-cane" -> {
                val height = args.getOrNull(1)?.toIntOrNull()
                if (height == null || args.size < 3) return tell(sender, "/civworld enable-cane <max-height 1..3> <audit reason>. Enables server-wide cane restrictions permanently for this experiment; register all worlds first.")
                val season = ready.activeSeason?.season ?: return tell(sender, "No active season")
                val reason = args.drop(2).joinToString(" ")
                val actor = (sender as? Player)?.uniqueId?.toString() ?: "console"
                val worlds = java.util.List.copyOf(sender.server.worlds.map { LoadedResourceWorld(WorldId(it.key.asString()), it.uid, it.minHeight, it.maxHeight) })
                runtime.submitMutation(operation = { caneActivation.enable(season.id, height, actor, reason, worlds) }, completion = { outcome ->
                    when (outcome) {
                        is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                            is ApplicationResult.Rejected -> tell(sender, "Rejected: ${result.failure.description}")
                            else -> tell(sender, status(outcome.state))
                        }
                        is RuntimeMutationOutcome.Failed -> tell(sender, "Storage failed; see server log")
                        is RuntimeMutationOutcome.NotReady -> tell(sender, "Civilizations is not ready")
                    }
                })
            }
            "validate", "import" -> {
                if (args.size != 2) return help(sender)
                // Live identity is captured only on the server thread; the worker receives values.
                val worlds = java.util.List.copyOf(sender.server.worlds.map { LoadedResourceWorld(WorldId(it.key.asString()), it.uid, it.minHeight, it.maxHeight) })
                val actor = (sender as? Player)?.uniqueId?.toString() ?: "console"
                val filename = args[1]
                val persist = args[0] == "import"
                runtime.submitMutation(operation = {
                    val manifest = try { parser.read(filename) }
                    catch (e: java.io.IOException) { return@submitMutation ApplicationResult.Rejected(WorldManifestRejected("file: ${e.message}")) }
                    catch (e: org.bukkit.configuration.InvalidConfigurationException) { return@submitMutation ApplicationResult.Rejected(WorldManifestRejected("YAML: ${e.message}")) }
                    catch (e: IllegalArgumentException) { return@submitMutation ApplicationResult.Rejected(WorldManifestRejected(e.message ?: "Invalid manifest")) }
                    if (persist) worldManifests.register(manifest, worlds, actor) else worldManifests.validate(manifest, worlds, actor)
                }, completion = { outcome ->
                    when (outcome) {
                        is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                            is ApplicationResult.Applied -> tell(sender, "${if (persist) "Registered" else "Validated (not saved)"} ${result.value.manifest.id}; ${result.value.manifest.zones.size} zones; ${status(ready)}")
                            is ApplicationResult.Unchanged -> tell(sender, "Already registered identically: ${result.value.manifest.id}; ${status(ready)}")
                            is ApplicationResult.Rejected -> tell(sender, "Rejected: ${result.failure.description}")
                        }
                        is RuntimeMutationOutcome.Failed -> tell(sender, "Storage failed; see server log")
                        is RuntimeMutationOutcome.NotReady -> tell(sender, "Civilizations is not ready")
                    }
                })
            }
            "worlds" -> {
                if (args.size != 1) return help(sender)
                sender.server.worlds.forEach {
                    tell(sender, "${it.key.asString()} uuid=${it.uid} build-height=${it.minHeight}..${it.maxHeight - 1}")
                }
            }
            "list" -> {
                if (args.size != 1) return help(sender)
                if (ready.worldManifests.isEmpty()) tell(sender, "No world manifests registered; ${status(ready)}")
                ready.worldManifests.forEach { record ->
                    val m = record.manifest
                    tell(sender, "${m.id} season=${m.seasonId} world=${m.worldId} revision=${m.revision} zones=${m.zones.size} ${status(ready)}")
                }
            }
            "inspect" -> {
                val id = args.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (args.size != 2 || id == null) return help(sender)
                val r = ready.worldManifests.singleOrNull { it.manifest.id == id } ?: return tell(sender, "Manifest not found")
                val m = r.manifest
                val live = sender.server.worlds.singleOrNull { it.key.asString() == m.worldId.value }
                tell(sender, "${m.id} season=${m.seasonId} world=${m.worldId}/${m.worldUuid} revision=${m.revision} ${status(ready)}")
                tell(sender, "Identity=${if (live == null) "UNLOADED" else if (live.uid == m.worldUuid) "MATCH" else "MISMATCH"}; sha256=${m.sourceSha256}; imported=${r.importedAt}; actor=${r.actor}")
                tell(sender, "Bounds=${m.bounds}")
                m.zones.forEach { tell(sender, "${it.id}: ${it.resource} ${it.bounds}") }
            }
            "here" -> {
                if (args.size != 1) return help(sender)
                val player = sender as? Player ?: return tell(sender, "here requires a player")
                val seasonId = ready.caneActivation?.seasonId ?: ready.activeSeason?.season?.id ?: return tell(sender, "No active season")
                val l = player.location
                val zones = ready.resourceZoneIndex.at(seasonId, WorldId(player.world.key.asString()), player.world.uid, l.blockX, l.blockY, l.blockZ)
                tell(sender, "Registered zones here: ${zones.joinToString { it.id + ":" + it.resource }.ifEmpty { "none" }}; ${status(ready)}")
            }
            else -> help(sender)
        }
    }
    private fun status(ready: CivilizationsRuntimeState.Ready): String = ready.caneActivation?.let {
        "cane=ON season=${it.seasonId} max-height=${it.maxHeight}; diamond=${if (ready.diamondActivation == null) "OFF" else "ON"}; cattle: /civcattle status"
    } ?: "cane=OFF; diamond=${if (ready.diamondActivation == null) "OFF" else "ON"}; cattle: /civcattle status"
    private fun help(sender: CommandSender) = tell(sender, "/civworld validate|import <file.yml> | worlds | list | inspect <manifest-uuid> | here | diamond-status | enable-diamonds <raw-only|equipment> <audit-sha256> <reason> | cane-status | enable-cane <height> <reason>.")
    private fun tell(sender: CommandSender, message: String) { sender.sendMessage(Component.text(message)) }
}
