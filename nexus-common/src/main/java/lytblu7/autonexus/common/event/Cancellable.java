package lytblu7.autonexus.common.event;

public interface Cancellable {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}

