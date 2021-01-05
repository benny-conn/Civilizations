/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.event.civ;

import net.tolmikarc.civilizations.model.Civilization;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.region.Region;

public class UnclaimEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Civilization civ;
	private final Region claim;
	private final Player player;


	public UnclaimEvent(Civilization civ, Region claim, Player player) {
		this.civ = civ;
		this.claim = claim;
		this.player = player;
	}

	public Player getPlayer() {
		return player;
	}


	public Region getClaim() {
		return claim;
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
