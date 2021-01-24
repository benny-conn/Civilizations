/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.api.event;


import net.tolmikarc.civilizations.model.Civ;
import net.tolmikarc.civilizations.war.Raid;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RaidBeginEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Raid raid;


	public RaidBeginEvent(Raid raid) {
		this.raid = raid;
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


	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}