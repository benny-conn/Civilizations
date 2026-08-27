package io.bennyc.civilizations.infrastructure.paper.protection

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.damage.JournalActorRelationship
import io.bennyc.civilizations.application.damage.PrepareBlockMutation
import io.bennyc.civilizations.application.damage.PreparedBlockMutation
import io.bennyc.civilizations.application.protection.PlayerProtectionAction
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BattleBlockChange
import io.bennyc.civilizations.domain.damage.BlockChangeId
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.infrastructure.runtime.ActiveBattleBlockMutationAuthorization
import io.bennyc.civilizations.infrastructure.runtime.BattleBlockMutationQueue
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.TileState
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.PluginManager
import org.mockito.Mockito
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PaperBattleBlockMutationAdapterTest {
    @Test
    fun `break is cancelled journaled and replayed only after durable completion`() {
        val harness = PaperHarness(targetMaterial = Material.STONE, heldMaterial = Material.IRON_PICKAXE)
        val event = BlockBreakEvent(harness.target, harness.player)

        assertTrue(harness.adapter.intercept(event))
        assertTrue(event.isCancelled)
        assertEquals(Material.STONE, harness.target.type)
        assertEquals("minecraft:stone", harness.journalRequest?.observedState?.blockData)

        harness.completeJournal()

        assertEquals(Material.AIR, harness.target.type)
        assertEquals(1, harness.replayedBreaks)
        assertEquals(1, harness.adapterMetrics().applied)
    }

    @Test
    fun `place waits for rollback replays the event and consumes exactly one item`() {
        val harness = PaperHarness(targetMaterial = Material.STONE, heldMaterial = Material.STONE)
        harness.heldItem = harness.item(Material.STONE, 2)
        val replaced = harness.blockState(harness.target, Material.AIR)
        val event = BlockPlaceEvent(
            harness.target,
            replaced,
            harness.support,
            harness.heldItem.clone(),
            harness.player,
            true,
            EquipmentSlot.HAND,
        )

        assertTrue(harness.adapter.intercept(event))
        assertTrue(event.isCancelled)
        assertEquals("minecraft:air", harness.journalRequest?.observedState?.blockData)

        harness.setMaterial(harness.target, Material.AIR)
        harness.completeJournal()

        assertEquals(Material.STONE, harness.target.type)
        assertEquals(1, harness.heldItem.amount)
        assertEquals(1, harness.replayedPlaces)
        assertEquals(1, harness.adapterMetrics().applied)
    }

    @Test
    fun `cancelled placement replay restores the original block and item`() {
        val harness = PaperHarness(
            targetMaterial = Material.STONE,
            heldMaterial = Material.STONE,
            cancelReplayPlace = true,
        )
        harness.heldItem = harness.item(Material.STONE, 2)
        val event = BlockPlaceEvent(
            harness.target,
            harness.blockState(harness.target, Material.AIR),
            harness.support,
            harness.heldItem.clone(),
            harness.player,
            true,
            EquipmentSlot.HAND,
        )

        assertTrue(harness.adapter.intercept(event))
        harness.setMaterial(harness.target, Material.AIR)
        harness.completeJournal()

        assertEquals(Material.AIR, harness.target.type)
        assertEquals(2, harness.heldItem.amount)
        assertEquals(1, harness.replayedPlaces)
        assertEquals(1, harness.adapterMetrics().stale)
    }

    @Test
    fun `block entities stay cancelled without entering the journal queue`() {
        val harness = PaperHarness(
            targetMaterial = Material.CHEST,
            heldMaterial = Material.IRON_PICKAXE,
            targetIsTile = true,
        )
        val event = BlockBreakEvent(harness.target, harness.player)

        assertTrue(harness.adapter.intercept(event))
        assertTrue(event.isCancelled)
        assertEquals(null, harness.journalRequest)
        assertEquals(Material.CHEST, harness.target.type)
    }

    @Test
    fun `gravity dependency stays cancelled without entering the journal queue`() {
        val harness = PaperHarness(targetMaterial = Material.STONE, heldMaterial = Material.IRON_PICKAXE)
        harness.setMaterialAboveTarget(Material.SAND)
        val event = BlockBreakEvent(harness.target, harness.player)

        assertTrue(harness.adapter.intercept(event))
        assertTrue(event.isCancelled)
        assertEquals(null, harness.journalRequest)
        assertEquals(Material.STONE, harness.target.type)
    }

    @Test
    fun `changed world state aborts instead of overwriting the newer block`() {
        val harness = PaperHarness(targetMaterial = Material.STONE, heldMaterial = Material.IRON_PICKAXE)
        val event = BlockBreakEvent(harness.target, harness.player)

        assertTrue(harness.adapter.intercept(event))
        harness.setMaterial(harness.target, Material.COBBLESTONE)
        harness.completeJournal()

        assertEquals(Material.COBBLESTONE, harness.target.type)
        assertEquals(0, harness.replayedBreaks)
        assertEquals(1, harness.adapterMetrics().stale)
    }

    private class PaperHarness(
        targetMaterial: Material,
        heldMaterial: Material,
        targetIsTile: Boolean = false,
        private val cancelReplayPlace: Boolean = false,
    ) {
        private val targetPosition = BlockPosition3D(worldId, 0, 64, 0)
        private val supportPosition = BlockPosition3D(worldId, 0, 63, 0)
        private val blocks = linkedMapOf<BlockPosition3D, FakeBlock>()
        private lateinit var journalCompletion:
            (RuntimeMutationOutcome<PreparedBlockMutation>) -> Unit
        private val adapterField: PaperBattleBlockMutationAdapter

        var heldItem: ItemStack = item(heldMaterial)
        var journalRequest: PrepareBlockMutation? = null
            private set
        var replayedBreaks = 0
            private set
        var replayedPlaces = 0
            private set

        private val world: World = proxy(World::class.java, "world") { method, args ->
            when (method.name) {
                "getKey" -> NamespacedKey("minecraft", "overworld")
                "getMinHeight" -> -64
                "getMaxHeight" -> 320
                "isChunkLoaded" -> true
                "getBlockAt" -> blockAt(
                    BlockPosition3D(worldId, args[0] as Int, args[1] as Int, args[2] as Int),
                ).paper
                else -> unexpected(method)
            }
        }
        private val inventory: PlayerInventory = proxy(PlayerInventory::class.java, "inventory") {
                method,
                args,
            ->
            when (method.name) {
                "getHeldItemSlot" -> 0
                "getItemInMainHand" -> heldItem
                "getItem" -> when (args.single()) {
                    EquipmentSlot.HAND -> heldItem
                    EquipmentSlot.OFF_HAND -> item(Material.AIR, 0)
                    else -> item(Material.AIR, 0)
                }
                "setItem" -> {
                    if (args[0] == EquipmentSlot.HAND) {
                        heldItem = args[1] as ItemStack
                    }
                    null
                }
                else -> unexpected(method)
            }
        }
        val player: Player = proxy(Player::class.java, "player") { method, args ->
            when (method.name) {
                "getUniqueId" -> actorId.value
                "hasPermission" -> false
                "getInventory" -> inventory
                "getGameMode" -> GameMode.SURVIVAL
                "getWorld" -> world
                "getLocation" -> Location(world, 0.5, 64.5, 2.0)
                "sendActionBar" -> null
                "breakBlock" -> {
                    val block = args.single() as Block
                    val replay = BlockBreakEvent(block, player)
                    adapterField.intercept(replay)
                    if (replay.isCancelled) {
                        false
                    } else {
                        replayedBreaks++
                        setMaterial(block, Material.AIR)
                        true
                    }
                }
                else -> unexpected(method)
            }
        }
        private val pluginManager: PluginManager = proxy(
            PluginManager::class.java,
            "pluginManager",
        ) { method, args ->
            when (method.name) {
                "callEvent" -> {
                    val event = args.single() as Event
                    if (event is BlockPlaceEvent) {
                        replayedPlaces++
                        adapterField.intercept(event)
                        if (cancelReplayPlace) {
                            event.isCancelled = true
                        }
                    }
                    null
                }
                else -> unexpected(method)
            }
        }
        private val server: Server = proxy(Server::class.java, "server") { method, args ->
            when (method.name) {
                "getWorld" -> world.takeIf {
                    (args.single() as NamespacedKey).asString() == worldId.value
                }
                "getPlayer" -> player.takeIf { args.single() == actorId.value }
                "getPluginManager" -> pluginManager
                "createBlockData" -> blockData(materialFrom(args.single() as String))
                else -> unexpected(method)
            }
        }
        private val queue = BattleBlockMutationQueue(
            prepare = { request, completion ->
                assertEquals(null, journalRequest)
                journalRequest = request
                journalCompletion = completion
            },
        )

        val target: Block
        val support: Block
        val adapter: PaperBattleBlockMutationAdapter
            get() = adapterField

        init {
            target = blockAt(targetPosition, targetMaterial, targetIsTile).paper
            support = blockAt(supportPosition, Material.STONE).paper
            adapterField = PaperBattleBlockMutationAdapter(
                server = server,
                logger = Logger.getLogger("battle-mutation-test"),
                mutationQueue = queue,
                authorize = { playerId, action, target ->
                    ActiveBattleBlockMutationAuthorization(
                        battleId = battleId,
                        claimId = claimId,
                        actorId = playerId,
                        actorCivilizationId = civilizationId,
                        action = action,
                        target = target,
                    )
                },
            )
        }

        fun completeJournal() {
            val request = assertNotNull(journalRequest)
            journalCompletion(
                RuntimeMutationOutcome.Completed(
                    ApplicationResult.Applied(prepared(request)),
                    CivilizationsRuntimeState.Ready(activeSeason = null),
                ),
            )
        }

        fun setMaterial(block: Block, material: Material) {
            val fake = blocks.values.single { it.paper === block }
            fake.material = material
        }

        fun setMaterialAboveTarget(material: Material) {
            blockAt(targetPosition.copy(y = targetPosition.y + 1), material).material = material
        }

        fun blockState(block: Block, material: Material): BlockState =
            FakeState(block, material, isTile = false).paper

        fun item(material: Material, amount: Int = 1): ItemStack {
            val item = Mockito.mock(ItemStack::class.java)
            Mockito.`when`(item.type).thenReturn(material)
            Mockito.`when`(item.amount).thenReturn(amount)
            Mockito.`when`(item.enchantments).thenReturn(emptyMap())
            Mockito.`when`(item.clone()).thenReturn(item)
            Mockito.`when`(item.isSimilar(Mockito.any(ItemStack::class.java))).thenAnswer { call ->
                (call.getArgument<ItemStack>(0)).type == material
            }
            Mockito.`when`(item.asQuantity(Mockito.anyInt())).thenAnswer { call ->
                item(material, call.getArgument(0))
            }
            return item
        }

        fun adapterMetrics(): ParsedMetrics {
            val values = adapter.metricsSummary()
                .split(", ")
                .associate { part ->
                    val (key, value) = part.split("=", limit = 2)
                    key to value
                }
            return ParsedMetrics(
                applied = values.getValue("applied").toLong(),
                stale = values.getValue("stale").toLong(),
            )
        }

        private fun blockAt(
            position: BlockPosition3D,
            material: Material = Material.AIR,
            isTile: Boolean = false,
        ): FakeBlock = blocks.getOrPut(position) { FakeBlock(position, material, isTile) }

        private fun materialFrom(blockData: String): Material = when (blockData.substringBefore('[')) {
            "minecraft:air" -> Material.AIR
            "minecraft:stone" -> Material.STONE
            else -> error("Unexpected block data $blockData")
        }

        private inner class FakeBlock(
            val position: BlockPosition3D,
            var material: Material,
            private val isTile: Boolean,
        ) {
            val paper: Block = proxy(Block::class.java, "block@$position") { method, args ->
                when (method.name) {
                    "getWorld" -> world
                    "getX" -> position.x
                    "getY" -> position.y
                    "getZ" -> position.z
                    "getType" -> material
                    "getBlockData" -> blockData(material)
                    "getState" -> FakeState(paper, material, isTile).paper
                    "isEmpty" -> material == Material.AIR
                    "isSolid" -> material != Material.AIR &&
                        material != Material.WATER &&
                        material != Material.LAVA
                    "isLiquid" -> material == Material.WATER || material == Material.LAVA
                    "getRelative" -> {
                        val face = args[0] as BlockFace
                        blockAt(
                            BlockPosition3D(
                                worldId,
                                position.x + face.modX,
                                position.y + face.modY,
                                position.z + face.modZ,
                            ),
                        ).paper
                    }
                    "setBlockData" -> {
                        material = (args[0] as BlockData).material
                        null
                    }
                    else -> unexpected(method)
                }
            }
        }

        private inner class FakeState(
            private val block: Block,
            private val material: Material,
            isTile: Boolean,
        ) {
            val paper: BlockState = proxy(
                if (isTile) TileState::class.java else BlockState::class.java,
                "state:${material.name}",
            ) { method, _ ->
                when (method.name) {
                    "getBlock" -> block
                    "getType" -> material
                    "getBlockData" -> blockData(material)
                    "update" -> {
                        setMaterial(block, material)
                        true
                    }
                    else -> unexpected(method)
                }
            }
        }

        private fun blockData(material: Material): BlockData =
            proxy(BlockData::class.java, "data:${material.name}") { method, _ ->
                when (method.name) {
                    "getMaterial" -> material
                    "getAsString" -> when (material) {
                        Material.AIR -> "minecraft:air"
                        Material.STONE -> "minecraft:stone"
                        Material.CHEST -> "minecraft:chest[facing=north,type=single,waterlogged=false]"
                        else -> "minecraft:${material.name.lowercase()}"
                    }
                    else -> unexpected(method)
                }
            }

        private fun prepared(request: PrepareBlockMutation): PreparedBlockMutation =
            PreparedBlockMutation(
                journalEntry = BattleBlockChange(
                    id = BlockChangeId(UUID(0, 6)),
                    seasonId = seasonId,
                    battleId = request.battleId,
                    claimId = request.claimId,
                    position = request.position,
                    originalState = request.observedState,
                    firstMutationCause = request.cause,
                    firstActorId = request.actorId,
                    recordedAt = instant,
                ),
                expectedCurrentState = request.observedState,
                actorId = request.actorId,
                cause = request.cause,
                relationship = JournalActorRelationship.OPPONENT,
                preparedAt = instant,
                capturedOriginalState = true,
            )
    }

    private data class ParsedMetrics(
        val applied: Long,
        val stale: Long,
    )

    private companion object {
        val worldId = WorldId("minecraft:overworld")
        val seasonId = SeasonId(UUID(0, 1))
        val civilizationId = CivilizationId(UUID(0, 2))
        val battleId = BattleId(UUID(0, 3))
        val claimId = ClaimId(UUID(0, 4))
        val actorId = PlayerId(UUID(0, 5))
        val instant = Instant.parse("2026-08-18T12:00:00Z")

        fun unexpected(method: Method): Nothing =
            error("Unexpected Paper call ${method.declaringClass.simpleName}.${method.name}")

        @Suppress("UNCHECKED_CAST")
        fun <T> proxy(
            type: Class<T>,
            label: String,
            handler: (Method, Array<out Any?>) -> Any?,
        ): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, rawArgs ->
            val args = rawArgs ?: emptyArray()
            when (method.name) {
                "toString" -> label
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args.singleOrNull()
                else -> handler(method, args)
            }
        } as T
    }
}
