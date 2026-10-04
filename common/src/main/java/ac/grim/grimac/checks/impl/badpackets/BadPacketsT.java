package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.clientdetection.BrandSpoof;
import ac.grim.grimac.utils.clientdetection.ClientDetectionSettings;
import ac.grim.grimac.utils.reflection.GeyserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Brand spoofing. Disabled unless {@code client-brand.spoof-check.enabled} is set,
 * fed by {@link ac.grim.grimac.checks.impl.misc.ClientBrand}; flags each condition
 * at most once per session.
 */
@CheckData(name = "BadPacketsT", stableKey = "grim.badpackets.brand_spoof", description = "Client brand contradicts its registered channels or changed during the session")
public class BadPacketsT extends Check {
    private static final int VANILLA_WITH_MOD_LOADER = 0;
    private static final int BRAND_CHANGE = 1;
    private static final Verbose V = Verbose.of("brand={str}, channel={str}") // VANILLA_WITH_MOD_LOADER
            .or("first={str}, now={str}");                                     // BRAND_CHANGE

    private boolean flaggedVanillaWithModLoader;
    private boolean flaggedBrandChange;

    public BadPacketsT(GrimPlayer player) {
        super(player);
    }

    /** Called whenever the brand or the registered channels change. */
    public void onClientInfo(@Nullable String brand, @NotNull Collection<String> channels) {
        if (flaggedVanillaWithModLoader) return;
        ClientDetectionSettings settings = GrimAPI.INSTANCE.getConfigManager().getClientDetection();
        if (!settings.spoofCheckEnabled() || !settings.spoofVanillaWithModLoader() || isBedrock()) return;

        String channel = BrandSpoof.vanillaWithModLoader(brand, channels);
        if (channel == null) return;
        flaggedVanillaWithModLoader = true;
        flag(V.write(verbose(), VANILLA_WITH_MOD_LOADER).str(brand).str(channel));
    }

    /** Called for every valid brand payload after the first one. */
    public void onLaterBrand(@NotNull String first, @NotNull String next) {
        if (flaggedBrandChange) return;
        ClientDetectionSettings settings = GrimAPI.INSTANCE.getConfigManager().getClientDetection();
        if (!settings.spoofCheckEnabled() || !settings.spoofBrandChange() || isBedrock()) return;

        if (!BrandSpoof.isBrandChange(first, next)) return;
        flaggedBrandChange = true;
        flag(V.write(verbose(), BRAND_CHANGE).str(first).str(next));
    }

    // Bedrock players are normally exempt from Grim entirely; don't rely on it here
    private boolean isBedrock() {
        return GeyserUtil.isBedrockPlayer(player.getUniqueId());
    }
}
