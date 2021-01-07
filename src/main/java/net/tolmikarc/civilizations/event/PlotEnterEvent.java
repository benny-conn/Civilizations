/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.event;

import net.tolmikarc.civilizations.model.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlotEnterEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Plot plot;
    private final Player player;
    private boolean cancelled;


    public PlotEnterEvent(Plot plot, Player player) {
        this.plot = plot;
        this.player = player;
    }


    public Player getPlayer() {
        return player;
    }

    public Plot getPlot() {
        return plot;
    }


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        cancelled = b;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}