/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.conversation

import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.permissions.Rank
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.conversations.ConversationCanceller
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.Prompt
import org.bukkit.entity.Player
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.conversation.SimpleCanceller
import org.mineacademy.fo.conversation.SimpleConversation
import org.mineacademy.fo.conversation.SimplePrompt

class RankCreationConversation(val civ: Civilization, val player: Player) :
    SimpleConversation() {


    override fun getFirstPrompt(): Prompt {
        return NamePrompt()
    }

    override fun getCanceller(): ConversationCanceller {
        return SimpleCanceller(Localization.CANCEL)
    }

    inner class NamePrompt : SimplePrompt() {
        override fun acceptValidatedInput(context: ConversationContext, input: String): Prompt? {
            val rank = Rank(input, HashSet())
            civ.permissions.ranks.add(rank)
            Messenger.success(player, "${Settings.PRIMARY_COLOR}Created new rank: $input")
            return null
        }

        override fun isInputValid(context: ConversationContext, input: String): Boolean {
            return !input.contains(" ")
        }

        override fun getPrompt(p0: ConversationContext?): String {
            return "${Settings.PRIMARY_COLOR}Type the name for the new rank:"
        }

    }


}