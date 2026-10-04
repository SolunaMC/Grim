package ac.grim.grimac.utils.clientdetection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandSpoofTest {

    @Test
    void vanillaBrandWithModLoaderChannels() {
        assertEquals("fabric:recipe_sync", BrandSpoof.vanillaWithModLoader("vanilla", List.of("fabric:recipe_sync")));
        assertEquals("neoforge:register", BrandSpoof.vanillaWithModLoader("Vanilla", List.of("c:version", "neoforge:register")));
        assertEquals("forge:handshake", BrandSpoof.vanillaWithModLoader("VANILLA", List.of("forge:handshake")));
    }

    @Test
    void consistentClientsAreNotFlagged() {
        // honest mod loader brands
        assertNull(BrandSpoof.vanillaWithModLoader("fabric", List.of("fabric:recipe_sync")));
        assertNull(BrandSpoof.vanillaWithModLoader("neoforge", List.of("neoforge:register")));
        assertNull(BrandSpoof.vanillaWithModLoader("forge", List.of("forge:handshake")));
        // clients reporting their own brand
        assertNull(BrandSpoof.vanillaWithModLoader("lunarclient:v2.18.3-2451,fabric", List.of("fabric:recipe_sync", "lunar:apollo")));
        assertNull(BrandSpoof.vanillaWithModLoader("Feather Fabric", List.of("fabric:recipe_sync", "feather:client")));
        // vanilla brand with only non loader channels, or none
        assertNull(BrandSpoof.vanillaWithModLoader("vanilla", List.of("c:register", "lunar:apollo", "voicechat:secret")));
        assertNull(BrandSpoof.vanillaWithModLoader("vanilla", List.of()));
        // brand not known yet / similar brand strings
        assertNull(BrandSpoof.vanillaWithModLoader(null, List.of("fabric:recipe_sync")));
        assertNull(BrandSpoof.vanillaWithModLoader("vanilla ", List.of("fabric:recipe_sync")));
        assertNull(BrandSpoof.vanillaWithModLoader("vanillaplus", List.of("fabric:recipe_sync")));
    }

    @Test
    void brandChange() {
        assertTrue(BrandSpoof.isBrandChange("fabric", "vanilla"));
        assertFalse(BrandSpoof.isBrandChange("vanilla", "vanilla"));
        assertFalse(BrandSpoof.isBrandChange(null, "vanilla"));
        assertFalse(BrandSpoof.isBrandChange("vanilla", null));
    }
}
