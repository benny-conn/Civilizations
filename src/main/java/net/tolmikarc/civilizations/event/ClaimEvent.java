/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.event;


import net.tolmikarc.civilizations.model.Civilization;
import net.tolmikarc.civilizations.model.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ClaimEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Civilization civ;
    private final Region region;
    private final Player player;


    public ClaimEvent(Civilization civ, Region region, Player player) {
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

    public Civilization getCiv() {
        return civ;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

}