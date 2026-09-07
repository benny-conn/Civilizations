package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.*

class PaperPortalListenerTest {
    private class Fixture(owned: Boolean=true) {
        val plugin=mock(JavaPlugin::class.java)
        val server=mock(Server::class.java)
        val scheduler=mock(BukkitScheduler::class.java)
        val tasks=mutableListOf<Runnable>()
        val sites=mock(PaperPortalSites::class.java)
        val world=mock(World::class.java)
        val target=mock(World::class.java)
        val chunk=mock(Chunk::class.java)
        val player=mock(Player::class.java)
        val a=PortalSite(WorldId("minecraft:overworld"),UUID(0,1),ResourceBounds(0,64,0,1,66,0))
        val b=PortalSite(WorldId("minecraft:the_nether"),UUID(0,2),ResourceBounds(32,64,0,33,66,0))
        val pair=PortalPair("crossing",a,b)
        val network=PortalNetwork(SeasonId(UUID(0,3)),listOf(pair),"a".repeat(64),"console",Instant.EPOCH)
        var state: CivilizationsRuntimeState= CivilizationsRuntimeState.Ready(null,portals=network)
        val destination=Location(target,33.5,64.0,2.5)
        val listener: PaperPortalListener
        init {
            `when`(plugin.server).thenReturn(server);`when`(plugin.isEnabled).thenReturn(true)
            `when`(server.scheduler).thenReturn(scheduler)
            doAnswer { tasks.add(it.getArgument(1));mock(BukkitTask::class.java) }.`when`(scheduler).runTask(eq(plugin),any(Runnable::class.java))
            `when`(world.key).thenReturn(NamespacedKey.minecraft("overworld"));`when`(world.uid).thenReturn(a.uuid)
            `when`(target.uid).thenReturn(b.uuid)
            `when`(player.uniqueId).thenReturn(UUID(0,10));`when`(player.world).thenReturn(world)
            `when`(player.location).thenReturn(Location(world,0.5,64.0,0.5))
            `when`(player.isValid).thenReturn(true);`when`(player.passengers).thenReturn(emptyList())
            `when`(player.teleport(destination,TeleportCause.PLUGIN)).thenReturn(true)
            `when`(sites.world(b)).thenReturn(target);`when`(sites.chunks(b)).thenReturn(setOf(2 to 0))
            `when`(sites.destination(b)).thenReturn(destination)
            `when`(chunk.world).thenReturn(target);`when`(chunk.x).thenReturn(2)
            `when`(chunk.addPluginChunkTicket(plugin)).thenReturn(owned)
            `when`(target.getChunkAtAsync(2,0,false)).thenReturn(CompletableFuture.completedFuture(chunk))
            listener=PaperPortalListener(plugin,{state},sites)
        }
        fun event(cause: TeleportCause=TeleportCause.NETHER_PORTAL)=PlayerPortalEvent(player,player.location,Location(target,0.0,64.0,0.0),cause)
    }
    @Test fun `player crossing cancels vanilla search then uses fixed landing after snapshot refresh`() {
        val f=Fixture();val event=f.event();f.listener.onPlayer(event)
        assertTrue(event.isCancelled)
        verify(f.player,never()).teleport(f.destination,TeleportCause.PLUGIN)
        f.state=CivilizationsRuntimeState.Ready(null,portals=PortalNetwork(f.network.seasonId,listOf(f.pair),f.network.sourceSha256,"console",Instant.EPOCH))
        f.tasks.toList().forEach(Runnable::run)
        verify(f.player).teleport(f.destination,TeleportCause.PLUGIN)
        verify(f.player).portalCooldown=100
        verify(f.chunk).removePluginChunkTicket(f.plugin)
    }
    @Test fun `moving away while loading rejects and releases chunks`() {
        val f=Fixture();f.listener.onPlayer(f.event())
        `when`(f.player.location).thenReturn(Location(f.world,100.0,64.0,0.0))
        f.tasks.toList().forEach(Runnable::run)
        verify(f.player,never()).teleport(f.destination,TeleportCause.PLUGIN)
        verify(f.chunk).removePluginChunkTicket(f.plugin)
    }
    @Test fun `does not remove a preexisting plugin ticket or route End travel`() {
        val f=Fixture(false)
        val end=f.event(TeleportCause.END_PORTAL);f.listener.onPlayer(end);assertFalse(end.isCancelled)
        f.listener.onPlayer(f.event());f.tasks.toList().forEach(Runnable::run)
        verify(f.chunk,never()).removePluginChunkTicket(f.plugin)
    }
    @Test fun `missing chunks and shutdown do not teleport or create chunks`() {
        val f=Fixture()
        `when`(f.target.getChunkAtAsync(2,0,false)).thenReturn(CompletableFuture.failedFuture(IllegalStateException("missing")))
        f.listener.onPlayer(f.event());f.tasks.toList().forEach(Runnable::run)
        verify(f.player,never()).teleport(f.destination,TeleportCause.PLUGIN)
        verify(f.target,never()).getChunkAtAsync(2,0,true)
        val g=Fixture();g.listener.onPlayer(g.event());g.listener.close();g.tasks.toList().forEach(Runnable::run)
        verify(g.player,never()).teleport(g.destination,TeleportCause.PLUGIN)
    }
}
