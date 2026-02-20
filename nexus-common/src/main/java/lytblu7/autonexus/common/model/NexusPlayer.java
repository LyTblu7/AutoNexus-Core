package lytblu7.autonexus.common.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a player in the Nexus network.
 * Stores global state including location and custom metadata.
 */
public class NexusPlayer {
    private final UUID uuid;
    private volatile String lastSeenName;
    private volatile String currentServer;
    private final Map<String, String> metadata;

    public NexusPlayer(UUID uuid, String lastSeenName, String currentServer) {
        this.uuid = uuid;
        this.lastSeenName = lastSeenName;
        this.currentServer = currentServer;
        this.metadata = new ConcurrentHashMap<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastSeenName() {
        return lastSeenName;
    }

    public void setLastSeenName(String lastSeenName) {
        this.lastSeenName = lastSeenName;
    }

    public String getCurrentServer() {
        return currentServer;
    }

    public void setCurrentServer(String currentServer) {
        this.currentServer = currentServer;
    }

    /**
     * Gets a read-only view of the player's metadata.
     * @return Map of metadata keys and values
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    /**
     * Sets a metadata value for this player.
     * This is thread-safe.
     * @param key The metadata key
     * @param value The value to store
     */
    public void setMetadata(String key, String value) {
        this.metadata.put(key, value);
    }
    
    /**
     * Retrieves a metadata value.
     * @param key The metadata key
     * @return The value, or null if not present
     */
    public String getMetadata(String key) {
        return this.metadata.get(key);
    }

    /**
     * Checks if a specific metadata key exists.
     * @param key The metadata key
     * @return true if the key exists
     */
    public boolean hasMetadata(String key) {
        return this.metadata.containsKey(key);
    }

    /**
     * Removes a metadata entry.
     * @param key The metadata key to remove
     */
    public void removeMetadata(String key) {
        this.metadata.remove(key);
    }
}
