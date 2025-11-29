package net.discrealms.featherClientUtility;

import net.digitalingot.feather.serverapi.api.model.FeatherMod;
import net.digitalingot.feather.serverapi.api.player.FeatherPlayer;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ModManager {

    private Collection<FeatherMod> toMods(Collection<String> names) {
        return names.stream().map(FeatherMod::new).collect(Collectors.toList());
    }

    public void disableMods(FeatherPlayer player, List<String> mods) {
        if (player != null && mods != null && !mods.isEmpty()) {
            player.disableMods(toMods(mods));
        }
    }

    public void banMods(FeatherPlayer player, List<String> mods) {
        if (player != null && mods != null && !mods.isEmpty()) {
            player.blockMods(toMods(mods));
        }
    }

    public void allowMods(FeatherPlayer player, List<String> mods) {
        if (player != null && mods != null && !mods.isEmpty()) {
            player.unblockMods(toMods(mods));
        }
    }

    public void enableMods(FeatherPlayer player, List<String> mods) {
        if (player != null && mods != null && !mods.isEmpty()) {
            player.enableMods(toMods(mods));
        }
    }
}
