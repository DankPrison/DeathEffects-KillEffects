package com.dank420girl.killeffects.api;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called before a kill effect plays. Cancel it to stop the effect (for example in minigame arenas),
 * or change the effect or where it plays.
 */
public class KillEffectPlayEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player killer;
    private final LivingEntity victim;
    private String effectId;
    private Location location;
    private boolean cancelled;

    public KillEffectPlayEvent(Player killer, LivingEntity victim, String effectId, Location location) {
        this.killer = killer;
        this.victim = victim;
        this.effectId = effectId;
        this.location = location;
    }

    /** The player whose kill effect plays. */
    public Player getKiller() {
        return killer;
    }

    /** The player or mob that was killed (can be the killer for self-kills). */
    public LivingEntity getVictim() {
        return victim;
    }

    /** The id of the effect that will play. */
    public String getEffectId() {
        return effectId;
    }

    /** Plays another effect instead; unknown ids cancel the effect. */
    public void setEffectId(String effectId) {
        this.effectId = effectId;
    }

    /** Where the effect plays: where the victim died. */
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
