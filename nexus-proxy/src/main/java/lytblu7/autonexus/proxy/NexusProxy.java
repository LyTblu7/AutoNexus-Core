package lytblu7.autonexus.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lytblu7.autonexus.common.NexusAPI;
import lytblu7.autonexus.common.NexusPacket;
import lytblu7.autonexus.common.model.NexusPlayer;
import lytblu7.autonexus.common.model.LeaderboardEntry;
import lytblu7.autonexus.common.model.NexusProfile;
import lytblu7.autonexus.common.model.ServerInfo;
import lytblu7.autonexus.proxy.storage.RedisManager;
import lytblu7.autonexus.proxy.config.ProxyConfig;
import lytblu7.autonexus.proxy.event.DataUpdateEvent;
import lytblu7.autonexus.proxy.meta.ProxyMetadataManager;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import lytblu7.autonexus.common.INexusAPI;
import lytblu7.autonexus.common.INexusRedis;
import lytblu7.autonexus.common.NexusProvider;
import lytblu7.autonexus.common.event.DefaultNexusEventBus;
import lytblu7.autonexus.common.event.NexusEventBus;
import lytblu7.autonexus.common.meta.MetadataManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@Plugin(id = "autonexus", name = "AutoNexus", version = "1.0.0", authors = {"lytblu7"})
public class NexusProxy implements NexusAPI, INexusAPI {
    private final ProxyServer server;
    private final Logger logger;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();
    private final ProxyConfig config;
    private final NexusEventBus eventBus;
    private final MetadataManager metadataManager;

    @Inject
    public NexusProxy(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.config = new ProxyConfig(dataDirectory);
        this.redisManager = new RedisManager();
        this.eventBus = new DefaultNexusEventBus();
        this.metadataManager = new ProxyMetadataManager(redisManager, this::getServerGroup, eventBus, logger);
    }

    @Override
    public INexusRedis getRedisManager() {
        return redisManager;
    }

    @Override
    public MetadataManager getMetadataManager() {
        return metadataManager;
    }

    @Override
    public NexusEventBus getEventBus() {
        return eventBus;
    }
    
    @Override
    public String getServerGroup() {
        String g = config.getGroup();
        if (g != null && g.equalsIgnoreCase("auto")) {
            try {
                Path dd = config.getDataDirectory();
                if (dd != null) {
                    Path serverRoot = dd.getParent() != null ? dd.getParent().getParent() : null;
                    if (serverRoot != null && serverRoot.getFileName() != null) {
                        return serverRoot.getFileName().toString();
                    }
                }
            } catch (Exception ignored) {}
            return "default";
        }
        return g != null ? g : "default";
    }
    
    public Logger getLogger() {
        return logger;
    }
    
    public ProxyServer getServer() {
        return server;
    }

    public boolean isDebug() {
        return config.isDebug();
    }
    
