package tech.sarthakgoyal.bastionvault.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses .env files with the same rules Docker Compose uses, and reads back everything
 * {@link EnvFormatter} writes.
 *
 * <ul>
 * <li>Blank lines and lines starting with {@code #} are ignored. An {@code export } prefix is allowed.</li>
 * <li>{@code KEY='value'}: literal, may span lines, no escapes.</li>
 * <li>{@code KEY="value"}: may span lines; {@code \n \r \t \\ \" \$} are unescaped.</li>
 * <li>{@code KEY=value}: trimmed; a {@code #} preceded by whitespace starts a comment.</li>
 * <li>A key that appears twice keeps the last value (with a warning). Empty values are skipped.</li>
 * </ul>
 * Problems never abort parsing; they are returned as warnings with line numbers.
 */
public final class EnvParser {

    public static final int MAX_BYTES = 256 * 1024;
    public static final int MAX_ENTRIES = 1000;

    public record Result(Map<String, String> entries, List<String> warnings) {
    }

    private EnvParser() {
    }

    public static Result parse(byte[] content) {
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("File is larger than 256 KiB");
        }
        return parse(new String(content, StandardCharsets.UTF_8));
    }

    public static Result parse(String text) {
        if (text.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Input is larger than 256 KiB");
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        if (s.startsWith("﻿")) {
            s = s.substring(1);
        }
        Map<String, String> entries = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int i = 0;
        int line = 1;
        while (i < s.length()) {
            int lineStart = line;
            int eol = indexOrEnd(s, '\n', i);
            String raw = s.substring(i, eol);
            String trimmed = raw.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i = eol + 1;
                line++;
                continue;
            }
            int eq = raw.indexOf('=');
            if (eq < 0) {
                warnings.add("line " + lineStart + ": no '=' found, ignored");
                i = eol + 1;
                line++;
                continue;
            }
            String key = raw.substring(0, eq).strip();
            if (key.startsWith("export ") || key.startsWith("export\t")) {
                key = key.substring(7).strip();
            }
            int v = i + eq + 1;
            while (v < s.length() && (s.charAt(v) == ' ' || s.charAt(v) == '\t')) {
                v++;
            }
            String value;
            if (v < s.length() && (s.charAt(v) == '\'' || s.charAt(v) == '"')) {
                char quote = s.charAt(v);
                StringBuilder out = new StringBuilder();
                int j = v + 1;
                boolean closed = false;
                while (j < s.length()) {
                    char c = s.charAt(j);
                    if (c == '\n') {
                        line++;
                    }
                    if (quote == '"' && c == '\\' && j + 1 < s.length()) {
                        char n = s.charAt(j + 1);
                        switch (n) {
                            case 'n' -> out.append('\n');
                            case 'r' -> out.append('\r');
                            case 't' -> out.append('\t');
                            case '\\', '"', '$' -> out.append(n);
                            default -> out.append(c).append(n);
                        }
                        j += 2;
                        continue;
                    }
                    if (c == quote) {
                        closed = true;
                        break;
                    }
                    out.append(c);
                    j++;
                }
                if (!closed) {
                    warnings.add("line " + lineStart + ": unterminated " + quote + " quote for " + key
                            + "; rest of file ignored");
                    break;
                }
                value = out.toString();
                int after = indexOrEnd(s, '\n', j + 1);
                String rest = s.substring(j + 1, after).strip();
                if (!rest.isEmpty() && !rest.startsWith("#")) {
                    warnings.add("line " + line + ": text after the closing quote of " + key + " ignored");
                }
                i = after + 1;
                line++;
            } else {
                String rawValue = s.substring(v, eol);
                int comment = inlineComment(rawValue);
                value = (comment >= 0 ? rawValue.substring(0, comment) : rawValue).strip();
                i = eol + 1;
                line++;
            }
            if (key.isEmpty()) {
                warnings.add("line " + lineStart + ": empty key, ignored");
                continue;
            }
            if (value.isEmpty()) {
                warnings.add("line " + lineStart + ": " + key + " has an empty value, skipped");
                continue;
            }
            if (entries.containsKey(key)) {
                warnings.add("line " + lineStart + ": " + key + " defined again, last value wins");
            }
            entries.put(key, value);
            if (entries.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("More than " + MAX_ENTRIES + " entries");
            }
        }
        return new Result(entries, warnings);
    }

    private static int indexOrEnd(String s, char c, int from) {
        int idx = s.indexOf(c, from);
        return idx < 0 ? s.length() : idx;
    }

    /** Position of a '#' that starts an inline comment (preceded by whitespace), or -1. */
    private static int inlineComment(String value) {
        for (int k = 1; k < value.length(); k++) {
            if (value.charAt(k) == '#' && Character.isWhitespace(value.charAt(k - 1))) {
                return k;
            }
        }
        return -1;
    }
}
