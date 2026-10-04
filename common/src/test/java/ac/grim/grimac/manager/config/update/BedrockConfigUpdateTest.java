package ac.grim.grimac.manager.config.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockConfigUpdateTest {

    @Test
    void v11ConfigGainsBedrockBlockAndKeepsUserValues(@TempDir Path dir) throws Exception {
        String bundled;
        try (InputStream in = ConfigUpdater.class.getResourceAsStream("/config/en.yml")) {
            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Rebuild what a v11 config looked like: no bedrock block, version 11, plus a user change
        String v11 = bundled
                .replaceAll("(?s)\n# Experimental: track Bedrock.*?    - Exploit\n", "\n")
                .replace("config-version: 12", "config-version: 11")
                .replace("experimental-checks: false", "experimental-checks: true");
        assertFalse(v11.contains("bedrock:"), "fixture has no bedrock block");
        File config = dir.resolve("config.yml").toFile();
        Files.writeString(config.toPath(), v11, StandardCharsets.UTF_8);

        new ConfigUpdater(ConfigUpdater.class, Logger.getLogger("test")).update(config, GrimConfigSpecs.mainConfig());

        String updated = Files.readString(config.toPath(), StandardCharsets.UTF_8);
        assertTrue(updated.contains("config-version: 12"), "version bumped");
        assertTrue(updated.contains("bedrock:"), "bedrock block added");
        assertTrue(updated.contains("experimental-checks: true"), "user value kept");
    }
}
