package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.repair.RepairTargetAlreadyReached
import io.bennyc.civilizations.application.repair.RepairTargetUnreachable
import io.bennyc.civilizations.application.war.DeclareWar
import io.bennyc.civilizations.application.war.BattleSurrender
import io.bennyc.civilizations.application.war.SurrenderBattle
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.economy.EconomyBridgeDirection
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
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairOutcome
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairStatus
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
    private val battleResolutionCoordinator: PaperBattleResolutionCoordinator,
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
                listOf("status", "declare", "surrender", "balance", "deposit", "withdraw", "repair")
            args.firstOrNull().equals("declare", true) && args.size == 2 ->
                targetCivilizationReferences(source.sender)
            args.firstOrNull().equals("repair", true) && args.size == 2 ->
                listOf("status", "start")
            args.firstOrNull().equals("repair", true) && args.size == 3 -> battleReferences()
            else -> emptyList()
        }
        val partial = args.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
        return choices.filter { it.lowercase(Locale.ROOT).startsWith(partial) }
    }

    override fun permission(): String = USE_PERMISSION

    private fun handleRepair(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return error(sender, "Only a player can repair")
        val active = activeSeason(sender) ?: return
        val membership = active.membershipOf(PlayerId(player.uniqueId))
            ?: return error(sender, "You are not in a civilization this season")
        val battleId = args.getOrNull(2)?.let { parseBattleId(sender, it) }
            ?: return error(sender, "Usage: /civ repair <status|start> <battle-id> [target-percent]")
        when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
            "status" -> {
                if (!player.hasPermission(REPAIR_STATUS_PERMISSION)) {
                    return error(sender, "You do not have permission to view repair status")
                }
                if (args.size != 3) {
                    return error(sender, "Usage: /civ repair status <battle-id>")
                }
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
            else -> error(sender, "Usage: /civ repair <status|start> <battle-id> [target-percent]")
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
        when (val quote = status.quoteToFull) {
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

    private fun battleReferences(): List<String> =
        (runtime.state as? CivilizationsRuntimeState.Ready)
            ?.activeSeason
            ?.battles
            ?.filter { it.status == BattleStatus.CLOSED }
            ?.map { it.id.toString() }
            .orEmpty()

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
        sender.sendMessage(Component.text("/civ status", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ declare <civilization>", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("/civ surrender", NamedTextColor.GRAY))
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
        const val REPAIR_STATUS_PERMISSION = "civilizations.repair.status"
        const val REPAIR_START_PERMISSION = "civilizations.repair.start"
    }
}
