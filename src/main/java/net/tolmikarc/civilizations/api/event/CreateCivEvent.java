/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.api.event;


import net.tolmikarc.civilizations.model.Civ;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CreateCivEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Civ civ;
	private final Player player;


	public CreateCivEvent(Civ civ, Player player) {
		this.civ = civ;
		this.player = player;
	}

	public Player getPlayer() {
		return player;
	}


	public Civ getCiv() {
		return civ;
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