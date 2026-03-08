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

    public boolean isSameGroup(String group) {
        // Since NexusProfile is a lightweight object, it might not have group info unless we add it.
        // For now, return false or try to fetch it?
        // Actually, NexusPlayer extends NexusProfile (or should).
        // If this is just a profile, maybe we can't check group easily without metadata.
        // Let's add metadata map support to NexusProfile if needed, or just keep it simple.
        // Wait, RedisManager.getPlayerProfile returns NexusProfile.
        // If we want group info, we need to store it in NexusProfile or cast to NexusPlayer.
        // Let's add a dummy implementation or expand NexusProfile.
        return false; 
    }
}
