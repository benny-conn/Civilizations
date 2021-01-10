/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.conversation

import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.MathUtil
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.Prompt
import org.bukkit.entity.Player
import org.mineacademy.fo.conversation.SimplePrompt
import org.mineacademy.fo.model.HookManager

class DepositPrompt(val civilization: Civ, val player: Player) : SimplePrompt() {
    override fun acceptValidatedInput(p0: ConversationContext, p1: String): Prompt? {
        val cost = MathUtil.doubleToMoney(p1.toDouble())
        if (HookManager.getBalance(player) - cost < 0) {
            tell("&cYou cannot deposit more money than you have")
            return null
        }
        HookManager.withdraw(player, cost)
        civilization.bank.addBalance(cost)
        tell("Successfully deposited $cost")
        return null
    }

    override fun getPrompt(p0: ConversationContext?): String {
        return "${Settings.PRIMARY_COLOR}How much would you like to deposit?"
    }

    override fun getFailedValidationText(context: ConversationContext, invalidInput: String): String? {
        return "Please specify a valid number"
    }

    override fun isInputValid(context: ConversationContext, input: String): Boolean {
        return net.tolmikarc.civilizations.util.MathUtil.isDouble(input)
    }

}