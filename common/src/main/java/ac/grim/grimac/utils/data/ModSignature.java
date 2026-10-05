package ac.grim.grimac.utils.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Detects mods by the plugin channels a client registers. Mods without networking (e.g. Freecam) register nothing.
public record ModSignature(@NotNull Pattern pattern, @NotNull String name) {
    public static final int MAX_CHANNEL_LENGTH = 256;

    public static final ModSignature FABRIC_API = of("^fabric(-[a-z0-9_.-]+)?:", "Fabric API"); // fabric:recipe_sync, fabric-menu-api-v1:open_screen
    public static final ModSignature NEOFORGE = of("^neoforge:", "NeoForge"); // neoforge:register, neoforge:network
    public static final ModSignature FORGE = of("^(forge|fml):", "Forge"); // forge:handshake, fml:handshake, fml:hs (ViaVersion)
    public static final ModSignature FORGE_LEGACY = of("^(FML|FML\\|HS|FML\\|MP|FORGE)$", "Forge"); // Forge 1.8 - 1.12

    // Only a mod loader registers these, used by BadPacketsT
    public static final List<ModSignature> MOD_LOADERS = List.of(FABRIC_API, NEOFORGE, FORGE, FORGE_LEGACY);

    public static final List<ModSignature> BUILT_IN = List.of(
            FABRIC_API,
            NEOFORGE,
            FORGE,
            FORGE_LEGACY,
            of("^lunar:apollo$", "Lunar Client"), // https://github.com/LunarClient/Apollo
            of("^feather:client(/frag)?$", "Feather Client"), // https://github.com/FeatherMC/feather-server-api
            of("^labymod:", "LabyMod"), // labymod:neo, https://github.com/LabyMod/labymod4-server-api
            of("^voicechat:", "Simple Voice Chat"), // https://github.com/henkelmax/simple-voice-chat
            of("^plasmo:voice(/|$)", "Plasmo Voice"), // plasmo:voice/v2, https://github.com/plasmoapp/plasmo-voice
            of("^xaerominimap:", "Xaero's Minimap"), // xaerominimap:main
            of("^xaeroworldmap:", "Xaero's World Map"), // xaeroworldmap:main
            of("^journeymap:", "JourneyMap"), // journeymap:version
            of("^voxelmap:", "VoxelMap"), // voxelmap:settings
            of("^servux:litematics$", "Litematica"), // https://github.com/sakura-ryoko/litematica
            of("^servux:(hud_metadata|structures)$", "MiniHUD"), // https://github.com/sakura-ryoko/minihud
            of("^(worldedit:cui|WECUI)$", "WorldEdit CUI"), // https://github.com/EngineHub/WorldEditCUI
            of("^(replaymod:restrict|Replay\\|Restrict)$", "Replay Mod") // https://github.com/ReplayMod/ReplayMod
    );

    public static @NotNull ModSignature of(@NotNull String regex, @NotNull String name) {
        return new ModSignature(Pattern.compile(regex), name);
    }

    // "regex -> name", split at the last "->" so the regex itself may contain one
    public static @NotNull ModSignature parse(@NotNull String entry) {
        int separator = entry.lastIndexOf("->");
        String regex = separator < 0 ? "" : entry.substring(0, separator).trim();
        String name = separator < 0 ? "" : entry.substring(separator + 2).trim();
        if (regex.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("expected \"regex -> name\"");
        }
        try {
            return of(regex, name);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid regex: " + e.getDescription(), e);
        }
    }

    public boolean matches(@NotNull String channel) {
        return pattern.matcher(channel).find();
    }

    // Distinct names of all signatures matching at least one channel, in signature order
    public static @NotNull List<String> detect(@NotNull Collection<String> channels, @NotNull List<ModSignature> signatures) {
        if (channels.isEmpty()) return List.of();
        Set<String> names = new LinkedHashSet<>();
        for (ModSignature signature : signatures) {
            if (names.contains(signature.name)) continue;
            for (String channel : channels) {
                if (signature.matches(channel)) {
                    names.add(signature.name);
                    break;
                }
            }
        }
        return List.copyOf(names);
    }

    public static @Nullable String findModLoaderChannel(@NotNull Collection<String> channels) {
        for (String channel : channels) {
            for (ModSignature signature : MOD_LOADERS) {
                if (signature.matches(channel)) return channel;
            }
        }
        return null;
    }

    // minecraft:register (REGISTER before 1.13) payloads are channel names separated by NUL bytes
    public static @NotNull List<String> readChannels(byte @NotNull [] data, int limit) {
        List<String> channels = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= data.length && channels.size() < limit; i++) {
            if (i == data.length || data[i] == 0) {
                int length = i - start;
                if (length > 0 && length <= MAX_CHANNEL_LENGTH) {
                    channels.add(new String(data, start, length, StandardCharsets.UTF_8));
                }
                start = i + 1;
            }
        }
        return channels;
    }
}
