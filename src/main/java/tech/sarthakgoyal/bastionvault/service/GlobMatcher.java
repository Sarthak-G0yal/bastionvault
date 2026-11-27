package tech.sarthakgoyal.bastionvault.service;

/**
 * Minimal glob: {@code *} = any run of characters, {@code ?} = exactly one. Everything else literal.
 *
 * <p>Matched with the classic two-pointer algorithm instead of a regular expression, so a pattern
 * with many wildcards cannot cause catastrophic backtracking: the cost is at most
 * O(pattern length × value length).
 */
public final class GlobMatcher {

    private GlobMatcher() {
    }

    public static boolean matches(String glob, String value) {
        int p = 0;
        int v = 0;
        int starP = -1;
        int starV = -1;
        while (v < value.length()) {
            char g = p < glob.length() ? glob.charAt(p) : 0;
            if (p < glob.length() && g != '*' && (g == '?' || g == value.charAt(v))) {
                p++;
                v++;
            } else if (p < glob.length() && g == '*') {
                starP = p++;
                starV = v;
            } else if (starP >= 0) {
                p = starP + 1;
                v = ++starV;
            } else {
                return false;
            }
        }
        while (p < glob.length() && glob.charAt(p) == '*') {
            p++;
        }
        return p == glob.length();
    }
}
