/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.conversation

import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.util.MathUtil
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.Prompt
import org.bukkit.entity.Player
import org.mineacademy.fo.conversation.SimpleDecimalPrompt
import org.mineacademy.fo.model.HookManager

class DepositPrompt(val civilization: Civ, val player: Player) : SimpleDecimalPrompt() {

    override fun acceptValidatedInput(context: ConversationContext?, input: Double): Prompt? {
        val cost = MathUtil.doubleToMoney(input)
        if (HookManager.getBalance(player) - cost < 0) {
            tell("{3}You cannot deposit more money than you have")
            return null
        }
        HookManager.withdraw(player, cost)
        civilization.bank.addBalance(cost)
        tell("Successfully deposited $cost")
        return null
    }

    override fun getPrompt(p0: ConversationContext?): String {
        return "{1}How much would you like to deposit?"
    }

    override fun getFailedValidationText(context: ConversationContext, invalidInput: String): String {
        return Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
    }

}