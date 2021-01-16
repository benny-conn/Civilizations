/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.event;

import net.tolmikarc.civilizations.model.Civ;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CivLeaveEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();

	private final Civ civ;
	private final Player player;
	private boolean cancelled;
	private final Location from;
	private final Location to;


	public CivLeaveEvent(Civ civ, Player player, Location from, Location to) {
		this.civ = civ;
		this.player = player;
		this.from = from;
		this.to = to;
	}


	public Player getPlayer() {
		return player;
	}

	public Civ getCiv() {
		return civ;
	}

	public Location getFrom() {
		return from;
	}

	public Location getTo() {
		return to;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean b) {
		cancelled = b;
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