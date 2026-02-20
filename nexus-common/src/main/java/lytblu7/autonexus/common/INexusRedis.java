package lytblu7.autonexus.common;

import java.util.concurrent.CompletableFuture;

/**
 * Restricted Redis operations exposed to the API.
 */
public interface INexusRedis {
    CompletableFuture<Long> publish(String channel, String message);
    CompletableFuture<String> get(String key);
    CompletableFuture<Void> set(String key, String value);
    CompletableFuture<java.util.UUID> getUuidByName(String name);
    
    /**
     * Fetches all fields of a Redis hash as a Map.
     */
    CompletableFuture<java.util.Map<String, String>> hgetall(String key);
}
