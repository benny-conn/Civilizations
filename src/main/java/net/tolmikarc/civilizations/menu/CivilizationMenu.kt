/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.menu

import net.tolmikarc.civilizations.conversation.DepositPrompt
import net.tolmikarc.civilizations.conversation.WithdrawPrompt
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.mineacademy.fo.Common
import org.mineacademy.fo.menu.Menu
import org.mineacademy.fo.menu.MenuPagged
import org.mineacademy.fo.menu.button.Button
import org.mineacademy.fo.menu.button.ButtonMenu
import org.mineacademy.fo.menu.model.ItemCreator
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.remain.CompMaterial
import kotlin.math.roundToInt

class CivilizationMenu(civ: Civ) : Menu() {
    private val infoButton: Button
    private val emptyButton: Button.DummyButton =
        Button.makeDummy(ItemCreator.of(CompMaterial.GRAY_STAINED_GLASS_PANE).name(" "))
    private val itemMenuButton: Button
    private val citizensMenuButton: Button
    private val economyMenuButton: Button
    private val toggleMenuButton: Button
    private val inviteMenuButton: Button


    override fun getItemAt(slot: Int): ItemStack {
        return when (slot) {
            1 -> infoButton.item
            3 -> citizensMenuButton.item
            5 -> economyMenuButton.item
            7 -> toggleMenuButton.item
            9 * 2 + 1 -> itemMenuButton.item
            9 * 2 + 3 -> inviteMenuButton.item
            else -> emptyButton.item
        }
    }

    inner class PermissionsMenu(val civilization: Civ) : Menu(this@CivilizationMenu) {
        override fun getItemAt(slot: Int): ItemStack {
            return super.getItemAt(slot)
        }

        init {

        }
    }


