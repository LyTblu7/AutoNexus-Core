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
     * Default implementation returns "default" for compatibility.
     */
    default String getServerGroup() {
        return "default";
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
