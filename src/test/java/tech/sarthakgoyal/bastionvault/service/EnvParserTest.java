package tech.sarthakgoyal.bastionvault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class EnvParserTest {

    @Test
    void parsesTheCommonForms() {
        String env = """
                # comment
                PLAIN=value
                SPACED = padded value   # trailing comment
                HASH_IN_VALUE=abc#def
                export EXPORTED=yes
                SINGLE='literal $HOME \\n # not a comment'
                DOUBLE="tab\\tnew\\nline \\"q\\" \\$HOME \\\\"
                URL=postgres://u:p@host:5432/db?sslmode=disable
                """;
        Map<String, String> e = EnvParser.parse(env).entries();
        assertThat(e).containsEntry("PLAIN", "value")
                .containsEntry("SPACED", "padded value")
                .containsEntry("HASH_IN_VALUE", "abc#def")
                .containsEntry("EXPORTED", "yes")
                .containsEntry("SINGLE", "literal $HOME \\n # not a comment")
                .containsEntry("DOUBLE", "tab\tnew\nline \"q\" $HOME \\")
                .containsEntry("URL", "postgres://u:p@host:5432/db?sslmode=disable");
        assertThat(e).hasSize(7);
    }

    @Test
    void multiLineQuotedValuesAndCrlf() {
        String env = "KEY=\"-----BEGIN EC PRIVATE KEY-----\r\nabc\r\n-----END EC PRIVATE KEY-----\"\r\nNEXT=1\r\n";
        Map<String, String> e = EnvParser.parse(env).entries();
        assertThat(e.get("KEY")).isEqualTo("-----BEGIN EC PRIVATE KEY-----\nabc\n-----END EC PRIVATE KEY-----");
        assertThat(e.get("NEXT")).isEqualTo("1");
        assertThat(EnvParser.parse("A='x\ny'\nB=2").entries()).containsEntry("A", "x\ny").containsEntry("B", "2");
    }

    @Test
    void problemsBecomeWarningsNotFailures() {
        EnvParser.Result r = EnvParser.parse("""
                NOEQUALS
                EMPTY=
                DUP=1
                DUP=2
                =novalue
                OK=fine
                """);
        assertThat(r.entries()).containsEntry("DUP", "2").containsEntry("OK", "fine").hasSize(2);
        assertThat(r.warnings()).hasSize(4)
                .anyMatch(w -> w.contains("line 1") && w.contains("no '='"))
                .anyMatch(w -> w.contains("EMPTY") && w.contains("empty value"))
                .anyMatch(w -> w.contains("DUP") && w.contains("last value wins"))
                .anyMatch(w -> w.contains("empty key"));
    }

    @Test
    void unterminatedQuoteStopsParsing() {
        EnvParser.Result r = EnvParser.parse("A=1\nB=\"never closed\nC=3\n");
        assertThat(r.entries()).containsOnlyKeys("A");
        assertThat(r.warnings()).anyMatch(w -> w.contains("unterminated"));
    }

    @Test
    void byteOrderMarkAndSizeLimit() {
        assertThat(EnvParser.parse("\uFEFFA=1").entries()).containsEntry("A", "1");
        assertThatThrownBy(() -> EnvParser.parse(new byte[EnvParser.MAX_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Whatever EnvFormatter writes, EnvParser must read back exactly. */
    @Test
    void roundTripWithEnvFormatter() {
        Random random = new Random(42);
        String alphabet = "abcXYZ019 _-=#$'\"\\\n\r\t{}@:/?&%!";
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
            String env = EnvFormatter.format(values);
            assertThat(EnvParser.parse(env).entries()).as(env).isEqualTo(values);
        }
    }
}
