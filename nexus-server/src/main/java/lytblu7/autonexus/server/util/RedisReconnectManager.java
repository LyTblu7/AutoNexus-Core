package lytblu7.autonexus.server.util;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;

public class RedisReconnectManager {
    private final JavaPlugin plugin;
    private BukkitTask task;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    public RedisReconnectManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    public synchronized void start(Runnable attempt, long delayTicks, long periodTicks) {
        if (running.get()) {
            return;
        }
        running.set(true);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, attempt, delayTicks, periodTicks);
    }
    
    public synchronized void stop() {
        running.set(false);
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
            task = null;
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
}
