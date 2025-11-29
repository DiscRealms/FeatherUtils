package net.discrealms.featherClientUtility.paper;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public final class Lang {
    private static Map<String, Object> messages = Collections.emptyMap();
    private static JavaPlugin plugin;

    private Lang() {}

    public static void init(JavaPlugin pl) {
        plugin = pl;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (!plugin.getDataFolder().toPath().resolve("lang.yml").toFile().exists()) {
            plugin.saveResource("lang.yml", false);
        }
        reload();
    }

    public static void reload() {
        try {
            Path file = plugin.getDataFolder().toPath().resolve("lang.yml");
            try (InputStream in = Files.newInputStream(file)) {
                Yaml yaml = new Yaml();
                Object obj = yaml.load(in);
                if (obj instanceof Map) {
                    messages = (Map<String, Object>) obj;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load lang.yml: " + e.getMessage());
        }
    }

    public static String msg(String key) {
        return colorize(String.valueOf(messages.getOrDefault(key, key)));
    }


    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
