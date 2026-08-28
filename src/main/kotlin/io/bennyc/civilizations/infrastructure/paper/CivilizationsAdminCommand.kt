package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.ProvisionCivilization
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.economy.LedgerTransactionRequest
import io.bennyc.civilizations.application.economy.ReconcileEconomyBridgeTransfer
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.economy.EconomyBridgeTransferId
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.WarId
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
import java.util.logging.Logger

class CivilizationsAdminCommand(
    private val runtime: CivilizationsRuntime,
    private val logger: Logger,
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
            "war" -> handleWar(sender, args)
            "battle" -> handleBattle(sender, args)
            "economy" -> handleEconomy(sender, args)
            else -> showHelp(sender)
        }
    }

    override fun suggest(
        source: CommandSourceStack,
        args: Array<out String>,
    ): Collection<String> {
        val choices = when {
            args.size <= 1 ->
                listOf("status", "season", "civilization", "claim", "war", "battle", "economy")
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
                    "move-member",
                    "leader",
                    "activate",
                )
            args[0].equals("claim", true) && args.size == 2 -> civilizationReferences()
            args[0].equals("claim", true) && args.size == 3 ->
                source.sender.server.worlds.map { it.key.asString() }
            args[0].equals("war", true) && args.size == 2 ->
                listOf("list", "inspect", "activate", "close", "cancel")
            args[0].equals("war", true) && args.size == 3 -> warReferences()
            args[0].equals("battle", true) && args.size == 2 ->
                listOf("list", "inspect", "force-resolve", "cancel")
            args[0].equals("battle", true) && args.size == 3 -> battleReferences()
            args[0].equals("battle", true) &&
                args.getOrNull(1).equals("force-resolve", true) && args.size == 4 ->
                listOf("attacker", "defender", "draw")
            args[0].equals("economy", true) && args.size == 2 ->
                listOf("balances", "ledger", "adjust", "pending", "reconcile")
            args[0].equals("economy", true) &&
                args.getOrNull(1).equals("adjust", true) && args.size == 3 ->
                civilizationReferences()
            args[0].equals("economy", true) &&
                args.getOrNull(1).equals("ledger", true) && args.size == 3 ->
                civilizationReferences()
            args[0].equals("economy", true) &&
                args.getOrNull(1).equals("reconcile", true) && args.size == 4 ->
                listOf("succeeded", "failed")
            else -> emptyList()
        }
        val partial = args.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
        return choices.filter { it.lowercase(Locale.ROOT).startsWith(partial) }
    }

    override fun permission(): String = ADMIN_PERMISSION

    private fun handleEconomy(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
            "balances" -> listEconomyBalances(sender)
            "ledger" -> listEconomyLedger(sender, args)
            "pending" -> listPendingEconomyTransfers(sender)
            "adjust" -> adjustEconomyBalance(sender, args)
            "reconcile" -> reconcileEconomyTransfer(sender, args)
            else -> usage(
                sender,
                "/civadmin economy <balances|ledger|adjust|pending|reconcile> ...",
            )
        }
    }

    private fun listEconomyLedger(sender: CommandSender, args: Array<out String>) {
        if (args.size !in 3..4) {
            return usage(sender, "/civadmin economy ledger <civilization> [limit]")
        }
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return error(sender, "No active season is loaded")
        val civilization = active.findCivilizationForCommand(args[2])
            ?: return error(sender, "Civilization '${args[2]}' does not exist")
        val limit = args.getOrNull(3)?.let { configured ->
            configured.toIntOrNull()
                ?: return error(sender, "Ledger limit must be an integer")
        } ?: 20
        if (limit !in 1..100) {
            return error(sender, "Ledger limit must be between 1 and 100")
        }
        info(sender, "Loading '${civilization.name.value}' treasury ledger…")
        runtime.submitMutation(
            operation = { ApplicationResult.Unchanged(economy.listLedger(civilization.id, limit)) },
        ) { outcome ->
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> {
                    val transactions = when (val result = outcome.result) {
                        is ApplicationResult.Applied -> result.value
                        is ApplicationResult.Unchanged -> result.value
                        is ApplicationResult.Rejected -> return@submitMutation error(
                            sender,
                            result.failure.description,
                        )
                    }
                    if (transactions.isEmpty()) {
                        return@submitMutation info(sender, "That treasury has no ledger entries")
                    }
                    transactions.forEach { transaction ->
                        val posting = transaction.postings.single { posting ->
                            posting.civilizationId == civilization.id
                        }
                        sender.sendMessage(
                            Component.text(
                                "- ${transaction.id}: ${transaction.kind} " +
                                    active.economySettings.currencyScale.format(posting.amount) +
                                    " — ${transaction.description}",
                                NamedTextColor.GRAY,
                            ),
                        )
                    }
                }
                is RuntimeMutationOutcome.NotReady -> error(sender, "Civilizations is not ready")
                is RuntimeMutationOutcome.Failed ->
                    error(sender, "Storage failed: ${outcome.failure.message}")
            }
        }
    }

    private fun listEconomyBalances(sender: CommandSender) {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return error(sender, "No active season is loaded")
        info(sender, "Civilization treasury balances:")
        active.civilizations.forEach { civilization ->
            val balance = active.civilizationAccounts[civilization.id]?.balance ?: return@forEach
            sender.sendMessage(
                Component.text(
                    "- ${civilization.name.value}: " +
                        active.economySettings.currencyScale.format(balance),
                    NamedTextColor.GRAY,
                ),
            )
        }
    }

    private fun listPendingEconomyTransfers(sender: CommandSender) {
        info(sender, "Loading transfers that need reconciliation…")
        runtime.submitMutation(
            operation = { ApplicationResult.Unchanged(economy.listReconciliationRequired()) },
        ) { outcome ->
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> {
                    val transfers = when (val result = outcome.result) {
                        is ApplicationResult.Applied -> result.value
                        is ApplicationResult.Unchanged -> result.value
                        is ApplicationResult.Rejected -> return@submitMutation error(
                            sender,
                            result.failure.description,
                        )
                    }
                    if (transfers.isEmpty()) {
                        info(sender, "No economy transfers need reconciliation")
                    } else {
                        info(sender, "Economy transfers requiring a decision:")
                        transfers.forEach { transfer ->
                            sender.sendMessage(
                                Component.text(
                                    "- ${transfer.id}: ${transfer.direction}, player=${transfer.playerId}, " +
                                        "civ=${transfer.civilizationId}, amount=" +
                                        transfer.currencyScale.format(transfer.amount),
                                    NamedTextColor.GRAY,
                                ),
                            )
                        }
                    }
                }
                is RuntimeMutationOutcome.NotReady -> error(sender, "Civilizations is not ready")
                is RuntimeMutationOutcome.Failed ->
                    error(sender, "Storage failed: ${outcome.failure.message}")
            }
        }
    }

    private fun adjustEconomyBalance(sender: CommandSender, args: Array<out String>) {
        if (args.size < 5) {
            return usage(sender, "/civadmin economy adjust <civilization> <signed-amount> <reason>")
        }
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return error(sender, "No active season is loaded")
        val civilization = active.findCivilizationForCommand(args[2])
            ?: return error(sender, "Civilization '${args[2]}' does not exist")
        val amount = try {
            active.economySettings.currencyScale.parse(args[3])
        } catch (failure: IllegalArgumentException) {
            return error(sender, failure.message ?: "Invalid money amount")
        }
        if (amount.minorUnits == 0L) {
            return error(sender, "Adjustment cannot be zero")
        }
        val reason = args.drop(4).joinToString(" ").trim()
        val actor = (sender as? org.bukkit.entity.Player)?.let { PlayerId(it.uniqueId) }
        mutate(
            sender = sender,
            operation = {
                economy.post(
                    LedgerTransactionRequest(
                        seasonId = active.season.id,
                        idempotencyKey = "admin-adjustment:${UUID.randomUUID()}",
                        kind = LedgerTransactionKind.ADMIN_ADJUSTMENT,
                        postings = listOf(LedgerPosting(civilization.id, amount)),
                        referenceType = "CIVILIZATION",
                        referenceId = civilization.id.toString(),
                        actorPlayerId = actor,
                        description = "Admin adjustment: $reason".take(512),
                    ),
                )
            },
            describe = {
                "Adjusted '${civilization.name.value}' by " +
                    active.economySettings.currencyScale.format(amount)
            },
            afterApplied = {
                audit(sender, "economy.adjust", civilization.id.toString(), reason)
            },
        )
    }

    private fun reconcileEconomyTransfer(sender: CommandSender, args: Array<out String>) {
        if (args.size < 5) {
            return usage(
                sender,
                "/civadmin economy reconcile <transfer-id> <succeeded|failed> <reason>",
            )
        }
        val transferId = try {
            EconomyBridgeTransferId(UUID.fromString(args[2]))
        } catch (_: IllegalArgumentException) {
            return error(sender, "'${args[2]}' is not a valid economy transfer ID")
        }
        val succeeded = when (args[3].lowercase(Locale.ROOT)) {
            "succeeded" -> true
            "failed" -> false
            else -> return usage(sender, "External result must be 'succeeded' or 'failed'")
        }
        val reason = args.drop(4).joinToString(" ").trim()
        val actor = (sender as? org.bukkit.entity.Player)?.let { PlayerId(it.uniqueId) }
        mutate(
            sender = sender,
            operation = {
                economy.reconcileBridgeTransfer(
                    ReconcileEconomyBridgeTransfer(transferId, succeeded, actor, reason),
                )
            },
            describe = { transfer -> "Economy transfer ${transfer.id} is ${transfer.status}" },
            afterApplied = {
                audit(sender, "economy.reconcile", transferId.toString(), reason)
            },
        )
    }

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
                val current = (runtime.state as? CivilizationsRuntimeState.Ready)
                    ?.activeSeason
                    ?.season
                    ?.status
                if ((current == SeasonStatus.WAR || target == SeasonStatus.WAR) &&
                    !sender.hasPermission(WAR_PHASE_PERMISSION)
                ) {
                    return error(
                        sender,
                        "You need $WAR_PHASE_PERMISSION to enter or leave WAR phase",
                    )
                }
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
            "move-member" -> {
                if (args.size != 4) {
                    return usage(
                        sender,
                        "/civadmin civilization move-member <player-uuid> <target-civ>",
                    )
                }
                val playerId = parsePlayerId(sender, args[2]) ?: return
                mutate(
                    sender,
                    operation = {
                        val seasonId = activeSeasonId()
                            ?: return@mutate rejected("No active season is selected")
                        val target = findActiveCivilization(args[3])
                            ?: return@mutate rejected(
                                "Active civilization '${args[3]}' does not exist",
                            )
                        civilizations.moveMember(seasonId, playerId, target.id)
                    },
                ) { membership ->
                    "Moved ${membership.playerId} to civilization ${membership.civilizationId}"
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
                "/civadmin civilization " +
                    "<list|draft|provision|add-member|move-member|leader|activate> ...",
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

    private fun handleWar(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
            "list" -> listWars(sender)
            "inspect" -> {
                val warId = args.getOrNull(2)?.let { parseWarId(sender, it) } ?: return
                inspectWar(sender, warId)
            }
            "activate", "close", "cancel" -> {
                val operationName = args[1].lowercase(Locale.ROOT)
                val warId = args.getOrNull(2)?.let { parseWarId(sender, it) } ?: return
                val reason = args.drop(3).joinToString(" ").trim()
                if (reason.isEmpty()) {
                    return usage(
                        sender,
                        "/civadmin war $operationName <war-id> <audit-reason>",
                    )
                }
                mutate(
                    sender = sender,
                    operation = {
                        when (operationName) {
                            "activate" -> wars.activate(warId)
                            "close" -> wars.closeWar(warId)
                            else -> wars.cancelWar(warId)
                        }
                    },
                    describe = { war -> "War ${war.id} is now ${war.status}" },
                    afterApplied = { war ->
                        audit(sender, "war.$operationName", war.id.toString(), reason)
                    },
                )
            }
            else -> usage(
                sender,
                "/civadmin war <list|inspect|activate|close|cancel> ...",
            )
        }
    }

    private fun handleBattle(sender: CommandSender, args: Array<out String>) {
        when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
            "list" -> listBattles(sender)
            "inspect" -> {
                val battleId = args.getOrNull(2)?.let { parseBattleId(sender, it) } ?: return
                inspectBattle(sender, battleId)
            }
            "force-resolve" -> {
                val battleId = args.getOrNull(2)?.let { parseBattleId(sender, it) } ?: return
                val outcome = args.getOrNull(3)?.let(::parseBattleOutcome)
                    ?: return usage(
                        sender,
                        "/civadmin battle force-resolve <battle-id> " +
                            "<attacker|defender|draw> <audit-reason>",
                    )
                val reason = args.drop(4).joinToString(" ").trim()
                if (reason.isEmpty()) {
                    return usage(sender, "Force-resolution requires an audit reason")
                }
                mutate(
                    sender = sender,
                    operation = { wars.forceResolve(battleId, outcome) },
                    describe = { battle ->
                        "Battle ${battle.id} is ${battle.status} with ${battle.outcome}"
                    },
                    afterApplied = { battle ->
                        audit(
                            sender,
                            "battle.force-resolve",
                            "${battle.id}; outcome=$outcome",
                            reason,
                        )
                    },
                )
            }
            "cancel" -> {
                val battleId = args.getOrNull(2)?.let { parseBattleId(sender, it) } ?: return
                val reason = args.drop(3).joinToString(" ").trim()
                if (reason.isEmpty()) {
                    return usage(
                        sender,
                        "/civadmin battle cancel <battle-id> <audit-reason>",
                    )
                }
                mutate(
                    sender = sender,
                    operation = { wars.cancelBattle(battleId) },
                    describe = { battle -> "Battle ${battle.id} is now ${battle.status}" },
                    afterApplied = { battle ->
                        audit(sender, "battle.cancel", battle.id.toString(), reason)
                    },
                )
            }
            else -> usage(
                sender,
                "/civadmin battle <list|inspect|force-resolve|cancel> ...",
            )
        }
    }

    private fun listWars(sender: CommandSender) {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return error(sender, "No active season is loaded")
        if (active.wars.isEmpty()) {
            return info(sender, "The active season has no wars")
        }
        info(sender, "Wars in '${active.season.name}':")
        active.wars.forEach { war ->
            sender.sendMessage(
                Component.text(
                    "- ${war.id}: ${war.declaringCivilizationId} -> " +
                        "${war.targetCivilizationId}, ${war.status}",
                    NamedTextColor.GRAY,
                ),
            )
        }
    }

    private fun inspectWar(sender: CommandSender, warId: WarId) {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return error(sender, "No active season is loaded")
        val war = active.wars.singleOrNull { it.id == warId }
            ?: return error(sender, "War $warId does not exist in the active season")
        info(
            sender,
            "War ${war.id}: ${war.declaringCivilizationId} -> ${war.targetCivilizationId}; " +
                "status=${war.status}; declaredBy=${war.declaredByPlayerId}; " +
                "declaredAt=${war.declaredAt}; activatedAt=${war.activatedAt}; " +
                "endedAt=${war.endedAt}; battleDuration=${war.rules.battleDurationSeconds}s",
        )
    }

    private fun listBattles(sender: CommandSender) {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return error(sender, "No active season is loaded")
        if (active.battles.isEmpty()) {
            return info(sender, "The active season has no battles")
        }
        info(sender, "Battles in '${active.season.name}':")
        active.battles.forEach { battle ->
            sender.sendMessage(
                Component.text(
                    "- ${battle.id}: ${battle.attackingCivilizationId} vs " +
                        "${battle.defendingCivilizationId}, ${battle.status}",
                    NamedTextColor.GRAY,
                ),
            )
        }
    }

    private fun inspectBattle(sender: CommandSender, battleId: BattleId) {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return error(sender, "No active season is loaded")
        val battle = active.battles.singleOrNull { it.id == battleId }
            ?: return error(sender, "Battle $battleId does not exist in the active season")
        val participants = active.battleParticipants[battle.id].orEmpty()
        val surrender = active.battleSurrenders[battle.id]
        info(
            sender,
            "Battle ${battle.id}: war=${battle.warId}; " +
                "attacker=${battle.attackingCivilizationId}; " +
                "defender=${battle.defendingCivilizationId}; status=${battle.status}; " +
                "participants=${participants.size}; startedAt=${battle.startedAt}; " +
                "endsAt=${battle.endsAt}; resolvingAt=${battle.resolvingAt}; " +
                "endedAt=${battle.endedAt}; outcome=${battle.outcome}; " +
                if (surrender == null) {
                    "surrender=none"
                } else {
                    "surrender=${surrender.surrenderedCivilizationId} by " +
                        "${surrender.surrenderedByPlayerId} at ${surrender.surrenderedAt}; " +
                        "requestedOutcome=${surrender.requestedOutcome}"
                },
        )
    }

    private fun showStatus(sender: CommandSender) {
        when (val state = runtime.state) {
            CivilizationsRuntimeState.Stopped -> error(sender, "Runtime is stopped")
            CivilizationsRuntimeState.Starting -> info(sender, "Runtime is starting")
            is CivilizationsRuntimeState.Failed ->
                error(sender, "Runtime failed: ${state.failure.message}")
            is CivilizationsRuntimeState.Ready -> {
                val active = state.activeSeason
                if (active == null) {
                    info(sender, "Civilizations is ready; no active season is selected")
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
        info(sender, "Civilizations commands:")
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
        sender.sendMessage(
            Component.text(
                "/civadmin war <list|inspect|activate|close|cancel> ...",
                NamedTextColor.GRAY,
            ),
        )
        sender.sendMessage(
            Component.text(
                "/civadmin battle <list|inspect|force-resolve|cancel> ...",
                NamedTextColor.GRAY,
            ),
        )
        sender.sendMessage(
            Component.text(
                "/civadmin economy <balances|ledger|adjust|pending|reconcile> ...",
                NamedTextColor.GRAY,
            ),
        )
    }

    private fun io.bennyc.civilizations.infrastructure.runtime.ActiveSeasonRuntimeState
        .findCivilizationForCommand(reference: String) =
        runCatching { io.bennyc.civilizations.domain.identity.CivilizationId(UUID.fromString(reference)) }
            .getOrNull()
            ?.let { id -> civilizations.singleOrNull { it.id == id } }
            ?: civilizations.singleOrNull { it.name.value.equals(reference, ignoreCase = true) }

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

    private fun warReferences(): List<String> =
        (runtime.state as? CivilizationsRuntimeState.Ready)
            ?.activeSeason
            ?.wars
            ?.map { it.id.toString() }
            .orEmpty()

    private fun battleReferences(): List<String> =
        (runtime.state as? CivilizationsRuntimeState.Ready)
            ?.activeSeason
            ?.battles
            ?.map { it.id.toString() }
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

    private fun parseBattleOutcome(value: String): BattleOutcome? = when (
        value.lowercase(Locale.ROOT)
    ) {
        "attacker" -> BattleOutcome.ATTACKER_VICTORY
        "defender" -> BattleOutcome.DEFENDER_VICTORY
        "draw" -> BattleOutcome.DRAW
        else -> null
    }

    private fun parseWarId(sender: CommandSender, value: String): WarId? =
        try {
            WarId(UUID.fromString(value))
        } catch (_: IllegalArgumentException) {
            error(sender, "'$value' is not a valid war UUID")
            null
        }

    private fun parseBattleId(sender: CommandSender, value: String): BattleId? =
        try {
            BattleId(UUID.fromString(value))
        } catch (_: IllegalArgumentException) {
            error(sender, "'$value' is not a valid battle UUID")
            null
        }

    private fun <T> mutate(
        sender: CommandSender,
        operation: RuntimeMutationScope.() -> ApplicationResult<T>,
        afterApplied: (T) -> Unit = {},
        describe: (T) -> String,
    ) {
        info(sender, "Queued mutation...")
        runtime.submitMutation(operation) { outcome ->
            when (outcome) {
                is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                    is ApplicationResult.Applied -> {
                        success(sender, describe(result.value))
                        afterApplied(result.value)
                    }
                    is ApplicationResult.Unchanged -> info(
                        sender,
                        "No change: ${describe(result.value)}",
                    )
                    is ApplicationResult.Rejected -> error(sender, result.failure.description)
                }
                is RuntimeMutationOutcome.Failed ->
                    error(sender, "Storage failed: ${outcome.failure.message}")
                is RuntimeMutationOutcome.NotReady ->
                    error(sender, "Runtime is not ready (${outcome.state.statusName()})")
            }
        }
    }

    private fun audit(
        sender: CommandSender,
        action: String,
        target: String,
        reason: String,
    ) {
        logger.info(
            "Civilizations admin audit: actor=${sender.name}; action=$action; " +
                "target=$target; reason=$reason",
        )
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
    ): Component = Component.text("[Civilizations] ", NamedTextColor.DARK_PURPLE)
        .append(Component.text(message, color))

    private data class CommandFailure(
        override val description: String,
    ) : ApplicationFailure

    private companion object {
        const val ADMIN_PERMISSION = "civilizations.admin"
        const val WAR_PHASE_PERMISSION = "civilizations.admin.phase.war"
    }
}
