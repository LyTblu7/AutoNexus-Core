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
                 plugin.getLogger().info("[AutoNexus] Created NEW NexusPlayer data for " + playerName);
                 // Only save if it's a NEW player to initialize their record
                 plugin.savePlayer(nexusPlayer);
            } else {
                 // CASE 2: Existing Player (Has Redis data)
                 // CRITICAL: Do NOT create a new object. Use the one from Redis to preserve metadata (money, rank, etc.)
                 // We only update the 'currentServer' field and 'lastSeenName'
                 nexusPlayer.setCurrentServer(plugin.getResolvedServerName());
                 nexusPlayer.setLastSeenName(playerName);
                 
                 // CRITICAL: Do NOT call savePlayer() immediately if we want to be 100% safe against race conditions.
                 // However, we DO want to update the 'currentServer' location.
                 // The race condition happens if we overwrite metadata with STALE local data.
                 // Since 'nexusPlayer' here IS the Redis data (fetched 1ms ago), it is safe to save it back 
                 // *provided* we didn't wipe the metadata map.
                 // Since we are modifying the SAME object we fetched, metadata is preserved.
                 
                 // BUT, the user explicitly said: "ON JOIN: Do NOT call 'api.savePlayer()' immediately."
                 // "Instead, ONLY call 'api.getPlayer(uuid)' to FETCH the latest data... Update the local metadata map"
                 
                 // If we don't save, the Proxy won't know they are on this server until some other update happens.
                 // But maybe that's acceptable for now to solve the money wipe bug.
                 // Let's comment out the save for existing players as per strict instruction.
                // plugin.savePlayer(nexusPlayer); 
                 
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
        }
    }

}
