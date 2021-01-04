package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimToggleables
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import org.mineacademy.fo.region.Region
import java.util.*
import java.util.stream.Collectors

data class Plot(val civ: Civilization, val id: Int, val region: Region) : ConfigSerializable {
    var price = 0.0
    lateinit var owner: CivPlayer
    var forSale = false
    var members: MutableSet<CivPlayer> = HashSet()
    var claimPermissions = ClaimPermissions()
    var claimToggleables = ClaimToggleables()


    fun addMember(player: CivPlayer) {
        members.add(player)
        civ.queueForSaving()
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civilization", civ.uuid)
        map.put("Region", region)
        map.put("ID", id)
        map.putIfExist("Owner", owner?.playerUUID)
        map.put("Price", price)
        map.putIfExist("For_Sale", forSale)
        map.putIfExist("Members", members.stream().map(CivPlayer::playerUUID).collect(Collectors.toSet()))
        map.put("Permissions", claimPermissions)
        map.put("Toggleables", claimToggleables)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Plot {
            val civ = Civilization.fromUUID(map["Civilization", UUID::class.java])
            val region = map.get("Region", Region::class.java)
            val id = map.getInteger("ID")
            val plot = Plot(civ, id, region)
            val owner = CivPlayer.fromUUID(map["Owner", UUID::class.java])
            val price = map.getInteger("Price")
            val forSale = map.getBoolean("For_Sale")
            val members: MutableSet<CivPlayer> =
                map.getSet("Members", UUID::class.java).stream().map(CivPlayer::fromUUID).collect(Collectors.toSet())
            val claimPermissions = map.get("Permissions", ClaimPermissions::class.java)
            val claimToggleables = map.get("Toggleables", ClaimToggleables::class.java)
            if (owner != null) plot.owner = owner
            plot.price = price.toDouble()
            plot.forSale = forSale
            plot.members = members
            plot.claimPermissions = claimPermissions
            plot.claimToggleables = claimToggleables
            return plot
        }
    }
}