package lytblu7.autonexus.common.meta;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.List;

public interface MetadataManager {
    CompletableFuture<Double> modifyMetadata(UUID player, String key, double delta);
    CompletableFuture<Double> modifyMetadata(UUID player, String key, double delta, String group);
    
    default CompletableFuture<Double> modifyMetadata(UUID player, String key, double delta, String group, String reason) {
        return modifyMetadata(player, key, delta, group);
    }
    CompletableFuture<Void> setMetadata(UUID player, String key, Object value);
    CompletableFuture<Void> setMetadata(UUID player, String key, Object value, String group);
    CompletableFuture<List<String>> getGlobalHistory(UUID player);
}