    inner class ToggleMenu(val civilization: Civ) : Menu(this@CivilizationMenu) {
        private val pvp: Button
        private val mobs: Button
        private val explosions: Button
        private val fire: Button
        private val public: Button
        private val inviteOnly: Button


        override fun getItemAt(slot: Int): ItemStack {
            return when (slot) {
                1 -> pvp.item
                3 -> mobs.item
                5 -> explosions.item
                7 -> fire.item
                9 + 2 -> public.item
                9 + 6 -> inviteOnly.item
                else -> emptyButton.item
            }
        }

        init {
            size = 9 * 2
            setInfo("Toggle settings on or off for your Civilization")
            pvp = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.claimToggleables.pvp = !civilization.claimToggleables.pvp
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.IRON_SWORD,
                        "&c&lToggle PVP",
                        "",
                        "PVP: ${civilization.claimToggleables.pvp}"
                    ).build().make()
                }
            }
            mobs = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.claimToggleables.mobs = !civilization.claimToggleables.mobs
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.SPAWNER,
                        "&2&lToggle Mobs Spawning",
                        "",
                        "Mobs: ${civilization.claimToggleables.mobs}"
                    ).build().make()
                }
            }
            explosions = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.claimToggleables.explosion = !civilization.claimToggleables.explosion
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.TNT_MINECART,
                        "&4&lToggle Explosions",
                        "",
                        "Explosions: ${civilization.claimToggleables.explosion}"
                    ).build().make()
                }
            }
            fire = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.claimToggleables.fire = !civilization.claimToggleables.fire
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.CAMPFIRE,
                        "&6&lToggle Fire Spreading",
                        "",
                        "Fire: ${civilization.claimToggleables.fire}"
                    ).build().make()
                }
            }
            public = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.claimToggleables.public = !civilization.claimToggleables.public
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.BIRCH_DOOR,
                        "&b&lToggle Public Home",
                        "",
                        "Public: ${civilization.claimToggleables.public}"
                    ).build().make()
                }
            }
            inviteOnly = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.claimToggleables.inviteOnly = !civilization.claimToggleables.inviteOnly
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.CLOCK,
                        "&3&lToggle Invite Only",
                        "",
                        "Invite Only: ${civilization.claimToggleables.inviteOnly}"
                    ).build().make()
                }
            }


        }


    }


    inner class EconomyMenu(val civilization: Civ) : Menu(this@CivilizationMenu) {
        private val balanceButton: Button
        private val depositButton: Button
        private val withdrawButton: Button

        override fun getItemAt(slot: Int): ItemStack {
            return when (slot) {
                1 -> balanceButton.item
                4 -> depositButton.item
                7 -> withdrawButton.item
                else -> emptyButton.item
            }
        }

        init {
            size = 9
            setInfo("Deposit and withdraw money from your Civilization.")
            balanceButton = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu?, p2: ClickType?) {
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.ENDER_CHEST,
                        "&2&lBalance",
                        "",
                        "${Settings.CURRENCY_SYMBOL}${civilization.bank.balance}"
                    ).build().make()
                }
            }
            depositButton = object : Button() {
                override fun onClickedInMenu(p0: Player, p1: Menu, p2: ClickType) {
                    p0.closeInventory()
                    DepositPrompt(civilization, p0).show(p0)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.EMERALD,
                        "&a&lDeposit",
                    ).build().make()
                }
            }
            withdrawButton = object : Button() {
                override fun onClickedInMenu(p0: Player, p1: Menu, p2: ClickType) {
                    p0.closeInventory()
                    WithdrawPrompt(civilization, p0).show(p0)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.REDSTONE_BLOCK,
                        "&c&lWithdraw",
                    ).build().make()
                }
            }


        }
    }


    inner class CitizensMenu(val civilization: Civ) :
        MenuPagged<CPlayer>(this@CivilizationMenu, civilization.citizens) {
        override fun convertToItemStack(civPlayer: CPlayer): ItemStack {
            val skull = ItemStack(Material.PLAYER_HEAD, 1)
            val skullMeta = skull.itemMeta as SkullMeta
            skullMeta.owningPlayer = Bukkit.getPlayer(civPlayer.uuid)
            skullMeta.setDisplayName("${ChatColor.YELLOW}${ChatColor.BOLD}${skullMeta.owningPlayer?.name}")
            if (civilization.leader?.uuid == civPlayer.uuid) {
                val skullLore: MutableList<String> = ArrayList()
                skullLore.add("")
                skullLore.add("${ChatColor.RED}${ChatColor.BOLD}Leader")
                skullMeta.lore = skullLore
            }
            TODO("groups")
            skull.itemMeta = skullMeta
            return skull
        }

        override fun onPageClick(p0: Player, p1: CPlayer, p2: ClickType) {
            if (p1 != PlayerManager.fromBukkitPlayer(p0))
                PlayerMenu(civilization, p1).displayTo(p0)
        }

    }


    inner class InfoMenu(civilization: Civ) : Menu(this@CivilizationMenu) {
        private val homeButton: Button
        private val leaderButton: Button
        private val statsButton: Button
        private val toggleablesButton: Button
        private val permissionsButton: Button
        private val relationshipsButton: Button

        override fun getItemAt(slot: Int): ItemStack {
            return when (slot) {
                9 + 2 -> homeButton.item
                9 + 6 -> leaderButton.item
                9 * 3 + 1 -> statsButton.item
                9 * 3 + 3 -> toggleablesButton.item
                9 * 3 + 5 -> permissionsButton.item
                9 * 3 + 7 -> relationshipsButton.item
                else -> return emptyButton.item
            }
        }

        init {
            size = 9 * 5
            setInfo("See stats and info on your Civilization")
            homeButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    civilization.home?.let { player.teleport(it) }
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.BELL, "&6&lHome", "",
                        "Coords: ${civilization.home?.x?.roundToInt() ?: ""}, ${civilization.home?.z?.roundToInt() ?: ""} ",
                        "Click to teleport!"
                    ).build().make()
                }
            }
            leaderButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.GOLDEN_APPLE, "&c&lLeader", "",
                        "Leader: ${civilization.leader?.playerName ?: "None"}"
                    ).build().make()
                }
            }
            statsButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.BEACON, "&b&lStats", "",
                        "Power: ${civilization.power}",
                        "Land: ${civilization.totalBlocksCount} blocks"
                    ).build().make()
                }
            }
            toggleablesButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    ToggleMenu(civilization).displayTo(player)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.FLINT_AND_STEEL, "&4&lToggleables", "",
                        "Fire: ${civilization.claimToggleables.fire}",
                        "PVP: ${civilization.claimToggleables.pvp}",
                        "Explosions: ${civilization.claimToggleables.explosion}",
                        "Mobs: ${civilization.claimToggleables.mobs}",
                        "Public: ${civilization.claimToggleables.public}",
                        "",
                        "Click to adjust a toggle setting!"
                    ).build().make()
                }
            }
            permissionsButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    TODO("go to permissions menu")
                }

                override fun getItem(): ItemStack {
                    TODO("make this better")
                    return ItemCreator.of(
                        CompMaterial.STONE_PICKAXE, "&4&lPermissions", "",
                        "Click to adjust permissions!"
                    ).build().make()
                }
            }
            relationshipsButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    TODO("go to permissions menu")
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.COMPASS, "&e&lRelationships", "",
                        "Allies: ${
                            Common.join(
                                civilization.allies.map { civilization -> civilization.name + "\n" },
                                ", "
                            )
                        }",
                        "Enemies: ${
                            Common.join(
                                civilization.enemies.map { civilization -> civilization.name + "\n" },
                                ", "
                            )
                        }",
                        "Outlaws: ${
                            Common.join(
                                civilization.outlaws.map { civPlayer -> civPlayer.playerName + "\n" },
                                ", "
                            )
                        }"
                    ).build().make()
                }
            }
        }
    }

    inner class ItemMenu(civilization: Civ) : Menu(this@CivilizationMenu) {
        private val getBookButton: Button
        private val getBannerButton: Button

        override fun getItemAt(slot: Int): ItemStack? {
            return when (slot) {
                2 -> getBookButton.item
                6 -> getBannerButton.item
                else -> emptyButton.item
            }
        }

        init {
            size = 9
            setInfo("Get your Civilizations's", "historical items")
            getBookButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, clickType: ClickType) {
                    if (civilization.book == null) {
                        Common.tell(player, "&cYour Civilization does not have a book.")
                        player.closeInventory()
                        return
                    }
                    val book = civilization.book
                    if (HookManager.getBalance(player) < 100) {
                        Common.tell(player, "&cYou need at least $100 to obtain this item.")
                        return
                    }
                    HookManager.withdraw(player, 100.0)
                    Common.tell(
                        player,
                        "${Settings.SECONDARY_COLOR}Successfully obtained your Civilization's Guiding Book"
                    )
                    player.inventory.addItem(book!!)
                    player.closeInventory()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.WRITTEN_BOOK,
                        "&b&lGet Guiding Book",
                        "",
                        "Get the guiding book",
                        "of your civilization"
                    ).build().make()
                }
            }
            getBannerButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, clickType: ClickType) {
                    if (civilization.banner == null) {
                        Common.tell(player, "&cYour Civilization does not have a book.")
                        player.closeInventory()
                        return
                    }
                    val banner = civilization.banner
                    if (HookManager.getBalance(player) < 200) {
                        Common.tell(player, "&cYou need at least $100 to obtain this item.")
                        return
                    }
                    HookManager.withdraw(player, 200.0)
                    Common.tell(
                        player,
                        "${Settings.SECONDARY_COLOR}Successfully obtained your Civilization's Banner"
                    )
                    player.inventory.addItem(banner!!)
                    player.closeInventory()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.BLUE_BANNER,
                        "&c&lGet Banner",
                        "",
                        "Get the symbol of",
                        "your Civilization"
                    ).build().make()
                }
            }
        }
    }

    inner class PlayerMenu(val civilization: Civ, val civPlayer: CPlayer) : Menu(this@CivilizationMenu) {
        private val groupChangeButton: Button
        private val kickButton: Button


        override fun getItemAt(slot: Int): ItemStack {
            return when (slot) {
                2 -> groupChangeButton.item
                6 -> kickButton.item
                else -> emptyButton.item
            }
        }

        init {
            size = 9
            setInfo("Manage a specific player in your Civilization")
            groupChangeButton = object : Button() {
                override fun onClickedInMenu(p0: Player, p1: Menu, p2: ClickType) {
                    fun groupChangePlayer() {
                        TODO()
                    }
                    p0.closeInventory()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.END_CRYSTAL,
                        "TODO"
                    ).build().make()
                }
            }
            kickButton = object : Button() {
                override fun onClickedInMenu(p0: Player, p1: Menu, p2: ClickType) {
                    fun kickPlayer() {
                        civilization.removeCitizen(civPlayer)
                        civPlayer.civilization = null
                        Bukkit.getPlayer(civPlayer.uuid)
                            ?.let { Common.tell(it, "&cYou have been kicked from your Civilization") }
                        tell("${Settings.PRIMARY_COLOR}Successfully kicked${Settings.SECONDARY_COLOR} ${civPlayer.playerName}")
                    }
                    p0.closeInventory()
                    ConfirmMenu(
                        "&4Kick player?",
                        "Permanently kick a player from your town",
                        ::kickPlayer
                    )
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.BARRIER,
                        "&c&lKick Player"
                    ).build().make()
                }

            }

        }
    }

    inner class InviteMenu(val civilization: Civ) :
        MenuPagged<Player>(this@CivilizationMenu, Bukkit.getOnlinePlayers()) {
        override fun convertToItemStack(player: Player): ItemStack? {
            if (player == viewer)
                return null
            val skull = ItemStack(Material.PLAYER_HEAD, 1)
            val skullMeta = skull.itemMeta as SkullMeta
            skullMeta.owningPlayer = player
            skullMeta.setDisplayName("${ChatColor.YELLOW}${ChatColor.BOLD}${player.name}")
            val skullLore: MutableList<String> = ArrayList()
            skullLore.add("")
            skullLore.add("${ChatColor.GOLD}${ChatColor.BOLD}Click to invite!")
            skullMeta.lore = skullLore
            skull.itemMeta = skullMeta
            return skull
        }

        override fun onPageClick(p0: Player, p1: Player, p2: ClickType?) {
            p0.closeInventory()
            val civPlayer = PlayerManager.fromBukkitPlayer(p1)
            civPlayer.civilizationInvite = civilization
            Common.tell(
                p1,
                "${Settings.PRIMARY_COLOR}You have been invited to join the Civilization ${Settings.SECONDARY_COLOR}${civilization.name}${Settings.PRIMARY_COLOR}! Type ${Settings.SECONDARY_COLOR}/civ accept${Settings.PRIMARY_COLOR} to accept"
            )
        }

    }

    init {
        size = 9 * 3
        itemMenuButton = ButtonMenu(
            ItemMenu(civ),
            CompMaterial.BOOK,
            "&b&lItem Menu",
            "",
            "Obtaining and Assigning",
            "your Civilization's Items."
        )
        infoButton = ButtonMenu(
            InfoMenu(civ),
            CompMaterial.BEEHIVE,
            "&d&lInfo Menu",
            "",
            "Display stats and info",
            "for your Civilization."
        )
        citizensMenuButton = ButtonMenu(
            CitizensMenu(civ),
            CompMaterial.CHEST,
            "&e&lCitizens Menu",
            "",
            "See a list of Citizens and",
            "their roles."
        )
        economyMenuButton = ButtonMenu(
            EconomyMenu(civ),
            CompMaterial.EMERALD_BLOCK,
            "&a&lEconomy Menu",
            "",
            "Manage your Civilization's",
            "money and taxes"
        )
        toggleMenuButton = ButtonMenu(
            ToggleMenu(civ),
            CompMaterial.LEVER,
            "&9&lToggle Menu",
            "",
            "Manage your Civilization's",
            "toggleable settings"
        )
        inviteMenuButton = ButtonMenu(
            InviteMenu(civ),
            CompMaterial.CAMPFIRE,
            "&3&lInvite Menu",
            "",
            "Invite players to",
            "your Civilization"
        )
    }
}