package lytblu7.autonexus.proxy.config;

import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ProxyConfig {
    private final Path dataDirectory;
    private Map<String, Object> root;

    public ProxyConfig(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public void load() {
        try {
            root = java.util.Collections.emptyMap();
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            File configFile = dataDirectory.resolve("config.yml").toFile();
            if (!configFile.exists()) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    } else {
                        String def = ""
                                + "# AutoNexus proxy configuration\n"
                                + "redis:\n"
                                + "  host: \"127.0.0.1\"\n"
                                + "  port: 6379\n"
                                + "  password: \"\"\n"
                                + "  timeout: 2000\n"
                                + "network:\n"
                                + "  namespace: \"global\"\n"
                                + "  heartbeat-interval: 5\n"
                                + "  cleanup-threshold: 15\n"
                                + "messages:\n"
                                + "  prefix: \"§8[§6AutoNexus§8] \"\n"
                                + "  reload-success: \"§aNetwork configuration has been reloaded successfully.\"\n"
                                + "  broadcast-prefix: \"§8[§4ANNOUNCEMENT§8] §f\"\n"
                                + "settings:\n"
                                + "  group: \"auto\"\n"
                                + "  debug: false\n";
                        Files.writeString(configFile.toPath(), def);
                    }
                }
            }
            Yaml yaml = new Yaml();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
                Object loaded = yaml.load(fis);
                if (loaded instanceof Map) {
                    root = castToMap(loaded);
                } else {
                    root = java.util.Collections.emptyMap();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    public String getRedisUrl() {
        Map<String, Object> redis = getMap(root, "redis");
        String host = getString(redis, "host", "127.0.0.1");
        int port = getInt(redis, "port", 6379);
        String pwd = getString(redis, "password", "");
        if (pwd != null && !pwd.isBlank()) {
            return "redis://:" + pwd + "@" + host + ":" + port;
        }
        return "redis://" + host + ":" + port;
    }

    public String getNamespace() {
        Map<String, Object> net = getMap(root, "network");
        return getString(net, "namespace", "global");
    }

    public int getHeartbeatInterval() {
        Map<String, Object> net = getMap(root, "network");
        return getInt(net, "heartbeat-interval", 5);
    }

    public boolean isDebug() {
        Map<String, Object> settings = getMap(root, "settings");
        return getBoolean(settings, "debug", false);
    }

    public String getGroup() {
        Map<String, Object> settings = getMap(root, "settings");
        return getString(settings, "group", "auto");
    }

    public String getBroadcastPrefix() {
        Map<String, Object> messages = getMap(root, "messages");
        return getString(messages, "broadcast-prefix", "§8[§4ANNOUNCEMENT§8] §f");
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> getMap(Map<String, Object> base, String key) {
        if (base == null) return java.util.Collections.emptyMap();
        Object v = base.get(key);
        if (v instanceof Map) return castToMap(v);
        return java.util.Collections.emptyMap();
    }

    private String getString(Map<String, Object> base, String key, String def) {
        if (base == null) return def;
        Object v = base.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private boolean getBoolean(Map<String, Object> base, String key, boolean def) {
        if (base == null) return def;
        Object v = base.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        }
        return def;
    }

    private int getInt(Map<String, Object> base, String key, int def) {
        if (base == null) return def;
        Object v = base.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
        }
        return def;
    }
}
