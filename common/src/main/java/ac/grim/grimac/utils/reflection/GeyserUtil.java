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

    /**
     * @return the Bedrock device OS and input mode, or null if the player isn't a
     * Bedrock player or neither Floodgate nor Geyser can tell
     */
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
        } catch (RuntimeException | LinkageError e) {
            // Older Floodgate/Geyser API without these methods, or the API isn't initialized yet
        }
        return null;
    }

    public record BedrockDevice(String os, String inputMode) {
    }
}
