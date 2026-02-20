package lytblu7.autonexus.common.api;

public interface NexusMessageListener {
    void onPluginMessage(String channel, String message);
}
