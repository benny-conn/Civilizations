/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.api.event;

import net.tolmikarc.civilizations.model.Civ;
import net.tolmikarc.civilizations.model.impl.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class UnclaimEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Civ civ;
    private final Region region;
    private final Player player;


    public UnclaimEvent(Civ civ, Region region, Player player) {
        this.civ = civ;
        this.region = region;
        this.player = player;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Player getPlayer() {
        return player;
    }

    public Region getClaim() {
        return region;
    }

    public Civ getCiv() {
        return civ;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

}