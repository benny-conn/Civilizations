/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.conversation

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.util.MathUtil
import org.bukkit.conversations.ConversationCanceller
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.Prompt
import org.bukkit.entity.Player
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.conversation.SimpleCanceller
import org.mineacademy.fo.conversation.SimpleConversation
import org.mineacademy.fo.conversation.SimpleDecimalPrompt
import org.mineacademy.fo.model.HookManager
import java.text.DecimalFormat

class BankTranscationConversation(
    private val transaction: Transaction,
    val civ: Civilization,
    val player: Player
) :
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

    inner class DepositPrompt(
        val civilization: Civilization,
        val player: Player
    ) : SimpleDecimalPrompt() {

        override fun acceptValidatedInput(context: ConversationContext?, input: Double): Prompt? {
            val cost = MathUtil.doubleToMoney(input)
            if (HookManager.getBalance(player) - cost < 0) {
                tell(
                    Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace(
                        "{cost}",
                        cost.toString().format(DecimalFormat.getCurrencyInstance())
                    )
                )
                return null
            }
            HookManager.withdraw(player, cost)
            civilization.bank.addBalance(cost)
            Messenger.success(
                player,
                Localization.Notifications.DEPOSITED.replace(
                    "{cost}",
                    cost.toString().format(DecimalFormat.getCurrencyInstance())
                )
            )
            return null
        }

        override fun getPrompt(p0: ConversationContext?): String {
            return "{1}How much would you like to deposit?"
        }

        override fun getFailedValidationText(context: ConversationContext, invalidInput: String): String {
            return Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                "{item}",
                Localization.NUMBER
            )
        }

    }

    inner class WithdrawPrompt(
        val civilization: Civilization,
        val player: Player
    ) : SimpleDecimalPrompt() {

        override fun acceptValidatedInput(p0: ConversationContext, p1: Double): Prompt? {
            val cost = MathUtil.doubleToMoney(p1)
            if (civilization.bank.balance - cost < 0) {
                tell(
                    Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                        "{cost}",
                        cost.toString().format(DecimalFormat.getCurrencyInstance())
                    )
                )
                return null
            }

            HookManager.deposit(player, cost)
            civilization.bank.removeBalance(cost)
            Messenger.success(
                player,
                Localization.Notifications.WITHDREW.replace(
                    "{cost}",
                    cost.toString().format(DecimalFormat.getCurrencyInstance())
                )
            )
            return null
        }

        override fun getPrompt(p0: ConversationContext?): String {
            return "{1}How much would you like to withdraw?"
        }

        override fun getFailedValidationText(context: ConversationContext, invalidInput: String): String {
            return Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                "{item}",
                Localization.NUMBER
            )
        }


    }

    enum class Transaction {
        DEPOSIT, WITHDRAW
    }
}