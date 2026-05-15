package com.example.enchantpurge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MessageFormatter}.
 * <p>
 * Tests MiniMessage deserialization, placeholder sanitization,
 * and tag injection prevention — all pure functions that require
 * no Bukkit/Paper runtime.
 */
@DisplayName("MessageFormatter")
class MessageFormatterTest {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // ── deserialize ───────────────────────────────────────────

    @Nested
    @DisplayName("deserialize()")
    class Deserialize {

        @Test
        @DisplayName("converts MiniMessage string to Component")
        void convertsMiniMessageToComponent() {
            Component result = MessageFormatter.deserialize("<green>Success!</green>");
            assertNotNull(result);

            String plainText = MINI_MESSAGE.serialize(result);
            assertTrue(plainText.contains("Success"));
        }

        @Test
        @DisplayName("handles plain text without tags")
        void handlesPlainText() {
            Component result = MessageFormatter.deserialize("Plain text");
            assertNotNull(result);
            String plain = MINI_MESSAGE.serialize(result);
            assertEquals("Plain text", plain);
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            Component result = MessageFormatter.deserialize("");
            assertNotNull(result);
        }
    }

    // ── sanitize ──────────────────────────────────────────────

    @Nested
    @DisplayName("sanitize()")
    class Sanitize {

        @Test
        @DisplayName("escapes < and > characters")
        void escapesAngleBrackets() {
            String result = MessageFormatter.sanitize("<red>malicious</red>");
            assertEquals("\\<red\\>malicious\\</red\\>", result);
        }

        @Test
        @DisplayName("escapes backslash before angle brackets")
        void escapesBackslashFirst() {
            String result = MessageFormatter.sanitize("a\\b<c>");
            assertEquals("a\\\\b\\<c\\>", result);
        }

        @Test
        @DisplayName("null returns empty string")
        void nullReturnsEmptyString() {
            assertEquals("", MessageFormatter.sanitize(null));
        }

        @Test
        @DisplayName("plain text unchanged")
        void plainTextUnchanged() {
            String result = MessageFormatter.sanitize("Sharpness V");
            assertEquals("Sharpness V", result);
        }

        @Test
        @DisplayName("prevents MiniMessage tag injection via placeholder value")
        void preventsTagInjection() {
            // A user-controlled value containing MiniMessage tags
            String userValue = "<rainbow>Hacked</rainbow>";
            String sanitized = MessageFormatter.sanitize(userValue);

            // Verify < and > are escaped
            assertFalse(sanitized.contains("<rainbow>"));
            assertTrue(sanitized.contains("\\<"));
            assertTrue(sanitized.contains("\\>"));
        }

        @Test
        @DisplayName("enchantment key names with colons are safe")
        void enchantmentKeyNamesSafe() {
            // Enchantment names might contain colons in some contexts
            String result = MessageFormatter.sanitize("minecraft:sharpness");
            assertEquals("minecraft:sharpness", result);
        }

        @Test
        @DisplayName("already escaped backslashes are handled correctly")
        void alreadyEscapedBackslashes() {
            String result = MessageFormatter.sanitize("\\\\<tag>");
            // First escape all backslashes: \\\\ → \\\\\\\\,
            // then escape < → \\<, > → \\>
            assertEquals("\\\\\\\\\\<tag\\>", result);
        }
    }

    // ── Placeholder replacement logic (via sanitize + template merging) ─

    @Nested
    @DisplayName("placeholder replacement behavior")
    class PlaceholderReplacement {

        @Test
        @DisplayName("sanitized value does not inject tags into MiniMessage")
        void sanitizedValueBlocksInjection() {
            // Simulate what happens during placeholder replacement
            String template = "<green>Removed: <enchant> Lv.<level></green>";
            String rawEnchantValue = "<bold>Curse of Binding</bold>";

            String safeValue = MessageFormatter.sanitize(rawEnchantValue);
            String result = template.replace("<enchant>", safeValue);

            // The result should NOT parse as an injection
            assertTrue(result.contains("\\<bold\\>"));
            assertFalse(result.contains("<bold>"));

            // Verify it deserializes without throwing (no unescaped tags)
            Component component = MessageFormatter.deserialize(
                    result.replace("<level>", "3"));
            assertNotNull(component);
        }

        @Test
        @DisplayName("multiple placeholders are sanitized independently")
        void multiplePlaceholdersSanitized() {
            String template = "<gold><enchant> - <player></gold>";

            String enchantValue = MessageFormatter.sanitize("<red>Sharpness</red>");
            String playerValue = MessageFormatter.sanitize("<blue>Steve</blue>");

            String result = template
                    .replace("<enchant>", enchantValue)
                    .replace("<player>", playerValue);

            assertTrue(result.contains("\\<red\\>"));
            assertTrue(result.contains("\\<blue\\>"));
            assertFalse(result.contains("<red>"));
            assertFalse(result.contains("<blue>"));
        }
    }

    // ── prefix + message combining ────────────────────────────

    @Nested
    @DisplayName("prefix and message combining")
    class PrefixMessageCombining {

        @Test
        @DisplayName("deserialize works for typical prefix format")
        void deserializePrefix() {
            String prefix = "<gray>[<gold>EnchantPurge</gold>]</gray> ";
            Component result = MessageFormatter.deserialize(prefix);
            assertNotNull(result);
            String serialized = MINI_MESSAGE.serialize(result);
            assertTrue(serialized.contains("EnchantPurge"));
        }

        @Test
        @DisplayName("deserialize works for typical message format")
        void deserializeMessage() {
            String message = "<green>Sharpness Lv.5 removed!</green>";
            Component result = MessageFormatter.deserialize(message);
            assertNotNull(result);
        }

        @Test
        @DisplayName("prefix and message can be combined as separate Components")
        void prefixAndMessageCombined() {
            Component prefix = MessageFormatter.deserialize("<gray>[Prefix]</gray> ");
            Component message = MessageFormatter.deserialize("<green>Message</green>");

            Component combined = Component.empty().append(prefix).append(message);
            String serialized = MINI_MESSAGE.serialize(combined);
            assertTrue(serialized.contains("Prefix"));
            assertTrue(serialized.contains("Message"));
        }
    }
}
