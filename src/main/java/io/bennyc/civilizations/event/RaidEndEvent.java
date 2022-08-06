/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.event;


import io.bennyc.civilizations.model.Civilization;
import io.bennyc.civilizations.war.Raid;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RaidEndEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Raid raid;


    public RaidEndEvent(Raid raid) {
        this.raid = raid;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Raid getRaid() {
        return raid;
    }

    public Civilization getAttacker() {
        return raid.getCivRaiding();
    }

    public Civilization getDefender() {
        return raid.getCivBeingRaided();
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

}