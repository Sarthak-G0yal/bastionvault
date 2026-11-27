package tech.sarthakgoyal.bastionvault.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import tech.sarthakgoyal.bastionvault.crypto.KeyManager;

/**
 * Stores every entry of a parsed .env file in one group. All entries are validated before anything
 * is written, so an invalid key never leaves the group half-updated. Not transactional on purpose:
 * each value commits on its own and the result reports exactly what happened.
 */
@Service
public class EnvImportService {

    private final SecretService secrets;
    private final KeyManager keys;

    public EnvImportService(SecretService secrets, KeyManager keys) {
        this.secrets = secrets;
        this.keys = keys;
    }

    public record Result(int created, int updated, int skippedExisting, List<String> warnings) {
        public int stored() {
            return created + updated;
        }
    }

    public Result importEntries(String group, Map<String, String> entries, boolean overwrite, String actor,
            List<String> parseWarnings) {
        Names.group(group);
        keys.dek(); // fail fast with 503 when sealed
        List<String> warnings = new ArrayList<>(parseWarnings);
        Set<String> existing = secrets.existingKeys(group);

        // validate everything first
        List<Map.Entry<String, String>> valid = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            try {
                Names.key(entry.getKey());
                if (entry.getValue().getBytes(StandardCharsets.UTF_8).length > Names.MAX_VALUE_BYTES) {
                    throw new IllegalArgumentException("value is larger than 64 KiB");
                }
                valid.add(entry);
            } catch (IllegalArgumentException e) {
                warnings.add(entry.getKey() + ": " + e.getMessage() + ", skipped");
            }
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (Map.Entry<String, String> entry : valid) {
            boolean exists = existing.contains(entry.getKey());
            if (exists && !overwrite) {
                skipped++;
                continue;
            }
            secrets.put(group, entry.getKey(), entry.getValue(), actor);
            if (exists) {
                updated++;
            } else {
                created++;
            }
        }
        return new Result(created, updated, skipped, warnings);
    }

    /** Parses and imports in one step (used by the API). */
    public Result importEnv(String group, String envText, boolean overwrite, String actor) {
        EnvParser.Result parsed = EnvParser.parse(envText == null ? "" : envText);
        if (parsed.entries().isEmpty()) {
            throw new IllegalArgumentException("No entries found" + (parsed.warnings().isEmpty() ? ""
                    : ": " + String.join("; ", parsed.warnings())));
        }
        return importEntries(group, parsed.entries(), overwrite, actor, parsed.warnings());
    }
}
