/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.conversation

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.bukkit.conversations.ConversationCanceller
import org.bukkit.conversations.ConversationContext
import org.bukkit.conversations.Prompt
import org.bukkit.entity.Player
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.conversation.SimpleCanceller
import org.mineacademy.fo.conversation.SimpleConversation
import org.mineacademy.fo.conversation.SimplePrompt

class DescriptionConversation(val civ: Civilization, val player: Player) :
    SimpleConversation() {


    override fun getFirstPrompt(): Prompt {
        return NamePrompt()
    }

    override fun getCanceller(): ConversationCanceller {
        return SimpleCanceller(Localization.CANCEL)
    }

    inner class NamePrompt : SimplePrompt() {
        override fun acceptValidatedInput(context: ConversationContext, input: String): Prompt? {
            civ.description = input
            Messenger.success(player, "${Settings.PRIMARY_COLOR}Description Set: $input")
            return null
        }

        override fun getPrompt(p0: ConversationContext?): String {
            return "${Settings.PRIMARY_COLOR}Type a new description:"
        }

    }


}