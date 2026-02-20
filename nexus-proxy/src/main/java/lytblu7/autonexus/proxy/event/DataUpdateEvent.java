package lytblu7.autonexus.proxy.event;

import lytblu7.autonexus.common.model.NexusPlayer;

public class DataUpdateEvent {
    private final NexusPlayer player;

    public DataUpdateEvent(NexusPlayer player) {
        this.player = player;
    }

    public NexusPlayer getPlayer() {
        return player;
    }
}
