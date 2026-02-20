package lytblu7.autonexus.server;

import lytblu7.autonexus.common.NexusAPI;
import lytblu7.autonexus.common.NexusPacket;
import lytblu7.autonexus.common.model.NexusPlayer;
import lytblu7.autonexus.common.model.LeaderboardEntry;
import lytblu7.autonexus.common.model.ServerInfo;
import lytblu7.autonexus.common.INexusAPI;
import lytblu7.autonexus.common.INexusRedis;
import lytblu7.autonexus.common.NexusProvider;
import lytblu7.autonexus.server.command.NexusCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import lytblu7.autonexus.server.storage.ServerRedisManager;
import lytblu7.autonexus.common.redis.NexusKeyFactory;
import lytblu7.autonexus.server.util.RedisReconnectManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NexusServer extends JavaPlugin implements NexusAPI, INexusAPI {

    private ServerRedisManager redisManager;
    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    // Local cache for player session data
    private final java.util.Map<UUID, NexusPlayer> playerCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    private String resolvedServerName;
    private String redisNamespace = "global";
    private boolean debugLogging = false;
    private String serverGroup;
    private RedisReconnectManager reconnectManager;
    private lytblu7.autonexus.common.meta.MetadataManager metadataManager;
    private final java.util.Set<String> globalPlayersCache = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    @Override
    public void onEnable() {
        NexusAPI.InstanceHolder.set(this);
        NexusProvider.register(this);
        
        // Config
        saveDefaultConfig();
        // Force reload config to ensure we get the latest values
        reloadConfig();
        String host = getConfig().getString("redis.host", "127.0.0.1");
        int port = getConfig().getInt("redis.port", 6379);
        String pwd = getConfig().getString("redis.password", "");
        String redisUrl = (pwd != null && !pwd.isBlank())
                ? "redis://:" + pwd + "@" + host + ":" + port
                : "redis://" + host + ":" + port;
        String serverNameCfg = getConfig().getString("server-name", "auto");
        this.serverGroup = getConfig().getString("group", "default");
        this.redisNamespace = getConfig().getString("network.namespace", "global");
        this.debugLogging = getConfig().getBoolean("debug", false);
        
        this.resolvedServerName = resolveServerName(serverNameCfg);
        
        // Redis
        redisManager = new ServerRedisManager(this, resolvedServerName, serverGroup, redisNamespace, debugLogging);
        reconnectManager = new RedisReconnectManager(this);
        try {
            redisManager.connect(redisUrl);
            getLogger().info("[AutoNexus] Redis connected (server-side): " + redisManager.isConnected());
        } catch (Exception e) {
            getLogger().severe("[AutoNexus] Connection failed. Connection lost, attempting reconnect...");
            final String url = redisUrl;
            reconnectManager.start(() -> {
                try {
                    if (!redisManager.isReady()) {
                        redisManager.connect(url);
                        if (redisManager.isReady()) {
                            reconnectManager.stop();
                            getLogger().info("[AutoNexus] Reconnected to Redis successfully.");
                        }
                    }
                } catch (Exception ignored) {}
            }, 200L, 600L);
        }
        fetchGlobalSettings();
        metadataManager = new lytblu7.autonexus.server.meta.ServerMetadataManager(redisManager, this::getServerGroup, getLogger());
        
        // Register Event Listeners
        getServer().getPluginManager().registerEvents(new ServerListener(this), this);
        
        NexusCommand nexusCmd = new NexusCommand(this);
        getCommand("nexus").setExecutor(nexusCmd);
        getCommand("nexus").setTabCompleter(nexusCmd);
        
        getLogger().info("[AutoNexus] Identified as: " + resolvedServerName);
        getLogger().info("[AutoNexus] Registered in group '" + serverGroup + "' (namespace=" + redisNamespace + ").");
        
        getServer().getServicesManager().register(lytblu7.autonexus.common.INexusAPI.class, this, this, ServicePriority.Normal);
        
        int hbSec = getConfig().getInt("network.heartbeat-interval", 5);
        long periodTicks = Math.max(1, hbSec) * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            int online = getServer().getOnlinePlayers().size();
            int max = getServer().getMaxPlayers();
            double tps = 0.0;
            try {
                double[] tpsArr = org.bukkit.Bukkit.getTPS();
                if (tpsArr != null && tpsArr.length > 0) {
                    tps = tpsArr[0];
                }
            } catch (Throwable ignored) {}
            ServerInfo info = new ServerInfo(resolvedServerName, online, max, tps);
            if (redisManager != null) {
                redisManager.setServerHeartbeat(info);
            }
        }, 0L, periodTicks);

        // Async refresh of global player cache (every ~4s)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (redisManager instanceof lytblu7.autonexus.server.storage.ServerRedisManager) {
                java.util.List<String> names = ((lytblu7.autonexus.server.storage.ServerRedisManager) redisManager).getOnlinePlayerNames();
                globalPlayersCache.clear();
                if (names != null) {
                    globalPlayersCache.addAll(names);
                }
            }
        }, 0L, 80L);
    }

    public boolean isDebug() {
        return debugLogging;
    }

    @Override
    public INexusRedis getRedisManager() {
        return redisManager;
    }
    
    public java.util.List<String> getGlobalPlayersCacheSnapshot() {
        return new java.util.ArrayList<>(globalPlayersCache);
    }
    
    @Override
    public String getServerGroup() {
        return serverGroup;
    }

    @Override
    public lytblu7.autonexus.common.meta.MetadataManager getMetadataManager() {
        if (metadataManager == null) {
            throw new UnsupportedOperationException("MetadataManager not initialized on server");
        }
        return metadataManager;
    }

    @Override
    public void onDisable() {
        if (reconnectManager != null) {
            reconnectManager.stop();
        }
        if (redisManager != null) {
            redisManager.shutdown();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        this.debugLogging = getConfig().getBoolean("debug", false);
        getLogger().info("[AutoNexus] Reloaded config. Debug=" + debugLogging);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    @Override
    public void sendPacket(String targetServer, NexusPacket packet) {
        // Use Redis Pub/Sub for packet sending
        if (redisManager == null) return;
        
        ServerRedisManager.NetworkEnvelope envelope = new ServerRedisManager.NetworkEnvelope(targetServer, packet);
        String json = gson.toJson(envelope);
        
        redisManager.publish("autonexus:network", json);
    }

    @Override
    public CompletableFuture<NexusPlayer> getPlayer(UUID uuid) {
        // 1. Check local cache first (Primary Source of Truth for online players)
        if (playerCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(playerCache.get(uuid));
        }

        // 2. Fallback to Redis
        return redisManager.loadPlayerData(uuid).thenApply(data -> {
            if (data == null || data.isEmpty()) return null;
            String name = (String) data.getOrDefault("name", "Unknown");
            // Server might not know current server of offline player if not synced, 
            // but for now we return what we have.
            String currentServer = (String) data.getOrDefault("server", "unknown");
            
            NexusPlayer player = new NexusPlayer(uuid, name, currentServer);
            // Populate metadata
            for (java.util.Map.Entry<String, Object> entry : data.entrySet()) {
                if (!entry.getKey().equals("name") && !entry.getKey().equals("server")) {
                     // NexusPlayer metadata is Map<String, String>
                     player.setMetadata(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return player;
        });
    }
    
    public void cachePlayer(NexusPlayer player) {
        playerCache.put(player.getUuid(), player);
    }
    
    public NexusPlayer removeCachedPlayer(UUID uuid) {
        return playerCache.remove(uuid);
    }

    @Override
    public CompletableFuture<UUID> getUuid(String name) {
        return redisManager.getUuidByName(name);
    }

    @Override
    public CompletableFuture<Double> incrementMetadata(UUID uuid, String field, double delta, String reason) {
        return metadataManager.modifyMetadata(uuid, field, delta, null, reason);
    }

    @Override
    public CompletableFuture<Double> transferMetadata(UUID from, UUID to, String field, double amount, String reason) {
        return redisManager.transferMetadataAtomic(from, to, field, amount).thenApply(result -> {
            if (result == null) return Double.NaN;
            if ("INSUFFICIENT_FUNDS".equalsIgnoreCase(result)) {
                return Double.NaN;
            }
            try {
                return Double.parseDouble(result);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> updateBaltop(UUID uuid, String group, double balance, String playerName) {
        if (redisManager == null) return CompletableFuture.completedFuture(null);
        return redisManager.updateBaltop(uuid, group, balance, playerName);
    }
    
    @Override
    public CompletableFuture<java.util.List<LeaderboardEntry>> getTop(String group, int offset, int limit) {
        if (redisManager == null) {
            return CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }
        return redisManager.getBaltop(group, offset, limit).thenApply(list -> {
            java.util.List<LeaderboardEntry> out = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> row : list) {
                String name = String.valueOf(row.getOrDefault("name", ""));
                Object amountObj = row.get("amount");
                double score;
                if (amountObj instanceof Number) {
                    score = ((Number) amountObj).doubleValue();
                } else {
                    try {
                        score = Double.parseDouble(String.valueOf(amountObj));
                    } catch (Exception e) {
                        score = 0.0;
                    }
                }
                out.add(new LeaderboardEntry(name, score));
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<java.util.List<String>> getGlobalHistory(UUID uuid, int limit) {
        if (redisManager == null) {
            return CompletableFuture.completedFuture(java.util.Collections.emptyList());
        }
        return redisManager.getGlobalHistory(uuid, limit);
    }

    @Override
    public void publishMessage(String channel, String message) {
        if (redisManager != null) {
            redisManager.publishMessage(channel, message);
        }
    }

    @Override
    public void registerMessageListener(String channel, lytblu7.autonexus.common.api.NexusMessageListener listener) {
        if (redisManager != null) {
            redisManager.registerMessageListener(channel, listener);
        }
    }

    @Override
    public void unregisterMessageListener(String channel, lytblu7.autonexus.common.api.NexusMessageListener listener) {
        if (redisManager != null) {
            redisManager.unregisterMessageListener(channel, listener);
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<lytblu7.autonexus.common.model.NexusProfile> getPlayerProfile(UUID uuid) {
        if (redisManager == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return redisManager.getPlayerProfile(uuid);
    }

    @Override
    public java.util.concurrent.CompletableFuture<UUID> getPlayerIdByName(String name) {
        if (redisManager == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return redisManager.getPlayerIdByName(name);
    }

    @Override
    public java.util.List<ServerInfo> getServers() {
        if (redisManager == null) return java.util.Collections.emptyList();
        return redisManager.getServers();
    }

    @Override
    public ServerInfo getServer(String name) {
        if (redisManager == null) return null;
        return redisManager.getServer(name);
    }

    @Override
    public void sendPlayerToServer(UUID playerUuid, String serverName) {
        if (redisManager == null) {
            return;
        }
        redisManager.sendPlayerToServer(playerUuid, serverName);
    }

    @Override
    public void registerNetworkCommand(String name, String permission, String targetGroup) {
        getLogger().warning("Attempted to register network command '/" + name + "' from Server side. This method is only supported on Proxy.");
    }

    @Override
    public CompletableFuture<Void> savePlayer(NexusPlayer player) {
        if (redisManager == null) return CompletableFuture.completedFuture(null);
        
        String key = NexusKeyFactory.of(redisNamespace).player(player.getUuid());
        String json = gson.toJson(player);
        
        return redisManager.set(key, json).thenRun(() -> {
            // Notify network of update
            // Payload format: UUID (simple invalidation/reload request)
            NexusPacket packet = new NexusPacket("SYNC_PLAYER", player.getUuid().toString());
            sendPacket("ALL", packet);
        });
    }

    @Override
    public void dispatchCommand(String target, String command) {
        dispatchCommand(target, command, null);
    }

    @Override
    public void dispatchCommand(String target, String command, UUID sender) {
         NexusPacket packet;
         String senderStr = sender != null ? sender.toString() : null;
         
         if (target.startsWith("group:")) {
             String groupName = target.substring(6);
             packet = new NexusPacket("DISPATCH_COMMAND", command, groupName, senderStr);
             sendPacket("GROUP", packet);
         } else {
             packet = new NexusPacket("DISPATCH_COMMAND", command, null, senderStr);
             sendPacket(target, packet);
         }
    }

    @Override
    public void updateMetadata(UUID uuid, String key, String value) {
        // Server-side implementation: Send update request to Proxy
        // We broadcast this so Proxy (and potentially other servers) can pick it up
        NexusPacket packet = new NexusPacket("METADATA_UPDATE", uuid.toString() + ":" + key + ":" + value);
        // We target "proxy" specifically if we wanted, but since we use PubSub, 
        // "ALL" or specific name works. Proxy usually listens to all or specific channel.
        // But our protocol says Proxy listens to "autonexus:network" and might filter.
        // For now, let's target "ALL" to be safe or "proxy" if we had a standard name.
        // Let's use "ALL" as safe default for metadata updates.
        sendPacket("ALL", packet);
    }

    @Override
    public boolean isServerOnline(String serverName) {
        // From spigot side, we assume proxy knows best.
        // We could ping proxy, but for now we just return false if not implemented
        return false;
    }
    
    public String getResolvedServerName() {
        return resolvedServerName;
    }
    
    private String resolveServerName(String serverNameCfg) {
        if (serverNameCfg == null || serverNameCfg.trim().isEmpty() || "auto".equalsIgnoreCase(serverNameCfg.trim())) {
            try {
                java.nio.file.Path rootPath = java.nio.file.Paths.get("").toAbsolutePath();
                java.io.File serverRoot = rootPath.toFile();
                java.io.File sp = new java.io.File(serverRoot, "server.properties");
                if (sp.exists()) {
                    java.util.Properties props = new java.util.Properties();
                    try (java.io.FileInputStream in = new java.io.FileInputStream(sp)) {
                        props.load(in);
                    }
                    String fromProps = props.getProperty("server-name");
                    if (fromProps != null) {
                        String trimmed = fromProps.trim();
                        // Ignore default placeholder value from Spigot/Paper
                        if (!trimmed.isEmpty() && !"Unknown Server".equalsIgnoreCase(trimmed)) {
                            return trimmed;
                        }
                    }
                }
                // Fallback to folder name
                java.nio.file.Path fileName = rootPath.getFileName();
                return (fileName != null ? fileName.toString() : "unknown-server");
            } catch (Exception e) {
                getLogger().warning("Failed to auto-detect server-name, using 'unknown-server': " + e.getMessage());
                return "unknown-server";
            }
        }
        return serverNameCfg;
    }
    
    private void fetchGlobalSettings() {
        String key = NexusKeyFactory.of(redisNamespace).groupMap();
        getRedisManager().hgetall(key).thenAccept(map -> {
            if (map == null || map.isEmpty()) return;
            String matched = null;
            for (java.util.Map.Entry<String, String> e : map.entrySet()) {
                String pattern = e.getKey();
                if (matchesPattern(resolvedServerName, pattern)) {
                    matched = e.getValue();
                    break;
                }
            }
            if (matched != null && !matched.isBlank()) {
                this.serverGroup = matched;
                if (redisManager != null) {
                    redisManager.setServerGroup(matched);
                }
                getLogger().info("[AutoNexus] Group mapped by Redis: '" + matched + "' (via groups_map)");
            }
        });
    }
    
    private boolean matchesPattern(String text, String wildcard) {
        if (wildcard == null || wildcard.isEmpty()) return false;
        String regex = java.util.regex.Pattern.quote(wildcard)
                .replace("\\*", ".*")
                .replace("\\?", ".");
        return java.util.regex.Pattern.compile("^" + regex + "$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text).matches();
    }
}
