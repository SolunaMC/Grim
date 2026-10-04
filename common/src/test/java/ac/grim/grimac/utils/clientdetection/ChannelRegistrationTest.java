package ac.grim.grimac.utils.clientdetection;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelRegistrationTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void recognisesRegisterChannels() {
        assertTrue(ChannelRegistration.isRegisterChannel("minecraft:register"));
        assertTrue(ChannelRegistration.isRegisterChannel("REGISTER"));
        assertFalse(ChannelRegistration.isRegisterChannel("minecraft:unregister"));
        assertFalse(ChannelRegistration.isRegisterChannel("c:register"));
        assertFalse(ChannelRegistration.isRegisterChannel("minecraft:brand"));
    }

    @Test
    void splitsOnNul() {
        assertEquals(List.of("fabric:a", "voicechat:b"), ChannelRegistration.parse(bytes("fabric:a\0voicechat:b"), 10));
        // Forge terminates every entry, Fabric only separates them
        assertEquals(List.of("forge:a", "forge:b"), ChannelRegistration.parse(bytes("forge:a\0forge:b\0"), 10));
        assertEquals(List.of("x:y"), ChannelRegistration.parse(bytes("\0\0x:y\0\0"), 10));
        assertEquals(List.of(), ChannelRegistration.parse(new byte[0], 10));
    }

    @Test
    void honoursLimits() {
        assertEquals(List.of("a:1", "a:2"), ChannelRegistration.parse(bytes("a:1\0a:2\0a:3"), 2));
        assertEquals(List.of(), ChannelRegistration.parse(bytes("a:1"), 0));

        String tooLong = "a:" + "x".repeat(ChannelRegistration.MAX_CHANNEL_LENGTH);
        assertEquals(List.of("b:c"), ChannelRegistration.parse(bytes(tooLong + "\0b:c"), 10));
    }
}
