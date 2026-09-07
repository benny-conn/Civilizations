package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.PortalNetwork
import io.bennyc.civilizations.application.scarcity.PortalSite
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import net.kyori.adventure.text.Component
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import org.bukkit.event.world.PortalCreateEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class PaperPortalListener(private val plugin: JavaPlugin, private val state: () -> CivilizationsRuntimeState, private val sites: PaperPortalSites = PaperPortalSites(plugin.server)) : Listener, AutoCloseable {
    private val pending=mutableMapOf<UUID,MutableList<Chunk>>()
    private data class Ticket(val world: UUID,val x: Int,val z: Int)
    private fun ticket(c: Chunk)=Ticket(c.world.uid,c.x,c.z)
    private data class Retention(val users: Int,val owned: Boolean)
    private val ticketUsers=mutableMapOf<Ticket,Retention>()
    private var closed=false
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onCreate(event: PortalCreateEvent) {
        if(event.reason == PortalCreateEvent.CreateReason.END_PLATFORM) return
        val ready=state() as? CivilizationsRuntimeState.Ready
        if(ready==null) { event.isCancelled=true;return }
        val network=ready.portals ?: return
        val proposed=event.blocks.filter { it.type==Material.NETHER_PORTAL }.map { Triple(it.x,it.y,it.z) }.toSet()
        val candidates=network.pairs.flatMap { listOf(it.first,it.second) }.filter { it.world.value==event.world.key.asString() && it.uuid==event.world.uid }
        val match=candidates.any { site ->
            val cells=sites.cells(site,true)
            proposed==cells.filter { it.interior }.map { Triple(it.x,it.y,it.z) }.toSet() &&
                event.blocks.all { b -> cells.any { it.x==b.x && it.y==b.y && it.z==b.z } } && sites.problem(site,false)==null
        }
        if(!match) { event.isCancelled=true; message(event.entity,"Nether portals can only be lit at registered sites with a clear exit.") }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onPlayer(event: PlayerPortalEvent) {
        if(event.cause!=TeleportCause.NETHER_PORTAL) return
        val ready=state() as? CivilizationsRuntimeState.Ready
        if(ready!=null && ready.portals==null) return
        event.isCancelled=true
        ready?.portals?.let { travel(event.player,it) }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onEntity(event: EntityPortalEvent) {
        if(event.portalType != org.bukkit.PortalType.NETHER) return
        val ready=state() as? CivilizationsRuntimeState.Ready
        if(ready!=null && ready.portals==null) return
        event.isCancelled=true
        ready?.portals?.let { travel(event.entity,it) }
    }
    private fun travel(entity: Entity, network: PortalNetwork) {
        if(closed || pending.containsKey(entity.uniqueId)) return
        if(pending.size>=32) { message(entity,"Portal busy; try again shortly.");return }
        val l=entity.location
        val route=network.route(WorldId(entity.world.key.asString()),entity.world.uid,l.blockX,l.blockY,l.blockZ)
        if(route==null) { message(entity,"This Nether portal is not a registered crossing.");return }
        if(entity.isInsideVehicle || entity.passengers.isNotEmpty() || entity.width>2 || entity.height>3) { message(entity,"Dismount and cross separately; this entity does not fit the portal exit.");return }
        if(sites.problem(route.first)!=null) { message(entity,"Source portal is damaged or its exit is blocked.");return }
        val world=sites.world(route.second) ?: run { message(entity,"Destination world is unavailable.");return }
        val chunks=sites.chunks(route.second).toList()
        pending[entity.uniqueId]=mutableListOf()
        var remaining=chunks.size
        var failed=false
        chunks.forEach { (x,z) ->
            world.getChunkAtAsync(x,z,false).whenComplete { chunk,error ->
                if(!plugin.isEnabled || closed) return@whenComplete
                plugin.server.scheduler.runTask(plugin,Runnable {
                    val lease=pending[entity.uniqueId] ?: return@Runnable
                    if(error!=null || chunk==null) failed=true else { retain(chunk);lease.add(chunk) }
                    remaining--
                    if(remaining==0) {
                        try {
                            if(!failed && entity.isValid && !entity.isDead && (state() as? CivilizationsRuntimeState.Ready)?.portals?.sourceSha256 == network.sourceSha256 && stillAt(entity,route.first) && sites.problem(route.first)==null && sites.problem(route.second)==null) {
                                if(entity.teleport(sites.destination(route.second),TeleportCause.PLUGIN)) entity.portalCooldown=100
                                else message(entity,"Portal travel was cancelled.")
                            } else message(entity,"Portal destination is missing, damaged, or obstructed; repair/clear the registered site.")
                        } finally { release(entity.uniqueId) }
                    }
                })
            }
        }
    }
    private fun stillAt(e: Entity,s: PortalSite): Boolean {
        val l=e.location
        return e.world.uid==s.uuid && e.world.key.asString()==s.world.value && s.near(l.blockX,l.blockY,l.blockZ)
    }
    private fun retain(chunk: Chunk) {
        val key=ticket(chunk)
        val old=ticketUsers[key]
        ticketUsers[key]=if(old==null) Retention(1,chunk.addPluginChunkTicket(plugin)) else old.copy(users=old.users+1)
    }
    private fun release(id: UUID) { pending.remove(id)?.forEach {
        val key=ticket(it)
        val old=ticketUsers.getValue(key)
        if(old.users==1) { ticketUsers.remove(key);if(old.owned) it.removePluginChunkTicket(plugin) }
        else ticketUsers[key]=old.copy(users=old.users-1)
    } }
    override fun close() { closed=true;pending.keys.toList().forEach(::release) }
    private fun message(e: Entity?,text: String) { (e as? Player)?.sendMessage(Component.text(text)) }
}
