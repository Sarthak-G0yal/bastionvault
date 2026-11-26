package tech.sarthakgoyal.bastionvault.service;

import java.util.regex.Pattern;

/** Minimal glob: {@code *} = any run of characters, {@code ?} = exactly one. Everything else literal. */
public final class GlobMatcher {

    private GlobMatcher() {
    }

    public static boolean matches(String glob, String value) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (char c : glob.toCharArray()) {
            if (c == '*' || c == '?') {
                if (!literal.isEmpty()) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return Pattern.matches(regex.toString(), value);
    }
}
