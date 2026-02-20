package lytblu7.autonexus.server.command;

import lytblu7.autonexus.common.INexusAPI;
import lytblu7.autonexus.common.model.NexusProfile;
import lytblu7.autonexus.common.model.ServerInfo;
import lytblu7.autonexus.server.NexusServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NexusCommand implements CommandExecutor, TabCompleter {
    private final NexusServer plugin;
    private final INexusAPI api;
    private final java.util.Map<java.util.UUID, Long> joinCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    public NexusCommand(NexusServer plugin) {
        this.plugin = plugin;
        this.api = plugin;
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("autonexus.admin") || sender.isOp();
    }

    private boolean canUseJoin(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }
        boolean requirePerm = plugin.getConfig().getBoolean("commands.join.require-permission", false);
        if (!requirePerm) {
            return true;
        }
        return sender.hasPermission("autonexus.command.join") || hasAdmin(sender);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("join")) {
            if (!plugin.getConfig().getBoolean("commands.join.enabled", true)) {
                return true;
            }
            if (!canUseJoin(sender)) {
                sender.sendMessage(prefix() + plugin.getConfig().getString("messages.no-permission", "§cYou do not have permission to use this command."));
                return true;
            }
            handleJoin(sender, args);
            return true;
        }

        if (!hasAdmin(sender)) {
            sender.sendMessage(prefix() + plugin.getConfig().getString("messages.no-permission", "§cYou do not have permission to use this command."));
            return true;
        }

        if (sub.equals("servers")) {
            handleServers(sender);
            return true;
        }
        if (sub.equals("find")) {
            handleFind(sender, args);
            return true;
        }
        if (sub.equals("broadcast")) {
            handleBroadcast(sender, args);
            return true;
        }
        if (sub.equals("reload")) {
            handleReload(sender);
            return true;
        }
        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }

        String unknown = plugin.getConfig().getString("messages.unknown-command", "§cUnknown subcommand. Use /nexus help.");
        sender.sendMessage(prefix() + unknown);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix() + "§7Available subcommands:");
        boolean admin = hasAdmin(sender);
        if (plugin.getConfig().getBoolean("commands.join.enabled", true) && canUseJoin(sender)) {
            sender.sendMessage("§7/nexus join <server>");
        }
        if (admin && plugin.getConfig().getBoolean("commands.servers.enabled", true)) {
            sender.sendMessage("§7/nexus servers");
        }
        if (admin && plugin.getConfig().getBoolean("commands.find.enabled", true)) {
            sender.sendMessage("§7/nexus find <player>");
        }
        if (admin && plugin.getConfig().getBoolean("commands.broadcast.enabled", true)) {
            sender.sendMessage("§7/nexus broadcast <message>");
        }
        if (admin) {
            sender.sendMessage("§7/nexus reload");
        }
        sender.sendMessage("§7/nexus help");
    }

    private void handleServers(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("commands.servers.enabled", true)) {
            return;
        }
        List<ServerInfo> servers;
        try {
            servers = api.getServers();
        } catch (UnsupportedOperationException e) {
            String msg = "§cThis platform does not support server discovery.";
            sender.sendMessage(prefix() + msg);
            return;
        }

        if (servers == null || servers.isEmpty()) {
            sender.sendMessage(prefix() + "§7No servers are currently registered.");
            return;
        }

        for (ServerInfo info : servers) {
            String line = "§f" + info.getName() + " §7- §e" + info.getOnlinePlayers() + "/" + info.getMaxPlayers() + " §7(TPS: " + info.getTps() + ")";
            sender.sendMessage(prefix() + line);
        }
    }

    private void handleFind(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUsage: /nexus find <player>");
            return;
        }

        String name = args[1];
        CompletableFuture<UUID> idFuture = api.getPlayerIdByName(name);
        idFuture.thenAccept(uuid -> {
            if (uuid == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String template = plugin.getConfig().getString("messages.player-not-found", "§cPlayer %player% was not found on the network.");
                    sender.sendMessage(prefix() + applyPlaceholders(template, name, null, -1));
                });
                return;
            }

            CompletableFuture<NexusProfile> profileFuture = api.getPlayerProfile(uuid);
            profileFuture.thenAccept(profile -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (profile == null || !profile.isOnline()) {
                    String template = plugin.getConfig().getString("messages.player-not-found", "§cPlayer %player% was not found on the network.");
                    sender.sendMessage(prefix() + applyPlaceholders(template, name, null, -1));
                    return;
                }

                String displayName = profile.getName() != null ? profile.getName() : name;
                String currentServer = profile.getCurrentServer() != null ? profile.getCurrentServer() : "unknown";
                String template = plugin.getConfig().getString("messages.player-found", "§7Player §a%player% §7is currently on server §6%server%§7.");
                sender.sendMessage(prefix() + applyPlaceholders(template, displayName, currentServer, -1));
            }));
        });
    }

    private void handleBroadcast(CommandSender sender, String[] args) {
        if (!plugin.getConfig().getBoolean("commands.broadcast.enabled", true)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUsage: /nexus broadcast <message>");
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String senderId = plugin.getResolvedServerName();
        String safeMessage = message.replace("\"", "\\\"");
        String json = "{\"action\":\"BROADCAST\",\"message\":\"" + safeMessage + "\",\"senderId\":\"" + senderId + "\"}";
        api.getRedisManager().publish("autonexus:network", json).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String prefix = plugin.getConfig().getString("messages.broadcast-prefix", "§8[§4ANNOUNCEMENT§8] §f");
                    Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(prefix + message));
                })
        );
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!plugin.getConfig().getBoolean("commands.join.enabled", true)) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix() + plugin.getConfig().getString("messages.no-permission", "§cYou do not have permission to use this command."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(prefix() + "§cUsage: /nexus join <server>");
            return;
        }

        Player player = (Player) sender;
        String targetServer = args[1];
        int cooldownSec = plugin.getConfig().getInt("commands.join.cooldown", 3);
        if (cooldownSec > 0) {
            Long last = joinCooldowns.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (last != null && (now - last) < cooldownSec * 1000L) {
                long remainingMs = cooldownSec * 1000L - (now - last);
                long remainingSec = (long) Math.ceil(remainingMs / 1000.0);
                String template = plugin.getConfig().getString("messages.cooldown-wait", "§cPlease wait %time% seconds before using this jump again.");
                sender.sendMessage(prefix() + applyPlaceholders(template, sender.getName(), null, remainingSec));
                playSound(player, "error");
                return;
            }
            joinCooldowns.put(player.getUniqueId(), now);
        }
        boolean validate = plugin.getConfig().getBoolean("commands.join.validation", true);
        String resolvedName = targetServer;
        if (validate) {
            List<ServerInfo> servers;
            try {
                servers = api.getServers();
            } catch (UnsupportedOperationException e) {
                servers = Collections.emptyList();
            }
            if (servers == null || servers.isEmpty()) {
                String template = plugin.getConfig().getString("messages.server-not-found", "§cTarget server '%server%' is offline or does not exist.");
                sender.sendMessage(prefix() + template.replace("%server%", targetServer));
                playSound(player, "error");
                return;
            }
            String match = null;
            for (ServerInfo info : servers) {
                if (info.getName() != null && info.getName().equalsIgnoreCase(targetServer)) {
                    match = info.getName();
                    break;
                }
            }
            if (match == null) {
                String template = plugin.getConfig().getString("messages.server-not-found", "§cTarget server '%server%' is offline or does not exist.");
                sender.sendMessage(prefix() + template.replace("%server%", targetServer));
                playSound(player, "error");
                return;
            }
            resolvedName = match;
        }
        api.sendPlayerToServer(player.getUniqueId(), resolvedName);
        String connecting = plugin.getConfig().getString("messages.connecting", "§8[§aJoin§8] §7Connecting you to §a%server%§7...");
        player.sendMessage(prefix() + connecting.replace("%server%", resolvedName));
        playSound(player, "teleport-start");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadSettings();
        String json = "{\"action\":\"RELOAD_NETWORK\"}";
        api.getRedisManager().publish("autonexus:network", json).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(prefix() + plugin.getConfig().getString("messages.reload-success", "§aNetwork configuration has been reloaded successfully."))
                )
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (plugin.isDebug()) {
            plugin.getLogger().info("[DEBUG] /nexus tabcomplete by " + sender.getName() + " args=" + java.util.Arrays.toString(args));
        }
        if (args.length == 0 || args.length == 1) {
            List<String> base = new ArrayList<>();
            boolean admin = hasAdmin(sender);
            if (plugin.getConfig().getBoolean("commands.join.enabled", true) && canUseJoin(sender)) {
                base.add("join");
            }
            if (admin && plugin.getConfig().getBoolean("commands.servers.enabled", true)) {
                base.add("servers");
            }
            if (admin && plugin.getConfig().getBoolean("commands.find.enabled", true)) {
                base.add("find");
            }
            if (admin && plugin.getConfig().getBoolean("commands.broadcast.enabled", true)) {
                base.add("broadcast");
            }
            if (admin) {
                base.add("reload");
            }
            base.add("help");
            List<String> out = new ArrayList<>();
            String token = args.length == 0 ? "" : args[0];
            StringUtil.copyPartialMatches(token, base, out);
            return out;
        }
        List<String> suggestions = new ArrayList<>();
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String token = args[1];
            if (sub.equals("join")) {
                List<ServerInfo> servers;
                try {
                    servers = api.getServers();
                } catch (UnsupportedOperationException e) {
                    servers = Collections.emptyList();
                }
                List<String> names = new ArrayList<>();
                if (servers != null) for (ServerInfo info : servers) if (info.getName() != null) names.add(info.getName());
                StringUtil.copyPartialMatches(token, names, suggestions);
                if (plugin.isDebug()) {
                    plugin.getLogger().info("[DEBUG] /nexus join tab suggestions: " + suggestions);
                }
                return suggestions;
            }
            if (sub.equals("find")) {
                if (!hasAdmin(sender)) {
                    return Collections.emptyList();
                }
                List<String> names = plugin.getGlobalPlayersCacheSnapshot();
                if (names == null) names = new ArrayList<>();
                StringUtil.copyPartialMatches(token, names, suggestions);
                if (plugin.isDebug()) {
                    plugin.getLogger().info("[DEBUG] /nexus find tab suggestions: " + suggestions);
                }
                return suggestions;
            }
        }
        return Collections.emptyList();
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    private String applyPlaceholders(String template, String playerName, String serverName, long timeSec) {
        if (template == null) return "";
        String result = template;
        if (playerName != null) result = result.replace("%player%", playerName);
        if (serverName != null) result = result.replace("%server%", serverName);
        if (timeSec >= 0) result = result.replace("%time%", String.valueOf(timeSec));
        return result;
    }

    private void playSound(Player player, String key) {
        String base = "ux.sounds." + key + ".";
        if (!plugin.getConfig().getBoolean(base + "enabled", true)) return;
        String type = plugin.getConfig().getString(base + "type", "");
        float volume = (float) plugin.getConfig().getDouble(base + "volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble(base + "pitch", 1.0);
        if (type == null || type.isBlank()) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(type);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {}
    }
}

