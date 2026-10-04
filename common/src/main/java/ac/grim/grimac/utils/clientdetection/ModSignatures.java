package ac.grim.grimac.utils.clientdetection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Built-in channel signatures and the matching logic used for mod/client detection.
 *
 * <p>Every built-in entry was checked against the mod's source code or released jar
 * (see docs/CLIENT-DETECTION.md for the evidence). Mods that register no plugin
 * channel (e.g. Freecam) cannot be detected this way and are deliberately absent.
 */
public final class ModSignatures {

    // Mod loader namespaces; also used by the brand spoof check.
    public static final ModSignature FABRIC_API = ModSignature.of("^fabric(-[a-z0-9_.-]+)?:", "Fabric API");
    public static final ModSignature NEOFORGE = ModSignature.of("^neoforge:", "NeoForge");
    public static final ModSignature FORGE = ModSignature.of("^(forge|fml):", "Forge");
    // Forge 1.8 - 1.12 channel names, as sent by those clients to servers of the same version
    public static final ModSignature FORGE_LEGACY = ModSignature.of("^(FML|FML\\|HS|FML\\|MP|FORGE)$", "Forge");

    /** Signatures whose channels only a mod loader registers. */
    public static final List<ModSignature> MOD_LOADERS = List.of(FABRIC_API, NEOFORGE, FORGE, FORGE_LEGACY);

    public static final List<ModSignature> BUILT_IN = List.of(
            FABRIC_API,
            NEOFORGE,
            FORGE,
            FORGE_LEGACY,
            ModSignature.of("^lunar:apollo$", "Lunar Client"),
            ModSignature.of("^feather:client(/frag)?$", "Feather Client"),
            ModSignature.of("^labymod:", "LabyMod"),
            ModSignature.of("^voicechat:", "Simple Voice Chat"),
            ModSignature.of("^plasmo:voice(/|$)", "Plasmo Voice"),
            ModSignature.of("^xaerominimap:", "Xaero's Minimap"),
            ModSignature.of("^xaeroworldmap:", "Xaero's World Map"),
            ModSignature.of("^journeymap:", "JourneyMap"),
            ModSignature.of("^voxelmap:", "VoxelMap"),
            ModSignature.of("^servux:litematics$", "Litematica"),
            ModSignature.of("^servux:(hud_metadata|structures)$", "MiniHUD"),
            ModSignature.of("^(worldedit:cui|WECUI)$", "WorldEdit CUI"),
            ModSignature.of("^(replaymod:restrict|Replay\\|Restrict)$", "Replay Mod")
    );

    private ModSignatures() {
    }

    /**
     * Parses operator supplied {@code "regex -> name"} entries, skipping invalid ones.
     *
     * @param onError receives the offending entry and the reason
     */
    public static @NotNull List<ModSignature> parse(@NotNull Collection<?> entries, @NotNull BiConsumer<Object, String> onError) {
        List<ModSignature> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof String string)) {
                onError.accept(entry, "expected a string \"regex -> name\"");
                continue;
            }
            try {
                result.add(ModSignature.parse(string));
            } catch (IllegalArgumentException e) {
                onError.accept(entry, e.getMessage());
            }
        }
        return List.copyOf(result);
    }

    /**
     * @return the distinct names of all signatures matching at least one channel,
     * in signature order
     */
    public static @NotNull List<String> detect(@NotNull Collection<String> channels, @NotNull List<ModSignature> signatures) {
        if (channels.isEmpty()) return List.of();
        Set<String> names = new LinkedHashSet<>();
        for (ModSignature signature : signatures) {
            if (names.contains(signature.name())) continue;
            for (String channel : channels) {
                if (signature.matches(channel)) {
                    names.add(signature.name());
                    break;
                }
            }
        }
        return List.copyOf(names);
    }

    /** @return the first channel that only a mod loader registers, or null */
    public static @Nullable String findModLoaderChannel(@NotNull Collection<String> channels) {
        for (String channel : channels) {
            for (ModSignature signature : MOD_LOADERS) {
                if (signature.matches(channel)) return channel;
            }
        }
        return null;
    }
}
