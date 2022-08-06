/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.task.TownyAdapterTask
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ATownyAdaptCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "townyadapt") {
    override fun onCommand() {
        tellSuccess("Beginning Town Adapting")
//        TownyAdapter.getResidentsUUIDS()
        TownyAdapterTask(5).startChain()
    }

    init {
        setDescription("Adapt all Towny Towns to Civilizations")
    }
}