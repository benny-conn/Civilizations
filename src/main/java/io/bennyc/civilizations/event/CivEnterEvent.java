package io.bennyc.civilizations.event;


import io.bennyc.civilizations.model.Civilization;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CivEnterEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Civilization civ;
    private final Player player;
    private final Location from;
    private final Location to;
    private boolean cancelled;


    public CivEnterEvent(Civilization civ, Player player, Location from, Location to) {
        this.civ = civ;
        this.player = player;
        this.from = from;
        this.to = to;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public Player getPlayer() {
        return player;
    }

    public Civilization getCiv() {
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

}