/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.conversation

import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Localization
import org.bukkit.conversations.ConversationCanceller
import org.bukkit.conversations.Prompt
import org.bukkit.entity.Player
import org.mineacademy.fo.conversation.SimpleCanceller
import org.mineacademy.fo.conversation.SimpleConversation

class BankTranscationConversation(private val transaction: Transaction, val civ: Civ, val player: Player) :
    SimpleConversation() {
    override fun getFirstPrompt(): Prompt? {
        if (transaction == Transaction.DEPOSIT)
            return DepositPrompt(civ, player)
        return if (transaction == Transaction.WITHDRAW)
            WithdrawPrompt(civ, player)
        else
            null
    }

    override fun getCanceller(): ConversationCanceller {
        return SimpleCanceller(Localization.CANCEL)
    }

    enum class Transaction {
        DEPOSIT, WITHDRAW
    }
}