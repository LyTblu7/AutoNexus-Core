package lytblu7.autonexus.common.model;

public class LeaderboardEntry {
    private final String name;
    private final double score;

    public LeaderboardEntry(String name, double score) {
        this.name = name;
        this.score = score;
    }

    public String getName() {
        return name;
    }

    public double getScore() {
        return score;
    }
}

