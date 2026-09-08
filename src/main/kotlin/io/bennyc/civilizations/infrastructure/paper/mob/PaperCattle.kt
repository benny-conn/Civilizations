package io.bennyc.civilizations.infrastructure.paper.mob

import io.bennyc.civilizations.application.*
import io.bennyc.civilizations.application.mob.*
import io.bennyc.civilizations.infrastructure.runtime.*
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.command.CommandSender
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerUnleashEntityEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.event.world.EntitiesUnloadEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.util.UUID
import java.util.function.Consumer

/** Main-thread coordinator. SQL work uses the runtime's single worker; hot events read entries only. */
class PaperCattle(private val plugin: JavaPlugin, private val runtime: CivilizationsRuntime, private val rules: MobRules) : Listener, BasicCommand, AutoCloseable {
    private val server=plugin.server
    private val idKey=NamespacedKey(plugin,"mob-id")
    private val opKey=NamespacedKey(plugin,"mob-creation")
    private val versionKey=NamespacedKey(plugin,"mob-version")
    private val holdKey=NamespacedKey(plugin,"mob-hold")
    private data class View(val mob: ManagedMob?, val reserved: Boolean, val death: MobDeath?)
    private class Entry(var view: View?=null, var fetching: Boolean=false, var busy: Boolean=false)
    private val entries=linkedMapOf<UUID,Entry>()
    private val allowedSpawns=hashSetOf<UUID>()
    private data class Rewards(val items: List<org.bukkit.inventory.ItemStack>, val xp: Int)
    private val completing=hashMapOf<UUID,Rewards>()
    private val chunks=ArrayDeque<Pair<UUID,Pair<Int,Int>>>()
    private var activation: CattleActivation?=null
    private var initialized=false
    private var initializing=false
    private var work=0
    private var cursor=0
    private var closed=false
    private val task=server.scheduler.runTaskTimer(plugin,Runnable { tick() },1,1)
    override fun permission()="civilizations.admin"
    private fun tell(sender: CommandSender,text: String)=sender.sendMessage(Component.text(text))
    private fun cow(id: UUID)= (server.getEntity(id) as? Cow)?.takeIf { it.type==EntityType.COW }
    private fun managedType(e: Entity)=e.type==EntityType.COW
    private fun id(c: Cow)=c.persistentDataContainer.get(idKey,PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private fun markerMatches(c: Cow,m: ManagedMob)=id(c)==m.id && c.persistentDataContainer.get(opKey,PersistentDataType.STRING)==m.creation.operationId.toString() && c.persistentDataContainer.get(versionKey,PersistentDataType.INTEGER)==1
    private fun active()=initialized && activation!=null
    private fun ready(c: Cow): ManagedMob? {
        if(!active() || runtime.state !is CivilizationsRuntimeState.Ready) return null
        val state=runtime.state as? CivilizationsRuntimeState.Ready ?: return null
        if(state.worldManifests.none { it.manifest.seasonId==activation?.seasonId && it.manifest.worldUuid==c.world.uid && it.manifest.worldId.value==c.world.key.asString() }) return null
        val e=entries[c.uniqueId] ?: return null
        val m=e.view?.mob ?: return null
        if(e.busy || e.fetching || e.view?.reserved==true || !markerMatches(c,m) || m.observe(c.uniqueId,MobSpecies("minecraft:cow"))!=MobObservation.MANAGED) return null
        return m
    }
    private fun hold(c: Cow) {
        val p=c.persistentDataContainer
        if(!p.has(holdKey)) p.set(holdKey,PersistentDataType.STRING,listOf(c.hasAI(),c.hasGravity(),c.isInvulnerable,c.isSilent,c.ageLock,c.isCollidable).joinToString(","))
        c.setAI(false);c.setGravity(false);c.isInvulnerable=true;c.isSilent=true;c.isCollidable=false;c.ageLock=true;c.loveModeTicks=0
        c.velocity=org.bukkit.util.Vector()
    }
    private fun release(c: Cow) {
        val p=c.persistentDataContainer
        val old=p.get(holdKey,PersistentDataType.STRING)?.split(",") ?: return
        if(old.size==6) { c.setAI(old[0].toBoolean());c.setGravity(old[1].toBoolean());c.isInvulnerable=old[2].toBoolean();c.isSilent=old[3].toBoolean();c.ageLock=old[4].toBoolean();c.isCollidable=old[5].toBoolean() }
        p.remove(holdKey)
    }
    private fun applyAge(c: Cow,m: ManagedMob) {
        c.ageLock=true
        c.age=when { m.matureAt>Instant.now() -> -24000; m.breedAfter>Instant.now() -> 6000; else -> 0 }
        c.isPersistent=true;c.removeWhenFarAway=false
    }
    private fun track(c: Cow) {
        if(initialized && activation==null) { release(c);return }
        if(!entries.containsKey(c.uniqueId) && entries.size>=4096) { hold(c);return }
        entries.getOrPut(c.uniqueId) { Entry() }
        if(ready(c)==null) hold(c)
    }
    private fun scanLoaded() {
        chunks.clear()
        for(w in server.worlds) for(c in w.loadedChunks) {
            if(chunks.size>=8192) { plugin.logger.warning("Cattle startup chunk queue full; reconcile overflow entities explicitly");return }
            chunks.add(w.uid to (c.x to c.z))
        }
    }
    private fun <T> submit(operation: RuntimeMutationScope.()->ApplicationResult<T>, completion: (ApplicationResult<T>?)->Unit): Boolean {
        if(closed || work>=32) return false
        work++
        runtime.submitMobOperation(operation) { out ->
            work--
            if(!closed) completion((out as? RuntimeMutationOutcome.Completed)?.result)
        }
        return true
    }
    private fun tick() {
        if(closed || runtime.state !is CivilizationsRuntimeState.Ready) return
        if(!initialized) {
            if(!initializing) {
                initializing=true
                if(!submit({ ApplicationResult.Applied(repository.read { findCattleActivation() }) }) { result ->
                    initializing=false
                    if(result is ApplicationResult.Applied) { activation=result.value;initialized=true;scanLoaded() }
                }) initializing=false
            }
            return
        }
        repeat(2) {
            val next=chunks.removeFirstOrNull() ?: return@repeat
            val w=server.getWorld(next.first)
            if(w!=null && w.isChunkLoaded(next.second.first,next.second.second)) w.getChunkAt(next.second.first,next.second.second).entities.filterIsInstance<Cow>().filter { managedType(it) }.forEach(::track)
        }
        if(activation==null) { entries.keys.mapNotNull(::cow).forEach(::release);entries.clear();return }
        val keys=entries.keys.toList()
        repeat(minOf(16,keys.size)) {
            if(keys.isEmpty()) return@repeat
            val entityId=keys[(cursor++)%keys.size];if(cursor==Int.MAX_VALUE) cursor=0
            val c=cow(entityId)
            if(c==null) { entries.remove(entityId);return@repeat }
            val e=entries[entityId] ?: return@repeat
            if(e.busy) return@repeat
            if(e.view==null && !e.fetching) refresh(c)
            val m=ready(c)
            if(m==null) hold(c) else { release(c);applyAge(c,m) }
        }
    }
    private fun refresh(c: Cow) {
        val entity=c.uniqueId;val logical=id(c);val e=entries[entity] ?: return
        if(e.fetching) return
        e.fetching=true
        if(!submit({ ApplicationResult.Applied(repository.read {
            val m=logical?.let(::findManagedMob)
            View(m,logical?.let(::hasMobReservation) ?: false,logical?.let(::findMobDeathForMob))
        }) }) { result ->
            e.fetching=false
            if(result is ApplicationResult.Applied) {
                e.view=result.value
                val live=cow(entity) ?: return@submit
                val m=result.value.mob
                if(m!=null && markerMatches(live,m)) {
                    if(m.life==MobLife.DEAD) { live.remove();entries.remove(entity) }
                    else if(m.life==MobLife.APPLYING && !e.busy) acknowledge(live,m)
                }
            }
        }) e.fetching=false
    }
    private fun invalidate(vararg ids: UUID?) {
        ids.filterNotNull().forEach { logical -> entries.values.filter { it.view?.mob?.id==logical }.forEach { it.view=null } }
    }
    private fun acknowledge(c: Cow,m: ManagedMob) {
        val entity=c.uniqueId;val world=c.world.uid;val e=entries[entity] ?: return
        e.busy=true;hold(c)
        if(!submit({ mobs.acknowledgeSpawn(m.creation.operationId,entity,MobSpecies("minecraft:cow"),world) }) { result ->
            e.busy=false
            if(result is ApplicationResult.Applied || result is ApplicationResult.Unchanged) {
                invalidate(m.creation.parentA,m.creation.parentB)
                e.view=null;cow(entity)?.let(::refresh)
            }
        }) e.busy=false
    }
    private fun safe(seed: CattleSeed): Location? {
        val w=server.getWorld(seed.worldUuid) ?: return null
        val state=runtime.state as? CivilizationsRuntimeState.Ready ?: return null
        if(state.worldManifests.none { it.manifest.worldUuid==w.uid && it.manifest.worldId.value==w.key.asString() }) return null
        if(seed.y<=w.minHeight || seed.y+1>=w.maxHeight || !w.isChunkLoaded(seed.x shr 4,seed.z shr 4)) return null
        if(!w.getBlockAt(seed.x,seed.y-1,seed.z).type.isSolid || !w.getBlockAt(seed.x,seed.y,seed.z).isEmpty || !w.getBlockAt(seed.x,seed.y+1,seed.z).isEmpty) return null
        return Location(w,seed.x+0.5,seed.y.toDouble(),seed.z+0.5)
    }
    private fun spawn(m: ManagedMob, location: Location) {
        var created: Cow?=null
        try {
            location.world.spawn(location,Cow::class.java,Consumer { c ->
                created=c
                c.persistentDataContainer.set(idKey,PersistentDataType.STRING,m.id.toString())
                c.persistentDataContainer.set(opKey,PersistentDataType.STRING,m.creation.operationId.toString())
                c.persistentDataContainer.set(versionKey,PersistentDataType.INTEGER,1)
                c.isPersistent=true;c.removeWhenFarAway=false;hold(c);applyAge(c,m)
                allowedSpawns.add(c.uniqueId)
            })
            val c=created
            if(c!=null && c.isValid) { track(c);acknowledge(c,m) }
        } finally { created?.let { allowedSpawns.remove(it.uniqueId) } }
    }
    private fun seed(slot: CattleSeed,sender: CommandSender) {
        val a=activation ?: return
        if(safe(slot)==null) { tell(sender,"${slot.id}: load chunk and provide solid ground plus two clear blocks");return }
        if(!submit({ mobs.prepare(a.request(slot)) }) { prepared ->
            val m=when(prepared) { is ApplicationResult.Applied -> prepared.value;is ApplicationResult.Unchanged -> prepared.value;else -> null }
            if(m==null) { tell(sender,"${slot.id}: prepare rejected/unavailable");return@submit }
            if(m.life!=MobLife.PREPARED) { tell(sender,"${slot.id}: ${m.life}; no replacement spawned");return@submit }
            if(!submit({ mobs.beginSpawn(m.creation.operationId) }) { begun ->
                if(begun is ApplicationResult.Applied) {
                    val loc=safe(slot)
                    if(loc!=null) spawn(begun.value,loc)
                    tell(sender,"${slot.id}: apply attempted; inspect status for confirmation")
                }
            }) tell(sender,"Busy; repeat seed with the same fixed slots")
        }) tell(sender,"Cattle worker full; retry seed")
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onSpawn(e: CreatureSpawnEvent) {
        if(managedType(e.entity) && (!initialized || active()) && e.entity.uniqueId !in allowedSpawns) e.isCancelled=true
    }
    @EventHandler fun onLoad(e: EntitiesLoadEvent) { e.entities.filterIsInstance<Cow>().filter { managedType(it) }.forEach(::track) }
    @EventHandler fun onUnload(e: EntitiesUnloadEvent) { e.entities.forEach { entries.remove(it.uniqueId) } }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onInteract(e: PlayerInteractEntityEvent) {
        val c=(e.rightClicked as? Cow)?.takeIf(::managedType) ?: return
        if(initialized && activation==null) return
        track(c)
        val m=ready(c)
        val food=e.player.inventory.getItem(e.hand).type==Material.WHEAT
        if(m==null || food && (m.matureAt>Instant.now() || m.breedAfter>Instant.now())) e.isCancelled=true
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onInteractAt(e: PlayerInteractAtEntityEvent) = onInteract(e)
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onLeash(e: PlayerLeashEntityEvent) {
        val c=(e.entity as? Cow)?.takeIf(::managedType) ?: return
        if((!initialized || active()) && ready(c)==null) e.isCancelled=true
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onUnleash(e: PlayerUnleashEntityEvent) {
        val c=(e.entity as? Cow)?.takeIf(::managedType) ?: return
        if((!initialized || active()) && ready(c)==null) e.isCancelled=true
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onPortal(e: EntityPortalEvent) = onTeleport(e)
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onTeleport(e: EntityTeleportEvent) {
        val c=(e.entity as? Cow)?.takeIf(::managedType) ?: return
        if((!initialized || active()) && ready(c)==null) e.isCancelled=true
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onDamage(e: EntityDamageEvent) {
        val c=(e.entity as? Cow)?.takeIf(::managedType) ?: return
        if(initialized && activation==null || c.uniqueId in completing) return
        track(c);if(ready(c)==null) e.isCancelled=true
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onTransform(e: EntityTransformEvent) {
        if((!initialized || active()) && (managedType(e.entity) || e.transformedEntities.any(::managedType))) e.isCancelled=true
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onBreed(e: EntityBreedEvent) {
        if(!managedType(e.entity) || initialized && activation==null) return
        e.isCancelled=true
        val a=e.mother as? Cow ?: return;val b=e.father as? Cow ?: return
        val ma=ready(a) ?: return;val mb=ready(b) ?: return
        val activation=activation ?: return
        val request=MobCreation(UUID.randomUUID(),UUID.randomUUID(),activation.seasonId,a.world.uid,MobSpecies("minecraft:cow"),activation.rules,"breeding","Native cattle breeding",ma.id,mb.id)
        val aid=a.uniqueId;val bid=b.uniqueId
        entries[aid]?.busy=true;entries[bid]?.busy=true;hold(a);hold(b)
        fun releaseParents() { entries[aid]?.let { it.busy=false;it.view=null };entries[bid]?.let { it.busy=false;it.view=null } }
        if(!submit({ mobs.prepare(request) }) { result ->
            if(result !is ApplicationResult.Applied) { releaseParents();return@submit }
            val liveA=cow(aid);val liveB=cow(bid)
            if(liveA==null || liveB==null || liveA.world!=liveB.world || liveA.world.uid!=request.worldUuid || liveA.location.distanceSquared(liveB.location)>16) {
                if(!submit({ mobs.cancelPrepared(request.operationId) }) { releaseParents() }) releaseParents()
                return@submit
            }
            if(!submit({ mobs.beginSpawn(request.operationId) }) { begun ->
                val first=cow(aid);val second=cow(bid)
                if(begun is ApplicationResult.Applied && first!=null && second!=null && first.world==second.world && first.world.uid==request.worldUuid && first.location.distanceSquared(second.location)<=16) spawn(begun.value,first.location)
                releaseParents()
            }) releaseParents()
        }) releaseParents()
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    fun onDeath(e: EntityDeathEvent) {
        val c=(e.entity as? Cow)?.takeIf(::managedType) ?: return
        if(initialized && activation==null) return
        completing[c.uniqueId]?.let { e.drops.clear();e.drops.addAll(it.items.map { item -> item.clone() });e.droppedExp=it.xp;return }
        val m=ready(c)
        e.isCancelled=true;e.reviveHealth=1.0
        val rewards=Rewards(e.drops.map { it.clone() },e.droppedExp)
        hold(c)
        if(m==null) return
        val entity=c.uniqueId;val op=UUID.randomUUID();entries[entity]?.busy=true
        if(!submit({ mobs.prepareDeath(op,m.id,"damage","Native cattle lethal damage") }) { prepared ->
            if(prepared !is ApplicationResult.Applied) { entries[entity]?.let { it.busy=false;it.view=null };return@submit }
            if(!submit({ mobs.beginDeath(op) }) { begun ->
                if(begun is ApplicationResult.Applied) {
                    val live=cow(entity)
                    if(live!=null && live.isValid) {
                        completing[entity]=rewards
                        try { live.health=0.0 } finally { completing.remove(entity) }
                        if(live.isDead) submit({ mobs.acknowledgeDeath(op) }) { entries.remove(entity) }
                    }
                }
                entries[entity]?.let { it.busy=false;it.view=null }
            }) entries[entity]?.let { it.busy=false;it.view=null }
        }) entries[entity]?.busy=false
    }
    override fun execute(source: CommandSourceStack,args: Array<out String>) {
        val sender=source.sender
        if(!sender.hasPermission(permission())) return
        if(!initialized) { tell(sender,"Cattle state loading");return }
        when(args.firstOrNull()) {
            "enable" -> {
                if(args.size!=2) { tell(sender,"/civcattle enable <seeds.yml>");return }
                val season=(runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason?.season?.id ?: return
                val file=args[1];val actor=(sender as? Player)?.uniqueId?.toString() ?: "console"
                submit({ try { ApplicationResult.Applied(CattleSettings.seeds(plugin.dataFolder.toPath().resolve("cattle"),file)) } catch(e: Exception) { ApplicationResult.Rejected(MobRejected(e.message ?: "Invalid cattle file")) } }) { parsed ->
                    if(parsed !is ApplicationResult.Applied) { tell(sender,"Rejected: ${(parsed as? ApplicationResult.Rejected)?.failure?.description}");return@submit }
                    if(parsed.value.any { safe(it)==null }) { tell(sender,"Seeds need loaded chunks, solid ground and two clear air blocks");return@submit }
                    submit({ cattle.install(season,rules,parsed.value,actor) }) { saved ->
                        val a=when(saved) { is ApplicationResult.Applied -> saved.value;is ApplicationResult.Unchanged -> saved.value;else -> null }
                        if(a==null) tell(sender,"Rejected: ${(saved as? ApplicationResult.Rejected)?.failure?.description}")
                        else { activation=a;entries.clear();scanLoaded();tell(sender,"Cattle enabled; ${a.seeds.size} fixed seed slots. Run /civcattle seed.") }
                    }
                }
            }
            "seed" -> { val a=activation;if(a==null) tell(sender,"Cattle OFF") else a.seeds.forEach { seed(it,sender) } }
            "status" -> {
                val a=activation
                if(a==null) { tell(sender,"Cattle OFF");return }
                tell(sender,"Cattle ON; season=${a.seasonId}; loaded=${entries.size}; worker=$work/32; quarantine=${entries.keys.count { cow(it)?.let(::ready)==null }}")
                val contained=entries.keys.filter { cow(it)?.let(::ready)==null }.take(20)
                tell(sender,"Contained entity UUIDs (first 20): $contained")
                submit({
                    val counts=mutableMapOf<MobLife,Int>();val unresolved=mutableListOf<UUID>();var after: UUID?=null
                    do { val page=repository.read { listManagedMobs(a.seasonId,after,1000) };page.forEach { m ->
                        if(m.creation.species.key=="minecraft:cow") { counts[m.life]=(counts[m.life] ?: 0)+1;if(m.life in setOf(MobLife.PREPARED,MobLife.APPLYING,MobLife.DEATH_PENDING) && unresolved.size<20) unresolved.add(m.id) }
                    };after=page.lastOrNull()?.id } while(after!=null)
                    ApplicationResult.Applied("Durable $counts; unresolved IDs (first 20)=$unresolved")
                }) { if(it is ApplicationResult.Applied) tell(sender,it.value) }
            }
            "reconcile" -> {
                val uuid=args.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val c=uuid?.let(::cow)
                if(c==null) { tell(sender,"/civcattle reconcile <loaded entity UUID>; absence is not death");return }
                track(c);entries[c.uniqueId]?.view=null;refresh(c);tell(sender,"Identity refresh queued; pending deaths remain contained. Use settle-death for confirmed loaded pending deaths.")
            }
            "settle-death" -> {
                val uuid=args.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() };val c=uuid?.let(::cow)
                val e=uuid?.let(entries::get);val d=e?.view?.death;val m=e?.view?.mob
                if(c==null || d==null || m==null || e.busy || !markerMatches(c,m)) { tell(sender,"Supply a loaded, reconciled DEATH_PENDING entity UUID");return }
                if(work>=32) { tell(sender,"Cattle worker full; retry settle-death");return }
                e.busy=true;hold(c)
                submit({ mobs.beginDeath(d.operationId) }) { result ->
                    if(result is ApplicationResult.Applied || result is ApplicationResult.Unchanged) {
                        cow(c.uniqueId)?.let { live -> if(markerMatches(live,m)) { live.remove();submit({ mobs.acknowledgeDeath(d.operationId) }) { settled ->
                            if(settled is ApplicationResult.Applied || settled is ApplicationResult.Unchanged) {
                                tell(sender,"Death settled without rewards")
                                plugin.logger.info("Cattle death ${d.operationId} settled without rewards by ${sender.name}")
                            } else tell(sender,"Entity removed; SQL settlement incomplete. Inspect the pending record.")
                            entries.remove(c.uniqueId)
                        } } }
                    }
                    e.busy=false;e.view=null
                }
            }
            else -> tell(sender,"/civcattle enable <file.yml> | seed | status | reconcile <entity UUID> | settle-death <entity UUID>")
        }
    }
    override fun close() { closed=true;task.cancel();entries.clear();chunks.clear();completing.clear() }
}
