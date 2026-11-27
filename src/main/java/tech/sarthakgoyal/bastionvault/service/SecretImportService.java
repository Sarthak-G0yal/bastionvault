package tech.sarthakgoyal.bastionvault.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import tech.sarthakgoyal.bastionvault.crypto.KeyManager;

/**
 * Imports parsed entries into one group, or a whole vault file into many groups.
 *
 * <p>Everything is validated before anything is written, so an invalid key or group never leaves a
 * half-applied import. Not transactional on purpose: each value commits on its own and the result
 * reports exactly what happened. Existing keys are kept unless {@code overwrite} is set.
 */
@Service
public class SecretImportService {

    private final SecretService secrets;
    private final SecretCodec codec;
    private final KeyManager keys;

    public SecretImportService(SecretService secrets, SecretCodec codec, KeyManager keys) {
        this.secrets = secrets;
        this.codec = codec;
        this.keys = keys;
    }

    public record Result(int created, int updated, int skippedExisting, List<String> warnings) {
        public int stored() {
            return created + updated;
        }
    }

    public record VaultResult(Map<String, Result> groups, List<String> warnings) {
        public int created() { return groups.values().stream().mapToInt(Result::created).sum(); }
        public int updated() { return groups.values().stream().mapToInt(Result::updated).sum(); }
        public int skippedExisting() { return groups.values().stream().mapToInt(Result::skippedExisting).sum(); }
    }

    // ---------------------------------------------------------------- one group

    public Result importEntries(String group, Map<String, String> entries, boolean overwrite, String actor,
            List<String> parseWarnings) {
        Names.group(group);
        keys.dek(); // fail fast with 503 when sealed
        List<String> warnings = new ArrayList<>(parseWarnings);
        Map<String, String> valid = validEntries(group, entries, warnings, "");
        return write(group, valid, overwrite, actor, warnings);
    }

    /** Parses text in the given format and imports it into one group. */
    public Result importGroup(String group, String text, SecretFormat format, boolean overwrite, String actor) {
        SecretCodec.Parsed parsed = codec.readGroup(text, format);
        if (parsed.entries().isEmpty()) {
            throw new IllegalArgumentException("No entries found" + (parsed.warnings().isEmpty() ? ""
                    : ": " + String.join("; ", parsed.warnings())));
        }
        return importEntries(group, parsed.entries(), overwrite, actor, parsed.warnings());
    }

    // ---------------------------------------------------------------- whole vault

    /** Imports {@code {group: {KEY: value}}}. Invalid group names are reported and skipped. */
    public VaultResult importVault(SecretCodec.ParsedVault vault, boolean overwrite, String actor) {
        keys.dek();
        if (vault.groups().isEmpty()) {
            throw new IllegalArgumentException("No groups found" + (vault.warnings().isEmpty() ? ""
                    : ": " + String.join("; ", vault.warnings())));
        }
        List<String> warnings = new ArrayList<>(vault.warnings());
        Map<String, Map<String, String>> plan = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> group : vault.groups().entrySet()) {
            try {
                Names.group(group.getKey());
            } catch (IllegalArgumentException e) {
                warnings.add(group.getKey() + ": invalid group name, skipped");
                continue;
            }
            plan.put(group.getKey(), validEntries(group.getKey(), group.getValue(), warnings, group.getKey() + "/"));
        }
        Map<String, Result> results = new LinkedHashMap<>();
        plan.forEach((group, entries) -> results.put(group, write(group, entries, overwrite, actor, List.of())));
        return new VaultResult(results, warnings);
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, String> validEntries(String group, Map<String, String> entries, List<String> warnings,
            String prefix) {
        Map<String, String> valid = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            try {
                Names.key(entry.getKey());
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    throw new IllegalArgumentException("empty value");
                }
                if (entry.getValue().getBytes(StandardCharsets.UTF_8).length > Names.MAX_VALUE_BYTES) {
                    throw new IllegalArgumentException("value is larger than 64 KiB");
                }
                valid.put(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException e) {
                warnings.add(prefix + entry.getKey() + ": " + e.getMessage() + ", skipped");
            }
        }
        return valid;
    }

    private Result write(String group, Map<String, String> entries, boolean overwrite, String actor,
            List<String> warnings) {
        Set<String> existing = secrets.existingKeys(group);
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
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
}
