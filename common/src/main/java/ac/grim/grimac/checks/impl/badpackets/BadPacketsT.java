package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.ModSignature;
import ac.grim.grimac.utils.reflection.GeyserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// Fed by ClientBrand, flags each condition at most once per session
@CheckData(name = "BadPacketsT", stableKey = "grim.badpackets.brand_spoof", description = "Client brand contradicts its registered channels or changed during the session")
public class BadPacketsT extends Check {
    private static final int VANILLA_WITH_MOD_LOADER = 0;
    private static final int BRAND_CHANGE = 1;
    private static final Verbose V = Verbose.of("brand={str}, channel={str}") // VANILLA_WITH_MOD_LOADER
            .or("first={str}, now={str}");                                     // BRAND_CHANGE

    private boolean checkVanillaWithModLoader;
    private boolean checkBrandChange;
    private boolean flaggedVanillaWithModLoader;
    private boolean flaggedBrandChange;

    public BadPacketsT(GrimPlayer player) {
        super(player);
    }

    public void onClientInfo(@Nullable String brand, @NotNull Collection<String> channels) {
        if (flaggedVanillaWithModLoader || !checkVanillaWithModLoader || isBedrock()) return;
        // The vanilla client reports exactly "vanilla" and registers no mod loader channels
        if (brand == null || !brand.equalsIgnoreCase("vanilla")) return;

        String channel = ModSignature.findModLoaderChannel(channels);
        if (channel == null) return;
        flaggedVanillaWithModLoader = true;
        flag(V.write(verbose(), VANILLA_WITH_MOD_LOADER).str(brand).str(channel));
    }

    public void onLaterBrand(@NotNull String first, @NotNull String next) {
        if (flaggedBrandChange || !checkBrandChange || isBedrock() || first.equals(next)) return;
        flaggedBrandChange = true;
        flag(V.write(verbose(), BRAND_CHANGE).str(first).str(next));
    }

    // Bedrock players are normally exempt from Grim entirely, don't rely on it here
    private boolean isBedrock() {
        return GeyserUtil.isBedrockPlayer(player.getUniqueId());
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        boolean enabled = config.getBooleanElse("client-brand.spoof-check.enabled", false);
        checkVanillaWithModLoader = enabled && config.getBooleanElse("client-brand.spoof-check.vanilla-with-mod-loader", true);
        checkBrandChange = enabled && config.getBooleanElse("client-brand.spoof-check.brand-change", false);
    }
}
