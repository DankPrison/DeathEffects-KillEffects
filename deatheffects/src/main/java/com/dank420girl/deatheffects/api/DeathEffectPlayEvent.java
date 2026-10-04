package com.dank420girl.deatheffects.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called before a death effect plays. Cancel it to stop the effect (for example in minigame arenas),
 * or change the effect or where it plays.
 */
public class DeathEffectPlayEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Player killer;
    private String effectId;
    private Location location;
    private boolean cancelled;

    public DeathEffectPlayEvent(Player player, Player killer, String effectId, Location location) {
        this.player = player;
        this.killer = killer;
        this.effectId = effectId;
        this.location = location;
    }

    /** The player who died. */
    public Player getPlayer() {
        return player;
    }

    /** The player who killed them, or {@code null} for other deaths. */
    public Player getKiller() {
        return killer;
    }

    /** The id of the effect that will play. */
    public String getEffectId() {
        return effectId;
    }

    /** Plays another effect instead; unknown ids cancel the effect. */
    public void setEffectId(String effectId) {
        this.effectId = effectId;
    }

    /** Where the effect plays (a copy can be changed freely). */
    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("location must have a world");
        }
        this.location = location;
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
