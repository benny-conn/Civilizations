/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.event;


import io.bennyc.civilizations.model.Civilization;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class DeleteCivEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Civilization civ;
    private final Player player;


    public DeleteCivEvent(Civilization civ, Player player) {
        this.civ = civ;
        this.player = player;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Player getPlayer() {
        return player;
    }

    public Civilization getCiv() {
        return civ;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

}