package ac.grim.grimac.manager.config.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockConfigUpdateTest {

    @Test
    void everyBundledLanguageHasABedrockFile() throws Exception {
        for (String lang : new String[]{"en", "de", "es", "fr", "it", "ja", "nl", "pl", "pt", "ro", "ru", "tr", "zh"}) {
            try (InputStream in = ConfigUpdater.class.getResourceAsStream("/bedrock/" + lang + ".yml")) {
                assertNotNull(in, "missing /bedrock/" + lang + ".yml");
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(content.contains("bedrock:") && content.contains("enabled: false"), lang);
            }
        }
    }

    @Test
    void updaterKeepsUserValues(@TempDir Path dir) throws Exception {
        // A current-version user file is left as is; missing keys fall back to the defaults in code
        File bedrock = dir.resolve("bedrock.yml").toFile();
        Files.writeString(bedrock.toPath(), "bedrock:\n  enabled: true\nconfig-flavor: V2\nconfig-version: 1\n", StandardCharsets.UTF_8);

        new ConfigUpdater(ConfigUpdater.class, Logger.getLogger("test")).update(bedrock, GrimConfigSpecs.bedrock());

        String updated = Files.readString(bedrock.toPath(), StandardCharsets.UTF_8);
        assertTrue(updated.contains("enabled: true"), "user value kept");
    }
}
