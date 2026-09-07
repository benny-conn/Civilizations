package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.infrastructure.runtime.*
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.nio.file.Path

class CivilizationsPortalCommand(private val runtime: CivilizationsRuntime, directory: Path) : BasicCommand {
    private val parser=PortalNetworkYaml(directory)
    override fun permission()="civilizations.admin"
    override fun suggest(source: CommandSourceStack,args: Array<out String>): Collection<String> = if(args.size<=1) listOf("check","validate","enable").filter { it.startsWith(args.lastOrNull().orEmpty()) } else emptyList()
    override fun execute(source: CommandSourceStack,args: Array<out String>) {
        val sender=source.sender
        if(!sender.hasPermission(permission())) return
        val ready=runtime.state as? CivilizationsRuntimeState.Ready ?: return tell(sender,"Civilizations is not ready")
        val live=PaperPortalSites(sender.server)
        when(args.firstOrNull()) {
            "check" -> {
                val network=ready.portals ?: return tell(sender,"Registered portal enforcement OFF")
                tell(sender,"Portal enforcement ON; season=${network.seasonId}; pairs=${network.pairs.size}; actor=${network.actor}; at=${network.importedAt}")
                network.pairs.forEach { p ->
                    tell(sender,"${p.id}: ${p.first.world}/${p.first.uuid} ${p.first.bounds} → ${p.second.world}/${p.second.uuid} ${p.second.bounds}")
                    tell(sender,"${p.id}: first=${live.problem(p.first) ?: "ready"}; second=${live.problem(p.second) ?: "ready"}. Repair/re-light frames and clear exit platforms as needed.")
                }
            }
            "validate","enable" -> {
                if(args.size!=2) return help(sender)
                val enable=args[0]=="enable"
                val name=args[1]
                val actor=(sender as? Player)?.uniqueId?.toString() ?: "console"
                runtime.submitMutation(operation={
                    try { ApplicationResult.Applied(parser.read(name)) }
                    catch(e: java.io.IOException) { ApplicationResult.Rejected(WorldManifestRejected("file: ${e.message}")) }
                    catch(e: IllegalArgumentException) { ApplicationResult.Rejected(WorldManifestRejected(e.message ?: "Invalid portal file")) }
                },completion={ outcome ->
                    when(outcome) {
                        is RuntimeMutationOutcome.Completed -> when(val result=outcome.result) {
                            is ApplicationResult.Applied -> {
                                val network=result.value
                                val problems=network.pairs.flatMap { p ->
                                    val envs=setOf(live.world(p.first)?.environment,live.world(p.second)?.environment)
                                    buildList {
                                        if(envs!=setOf(World.Environment.NORMAL,World.Environment.NETHER)) add("${p.id}: pair must connect a loaded Overworld and Nether")
                                        live.problem(p.first)?.let { add("${p.id}.first: $it") }
                                        live.problem(p.second)?.let { add("${p.id}.second: $it") }
                                    }
                                }
                                if(problems.isNotEmpty()) tell(sender,"Rejected: ${problems.joinToString("; ")}")
                                else if(!enable) tell(sender,"Geometry/worlds valid; ${network.pairs.size} pairs; nothing saved. Enabling requires active SETUP.")
                                else runtime.submitMutation(operation={ portals.install(network.seasonId,network.pairs,network.sourceSha256,actor) },completion={ saved ->
                                    when(saved) {
                                        is RuntimeMutationOutcome.Completed -> when(val r=saved.result) {
                                            is ApplicationResult.Rejected -> tell(sender,"Rejected: ${r.failure.description}")
                                            else -> tell(sender,"Fixed Nether portal network enabled; ${network.pairs.size} pairs. Use /civportal check.")
                                        }
                                        else -> tell(sender,"Portal setup failed; check server status/log.")
                                    }
                                })
                            }
                            is ApplicationResult.Rejected -> tell(sender,"Rejected: ${result.failure.description}")
                            is ApplicationResult.Unchanged -> Unit
                        }
                        else -> tell(sender,"Portal setup unavailable; check server status/log.")
                    }
                })
            }
            else -> help(sender)
        }
    }
    private fun help(sender: CommandSender)=tell(sender,"/civportal validate <file.yml> | enable <file.yml> | check. Enable installs fixed two-way Nether pairs; prepare both lit portals and clear exits first.")
    private fun tell(sender: CommandSender,message: String) { sender.sendMessage(Component.text(message)) }
}
