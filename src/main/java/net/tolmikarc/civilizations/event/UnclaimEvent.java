/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.event;

import net.tolmikarc.civilizations.model.Civ;
import net.tolmikarc.civilizations.model.impl.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class UnclaimEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Civ civ;
	private final Claim claim;
	private final Player player;


	public UnclaimEvent(Civ civ, Claim claim, Player player) {
		this.civ = civ;
		this.claim = claim;
		this.player = player;
	}

	public Player getPlayer() {
		return player;
	}


	public Claim getClaim() {
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