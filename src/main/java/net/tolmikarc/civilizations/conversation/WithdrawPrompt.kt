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

class WithdrawPrompt(val civilization: Civ, val player: Player) : SimpleDecimalPrompt() {

    override fun acceptValidatedInput(p0: ConversationContext, p1: Double): Prompt? {
        val amount = MathUtil.doubleToMoney(p1)
        if (civilization.bank.balance - amount < 0) {
            tell("{3}You cannot withdraw more money than your Civilization has")
            return null
        }

        HookManager.deposit(player, amount)
        civilization.bank.removeBalance(amount)
        tell("Successfully withdrew $amount")
        return null
    }

    override fun getPrompt(p0: ConversationContext?): String {
        return "{1}How much would you like to withdraw?"
    }

    override fun getFailedValidationText(context: ConversationContext, invalidInput: String): String {
        return Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.NUMBER)
    }


}