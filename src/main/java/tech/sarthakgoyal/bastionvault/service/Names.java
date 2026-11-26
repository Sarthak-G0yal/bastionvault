package tech.sarthakgoyal.bastionvault.service;

import java.util.regex.Pattern;

/** Validation for user-supplied identifiers. Keeps URLs, .env output and policies unambiguous. */
public final class Names {

    private static final Pattern GROUP = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern GROUP_PATTERN = Pattern.compile("[A-Za-z0-9._*?-]{1,128}");
    private static final Pattern CLIENT_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{1,63}");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._@-]{3,64}");
    public static final int MAX_VALUE_BYTES = 64 * 1024;

    private Names() {
    }

    public static String group(String value) {
        return check(GROUP, value, "Group must match " + GROUP.pattern());
    }

    public static String key(String value) {
        return check(KEY, value, "Key must match " + KEY.pattern());
    }

    public static String groupPattern(String value) {
        return check(GROUP_PATTERN, value, "Pattern may contain letters, digits, . _ - and the wildcards * ?");
    }

    public static String clientId(String value) {
        return check(CLIENT_ID, value, "Client ID must be lowercase and match " + CLIENT_ID.pattern());
    }

    public static String username(String value) {
        return check(USERNAME, value, "Username must be 3-64 characters: letters, digits, . _ @ -");
    }

    public static void password(String value, String label) {
        if (value == null || value.length() < 12) {
            throw new IllegalArgumentException(label + " must be at least 12 characters");
        }
    }

    private static String check(Pattern pattern, String value, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
