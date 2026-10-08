package ac.grim.grimac.utils.reflection;

import lombok.experimental.UtilityClass;
import org.geysermc.api.Geyser;
import org.geysermc.api.connection.Connection;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@UtilityClass
public class GeyserUtil {
    // Floodgate is the authentication system for Geyser on servers that use Geyser as a proxy instead of installing it as a plugin directly on the server
    private static final boolean floodgate = ReflectionUtils.hasClass("org.geysermc.floodgate.api.FloodgateApi");
    private static final boolean geyser = ReflectionUtils.hasClass("org.geysermc.api.Geyser");

    public static boolean isBedrockPlayer(UUID uuid) {
        return floodgate && FloodgateApi.getInstance().isFloodgatePlayer(uuid)
                || geyser && Geyser.api().isBedrockPlayer(uuid);
    }

    // Geyser formatted player string
    // This will never happen for Java players, as the first character in the 3rd group is always 4 (xxxxxxxx-xxxx-4xxx-xxxx-xxxxxxxxxxxx)
    public static boolean hasGeyserUuid(UUID uuid) {
        return uuid.toString().startsWith("00000000-0000-0000-0009");
    }

    public static boolean isBedrock(UUID uuid) {
        return isBedrockPlayer(uuid) || hasGeyserUuid(uuid);
    }

    public static @Nullable BedrockDevice getBedrockDevice(UUID uuid) {
        try {
            if (floodgate) {
                FloodgatePlayer player = FloodgateApi.getInstance().getPlayer(uuid);
                if (player != null) {
                    return new BedrockDevice(String.valueOf(player.getDeviceOs()), String.valueOf(player.getInputMode()));
                }
            }
            if (geyser) {
                Connection connection = Geyser.api().connectionByUuid(uuid);
                if (connection != null) {
                    return new BedrockDevice(String.valueOf(connection.platform()), String.valueOf(connection.inputMode()));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Older Floodgate/Geyser API without these methods, or the API isn't initialized yet
        }
        return null;
    }

    public static @Nullable String getBedrockVersion(UUID uuid) {
        try {
            if (floodgate) {
                FloodgatePlayer player = FloodgateApi.getInstance().getPlayer(uuid);
                if (player != null) return player.getVersion();
            }
            if (geyser) {
                Connection connection = Geyser.api().connectionByUuid(uuid);
                if (connection != null) return connection.version();
            }
        } catch (RuntimeException | LinkageError e) {
            // Older Floodgate/Geyser API without these methods, or the API isn't initialized yet
        }
        return null;
    }

    public record BedrockDevice(String os, String inputMode) {
    }
}
