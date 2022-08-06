package io.bennyc.civilizations.menu

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.permissions.PermissionType
import io.bennyc.civilizations.permissions.Rank
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.mineacademy.fo.menu.Menu
import org.mineacademy.fo.menu.button.Button
import org.mineacademy.fo.menu.model.ItemCreator
import org.mineacademy.fo.remain.CompMaterial

class RankMenu(rank: Rank, civilization: Civilization, parent: Menu?) : Menu(parent) {

    private val nameButton: Button
    private val buildButton: Button
    private val breakButton: Button
    private val switchButton: Button
    private val interactButton: Button

    private val emptyButton: Button.DummyButton =
        Button.makeDummy(ItemCreator.of(CompMaterial.GRAY_STAINED_GLASS_PANE).name(" "))


    override fun getItemAt(slot: Int): ItemStack {
        return when (slot) {
            9 + 2 -> nameButton.item
            1 -> buildButton.item
            3 -> breakButton.item
            5 -> switchButton.item
            7 -> interactButton.item
            else -> emptyButton.item
        }
    }

    override fun getInfo(): Array<String> {
        return arrayOf("Adjust rank settings.")
    }

    init {
        size = 9 * 2

        nameButton = object : Button() {
            override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                if (civilization.permissions.defaultRank == rank || civilization.permissions.outsiderRank == rank || civilization.permissions.allyRank == rank || civilization.permissions.enemyRank == rank) return
                io.bennyc.civilizations.conversation.RankRenameConversation(rank, civilization, player).start(player)
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(CompMaterial.OAK_SIGN, "&6&lRename").make()
            }
        }
        buildButton = object : Button() {
            override fun onClickedInMenu(p0: Player?, p1: Menu?, p2: ClickType?) {
                if (rank.permissions.contains(PermissionType.BUILD))
                    rank.permissions.remove(PermissionType.BUILD)
                else
                    rank.permissions.add(PermissionType.BUILD)
                restartMenu()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(
                    CompMaterial.BRICK_STAIRS,
                    "&2&lBuild: " + if (rank.permissions.contains(PermissionType.BUILD)) "&aON" else "&cOFF"
                ).make()
            }
        }
        breakButton = object : Button() {
            override fun onClickedInMenu(p0: Player?, p1: Menu?, p2: ClickType?) {
                if (rank.permissions.contains(PermissionType.BREAK))
                    rank.permissions.remove(PermissionType.BREAK)
                else
                    rank.permissions.add(PermissionType.BREAK)
                restartMenu()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(
                    CompMaterial.GOLDEN_PICKAXE,
                    "&c&lBreak: " + if (rank.permissions.contains(PermissionType.BREAK)) "&aON" else "&cOFF"
                ).make()
            }
        }
        switchButton = object : Button() {
            override fun onClickedInMenu(p0: Player?, p1: Menu?, p2: ClickType?) {
                if (rank.permissions.contains(PermissionType.SWITCH))
                    rank.permissions.remove(PermissionType.SWITCH)
                else
                    rank.permissions.add(PermissionType.SWITCH)
                restartMenu()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(
                    CompMaterial.LEVER,
                    "&e&lSwitch: " + if (rank.permissions.contains(PermissionType.SWITCH)) "&aON" else "&cOFF"
                ).make()
            }
        }
        interactButton = object : Button() {
            override fun onClickedInMenu(p0: Player?, p1: Menu?, p2: ClickType?) {
                if (rank.permissions.contains(PermissionType.INTERACT))
                    rank.permissions.remove(PermissionType.INTERACT)
                else
                    rank.permissions.add(PermissionType.INTERACT)

                restartMenu()
            }

            override fun getItem(): ItemStack {
                return ItemCreator.of(
                    CompMaterial.CHEST,
                    "&b&lInteract: " + if (rank.permissions.contains(PermissionType.INTERACT)) "&aON" else "&cOFF"
                ).make()
            }
        }


    }

}