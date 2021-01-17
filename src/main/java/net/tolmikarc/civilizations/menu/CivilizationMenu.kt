/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.menu

import io.papermc.lib.PaperLib
import net.tolmikarc.civilizations.conversation.BankTranscationConversation
import net.tolmikarc.civilizations.conversation.RankCreationConversation
import net.tolmikarc.civilizations.conversation.RankRenameConversation
import net.tolmikarc.civilizations.conversation.RenameConversation
import net.tolmikarc.civilizations.event.DeleteCivEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Colony
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.permissions.Rank
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.mineacademy.fo.Common
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.RandomUtil
import org.mineacademy.fo.Valid
import org.mineacademy.fo.Valid.checkBoolean
import org.mineacademy.fo.menu.Menu
import org.mineacademy.fo.menu.MenuPagged
import org.mineacademy.fo.menu.button.Button
import org.mineacademy.fo.menu.button.ButtonMenu
import org.mineacademy.fo.menu.model.ItemCreator
import org.mineacademy.fo.remain.CompColor
import org.mineacademy.fo.remain.CompMaterial
import kotlin.math.roundToInt

class CivilizationMenu(val civilization: Civ) : Menu() {


    // TODO finish this

    private val infoButton: Button
    private val emptyButton: Button.DummyButton =
        Button.makeDummy(ItemCreator.of(CompMaterial.GRAY_STAINED_GLASS_PANE).name(" "))
    private val citizensMenuButton: Button
    private val economyMenuButton: Button
    private val toggleMenuButton: Button
    private val permissionsMenuButton: Button
    private val inviteMenuButton: Button
    private val generalSettingsButton: Button
    private val actionsButton: Button


    override fun getItemAt(slot: Int): ItemStack {
        return when (slot) {
            1 -> infoButton.item
            3 -> citizensMenuButton.item
            5 -> economyMenuButton.item
            7 -> toggleMenuButton.item
            9 * 2 + 1 -> permissionsMenuButton.item
            9 * 2 + 3 -> inviteMenuButton.item
            9 * 2 + 5 -> generalSettingsButton.item
            9 * 2 + 7 -> actionsButton.item
            else -> emptyButton.item
        }
    }

