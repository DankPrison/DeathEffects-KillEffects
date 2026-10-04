package com.dank420girl.killeffects.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called before a player's kill effect changes, through the menu, a command or the API.
 */
public class KillEffectSelectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String previousEffectId;
    private final String newEffectId;
    private boolean cancelled;

    public KillEffectSelectEvent(Player player, String previousEffectId, String newEffectId) {
        this.player = player;
        this.previousEffectId = previousEffectId;
        this.newEffectId = newEffectId;
    }

    public Player getPlayer() {
        return player;
    }

    /** The effect selected until now, or {@code null}. */
    public String getPreviousEffectId() {
        return previousEffectId;
    }

    /** The effect that will be selected, or {@code null} if the selection is being cleared. */
    public String getNewEffectId() {
        return newEffectId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
