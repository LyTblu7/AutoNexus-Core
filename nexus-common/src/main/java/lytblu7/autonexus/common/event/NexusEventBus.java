package lytblu7.autonexus.common.event;

import java.util.function.Consumer;

public interface NexusEventBus {
    void registerMetadataListener(Consumer<NexusMetadataUpdateEvent> listener);
    void unregisterMetadataListener(Consumer<NexusMetadataUpdateEvent> listener);
    void postMetadataUpdate(NexusMetadataUpdateEvent event);
}

