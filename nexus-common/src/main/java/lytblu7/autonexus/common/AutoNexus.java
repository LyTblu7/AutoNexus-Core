package lytblu7.autonexus.common;

public final class AutoNexus {
    private AutoNexus() {
    }

    public static INexusAPI getApi() {
        return NexusProvider.get();
    }
}

