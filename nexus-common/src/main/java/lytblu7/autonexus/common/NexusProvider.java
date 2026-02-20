package lytblu7.autonexus.common;

/**
 * Service locator for the NexusAPI.
 * Plugins should call NexusProvider.get() to access the API.
 */
public class NexusProvider {
    private static INexusAPI instance;

    /**
     * Retrieves the singleton instance of the NexusAPI.
     * @return The active API instance.
     * @throws IllegalStateException if the API is not initialized.
     */
    public static INexusAPI get() {
        if (instance == null) {
            throw new IllegalStateException("NexusAPI is not initialized yet!");
        }
        return instance;
    }

    /**
     * Registers the implementation of the NexusAPI.
     * This should only be called by the core AutoNexus plugin.
     * @param api The implementation to register.
     */
    public static void register(INexusAPI api) {
        if (instance != null) {
            // Allow re-registration or throw error? For now, allow but log if possible (can't log here easily).
            // Actually, usually providers are set once.
            // But for development/reloading, overwriting is fine.
        }
        instance = api;
    }
}
