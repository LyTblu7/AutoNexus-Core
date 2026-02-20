package lytblu7.autonexus.common;

/**
 * Represents a packet sent across the Nexus network.
 * Contains a type identifier and a string payload.
 */
public class NexusPacket {
    private final String type;
    private final String payload;
    private final String targetGroup; // Nullable
    private final String senderUuid; // Nullable (if sent by console)
    
    public NexusPacket(String type, String payload) {
        this(type, payload, null, null);
    }

    public NexusPacket(String type, String payload, String targetGroup) {
        this(type, payload, targetGroup, null);
    }

    public NexusPacket(String type, String payload, String targetGroup, String senderUuid) {
        this.type = type;
        this.payload = payload;
        this.targetGroup = targetGroup;
        this.senderUuid = senderUuid;
    }
    
    public String getType() {
        return type;
    }
    
    public String getPayload() {
        return payload;
    }

    public String getTargetGroup() {
        return targetGroup;
    }

    public String getSenderUuid() {
        return senderUuid;
    }
}