    inner class PermissionsMenu : MenuPagged<Rank>(civilization.permissions.ranks) {

        override fun convertToItemStack(rank: Rank): ItemStack {
            val color = RandomUtil.nextChatColor()
            return ItemCreator.ofWool(CompColor.fromChatColor(color)).name("$color${rank.name}")
                .lore("&eClick to edit!")
                .build().make()
        }

        override fun onPageClick(player: Player, rank: Rank, click: ClickType) {
            RankMenu(rank).displayTo(player)
        }

        inner class RankMenu(rank: Rank) : Menu(this@PermissionsMenu) {

            private val nameButton: Button
            private val buildButton: Button
            private val breakButton: Button
            private val switchButton: Button
            private val interactButton: Button
            private val adminButton: Button


            override fun getItemAt(slot: Int): ItemStack {
                return when (slot) {
                    9 + 2 -> nameButton.item
                    1 -> buildButton.item
                    3 -> breakButton.item
                    5 -> switchButton.item
                    7 -> interactButton.item
                    9 + 6 -> adminButton.item
                    else -> emptyButton.item
                }
            }

            init {
                size = 9 * 2
                setInfo("Adjust rank settings.")

                nameButton = object : Button() {
                    override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                        if (civilization.permissions.defaultRank == rank || civilization.permissions.outsiderRank == rank || civilization.permissions.allyRank == rank || civilization.permissions.enemyRank == rank) return
                        RankRenameConversation(rank, civilization, player).start(player)
                    }

                    override fun getItem(): ItemStack {
                        return ItemCreator.of(CompMaterial.OAK_SIGN, "&6&lRename").build().make()
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
                        ).build().make()
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
                        ).build().make()
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
                        ).build().make()
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
                        ).build().make()
                    }
                }
                adminButton = object : Button() {
                    override fun onClickedInMenu(p0: Player?, p1: Menu?, p2: ClickType?) {
                        if (civilization.permissions.adminGroups.contains(rank))
                            civilization.permissions.adminGroups.remove(rank)
                        else
                            civilization.permissions.adminGroups.add(rank)

                        restartMenu()
                    }

                    override fun getItem(): ItemStack {
                        return ItemCreator.of(
                            CompMaterial.DIAMOND,
                            "&4&lAdmin: " + if (civilization.permissions.adminGroups.contains(rank)) "&aTrue" else "&cFalse",
                            "",
                            "&7Can this rank manage this Civ?"
                        ).build().make()
                    }
                }


            }

        }

    }


    inner class ToggleMenu : Menu(this@CivilizationMenu) {
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
                    civilization.toggleables.pvp = !civilization.toggleables.pvp
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.IRON_SWORD,
                        "&c&lToggle PVP",
                        "",
                        "PVP: ${civilization.toggleables.pvp}"
                    ).build().make()
                }
            }
            mobs = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.toggleables.mobs = !civilization.toggleables.mobs
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.SPAWNER,
                        "&2&lToggle Mobs Spawning",
                        "",
                        "Mobs: ${civilization.toggleables.mobs}"
                    ).build().make()
                }
            }
            explosions = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.toggleables.explosion = !civilization.toggleables.explosion
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.TNT_MINECART,
                        "&4&lToggle Explosions",
                        "",
                        "Explosions: ${civilization.toggleables.explosion}"
                    ).build().make()
                }
            }
            fire = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.toggleables.fire = !civilization.toggleables.fire
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.CAMPFIRE,
                        "&6&lToggle Fire Spreading",
                        "",
                        "Fire: ${civilization.toggleables.fire}"
                    ).build().make()
                }
            }
            public = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.toggleables.public = !civilization.toggleables.public
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.BIRCH_DOOR,
                        "&b&lToggle Public Home",
                        "",
                        "Public: ${civilization.toggleables.public}"
                    ).build().make()
                }
            }
            inviteOnly = object : Button() {
                override fun onClickedInMenu(p0: Player?, p1: Menu, p2: ClickType?) {
                    civilization.toggleables.inviteOnly = !civilization.toggleables.inviteOnly
                    restartMenu()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.CLOCK,
                        "&3&lToggle Invite Only",
                        "",
                        "Invite Only: ${civilization.toggleables.inviteOnly}"
                    ).build().make()
                }
            }


        }


    }


    inner class EconomyMenu : Menu(this@CivilizationMenu) {
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
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    player.closeInventory()
                    BankTranscationConversation(
                        BankTranscationConversation.Transaction.DEPOSIT,
                        civilization,
                        player
                    ).start(player)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.EMERALD,
                        "&a&lDeposit",
                    ).build().make()
                }
            }
            withdrawButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    player.closeInventory()
                    BankTranscationConversation(
                        BankTranscationConversation.Transaction.WITHDRAW,
                        civilization,
                        player
                    ).start(player)
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


    inner class CitizensMenu :
        MenuPagged<CPlayer>(this@CivilizationMenu, civilization.citizens) {
        override fun convertToItemStack(civPlayer: CPlayer): ItemStack {
            val skull = ItemStack(Material.PLAYER_HEAD, 1)
            val skullMeta = skull.itemMeta as SkullMeta
            skullMeta.owningPlayer = Bukkit.getPlayer(civPlayer.uuid)
            skullMeta.setDisplayName("${ChatColor.YELLOW}${ChatColor.BOLD}${skullMeta.owningPlayer?.name}")
            val skullLore: MutableList<String> = ArrayList()
            if (civilization.leader?.uuid == civPlayer.uuid) {
                skullLore.add("")
                skullLore.add("${ChatColor.RED}${ChatColor.BOLD}Leader")
                skullMeta.lore = skullLore
            } else {
                val rank = civilization.permissions.getPlayerGroup(civPlayer)
                skullLore.add("")
                skullLore.add("${ChatColor.YELLOW}${rank.name}")
            }
            skull.itemMeta = skullMeta
            return skull
        }

        override fun onPageClick(p0: Player, p1: CPlayer, p2: ClickType) {
            if (p1 != PlayerManager.fromBukkitPlayer(p0))
                PlayerMenu(civilization, p1).displayTo(p0)
        }

        inner class PlayerMenu(val civilization: Civ, val civPlayer: CPlayer) : Menu(this@CitizensMenu) {
            private val groupChangeButton: Button
            private val kickButton: Button
            private val outlawButton: Button


            override fun getItemAt(slot: Int): ItemStack {
                return when (slot) {
                    2 -> groupChangeButton.item
                    4 -> kickButton.item
                    6 -> outlawButton.item
                    else -> emptyButton.item
                }
            }

            init {
                size = 9
                setInfo("Manage a specific player in your Civilization")
                groupChangeButton = object : Button() {
                    override fun onClickedInMenu(player: Player, p1: Menu, p2: ClickType) {
                        RankSelectorMenu().displayTo(player)
                    }

                    override fun getItem(): ItemStack {
                        return ItemCreator.of(
                            CompMaterial.COOKED_BEEF,
                            "&6&lCurrent Rank: ${civilization.permissions.getPlayerGroup(civPlayer).name}",
                            "&eClick to change"
                        ).build().make()
                    }
                }
                kickButton = object : Button() {
                    override fun onClickedInMenu(player: Player, p1: Menu, p2: ClickType) {
                        fun kickPlayer() {
                            civilization.removeCitizen(civPlayer)
                            civPlayer.civilization = null
                            Bukkit.getPlayer(civPlayer.uuid)
                                ?.let { Common.tell(it, "&cYou have been kicked from your Civilization") }
                            tellSuccess("${Settings.PRIMARY_COLOR}Successfully kicked ${Settings.SECONDARY_COLOR} ${civPlayer.playerName}")
                        }
                        player.closeInventory()
                        ConfirmMenu(
                            "&4Kick player?",
                            "Permanently kick a player from your town",
                            ::kickPlayer
                        ).displayTo(player)
                    }

                    override fun getItem(): ItemStack {
                        return ItemCreator.of(
                            CompMaterial.BARRIER,
                            "&c&lKick Player"
                        ).build().make()
                    }

                }
                outlawButton = object : Button() {
                    override fun onClickedInMenu(player: Player, p1: Menu, p2: ClickType) {
                        if (civilization.relationships.outlaws.contains(civPlayer))
                            civilization.relationships.removeOutlaw(civPlayer)
                        else
                            civilization.relationships.addOutlaw(civPlayer)
                        restartMenu()
                    }

                    override fun getItem(): ItemStack {
                        return ItemCreator.of(
                            CompMaterial.STONE_SWORD,
                            "&4&lOutlaw Player", "",
                            if (civilization.relationships.outlaws.contains(civPlayer)) "Player is currently Outlawed" else "Player is not Outlawed"
                        ).build().make()
                    }

                }

            }

            inner class RankSelectorMenu :
                MenuPagged<Rank>(civilization.permissions.ranks) {
                override fun convertToItemStack(rank: Rank): ItemStack {
                    val color = RandomUtil.nextChatColor()
                    return ItemCreator.ofWool(CompColor.fromChatColor(color)).name("$color${rank.name}")
                        .lore("&eClick to edit!")
                        .build().make()
                }

                override fun onPageClick(p0: Player?, rank: Rank, p2: ClickType?) {
                    civilization.permissions.setPlayerGroup(civPlayer, rank)
                }

            }

        }

    }


    inner class InfoMenu : Menu(this@CivilizationMenu) {
        private val homeButton: Button
        private val leaderButton: Button
        private val statsButton: Button
        private val toggleablesButton: Button
        private val relationshipsButton: Button

        override fun getItemAt(slot: Int): ItemStack {
            return when (slot) {
                9 + 2 -> homeButton.item
                9 + 6 -> leaderButton.item
                9 * 2 + 1 -> statsButton.item
                9 * 3 + 4 -> toggleablesButton.item
                9 * 3 + 7 -> relationshipsButton.item
                else -> return emptyButton.item
            }
        }

        init {
            size = 9 * 5
            setInfo("See stats and info on your Civilization")
            homeButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    player.closeInventory()
                    val civPlayer = PlayerManager.fromBukkitPlayer(player)
                    Valid.checkNotNull(
                        civilization.home,
                        Localization.Warnings.NULL_RESULT.replace("{item}", "${Localization.CIVILIZATION} home")
                    )
                    checkBoolean(
                        !CooldownTask.hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                        Localization.Warnings.COOLDOWN_WAIT.replace(
                            "{duration}",
                            CooldownTask.getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                        )
                    )
                    PaperLib.teleportAsync(player, civilization.home!!).thenAccept {
                        if (it)
                            tellSuccess(Localization.Notifications.SUCCESS_TELEPORT)
                        else
                            tellError(Localization.Warnings.FAILED_TELEPORT)
                    }
                    CooldownTask.addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.BELL, "&6&lHome", "",
                        "Coords: ${civilization.home?.x?.roundToInt() ?: "NONE"}, ${civilization.home?.z?.roundToInt() ?: "NONE"} ",
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
                        "Land: ${civilization.claims.totalBlocksCount} blocks"
                    ).build().make()
                }
            }
            toggleablesButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    ToggleMenu().displayTo(player)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.FLINT_AND_STEEL, "&4&lToggleables", "",
                        "Fire: ${civilization.toggleables.fire}",
                        "PVP: ${civilization.toggleables.pvp}",
                        "Explosions: ${civilization.toggleables.explosion}",
                        "Mobs: ${civilization.toggleables.mobs}",
                        "Public: ${civilization.toggleables.public}",
                        "Invite Only: ${civilization.toggleables.inviteOnly}",
                        "",
                        "Click to adjust a toggle setting!"
                    ).build().make()
                }
            }
            relationshipsButton = object : Button() {
                override fun onClickedInMenu(player: Player, menu: Menu, click: ClickType) {
                    PermissionsMenu().displayTo(player)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(
                        CompMaterial.COMPASS, "&e&lRelationships", "",
                        "Allies: ${
                            Common.join(
                                civilization.relationships.allies.map { civilization -> civilization.name + "\n" },
                                ", "
                            )
                        }",
                        "Enemies: ${
                            Common.join(
                                civilization.relationships.enemies.map { civilization -> civilization.name + "\n" },
                                ", "
                            )
                        }",
                        "Outlaws: ${
                            Common.join(
                                civilization.relationships.outlaws.map { civPlayer -> civPlayer.playerName + "\n" },
                                ", "
                            )
                        }"
                    ).build().make()
                }
            }
        }
    }

    inner class InviteMenu :
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

        override fun onPageClick(player: Player, invitedPlayer: Player, p2: ClickType?) {
            player.closeInventory()
            val civPlayer = PlayerManager.fromBukkitPlayer(invitedPlayer)
            civPlayer.civilizationInvite = civilization
            Common.tell(
                invitedPlayer,
                Localization.Notifications.INVITE_RECEIVED.replace("{civ}", civilization.name!!)
            )
        }
    }

    inner class GeneralSettingsMenu : Menu(this@CivilizationMenu) {
        private val renameButton: Button
        private val descriptionButton: Button
        private val leaderButton: Button


        override fun getItemAt(slot: Int): ItemStack {
            return when (slot) {
                2 -> renameButton.item
                4 -> descriptionButton.item
                6 -> leaderButton.item
                else -> emptyButton.item
            }
        }

        init {
            size = 9
            setInfo("Adjust general settings")
            renameButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    if (civilization.leader?.equals(PlayerManager.fromBukkitPlayer(player)) == true) {
                        player.closeInventory()
                        RenameConversation(civilization, player).start(player)
                    }
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.BOOK, "&a&lRename Civilization").build().make()
                }
            }
            descriptionButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    player.closeInventory()
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.PAPER, "&6&lSet Description").build().make()
                }
            }
            leaderButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    if (civilization.leader?.equals(PlayerManager.fromBukkitPlayer(player)) == true)
                        LeaderMenu().displayTo(player)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.NETHER_STAR, "&4&lAssign New Leader").build().make()
                }
            }
        }

        inner class LeaderMenu : MenuPagged<CPlayer>(civilization.citizens) {
            override fun convertToItemStack(civPlayer: CPlayer): ItemStack {
                val skull = ItemStack(Material.PLAYER_HEAD, 1)
                val skullMeta = skull.itemMeta as SkullMeta
                skullMeta.owningPlayer = Bukkit.getPlayer(civPlayer.uuid)
                skullMeta.setDisplayName("${ChatColor.YELLOW}${ChatColor.BOLD}${skullMeta.owningPlayer?.name}")
                val skullLore: MutableList<String> = ArrayList()
                if (civilization.leader?.uuid == civPlayer.uuid) {
                    skullLore.add("")
                    skullLore.add("${ChatColor.RED}${ChatColor.BOLD}Leader")
                    skullMeta.lore = skullLore
                } else {
                    val rank = civilization.permissions.getPlayerGroup(civPlayer)
                    skullLore.add("")
                    skullLore.add("${ChatColor.YELLOW}${rank.name}")
                }
                skull.itemMeta = skullMeta
                return skull
            }

            override fun onPageClick(player: Player, civPlayer: CPlayer, p2: ClickType?) {
                fun leaderPlayer() {
                    civilization.permissions.setPlayerGroup(civilization.leader!!, civilization.permissions.defaultRank)
                    civilization.leader = civPlayer
                    player.closeInventory()
                    tellSuccess("Set new Civilization Leader")
                }
                ConfirmMenu(
                    "&4&lSet Leader?",
                    "Make this player the leader. WARNING: Irreversible",
                    ::leaderPlayer
                ).displayTo(player)
            }

        }
    }

    inner class ActionsMenu : Menu(this@CivilizationMenu) {

        private val homeButton: Button
        private val warpButton: Button
        private val colonyButton: Button
        private val createRankButton: Button
        private val deleteButton: Button

        override fun getItemAt(slot: Int): ItemStack {
            return when (slot) {
                2 -> homeButton.item
                4 -> warpButton.item
                6 -> colonyButton.item
                9 + 2 -> createRankButton.item
                9 + 6 -> deleteButton.item
                else -> emptyButton.item
            }
        }

        init {
            size = 9 * 2
            setInfo("Perform actions with your Civilization")
            homeButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    player.closeInventory()
                    val civPlayer = PlayerManager.fromBukkitPlayer(player)
                    Valid.checkNotNull(
                        civilization.home,
                        Localization.Warnings.NULL_RESULT.replace("{item}", "${Localization.CIVILIZATION} home")
                    )
                    if (CooldownTask.hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT))
                        Messenger.error(
                            player, Localization.Warnings.COOLDOWN_WAIT.replace(
                                "{duration}",
                                CooldownTask.getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT)
                                    .toString()
                            )
                        )
                    PaperLib.teleportAsync(player, civilization.home!!).thenAccept {
                        if (it)
                            tellSuccess(Localization.Notifications.SUCCESS_TELEPORT)
                        else
                            tellError(Localization.Warnings.FAILED_TELEPORT)
                    }
                    CooldownTask.addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.CAKE, "&b&lTeleport Home").build().make()
                }
            }
            warpButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    if (civilization.warps.isNotEmpty())
                        WarpsMenu().displayTo(player)
                    else
                        Messenger.error(player, "&cNo colonies available")
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.CRAFTING_TABLE, "&6&lTeleport To A Warp").build().make()
                }
            }
            colonyButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    if (civilization.claims.colonies.isNotEmpty())
                        ColoniesMenu().displayTo(player)
                    else
                        Messenger.error(player, "&cNo colonies available")
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.OAK_SAPLING, "&3&lTeleport To A Colony").build().make()
                }
            }
            createRankButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    player.closeInventory()
                    RankCreationConversation(civilization, player).start(player)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.ENDER_PEARL, "&d&lCreate a New Rank").build().make()
                }

            }
            deleteButton = object : Button() {
                override fun onClickedInMenu(player: Player, p1: Menu?, p2: ClickType?) {
                    fun run() {
                        tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                        for (citizen in civilization.citizens) {
                            citizen.civilization = null
                            PlayerManager.queueForSaving(citizen)
                        }
                        CivManager.removeCiv(civilization)
                        Common.callEvent(DeleteCivEvent(civilization, player))
                    }
                    ConfirmMenu("&4Delete Civ?", "WARNING: Irreversible", ::run).displayTo(player)
                }

                override fun getItem(): ItemStack {
                    return ItemCreator.of(CompMaterial.TNT, "&4&lDelete Civilization").build().make()
                }

            }

        }

        inner class WarpsMenu : MenuPagged<String>(civilization.warps.keys) {
            override fun convertToItemStack(warp: String?): ItemStack {
                val color = RandomUtil.nextChatColor()
                return ItemCreator.ofWool(CompColor.fromChatColor(color)).name("$color$warp").build().make()
            }

            override fun onPageClick(player: Player, warp: String, p2: ClickType?) {
                player.closeInventory()
                val civPlayer = PlayerManager.fromBukkitPlayer(player)
                val location = civilization.warps[warp]
                if (CooldownTask.hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT))
                    Messenger.error(
                        player, Localization.Warnings.COOLDOWN_WAIT.replace(
                            "{duration}",
                            CooldownTask.getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                        )
                    )
                PaperLib.teleportAsync(player, location!!).thenAccept {
                    if (it)
                        tellSuccess(Localization.Notifications.SUCCESS_TELEPORT)
                    else
                        tellError(Localization.Warnings.FAILED_TELEPORT)
                }
                CooldownTask.addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
            }
        }

        inner class ColoniesMenu : MenuPagged<Colony>(civilization.claims.colonies) {
            override fun convertToItemStack(colony: Colony): ItemStack {
                val color = RandomUtil.nextChatColor()
                return ItemCreator.ofWool(CompColor.fromChatColor(color)).name("${color}ID: ${colony.id}").build()
                    .make()
            }

            override fun onPageClick(player: Player, colony: Colony, p2: ClickType?) {
                player.closeInventory()
                val civPlayer = PlayerManager.fromBukkitPlayer(player)
                val location = colony.warp
                if (CooldownTask.hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT))
                    Messenger.error(
                        player, Localization.Warnings.COOLDOWN_WAIT.replace(
                            "{duration}",
                            CooldownTask.getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                        )
                    )
                PaperLib.teleportAsync(player, location).thenAccept {
                    if (it)
                        tellSuccess(Localization.Notifications.SUCCESS_TELEPORT)
                    else
                        tellError(Localization.Warnings.FAILED_TELEPORT)
                }
                CooldownTask.addCooldownTimer(civPlayer, CooldownTask.CooldownType.TELEPORT)
            }
        }
    }

    init {
        size = 9 * 3
        infoButton = ButtonMenu(
            InfoMenu(),
            CompMaterial.BEEHIVE,
            "&d&lInfo Menu",
            "",
            "Display stats and info",
            "for your Civilization."
        )
        citizensMenuButton = ButtonMenu(
            CitizensMenu(),
            CompMaterial.CHEST,
            "&e&lCitizens Menu",
            "",
            "See a list of Citizens and",
            "their roles."
        )
        economyMenuButton = ButtonMenu(
            EconomyMenu(),
            CompMaterial.EMERALD_BLOCK,
            "&a&lEconomy Menu",
            "",
            "Manage your Civilization's",
            "money and taxes"
        )
        toggleMenuButton = ButtonMenu(
            ToggleMenu(),
            CompMaterial.LEVER,
            "&9&lToggle Menu",
            "",
            "Manage your Civilization's",
            "toggleable settings"
        )
        permissionsMenuButton = ButtonMenu(
            PermissionsMenu(),
            CompMaterial.OAK_SIGN,
            "&6&lPermissions Menu",
            "",
            "Manage your Civilization's",
            "Ranks and Permissions"
        )
        inviteMenuButton = ButtonMenu(
            InviteMenu(),
            CompMaterial.CAMPFIRE,
            "&3&lInvite Menu",
            "",
            "Invite players to",
            "your Civilization"
        )
        generalSettingsButton = ButtonMenu(
            GeneralSettingsMenu(),
            CompMaterial.COMPASS,
            "&2&lGeneral Settings",
            "",
            "Adjust general",
            "settings."
        )
        actionsButton = ButtonMenu(
            ActionsMenu(),
            CompMaterial.BLAZE_ROD,
            "&b&lActions Menu",
            "",
            "Perform actions on",
            "your Civilization"
        )
    }
}