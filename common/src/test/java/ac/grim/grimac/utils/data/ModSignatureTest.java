package ac.grim.grimac.utils.data;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSignatureTest {

    private static List<String> detect(String... channels) {
        return ModSignature.detect(List.of(channels), ModSignature.BUILT_IN);
    }

    private static List<String> read(String data, int limit) {
        return ModSignature.readChannels(data.getBytes(StandardCharsets.UTF_8), limit);
    }

    @Test
    void builtInSignaturesMatchVerifiedChannels() {
        assertEquals(List.of("Fabric API"), detect("fabric:recipe_sync", "fabric-menu-api-v1:open_screen"));
        assertEquals(List.of("Fabric API"), detect("fabric-screen-handler-api-v1:open_screen"));
        assertEquals(List.of("NeoForge"), detect("neoforge:register", "c:version"));
        assertEquals(List.of("Forge"), detect("forge:handshake"));
        assertEquals(List.of("Forge"), detect("fml:hs"));
        assertEquals(List.of("Forge"), detect("FML|HS", "FORGE"));
        assertEquals(List.of("Lunar Client"), detect("lunar:apollo"));
        assertEquals(List.of("Feather Client"), detect("feather:client/frag"));
        assertEquals(List.of("LabyMod"), detect("labymod:neo"));
        assertEquals(List.of("Simple Voice Chat"), detect("voicechat:secret"));
        assertEquals(List.of("Plasmo Voice"), detect("plasmo:voice/v2/installed"));
        assertEquals(List.of("Xaero's Minimap"), detect("xaerominimap:main"));
        assertEquals(List.of("Xaero's World Map"), detect("xaeroworldmap:main"));
        assertEquals(List.of("JourneyMap"), detect("journeymap:version"));
        assertEquals(List.of("VoxelMap"), detect("voxelmap:settings"));
        assertEquals(List.of("Litematica"), detect("servux:litematics"));
        assertEquals(List.of("MiniHUD"), detect("servux:structures", "servux:hud_metadata"));
        assertEquals(List.of("WorldEdit CUI"), detect("worldedit:cui"));
        assertEquals(List.of("WorldEdit CUI"), detect("WECUI"));
        assertEquals(List.of("Replay Mod"), detect("replaymod:restrict"));
        assertEquals(List.of("Replay Mod"), detect("Replay|Restrict"));
    }

    @Test
    void genericChannelsAreNotAttributed() {
        assertEquals(List.of(), detect("minecraft:brand", "minecraft:register", "c:register", "c:version",
                "bungeecord:main", "BungeeCord", "worldinfo:world_id", "servux:entity_data", "fabricated:thing",
                "notfabric:x", "forgery:x", "lunar:other"));
        assertEquals(List.of(), detect());
    }

    @Test
    void namesAreDistinctAndInSignatureOrder() {
        List<String> mods = detect("voicechat:state", "fabric:recipe_sync", "voicechat:secret", "fabric:attachment_sync_v1");
        assertEquals(List.of("Fabric API", "Simple Voice Chat"), mods);
    }

    @Test
    void parsesOperatorSignatures() {
        ModSignature signature = ModSignature.parse("  ^mymod:  ->  My Mod ");
        assertEquals("^mymod:", signature.pattern().pattern());
        assertEquals("My Mod", signature.name());
        assertTrue(signature.matches("mymod:sync"));

        // the last separator splits, so the regex may contain "->"
        ModSignature arrow = ModSignature.parse("a->b -> Arrow");
        assertEquals("a->b", arrow.pattern().pattern());
        assertEquals("Arrow", arrow.name());

        assertThrows(IllegalArgumentException.class, () -> ModSignature.parse("no separator"));
        assertThrows(IllegalArgumentException.class, () -> ModSignature.parse("-> name"));
        assertThrows(IllegalArgumentException.class, () -> ModSignature.parse("^x: ->"));
        assertThrows(IllegalArgumentException.class, () -> ModSignature.parse("[unclosed -> Broken"));

        List<ModSignature> signatures = new ArrayList<>(ModSignature.BUILT_IN);
        signatures.add(ModSignature.parse("^a: -> A"));
        assertEquals(List.of("Fabric API", "A"), ModSignature.detect(List.of("a:b", "fabric:x"), signatures));
    }

    @Test
    void findsModLoaderChannels() {
        assertEquals("fabric:recipe_sync", ModSignature.findModLoaderChannel(List.of("voicechat:secret", "fabric:recipe_sync")));
        assertEquals("neoforge:network", ModSignature.findModLoaderChannel(List.of("c:register", "neoforge:network")));
        assertEquals("forge:handshake", ModSignature.findModLoaderChannel(List.of("forge:handshake")));
        assertEquals("FML|HS", ModSignature.findModLoaderChannel(List.of("FML|HS")));
        assertNull(ModSignature.findModLoaderChannel(List.of("c:register", "c:version", "lunar:apollo", "voicechat:secret")));
        assertNull(ModSignature.findModLoaderChannel(List.of()));
    }

    @Test
    void readsRegisteredChannels() {
        assertEquals(List.of("fabric:a", "voicechat:b"), read("fabric:a\0voicechat:b", 10));
        // Forge terminates every entry, Fabric only separates them
        assertEquals(List.of("forge:a", "forge:b"), read("forge:a\0forge:b\0", 10));
        assertEquals(List.of("x:y"), read("\0\0x:y\0\0", 10));
        assertEquals(List.of(), read("", 10));
    }

    @Test
    void readChannelsHonoursLimits() {
        assertEquals(List.of("a:1", "a:2"), read("a:1\0a:2\0a:3", 2));
        assertEquals(List.of(), read("a:1", 0));

        String tooLong = "a:" + "x".repeat(ModSignature.MAX_CHANNEL_LENGTH);
        assertEquals(List.of("b:c"), read(tooLong + "\0b:c", 10));
    }
}
