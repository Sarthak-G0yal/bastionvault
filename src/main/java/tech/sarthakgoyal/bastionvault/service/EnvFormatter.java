package tech.sarthakgoyal.bastionvault.service;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renders a group as a .env file that Docker Compose and shells parse back to the exact value
 * (decision 7A).
 *
 * <ul>
 * <li>Values without a single quote or newline: {@code KEY='value'} (literal, no interpolation).</li>
 * <li>Otherwise: {@code KEY="value"} with {@code \\ \" \$ \n \r} escaped.</li>
 * <li>Keys that are not valid variable names (e.g. {@code private.key}) are skipped and listed in a
 * comment; fetch those through the JSON endpoints.</li>
 * </ul>
 */
public final class EnvFormatter {

    private static final Pattern ENV_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private EnvFormatter() {
    }

    public static String format(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        StringBuilder skipped = new StringBuilder();
        values.forEach((key, value) -> {
            if (!ENV_NAME.matcher(key).matches()) {
                skipped.append(skipped.isEmpty() ? "" : ", ").append(key);
                return;
            }
            out.append(key).append('=').append(quote(value)).append('\n');
        });
        if (!skipped.isEmpty()) {
            out.append("# skipped (not valid variable names): ").append(skipped).append('\n');
        }
        return out.toString();
    }

    static String quote(String value) {
        if (value.indexOf('\'') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return "'" + value + "'";
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '$' -> escaped.append("\\$");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                default -> escaped.append(c);
            }
        }
        return escaped.append('"').toString();
    }
}
