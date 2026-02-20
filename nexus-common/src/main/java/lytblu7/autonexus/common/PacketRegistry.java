package lytblu7.autonexus.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PacketRegistry {
    private static final Map<Integer, PacketType> ID_MAP = new ConcurrentHashMap<>();
    
    static {
        for (PacketType type : PacketType.values()) {
            ID_MAP.put(type.ordinal(), type);
        }
    }
    
    public static PacketType getById(int id) {
        return ID_MAP.get(id);
    }
    
    public static int getId(PacketType type) {
        return type.ordinal();
    }
}
