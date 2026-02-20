package lytblu7.autonexus.common.model;

import java.util.UUID;

public class NexusProfile {
    private UUID uuid;
    private String name;
    private String currentServer;

    public NexusProfile(UUID uuid, String name, String currentServer) {
        this.uuid = uuid;
        this.name = name;
        this.currentServer = currentServer;
    }
    
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public String getCurrentServer() { return currentServer; }
    public boolean isOnline() { return currentServer != null && !currentServer.isEmpty() && !currentServer.equalsIgnoreCase("offline"); }
}
