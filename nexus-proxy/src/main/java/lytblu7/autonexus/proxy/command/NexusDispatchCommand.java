package lytblu7.autonexus.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import lytblu7.autonexus.common.NexusAPI;
import lytblu7.autonexus.common.NexusPacket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NexusDispatchCommand implements SimpleCommand {

    private final NexusAPI api;
    private final ProxyServer server;

    public NexusDispatchCommand(NexusAPI api, ProxyServer server) {
        this.api = api;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            invocation.source().sendMessage(Component.text("Usage: /dispatch <target|group:name|all> <command>", NamedTextColor.RED));
            return;
        }

        String targetArg = args[0];
        // Reconstruct the command string from remaining arguments
        StringBuilder commandBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            commandBuilder.append(args[i]).append(" ");
        }
        String command = commandBuilder.toString().trim();

        String targetServer = null;
        String targetGroup = null;

        if (targetArg.toLowerCase().startsWith("group:")) {
            targetGroup = targetArg.substring(6);
        } else if ("all".equalsIgnoreCase(targetArg) || "*".equalsIgnoreCase(targetArg)) {
            targetServer = "ALL";
        } else {
            targetServer = targetArg;
        }

        NexusPacket packet = new NexusPacket("DISPATCH_COMMAND", command, targetGroup);
        
        // If targetGroup is set, we send to "ALL" so servers can filter themselves
        String sendTarget = targetGroup != null ? "ALL" : targetServer;
        
        api.sendPacket(sendTarget, packet);
        
        String debugTarget = targetGroup != null ? "Group " + targetGroup : targetServer;
        invocation.source().sendMessage(Component.text("Dispatched command to " + debugTarget + ": " + command, NamedTextColor.GREEN));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            suggestions.addAll(server.getAllServers().stream()
                    .map(server -> server.getServerInfo().getName())
                    .collect(Collectors.toList()));
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("autonexus.admin");
    }
}