package lytblu7.autonexus.server;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import lytblu7.autonexus.server.storage.ServerRedisManager;
import java.util.UUID;

public class ServerListener implements Listener {

    private final NexusServer plugin;

    public ServerListener(NexusServer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();
        
        // 1. Save Name Index for offline lookup (CRITICAL)
        if (plugin.getRedisManager() instanceof ServerRedisManager) {
            ((ServerRedisManager) plugin.getRedisManager()).saveNameMapping(playerName, uuid);
            // ADDED: Add to global online players set for TabComplete
            ((ServerRedisManager) plugin.getRedisManager()).addOnlinePlayer(playerName);
        }
        
        // 2. Async load from Redis
        // MANDATORY: Remove from local cache to force a fresh fetch from Redis!
        // This ensures we get the LATEST balance/metadata even if the player was previously cached.
        plugin.removeCachedPlayer(uuid);
        
        plugin.getLogger().info("[AutoNexus] Checking Redis data for " + playerName + " (" + uuid + ")");
        
        plugin.getPlayer(uuid).thenAccept(nexusPlayer -> {
            if (nexusPlayer == null) {
                 // CASE 1: New Player (Never joined network before)
                 nexusPlayer = new lytblu7.autonexus.common.model.NexusPlayer(uuid, playerName, plugin.getResolvedServerName());
                 nexusPlayer.setMetadata("serverGroup", plugin.getServerGroup());
                 plugin.getLogger().info("[AutoNexus] Created NEW NexusPlayer data for " + playerName);
                 // Only save if it's a NEW player to initialize their record
                 plugin.savePlayer(nexusPlayer);
            } else {
                 // CASE 2: Existing Player (Has Redis data)
                 // FORCE UPDATE local session data to ensure Redis knows where they are
                 nexusPlayer.setCurrentServer(plugin.getResolvedServerName());
                 nexusPlayer.setLastSeenName(playerName);
                 nexusPlayer.setMetadata("serverGroup", plugin.getServerGroup());
                 
                 // CRITICAL: We MUST save this session update immediately so Proxy/other servers know 
                 // the player is here (for PMs, strict isolation, etc.)
                 // Since we just fetched this object from Redis (nexusPlayer is fresh), 
                 // saving it back is safe and won't overwrite metadata with stale local state.
                 plugin.savePlayer(nexusPlayer);
                 
                 plugin.getLogger().info("[AutoNexus] Updated session for " + playerName + ": Server=" + plugin.getResolvedServerName() + ", Group=" + plugin.getServerGroup());

                 // Debug logging for balances
                java.util.Map<String, String> meta = nexusPlayer.getMetadata();
                plugin.getLogger().info("[AutoNexus] Loaded existing data for " + playerName + " metadata=" + meta);
                StringBuilder balances = new StringBuilder();
                for (java.util.Map.Entry<String, String> e : meta.entrySet()) {
                    if (e.getKey() != null && e.getKey().startsWith("balance_")) {
                        if (balances.length() > 0) balances.append(", ");
                        balances.append(e.getKey()).append("=").append(e.getValue());
                    }
                }
                if (balances.length() > 0) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("[DEBUG] Join Fetch: balances(" + playerName + "): " + balances);
            }
                }
            }

            // Cache it locally
            plugin.cachePlayer(nexusPlayer);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Retrieve and remove from cache
        lytblu7.autonexus.common.model.NexusPlayer cached = plugin.removeCachedPlayer(uuid);
        
        if (cached != null) {
            plugin.getLogger().info("[AutoNexus] Saving data for " + event.getPlayer().getName() + " on Quit.");
            plugin.savePlayer(cached);
        }
        
        if (plugin.isDebug() && plugin.getRedisManager() instanceof ServerRedisManager) {
            plugin.getLogger().info("[DEBUG] Marking " + event.getPlayer().getName() + " as offline in Redis.");
        }
        if (plugin.getRedisManager() instanceof ServerRedisManager) {
            ((ServerRedisManager) plugin.getRedisManager()).setPlayerOffline(uuid);
            // ADDED: Remove from global online players set
            ((ServerRedisManager) plugin.getRedisManager()).removeOnlinePlayer(event.getPlayer().getName());
        }
    }

}
