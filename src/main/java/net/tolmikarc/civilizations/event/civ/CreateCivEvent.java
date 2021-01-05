/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.event.civ;

import net.tolmikarc.civilizations.model.Civilization;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CreateCivEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Civilization civ;
	private final Player player;


	public CreateCivEvent(Civilization civ, Player player) {
		this.civ = civ;
		this.player = player;
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

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
