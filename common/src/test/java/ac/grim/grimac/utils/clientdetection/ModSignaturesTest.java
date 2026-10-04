package ac.grim.grimac.utils.clientdetection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSignaturesTest {

    private static List<String> detect(String... channels) {
        return ModSignatures.detect(List.of(channels), ModSignatures.BUILT_IN);
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
    }

    @Test
    void parseSkipsInvalidEntries() {
        List<Object> errors = new ArrayList<>();
        List<ModSignature> parsed = ModSignatures.parse(List.of("^a: -> A", "broken", 5, "(x -> X"),
                (entry, reason) -> errors.add(entry));
        assertEquals(1, parsed.size());
        assertEquals("A", parsed.get(0).name());
        assertEquals(List.of("broken", 5, "(x -> X"), errors);

        List<ModSignature> signatures = new ArrayList<>(ModSignatures.BUILT_IN);
        signatures.addAll(parsed);
        assertEquals(List.of("Fabric API", "A"), ModSignatures.detect(List.of("a:b", "fabric:x"), signatures));
    }

    @Test
    void findsModLoaderChannels() {
        assertEquals("fabric:recipe_sync", ModSignatures.findModLoaderChannel(List.of("voicechat:secret", "fabric:recipe_sync")));
        assertEquals("neoforge:network", ModSignatures.findModLoaderChannel(List.of("c:register", "neoforge:network")));
        assertEquals("FML|HS", ModSignatures.findModLoaderChannel(List.of("FML|HS")));
        assertNull(ModSignatures.findModLoaderChannel(List.of("c:register", "c:version", "lunar:apollo", "voicechat:secret")));
    }
}
