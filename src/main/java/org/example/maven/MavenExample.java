package org.example.maven;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pluginlib.DependentJavaPlugin;

import java.time.Duration;
import java.util.UUID;

public final class MavenExample extends DependentJavaPlugin implements Listener {

    private static final Cache<UUID, String> UUID_TO_NAME = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(6))
            .build(); // some example use of caffeine, where we want to do a uuid -> name mapping

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID_TO_NAME.put(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
