package lytblu7.autonexus.common;

import lytblu7.autonexus.common.model.NexusPlayer;
import lytblu7.autonexus.common.model.LeaderboardEntry;
import lytblu7.autonexus.common.model.ServerInfo;
import lytblu7.autonexus.common.model.NexusProfile;
import lytblu7.autonexus.common.event.NexusEventBus;
import lytblu7.autonexus.common.meta.MetadataManager;
import lytblu7.autonexus.common.api.NexusMessageListener;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The main API for interacting with the AutoNexus network.
 */
public interface INexusAPI {
    /**
     * Asynchronously retrieves a NexusPlayer by their unique ID.
     * @param uuid The player's UUID.
     * @return A future containing the NexusPlayer, or null if not found.
     */
    CompletableFuture<NexusPlayer> getPlayer(UUID uuid);

    /**
     * Asynchronously resolves a player's UUID from their name.
     * @param name The player's name.
     * @return A future containing the UUID, or null if not found.
     */
    CompletableFuture<UUID> getUuid(String name);

    /**
     * Registers a network-wide command on the Proxy.
     * When executed on the Proxy, it will be dispatched to the specified target group.
     * @param name The command name (e.g., "eco").
     * @param permission The required permission (or null for none).
     * @param targetGroup The target group (e.g., "ALL", "survival", "lobby").
     */
    void registerNetworkCommand(String name, String permission, String targetGroup);

    /**
     * Asynchronously saves a NexusPlayer to persistent storage.
     * @param player The player object to save.
     * @return A future that completes when the save operation is finished.
     */
    CompletableFuture<Void> savePlayer(NexusPlayer player);

    /**
     * Dispatches a command across the network.
     * @param target The target identifier (e.g., server name, "ALL", "group:name").
     * @param command The command to execute.
     */
    void dispatchCommand(String target, String command);

    /**
     * Dispatches a command across the network on behalf of a sender.
     * @param target The target identifier.
     * @param command The command to execute.
     * @param sender The UUID of the sender (or null for console).
     */
    void dispatchCommand(String target, String command, UUID sender);

    /**
     * Returns a restricted version of Redis operations.
     * @return The INexusRedis instance.
     */
    INexusRedis getRedisManager();

    default MetadataManager getMetadataManager() {
        throw new UnsupportedOperationException("MetadataManager is not supported on this platform");
    }

    default NexusEventBus getEventBus() {
        throw new UnsupportedOperationException("EventBus is not supported on this platform");
    }
    
    /**
     * Returns the current server group as seen by the Core.
     * @return The server group name (e.g. "lobby", "survival").
     */
    String getServerGroup();

    /**
     * Returns the unique name of this server instance.
     * @return The server name (e.g. "lobby-1", "survival-2").
     */
    String getServerName();
    
    /**
     * Checks if this server is in the specified server group.
     * @param otherGroup The group to check against.
     * @return true if the server is in the group (case-insensitive).
     */
    default boolean isSameGroup(String otherGroup) {
        if (otherGroup == null) return false;
        return getServerGroup().equalsIgnoreCase(otherGroup);
    }

    /**
     * Retrieves a list of names of all players currently online across the entire network.
     * @return A future containing the list of player names.
     */
    default CompletableFuture<java.util.List<String>> getGlobalPlayerNames() {
        CompletableFuture<java.util.List<String>> f = new CompletableFuture<>();
        f.completeExceptionally(new UnsupportedOperationException("getGlobalPlayerNames is not supported on this platform"));
        return f;
    }

    /**
     * Retrieves a list of names of players currently online on servers within the specified group.
     * @param group The server group to filter by (e.g. "lobby").
     * @return A future containing the list of player names.
     */
    default CompletableFuture<java.util.List<String>> getGroupPlayerNames(String group) {
        CompletableFuture<java.util.List<String>> f = new CompletableFuture<>();
        f.completeExceptionally(new UnsupportedOperationException("getGroupPlayerNames is not supported on this platform"));
        return f;
    }

    /**
     * Retrieves a cached list of all global player names.
     * This method returns immediately with the last known list of players.
     * The cache is updated asynchronously in the background.
     * @return A list of player names (may be empty if cache not yet populated).
     */
    default java.util.List<String> getCachedGlobalPlayerNames() {
        return java.util.Collections.emptyList();
    }
    
    /**
     * Retrieves a cached list of player names in a specific server group.
     * This method returns immediately with the last known list of players.
     * The cache is updated asynchronously in the background.
     * @param group The server group to filter by.
     * @return A list of player names (may be empty if cache not yet populated).
     */
    default java.util.List<String> getCachedGroupPlayerNames(String group) {
        return java.util.Collections.emptyList();
    }

    default CompletableFuture<Double> incrementMetadata(UUID uuid, String field, double delta, String reason) {
        CompletableFuture<Double> f = new CompletableFuture<>();
        f.completeExceptionally(new UnsupportedOperationException("incrementMetadata is not supported on this platform"));
        return f;
    }
    
    default CompletableFuture<Double> transferMetadata(UUID from, UUID to, String field, double amount, String reason) {
        CompletableFuture<Double> f = new CompletableFuture<>();
        f.completeExceptionally(new UnsupportedOperationException("transferMetadata is not supported on this platform"));
        return f;
    }
    
    default CompletableFuture<Void> updateBaltop(UUID uuid, String group, double balance, String playerName) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        f.completeExceptionally(new UnsupportedOperationException("updateBaltop is not supported on this platform"));
        return f;
    }

    default CompletableFuture<java.util.List<LeaderboardEntry>> getTop(String group, int offset, int limit) {
        CompletableFuture<java.util.List<LeaderboardEntry>> f = new CompletableFuture<>();
        f.completeExceptionally(new UnsupportedOperationException("getTop is not supported on this platform"));
        return f;
    }

    default CompletableFuture<java.util.List<String>> getGlobalHistory(UUID uuid, int limit) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }
    
    default void publishMessage(String channel, String message) {
        throw new UnsupportedOperationException("publishMessage is not supported on this platform");
    }
    
    default void registerMessageListener(String channel, NexusMessageListener listener) {
        throw new UnsupportedOperationException("registerMessageListener is not supported on this platform");
    }
    
    default void unregisterMessageListener(String channel, NexusMessageListener listener) {
        throw new UnsupportedOperationException("unregisterMessageListener is not supported on this platform");
    }
    
    default java.util.List<ServerInfo> getServers() {
        throw new UnsupportedOperationException("getServers is not supported on this platform");
    }
    
    default ServerInfo getServer(String name) {
        throw new UnsupportedOperationException("getServer is not supported on this platform");
    }

    default java.util.concurrent.CompletableFuture<NexusProfile> getPlayerProfile(java.util.UUID uuid) {
        throw new UnsupportedOperationException("getPlayerProfile is not supported on this platform");
    }

    default java.util.concurrent.CompletableFuture<java.util.UUID> getPlayerIdByName(String name) {
        throw new UnsupportedOperationException("getPlayerIdByName is not supported on this platform");
    }

    default void sendPlayerToServer(java.util.UUID playerUuid, String serverName) {
        throw new UnsupportedOperationException("sendPlayerToServer is not supported on this platform");
    }
}
