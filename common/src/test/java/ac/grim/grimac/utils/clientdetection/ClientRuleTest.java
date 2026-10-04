package ac.grim.grimac.utils.clientdetection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientRuleTest {

    private static Map<String, Object> rule(String regex, String type, String action, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (regex != null) map.put("regex", regex);
        if (type != null) map.put("type", type);
        if (action != null) map.put("action", action);
        if (message != null) map.put("message", message);
        return map;
    }

    @Test
    void parsesEntries() {
        ClientRule kick = ClientRule.parse(rule("(?i)^wurst", "brand", "KICK", "<red>No."));
        assertEquals(ClientRule.Type.BRAND, kick.type());
        assertEquals(ClientRule.Action.KICK, kick.action());
        assertEquals("<red>No.", kick.message());

        ClientRule alert = ClientRule.parse(rule("Voice", " Mod ", "alert", ""));
        assertEquals(ClientRule.Type.MOD, alert.type());
        assertEquals(ClientRule.Action.ALERT, alert.action());
        assertNull(alert.message());
    }

    @Test
    void rejectsInvalidEntries() {
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse("brand:x"));
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse(rule(null, "brand", "kick", null)));
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse(rule("[", "brand", "kick", null)));
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse(rule("x", "channel", "kick", null)));
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse(rule("x", "brand", "ban", null)));
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse(rule("x", null, "kick", null)));
        assertThrows(IllegalArgumentException.class, () -> ClientRule.parse(rule("x", "mod", null, null)));
    }

    @Test
    void parseAllSkipsInvalidEntries() {
        List<Object> errors = new ArrayList<>();
        List<ClientRule> rules = ClientRule.parseAll(List.of(
                rule("a", "brand", "alert", null),
                rule("b", "nope", "alert", null),
                "c"), (entry, reason) -> errors.add(entry));
        assertEquals(1, rules.size());
        assertEquals(2, errors.size());
    }

    @Test
    void brandRulesMatchTheBrandOnly() {
        ClientRule rule = ClientRule.parse(rule("(?i)^vanilla$", "brand", "alert", null));
        assertEquals("Vanilla", rule.match("Vanilla", List.of(), List.of()));
        assertNull(rule.match(null, List.of("vanilla"), List.of("vanilla")), "brand unknown yet");
        assertNull(rule.match("vanilla2", List.of(), List.of()));
    }

    @Test
    void modRulesMatchModNamesThenChannels() {
        ClientRule byName = ClientRule.parse(rule("Voice Chat", "mod", "kick", null));
        assertEquals("Simple Voice Chat", byName.match("fabric", List.of("Fabric API", "Simple Voice Chat"), List.of("voicechat:secret")));
        assertNull(byName.match("Voice Chat", List.of(), List.of()), "mod rules ignore the brand");

        ClientRule byChannel = ClientRule.parse(rule("^freecam:", "mod", "kick", null));
        assertEquals("freecam:sync", byChannel.match(null, List.of("Fabric API"), List.of("fabric:x", "freecam:sync")));
        assertNull(byChannel.match(null, List.of(), List.of()));
    }
}
