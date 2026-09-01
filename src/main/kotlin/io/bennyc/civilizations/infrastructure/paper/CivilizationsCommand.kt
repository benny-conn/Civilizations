package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.repair.RepairTargetAlreadyReached
import io.bennyc.civilizations.application.repair.RepairTargetUnreachable
import io.bennyc.civilizations.application.claim.PlaceClaim
import io.bennyc.civilizations.application.war.DeclareWar
import io.bennyc.civilizations.application.war.BattleSurrender
import io.bennyc.civilizations.application.war.SurrenderBattle
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.economy.EconomyBridgeDirection
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.repair.RepairJob
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.WarRulesSnapshot
import io.bennyc.civilizations.domain.war.WarStatus
import io.bennyc.civilizations.infrastructure.runtime.ActiveSeasonRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import io.bennyc.civilizations.infrastructure.paper.economy.PaperEconomyBridgeCoordinator
import io.bennyc.civilizations.infrastructure.paper.economy.PaperEconomyTransferOutcome
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairCoordinator
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairMenu
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairOutcome
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairStatus
import io.bennyc.civilizations.infrastructure.paper.protection.LandProtectionPaperOutcome
import io.bennyc.civilizations.infrastructure.paper.protection.PaperLandProtectionCoordinator
import io.bennyc.civilizations.domain.protection.ProtectionRepairJobId
import io.bennyc.civilizations.infrastructure.paper.war.PaperBattleResolutionCoordinator
import io.bennyc.civilizations.infrastructure.paper.war.PaperBattleResolutionOutcome
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Locale
import java.util.UUID
import java.util.logging.Logger
import java.math.BigDecimal
import java.math.RoundingMode

