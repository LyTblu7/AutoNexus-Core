package lytblu7.autonexus.common.redis;

import java.util.UUID;

public class NexusKeyFactory {
    private final String namespace;
    
    public NexusKeyFactory(String namespace) {
        this.namespace = (namespace != null && !namespace.isBlank()) ? namespace : "global";
    }
    
    public static NexusKeyFactory of(String namespace) {
        return new NexusKeyFactory(namespace);
    }
    
    public String player(UUID uuid) {
        return "autonexus:" + namespace + ":player:" + uuid.toString();
    }
    
    public String metadata(UUID uuid) {
        return "autonexus:" + namespace + ":metadata:" + uuid.toString();
    }
    
    public String location(UUID uuid) {
        return "autonexus:" + namespace + ":location:" + uuid.toString();
    }
    
    public String groupMap() {
        return "autonexus:" + namespace + ":groups_map";
    }
    
    public String nameToUuid(String name) {
        return "autonexus:" + namespace + ":name_to_uuid:" + name.toLowerCase();
    }
    
    public String economyBaltop(String group) {
        String g = (group == null || group.isBlank()) ? "default" : group.toLowerCase();
        return "autonexus:" + namespace + ":economy:baltop:" + g;
    }
    
    public String history(UUID uuid) {
        return "autonexus:history:" + uuid.toString();
    }
}
