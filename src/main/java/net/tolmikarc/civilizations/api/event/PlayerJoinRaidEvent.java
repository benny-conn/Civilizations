/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.api.event;

import net.tolmikarc.civilizations.model.Civ;
import net.tolmikarc.civilizations.war.Raid;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerJoinRaidEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Raid raid;
    private final Player player;


    public PlayerJoinRaidEvent(Raid raid, Player player) {
        this.raid = raid;
        this.player = player;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Raid getRaid() {
        return raid;
    }

    public Civ getAttacker() {
        return raid.getCivRaiding();
    }

    public Civ getDefender() {
        return raid.getCivBeingRaided();
    }

    public Player getPlayer() {
        return player;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

}