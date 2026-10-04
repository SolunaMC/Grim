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

class MainConfigV12UpdateTest {

    @Test
    void v11ConfigGainsExploitLimitsAndKeepsUserValues(@TempDir Path dir) throws Exception {
        String bundled;
        try (InputStream in = ConfigUpdater.class.getResourceAsStream("/config/en.yml")) {
            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Rebuild a v11 config: no exploit limits, version 11, plus a user change
        String v11 = bundled
                .replaceAll("(?s)(    distance-to-check-if-ghostblocks: 2\\n).*?    max-command-length: 2048\\n", "$1")
                .replace("config-version: 12", "config-version: 11")
                .replace("experimental-checks: false", "experimental-checks: true");
        assertFalse(v11.contains("max-sign-length"), "fixture has no exploit limits");

        File config = dir.resolve("config.yml").toFile();
        Files.writeString(config.toPath(), v11, StandardCharsets.UTF_8);
        new ConfigUpdater(ConfigUpdater.class, Logger.getLogger("test")).update(config, GrimConfigSpecs.mainConfig());

        String updated = Files.readString(config.toPath(), StandardCharsets.UTF_8);
        assertTrue(updated.contains("config-version: 12"), "version bumped");
        assertTrue(updated.contains("max-sign-length: 256"), "exploit limits added");
        assertTrue(updated.contains("commands-per-second: 20"), "exploit limits added");
        assertTrue(updated.contains("experimental-checks: true"), "user value kept");
    }

    @Test
    void everyLanguageHasTheSameExploitKeys() throws Exception {
        for (String lang : new String[]{"en", "de", "es", "fr", "it", "ja", "nl", "pl", "pt", "ro", "ru", "tr", "zh"}) {
            try (InputStream in = ConfigUpdater.class.getResourceAsStream("/config/" + lang + ".yml")) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(content.contains("config-version: 12"), lang);
                for (String key : new String[]{"tab-completes-per-second", "max-item-packet-bytes", "max-item-nbt-depth",
                        "max-sign-line-length", "max-sign-length", "sign-block-control-characters", "sign-block-format-codes",
                        "payloads-per-second", "max-payload-bytes", "blocked-payload-channels", "commands-per-second", "max-command-length"}) {
                    assertTrue(content.contains("    " + key + ": "), lang + " " + key);
                }
            }
        }
    }
}