/** Player-facing declared-war operations; civilization authority stays application-owned. */
class CivilizationsCommand(
    private val runtime: CivilizationsRuntime,
    private val rules: WarRulesSnapshot,
    private val server: Server,
    private val logger: Logger,
    private val economyBridge: PaperEconomyBridgeCoordinator,
    private val repairCoordinator: PaperRepairCoordinator,
    private val repairMenu: PaperRepairMenu,
    private val battleResolutionCoordinator: PaperBattleResolutionCoordinator,
    private val landProtectionCoordinator: PaperLandProtectionCoordinator,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        val sender = source.sender
        when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
            "status" -> showStatus(sender)
            "declare" -> declare(sender, args)
            "surrender" -> surrender(sender)
            "balance" -> showBalance(sender)
            "deposit" -> transferMoney(sender, args, EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION)
            "withdraw" -> transferMoney(sender, args, EconomyBridgeDirection.WITHDRAW_TO_PLAYER)
            "claim" -> claim(sender, args)
            "protection" -> handleLandProtection(sender, args)
            "repair" -> handleRepair(sender, args)
            else -> showHelp(sender)
        }
    }

    override fun suggest(
        source: CommandSourceStack,
        args: Array<out String>,
    ): Collection<String> {
        val choices = when {
            args.size <= 1 ->
                listOf(
                    "status", "declare", "surrender", "balance", "deposit", "withdraw",
                    "claim", "protection", "repair",
                )
            args.firstOrNull().equals("declare", true) && args.size == 2 ->
                targetCivilizationReferences(source.sender)
            args.firstOrNull().equals("repair", true) && args.size == 2 ->
                listOf("menu", "status", "start")
            args.firstOrNull().equals("protection", true) && args.size == 2 ->
                listOf("status", "pay", "repair", "resume")
            args.firstOrNull().equals("repair", true) &&
                args.getOrNull(1).equals("menu", true) && args.size == 3 ->
                battleReferences(source.sender)
            args.firstOrNull().equals("repair", true) &&
                args.getOrNull(1)?.lowercase(Locale.ROOT) in setOf("status", "start") &&
                args.size == 3 -> battleReferences(source.sender)
            else -> emptyList()
        }
        val partial = args.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
        return choices.filter { it.lowercase(Locale.ROOT).startsWith(partial) }
    }

    override fun permission(): String = USE_PERMISSION

    private fun handleLandProtection(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
            ?: return error(sender, "Only a player has civilization land protection")
        if (!player.hasPermission(LAND_PROTECTION_PERMISSION)) {
            return error(sender, "You do not have permission to manage land protection")
        }
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return error(sender, "You are not in a civilization this season")
        when (args.getOrNull(1)?.lowercase(Locale.ROOT) ?: "status") {
            "status" -> {
                if (args.size > 2) return error(sender, "Usage: /civ protection [status]")
                val state = active.landProtectionStates[membership.civilizationId]
                    ?: return error(sender, "Land protection state is unavailable")
                val scale = active.economySettings.currencyScale
                info(
                    sender,
                    "Land protection is ${state.status}; required treasury reserve " +
                        "${scale.format(state.requiredReserve)}, next assessment " +
                        "${state.nextAssessmentAt ?: "not scheduled"}.",
                )
                if (state.delinquentAmount.minorUnits > 0) {
                    info(
                        sender,
                        "Unpaid upkeep is ${scale.format(state.delinquentAmount)}; " +
                            "grace ends ${state.graceEndsAt}.",
                    )
                }
                state.exposureDamageLimit?.let { limit ->
                    info(
                        sender,
                        "This exposure has journaled ${state.exposureDamageCount}/$limit " +
                            "distinct damaged blocks.",
                    )
                }
                info(sender, "Scanning current journaled damage in bounded batches…")
                landProtectionCoordinator.status(membership.civilizationId) { outcome ->
                    when (outcome) {
                        is LandProtectionPaperOutcome.Completed -> {
                            val assessment = outcome.value
                            info(
                                sender,
                                "Restoration is ${formatPercentage(assessment.completionBasisPoints)}: " +
                                "${assessment.restoredCount}/${assessment.totalDamageCount} already " +
                                    "restored, ${assessment.repairable.size} repairable, " +
                                    "${assessment.conflictCount} changed again. Restoring every " +
                                    "currently repairable block costs " +
                                    active.economySettings.currencyScale.format(
                                        assessment.repairableCost,
                                    ) + ".",
                            )
                        }
                        is LandProtectionPaperOutcome.Rejected -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Unavailable -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Failed -> error(
                            sender,
                            "Protection scan failed: ${outcome.failure.message}",
                        )
                    }
                }
            }
            "pay" -> {
                if (args.size != 2) return error(sender, "Usage: /civ protection pay")
                info(sender, "Checking upkeep and treasury reserve…")
                landProtectionCoordinator.assessNow(membership.civilizationId) { outcome ->
                    when (outcome) {
                        is LandProtectionPaperOutcome.Completed -> success(
                            sender,
                            "Land protection is now ${outcome.value.status}.",
                        )
                        is LandProtectionPaperOutcome.Rejected -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Unavailable -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Failed -> error(
                            sender,
                            "Upkeep payment failed: ${outcome.failure.message}",
                        )
                    }
                }
            }
            "repair" -> {
                if (args.size != 3) {
                    return error(sender, "Usage: /civ protection repair <target-percent>")
                }
                val target = parsePercentage(sender, args[2]) ?: return
                info(sender, "Scanning current damage before pricing restoration…")
                landProtectionCoordinator.start(
                    civilizationId = membership.civilizationId,
                    playerId = PlayerId(player.uniqueId),
                    targetBasisPoints = target,
                ) { outcome ->
                    when (outcome) {
                        is LandProtectionPaperOutcome.Completed -> success(
                            sender,
                            "Protection repair ${outcome.value.id} started: " +
                                "${outcome.value.selectedCount} blocks for " +
                                active.economySettings.currencyScale.format(outcome.value.grossCost) +
                                "; target ${formatPercentage(target)}.",
                        )
                        is LandProtectionPaperOutcome.Rejected -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Unavailable -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Failed -> error(
                            sender,
                            "Protection repair failed: ${outcome.failure.message}",
                        )
                    }
                }
            }
            "resume" -> {
                if (args.size != 3) {
                    return error(sender, "Usage: /civ protection resume <job-id>")
                }
                val jobId = try {
                    ProtectionRepairJobId(UUID.fromString(args[2]))
                } catch (_: IllegalArgumentException) {
                    return error(sender, "'${args[2]}' is not a valid protection repair UUID")
                }
                landProtectionCoordinator.resume(jobId, membership.civilizationId) { outcome ->
                    when (outcome) {
                        is LandProtectionPaperOutcome.Completed -> success(
                            sender,
                            "Protection repair ${outcome.value.id} queued to resume.",
                        )
                        is LandProtectionPaperOutcome.Rejected -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Unavailable -> error(sender, outcome.description)
                        is LandProtectionPaperOutcome.Failed -> error(
                            sender,
                            "Protection repair resume failed: ${outcome.failure.message}",
                        )
                    }
                }
            }
            else -> error(sender, "Usage: /civ protection <status|pay|repair|resume> …")
        }
    }

    private fun claim(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return error(sender, "Only a player can claim land")
        if (!player.hasPermission(CLAIM_PERMISSION)) {
            return error(sender, "You do not have permission to claim land")
        }
        if (args.size != 5) {
            return error(sender, "Usage: /civ claim <x1> <z1> <x2> <z2>")
        }
        val coordinates = args.drop(1).map { value ->
            value.toIntOrNull() ?: return error(sender, "Claim coordinates must be integers")
        }
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return error(sender, "You are not in a civilization this season")
        val bounds = try {
            ClaimBounds.between(
                WorldId(player.world.key.asString()),
                coordinates[0],
                coordinates[1],
                coordinates[2],
                coordinates[3],
            )
        } catch (failure: IllegalArgumentException) {
            return error(sender, failure.message ?: "Invalid claim bounds")
        }
        info(sender, "Validating and purchasing the claim…")
        runtime.submitMutation(
            operation = {
                claims.place(
                    PlaceClaim(
                        civilizationId = membership.civilizationId,
                        bounds = bounds,
                        actorPlayerId = PlayerId(player.uniqueId),
                        adminSponsored = false,
                        idempotencyKey = "player-claim:${UUID.randomUUID()}",
                    ),
                )
            },
            completion = { outcome ->
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> success(
                            sender,
                            "Claim ${result.value.id} purchased: ${result.value.bounds.area} " +
                                "blocks in group ${result.value.groupId}.",
                        )
                        is ApplicationResult.Unchanged -> info(sender, "Claim already exists")
                        is ApplicationResult.Rejected -> error(sender, result.failure.description)
                    }
                    is RuntimeMutationOutcome.NotReady -> error(sender, "Civilizations is not ready")
                    is RuntimeMutationOutcome.Failed ->
                        error(sender, "Claim purchase failed: ${outcome.failure.message}")
                }
            },
        )
    }

    private fun handleRepair(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return error(sender, "Only a player can repair")
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return error(sender, "You are not in a civilization this season")
        val action = args.getOrNull(1)?.lowercase(Locale.ROOT)
        if (action == null) {
            if (!player.hasPermission(REPAIR_STATUS_PERMISSION)) {
                return error(sender, "You do not have permission to view repair status")
            }
            repairMenu.open(player)
            return
        }
        when (action) {
            "menu" -> {
                if (!player.hasPermission(REPAIR_STATUS_PERMISSION)) {
                    return error(sender, "You do not have permission to view repair status")
                }
                if (args.size !in 2..3) {
                    return error(sender, "Usage: /civ repair menu [battle-id]")
                }
                val battleId = args.getOrNull(2)?.let { parseBattleId(sender, it) }
                if (args.size == 3 && battleId == null) return
                repairMenu.open(player, battleId)
            }
            "status" -> {
                if (!player.hasPermission(REPAIR_STATUS_PERMISSION)) {
                    return error(sender, "You do not have permission to view repair status")
                }
                if (args.size != 3) {
                    return error(sender, "Usage: /civ repair status <battle-id>")
                }
                val battleId = parseBattleId(sender, args[2]) ?: return
                info(sender, "Scanning battle damage in bounded batches…")
                repairCoordinator.status(battleId, membership.civilizationId) { outcome ->
                    when (outcome) {
                        is PaperRepairOutcome.Completed -> showRepairStatus(
                            sender,
                            active,
                            outcome.value,
                        )
                        is PaperRepairOutcome.Rejected -> error(sender, outcome.description)
                        is PaperRepairOutcome.Unavailable -> error(sender, outcome.description)
                        is PaperRepairOutcome.Failed ->
                            error(sender, "Repair scan failed: ${outcome.failure.message}")
                    }
                }
            }
            "start" -> {
                if (!player.hasPermission(REPAIR_START_PERMISSION)) {
                    return error(sender, "You do not have permission to start repairs")
                }
                if (args.size != 4) {
                    return error(sender, "Usage: /civ repair start <battle-id> <target-percent>")
                }
                val battleId = parseBattleId(sender, args[2]) ?: return
                val target = parsePercentage(sender, args[3]) ?: return
                info(sender, "Scanning current damage before creating the repair…")
                repairCoordinator.startOrdinary(
                    battleId = battleId,
                    civilizationId = membership.civilizationId,
                    playerId = PlayerId(player.uniqueId),
                    targetCompletionBasisPoints = target,
                ) { outcome ->
                    when (outcome) {
                        is PaperRepairOutcome.Completed -> {
                            val created = outcome.value
                            success(
                                sender,
                                "Repair ${created.job.id} started: ${created.job.selectedCount} " +
                                    "blocks for " +
                                    active.economySettings.currencyScale.format(
                                created.job.grossCost,
                                    ) + "; target ${formatPercentage(target)}",
                            )
                            info(
                                sender,
                                "The runner restores up to " +
                                    "${repairCoordinator.configuredBlocksPerTick} blocks/tick " +
                                    "(${repairCoordinator.configuredBlocksPerSecond}/second).",
                            )
                        }
                        is PaperRepairOutcome.Rejected -> error(sender, outcome.description)
                        is PaperRepairOutcome.Unavailable -> error(sender, outcome.description)
                        is PaperRepairOutcome.Failed ->
                            error(sender, "Repair creation failed: ${outcome.failure.message}")
                    }
                }
            }
            else -> {
                if (!player.hasPermission(REPAIR_STATUS_PERMISSION)) {
                    return error(sender, "You do not have permission to view repair status")
                }
                if (args.size != 2) {
                    return error(
                        sender,
                        "Usage: /civ repair [battle-id] or /civ repair <status|start> …",
                    )
                }
                val battleId = parseBattleId(sender, args[1]) ?: return
                repairMenu.open(player, battleId)
            }
        }
    }

    private fun showRepairStatus(
        sender: CommandSender,
        active: ActiveSeasonRuntimeState,
        status: PaperRepairStatus,
    ) {
        val assessment = status.assessment
        info(
            sender,
            "Repair completion is ${formatPercentage(assessment.completionBasisPoints)}: " +
                "${assessment.restoredCount}/${assessment.totalEligibleCount} restored, " +
                "${assessment.repairableCount} still repairable, " +
                "${assessment.conflictCount} changed by players.",
        )
        when (val quote = status.quote) {
            is ApplicationResult.Applied -> info(
                sender,
                "To reach 100%: ${quote.value.selectedCount} blocks cost " +
                    active.economySettings.currencyScale.format(quote.value.grossCost) +
                    "; victor receives " +
                    active.economySettings.currencyScale.format(quote.value.victorProceeds) + ".",
            )
            is ApplicationResult.Unchanged -> Unit
            is ApplicationResult.Rejected -> when (val failure = quote.failure) {
                is RepairTargetAlreadyReached ->
                    success(sender, "This battle's eligible damage is already 100% restored.")
                is RepairTargetUnreachable -> info(
                    sender,
                    "100% cannot currently be reached: ${failure.repairableCount} blocks " +
                        "remain repairable and ${failure.conflictCount} have later player changes.",
                )
                else -> info(sender, failure.description)
            }
        }
        status.jobs.firstOrNull()?.let { job ->
            info(sender, jobSummary(job))
        }
    }

    private fun jobSummary(job: RepairJob): String =
        "Latest job ${job.id}: ${job.status}, cursor ${job.nextItemOrdinal}/${job.selectedCount}, " +
            "restored=${job.restoredCount}, conflicts=${job.skippedConflictCount}, " +
            "failed=${job.failedCount}"

    private fun showBalance(sender: CommandSender) {
        val player = sender as? Player ?: return error(sender, "Only a player has a treasury balance")
        if (!player.hasPermission(BALANCE_PERMISSION)) {
            return error(sender, "You do not have permission to view the treasury")
        }
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return error(sender, "You are not in a civilization this season")
        val account = active.civilizationAccounts[membership.civilizationId]
            ?: return error(sender, "Your civilization treasury is unavailable")
        info(
            sender,
            "Civilization treasury: ${active.economySettings.currencyScale.format(account.balance)}",
        )
    }

    private fun transferMoney(
        sender: CommandSender,
        args: Array<out String>,
        direction: EconomyBridgeDirection,
    ) {
        val player = sender as? Player ?: return error(sender, "Only a player can transfer money")
        val permission = when (direction) {
            EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION -> DEPOSIT_PERMISSION
            EconomyBridgeDirection.WITHDRAW_TO_PLAYER -> WITHDRAW_PERMISSION
        }
        if (!player.hasPermission(permission)) {
            return error(sender, "You do not have permission for that treasury operation")
        }
        if (args.size != 2) {
            val name = direction.name.lowercase(Locale.ROOT).substringBefore('_')
            return error(sender, "Usage: /civ $name <amount>")
        }
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return error(sender, "You are not in a civilization this season")
        val amount = try {
            active.economySettings.currencyScale.parse(args[1])
        } catch (failure: IllegalArgumentException) {
            return error(sender, failure.message ?: "Invalid money amount")
        }
        if (amount.minorUnits <= 0) {
            return error(sender, "Amount must be greater than zero")
        }
        info(sender, "Preparing treasury transfer…")
        economyBridge.transfer(
            player = player,
            seasonId = active.season.id,
            civilizationId = membership.civilizationId,
            direction = direction,
            amount = amount,
        ) { outcome ->
            when (outcome) {
                is PaperEconomyTransferOutcome.Completed -> success(
                    sender,
                    when (direction) {
                        EconomyBridgeDirection.DEPOSIT_TO_CIVILIZATION ->
                            "Deposited ${active.economySettings.currencyScale.format(amount)} into the civilization treasury"
                        EconomyBridgeDirection.WITHDRAW_TO_PLAYER ->
                            "Withdrew ${active.economySettings.currencyScale.format(amount)} to your player wallet"
                    },
                )
                is PaperEconomyTransferOutcome.Rejected -> error(sender, outcome.description)
                is PaperEconomyTransferOutcome.ReconciliationRequired -> error(
                    sender,
                    "Transfer ${outcome.transferId} needs admin reconciliation; it will not be retried automatically",
                )
                is PaperEconomyTransferOutcome.Failed -> error(
                    sender,
                    "Treasury transfer failed: ${outcome.failure.message}",
                )
            }
        }
    }

    private fun declare(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return error(sender, "Only a player can declare war")
        if (!player.hasPermission(DECLARE_PERMISSION)) {
            return error(sender, "You do not have permission to declare war")
        }
        val targetReference = args.drop(1).joinToString(" ").trim()
        if (targetReference.isEmpty()) {
            return error(sender, "Usage: /civ declare <civilization>")
        }
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return error(sender, "You are not in a civilization this season")
        val target = active.findCivilization(targetReference)
            ?: return error(sender, "Civilization '$targetReference' does not exist")

        info(sender, "Declaring war…")
        runtime.submitMutation(
            operation = {
                wars.declare(
                    DeclareWar(
                        seasonId = active.season.id,
                        declaringCivilizationId = membership.civilizationId,
                        targetCivilizationId = target.id,
                        declaredByPlayerId = PlayerId(player.uniqueId),
                        battleDurationSeconds = rules.battleDurationSeconds,
                    ),
                )
            },
            completion = { outcome ->
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> {
                            success(
                                sender,
                                "War ${result.value.id} declared against ${target.name.value}",
                            )
                            announceDeclaration(
                                result.value.declaringCivilizationId,
                                target.id,
                                outcome.state,
                            )
                            logger.info(
                                "War ${result.value.id} declared by ${player.uniqueId}: " +
                                    "${result.value.declaringCivilizationId} -> ${target.id}",
                            )
                        }
                        is ApplicationResult.Unchanged ->
                            info(sender, "War ${result.value.id} is already declared")
                        is ApplicationResult.Rejected -> error(sender, result.failure.description)
                    }
                    is RuntimeMutationOutcome.NotReady ->
                        error(sender, "Civilizations is not ready")
                    is RuntimeMutationOutcome.Failed ->
                        error(sender, "War declaration failed: ${outcome.failure.message}")
                }
            },
        )
    }

    private fun surrender(sender: CommandSender) {
        val player = sender as? Player ?: return error(sender, "Only a player can surrender")
        if (!player.hasPermission(SURRENDER_PERMISSION)) {
            return error(sender, "You do not have permission to surrender")
        }
        val active = activeSeason(sender) ?: return
        info(sender, "Submitting surrender…")
        runtime.submitMutation(
            operation = {
                wars.surrender(
                    SurrenderBattle(
                        seasonId = active.season.id,
                        surrenderedByPlayerId = PlayerId(player.uniqueId),
                    ),
                )
            },
            completion = { outcome ->
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> continueSurrenderResolution(
                            sender,
                            player,
                            result.value,
                            outcome.state,
                        )
                        is ApplicationResult.Unchanged -> continueSurrenderResolution(
                            sender,
                            player,
                            result.value,
                            outcome.state,
                        )
                        is ApplicationResult.Rejected -> error(sender, result.failure.description)
                    }
                    is RuntimeMutationOutcome.NotReady ->
                        error(sender, "Civilizations is not ready")
                    is RuntimeMutationOutcome.Failed ->
                        error(sender, "Surrender failed: ${outcome.failure.message}")
                }
            },
        )
    }

    private fun continueSurrenderResolution(
        sender: CommandSender,
        player: Player,
        surrender: BattleSurrender,
        state: CivilizationsRuntimeState.Ready,
    ) {
        val message = prefixed(
            "Civilization ${surrender.surrenderedCivilizationId} surrendered " +
                "battle ${surrender.battle.id}; destruction is closed and " +
                "the damage report is being sealed.",
            NamedTextColor.RED,
        )
        val recipients = state.activeSeason
            ?.battleParticipants
            ?.get(surrender.battle.id)
            .orEmpty()
            .mapTo(linkedSetOf()) { it.playerId.value }
            .apply { add(player.uniqueId) }
        recipients.forEach { playerId ->
            server.getPlayer(playerId)?.sendMessage(message)
        }
        logger.info(
            "Battle ${surrender.battle.id} surrendered by ${player.uniqueId}; " +
                "civilization=${surrender.surrenderedCivilizationId}; " +
                "requestedOutcome=${surrender.requestedOutcome}",
        )
        battleResolutionCoordinator.continueSurrender(
            surrender.battle.id,
            surrender.requestedOutcome,
        ) { resolution ->
            when (resolution) {
                is PaperBattleResolutionOutcome.Completed -> Unit
                is PaperBattleResolutionOutcome.Rejected ->
                    error(sender, resolution.description)
                is PaperBattleResolutionOutcome.Unavailable -> error(
                    sender,
                    "Battle remains resolving: ${resolution.description}",
                )
                is PaperBattleResolutionOutcome.Failed -> error(
                    sender,
                    "Battle resolution failed: ${resolution.failure.message}",
                )
            }
        }
    }

    private fun showStatus(sender: CommandSender) {
        val player = sender as? Player ?: return error(sender, "Only a player has war status")
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return info(sender, "You are not in a civilization this season")
        val wars = active.wars.filter { war ->
            membership.civilizationId in war.civilizationIds &&
                (war.status == WarStatus.DECLARED || war.status == WarStatus.ACTIVE)
        }
        val battle = active.battles.singleOrNull { candidate ->
            candidate.status in setOf(BattleStatus.ACTIVE, BattleStatus.RESOLVING) &&
                membership.civilizationId in setOf(
                    candidate.attackingCivilizationId,
                    candidate.defendingCivilizationId,
                )
        }
        if (wars.isEmpty()) {
            info(sender, "Your civilization has no open wars")
        } else {
            info(sender, "Open wars: ${wars.joinToString { "${it.id} (${it.status})" }}")
        }
        if (battle != null) {
            info(
                sender,
                "Battle ${battle.id} is ${battle.status}; ends ${battle.endsAt}",
            )
        }
    }

    private fun announceDeclaration(
        declaringId: CivilizationId,
        targetId: CivilizationId,
        ready: CivilizationsRuntimeState.Ready,
    ) {
        val active = ready.activeSeason ?: return
        val declaring = active.civilizationName(declaringId)
        val target = active.civilizationName(targetId)
        val message = prefixed(
            "$declaring declared war on $target. Battles can begin by hostile entry during WAR.",
            NamedTextColor.RED,
        )
        for (civilizationId in setOf(declaringId, targetId)) {
            for (membership in active.memberships[civilizationId].orEmpty()) {
                server.getPlayer(membership.playerId.value)?.sendMessage(message)
            }
        }
    }

    private fun activeSeason(sender: CommandSender): ActiveSeasonRuntimeState? {
        val state = runtime.state as? CivilizationsRuntimeState.Ready
        val active = state?.activeSeason
        if (active == null) {
            error(sender, "No active season is ready")
        }
        return active
    }

    private fun targetCivilizationReferences(sender: CommandSender): List<String> {
        val player = sender as? Player ?: return emptyList()
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return emptyList()
        val ownId = active.membershipOf(PlayerId(player.uniqueId))?.civilizationId
        return active.civilizations
            .filterNot { it.id == ownId }
            .flatMap { civilization ->
                buildList {
                    add(civilization.id.toString())
                    if (' ' !in civilization.name.value) {
                        add(civilization.name.value)
                    }
                }
            }
    }

    private fun battleReferences(sender: CommandSender): List<String> {
        val player = sender as? Player ?: return emptyList()
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
            ?: return emptyList()
        val civilizationId = active.membershipOf(PlayerId(player.uniqueId))?.civilizationId
            ?: return emptyList()
        return active.battles
            .filter { battle ->
                battle.status == BattleStatus.CLOSED &&
                    civilizationId in setOf(
                        battle.attackingCivilizationId,
                        battle.defendingCivilizationId,
                    )
            }
            .map { it.id.toString() }
    }

    private fun parseBattleId(sender: CommandSender, value: String): BattleId? = try {
        BattleId(UUID.fromString(value))
    } catch (_: IllegalArgumentException) {
        error(sender, "'$value' is not a valid battle UUID")
        null
    }

    private fun parsePercentage(sender: CommandSender, value: String): Int? = try {
        val percentage = BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY)
        if (percentage <= BigDecimal.ZERO || percentage > BigDecimal(100)) {
            error(sender, "Target percent must be greater than 0 and at most 100")
            null
        } else {
            percentage.movePointRight(2).intValueExact()
        }
    } catch (_: NumberFormatException) {
        error(sender, "Target percent must be a number")
        null
    } catch (_: ArithmeticException) {
        error(sender, "Target percent may have at most two decimal places")
        null
    }

    private fun formatPercentage(basisPoints: Int): String =
        BigDecimal.valueOf(basisPoints.toLong(), 2).stripTrailingZeros().toPlainString() + "%"

    private fun ActiveSeasonRuntimeState.findCivilization(reference: String): Civilization? {
        val id = runCatching { CivilizationId(UUID.fromString(reference)) }.getOrNull()
        return if (id != null) {
            civilizations.singleOrNull { it.id == id }
        } else {
            civilizations.singleOrNull { it.name.value.equals(reference, ignoreCase = true) }
        }
    }

    private fun ActiveSeasonRuntimeState.civilizationName(id: CivilizationId): String =
        civilizations.singleOrNull { it.id == id }?.name?.value ?: id.toString()

    private fun showHelp(sender: CommandSender) {
        info(sender, "Civilizations commands:")
        sender.sendMessage(Component.text("/civ balance", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ deposit <amount>", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ withdraw <amount> (leader)", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ claim <x1> <z1> <x2> <z2> (leader)", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ protection [status|pay]", NamedTextColor.GRAY))
        sender.sendMessage(
            Component.text("/civ protection repair <target-percent>", NamedTextColor.GRAY),
        )
        sender.sendMessage(Component.text("/civ protection resume <job-id>", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ status", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ declare <civilization>", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ surrender", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ repair [battle-id] (menu)", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ repair status <battle-id>", NamedTextColor.GRAY))
        sender.sendMessage(
            Component.text(
                "/civ repair start <battle-id> <target-percent>",
                NamedTextColor.GRAY,
            ),
        )
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

    private fun prefixed(message: String, color: NamedTextColor): Component =
        Component.text("[Civilizations] ", NamedTextColor.DARK_PURPLE)
            .append(Component.text(message, color))

    companion object {
        const val USE_PERMISSION = "civilizations.use"
        const val DECLARE_PERMISSION = "civilizations.war.declare"
        const val SURRENDER_PERMISSION = "civilizations.war.surrender"
        const val BALANCE_PERMISSION = "civilizations.economy.balance"
        const val DEPOSIT_PERMISSION = "civilizations.economy.deposit"
        const val WITHDRAW_PERMISSION = "civilizations.economy.withdraw"
        const val CLAIM_PERMISSION = "civilizations.land.claim"
        const val LAND_PROTECTION_PERMISSION = "civilizations.land.protection"
        const val REPAIR_STATUS_PERMISSION = "civilizations.repair.status"
        const val REPAIR_START_PERMISSION = "civilizations.repair.start"
    }
}