    public void reloadSettings() {
        try {
            config.load();
            boolean newDebug = config.isDebug();
            String newGroup = getServerGroup();
            if (redisManager != null) {
                redisManager.setNamespace(config.getNamespace());
                redisManager.setDebug(newDebug);
            }
            logger.info("AutoNexus: Reloaded group to '" + newGroup + "', namespace='" + config.getNamespace() + "', debug=" + newDebug);
        } catch (Exception e) {
            logger.severe("AutoNexus: Reload failed: " + e.getMessage());
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        NexusAPI.InstanceHolder.set(this);
        NexusProvider.register(this);
        
        // Initialize Config & Redis
        try {
            config.load();
            String redisUrl = config.getRedisUrl();
            // Apply namespace & debug settings before connecting
            redisManager.setNamespace(config.getNamespace());
            redisManager.setDebug(config.isDebug());
            redisManager.connect(redisUrl);
            
            // Subscribe to network channel
            redisManager.subscribe("autonexus:network", this::processIncomingMessage);
            
            // Wipe potential ghost players on fresh startup
            redisManager.clearOnlinePlayers();
            
            int hbSec = config.getHeartbeatInterval();
            int ttlSec = Math.max(15, hbSec * 3);
            server.getScheduler().buildTask(this, () -> {
                redisManager.touchOnlinePlayersTtl(ttlSec);
            }).repeat(java.time.Duration.ofSeconds(5)).schedule();
            
            logger.info("[AutoNexus] Primary Data Layer: REDIS (Connected, namespace=" + config.getNamespace() + ")");
        } catch (Exception e) {
            logger.severe("AutoNexus: Failed to connect to Redis! " + e.getMessage());
            // Shutdown the proxy if Redis is critical
            server.shutdown(); 
            return;
        }
        
        logger.info("AutoNexus Proxy (Multi-Module) Enabled!");
    }

    @Override
    public void dispatchCommand(String target, String command) {
        dispatchCommand(target, command, null);
    }

    @Override
    public void dispatchCommand(String target, String command, UUID sender) {
        String senderStr = sender != null ? sender.toString() : null;
        NexusPacket packet;
        
        if (target.startsWith("group:")) {
             String groupName = target.substring(6);
             packet = new NexusPacket("DISPATCH_COMMAND", command, groupName, senderStr);
             sendPacketToTarget("GROUP", packet);
        } else {
             String t = ("ALL".equalsIgnoreCase(target) || "*".equalsIgnoreCase(target)) ? "ALL" : target;
             packet = new NexusPacket("DISPATCH_COMMAND", command, null, senderStr);
             sendPacketToTarget(t, packet);
        }
    }

    @Override
    public CompletableFuture<Void> savePlayer(NexusPlayer player) {
        return redisManager.savePlayer(player);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        redisManager.shutdown();
        logger.info("AutoNexus: Redis connection closed.");
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        redisManager.loadPlayer(event.getPlayer().getUniqueId()).thenAccept(player -> {
            if (player == null) {
                player = new NexusPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), "proxy");
            } else {
                player.setLastSeenName(event.getPlayer().getUsername());
                player.setCurrentServer("proxy");
            }
            redisManager.savePlayer(player);
            server.getEventManager().fireAndForget(new DataUpdateEvent(player));
            redisManager.addOnlinePlayer(event.getPlayer().getUsername());
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        redisManager.setPlayerOffline(event.getPlayer().getUniqueId());
        redisManager.removeOnlinePlayer(event.getPlayer().getUsername());
    }

    @Override
    public void sendPacket(String targetServer, NexusPacket packet) {
        if ("*".equals(targetServer) || "all".equalsIgnoreCase(targetServer)) {
            // Broadcast to ALL servers using "ALL" target
            sendPacketToTarget("ALL", packet);
        } else {
            // Send to specific server
            sendPacketToTarget(targetServer, packet);
        }
    }

    private void sendPacketToTarget(String targetName, NexusPacket packet) {
        // Prepare JSON Envelope for Redis Pub/Sub
        JsonObject envelope = new JsonObject();
        envelope.addProperty("target", targetName);
        envelope.add("packet", gson.toJsonTree(packet));
        
        String message = gson.toJson(envelope);
        
        // Publish to Redis
        redisManager.publish("autonexus:network", message).thenAccept(receivers -> {
            logger.info("[TRACE] Redis Pub/Sub: Sent packet '" + packet.getType() + "' to " + targetName + " (Receivers: " + receivers + ")");
            if ("DISPATCH_COMMAND".equals(packet.getType())) {
                logger.info("[AutoNexus] [REDIS] Published Dispatch command to Redis channel.");
            }
        });
    }

    // Deprecated method kept for compatibility if needed, but redirected to new logic
    // private void sendPacketToServer(RegisteredServer server, NexusPacket packet) {
    //    sendPacketToTarget(server.getServerInfo().getName(), packet);
    // }

    @Override
    public CompletableFuture<NexusPlayer> getPlayer(UUID uuid) {
        return redisManager.loadPlayer(uuid);
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
        return redisManager.updateBaltop(uuid, group, balance, playerName);
    }
    
    @Override
    public CompletableFuture<java.util.List<LeaderboardEntry>> getTop(String group, int offset, int limit) {
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
        return redisManager.getHistory(uuid, limit);
    }

    @Override
    public void publishMessage(String channel, String message) {
        redisManager.publishMessage(channel, message);
    }

    @Override
    public void registerMessageListener(String channel, lytblu7.autonexus.common.api.NexusMessageListener listener) {
        redisManager.registerMessageListener(channel, listener);
    }

    @Override
    public void unregisterMessageListener(String channel, lytblu7.autonexus.common.api.NexusMessageListener listener) {
        redisManager.unregisterMessageListener(channel, listener);
    }

    @Override
    public java.util.List<ServerInfo> getServers() {
        return redisManager.getServers();
    }

    @Override
    public ServerInfo getServer(String name) {
        return redisManager.getServer(name);
    }

    @Override
    public java.util.concurrent.CompletableFuture<NexusProfile> getPlayerProfile(UUID uuid) {
        return redisManager.getPlayerProfile(uuid);
    }

    @Override
    public java.util.concurrent.CompletableFuture<UUID> getPlayerIdByName(String name) {
        return redisManager.getPlayerIdByName(name);
    }

    @Override
    public void sendPlayerToServer(UUID playerUuid, String serverName) {
        if (playerUuid == null || serverName == null || serverName.isBlank()) {
            return;
        }
        java.util.Optional<Player> playerOpt = server.getPlayer(playerUuid);
        java.util.Optional<RegisteredServer> targetOpt = server.getServer(serverName);
        if (targetOpt.isEmpty()) {
            // Fallback: case-insensitive lookup
            targetOpt = server.getAllServers().stream()
                    .filter(s -> s.getServerInfo().getName().equalsIgnoreCase(serverName.trim()))
                    .findFirst();
        }
        if (playerOpt.isPresent() && targetOpt.isPresent()) {
            playerOpt.get().createConnectionRequest(targetOpt.get()).connect();
            logger.info("[AutoNexus] Teleporting " + playerOpt.get().getUsername() + " to server " + targetOpt.get().getServerInfo().getName());
        } else {
            String available = server.getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .collect(java.util.stream.Collectors.joining(", "));
            logger.info("[AutoNexus] TELEPORT failed: player or server not found (uuid=" + playerUuid + ", server=" + serverName + "). Available servers: [" + available + "]");
        }
    }

    @Override
    public void registerNetworkCommand(String name, String permission, String targetGroup) {
        com.velocitypowered.api.command.CommandManager commandManager = server.getCommandManager();
        com.velocitypowered.api.command.CommandMeta meta = commandManager.metaBuilder(name)
                .plugin(this)
                .build();

        commandManager.register(meta, new SimpleCommand() {
            @Override
            public void execute(SimpleCommand.Invocation invocation) {
                CommandSource source = invocation.source();
                if (permission != null && !source.hasPermission(permission)) {
                    source.sendMessage(Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
                    return;
                }
                
                String[] args = invocation.arguments();
                StringBuilder cmdBuilder = new StringBuilder(name);
                for (String arg : args) {
                    cmdBuilder.append(" ").append(arg);
                }
                
                String fullCommand = cmdBuilder.toString();
                
                UUID senderUuid = null;
                boolean isPlayer = source instanceof Player;
                if (isPlayer) {
                    senderUuid = ((Player) source).getUniqueId();
                }
                
                if (isDebug()) {
                    logger.info("[DEBUG] Command '" + fullCommand + "' sent by " + (isPlayer ? ((Player)source).getUsername() : "Console") + " (Player: " + isPlayer + ")");
                }
                
                // Prevent sending to "ALL" if Console (to avoid duplication on all servers)
                String actualTarget = targetGroup;
                if (!isPlayer && ("ALL".equalsIgnoreCase(targetGroup) || "*".equals(targetGroup))) {
                     // If console sends to ALL, we redirect to a single server to avoid duplication.
                     // Ideally, this should be "lobby" or the first available server.
                     // For now, we hardcode "lobby" as the 'Main' server for console commands.
                     actualTarget = "lobby"; 
                     if (isDebug()) {
                         logger.info("[DEBUG] Console command redirected from ALL to " + actualTarget + " to prevent duplication.");
                     }
                }
                
                dispatchCommand(actualTarget, fullCommand, senderUuid);
                source.sendMessage(Component.text("[Proxy] Dispatching network command: /" + fullCommand, NamedTextColor.GRAY));
            }

            @Override
            public boolean hasPermission(SimpleCommand.Invocation invocation) {
                return permission == null || invocation.source().hasPermission(permission);
            }
        });
        logger.info("Registered network command: /" + name + " -> " + targetGroup);
    }

    @Override
    public void updateMetadata(UUID uuid, String key, String value) {
        // Use Lua script for atomic partial update
        java.util.Map<String, String> updates = new java.util.HashMap<>();
        updates.put(key, value);
        
        redisManager.updatePlayerMetadata(uuid, updates).thenRun(() -> {
             // Sync metadata update to ALL servers
             NexusPacket packet = new NexusPacket("METADATA_SYNC", uuid.toString() + ":" + key + ":" + value);
             sendPacket("*", packet);
        });
    }

    @Override
    public boolean isServerOnline(String serverName) {
        return server.getServer(serverName).map(s -> !s.getPlayersConnected().isEmpty()).orElse(false);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String serverName = event.getServer().getServerInfo().getName();
        
        // Use ATOMIC update to only change location/name without touching metadata
        redisManager.updatePlayerLocation(event.getPlayer().getUniqueId(), event.getPlayer().getUsername(), serverName)
            .thenRun(() -> {
                // Optional: Broadcast SYNC_PLAYER if needed, but since we didn't change metadata, 
                // and Spigot loads from Redis on Join, maybe we don't need to spam SYNC_PLAYER unless necessary.
                // But let's keep it to announce "I am here".
                // However, constructing the player object for the packet requires loading it.
                // If we want to be lightweight, we can just send the UUID so servers can reload if they want.
                // But existing code sent the whole object.
                // Let's load and send to maintain compatibility, but strictly for notification.
                
                redisManager.loadPlayer(event.getPlayer().getUniqueId()).thenAccept(player -> {
                     if (player != null) {
                         NexusPacket packet = new NexusPacket("SYNC_PLAYER", gson.toJson(player));
                         sendPacket("ALL", packet);
                     }
                });
            });
    }

    private void processIncomingMessage(String message) {
        if (isDebug()) {
            logger.info("[PROXY-IN] Raw Redis message: " + message);
        }
        JsonObject envelope;
        try {
            envelope = gson.fromJson(message, JsonObject.class);
        } catch (Exception ex) {
            logger.warning("[PROXY-ERROR] Failed to parse JSON: " + message + " (" + ex.getMessage() + ")");
            return;
        }
        if (envelope == null) {
            return;
        }
        try {
            if (envelope.has("action")) {
                String action = envelope.get("action").getAsString();
                if ("PLUGIN_MESSAGE".equalsIgnoreCase(action)) {
                    String subchannel = envelope.has("subchannel") ? envelope.get("subchannel").getAsString() : null;
                    String payload = envelope.has("payload") ? envelope.get("payload").getAsString() : null;
                    if (subchannel != null) {
                        redisManager.dispatchPluginMessage(subchannel, payload);
                    }
                    return;
                } else if ("TELEPORT".equalsIgnoreCase(action)) {
                    String uuidStr = envelope.has("uuid") ? envelope.get("uuid").getAsString() : null;
                    String serverName = envelope.has("server") ? envelope.get("server").getAsString() : null;
                    if (uuidStr == null || serverName == null || serverName.isBlank()) {
                        return;
                    }
                    server.getScheduler().buildTask(this, () -> {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            
                            if (isDebug()) {
                                boolean playerListed = server.getAllPlayers().stream().anyMatch(p -> p.getUniqueId().equals(uuid));
                                logger.info("[REDIS-DEBUG] TELEPORT packet for UUID=" + uuid + ". Online players on proxy: " + server.getAllPlayers().size() + ". Present=" + playerListed);
                            }
                            
                            java.util.Optional<Player> playerOpt = server.getPlayer(uuid);
                            
                            java.util.Optional<RegisteredServer> targetOpt = server.getServer(serverName);
                            if (targetOpt.isEmpty()) {
                                logger.warning("[REDIS-ERROR] Target server NOT FOUND in velocity.toml (strict): " + serverName + ". Trying case-insensitive match…");
                                targetOpt = server.getAllServers().stream()
                                        .filter(s -> s.getServerInfo().getName().equalsIgnoreCase(serverName.trim()))
                                        .findFirst();
                            }
                            
                            if (playerOpt.isPresent() && targetOpt.isPresent()) {
                                Player player = playerOpt.get();
                                RegisteredServer target = targetOpt.get();
                                if (isDebug()) {
                                    logger.info("[REDIS-DEBUG] Attempting to move " + player.getUsername() + " to " + target.getServerInfo().getName());
                                }
                                
                                player.createConnectionRequest(target).connect()
                                    .thenAccept(result -> {
                                        boolean success = (result != null && result.isSuccessful());
                                        if (success) {
                                            logger.info("[AutoNexus] TELEPORT success: " + player.getUsername() + " -> " + target.getServerInfo().getName());
                                        } else {
                                            logger.warning("[AutoNexus] TELEPORT failed by connector: " + player.getUsername() + " -> " + target.getServerInfo().getName() + " (" + result + ")");
                                        }
                                    })
                                    .exceptionally(ex2 -> {
                                        logger.warning("[AutoNexus] TELEPORT exception: " + ex2.getMessage());
                                        return null;
                                    });
                            } else {
                                String available = server.getAllServers().stream()
                                        .map(s -> s.getServerInfo().getName())
                                        .collect(java.util.stream.Collectors.joining(", "));
                                logger.warning("[AutoNexus] TELEPORT failed via Redis: player or server not found (uuid=" + uuidStr + ", server=" + serverName + "). Available servers: [" + available + "]");
                            }
                        } catch (IllegalArgumentException ex) {
                            logger.warning("[AutoNexus] Invalid UUID in TELEPORT action: " + uuidStr);
                        }
                    }).schedule();
                    return;
                } else if ("RELOAD_NETWORK".equalsIgnoreCase(action)) {
                    reloadSettings();
                    logger.info("[AutoNexus] RELOAD_NETWORK received");
                    return;
                } else if ("BROADCAST".equalsIgnoreCase(action)) {
                    String msg = envelope.has("message") ? envelope.get("message").getAsString() : null;
                    String senderId = envelope.has("senderId") ? envelope.get("senderId").getAsString() : null;
                    if (senderId != null && !senderId.isBlank()) {
                        return;
                    }
                    if (msg != null && !msg.isBlank()) {
                        String prefix = config.getBroadcastPrefix();
                        String text = prefix + msg;
                        for (Player p : server.getAllPlayers()) {
                            p.sendMessage(net.kyori.adventure.text.Component.text(text));
                        }
                    }
                    return;
                }
            }
        
            String target = envelope.has("target") ? envelope.get("target").getAsString() : "ALL";
            if ("ALL".equalsIgnoreCase(target) || "PROXY".equalsIgnoreCase(target)) {
                if (envelope.has("packet")) {
                    NexusPacket packet = gson.fromJson(envelope.get("packet"), NexusPacket.class);
                    handlePacket(packet, "UNKNOWN");
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to process Redis message: " + e.getMessage());
        }
    }

    private void handlePacket(NexusPacket packet, String serverId) {
        if ("PING".equals(packet.getType())) {
            logger.info("[AutoNexus] PING received from " + serverId);
        } else if ("DISPATCH_COMMAND".equals(packet.getType())) {
            String command = packet.getPayload();
            server.getCommandManager().executeImmediatelyAsync(server.getConsoleCommandSource(), command);
            logger.info("[AutoNexus] Remote command executed: " + command);
        } else if ("METADATA_UPDATE".equals(packet.getType())) {
            // Payload: UUID:KEY:VALUE
            String[] parts = packet.getPayload().split(":", 3);
            if (parts.length == 3) {
                try {
                    UUID uuid = UUID.fromString(parts[0]);
                    String key = parts[1];
                    String value = parts[2];
                    
                    // Update globally (Redis + Sync to current server)
                    updateMetadata(uuid, key, value);
                    logger.info("[AutoNexus] Metadata updated by " + serverId + " for " + uuid);
                } catch (IllegalArgumentException e) {
                    logger.warning("[AutoNexus] Invalid UUID in METADATA_UPDATE from " + serverId);
                }
            }
        }
    }
}
