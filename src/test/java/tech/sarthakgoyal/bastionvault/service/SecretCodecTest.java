package tech.sarthakgoyal.bastionvault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class SecretCodecTest {

    private final SecretCodec codec = new SecretCodec(JsonMapper.builder().build());

    @Test
    void yamlKeepsValuesExactlyAsWritten() {
        String yaml = """
                ENABLED: yes
                ZIP: 0123
                PORT: 5432
                RATIO: 1e3
                LITERAL_NULL: "null"
                EMPTY: ~
                URL: postgres://u:p@h:5432/db?ssl=true
                PEM: |
                  -----BEGIN KEY-----
                  abc
                  -----END KEY-----
                NESTED:
                  A: 1
                LIST: [1, 2]
                """;
        SecretCodec.Parsed p = codec.readGroup(yaml, SecretFormat.YAML);
        assertThat(p.entries())
                .containsEntry("ENABLED", "yes")
                .containsEntry("ZIP", "0123")
                .containsEntry("PORT", "5432")
                .containsEntry("RATIO", "1e3")
                .containsEntry("LITERAL_NULL", "null")
                .containsEntry("URL", "postgres://u:p@h:5432/db?ssl=true")
                .containsEntry("PEM", "-----BEGIN KEY-----\nabc\n-----END KEY-----\n")
                .doesNotContainKeys("EMPTY", "NESTED", "LIST");
        assertThat(p.warnings()).hasSize(3);
    }

    @Test
    void jsonScalarsBecomeStringsWithWarnings() {
        SecretCodec.Parsed p = codec.readGroup("""
                {"A": "text", "PORT": 5432, "ON": true, "GONE": null, "OBJ": {"x": 1}, "ARR": [1]}
                """, SecretFormat.JSON);
        assertThat(p.entries()).containsEntry("A", "text").containsEntry("PORT", "5432").containsEntry("ON", "true")
                .hasSize(3);
        assertThat(p.warnings()).hasSize(5);
    }

    @Test
    void badInputIsRejectedClearly() {
        assertThatThrownBy(() -> codec.readGroup("{not json", SecretFormat.JSON))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Invalid JSON");
        assertThatThrownBy(() -> codec.readGroup("[1,2]", SecretFormat.JSON))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("object");
        assertThatThrownBy(() -> codec.readGroup("- a\n- b\n", SecretFormat.YAML))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mapping");
        assertThatThrownBy(() -> codec.readGroup("a: [unclosed", SecretFormat.YAML))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Invalid YAML");
        assertThatThrownBy(() -> codec.readGroup("   ", SecretFormat.YAML))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.writeVault(Map.of(), SecretFormat.ENV))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void yamlAliasBombIsRefused() {
        StringBuilder bomb = new StringBuilder("a: &a [x, x, x, x, x, x, x, x, x, x]\n");
        for (char c = 'b'; c <= 'z'; c++) {
            char prev = (char) (c - 1);
            bomb.append(c).append(": &").append(c).append(" [");
            for (int i = 0; i < 10; i++) {
                bomb.append(i == 0 ? "" : ", ").append('*').append(prev);
            }
            bomb.append("]\n");
        }
        assertThatThrownBy(() -> codec.readVault(bomb.toString(), SecretFormat.YAML))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiLineValuesAreWrittenAsYamlLiteralBlocks() {
        String yaml = codec.writeGroup(Map.of("PEM", "line1\nline2"), SecretFormat.YAML);
        assertThat(yaml).contains("PEM: |-\n  line1\n  line2");
    }

    @Test
    void vaultShape() {
        Map<String, Map<String, String>> vault = new LinkedHashMap<>();
        vault.put("affine", Map.of("DATABASE_URL", "postgres://x"));
        vault.put("memos", Map.of("MEMOS_DSN", "postgres://y"));
        for (SecretFormat f : new SecretFormat[] {SecretFormat.JSON, SecretFormat.YAML}) {
            SecretCodec.ParsedVault back = codec.readVault(codec.writeVault(vault, f), f);
            assertThat(back.groups()).isEqualTo(vault);
            assertThat(back.keyCount()).isEqualTo(2);
        }
        SecretCodec.ParsedVault mixed = codec.readVault("ok:\n  K: v\nbad: scalar\n", SecretFormat.YAML);
        assertThat(mixed.groups()).containsOnlyKeys("ok");
        assertThat(mixed.warnings()).anyMatch(w -> w.startsWith("bad:"));
    }

    /** Whatever we export, we import back identically, in every format. */
    @Test
    void randomRoundTrips() {
        Random random = new Random(7);
        String alphabet = "abcXYZ019 _-=#$'\"\\\n\r\t{}[]@:/?&%!*|>~,ü✓";
        for (int run = 0; run < 500; run++) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int k = 0; k < 5; k++) {
                StringBuilder v = new StringBuilder();
                int len = 1 + random.nextInt(40);
                for (int i = 0; i < len; i++) {
                    v.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                values.put("KEY_" + k, v.toString());
            }
            for (SecretFormat f : SecretFormat.values()) {
                String text = codec.writeGroup(values, f);
                assertThat(codec.readGroup(text, f).entries()).as(f + ":\n" + text).isEqualTo(values);
            }
            Map<String, Map<String, String>> vault = Map.of("g1", values);
            assertThat(codec.readVault(codec.writeVault(vault, SecretFormat.YAML), SecretFormat.YAML).groups())
                    .isEqualTo(vault);
        }
    }

    @Test
    void formatDetection() {
        assertThat(SecretFormat.fromFileName("x.YML")).isEqualTo(SecretFormat.YAML);
        assertThat(SecretFormat.fromFileName("app.json")).isEqualTo(SecretFormat.JSON);
        assertThat(SecretFormat.fromFileName(".env.production")).isEqualTo(SecretFormat.ENV);
        assertThat(SecretFormat.fromContentType("application/x-yaml")).isEqualTo(SecretFormat.YAML);
        assertThat(SecretFormat.fromContentType("application/json; charset=utf-8")).isEqualTo(SecretFormat.JSON);
        assertThat(SecretFormat.fromContentType(null)).isEqualTo(SecretFormat.ENV);
        assertThatThrownBy(() -> SecretFormat.of("xml")).isInstanceOf(IllegalArgumentException.class);
    }
}
