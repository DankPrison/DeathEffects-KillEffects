package com.dank420girl.core.listener;

import com.dank420girl.core.EffectsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads a player's selection when they join and forgets it when they leave.
 */
public final class PlayerListener implements Listener {
    private final EffectsPlugin plugin;

    public PlayerListener(EffectsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.loadSelection(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.unloadSelection(event.getPlayer().getUniqueId());
    }
}
