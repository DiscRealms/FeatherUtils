package net.discrealms.featherClientUtility;

import net.digitalingot.feather.serverapi.api.player.FeatherPlayer;

public class BypassMissPenaltyManager {

    public void setBypassMissPenalty(FeatherPlayer player, boolean bypass) {
        if (player != null) {
            player.bypassMissPenalty(bypass);
        }
    }
}
