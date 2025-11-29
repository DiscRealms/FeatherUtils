package net.discrealms.featherClientUtility.paper;

import net.discrealms.featherClientUtility.BypassMissPenaltyManager;
import net.discrealms.featherClientUtility.DiscordRichPresenceManager;
import net.discrealms.featherClientUtility.ModManager;
import net.discrealms.featherClientUtility.WaypointManager;
import net.digitalingot.feather.serverapi.api.FeatherAPI;
import net.digitalingot.feather.serverapi.api.event.EventSubscription;
import net.digitalingot.feather.serverapi.api.event.player.PlayerHelloEvent;
import net.digitalingot.feather.serverapi.api.meta.ServerListBackground;
import net.digitalingot.feather.serverapi.api.meta.ServerListBackgroundFactory;
import net.digitalingot.feather.serverapi.api.meta.exception.ImageSizeExceededException;
import net.digitalingot.feather.serverapi.api.meta.exception.InvalidImageException;
import net.digitalingot.feather.serverapi.api.meta.exception.UnsupportedImageFormatException;
import net.digitalingot.feather.serverapi.api.player.FeatherPlayer;
import net.digitalingot.feather.serverapi.api.waypoint.WaypointColor;
import net.digitalingot.feather.serverapi.api.waypoint.WaypointDuration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PaperFeatherClientUtility extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private WaypointManager waypointManager;
    private BypassMissPenaltyManager bypassMissPenaltyManager;
    private ModManager modManager;
    private DiscordRichPresenceManager discordRichPresenceManager;
    private WaypointStorage waypointStorage;
    private EventSubscription<PlayerHelloEvent> helloSubscription;

    private static final List<String> KNOWN_MODS = Arrays.asList(
            "teamtracker","animations","armorBar","armorStatus","attackIndicator","autohidehud","autoperspective","autoText",
            "backups","blockIndicator","blockOverlay","bossBar","brightness","camera","colorSaturation","comboDisplay",
            "coordinates","cps","crosshair","culllogs","customadvancementsscreen","customChat","customf3","customfog",
            "damageIndicator","darkmode","deathInfo","direction","discordRP","dropprevention","elytras","fovChanger",
            "fps","glint","hearts","hitbox","hitindicator","horses","hypixel","inventory","itemCounter","itemdespawn",
            "itemInfo1","itemPhysic","jumpreset","keystrokes","lightleveloverlay","lootBeams","mobOverlay","motionBlur",
            "mousestrokes","nametags","nickHider","packdisplay","packOrganizer","perspective","ping","playerModel",
            "playtime","potionEffects","reachDisplay","reconnect","saturation","scoreboard","screenshot","searchkeybind",
            "serverAddress","shulkertooltips","snaplook","soundfilters","speedMeter","stopwatch1","subtitles","systemresources",
            "tablist","tiertagger","time","timeChanger","titletweaker","tnttimer","toastcontrol","toggleSprint","tooltips",
            "totem","tps","uhcoverlay","uiScaling","viewModel","voice","waypoints","weatherchanger","zoom"
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Lang.init(this);
        this.waypointManager = new WaypointManager();
        this.bypassMissPenaltyManager = new BypassMissPenaltyManager();
        this.modManager = new ModManager();
        this.discordRichPresenceManager = new DiscordRichPresenceManager();
        this.waypointStorage = new WaypointStorage(this);
        this.waypointStorage.load();
        sweepExpiredWaypoints();

        PluginCommand cmd = this.getCommand("featherutils");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        this.helloSubscription = FeatherAPI.getEventService().subscribe(
                PlayerHelloEvent.class,
                this::onFeatherHello
        );

        loadServerListBackgroundConfigured();

        getLogger().info("FeatherClientUtility enabled");
    }

    @Override
    public void onDisable() {
        if (this.helloSubscription != null) {
            try { this.helloSubscription.unsubscribe(); } catch (Exception ignored) {}
            this.helloSubscription = null;
        }
        if (this.waypointStorage != null) this.waypointStorage.save();
        getLogger().info("FeatherClientUtility disabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Intentionally left minimal; Feather actions are performed on PlayerHelloEvent
    }

    private void onFeatherHello(PlayerHelloEvent event) {
        FeatherPlayer featherPlayer = event.getPlayer();
        Player player = Bukkit.getPlayer(featherPlayer.getUniqueId());
        if (player == null) return;

        if (getConfig().getBoolean("mod-management.enabled")) {
            List<String> disabledMods = getConfig().getStringList("mod-management.disabled-mods");
            List<String> bannedMods = getConfig().getStringList("mod-management.banned-mods");
            List<String> forceEnabledMods = getConfig().getStringList("mod-management.force-enabled-mods");

            if (!disabledMods.isEmpty()) {
                modManager.disableMods(featherPlayer, disabledMods);
            }
            if (!bannedMods.isEmpty()) {
                modManager.banMods(featherPlayer, bannedMods);
            }
            if (!forceEnabledMods.isEmpty()) {
                modManager.allowMods(featherPlayer, forceEnabledMods);
                modManager.enableMods(featherPlayer, forceEnabledMods);
            }
        }

        if (getConfig().getBoolean("discord-rich-presence.enabled")) {
            String details = getConfig().getString("discord-rich-presence.details").replace("%server%", getServer().getName());
            String state = getConfig().getString("discord-rich-presence.state");
            discordRichPresenceManager.update(featherPlayer, details, state);
        }

        List<WaypointStorage.WaypointData> stored = waypointStorage.get(player.getUniqueId());
        if (!stored.isEmpty()) {
            int recreated = 0;
            long now = System.currentTimeMillis();
            for (WaypointStorage.WaypointData wd : new ArrayList<>(stored)) {
                if (wd.durationSecs > 0) {
                    long expiresAt = wd.createdAtMillis + (wd.durationSecs * 1000L);
                    if (expiresAt <= now) {
                        waypointStorage.remove(player.getUniqueId(), wd.name);
                        continue;
                    }
                }
                WaypointColor color = wd.color != null ? parseColor(wd.color) : null;
                WaypointDuration duration = wd.durationSecs > 0 ? WaypointDuration.of(wd.durationSecs) : WaypointDuration.none();
                UUID worldId = wd.worldId != null ? wd.worldId : player.getWorld().getUID();
                waypointManager.createWaypoint(featherPlayer, wd.name, wd.x, wd.y, wd.z, color, duration, worldId);
                if (wd.durationSecs > 0) {
                    long remaining = (wd.createdAtMillis + (wd.durationSecs * 1000L) - now) / 1000L;
                    if (remaining > 0) scheduleWaypointExpiry(player.getUniqueId(), wd.name, (int) remaining);
                }
                recreated++;
            }
            if (recreated > 0) getLogger().info("Recreated " + recreated + " waypoints for " + player.getName());
            waypointStorage.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return help(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                return help(sender);
            case "reload":
                if (!sender.hasPermission("featherutils.admin.reload")) return noPerm(sender);
                reloadConfig();
                Lang.reload();
                loadServerListBackgroundConfigured();
                if (this.waypointStorage != null) this.waypointStorage.load();
                sender.sendMessage("[FeatherUtils] Reloaded config.");
                getLogger().info(sender.getName() + " reloaded the plugin");
                return true;
            case "banner":
                if (!sender.hasPermission("featherutils.admin.banner")) return noPerm(sender);
                if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                    boolean ok = loadServerListBackgroundConfigured();
                    sender.sendMessage(ok ? "[FeatherUtils] Banner reloaded." : "[FeatherUtils] Failed to reload banner. See console.");
                    getLogger().info(sender.getName() + " executed banner reload");
                    return true;
                }
                sender.sendMessage("Usage: /" + label + " banner reload");
                return true;
            case "misspenalty":
                if (!sender.hasPermission("featherutils.admin.misspenalty")) return noPerm(sender);
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /" + label + " misspenalty <allow|ban>");
                    return true;
                }
                Player p = (Player) sender;
                FeatherPlayer fp = FeatherAPI.getPlayerService().getPlayer(p.getUniqueId());
                if (fp == null) { sender.sendMessage("You are not using Feather Client."); return true; }
                boolean allow = args[1].equalsIgnoreCase("allow");
                bypassMissPenaltyManager.setBypassMissPenalty(fp, allow);
                sender.sendMessage("Miss penalty bypass set to " + allow);
                getLogger().info(sender.getName() + " set misspenalty to " + allow);
                return true;
            case "mod":
                if (!sender.hasPermission("featherutils.admin.mod")) return noPerm(sender);
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 3) {
                    sender.sendMessage("Usage: /" + label + " mod <ban|force|allow> <mod...>");
                    return true;
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                Player sp = (Player) sender;
                FeatherPlayer sfp = FeatherAPI.getPlayerService().getPlayer(sp.getUniqueId());
                if (sfp == null) { sender.sendMessage("You are not using Feather Client."); return true; }
                List<String> mods = Arrays.asList(Arrays.copyOfRange(args, 2, args.length));
                if (action.equals("ban")) {
                    modManager.banMods(sfp, mods);
                    sender.sendMessage("Banned mods: " + String.join(", ", mods));
                    updateModConfig(mods, Collections.singletonList("banned-mods"), Arrays.asList("force-enabled-mods", "disabled-mods"));
                } else if (action.equals("force")) {
                    modManager.allowMods(sfp, mods);
                    modManager.enableMods(sfp, mods);
                    sender.sendMessage("Force-enabled mods: " + String.join(", ", mods));
                    updateModConfig(mods, Collections.singletonList("force-enabled-mods"), Arrays.asList("banned-mods", "disabled-mods"));
                } else if (action.equals("allow")) {
                    modManager.allowMods(sfp, mods);
                    sender.sendMessage("Allowed mods (unblocked): " + String.join(", ", mods));
                    removeFromModLists(mods, Arrays.asList("banned-mods", "disabled-mods", "force-enabled-mods"));
                } else {
                    sender.sendMessage("Usage: /" + label + " mod <ban|force|allow> <mod...>");
                }
                getLogger().info(sender.getName() + " mod " + action + " " + mods);
                return true;
            case "playermod":
                if (!sender.hasPermission("featherutils.admin.playermod")) return noPerm(sender);
                if (args.length < 4) {
                    sender.sendMessage("Usage: /" + label + " playermod <player> <mod> <disable|enable>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("Player not found."); return true; }
                FeatherPlayer tfp = FeatherAPI.getPlayerService().getPlayer(target.getUniqueId());
                if (tfp == null) { sender.sendMessage("Target is not using Feather Client."); return true; }
                String mod = args[2];
                String toggle = args[3].toLowerCase(Locale.ROOT);
                if (toggle.equals("disable")) {
                    modManager.disableMods(tfp, Collections.singletonList(mod));
                    sender.sendMessage("Disabled mod '" + mod + "' for " + target.getName());
                } else if (toggle.equals("enable")) {
                    modManager.allowMods(tfp, Collections.singletonList(mod));
                    modManager.enableMods(tfp, Collections.singletonList(mod));
                    sender.sendMessage("Enabled mod '" + mod + "' for " + target.getName());
                } else {
                    sender.sendMessage("Usage: /" + label + " playermod <player> <mod> <disable|enable>");
                }
                getLogger().info(sender.getName() + " playermod " + target.getName() + " " + mod + " " + toggle);
                return true;
            case "waypoints":
                if (!sender.hasPermission("featherutils.admin.waypoints")) return noPerm(sender);
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                Player wpPlayer = (Player) sender;
                FeatherPlayer wpf = FeatherAPI.getPlayerService().getPlayer(wpPlayer.getUniqueId());
                if (wpf == null) { sender.sendMessage("You are not using Feather Client."); return true; }
                if (args.length < 3) {
                    sender.sendMessage("Usage: /" + label + " waypoints <create|delete|update> <name> [x] [y] [z] [color] [durationSecs] [world]");
                    return true;
                }
                String wAction = args[1].toLowerCase(Locale.ROOT);
                String name = args[2];
                if (wAction.equals("delete")) {
                    waypointManager.deleteWaypoint(wpf, name);
                    waypointStorage.remove(wpPlayer.getUniqueId(), name);
                    waypointStorage.save();
                    sender.sendMessage("Deleted waypoint '" + name + "'");
                    return true;
                }
                int baseIdx = 3;
                Integer x = null, y = null, z = null;
                if (args.length >= baseIdx + 3) {
                    String sx = args[baseIdx];
                    String sy = args[baseIdx + 1];
                    String sz = args[baseIdx + 2];
                    if (sx.equals("~") && sy.equals("~") && sz.equals("~")) {
                        x = wpPlayer.getLocation().getBlockX();
                        y = wpPlayer.getLocation().getBlockY();
                        z = wpPlayer.getLocation().getBlockZ();
                        baseIdx += 3;
                    } else if (sx.equals("~~") && sy.equals("~~") && sz.equals("~~")) {
                        baseIdx += 3;
                    } else {
                        try {
                            x = Integer.parseInt(sx);
                            y = Integer.parseInt(sy);
                            z = Integer.parseInt(sz);
                            baseIdx += 3;
                        } catch (NumberFormatException ignored) { /* leave as nulls */ }
                    }
                }
                WaypointColor color = null;
                String colorToken = null;
                if (args.length > baseIdx) {
                    colorToken = args[baseIdx];
                    color = parseColor(colorToken);
                    if (color != null) baseIdx++;
                }
                WaypointDuration duration = null;
                Integer durationSecs = null;
                if (args.length > baseIdx) {
                    try {
                        int secs = Integer.parseInt(args[baseIdx]);
                        durationSecs = secs;
                        duration = secs > 0 ? WaypointDuration.of(secs) : WaypointDuration.none();
                        baseIdx++;
                    } catch (NumberFormatException ignored) { }
                }
                UUID worldId = null;
                boolean worldProvided = false;
                if (args.length > baseIdx) {
                    World world = Bukkit.getWorld(args[baseIdx]);
                    if (world != null) { worldId = world.getUID(); worldProvided = true; }
                }

                if (wAction.equals("create")) {
                    if (x == null || y == null || z == null) {
                        sender.sendMessage("Missing coordinates. Use ~ ~ ~ for current location.");
                        return true;
                    }
                    if (worldId == null) worldId = wpPlayer.getWorld().getUID();
                    waypointManager.createWaypoint(wpf, name, x, y, z, color, duration, worldId);
                    WaypointStorage.WaypointData wd = new WaypointStorage.WaypointData(
                            name, x, y, z, worldId, colorToken, durationSecs != null ? durationSecs : 0);
                    waypointStorage.put(wpPlayer.getUniqueId(), wd);
                    waypointStorage.save();
                    if (wd.durationSecs > 0) {
                        scheduleWaypointExpiry(wpPlayer.getUniqueId(), name, wd.durationSecs);
                    }
                    sender.sendMessage("Created waypoint '" + name + "'");
                } else if (wAction.equals("update")) {
                    List<WaypointStorage.WaypointData> list = waypointStorage.get(wpPlayer.getUniqueId());
                    WaypointStorage.WaypointData old = null;
                    for (WaypointStorage.WaypointData it : list) {
                        if (it.name.equalsIgnoreCase(name)) { old = it; break; }
                    }
                    int fx = old != null ? old.x : wpPlayer.getLocation().getBlockX();
                    int fy = old != null ? old.y : wpPlayer.getLocation().getBlockY();
                    int fz = old != null ? old.z : wpPlayer.getLocation().getBlockZ();
                    UUID wFallback = old != null && old.worldId != null ? old.worldId : null;
                    WaypointColor cFallback = old != null && old.color != null ? parseColor(old.color) : null;
                    WaypointDuration dFallback = old != null ? (old.durationSecs > 0 ? WaypointDuration.of(old.durationSecs) : WaypointDuration.none()) : null;

                    WaypointColor useColor = (color != null ? color : cFallback);
                    WaypointDuration useDuration = (duration != null ? duration : dFallback);
                    UUID useWorld = (worldProvided && worldId != null) ? worldId : (wFallback != null ? wFallback : wpPlayer.getWorld().getUID());

                    waypointManager.updateWaypoint(wpf, name, x, y, z, useColor, useDuration, useWorld, fx, fy, fz);
                    int nx = x != null ? x : fx;
                    int ny = y != null ? y : fy;
                    int nz = z != null ? z : fz;
                    String useColorToken = colorToken != null ? colorToken : (old != null ? old.color : null);
                    int useDurationSecs = durationSecs != null ? durationSecs : (old != null ? old.durationSecs : 0);
                    UUID useWorldId = useWorld;
                    WaypointStorage.WaypointData newData = new WaypointStorage.WaypointData(name, nx, ny, nz, useWorldId, useColorToken, useDurationSecs);
                    waypointStorage.put(wpPlayer.getUniqueId(), newData);
                    waypointStorage.save();
                    if (useDurationSecs > 0) {
                        scheduleWaypointExpiry(wpPlayer.getUniqueId(), name, useDurationSecs);
                    }
                    sender.sendMessage("Updated waypoint '" + name + "'");
                } else {
                    sender.sendMessage("Usage: /" + label + " waypoints <create|delete|update> <name> [x] [y] [z] [color] [durationSecs] [world]");
                }
                return true;
            default:
                return help(sender);
        }
    }

    private boolean help(CommandSender sender) {
        if (!sender.hasPermission("featherutils.admin.help")) return noPerm(sender);
        sender.sendMessage("FeatherUtils commands:");
        sender.sendMessage(" - /featherutils help");
        sender.sendMessage(" - /featherutils reload");
        sender.sendMessage(" - /featherutils banner reload");
        sender.sendMessage(" - /featherutils mod <ban|force|allow> <mod...>");
        sender.sendMessage(" - /featherutils misspenalty <allow|ban>");
        sender.sendMessage(" - /featherutils waypoints <create|delete|update> <name> [x] [y] [z] [color] [durationSecs] [world]");
        sender.sendMessage(" - /featherutils playermod <player> <mod> <disable|enable>");
        return true;
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage("You don't have permission to do that.");
        return true;
    }

    private WaypointColor parseColor(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.equalsIgnoreCase("chroma")) return WaypointColor.chroma();
        try {
            if (v.startsWith("#")) {
                String hex = v.substring(1);
                int r, g, b, a = 255;
                if (hex.length() == 6) {
                    r = Integer.parseInt(hex.substring(0, 2), 16);
                    g = Integer.parseInt(hex.substring(2, 4), 16);
                    b = Integer.parseInt(hex.substring(4, 6), 16);
                } else if (hex.length() == 8) {
                    r = Integer.parseInt(hex.substring(0, 2), 16);
                    g = Integer.parseInt(hex.substring(2, 4), 16);
                    b = Integer.parseInt(hex.substring(4, 6), 16);
                    a = Integer.parseInt(hex.substring(6, 8), 16);
                } else return null;
                return a == 255 ? WaypointColor.fromRgb(r, g, b) : WaypointColor.fromRgba(r, g, b, a);
            }
            String[] parts = v.split(",");
            if (parts.length == 3 || parts.length == 4) {
                int r = Integer.parseInt(parts[0]);
                int g = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                if (parts.length == 4) {
                    int a = Integer.parseInt(parts[3]);
                    return WaypointColor.fromRgba(r, g, b, a);
                }
                return WaypointColor.fromRgb(r, g, b);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private boolean loadServerListBackgroundConfigured() {
        try {
            if (!getConfig().getBoolean("server-list-background.enabled")) return true;
            String file = getConfig().getString("server-list-background.file");
            if (file == null || file.isEmpty()) return true;
            Path path = file.startsWith("/") ? Path.of(file) : getDataFolder().toPath().resolve(file);
            if (!Files.exists(path)) {
                getLogger().warning("Background file not found: " + path);
                return false;
            }
            ServerListBackgroundFactory factory = FeatherAPI.getMetaService().getServerListBackgroundFactory();
            ServerListBackground background = factory.byPath(path);
            FeatherAPI.getMetaService().setServerListBackground(background);
            getLogger().info("Server background loaded: " + path);
            return true;
        } catch (UnsupportedImageFormatException e) {
            getLogger().severe("Image format not supported. Only PNG is supported.");
        } catch (ImageSizeExceededException e) {
            getLogger().severe("Image too large. Max 1009x202, 512KB.");
        } catch (InvalidImageException e) {
            getLogger().severe("Invalid/corrupted image file.");
        } catch (IOException e) {
            getLogger().severe("Error reading image file: " + e.getMessage());
        } catch (Exception e) {
            getLogger().severe("Failed to set server list background: " + e.getMessage());
        }
        return false;
    }

    private void sweepExpiredWaypoints() {
        if (this.waypointStorage == null) return;
        boolean changed = false;
        long now = System.currentTimeMillis();
        Map<UUID, List<WaypointStorage.WaypointData>> snapshot = new HashMap<>();
        for (World w : Bukkit.getWorlds())
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<WaypointStorage.WaypointData> list = new ArrayList<>(waypointStorage.get(p.getUniqueId()));
            boolean removedAny = false;
            for (Iterator<WaypointStorage.WaypointData> it = list.iterator(); it.hasNext();) {
                WaypointStorage.WaypointData wd = it.next();
                if (wd.durationSecs > 0 && wd.createdAtMillis + wd.durationSecs * 1000L <= now) {
                    it.remove();
                    removedAny = true;
                }
            }
            if (removedAny) {
                for (WaypointStorage.WaypointData wd : new ArrayList<>(waypointStorage.get(p.getUniqueId()))) {
                    waypointStorage.remove(p.getUniqueId(), wd.name);
                }
                for (WaypointStorage.WaypointData wd : list) {
                    waypointStorage.put(p.getUniqueId(), wd);
                }
                changed = true;
            }
        }
        if (changed) waypointStorage.save();
    }

    private void scheduleWaypointExpiry(UUID playerId, String name, int seconds) {
        if (seconds <= 0) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            FeatherPlayer fp = FeatherAPI.getPlayerService().getPlayer(playerId);
            if (fp != null) {
                waypointManager.deleteWaypoint(fp, name);
            }
            waypointStorage.remove(playerId, name);
            waypointStorage.save();
            Player bp = Bukkit.getPlayer(playerId);
            if (bp != null) {
                getLogger().info("Expired waypoint '" + name + "' for " + bp.getName());
            }
        }, seconds * 20L);
    }

    private void updateModConfig(List<String> mods, List<String> addListKeys, List<String> removeListKeys) {
        if (mods == null || mods.isEmpty()) return;
        if (addListKeys != null) {
            for (String shortKey : addListKeys) {
                String path = "mod-management." + shortKey;
                List<String> list = new ArrayList<>(getConfig().getStringList(path));
                boolean changed = false;
                for (String m : mods) {
                    if (!list.contains(m)) { list.add(m); changed = true; }
                }
                if (changed) getConfig().set(path, list);
            }
        }
        if (removeListKeys != null) {
            for (String shortKey : removeListKeys) {
                String path = "mod-management." + shortKey;
                List<String> list = new ArrayList<>(getConfig().getStringList(path));
                boolean changed = list.removeIf(mods::contains);
                if (changed) getConfig().set(path, list);
            }
        }
        saveConfig();
    }

    private void removeFromModLists(List<String> mods, List<String> shortKeys) {
        if (mods == null || mods.isEmpty() || shortKeys == null || shortKeys.isEmpty()) return;
        for (String shortKey : shortKeys) {
            String path = "mod-management." + shortKey;
            List<String> list = new ArrayList<>(getConfig().getStringList(path));
            boolean changed = list.removeIf(mods::contains);
            if (changed) getConfig().set(path, list);
        }
        saveConfig();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> empty = Collections.emptyList();
        if (args.length == 1) {
            return prefixFilter(Arrays.asList("help","reload","banner","mod","misspenalty","waypoints","playermod"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "banner":
                if (args.length == 2) return prefixFilter(Collections.singletonList("reload"), args[1]);
                return empty;
            case "mod":
                if (args.length == 2) return prefixFilter(Arrays.asList("ban","force","allow"), args[1]);
                if (args.length >= 3) return prefixFilter(KNOWN_MODS, args[args.length - 1]);
                return empty;
            case "misspenalty":
                if (args.length == 2) return prefixFilter(Arrays.asList("allow","ban"), args[1]);
                return empty;
            case "waypoints":
                if (args.length == 2) return prefixFilter(Arrays.asList("create","delete","update"), args[1]);
                if (args.length == 3 && sender instanceof Player) {
                    Player pl = (Player) sender;
                    List<WaypointStorage.WaypointData> list = waypointStorage != null ? waypointStorage.get(pl.getUniqueId()) : Collections.emptyList();
                    return prefixFilter(list.stream().map(w -> w.name).collect(Collectors.toList()), args[2]);
                }
                int idx = args.length;
                if (idx == 4) return prefixFilter(Arrays.asList("~","0","100"), args[3]);
                if (idx == 5) return prefixFilter(Arrays.asList("~","64","200"), args[4]);
                if (idx == 6) return prefixFilter(Arrays.asList("~","0","-100"), args[5]);
                if (idx == 7) return prefixFilter(Arrays.asList("chroma","#RRGGBB","R,G,B","R,G,B,A"), args[6]);
                if (idx == 8) return prefixFilter(Arrays.asList("30","60","120","300","0"), args[7]);
                if (idx == 9) return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                return empty;
            case "playermod":
                if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().collect(Collectors.toList());
                if (args.length == 3) return prefixFilter(KNOWN_MODS, args[2]);
                if (args.length == 4) return prefixFilter(Arrays.asList("disable","enable"), args[3]);
                return empty;
            default:
                return empty;
        }
    }

    private List<String> prefixFilter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).sorted().collect(Collectors.toList());
    }
}
