package ac.grim.grimac.manager.config.update;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Only client-brand is compared, the upstream keys already differ between languages
class BundledClientBrandKeysTest {

    private static final String[] CONFIG_LANGUAGES = {"de", "es", "fr", "it", "ja", "nl", "pl", "pt", "ro", "ru", "tr", "zh"};
    private static final String[] MESSAGES_LANGUAGES = {"de", "es", "fr", "it", "ja", "nl", "pt", "ru", "tr", "zh"};

    @Test
    void everyConfigLanguageHasTheClientBrandKeys() throws Exception {
        assertSameClientBrandKeys("config", CONFIG_LANGUAGES);
    }

    @Test
    void everyMessagesLanguageHasTheClientBrandKeys() throws Exception {
        assertSameClientBrandKeys("messages", MESSAGES_LANGUAGES);
    }

    private static void assertSameClientBrandKeys(String folder, String[] languages) throws Exception {
        Set<String> english = clientBrandKeys(folder, "en");
        assertFalse(english.isEmpty(), folder + "/en.yml has client-brand keys");
        for (String lang : languages) {
            assertEquals(english, clientBrandKeys(folder, lang), folder + "/" + lang + ".yml");
        }
    }

    private static Set<String> clientBrandKeys(String folder, String lang) throws Exception {
        Map<?, ?> root = new Yaml().load(resource("/" + folder + "/" + lang + ".yml"));
        Object section = root.get("client-brand");
        assertNotNull(section, folder + "/" + lang + ".yml has client-brand");
        Set<String> keys = new TreeSet<>();
        collectKeys((Map<?, ?>) section, "", keys);
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
