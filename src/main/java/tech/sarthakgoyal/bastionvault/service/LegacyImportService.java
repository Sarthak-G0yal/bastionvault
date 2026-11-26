package tech.sarthakgoyal.bastionvault.service;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import tech.sarthakgoyal.bastionvault.crypto.LegacyCbc;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;

/**
 * Imports secrets from a pre-0.2 MongoDB deployment. Input is the {@code secrets} collection from
 * {@code mongoexport} (JSON array or one document per line). Values are decrypted in memory with the
 * old master password + VAULT_SALT and re-encrypted with the new data key (AES-GCM).
 */
@Service
public class LegacyImportService {

    private final SecretService secrets;
    private final AuditService audit;
    private final ObjectMapper json;

    public LegacyImportService(SecretService secrets, AuditService audit, ObjectMapper json) {
        this.secrets = secrets;
        this.audit = audit;
        this.json = json;
    }

    public record Result(int imported, int skippedExisting, List<String> errors) {
    }

    /** Not transactional on purpose: each value commits on its own, so one bad row can't roll back the rest. */
    public Result importMongoExport(String export, String legacyMasterPassword, String legacySalt,
            boolean overwrite, String admin) {
        if (export == null || export.isBlank()) {
            throw new IllegalArgumentException("Paste the mongoexport output first");
        }
        SecretKey legacyKey = LegacyCbc.deriveKey(legacyMasterPassword.toCharArray(), legacySalt);
        List<JsonNode> documents = parse(export.trim());
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (JsonNode doc : documents) {
            String key = text(doc, "key");
            String group = text(doc, "applicationGroup");
            if (group == null || group.isBlank()) {
                group = "Ungrouped";
            }
            String label = group + "/" + key;
            try {
                if (!overwrite && secrets.get(group, key).isPresent()) {
                    skipped++;
                    continue;
                }
                String value = LegacyCbc.decrypt(legacyKey, text(doc, "iv"), text(doc, "ciphertext"));
                secrets.put(group, key, value, admin);
                imported++;
            } catch (RuntimeException e) {
                errors.add(label + ": " + e.getMessage());
            }
        }
        audit.record(tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType.ADMIN, admin, "LEGACY_IMPORT",
                null, errors.isEmpty() ? Outcome.SUCCESS : Outcome.FAILED,
                "imported=" + imported + " skipped=" + skipped + " errors=" + errors.size());
        return new Result(imported, skipped, errors);
    }

    private List<JsonNode> parse(String export) {
        List<JsonNode> documents = new ArrayList<>();
        if (export.startsWith("[")) {
            json.readTree(export).forEach(documents::add);
        } else {
            for (String line : export.split("\\R")) {
                if (!line.isBlank()) {
                    documents.add(json.readTree(line));
                }
            }
        }
        return documents;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
