package io.bennyc.civilizations.infrastructure.paper.repair

import io.bennyc.civilizations.application.repair.CreatedRepairJob
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.repair.RepairJob
import io.bennyc.civilizations.domain.repair.RepairJobStatus
import io.bennyc.civilizations.domain.war.Battle
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleOutcome
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.infrastructure.paper.CivilizationsCommand
import io.bennyc.civilizations.infrastructure.runtime.ActiveSeasonRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Player inventory adapter for repairs. It renders application-owned assessments and quotes;
 * every preview and confirmed start still runs through [PaperRepairCoordinator].
 */
class PaperRepairMenu(
    private val runtime: CivilizationsRuntime,
    private val server: Server,
    private val repairCoordinator: PaperRepairCoordinator,
    private val logger: Logger,
) : Listener, AutoCloseable {
    private val sessions = mutableMapOf<UUID, MenuSession>()
    private var closed = false

    fun open(player: Player, battleId: BattleId? = null) {
        check(server.isPrimaryThread) { "Repair inventories must open on the server thread" }
        if (closed) return error(player, "The repair menu is unavailable while the server stops")
        if (!player.hasPermission(CivilizationsCommand.REPAIR_STATUS_PERMISSION)) {
            return error(player, "You do not have permission to view repair status")
        }
        val context = context(player) ?: return
        if (battleId == null) {
            openBrowser(player, context, 0)
        } else {
            openBattle(player, context.civilizationId, battleId, returnPage = 0)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? RepairMenuHolder ?: return
        event.isCancelled = true
        if (holder.playerId != player.uniqueId || event.rawSlot !in 0 until event.view.topInventory.size) {
            return
        }
        val session = sessions[player.uniqueId]
            ?.takeIf { it.token == holder.token }
            ?: return
        when (val screen = session.screen) {
            is MenuScreen.Browser -> clickBrowser(player, screen, event.rawSlot)
            is MenuScreen.Detail -> clickDetail(player, screen, event.rawSlot)
            is MenuScreen.Confirmation -> clickConfirmation(player, screen, event.rawSlot)
            is MenuScreen.Error -> clickError(player, screen, event.rawSlot)
            is MenuScreen.Loading,
            is MenuScreen.Starting,
            -> if (event.rawSlot == RepairMenuLayout.CLOSE_SLOT) player.closeInventory()
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is RepairMenuHolder &&
            event.rawSlots.any { it < event.view.topInventory.size }
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val holder = event.inventory.holder as? RepairMenuHolder ?: return
        sessions[player.uniqueId]?.takeIf { it.token == holder.token }?.let {
            sessions.remove(player.uniqueId)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
    }

    override fun close() {
        if (closed) return
        closed = true
        val openPlayers = sessions.keys.mapNotNull(server::getPlayer)
        sessions.clear()
        openPlayers.forEach { player ->
            if (player.openInventory.topInventory.holder is RepairMenuHolder) {
                player.closeInventory()
            }
        }
    }

    private fun clickBrowser(player: Player, screen: MenuScreen.Browser, slot: Int) {
        screen.battleBySlot[slot]?.let { battleId ->
            openBattle(player, screen.civilizationId, battleId, screen.page)
            return
        }
        when (slot) {
            RepairMenuLayout.PREVIOUS_PAGE_SLOT -> {
                if (screen.page > 0) {
                    context(player, screen.civilizationId)?.let {
                        openBrowser(player, it, screen.page - 1)
                    }
                }
            }
            RepairMenuLayout.NEXT_PAGE_SLOT -> {
                if (screen.page + 1 < screen.pageCount) {
                    context(player, screen.civilizationId)?.let {
                        openBrowser(player, it, screen.page + 1)
                    }
                } else {
                    player.closeInventory()
                }
            }
        }
    }

    private fun clickDetail(player: Player, screen: MenuScreen.Detail, slot: Int) {
        when (slot) {
            RepairMenuLayout.BACK_SLOT -> context(player, screen.civilizationId)?.let {
                openBrowser(player, it, screen.returnPage)
            }
            RepairMenuLayout.REFRESH_SLOT -> openBattle(
                player,
                screen.civilizationId,
                screen.battleId,
                screen.returnPage,
            )
            RepairMenuLayout.CLOSE_SLOT -> player.closeInventory()
            else -> RepairMenuLayout.targetBySlot[slot]?.let { target ->
                if (!player.hasPermission(CivilizationsCommand.REPAIR_START_PERMISSION)) {
                    error(player, "You do not have permission to start repairs")
                } else {
                    openBattle(
                        player = player,
                        civilizationId = screen.civilizationId,
                        battleId = screen.battleId,
                        returnPage = screen.returnPage,
                        targetCompletionBasisPoints = target,
                        confirmation = true,
                    )
                }
            }
        }
    }

    private fun clickConfirmation(
        player: Player,
        screen: MenuScreen.Confirmation,
        slot: Int,
    ) {
        when (slot) {
            RepairMenuLayout.BACK_SLOT -> openBattle(
                player,
                screen.civilizationId,
                screen.battleId,
                screen.returnPage,
            )
            RepairMenuLayout.REFRESH_SLOT -> openBattle(
                player = player,
                civilizationId = screen.civilizationId,
                battleId = screen.battleId,
                returnPage = screen.returnPage,
                targetCompletionBasisPoints = screen.targetCompletionBasisPoints,
                confirmation = true,
            )
            RepairMenuLayout.CLOSE_SLOT -> player.closeInventory()
            RepairMenuLayout.CONFIRM_SLOT -> if (screen.confirmEnabled) {
                startRepair(player, screen)
            }
        }
    }

    private fun clickError(player: Player, screen: MenuScreen.Error, slot: Int) {
        when (slot) {
            RepairMenuLayout.BACK_SLOT -> context(player, screen.civilizationId)?.let {
                openBrowser(player, it, screen.returnPage)
            }
            RepairMenuLayout.REFRESH_SLOT -> openBattle(
                player = player,
                civilizationId = screen.civilizationId,
                battleId = screen.battleId,
                returnPage = screen.returnPage,
                targetCompletionBasisPoints = screen.targetCompletionBasisPoints,
                confirmation = screen.confirmation,
            )
            RepairMenuLayout.CLOSE_SLOT -> player.closeInventory()
        }
    }

    private fun openBrowser(player: Player, context: PlayerContext, requestedPage: Int) {
        val battles = context.active.battles
            .asSequence()
            .filter { battle ->
                battle.status == BattleStatus.CLOSED &&
                    context.civilizationId in setOf(
                        battle.attackingCivilizationId,
                        battle.defendingCivilizationId,
                    )
            }
            .sortedWith(
                compareByDescending<Battle> { it.endedAt }
                    .thenByDescending { it.id.toString() },
            )
            .toList()
        val pageCount = RepairMenuLayout.pageCount(battles.size)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val visible = RepairMenuLayout.page(battles, page)
        val battleBySlot = visible.mapIndexed { slot, battle -> slot to battle.id }.toMap()
        val inventory = install(
            player = player,
            size = 54,
            title = Component.text("Civilization Repairs", NamedTextColor.DARK_PURPLE),
            screen = MenuScreen.Browser(
                civilizationId = context.civilizationId,
                page = page,
                pageCount = pageCount,
                battleBySlot = battleBySlot,
            ),
        )
        visible.forEachIndexed { slot, battle ->
            inventory.setItem(slot, battleItem(context.active, context.civilizationId, battle))
        }
        if (visible.isEmpty()) {
            inventory.setItem(
                22,
                item(
                    Material.PAPER,
                    "No repairable battle history",
                    NamedTextColor.YELLOW,
                    listOf(
                        line("Closed battles involving your civilization appear here."),
                        line("A sealed damage report is checked after you select one."),
                    ),
                ),
            )
        }
        if (page > 0) {
            inventory.setItem(
                RepairMenuLayout.PREVIOUS_PAGE_SLOT,
                item(Material.ARROW, "Previous Page", NamedTextColor.YELLOW),
            )
        }
        inventory.setItem(
            49,
            item(
                Material.BOOK,
                "Battle History",
                NamedTextColor.GOLD,
                listOf(line("Page ${page + 1}/$pageCount"), line("${battles.size} closed battles")),
            ),
        )
        inventory.setItem(
            RepairMenuLayout.NEXT_PAGE_SLOT,
            if (page + 1 < pageCount) {
                item(Material.ARROW, "Next Page", NamedTextColor.YELLOW)
            } else {
                item(Material.OAK_DOOR, "Close", NamedTextColor.RED)
            },
        )
    }

    private fun openBattle(
        player: Player,
        civilizationId: CivilizationId,
        battleId: BattleId,
        returnPage: Int,
        targetCompletionBasisPoints: Int = 10_000,
        confirmation: Boolean = false,
    ) {
        if (context(player, civilizationId) == null) return
        val loading = MenuScreen.Loading(
            civilizationId,
            battleId,
            returnPage,
            targetCompletionBasisPoints,
            confirmation,
        )
        val inventory = install(
            player,
            54,
            Component.text("Scanning Battle Damage", NamedTextColor.DARK_PURPLE),
            loading,
        )
        inventory.setItem(
            22,
            item(
                Material.CLOCK,
                "Scanning current blocks…",
                NamedTextColor.YELLOW,
                listOf(
                    line("The scan is bounded across server ticks."),
                    line("You may close this menu safely."),
                ),
                glint = true,
            ),
        )
        inventory.setItem(
            RepairMenuLayout.CLOSE_SLOT,
            item(Material.OAK_DOOR, "Close", NamedTextColor.RED),
        )
        val token = sessions.getValue(player.uniqueId).token
        repairCoordinator.status(
            battleId = battleId,
            civilizationId = civilizationId,
            targetCompletionBasisPoints = targetCompletionBasisPoints,
        ) { outcome ->
            if (!accepts(player, token, loading)) return@status
            val current = context(player, civilizationId) ?: return@status
            when (outcome) {
                is PaperRepairOutcome.Completed -> if (confirmation) {
                    showConfirmation(player, current, outcome.value, returnPage)
                } else {
                    showDetail(player, current, outcome.value, returnPage)
                }
                is PaperRepairOutcome.Rejected -> showError(
                    player,
                    loading,
                    outcome.description,
                )
                is PaperRepairOutcome.Unavailable -> showError(
                    player,
                    loading,
                    outcome.description,
                )
                is PaperRepairOutcome.Failed -> {
                    logger.log(Level.WARNING, "Repair menu scan failed for $battleId", outcome.failure)
                    showError(
                        player,
                        loading,
                        "Repair scan failed: ${outcome.failure.message ?: "unexpected error"}",
                    )
                }
            }
        }
    }

    private fun showDetail(
        player: Player,
        context: PlayerContext,
        status: PaperRepairStatus,
        returnPage: Int,
    ) {
        val battle = status.assessment.basis.battle
        val inventory = install(
            player,
            54,
            Component.text("Repair ${shortId(battle.id)}", NamedTextColor.DARK_PURPLE),
            MenuScreen.Detail(context.civilizationId, battle.id, returnPage),
        )
        inventory.setItem(4, battleItem(context.active, context.civilizationId, battle))
        inventory.setItem(
            11,
            item(
                Material.LIME_CONCRETE,
                "Actual Completion: ${percentage(status.assessment.completionBasisPoints)}",
                NamedTextColor.GREEN,
                listOf(
                    line("${status.assessment.restoredCount}/${status.assessment.totalEligibleCount} blocks restored"),
                    line("Manual repairs are included automatically."),
                ),
            ),
        )
        inventory.setItem(
            13,
            item(
                Material.YELLOW_CONCRETE,
                "Still Repairable: ${status.assessment.repairableCount} " +
                    "(${percentage(fractionBasisPoints(status.assessment.repairableCount, status.assessment.totalEligibleCount))})",
                NamedTextColor.YELLOW,
                listOf(line("These blocks still match the sealed damaged state.")),
            ),
        )
        inventory.setItem(
            15,
            item(
                Material.ORANGE_CONCRETE,
                "Player Changes: ${status.assessment.conflictCount}",
                NamedTextColor.GOLD,
                listOf(
                    line("Later edits are preserved and never overwritten."),
                    line("A correct manual restoration counts on the next scan."),
                ),
            ),
        )
        inventory.setItem(21, treasuryItem(context))
        inventory.setItem(23, quoteItem(status, context.active.economySettings.currencyScale))
        inventory.setItem(25, latestJobItem(status.jobs.firstOrNull()))
        RepairMenuLayout.targetBySlot.forEach { (slot, target) ->
            inventory.setItem(
                slot,
                item(
                    Material.EMERALD,
                    "Preview ${percentage(target)} Target",
                    NamedTextColor.GREEN,
                    listOf(
                        line("Targets are absolute, not an extra percentage."),
                        line("The server will scan and calculate the exact price."),
                        line("Click to preview before paying.", NamedTextColor.YELLOW),
                    ),
                ),
            )
        }
        navigation(inventory, backLabel = "Battle List")
    }

    private fun showConfirmation(
        player: Player,
        context: PlayerContext,
        status: PaperRepairStatus,
        returnPage: Int,
    ) {
        val battle = status.assessment.basis.battle
        val presentation = status.presentQuote()
        val enabled = presentation is RepairQuotePresentation.Available
        val inventory = install(
            player,
            54,
            Component.text("Confirm ${percentage(status.targetCompletionBasisPoints)} Repair", NamedTextColor.DARK_PURPLE),
            MenuScreen.Confirmation(
                civilizationId = context.civilizationId,
                battleId = battle.id,
                returnPage = returnPage,
                targetCompletionBasisPoints = status.targetCompletionBasisPoints,
                confirmEnabled = enabled,
                maximumGrossCost = (presentation as? RepairQuotePresentation.Available)
                    ?.quote
                    ?.grossCost,
            ),
        )
        inventory.setItem(4, battleItem(context.active, context.civilizationId, battle))
        inventory.setItem(
            12,
            item(
                Material.LIME_CONCRETE,
                "Current: ${percentage(status.assessment.completionBasisPoints)}",
                NamedTextColor.GREEN,
                listOf(line("${status.assessment.restoredCount} blocks currently restored")),
            ),
        )
        inventory.setItem(14, treasuryItem(context))
        inventory.setItem(22, quoteItem(status, context.active.economySettings.currencyScale))
        inventory.setItem(
            RepairMenuLayout.CONFIRM_SLOT,
            confirmationItem(presentation, context.active.economySettings.currencyScale),
        )
        navigation(inventory, backLabel = "Back to Repair Details")
    }

    private fun startRepair(player: Player, screen: MenuScreen.Confirmation) {
        if (!player.hasPermission(CivilizationsCommand.REPAIR_START_PERMISSION)) {
            return error(player, "You do not have permission to start repairs")
        }
        if (context(player, screen.civilizationId) == null) return
        val starting = MenuScreen.Starting(
            screen.civilizationId,
            screen.battleId,
            screen.returnPage,
            screen.targetCompletionBasisPoints,
        )
        val inventory = install(
            player,
            54,
            Component.text("Starting Repair", NamedTextColor.DARK_PURPLE),
            starting,
        )
        inventory.setItem(
            22,
            item(
                Material.CLOCK,
                "Rechecking and creating repair…",
                NamedTextColor.YELLOW,
                listOf(
                    line("No money moves until the current world state is verified."),
                    line("This prevents stale menu prices and duplicate payments."),
                ),
                glint = true,
            ),
        )
        inventory.setItem(
            RepairMenuLayout.CLOSE_SLOT,
            item(Material.OAK_DOOR, "Close", NamedTextColor.RED),
        )
        val token = sessions.getValue(player.uniqueId).token
        repairCoordinator.startOrdinary(
            battleId = screen.battleId,
            civilizationId = screen.civilizationId,
            playerId = PlayerId(player.uniqueId),
            targetCompletionBasisPoints = screen.targetCompletionBasisPoints,
            maximumGrossCost = screen.maximumGrossCost,
        ) { outcome ->
            if (!accepts(player, token, starting)) return@startOrdinary
            when (outcome) {
                is PaperRepairOutcome.Completed -> {
                    repairStarted(player, outcome.value)
                    openBattle(
                        player,
                        screen.civilizationId,
                        screen.battleId,
                        screen.returnPage,
                    )
                }
                is PaperRepairOutcome.Rejected -> {
                    error(player, outcome.description)
                    openBattle(
                        player,
                        screen.civilizationId,
                        screen.battleId,
                        screen.returnPage,
                    )
                }
                is PaperRepairOutcome.Unavailable -> showError(
                    player,
                    MenuScreen.Loading(
                        screen.civilizationId,
                        screen.battleId,
                        screen.returnPage,
                        screen.targetCompletionBasisPoints,
                        confirmation = true,
                    ),
                    outcome.description,
                )
                is PaperRepairOutcome.Failed -> {
                    logger.log(
                        Level.WARNING,
                        "Repair menu start failed for ${screen.battleId}",
                        outcome.failure,
                    )
                    showError(
                        player,
                        MenuScreen.Loading(
                            screen.civilizationId,
                            screen.battleId,
                            screen.returnPage,
                            screen.targetCompletionBasisPoints,
                            confirmation = true,
                        ),
                        "Repair creation failed: ${outcome.failure.message ?: "unexpected error"}",
                    )
                }
            }
        }
    }

    private fun repairStarted(player: Player, created: CreatedRepairJob) {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason ?: return
        success(
            player,
            "Repair ${created.job.id} started: ${created.job.selectedCount} blocks for " +
                active.economySettings.currencyScale.format(created.job.grossCost) +
                "; target ${percentage(created.job.targetCompletionBasisPoints)}.",
        )
    }

    private fun showError(player: Player, loading: MenuScreen.Loading, description: String) {
        val inventory = install(
            player,
            54,
            Component.text("Repair Unavailable", NamedTextColor.DARK_RED),
            MenuScreen.Error(
                civilizationId = loading.civilizationId,
                battleId = loading.battleId,
                returnPage = loading.returnPage,
                targetCompletionBasisPoints = loading.targetCompletionBasisPoints,
                confirmation = loading.confirmation,
            ),
        )
        inventory.setItem(
            22,
            item(
                Material.BARRIER,
                "Repair unavailable",
                NamedTextColor.RED,
                wrapLore(description),
            ),
        )
        navigation(inventory, backLabel = "Battle List", refreshLabel = "Try Again")
    }

    private fun install(
        player: Player,
        size: Int,
        title: Component,
        screen: MenuScreen,
    ): Inventory {
        val token = UUID.randomUUID()
        val holder = RepairMenuHolder(player.uniqueId, token)
        val inventory = server.createInventory(holder, size, title)
        holder.backing = inventory
        sessions[player.uniqueId] = MenuSession(token, screen)
        player.openInventory(inventory)
        return inventory
    }

    private fun accepts(player: Player, token: UUID, expected: MenuScreen): Boolean =
        !closed && player.isOnline && sessions[player.uniqueId]?.let { session ->
            session.token == token && session.screen === expected
        } == true

    private fun context(
        player: Player,
        expectedCivilizationId: CivilizationId? = null,
    ): PlayerContext? {
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason
        if (active == null) {
            error(player, "No active season is ready")
            player.closeInventory()
            return null
        }
        val membership = active.membershipOf(PlayerId(player.uniqueId))
        if (membership == null ||
            expectedCivilizationId != null && membership.civilizationId != expectedCivilizationId
        ) {
            error(player, "You are no longer in the civilization that opened this repair menu")
            player.closeInventory()
            return null
        }
        return PlayerContext(active, membership.civilizationId)
    }

    private fun navigation(
        inventory: Inventory,
        backLabel: String,
        refreshLabel: String = "Refresh",
    ) {
        inventory.setItem(
            RepairMenuLayout.BACK_SLOT,
            item(Material.ARROW, backLabel, NamedTextColor.YELLOW),
        )
        inventory.setItem(
            RepairMenuLayout.REFRESH_SLOT,
            item(Material.RECOVERY_COMPASS, refreshLabel, NamedTextColor.AQUA),
        )
        inventory.setItem(
            RepairMenuLayout.CLOSE_SLOT,
            item(Material.OAK_DOOR, "Close", NamedTextColor.RED),
        )
    }

    private fun battleItem(
        active: ActiveSeasonRuntimeState,
        civilizationId: CivilizationId,
        battle: Battle,
    ): ItemStack {
        val opponentId = if (battle.attackingCivilizationId == civilizationId) {
            battle.defendingCivilizationId
        } else {
            battle.attackingCivilizationId
        }
        val opponent = active.civilizations.singleOrNull { it.id == opponentId }?.name?.value
            ?: shortId(opponentId)
        val side = if (battle.attackingCivilizationId == civilizationId) "Attacker" else "Defender"
        val result = when {
            battle.outcome == BattleOutcome.DRAW -> "Draw"
            battle.winnerCivilizationId == civilizationId -> "Victory"
            battle.winnerCivilizationId == null -> "No result"
            else -> "Defeat"
        }
        return item(
            Material.MAP,
            "Battle ${shortId(battle.id)}",
            NamedTextColor.LIGHT_PURPLE,
            listOf(
                line("Opponent: $opponent"),
                line("Side: $side"),
                line("Result: $result", resultColor(result)),
                line("Ended: ${battle.endedAt?.let(DISPLAY_TIME::format) ?: "unknown"}"),
                line("Click to scan current repair status.", NamedTextColor.YELLOW),
            ),
        )
    }

    private fun treasuryItem(context: PlayerContext): ItemStack {
        val scale = context.active.economySettings.currencyScale
        val balance = context.active.civilizationAccounts[context.civilizationId]?.balance
        return item(
            Material.GOLD_INGOT,
            "Civilization Treasury",
            NamedTextColor.GOLD,
            listOf(
                line(
                    if (balance == null) "Balance unavailable" else "Balance: ${scale.format(balance)}",
                ),
                line("Confirmed repairs can spend only this balance."),
            ),
        )
    }

    private fun quoteItem(status: PaperRepairStatus, scale: CurrencyScale): ItemStack =
        when (val presentation = status.presentQuote()) {
            is RepairQuotePresentation.Available -> item(
                Material.GOLD_BLOCK,
                "${percentage(status.targetCompletionBasisPoints)} Quote: ${scale.format(presentation.quote.grossCost)}",
                NamedTextColor.GOLD,
                listOf(
                    line("Repairs ${presentation.quote.selectedCount} remaining blocks."),
                    line(
                        "Victor share: ${percentage(presentation.quote.victorShareBasisPoints)} " +
                            "(${scale.format(presentation.quote.victorProceeds)})",
                    ),
                    line("Currency removed: ${scale.format(presentation.quote.sinkAmount)}"),
                ),
            )
            is RepairQuotePresentation.AlreadyReached -> item(
                Material.LIME_STAINED_GLASS_PANE,
                "${percentage(presentation.targetBasisPoints)} Already Reached",
                NamedTextColor.GREEN,
                listOf(line("${presentation.restoredCount} blocks are restored.")),
            )
            is RepairQuotePresentation.Unreachable -> item(
                Material.ORANGE_STAINED_GLASS_PANE,
                "${percentage(presentation.targetBasisPoints)} Cannot Be Reached",
                NamedTextColor.GOLD,
                listOf(
                    line("${presentation.repairableCount} blocks remain repairable."),
                    line("${presentation.conflictCount} later player changes are preserved."),
                ),
            )
            is RepairQuotePresentation.Rejected -> item(
                Material.BARRIER,
                "Quote unavailable",
                NamedTextColor.RED,
                wrapLore(presentation.description),
            )
        }

    private fun confirmationItem(
        presentation: RepairQuotePresentation,
        scale: CurrencyScale,
    ): ItemStack = when (presentation) {
        is RepairQuotePresentation.Available -> item(
            Material.EMERALD_BLOCK,
            "Confirm and Pay ${scale.format(presentation.quote.grossCost)}",
            NamedTextColor.GREEN,
            listOf(
                line("Starts ${presentation.quote.selectedCount} block repairs."),
                line("The world and price are rechecked before payment."),
                line("You will never pay more than this quote."),
                line("Click to confirm.", NamedTextColor.YELLOW),
            ),
            glint = true,
        )
        is RepairQuotePresentation.AlreadyReached -> item(
            Material.LIME_STAINED_GLASS_PANE,
            "Nothing to purchase",
            NamedTextColor.GREEN,
            listOf(line("This target is already complete.")),
        )
        is RepairQuotePresentation.Unreachable -> item(
            Material.BARRIER,
            "Target unavailable",
            NamedTextColor.RED,
            listOf(line("Later player changes currently prevent this target.")),
        )
        is RepairQuotePresentation.Rejected -> item(
            Material.BARRIER,
            "Cannot start repair",
            NamedTextColor.RED,
            wrapLore(presentation.description),
        )
    }

    private fun latestJobItem(job: RepairJob?): ItemStack = if (job == null) {
        item(
            Material.CLOCK,
            "No Previous Repair Job",
            NamedTextColor.GRAY,
            listOf(line("Select a target below to preview one.")),
        )
    } else {
        val material = when (job.status) {
            RepairJobStatus.QUEUED,
            RepairJobStatus.RUNNING,
            -> Material.CLOCK
            RepairJobStatus.PAUSED -> Material.LEVER
            RepairJobStatus.COMPLETED -> Material.LIME_DYE
            RepairJobStatus.CANCELLED,
            RepairJobStatus.FAILED,
            -> Material.RED_DYE
        }
        item(
            material,
            "Latest Job: ${job.status}",
            when (job.status) {
                RepairJobStatus.COMPLETED -> NamedTextColor.GREEN
                RepairJobStatus.CANCELLED,
                RepairJobStatus.FAILED,
                -> NamedTextColor.RED
                else -> NamedTextColor.YELLOW
            },
            listOf(
                line("Target: ${percentage(job.targetCompletionBasisPoints)}"),
                line("Progress: ${job.nextItemOrdinal}/${job.selectedCount}"),
                line("Restored: ${job.restoredCount}"),
                line("Conflicts: ${job.skippedConflictCount}"),
                line("Failed: ${job.failedCount}"),
            ),
        )
    }

    private fun item(
        material: Material,
        name: String,
        color: NamedTextColor,
        lore: List<Component> = emptyList(),
        glint: Boolean = false,
    ): ItemStack = ItemStack(material).also { stack ->
        stack.itemMeta = stack.itemMeta.apply {
            displayName(line(name, color))
            lore(lore)
            if (glint) setEnchantmentGlintOverride(true)
        }
    }

    private fun wrapLore(message: String): List<Component> = message
        .chunked(42)
        .map { line(it, NamedTextColor.RED) }

    private fun line(
        text: String,
        color: NamedTextColor = NamedTextColor.GRAY,
    ): Component = Component.text(text, color).decoration(TextDecoration.ITALIC, false)

    private fun percentage(basisPoints: Int): String =
        BigDecimal.valueOf(basisPoints.toLong(), 2).stripTrailingZeros().toPlainString() + "%"

    private fun fractionBasisPoints(part: Long, total: Long): Int =
        ((part * 10_000L) / total).toInt()

    private fun shortId(value: Any): String = value.toString().take(8)

    private fun resultColor(result: String): NamedTextColor = when (result) {
        "Victory" -> NamedTextColor.GREEN
        "Defeat" -> NamedTextColor.RED
        else -> NamedTextColor.YELLOW
    }

    private fun success(player: Player, message: String) {
        player.sendMessage(prefixed(message, NamedTextColor.GREEN))
    }

    private fun error(player: Player, message: String) {
        player.sendMessage(prefixed(message, NamedTextColor.RED))
    }

    private fun prefixed(message: String, color: NamedTextColor): Component =
        Component.text("[Civilizations] ", NamedTextColor.DARK_PURPLE)
            .append(Component.text(message, color))

    private data class PlayerContext(
        val active: ActiveSeasonRuntimeState,
        val civilizationId: CivilizationId,
    )

    private data class MenuSession(
        val token: UUID,
        val screen: MenuScreen,
    )

    private sealed interface MenuScreen {
        data class Browser(
            val civilizationId: CivilizationId,
            val page: Int,
            val pageCount: Int,
            val battleBySlot: Map<Int, BattleId>,
        ) : MenuScreen

        data class Loading(
            val civilizationId: CivilizationId,
            val battleId: BattleId,
            val returnPage: Int,
            val targetCompletionBasisPoints: Int,
            val confirmation: Boolean,
        ) : MenuScreen

        data class Detail(
            val civilizationId: CivilizationId,
            val battleId: BattleId,
            val returnPage: Int,
        ) : MenuScreen

        data class Confirmation(
            val civilizationId: CivilizationId,
            val battleId: BattleId,
            val returnPage: Int,
            val targetCompletionBasisPoints: Int,
            val confirmEnabled: Boolean,
            val maximumGrossCost: MoneyAmount?,
        ) : MenuScreen {
            init {
                require(confirmEnabled == (maximumGrossCost != null)) {
                    "A payable repair confirmation requires its displayed maximum price"
                }
            }
        }

        data class Starting(
            val civilizationId: CivilizationId,
            val battleId: BattleId,
            val returnPage: Int,
            val targetCompletionBasisPoints: Int,
        ) : MenuScreen

        data class Error(
            val civilizationId: CivilizationId,
            val battleId: BattleId,
            val returnPage: Int,
            val targetCompletionBasisPoints: Int,
            val confirmation: Boolean,
        ) : MenuScreen
    }

    private class RepairMenuHolder(
        val playerId: UUID,
        val token: UUID,
    ) : InventoryHolder {
        lateinit var backing: Inventory

        override fun getInventory(): Inventory = backing
    }

    private companion object {
        val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }
}
