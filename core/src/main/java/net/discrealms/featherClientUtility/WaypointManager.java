package net.discrealms.featherClientUtility;

import net.digitalingot.feather.serverapi.api.FeatherAPI;
import net.digitalingot.feather.serverapi.api.player.FeatherPlayer;
import net.digitalingot.feather.serverapi.api.waypoint.WaypointBuilder;
import net.digitalingot.feather.serverapi.api.waypoint.WaypointColor;
import net.digitalingot.feather.serverapi.api.waypoint.WaypointDuration;
import net.digitalingot.feather.serverapi.api.waypoint.WaypointService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WaypointManager {

    private final Map<UUID, Map<String, UUID>> waypointIds = new HashMap<>();

    public UUID createWaypoint(FeatherPlayer player,
                               String name,
                               int x, int y, int z,
                               WaypointColor color,
                               WaypointDuration duration,
                               UUID worldId) {
        if (player == null) return null;

        WaypointService waypointService = FeatherAPI.getWaypointService();
        WaypointBuilder builder = waypointService.createWaypointBuilder(x, y, z)
                .withName(name);
        if (color != null) builder = builder.withColor(color);
        if (duration != null) builder = builder.withDuration(duration);
        if (worldId != null) builder = builder.withWorldId(worldId);

        UUID id = waypointService.createWaypoint(player, builder);

        waypointIds.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(name, id);
        return id;
    }

    public void deleteWaypoint(FeatherPlayer player, String name) {
        if (player == null) return;
        Map<String, UUID> playerMap = waypointIds.get(player.getUniqueId());
        if (playerMap == null) return;
        UUID id = playerMap.remove(name);
        if (id != null) {
            FeatherAPI.getWaypointService().destroyWaypoint(player, id);
        }
    }

    public UUID updateWaypoint(FeatherPlayer player,
                               String name,
                               Integer x, Integer y, Integer z,
                               WaypointColor color,
                               WaypointDuration duration,
                               UUID worldId,
                               int fallbackX, int fallbackY, int fallbackZ) {
        deleteWaypoint(player, name);
        int nx = x != null ? x : fallbackX;
        int ny = y != null ? y : fallbackY;
        int nz = z != null ? z : fallbackZ;
        return createWaypoint(player, name, nx, ny, nz, color, duration, worldId);
    }
}
