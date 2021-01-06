/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.event.civ;

import net.tolmikarc.civilizations.model.Civ;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.region.Region;

public class ClaimEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Civ civ;
	private final Region claim;
	private final Player player;


	public ClaimEvent(Civ civ, Region claim, Player player) {
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
