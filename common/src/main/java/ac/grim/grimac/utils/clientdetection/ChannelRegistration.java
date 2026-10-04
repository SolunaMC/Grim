package ac.grim.grimac.utils.clientdetection;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for {@code minecraft:register} (1.13+) and {@code REGISTER} (1.8 - 1.12)
 * payloads: channel names separated by NUL bytes.
 */
public final class ChannelRegistration {

    /** Longest channel name that is kept; longer entries are ignored. */
    public static final int MAX_CHANNEL_LENGTH = 256;

    private ChannelRegistration() {
    }

    public static boolean isRegisterChannel(@NotNull String channel) {
        return channel.equals("minecraft:register") || channel.equals("REGISTER");
    }

    /**
     * @param limit maximum number of channel names to return
     * @return the non-empty channel names, in payload order
     */
    public static @NotNull List<String> parse(byte @NotNull [] data, int limit) {
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
