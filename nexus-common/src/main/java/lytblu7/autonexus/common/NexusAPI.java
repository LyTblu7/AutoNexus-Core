package lytblu7.autonexus.common;

import lytblu7.autonexus.common.model.NexusPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The main API for interacting with the AutoNexus network.
 * <p>
 * This singleton serves as the entry point for other plugins to:
 * <ul>
 *     <li>Access global player data (cross-server)</li>
 *     <li>Manage persistent metadata</li>
 *     <li>Dispatch commands and packets across the network</li>
 *     <li>Check server status and availability</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public interface NexusAPI {
    /**
     * Retrieves the singleton instance of the NexusAPI.
     * @return The active API instance.
     * @throws IllegalStateException if the API is not initialized.
     */
    static NexusAPI get() {
        if (InstanceHolder.instance == null) {
            throw new IllegalStateException("NexusAPI is not initialized yet!");
        }
        return InstanceHolder.instance;
    }

    /**
     * Sends a custom network packet to a specific target server.
     * 
     * @param targetServer The name of the target server (as defined in proxy config), or "*" for all servers.
     * @param packet The {@link NexusPacket} to send.
     */
    void sendPacket(String targetServer, NexusPacket packet);

    /**
     * Asynchronously retrieves a NexusPlayer by their unique ID.
     * <p>
     * This method fetches data from the central storage (Redis/Cache).
     * 
     * @param uuid The unique identifier of the player.
     * @return A {@link CompletableFuture} that completes with the {@link NexusPlayer}, or null if not found.
     */
    CompletableFuture<NexusPlayer> getPlayer(UUID uuid);

    /**
     * Updates a metadata value for a player globally.
     * <p>
     * This update is propagated to the player's current server and saved to persistent storage.
     * 
     * @param uuid The player's unique identifier.
     * @param key The metadata key (e.g., "coins", "rank").
     * @param value The value to store.
     */
    void updateMetadata(UUID uuid, String key, String value);

    /**
     * Checks if a specific server is currently online and accessible.
     * 
     * @param serverName The name of the server to check.
     * @return true if the server is registered and has active connections (if applicable).
     */
    boolean isServerOnline(String serverName);
    
    class InstanceHolder {
        private static NexusAPI instance;
        public static void set(NexusAPI api) { instance = api; }
    }
}
