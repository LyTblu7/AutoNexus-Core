package lytblu7.autonexus.common.event;

import java.util.UUID;

public final class NexusMetadataUpdateEvent implements Cancellable {
    private final UUID playerId;
    private final String key;
    private final String resolvedKey;
    private final String group;
    private final String oldValue;
    private final String newValue;
    private boolean cancelled;

    public NexusMetadataUpdateEvent(UUID playerId, String key, String resolvedKey, String group, String oldValue, String newValue) {
        this.playerId = playerId;
        this.key = key;
        this.resolvedKey = resolvedKey;
        this.group = group;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getKey() {
        return key;
    }

    public String getResolvedKey() {
        return resolvedKey;
    }

    public String getGroup() {
        return group;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

