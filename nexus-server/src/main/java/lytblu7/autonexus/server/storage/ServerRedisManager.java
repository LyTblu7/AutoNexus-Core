package lytblu7.autonexus.server.storage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lytblu7.autonexus.common.NexusPacket;
import lytblu7.autonexus.common.api.NexusMessageListener;
import org.bukkit.Bukkit;
import lytblu7.autonexus.server.NexusServer;
import lytblu7.autonexus.common.redis.NexusKeyFactory;
import lytblu7.autonexus.common.model.ServerInfo;
import lytblu7.autonexus.common.model.NexusProfile;
import lytblu7.autonexus.common.redis.RedisScripts;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class ServerRedisManager implements lytblu7.autonexus.common.INexusRedis {
    private RedisClient client;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private io.lettuce.core.api.StatefulRedisConnection<String, String> commandConnection;
    private final NexusServer plugin;
    private final Logger logger;
    private final String serverName;
    private String serverGroup;
    private final String namespace;
    private final Gson gson = new Gson();
    private final NexusKeyFactory keys;
    private final ConcurrentHashMap<String, List<NexusMessageListener>> messageListeners = new ConcurrentHashMap<>();

    @Override
    public java.util.concurrent.CompletableFuture<String> get(String key) {
        if (commandConnection == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return commandConnection.async().get(key).toCompletableFuture();
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> set(String key, String value) {
        if (commandConnection == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return commandConnection.async().set(key, value).toCompletableFuture().thenApply(v -> null);
    }


    public ServerRedisManager(NexusServer plugin, String serverName, String serverGroup, String namespace, boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.serverName = serverName;
        this.serverGroup = serverGroup;
        this.namespace = namespace != null ? namespace : "global";
        this.keys = NexusKeyFactory.of(this.namespace);
    }

    public void connect(String uri) {
        try {
            client = RedisClient.create(uri);
            client.setOptions(io.lettuce.core.ClientOptions.builder().autoReconnect(true).build());
            
            // Command Connection
            commandConnection = client.connect();
            
            // PubSub Connection
            pubSubConnection = client.connectPubSub();
            pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
                @Override
                public void message(String channel, String message) {
                    if (!channel.equals("autonexus:network")) return;
                    processMessage(message);
                }
            });
            pubSubConnection.async().subscribe("autonexus:network");
            logger.info("[AutoNexus] Redis Pub/Sub connected! Listening on 'autonexus:network' (namespace=" + namespace + ")");
        } catch (Exception e) {
            logger.severe("[AutoNexus] Redis Pub/Sub failed: " + e.getMessage());
        }
    }
    
    public void shutdown() {
        if (pubSubConnection != null) pubSubConnection.close();
        if (commandConnection != null) commandConnection.close();
        if (client != null) client.shutdown();
    }

    @Override
    public java.util.concurrent.CompletableFuture<Long> publish(String channel, String message) {
        if (commandConnection == null) return java.util.concurrent.CompletableFuture.completedFuture(0L);
        return commandConnection.async().publish(channel, message).toCompletableFuture();
    }

    public void sendPlayerToServer(java.util.UUID playerUuid, String serverName) {
        if (pubSubConnection == null || playerUuid == null || serverName == null || serverName.isBlank()) {
            return;
        }
        plugin.getLogger().info("[SPIGOT-OUT] Publishing TELEPORT to Redis for " + playerUuid + " to " + serverName);
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "TELEPORT");
        obj.addProperty("uuid", playerUuid.toString());
        obj.addProperty("server", serverName);
        String json = gson.toJson(obj);
        try {
            publish("autonexus:network", json).exceptionally(e -> {
                plugin.getLogger().severe("[SPIGOT-ERROR] Failed to publish TELEPORT: " + e.getMessage());
                return 0L;
            });
        } catch (Exception e) {
            plugin.getLogger().severe("[SPIGOT-ERROR] Exception while publishing TELEPORT: " + e.getMessage());
        }
    }

    public java.util.concurrent.CompletableFuture<Void> setPlayerOffline(java.util.UUID uuid) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = keys.player(uuid);
        return commandConnection.async().get(key).toCompletableFuture().thenCompose(json -> {
            if (json == null) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            try {
                com.google.gson.JsonObject root = gson.fromJson(json, com.google.gson.JsonObject.class);
                root.addProperty("currentServer", "offline");
                String updated = gson.toJson(root);
                return commandConnection.async().set(key, updated).toCompletableFuture().thenApply(v -> null);
            } catch (Exception e) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        });
    }

    public java.util.concurrent.CompletableFuture<NexusProfile> getPlayerProfile(java.util.UUID uuid) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = keys.player(uuid);
        return commandConnection.async().get(key).toCompletableFuture().thenApply(json -> {
            if (json == null) return null;
            try {
                com.google.gson.JsonObject root = gson.fromJson(json, com.google.gson.JsonObject.class);
                String name = root.has("lastSeenName") ? root.get("lastSeenName").getAsString() : null;
                String currentServer = root.has("currentServer") ? root.get("currentServer").getAsString() : null;
                return new NexusProfile(uuid, name, currentServer);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private String onlinePlayersKey() {
        return "autonexus:" + namespace + ":online_players";
    }

    public java.util.List<String> getOnlinePlayerNames() {
        if (commandConnection == null) {
            return java.util.Collections.emptyList();
        }
        try {
            String key = onlinePlayersKey();
            java.util.Set<String> raw = commandConnection.sync().smembers(key);
            if (raw == null || raw.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return new java.util.ArrayList<>(raw);
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[AutoNexus] Redis error while fetching global players, falling back to local list.");
            }
            java.util.List<String> fallback = new java.util.ArrayList<>();
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getName() != null) {
                    fallback.add(p.getName());
                }
            }
            return fallback;
        }
    }

    public java.util.concurrent.CompletableFuture<java.util.UUID> getPlayerIdByName(String name) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String lower = name != null ? name.toLowerCase() : "";
        String indexKey = "autonexus:name2uuid:" + lower;
        return commandConnection.async().get(indexKey).toCompletableFuture().thenCompose(value -> {
            if (value != null && !value.isEmpty()) {
                try {
                    return java.util.concurrent.CompletableFuture.completedFuture(java.util.UUID.fromString(value));
                } catch (Exception e) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
            }
            String legacyKey = keys.nameToUuid(name);
            return commandConnection.async().get(legacyKey).toCompletableFuture().thenApply(legacy -> {
                if (legacy == null || legacy.isEmpty()) return null;
                try {
                    return java.util.UUID.fromString(legacy);
                } catch (Exception e) {
                    return null;
                }
            });
        });
    }

    public ServerInfo getServer(String name) {
        if (commandConnection == null || name == null || name.isBlank()) return null;
        String key = "autonexus:server:" + name;
        String json = commandConnection.sync().get(key);
        if (json == null) return null;
        try {
            return gson.fromJson(json, ServerInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    public java.util.List<ServerInfo> getServers() {
        java.util.List<ServerInfo> out = new java.util.ArrayList<>();
        if (commandConnection == null) return out;
        try {
            java.util.List<String> keysList = commandConnection.sync().keys("autonexus:server:*");
            if (keysList == null) return out;
            for (String k : keysList) {
                try {
                    String json = commandConnection.sync().get(k);
                    if (json != null) {
                        ServerInfo info = gson.fromJson(json, ServerInfo.class);
                        if (info != null) out.add(info);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[AutoNexus] Redis error while fetching servers, clearing list cache.");
            }
            out.clear();
        }
        return out;
    }

    public java.util.concurrent.CompletableFuture<Void> setServerHeartbeat(ServerInfo info) {
        if (commandConnection == null || info == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = "autonexus:server:" + info.getName();
        String json = gson.toJson(info);
        return commandConnection.async().setex(key, 10, json).toCompletableFuture().thenApply(v -> null);
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

    public java.util.concurrent.CompletableFuture<Long> publishMessage(String channel, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "PLUGIN_MESSAGE");
        obj.addProperty("subchannel", channel);
        obj.addProperty("payload", message);
        String json = gson.toJson(obj);
        return publish("autonexus:network", json);
    }

    public java.util.concurrent.CompletableFuture<java.util.Map<String, Object>> loadPlayerData(java.util.UUID uuid) {
        if (commandConnection == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
        
        String key = keys.player(uuid);
        if (plugin.isDebug()) plugin.getLogger().info("[DEBUG] Fetching key: " + key);
        
        return commandConnection.async().get(key).toCompletableFuture().thenApply(json -> {
            if (plugin.isDebug()) plugin.getLogger().info("[DEBUG] Received from Redis: " + json);
            
            if (json == null) {
                if (plugin.isDebug()) plugin.getLogger().info("[DEBUG] Redis returned NULL for this key.");
                return null;
            }
            
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            try {
                // Parse JSON as NexusPlayer structure
                com.google.gson.JsonObject root = gson.fromJson(json, com.google.gson.JsonObject.class);
                
                // We mainly need metadata for isAdmin
                if (root.has("metadata") && root.get("metadata").isJsonObject()) {
                    com.google.gson.JsonObject metadata = root.getAsJsonObject("metadata");
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : metadata.entrySet()) {
                        result.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                
                // Add top-level fields just in case
                if (root.has("lastSeenName")) result.put("name", root.get("lastSeenName").getAsString());
                
            } catch (Exception e) {
                if (plugin.isDebug()) plugin.getLogger().warning("[DEBUG] Failed to parse JSON: " + e.getMessage());
            }
            return result;
        });
    }

    public void savePlayerData(java.util.UUID uuid, java.util.Map<String, String> data) {
        if (commandConnection == null) return;
        String key = keys.player(uuid);
        commandConnection.async().hmset(key, data);
    }
    
    public void saveNameMapping(String name, java.util.UUID uuid) {
        if (commandConnection == null) return;
        String key = keys.nameToUuid(name);
        commandConnection.async().set(key, uuid.toString());
    }
    
    public java.util.concurrent.CompletableFuture<java.util.UUID> getUuidByName(String name) {
        if (commandConnection == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
        String key = keys.nameToUuid(name);
        return commandConnection.async().get(key).toCompletableFuture().thenApply(uuidStr -> {
            if (uuidStr == null) return null;
            try {
                return java.util.UUID.fromString(uuidStr);
            } catch (Exception e) {
                return null;
            }
        });
    }
    
    @Override
    public java.util.concurrent.CompletableFuture<java.util.Map<String, String>> hgetall(String key) {
        if (commandConnection == null) return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.emptyMap());
        return commandConnection.async().hgetall(key).toCompletableFuture();
    }
    
    public boolean isReady() {
        return commandConnection != null;
    }

    public boolean isConnected() {
        return commandConnection != null;
    }

    public java.util.concurrent.CompletableFuture<Void> updatePlayerMetadata(java.util.UUID uuid, java.util.Map<String, String> metadataUpdates) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = keys.player(uuid);
        String jsonUpdates = gson.toJson(metadataUpdates);
        String script = RedisScripts.UPDATE_PLAYER_METADATA;
        return commandConnection.async()
                .eval(script, io.lettuce.core.ScriptOutputType.INTEGER, new String[]{key}, jsonUpdates)
                .toCompletableFuture()
                .thenApply(v -> null);
    }

    public java.util.concurrent.CompletableFuture<String> incrementMetadataAtomic(java.util.UUID uuid, String field, double delta, String group, String serverSource, String transactionType, String otherPlayerUuid, String reason) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String playerKey = keys.player(uuid);
        String historyKey = keys.history(uuid);
        String baltopKey = keys.economyBaltop(group);
        boolean isBalanceField = field != null && field.toLowerCase().startsWith("balance");
        String script = RedisScripts.INCREMENT_METADATA_ATOMIC;
        String isBalance = isBalanceField ? "1" : "0";
        String playerUuid = uuid.toString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String resolvedReason = (reason != null && !reason.isEmpty()) ? reason : "SYSTEM";
        return commandConnection.async()
                .eval(
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
                )
                .toCompletableFuture()
                .thenApply(Object::toString);
    }

    public java.util.concurrent.CompletableFuture<String> transferMetadataAtomic(java.util.UUID from, java.util.UUID to, String field, double amount) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String fromKey = keys.player(from);
        String toKey = keys.player(to);
        String script = RedisScripts.TRANSFER_METADATA_ATOMIC;
        return commandConnection.async()
                .eval(
                        script,
                        io.lettuce.core.ScriptOutputType.VALUE,
                        new String[]{fromKey, toKey},
                        field,
                        String.valueOf(amount)
                )
                .toCompletableFuture()
                .thenApply(Object::toString);
    }
    
    public java.util.concurrent.CompletableFuture<Void> updateBaltop(java.util.UUID uuid, String group, double balance, String playerName) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = keys.economyBaltop(group);
        String member = (playerName != null ? playerName : "") + "|" + uuid.toString();
        return commandConnection.async().zadd(key, balance, member).toCompletableFuture().thenApply(v -> null);
    }
    
    public java.util.concurrent.CompletableFuture<java.util.List<java.util.Map<String, Object>>> getBaltop(String group, int offset, int limit) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = keys.economyBaltop(group);
        long start = Math.max(0, offset);
        long end = start + Math.max(0, limit) - 1;
        return commandConnection.async()
                .zrevrangeWithScores(key, start, end)
                .toCompletableFuture()
                .thenApply(list -> {
                    java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
                    for (io.lettuce.core.ScoredValue<String> sv : list) {
                        String member = sv.getValue();
                        double score = sv.getScore();
                        String name = member;
                        int idx = (member != null) ? member.lastIndexOf('|') : -1;
                        if (member != null && idx > 0) {
                            name = member.substring(0, idx);
                        }
                        java.util.Map<String, Object> row = new java.util.HashMap<>();
                        row.put("name", name);
                        row.put("amount", score);
                        out.add(row);
                    }
                    return out;
                });
    }

    public java.util.concurrent.CompletableFuture<java.util.List<String>> getHistory(java.util.UUID uuid, int limit) {
        if (commandConnection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Redis is not connected"));
        }
        String key = keys.history(uuid);
        if (plugin.isDebug()) {
            plugin.getLogger().info("[DEBUG] Reading history from Redis key: " + key);
            plugin.getLogger().info("[DEBUG] [CRITICAL] Reading history from key: " + key);
        }
        int count = limit <= 0 ? 10 : limit;
        return commandConnection.async()
                .lrange(key, 0, count - 1)
                .toCompletableFuture()
                .thenApply(list -> {
                    if (list == null) {
                        return Collections.<String>emptyList();
                    }
                    if (!list.isEmpty() && plugin.isDebug()) {
                        plugin.getLogger().info("[DEBUG] [CRITICAL] First history entry: " + list.get(0));
                    }
                    return list;
                });
    }

    public java.util.concurrent.CompletableFuture<java.util.List<String>> getGlobalHistory(java.util.UUID uuid, int limit) {
        if (plugin.isDebug()) {
            plugin.getLogger().info("[DEBUG] [CRITICAL] ServerRedisManager.getGlobalHistory called for " + uuid + " limit=" + limit);
        }
        return getHistory(uuid, limit);
    }

    private void processMessage(String message) {
        try {
            JsonObject root = gson.fromJson(message, JsonObject.class);
            if (root != null && root.has("action")) {
                String action = root.get("action").getAsString();
                if ("PLUGIN_MESSAGE".equalsIgnoreCase(action)) {
                    String subchannel = root.has("subchannel") ? root.get("subchannel").getAsString() : null;
                    String payload = root.has("payload") ? root.get("payload").getAsString() : null;
                    if (subchannel != null) {
                        List<NexusMessageListener> listeners = messageListeners.get(subchannel);
                        if (listeners != null && !listeners.isEmpty()) {
                            for (NexusMessageListener listener : listeners) {
                                Bukkit.getScheduler().runTask(plugin, () -> listener.onPluginMessage(subchannel, payload));
                            }
                        }
                    }
                    return;
                } else if ("RELOAD_NETWORK".equalsIgnoreCase(action)) {
                    Bukkit.getScheduler().runTask(plugin, plugin::reloadSettings);
                    return;
                } else if ("BROADCAST".equalsIgnoreCase(action)) {
                    String msg = root.has("message") ? root.get("message").getAsString() : null;
                    String senderId = root.has("senderId") ? root.get("senderId").getAsString() : null;
                    if (senderId != null && senderId.equalsIgnoreCase(plugin.getResolvedServerName())) {
                        return;
                    }
                    if (msg != null && !msg.isBlank()) {
                        String prefix = plugin.getConfig().getString("messages.broadcast-prefix", "§8[§4ANNOUNCEMENT§8] §f");
                        String text = prefix + msg;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                                p.sendMessage(text);
                            }
                        });
                    }
                    return;
                }
            }

            NetworkEnvelope envelope = gson.fromJson(message, NetworkEnvelope.class);
            if (envelope == null) return;

            // Check target
            // If target is "all" or "ALL" (case insensitive), accept.
            // If target matches our serverName, accept.
            // If envelope has targetGroup and it matches our serverGroup, accept.
            // Otherwise ignore.
            boolean isGlobal = "all".equalsIgnoreCase(envelope.target);
            boolean isMyName = serverName.equalsIgnoreCase(envelope.target);
            String targetGroup = envelope.packet != null ? envelope.packet.getTargetGroup() : null;
            boolean isMyGroup = targetGroup != null && targetGroup.equalsIgnoreCase(serverGroup);

            // Debug logging for filtering logic
            if (plugin.isDebug() && targetGroup != null) {
                boolean shouldExecute = isGlobal || isMyName || isMyGroup;
                logger.info("[DEBUG] Received command for group '" + targetGroup + "'. My group is '" + serverGroup + "'. Executing: " + shouldExecute);
            }

            if (!isGlobal && !isMyName && !isMyGroup) {
                // logger.info("[DEBUG] Packet ignored, target was " + envelope.target + " (My name: " + serverName + ", Group: " + serverGroup + ")");
                return;
            }

            NexusPacket packet = envelope.packet;
            if (packet == null) return;

            // logger.info("[TRACE] Redis Pub/Sub: Received packet " + packet.getType());

            // Run on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // logger.info("[AutoNexus] [REDIS] Executing remote command: " + packet.getPayload());
                handlePacket(packet);
            });

        } catch (Exception e) {
            logger.warning("Failed to process Redis message: " + e.getMessage());
        }
    }
    
    public void setServerGroup(String newGroup) {
        this.serverGroup = newGroup;
    }
    
    public String getServerGroup() {
        return serverGroup;
    }

    private void handlePacket(NexusPacket packet) {
        String type = packet.getType();
        String payload = packet.getPayload();
        String senderUuid = packet.getSenderUuid();

        if ("DISPATCH_COMMAND".equals(type)) {
            
            // SPECIAL HANDLING FOR /ECO COMMANDS
            // Since we moved /eco logic to Proxy, we should ignore /eco commands dispatched here to prevent duplication
            // However, if the proxy is dispatching them for some reason (e.g. legacy), we should ignore.
            // But wait, ProxyEcoCommand does NOT dispatch a command packet anymore. It updates Redis directly.
            // So we don't need to filter anything here unless the user types /eco in Spigot console directly?
            // If user types /eco in Spigot console, it's a local command.
            // This is for REMOTE commands.
            
            if (senderUuid != null) {
                // Execute as Player (only if online on this server)
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(senderUuid);
                    org.bukkit.entity.Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        logger.info("[AutoNexus] Executing remote command for player " + p.getName() + ": " + payload);
                        Bukkit.dispatchCommand(p, payload);
                    } else {
                        // Player is not on this server, ignore the command here.
                        // It will be executed on the server where they ARE online.
                        // logger.info("[DEBUG] Skipped execution for " + senderUuid + " (Not on this server)");
                    }
                } catch (Exception e) {
                    logger.warning("Invalid sender UUID in DISPATCH_COMMAND: " + senderUuid);
                }
            } else {
                // Execute as Console (Global or targeted)
                // Prevention of x2 execution for Global commands (when target is ALL)
                // If the command target was "ALL", we must decide WHO executes it.
                // Usually "ALL" means "Run on every server", but for economy commands like "/eco give",
                // running it on every server means the player gets money X times.
                
                // However, we don't know if the original target was "ALL" here, we only see the packet.
                // The packet doesn't store the original envelope target.
                // But if the command is "/eco give ...", it implies a single transaction.
                
                // HACK: For now, if senderUuid is null (Console) and we are NOT the 'lobby' server (or a specific designated leader),
                // we should perhaps be careful.
                // BUT, wait. If I type "/eco give" in console, I WANT it to happen once.
                // The Proxy sends it to "ALL".
                
                // SOLUTION: Check if the command is an economy command or something that should only run once.
                // OR, relying on the fact that the Proxy SHOULD have sent it to a specific target if it was meant to be single.
                // But the user said: "The Proxy should NOT send to 'ALL' for console commands".
                
                // So let's modify Proxy to send to "lobby" by default for console commands if target is ALL.
                
                logger.info("[AutoNexus] Executing remote command via Redis (Console): " + payload + " [UUID: " + senderUuid + "]");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), payload);
            }
        } else if ("METADATA_SYNC".equals(type)) {
             String[] parts = payload.split(":", 3);
             if (parts.length == 3 && "isAdmin".equals(parts[1]) && "true".equals(parts[2])) {
                 try {
                     java.util.UUID uuid = java.util.UUID.fromString(parts[0]);
                     org.bukkit.entity.Player p = Bukkit.getPlayer(uuid);
                     if (p != null) {
                         p.setOp(true);
                         p.sendMessage("§a[AutoNexus] You have been granted Operator status (via Redis)!");
                         logger.info("Granted OP to " + p.getName());
                     }
                 } catch (Exception ignored) {}
             }
        } else if ("SYNC_PLAYER".equals(type)) {
            // Handle Sync Request (Invalidate Cache & Reload if online)
            try {
                String uuidStr = payload;
                // If payload is JSON (starts with {), extract UUID
                if (payload.trim().startsWith("{")) {
                     com.google.gson.JsonObject json = gson.fromJson(payload, com.google.gson.JsonObject.class);
                     if (json.has("uuid")) {
                         uuidStr = json.get("uuid").getAsString();
                     }
                }
                
                java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                
                // 1. Invalidate Local Cache
                plugin.removeCachedPlayer(uuid);
                
                // 2. If player is online, trigger a reload to update metadata (e.g. balance)
                if (Bukkit.getPlayer(uuid) != null) {
                    plugin.getPlayer(uuid).thenAccept(player -> {
                         // Data refreshed in cache
                         // Optional: Notify player?
                         // Bukkit.getPlayer(uuid).sendMessage("§7[Debug] Data synced from network.");
                    });
                }
                // logger.info("[AutoNexus] Synced player data for " + uuid);
            } catch (Exception e) {
                logger.warning("Failed to handle SYNC_PLAYER: " + e.getMessage());
            }
        }
    }

    // Removed handleEcoCommand as it is now handled by Proxy

    // Removed duplicate shutdown method
    
    // Simple helper class for envelope serialization
    public static class NetworkEnvelope {
        public String target;
        public NexusPacket packet;
        
        public NetworkEnvelope(String target, NexusPacket packet) {
            this.target = target;
            this.packet = packet;
        }
    }
}
