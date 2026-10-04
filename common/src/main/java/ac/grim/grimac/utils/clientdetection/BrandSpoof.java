package ac.grim.grimac.utils.clientdetection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Pure decision logic of the brand spoof check.
 */
public final class BrandSpoof {

    private BrandSpoof() {
    }

    /**
     * The vanilla client reports exactly {@code "vanilla"}; only a modified client
     * registers mod loader channels.
     *
     * @return the mod loader channel contradicting a {@code "vanilla"} brand, or null
     */
    public static @Nullable String vanillaWithModLoader(@Nullable String brand, @NotNull Collection<String> channels) {
        if (brand == null || !brand.equalsIgnoreCase("vanilla")) return null;
        return ModSignatures.findModLoaderChannel(channels);
    }

    /**
     * @param first the first valid brand of the session
     * @param next  a later valid brand of the same session
     * @return whether the client changed its brand
     */
    public static boolean isBrandChange(@Nullable String first, @Nullable String next) {
        return first != null && next != null && !first.equals(next);
    }
}
