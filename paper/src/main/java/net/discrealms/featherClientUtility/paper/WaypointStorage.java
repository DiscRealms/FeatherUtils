package net.discrealms.featherClientUtility.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class WaypointStorage {
    public static class WaypointData {
        public String name;
        public int x;
        public int y;
        public int z;
        public UUID worldId;
        public String color;
        public int durationSecs;
        public long createdAtMillis;

        public WaypointData(String name, int x, int y, int z, UUID worldId, String color, int durationSecs) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.worldId = worldId;
            this.color = color;
            this.durationSecs = durationSecs;
            this.createdAtMillis = System.currentTimeMillis();
        }
    }

    private final JavaPlugin plugin;
    private final Path file;
    private final Map<UUID, List<WaypointData>> data = new HashMap<>();

    public WaypointStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("waypoints.yml");
    }

    @SuppressWarnings("unchecked")
    public void load() {
        data.clear();
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Object rootObj = yaml.load(in);
            if (!(rootObj instanceof Map)) return;
            Map<String, Object> root = (Map<String, Object>) rootObj;
            Object playersObj = root.get("players");
            if (!(playersObj instanceof Map)) return;
            Map<String, Object> players = (Map<String, Object>) playersObj;
            for (Map.Entry<String, Object> e : players.entrySet()) {
                try {
                    UUID playerId = UUID.fromString(e.getKey());
                    List<WaypointData> list = new ArrayList<>();
                    Object arr = e.getValue();
                    if (arr instanceof List) {
                        for (Object el : (List<Object>) arr) {
                            if (el instanceof Map) {
                                Map<String, Object> m = (Map<String, Object>) el;
                                String name = Objects.toString(m.get("name"), null);
                                if (name == null) continue;
                                int x = ((Number) m.getOrDefault("x", 0)).intValue();
                                int y = ((Number) m.getOrDefault("y", 0)).intValue();
                                int z = ((Number) m.getOrDefault("z", 0)).intValue();
                                String worldStr = Objects.toString(m.get("world"), null);
                                UUID worldId = worldStr != null ? UUID.fromString(worldStr) : null;
                                String color = (String) m.get("color");
                                int duration = ((Number) m.getOrDefault("duration", 0)).intValue();
                                WaypointData wd = new WaypointData(name, x, y, z, worldId, color, duration);
                                Object createdAtObj = m.get("createdAt");
                                if (createdAtObj instanceof Number) {
                                    wd.createdAtMillis = ((Number) createdAtObj).longValue();
                                }
                                list.add(wd);
                            }
                        }
                    }
                    if (!list.isEmpty()) data.put(playerId, list);
                } catch (Exception ignored) {}
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to load waypoints.yml: " + ex.getMessage());
        }
    }

    public void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> players = new LinkedHashMap<>();
            for (Map.Entry<UUID, List<WaypointData>> e : data.entrySet()) {
                List<Map<String, Object>> arr = new ArrayList<>();
                for (WaypointData wd : e.getValue()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", wd.name);
                    m.put("x", wd.x);
                    m.put("y", wd.y);
                    m.put("z", wd.z);
                    if (wd.worldId != null) m.put("world", wd.worldId.toString());
                    if (wd.color != null) m.put("color", wd.color);
                    m.put("duration", wd.durationSecs);
                    if (wd.createdAtMillis > 0) m.put("createdAt", wd.createdAtMillis);
                    arr.add(m);
                }
                players.put(e.getKey().toString(), arr);
            }
            root.put("players", players);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);
            try (OutputStream out = Files.newOutputStream(file)) {
                yaml.dump(root, new java.io.OutputStreamWriter(out));
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save waypoints.yml: " + ex.getMessage());
        }
    }

    public List<WaypointData> get(UUID playerId) {
        return data.getOrDefault(playerId, new ArrayList<>());
    }

    public void put(UUID playerId, WaypointData waypoint) {
        data.computeIfAbsent(playerId, k -> new ArrayList<>());
        List<WaypointData> list = data.get(playerId);
        list.removeIf(w -> w.name.equalsIgnoreCase(waypoint.name));
        list.add(waypoint);
    }

    public boolean remove(UUID playerId, String name) {
        List<WaypointData> list = data.get(playerId);
        if (list == null) return false;
        return list.removeIf(w -> w.name.equalsIgnoreCase(name));
    }
}
