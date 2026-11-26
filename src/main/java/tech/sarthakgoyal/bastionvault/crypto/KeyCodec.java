package tech.sarthakgoyal.bastionvault.crypto;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Text encodings for the keys shown to the operator. */
public final class KeyCodec {

    private static final String UNSEAL_PREFIX = "bvu_";

    private KeyCodec() {
    }

    /** Recovery key: 64 hex characters in 8 dash-separated groups, easy to write down. */
    public static String formatRecoveryKey(byte[] raw) {
        String hex = HexFormat.of().formatHex(raw);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 8) {
            if (i > 0) {
                out.append('-');
            }
            out.append(hex, i, i + 8);
        }
        return out.toString();
    }

    public static byte[] parseRecoveryKey(String text) {
        String hex = text.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
        if (hex.length() != Aead.KEY_BYTES * 2) {
            throw new IllegalArgumentException("Recovery key must contain 64 hex characters");
        }
        return HexFormat.of().parseHex(hex);
    }

    /** Auto-unseal key: machine-handled, stored in a Kubernetes Secret. */
    public static String formatUnsealKey(byte[] raw) {
        return UNSEAL_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public static byte[] parseUnsealKey(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith(UNSEAL_PREFIX)) {
            throw new IllegalArgumentException("Unseal key must start with " + UNSEAL_PREFIX);
        }
        byte[] raw = Base64.getUrlDecoder().decode(trimmed.substring(UNSEAL_PREFIX.length()));
        if (raw.length != Aead.KEY_BYTES) {
            throw new IllegalArgumentException("Unseal key has the wrong length");
        }
        return raw;
    }
}
