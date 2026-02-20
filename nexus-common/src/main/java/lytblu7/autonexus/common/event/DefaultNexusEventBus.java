package lytblu7.autonexus.common.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultNexusEventBus implements NexusEventBus {
    private final List<Consumer<NexusMetadataUpdateEvent>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void registerMetadataListener(Consumer<NexusMetadataUpdateEvent> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void unregisterMetadataListener(Consumer<NexusMetadataUpdateEvent> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public void postMetadataUpdate(NexusMetadataUpdateEvent event) {
        for (Consumer<NexusMetadataUpdateEvent> listener : listeners) {
            listener.accept(event);
            if (event.isCancelled()) {
                break;
            }
        }
    }
}

