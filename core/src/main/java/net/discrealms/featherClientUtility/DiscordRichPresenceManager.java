package net.discrealms.featherClientUtility;

import net.digitalingot.feather.serverapi.api.FeatherAPI;
import net.digitalingot.feather.serverapi.api.meta.DiscordActivity;
import net.digitalingot.feather.serverapi.api.player.FeatherPlayer;

public class DiscordRichPresenceManager {

    public void update(FeatherPlayer player, String details, String state) {
        if (player == null) return;

        DiscordActivity activity = DiscordActivity.builder()
                .withDetails(details)
                .withState(state)
                .build();

        FeatherAPI.getMetaService().updateDiscordActivity(player, activity);
    }
}
