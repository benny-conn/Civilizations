package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.WarStatus
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationScope
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import java.util.Locale
import java.util.UUID

class CivilizationsAdminCommand(
    private val runtime: CivilizationsRuntime,
) : BasicCommand {
    override fun execute(
        source: CommandSourceStack,
        args: Array<out String>,
    ) {
        val sender = source.sender
        when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
            "status" -> showStatus(sender)
            "season" -> handleSeason(sender, args)
            "civilization", "civ" -> handleCivilization(sender, args)
            "claim" -> handleClaim(sender, args)
            else -> showHelp(sender)
        }
    }

    override fun suggest(
        source: CommandSourceStack,
        args: Array<out String>,
    ): Collection<String> {
        val choices = when {
            args.size <= 1 -> listOf("status", "season", "civilization", "claim")
            args[0].equals("season", true) && args.size == 2 ->
                listOf("create", "select", "phase")
            args[0].equals("season", true) && args.getOrNull(1).equals("phase", true) ->
                SeasonStatus.entries.map { it.name.lowercase(Locale.ROOT) }
            (args[0].equals("civilization", true) || args[0].equals("civ", true)) &&
                args.size == 2 -> listOf(
                    "list",
                    "draft",
                    "provision",
                    "add-member",
                    "leader",
                    "activate",
                )
            args[0].equals("claim", true) && args.size == 2 -> civilizationReferences()
            args[0].equals("claim", true) && args.size == 3 ->
                source.sender.server.worlds.map { it.key.asString() }
            else -> emptyList()
        }
        val partial = args.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
        return choices.filter { it.lowercase(Locale.ROOT).startsWith(partial) }
    }

    override fun permission(): String = ADMIN_PERMISSION

    private fun handleSeason(
        sender: CommandSender,
        args: Array<out String>,
    ) {
        when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
            "create" -> {
                val name = args.drop(2).joinToString(" ").trim()
                if (name.isEmpty()) {
                    return usage(sender, "/civadmin season create <name>")
                }
                mutate(sender, { seasons.create(name) }) { season ->
                    "Season '${season.name}' (${season.id}) is ${season.status}"
                }
            }
            "select" -> {
                val reference = args.drop(2).joinToString(" ").trim()
                if (reference.isEmpty()) {
                    return usage(sender, "/civadmin season select <uuid-or-exact-name>")
                }
                mutate(
                    sender,
                    operation = {
                        val season = findSeason(reference)
                            ?: return@mutate rejected("Season '$reference' does not exist")
                        seasons.selectActive(season.id)
                    },
                ) { season -> "Selected '${season.name}' (${season.id}) as the active season" }
            }
            "phase" -> {
                val target = args.getOrNull(2)?.let(::parseSeasonStatus)
                    ?: return usage(
                        sender,
                        "/civadmin season phase <setup|peace|war|finale|archived>",
                    )
                mutate(
                    sender,
                    operation = {
                        val seasonId = activeSeasonId()
                            ?: return@mutate rejected("No active season is selected")
                        seasons.transition(seasonId, target)
                    },
                ) { season -> "Season '${season.name}' is now ${season.status}" }
            }
            else -> usage(sender, "/civadmin season <create|select|phase> ...")
        }
    }

    private fun handleCivilization(
        sender: CommandSender,
        args: Array<out String>,
    ) {
        when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
            "list" -> listCivilizations(sender)
            "draft" -> {
                val name = args.drop(2).joinToString(" ").trim()
                if (name.isEmpty()) {
                    return usage(sender, "/civadmin civilization draft <name>")
                }
                mutate(
                    sender,
                    operation = {
                        val seasonId = activeSeasonId()
                            ?: return@mutate rejected("No active season is selected")
                        civilizations.createDraft(seasonId, name)
                    },
                ) { civilization ->
                    "Created landless draft '${civilization.name.value}' (${civilization.id})"
                }
            }
            "provision" -> {
                if (args.size < 4) {
                    return usage(
                        sender,
                        "/civadmin civilization provision <single-token-name> " +
                            "<leader-uuid> [member-uuid ...]",
                    )
                }
                val leaderId = parsePlayerId(sender, args[3]) ?: return
                val memberIds = args.drop(4).map { parsePlayerId(sender, it) ?: return }.toSet()
                mutate(
                    sender,
                    operation = {
                        val seasonId = activeSeasonId()
                            ?: return@mutate rejected("No active season is selected")
                        civilizations.provision(
                            ProvisionCivilization(
                                seasonId = seasonId,
                                rawName = args[2],
                                leaderId = leaderId,
                                memberIds = memberIds,
                            ),
                        )
                    },
                ) { roster ->
                    "Provisioned '${roster.civilization.name.value}' (${roster.civilization.id}) " +
                        "with ${roster.memberships.size} members and no required land"
                }
            }
            "add-member" -> {
                if (args.size != 4) {
                    return usage(
                        sender,
                        "/civadmin civilization add-member <civ-uuid-or-name> <player-uuid>",
                    )
                }
                val playerId = parsePlayerId(sender, args[3]) ?: return
                mutate(
                    sender,
                    operation = {
                        val civilization = findActiveCivilization(args[2])
                            ?: return@mutate rejected("Active civilization '${args[2]}' does not exist")
                        civilizations.assignMember(civilization.id, playerId)
                    },
                ) { membership ->
                    "Assigned ${membership.playerId} to civilization ${membership.civilizationId}"
                }
            }
            "leader" -> {
                if (args.size != 4) {
                    return usage(
                        sender,
                        "/civadmin civilization leader <civ-uuid-or-name> <member-uuid>",
                    )
                }
                val playerId = parsePlayerId(sender, args[3]) ?: return
                mutate(
                    sender,
                    operation = {
                        val civilization = findActiveCivilization(args[2])
                            ?: return@mutate rejected("Active civilization '${args[2]}' does not exist")
                        civilizations.transferLeadership(civilization.id, playerId)
                    },
                ) { roster ->
                    "Transferred leadership of '${roster.civilization.name.value}' to $playerId"
                }
            }
            "activate" -> {
                if (args.size != 3) {
                    return usage(
                        sender,
                        "/civadmin civilization activate <civ-uuid-or-name>",
                    )
                }
                mutate(
                    sender,
                    operation = {
                        val civilization = findActiveCivilization(args[2])
                            ?: return@mutate rejected("Active civilization '${args[2]}' does not exist")
                        civilizations.activate(civilization.id)
                    },
                ) { civilization -> "Activated '${civilization.name.value}' (${civilization.id})" }
            }
            else -> usage(
                sender,
                "/civadmin civilization <list|draft|provision|add-member|leader|activate> ...",
            )
        }
    }

    private fun handleClaim(
        sender: CommandSender,
        args: Array<out String>,
    ) {
        if (args.size != 7) {
            return usage(
                sender,
                "/civadmin claim <civ-uuid-or-name> <world-key> <x1> <z1> <x2> <z2>",
            )
        }
        val coordinates = args.slice(3..6).map { value ->
            value.toIntOrNull() ?: return usage(sender, "Claim coordinates must be integers")
        }
        val worldKey = NamespacedKey.fromString(args[2])
            ?: return usage(sender, "'${args[2]}' is not a valid namespaced world key")
        if (sender.server.getWorld(worldKey) == null) {
            return usage(sender, "World '${worldKey.asString()}' is not loaded")
        }
        val bounds = try {
            ClaimBounds.between(
                worldId = WorldId(worldKey.asString()),
                firstX = coordinates[0],
                firstZ = coordinates[1],
                secondX = coordinates[2],
                secondZ = coordinates[3],
            )
        } catch (failure: IllegalArgumentException) {
            return usage(sender, failure.message ?: "Invalid claim bounds")
        }

        mutate(
            sender,
            operation = {
                val civilization = findActiveCivilization(args[1])
                    ?: return@mutate rejected("Active civilization '${args[1]}' does not exist")
                claims.place(PlaceClaim(civilization.id, bounds))
            },
        ) { claim ->
            "Created claim ${claim.id} for ${claim.civilizationId} " +
                "(${claim.bounds.area} blocks in ${claim.bounds.worldId})"
        }
    }

    private fun showStatus(sender: CommandSender) {
        when (val state = runtime.state) {
            CivilizationsRuntimeState.Stopped -> error(sender, "V2 runtime is stopped")
            CivilizationsRuntimeState.Starting -> info(sender, "V2 runtime is starting")
            is CivilizationsRuntimeState.Failed ->
                error(sender, "V2 runtime failed: ${state.failure.message}")
            is CivilizationsRuntimeState.Ready -> {
                val active = state.activeSeason
                if (active == null) {
                    info(sender, "V2 is ready; no active season is selected")
                } else {
                    val openWars = active.wars.count {
                        it.status == WarStatus.DECLARED || it.status == WarStatus.ACTIVE
                    }
                    val activeBattles = active.battles.count {
                        it.status == BattleStatus.ACTIVE
                    }
                    success(
                        sender,
                        "Active season '${active.season.name}' (${active.season.id}) is " +
                            "${active.season.status}; ${active.civilizations.size} civilizations, " +
                            "${active.claimIndex.size} claims, $openWars open wars, " +
                            "$activeBattles active battles",
                    )
                }
            }
        }
    }

    private fun listCivilizations(sender: CommandSender) {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
        if (active == null) {
            return error(sender, "No active season is loaded")
        }
        if (active.civilizations.isEmpty()) {
            return info(sender, "The active season has no civilizations")
        }
        info(sender, "Civilizations in '${active.season.name}':")
        for (civilization in active.civilizations) {
            val memberCount = active.memberships[civilization.id]?.size ?: 0
            sender.sendMessage(
                Component.text(
                    "- ${civilization.name.value} (${civilization.id}) " +
                        "${civilization.status}, $memberCount members",
                    NamedTextColor.GRAY,
                ),
            )
        }
    }

    private fun showHelp(sender: CommandSender) {
        info(sender, "V2 commands:")
        sender.sendMessage(Component.text("/civadmin status", NamedTextColor.GRAY))
        sender.sendMessage(
            Component.text("/civadmin season <create|select|phase> ...", NamedTextColor.GRAY),
        )
        sender.sendMessage(
            Component.text(
                "/civadmin civilization <list|draft|provision|add-member|leader|activate> ...",
                NamedTextColor.GRAY,
            ),
        )
        sender.sendMessage(
            Component.text(
                "/civadmin claim <civ> <world-key> <x1> <z1> <x2> <z2>",
                NamedTextColor.GRAY,
            ),
        )
    }

    private fun civilizationReferences(): List<String> =
        (runtime.state as? CivilizationsRuntimeState.Ready)
            ?.activeSeason
            ?.civilizations
            ?.flatMap { civilization ->
                buildList {
                    add(civilization.id.toString())
                    if (' ' !in civilization.name.value) {
                        add(civilization.name.value)
                    }
                }
            }
            .orEmpty()

    private fun parsePlayerId(sender: CommandSender, value: String): PlayerId? =
        try {
            PlayerId(UUID.fromString(value))
        } catch (_: IllegalArgumentException) {
            error(sender, "'$value' is not a valid player UUID")
            null
        }

    private fun parseSeasonStatus(value: String): SeasonStatus? =
        runCatching { SeasonStatus.valueOf(value.uppercase(Locale.ROOT)) }.getOrNull()

    private fun <T> mutate(
        sender: CommandSender,
        operation: RuntimeMutationScope.() -> ApplicationResult<T>,
        describe: (T) -> String,
    ) {
        info(sender, "Queued V2 mutation...")
        runtime.submitMutation(operation) { outcome ->
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> success(sender, describe(result.value))
                    is ApplicationResult.Unchanged -> info(
                        sender,
                        "No change: ${describe(result.value)}",
                    )
                    is ApplicationResult.Rejected -> error(sender, result.failure.description)
                }
                is RuntimeMutationOutcome.Failed ->
                    error(sender, "V2 storage failed: ${outcome.failure.message}")
                is RuntimeMutationOutcome.NotReady ->
                    error(sender, "V2 runtime is not ready (${outcome.state.statusName()})")
            }
        }
    }

    private fun rejected(description: String): ApplicationResult.Rejected =
        ApplicationResult.Rejected(CommandFailure(description))

    private fun CivilizationsRuntimeState.statusName(): String =
        this::class.simpleName ?: "unknown"

    private fun usage(sender: CommandSender, message: String) {
        error(sender, message)
    }

    private fun success(sender: CommandSender, message: String) {
        sender.sendMessage(prefixed(message, NamedTextColor.GREEN))
    }

    private fun info(sender: CommandSender, message: String) {
        sender.sendMessage(prefixed(message, NamedTextColor.YELLOW))
    }

    private fun error(sender: CommandSender, message: String) {
        sender.sendMessage(prefixed(message, NamedTextColor.RED))
    }

    private fun prefixed(
        message: String,
        color: NamedTextColor,
    ): Component = Component.text("[Civilizations V2] ", NamedTextColor.DARK_PURPLE)
        .append(Component.text(message, color))

    private data class CommandFailure(
        override val description: String,
    ) : ApplicationFailure

    private companion object {
        const val ADMIN_PERMISSION = "civilizations.admin"
    }
}
