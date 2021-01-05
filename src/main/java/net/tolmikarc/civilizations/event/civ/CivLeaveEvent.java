/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.event.civ;

import net.tolmikarc.civilizations.model.Civilization;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

public class CivLeaveEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();

	private final Civilization civ;
	private final PlayerMoveEvent moveEvent;


	public CivLeaveEvent(Civilization civ, PlayerMoveEvent moveEvent) {
		this.civ = civ;
		this.moveEvent = moveEvent;
	}


	public Player getPlayer() {
		return moveEvent.getPlayer();
	}

	public Civilization getCiv() {
		return civ;
	}

	public PlayerMoveEvent getMoveEvent() {
		return moveEvent;
	}

	@Override
	public boolean isCancelled() {
		return moveEvent.isCancelled();
	}

	@Override
	public void setCancelled(boolean b) {
		moveEvent.setCancelled(b);
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
