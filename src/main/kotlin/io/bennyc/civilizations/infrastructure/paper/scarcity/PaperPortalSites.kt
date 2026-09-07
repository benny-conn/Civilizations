package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.PortalSite
import org.bukkit.Axis
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.data.Orientable

/** Bounded live inspection; callers load existing chunks before travel. */
class PaperPortalSites(private val server: Server) {
    data class Cell(val x: Int,val y: Int,val z: Int,val interior: Boolean)
    fun world(site: PortalSite): World? = server.worlds.singleOrNull { it.key.asString()==site.world.value && it.uid==site.uuid }
    fun cells(site: PortalSite, corners: Boolean = false): List<Cell> {
        val b=site.bounds
        val width=if(site.alongX) b.maxX-b.minX+1 else b.maxZ-b.minZ+1
        return buildList {
            for (u in -1..width) for(y in b.minY-1..b.maxY+1) {
                if (!corners && (u == -1 || u == width) && (y == b.minY-1 || y == b.maxY+1)) continue // Vanilla corners are optional.
                add(Cell(b.minX+if(site.alongX) u else 0,y,b.minZ+if(site.alongX) 0 else u,u in 0 until width && y in b.minY..b.maxY))
            }
        }
    }
    fun chunks(site: PortalSite): Set<Pair<Int,Int>> = buildSet {
        cells(site).forEach { add((it.x shr 4) to (it.z shr 4)) }
        for(x in site.exitX-1..site.exitX+1) for(z in site.exitZ-1..site.exitZ+1) add((x shr 4) to (z shr 4))
    }
    fun problem(site: PortalSite, lit: Boolean = true): String? {
        val world=world(site) ?: return "world missing or UUID changed"
        if(chunks(site).any { !world.isChunkLoaded(it.first,it.second) }) return "chunks unloaded"
        for(c in cells(site)) {
            if(c.y < world.minHeight || c.y >= world.maxHeight) return "frame outside build height"
            if(!world.worldBorder.isInside(Location(world,c.x+0.5,c.y.toDouble(),c.z+0.5))) return "frame outside world border"
            val block=world.getBlockAt(c.x,c.y,c.z)
            if(!c.interior && block.type != Material.OBSIDIAN) return "obsidian frame incomplete"
            if(c.interior && lit && (block.type != Material.NETHER_PORTAL || (block.blockData as? Orientable)?.axis != if(site.alongX) Axis.X else Axis.Z)) return "portal unlit or wrong shape/axis"
        }
        for(x in site.exitX-1..site.exitX+1) for(z in site.exitZ-1..site.exitZ+1) {
            if(site.exitY-1 < world.minHeight || site.exitY+2 >= world.maxHeight) return "exit outside build height"
            val floor=world.getBlockAt(x,site.exitY-1,z)
            if(!floor.isSolid || floor.type in setOf(Material.MAGMA_BLOCK,Material.CACTUS,Material.CAMPFIRE,Material.SOUL_CAMPFIRE)) return "exit needs a safe solid 3x3 floor"
            for(y in site.exitY..site.exitY+2) {
                if(!world.worldBorder.isInside(Location(world,x+0.5,y.toDouble(),z+0.5))) return "exit outside world border"
                if(!world.getBlockAt(x,y,z).type.isAir) return "exit needs 3x3x3 clear air"
            }
        }
        return null
    }
    fun destination(site: PortalSite) = Location(requireNotNull(world(site)),site.exitX+0.5,site.exitY.toDouble(),site.exitZ+0.5,if(site.alongX) 0f else -90f,0f)
}
