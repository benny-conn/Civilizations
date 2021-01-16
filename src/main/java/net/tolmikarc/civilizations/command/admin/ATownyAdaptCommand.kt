/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.task.TownyAdapterTask
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ATownyAdaptCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "townyadapt") {
    override fun onCommand() {
        tellSuccess("Beginning Town Adapting")
        TownyAdapterTask(4).startChain()
    }

    init {
        setDescription("Adapt all Towny Towns to Civilizations")
    }
}