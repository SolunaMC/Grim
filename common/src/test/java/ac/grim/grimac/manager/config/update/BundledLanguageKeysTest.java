package ac.grim.grimac.manager.config.update;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Every bundled translation has exactly the keys of the en.yml in the same folder
class BundledLanguageKeysTest {

    // Configuralize only uses a language when every source has it, so each folder needs all of them
    private static final String[] LANGUAGES = {"de", "es", "fr", "it", "ja", "nl", "pl", "pt", "ro", "ru", "tr", "zh"};
    private static final String[] FOLDERS = {"config", "messages", "database", "discord", "punishments",
            "databases/mongo", "databases/mysql", "databases/postgres", "databases/redis", "databases/sqlite", "bedrock"};

    @Test
    void everyFolderHasEveryLanguageWithTheEnglishKeys() throws Exception {
        for (String folder : FOLDERS) {
            assertSameKeys(folder, LANGUAGES);
        }
    }

    private static void assertSameKeys(String folder, String[] languages) throws Exception {
        Set<String> english = keys(folder, "en");
        assertFalse(english.isEmpty(), folder + "/en.yml has keys");
        for (String lang : languages) {
            Set<String> translated = keys(folder, lang);
            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(translated);
            Set<String> extra = new TreeSet<>(translated);
            extra.removeAll(english);
            assertTrue(missing.isEmpty() && extra.isEmpty(),
                    folder + "/" + lang + ".yml missing " + missing + ", extra " + extra);
        }
    }

    private static Set<String> keys(String folder, String lang) throws Exception {
        Map<?, ?> root = new Yaml().load(resource("/" + folder + "/" + lang + ".yml"));
        assertNotNull(root, folder + "/" + lang + ".yml is not empty");
        Set<String> keys = new TreeSet<>();
        collectKeys(root, "", keys);
        return keys;
    }

    private static void collectKeys(Map<?, ?> map, String prefix, Set<String> keys) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix + entry.getKey();
            keys.add(key);
            if (entry.getValue() instanceof Map<?, ?> child) {
                collectKeys(child, key + ".", keys);
            }
        }
    }

    // Normalised to LF: the resource has CRLF line endings when built from a Windows checkout
    private static String resource(String path) throws Exception {
        try (InputStream in = ConfigUpdater.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
