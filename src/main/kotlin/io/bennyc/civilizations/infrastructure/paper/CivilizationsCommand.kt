package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.war.DeclareWar
import io.bennyc.civilizations.application.war.SurrenderBattle
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.economy.EconomyBridgeDirection
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.domain.war.WarRulesSnapshot
import io.bennyc.civilizations.domain.war.WarStatus
import io.bennyc.civilizations.infrastructure.runtime.ActiveSeasonRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import io.bennyc.civilizations.infrastructure.paper.economy.PaperEconomyBridgeCoordinator
import io.bennyc.civilizations.infrastructure.paper.economy.PaperEconomyTransferOutcome
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

/** Player-facing declared-war operations; civilization authority stays application-owned. */
class CivilizationsCommand(
    private val runtime: CivilizationsRuntime,
    private val rules: WarRulesSnapshot,
    private val server: Server,
    private val logger: Logger,
    private val economyBridge: PaperEconomyBridgeCoordinator,
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
            else -> showHelp(sender)
        }
    }

    override fun suggest(
        source: CommandSourceStack,
        args: Array<out String>,
    ): Collection<String> {
        val choices = when {
            args.size <= 1 -> listOf("status", "declare", "surrender", "balance", "deposit", "withdraw")
            args.firstOrNull().equals("declare", true) && args.size == 2 ->
                targetCivilizationReferences(source.sender)
            else -> emptyList()
        }
        val partial = args.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
        return choices.filter { it.lowercase(Locale.ROOT).startsWith(partial) }
    }

    override fun permission(): String = USE_PERMISSION

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
                        is ApplicationResult.Applied -> {
                            val surrender = result.value
                            val message = prefixed(
                                "Civilization ${surrender.surrenderedCivilizationId} surrendered " +
                                    "battle ${surrender.battle.id}; destruction is closed and " +
                                    "${surrender.requestedOutcome} was requested.",
                                NamedTextColor.RED,
                            )
                            val recipients = outcome.state.activeSeason
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
                        }
                        is ApplicationResult.Unchanged -> Unit
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
    }
}
