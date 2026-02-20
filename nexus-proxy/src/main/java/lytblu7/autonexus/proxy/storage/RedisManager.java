package lytblu7.autonexus.proxy.storage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lytblu7.autonexus.common.api.NexusMessageListener;
import lytblu7.autonexus.common.model.ServerInfo;
import lytblu7.autonexus.common.model.NexusPlayer;
import lytblu7.autonexus.common.model.NexusProfile;
import lytblu7.autonexus.common.redis.NexusKeyFactory;
import lytblu7.autonexus.common.redis.RedisScripts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class RedisManager implements lytblu7.autonexus.common.INexusRedis {
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisAsyncCommands<String, String> async;
    private final Gson gson = new Gson();
    
    private boolean isConnected = false;
    private final Logger logger = Logger.getLogger("RedisManager");
    private String namespace = "global";
    private boolean debug = false;
    private final ConcurrentHashMap<String, List<NexusMessageListener>> messageListeners = new ConcurrentHashMap<>();
    
    private void debugLog(String msg) {
        if (debug) logger.info("[DEBUG] " + msg);
    }

    // ... (rest of methods)

    @Override
    public CompletableFuture<String> get(String key) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        return async.get(key).toCompletableFuture();
    }

    @Override
    public CompletableFuture<Void> set(String key, String value) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        return async.set(key, value).toCompletableFuture().thenApply(v -> null);
    }

    public void registerMessageListener(String channel, NexusMessageListener listener) {
        if (channel == null || listener == null) return;
        messageListeners.computeIfAbsent(channel, c -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unregisterMessageListener(String channel, NexusMessageListener listener) {
        if (channel == null || listener == null) return;
        List<NexusMessageListener> list = messageListeners.get(channel);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                messageListeners.remove(channel);
            }
        }
    }

    public CompletableFuture<Long> publishMessage(String channel, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "PLUGIN_MESSAGE");
        obj.addProperty("subchannel", channel);
        obj.addProperty("payload", message);
        String json = gson.toJson(obj);
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        return async.publish("autonexus:network", json).toCompletableFuture();
    }

    public void dispatchPluginMessage(String channel, String payload) {
        List<NexusMessageListener> listeners = messageListeners.get(channel);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (NexusMessageListener listener : listeners) {
            listener.onPluginMessage(channel, payload);
        }
    }

    public ServerInfo getServer(String name) {
        if (!isConnected || name == null || name.isBlank()) {
            return null;
        }
        String key = "autonexus:server:" + name;
        String json = connection.sync().get(key);
        if (json == null) return null;
        try {
            return gson.fromJson(json, ServerInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    public List<ServerInfo> getServers() {
        List<ServerInfo> out = new ArrayList<>();
        if (!isConnected) return out;
        List<String> keys = connection.sync().keys("autonexus:server:*");
        if (keys == null) return out;
        for (String k : keys) {
            try {
                String json = connection.sync().get(k);
                if (json != null) {
                    ServerInfo info = gson.fromJson(json, ServerInfo.class);
                    if (info != null) out.add(info);
                }
            } catch (Exception ignored) {}
        }
        return out;
    }


    public void connect(String uri) {
        try {
            client = RedisClient.create(uri);
            client.setOptions(io.lettuce.core.ClientOptions.builder().autoReconnect(true).build());
            connection = client.connect();
            pubSubConnection = client.connectPubSub();
            async = connection.async();
            isConnected = true;
            debugLog("Connected to Redis (namespace=" + namespace + ")");
        } catch (Exception e) {
            isConnected = false;
            logger.severe("[AutoNexus] FATAL: Redis connection failed! AutoNexus requires a running Redis server. Check your config.toml");
            throw new RuntimeException("Redis connection failed", e);
        }
    }

    public void shutdown() {
        if (isConnected) {
            if (connection != null) connection.close();
            if (pubSubConnection != null) pubSubConnection.close();
            if (client != null) client.shutdown();
        }
    }

    private String onlinePlayersKey() {
        return "autonexus:" + namespace + ":online_players";
    }

    public java.util.concurrent.CompletableFuture<Void> clearOnlinePlayers() {
        if (!isConnected) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return async.del(onlinePlayersKey()).toCompletableFuture().thenApply(v -> null);
    }

    public java.util.concurrent.CompletableFuture<Void> touchOnlinePlayersTtl(int seconds) {
        if (!isConnected || seconds <= 0) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return async.expire(onlinePlayersKey(), seconds).toCompletableFuture().thenApply(v -> null);
    }

    public java.util.concurrent.CompletableFuture<Void> addOnlinePlayer(String name) {
        if (!isConnected || name == null || name.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        String key = onlinePlayersKey();
        return async.sadd(key, name).toCompletableFuture().thenApply(v -> null);
    }

    public java.util.concurrent.CompletableFuture<Void> removeOnlinePlayer(String name) {
        if (!isConnected || name == null || name.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        String key = onlinePlayersKey();
        return async.srem(key, name).toCompletableFuture().thenApply(v -> null);
    }

    public java.util.List<String> getOnlinePlayerNames() {
        if (!isConnected) {
            return java.util.Collections.emptyList();
        }
        String key = onlinePlayersKey();
        java.util.Set<String> raw = connection.sync().smembers(key);
        if (raw == null || raw.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return new java.util.ArrayList<>(raw);
    }
    
    public void setNamespace(String namespace) {
        this.namespace = (namespace != null && !namespace.isBlank()) ? namespace : "global";
    }
    
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void subscribe(String channel, java.util.function.Consumer<String> messageHandler) {
        if (!isConnected || pubSubConnection == null) {
            throw new IllegalStateException("Redis PubSub is not connected");
        }
        
        pubSubConnection.addListener(new RedisPubSubListener<String, String>() {
            @Override
            public void message(String ch, String message) {
                if (channel.equals(ch)) {
                    logger.info("[PROXY-IN] Raw Redis message received on channel " + ch + ": " + message);
                    debugLog("Pub/Sub message on " + ch);
                    messageHandler.accept(message);
                }
            }
            @Override public void message(String pattern, String channel, String message) {}
            @Override public void subscribed(String channel, long count) {}
            @Override public void psubscribed(String pattern, long count) {}
            @Override public void unsubscribed(String channel, long count) {}
            @Override public void punsubscribed(String pattern, long count) {}
        });
        
        pubSubConnection.async().subscribe(channel);
    }

    public CompletableFuture<Void> updatePlayerLocation(UUID uuid, String name, String server) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String key = keys.player(uuid);
        String nameKey = keys.nameToUuid(name);
        String indexKey = "autonexus:name2uuid:" + (name != null ? name.toLowerCase() : "");
        
        String script = RedisScripts.UPDATE_PLAYER_LOCATION;

        async.set(nameKey, uuid.toString());
        async.set(indexKey, uuid.toString());

        return async.eval(script, io.lettuce.core.ScriptOutputType.INTEGER, new String[]{key}, server, name, uuid.toString())
            .toCompletableFuture()
            .thenApply(v -> null);
    }

    public CompletableFuture<Void> savePlayer(NexusPlayer player) {
        // Proxy should NEVER overwrite the full object to avoid data loss (metadata/economy).
        // This method is now a safe wrapper around updatePlayerLocation.
        return updatePlayerLocation(player.getUuid(), player.getLastSeenName(), player.getCurrentServer());
    }
    
    /**
     * Updates specific metadata fields for a player (e.g., balance) safely using Lua.
     * This merges the provided metadata into the existing metadata map in Redis.
     */
    public CompletableFuture<Void> updatePlayerMetadata(UUID uuid, Map<String, String> metadataUpdates) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String key = keys.player(uuid);
        String jsonUpdates = gson.toJson(metadataUpdates);
        
        String script = RedisScripts.UPDATE_PLAYER_METADATA;
            
        return async.eval(script, io.lettuce.core.ScriptOutputType.INTEGER, new String[]{key}, jsonUpdates)
            .toCompletableFuture()
            .thenApply(v -> null);
    }

    public CompletableFuture<NexusPlayer> loadPlayer(UUID uuid) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }

        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String key = keys.player(uuid);
        debugLog("GET " + key);
        return async.get(key).toCompletableFuture().thenApply(json -> {
            if (json == null) return null;
            return gson.fromJson(json, NexusPlayer.class);
        });
    }
    
    public CompletableFuture<UUID> getUuidByName(String name) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String nameKey = keys.nameToUuid(name);
        return async.get(nameKey).toCompletableFuture().thenApply(uuidStr -> {
            if (uuidStr == null) return null;
            try {
                return UUID.fromString(uuidStr);
            } catch (Exception e) {
                return null;
            }
        });
    }

    public CompletableFuture<Void> setPlayerOffline(UUID uuid) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String key = keys.player(uuid);
        return async.get(key).toCompletableFuture().thenCompose(json -> {
            if (json == null) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                JsonObject root = gson.fromJson(json, JsonObject.class);
                root.addProperty("currentServer", "offline");
                String updated = gson.toJson(root);
                return async.set(key, updated).toCompletableFuture().thenApply(v -> null);
            } catch (Exception e) {
                return CompletableFuture.completedFuture(null);
            }
        });
    }
    
    public CompletableFuture<NexusProfile> getPlayerProfile(UUID uuid) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String key = keys.player(uuid);
        return async.get(key).toCompletableFuture().thenApply(json -> {
            if (json == null) return null;
            try {
                JsonObject root = gson.fromJson(json, JsonObject.class);
                String name = root.has("lastSeenName") ? root.get("lastSeenName").getAsString() : null;
                String currentServer = root.has("currentServer") ? root.get("currentServer").getAsString() : null;
                return new NexusProfile(uuid, name, currentServer);
            } catch (Exception e) {
                return null;
            }
        });
    }

    public CompletableFuture<UUID> getPlayerIdByName(String name) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String lower = name != null ? name.toLowerCase() : "";
        String indexKey = "autonexus:name2uuid:" + lower;
        return async.get(indexKey).toCompletableFuture().thenCompose(value -> {
            if (value != null && !value.isEmpty()) {
                try {
                    return CompletableFuture.completedFuture(UUID.fromString(value));
                } catch (Exception e) {
                    return CompletableFuture.completedFuture(null);
                }
            }
            NexusKeyFactory keys = NexusKeyFactory.of(namespace);
            String legacyKey = keys.nameToUuid(name);
            return async.get(legacyKey).toCompletableFuture().thenApply(legacy -> {
                if (legacy == null || legacy.isEmpty()) return null;
                try {
                    return UUID.fromString(legacy);
                } catch (Exception e) {
                    return null;
                }
            });
        });
    }
    
    public CompletableFuture<String> incrementMetadataAtomic(UUID uuid, String field, double delta) {
        return incrementMetadataAtomic(uuid, field, delta, null, "proxy", "generic", "", "SYSTEM");
    }

    public CompletableFuture<String> incrementMetadataAtomic(UUID uuid, String field, double delta, String group, String serverSource, String transactionType, String otherPlayerUuid, String reason) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String playerKey = keys.player(uuid);
        String historyKey = keys.history(uuid);
        String baltopKey = keys.economyBaltop(group);
        boolean isBalanceField = field != null && field.toLowerCase().startsWith("balance");
        String script = RedisScripts.INCREMENT_METADATA_ATOMIC;
        String isBalance = isBalanceField ? "1" : "0";
        String playerUuid = uuid.toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String resolvedReason = (reason != null && !reason.isEmpty()) ? reason : "SYSTEM";
        return async.eval(
                script,
                io.lettuce.core.ScriptOutputType.VALUE,
                new String[]{playerKey, historyKey, baltopKey},
                field,
                String.valueOf(delta),
                isBalance,
                serverSource != null ? serverSource : "",
                transactionType != null ? transactionType : "",
                otherPlayerUuid != null ? otherPlayerUuid : "",
                playerUuid,
                timestamp,
                resolvedReason
        ).toCompletableFuture().thenApply(Object::toString);
    }
    
    public CompletableFuture<String> transferMetadataAtomic(UUID from, UUID to, String field, double amount) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String fromKey = keys.player(from);
        String toKey = keys.player(to);
        String script = RedisScripts.TRANSFER_METADATA_ATOMIC;
        return async.eval(script, io.lettuce.core.ScriptOutputType.VALUE, new String[]{fromKey, toKey}, field, String.valueOf(amount))
                .toCompletableFuture()
                .thenApply(Object::toString);
    }
    
    public CompletableFuture<Void> updateBaltop(UUID uuid, String group, double balance, String playerName) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String key = keys.economyBaltop(group);
        String member = playerName + "|" + uuid.toString();
        return async.zadd(key, balance, member).toCompletableFuture().thenApply(v -> null);
    }
    
    public CompletableFuture<List<String>> getHistory(UUID uuid, int limit) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = NexusKeyFactory.of(namespace).history(uuid);
        int count = limit <= 0 ? 10 : limit;
        return async.lrange(key, 0, count - 1).toCompletableFuture();
    }
    
    public CompletableFuture<List<Map<String, Object>>> getBaltop(String group, int offset, int limit) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        NexusKeyFactory keys = NexusKeyFactory.of(namespace);
        String key = keys.economyBaltop(group);
        long start = Math.max(0, offset);
        long end = start + Math.max(0, limit) - 1;
        return async.zrevrangeWithScores(key, start, end).toCompletableFuture().thenApply(list -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (io.lettuce.core.ScoredValue<String> sv : list) {
                String member = sv.getValue();
                double score = sv.getScore();
                String name = member;
                int idx = member.lastIndexOf('|');
                if (idx > 0) {
                    name = member.substring(0, idx);
                }
                Map<String, Object> row = new HashMap<>();
                row.put("name", name);
                row.put("amount", score);
                out.add(row);
            }
            return out;
        });
    }
    
    
    @Override
    public CompletableFuture<java.util.Map<String, String>> hgetall(String key) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        return async.hgetall(key).toCompletableFuture();
    }
    
    @Override
    public CompletableFuture<Long> publish(String channel, String message) {
        if (!isConnected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        return async.publish(channel, message).toCompletableFuture();
    }
    
    public boolean isConnected() {
        return isConnected;
    }
}
