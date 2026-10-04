package com.dank420girl.core.listener;

import com.dank420girl.core.effect.FireworkService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Fireworks of effects are only for show: their explosions never hurt anybody.
 */
public final class FireworkListener implements Listener {
    private final FireworkService fireworks;

    public FireworkListener(FireworkService fireworks) {
        this.fireworks = fireworks;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (fireworks.isOurs(event.getDamager())) {
            event.setCancelled(true);
        }
    }
}
