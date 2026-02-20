package lytblu7.autonexus.common.model;

public class ServerInfo {
    private String name;
    private int onlinePlayers;
    private int maxPlayers;
    private double tps;

    public ServerInfo(String name, int onlinePlayers, int maxPlayers, double tps) {
        this.name = name;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.tps = tps;
    }

    public String getName() { return name; }
    public int getOnlinePlayers() { return onlinePlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public double getTps() { return tps; }
}
