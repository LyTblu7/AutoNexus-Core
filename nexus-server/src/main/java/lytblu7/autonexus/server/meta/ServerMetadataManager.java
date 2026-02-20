package lytblu7.autonexus.server.meta;

import lytblu7.autonexus.common.meta.MetadataManager;
import lytblu7.autonexus.server.storage.ServerRedisManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ServerMetadataManager implements MetadataManager {
    private final ServerRedisManager redisManager;
    private final Supplier<String> groupSupplier;
    private final Logger logger;

    public ServerMetadataManager(ServerRedisManager redisManager, Supplier<String> groupSupplier, Logger logger) {
        this.redisManager = redisManager;
        this.groupSupplier = groupSupplier;
        this.logger = logger;
    }

    private String resolveGroup(String group) {
        if (group != null && !group.isBlank()) {
            return group;
        }
        String g = groupSupplier != null ? groupSupplier.get() : null;
        return g != null && !g.isBlank() ? g : "default";
    }

    private String resolveKey(String key, String group) {
        return key + "_" + group;
    }

    private <T> CompletableFuture<T> withRetry(java.util.function.Supplier<CompletableFuture<T>> supplier, int attempts) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executeWithRetry(supplier, attempts, future);
        return future;
    }

    private <T> void executeWithRetry(java.util.function.Supplier<CompletableFuture<T>> supplier, int remaining, CompletableFuture<T> target) {
        try {
            supplier.get().whenComplete((result, error) -> {
                if (error == null) {
                    target.complete(result);
                } else {
                    if (remaining > 1) {
                        logger.warning("[AutoNexus] Server metadata operation failed, retrying: " + error.getMessage());
                        executeWithRetry(supplier, remaining - 1, target);
                    } else {
                        target.completeExceptionally(error instanceof CompletionException ? error.getCause() : error);
                    }
                }
            });
        } catch (Exception e) {
            if (remaining > 1) {
                logger.warning("[AutoNexus] Server metadata operation failed before dispatch, retrying: " + e.getMessage());
                executeWithRetry(supplier, remaining - 1, target);
            } else {
                target.completeExceptionally(e);
            }
        }
    }

    @Override
    public CompletableFuture<Double> modifyMetadata(UUID player, String key, double delta) {
        String group = resolveGroup(null);
        return modifyMetadata(player, key, delta, group);
    }

    @Override
    public CompletableFuture<Double> modifyMetadata(UUID player, String key, double delta, String group) {
        return modifyMetadata(player, key, delta, group, "SYSTEM");
    }
    
    @Override
    public CompletableFuture<Double> modifyMetadata(UUID player, String key, double delta, String group, String reason) {
        String resolvedGroup = resolveGroup(group);
        String resolvedKey = resolveKey(key, resolvedGroup);
        String txType;
        if (delta > 0) {
            txType = "CREDIT";
        } else if (delta < 0) {
            txType = "DEBIT";
        } else {
            txType = "ADJUST";
        }
        String serverSource = resolvedGroup;
        String otherPlayer = "";
        String resolvedReason = (reason != null && !reason.isEmpty()) ? reason : txType;
        return withRetry(
                () -> redisManager.incrementMetadataAtomic(
                        player,
                        resolvedKey,
                        delta,
                        resolvedGroup,
                        serverSource,
                        txType,
                        otherPlayer,
                        resolvedReason
                ),
                3
        ).thenApply(result -> {
            logger.fine("[AutoNexus] Server modifyMetadata completed for key=" + resolvedKey + " value=" + result);
            if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(result)) {
                return Double.NaN;
            }
            double newValue;
            try {
                newValue = Double.parseDouble(result);
            } catch (NumberFormatException e) {
                logger.warning("[AutoNexus] Failed to parse server metadata value for " + resolvedKey + ": " + result);
                return Double.NaN;
            }
            return newValue;
        });
    }

    @Override
    public CompletableFuture<Void> setMetadata(UUID player, String key, Object value) {
        String group = resolveGroup(null);
        return setMetadata(player, key, value, group);
    }

    @Override
    public CompletableFuture<Void> setMetadata(UUID player, String key, Object value, String group) {
        String resolvedGroup = resolveGroup(group);
        String resolvedKey = resolveKey(key, resolvedGroup);
        Map<String, String> updates = new HashMap<>();
        updates.put(resolvedKey, value != null ? String.valueOf(value) : null);
        return withRetry(() -> redisManager.updatePlayerMetadata(player, updates), 3).thenApply(v -> null);
    }

    @Override
    public CompletableFuture<List<String>> getGlobalHistory(UUID player) {
        return redisManager.getHistory(player, 10).thenApply(list -> {
            logger.fine("[AutoNexus] Server getGlobalHistory fetched " + (list != null ? list.size() : 0) + " entries");
            return list;
        });
    }
}
