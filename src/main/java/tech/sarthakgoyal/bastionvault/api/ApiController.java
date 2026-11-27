package tech.sarthakgoyal.bastionvault.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.crypto.VaultSealedException;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.service.AttemptLimiter;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.ClientIp;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.EnvFormatter;
import tech.sarthakgoyal.bastionvault.service.Names;
import tech.sarthakgoyal.bastionvault.service.SecretCodec;
import tech.sarthakgoyal.bastionvault.service.SecretFormat;
import tech.sarthakgoyal.bastionvault.service.SecretImportService;
import tech.sarthakgoyal.bastionvault.service.NotFoundException;
import tech.sarthakgoyal.bastionvault.service.PolicyDeniedException;
import tech.sarthakgoyal.bastionvault.service.PolicyService;
import tech.sarthakgoyal.bastionvault.service.PolicyService.Capability;
import tech.sarthakgoyal.bastionvault.service.SecretService;
import tech.sarthakgoyal.bastionvault.service.TokenService;

/**
 * Machine API, v1. All secret endpoints require a Bearer credential and a matching policy rule.
 *
 * <pre>
 * POST   /api/v1/auth/token                      client id + secret -> short-lived token
 * GET    /api/v1/secrets/{group}/{key}           {"group","key","value"}          read
 * PUT    /api/v1/secrets/{group}/{key}           {"value": "..."}                 write
 * DELETE /api/v1/secrets/{group}/{key}                                            write
 * POST   /api/v1/secrets/{group}/{key}/generate  {"length": 32} create if absent  read+write
 * GET    /api/v1/groups/{group}                  {"KEY": "value", ...}            read
 * GET    /api/v1/env/{group}                     .env text                        read
 * PUT    /api/v1/env/{group}?overwrite=false     .env text body -> import         write
 * GET    /api/v1/export/{group}?format=json      one group as json | yaml | env   read
 * GET    /api/v1/export?format=json              every readable group (json|yaml) read (per group)
 * PUT    /api/v1/import/{group}?format=&overwrite=  json | yaml | env body       write
 * PUT    /api/v1/import?format=&overwrite=       {group: {KEY: value}} body      write (every group)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final TokenService tokens;
    private final ClientService clients;
    private final SecretService secrets;
    private final PolicyService policies;
    private final AuditService audit;
    private final AttemptLimiter limiter;
    private final KeyManager keys;
    private final SecretImportService importer;
    private final SecretCodec codec;

    public ApiController(TokenService tokens, ClientService clients, SecretService secrets, PolicyService policies,
            AuditService audit, AttemptLimiter limiter, KeyManager keys, SecretImportService importer, SecretCodec codec) {
        this.tokens = tokens;
        this.clients = clients;
        this.secrets = secrets;
        this.policies = policies;
        this.audit = audit;
        this.limiter = limiter;
        this.keys = keys;
        this.importer = importer;
        this.codec = codec;
    }

    public record TokenRequest(String clientId, String clientSecret, Integer expiresInMinutes) {
    }

    public record TokenResponse(String token, int expiresInMinutes, Instant expiresAt) {
    }

    public record SecretValue(String value) {
    }

    public record GenerateRequest(Integer length) {
    }

    // ---------------------------------------------------------------- auth

    @PostMapping("/auth/token")
    public ResponseEntity<?> token(@RequestBody TokenRequest request) {
        if (keys.isSealed()) {
            throw new VaultSealedException();
        }
        String clientKey = "client:" + request.clientId();
        String ipKey = AttemptLimiter.ip(ClientIp.current());
        limiter.begin(clientKey, ipKey);
        boolean ok = false;
        try {
            ok = clients.verifySecret(request.clientId(), request.clientSecret());
        } finally {
            limiter.end(ok, clientKey, ipKey);
        }
        if (!ok) {
            audit.client(String.valueOf(request.clientId()), "AUTH", null, Outcome.FAILED);
            return ApiErrors.error(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }
        limiter.reset(clientKey);
        TokenService.Issued issued = tokens.issue(request.clientId(), request.expiresInMinutes());
        clients.touch(request.clientId());
        audit.client(request.clientId(), "AUTH", null, Outcome.SUCCESS);
        return ResponseEntity.ok(new TokenResponse(issued.token(), issued.ttlMinutes(), issued.expiresAt()));
    }

    // ---------------------------------------------------------------- single secrets

    @GetMapping("/secrets/{group}/{key}")
    public Map<String, String> read(@PathVariable String group, @PathVariable String key, Authentication auth) {
        String client = auth.getName();
        authorize(client, "READ_SECRET", group, key, Capability.READ);
        String value = secrets.get(group, key).orElseThrow(() -> {
            audit.client(client, "READ_SECRET", group + "/" + key, Outcome.NOT_FOUND);
            return new NotFoundException("No secret " + group + "/" + key);
        });
        audit.client(client, "READ_SECRET", group + "/" + key, Outcome.SUCCESS);
        return Map.of("group", group, "key", key, "value", value);
    }

    @PutMapping("/secrets/{group}/{key}")
    public ResponseEntity<Void> write(@PathVariable String group, @PathVariable String key,
            @RequestBody SecretValue body, Authentication auth) {
        String client = auth.getName();
        authorize(client, "WRITE_SECRET", group, key, Capability.WRITE);
        boolean created = secrets.put(group, key, body == null ? null : body.value(), "client:" + client);
        audit.client(client, "WRITE_SECRET", group + "/" + key, Outcome.SUCCESS);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/secrets/{group}/{key}")
    public ResponseEntity<Void> delete(@PathVariable String group, @PathVariable String key, Authentication auth) {
        String client = auth.getName();
        authorize(client, "DELETE_SECRET", group, key, Capability.WRITE);
        if (!secrets.delete(group, key)) {
            audit.client(client, "DELETE_SECRET", group + "/" + key, Outcome.NOT_FOUND);
            throw new NotFoundException("No secret " + group + "/" + key);
        }
        audit.client(client, "DELETE_SECRET", group + "/" + key, Outcome.SUCCESS);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/secrets/{group}/{key}/generate")
    public ResponseEntity<Map<String, Object>> generate(@PathVariable String group, @PathVariable String key,
            @RequestBody(required = false) GenerateRequest body, Authentication auth) {
        String client = auth.getName();
        authorize(client, "GENERATE_SECRET", group, key, Capability.READ);
        authorize(client, "GENERATE_SECRET", group, key, Capability.WRITE);
        int length = body == null || body.length() == null ? 32 : body.length();
        SecretService.Generated result = secrets.generateIfAbsent(group, key, length, "client:" + client);
        audit.client(client, "GENERATE_SECRET", group + "/" + key, Outcome.SUCCESS);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("group", group, "key", key, "value", result.value(), "created", result.created()));
    }

    // ---------------------------------------------------------------- whole groups

    @GetMapping("/groups/{group}")
    public Map<String, String> group(@PathVariable String group, Authentication auth) {
        return readGroup(auth.getName(), group, "READ_GROUP");
    }

    @GetMapping(value = "/env/{group}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String env(@PathVariable String group, Authentication auth) {
        return EnvFormatter.format(readGroup(auth.getName(), group, "READ_ENV"));
    }

    // ---------------------------------------------------------------- import / export

    /** Imports a .env body into a group (kept for compatibility; same as /import/{group}?format=env). */
    @PutMapping("/env/{group}")
    public Map<String, Object> importEnv(@PathVariable String group,
            @RequestParam(defaultValue = "false") boolean overwrite, @RequestBody(required = false) byte[] body,
            Authentication auth) {
        return importGroup(group, SecretFormat.ENV.id(), overwrite, null, body, auth);
    }

    /** Imports one group. Format: ?format=, else from Content-Type (json/yaml), else .env. */
    @PutMapping("/import/{group}")
    public Map<String, Object> importGroup(@PathVariable String group,
            @RequestParam(required = false) String format, @RequestParam(defaultValue = "false") boolean overwrite,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Content-Type", required = false)
            String contentType, @RequestBody(required = false) byte[] body, Authentication auth) {
        String client = auth.getName();
        authorize(client, "IMPORT", group, null, Capability.WRITE);
        SecretFormat fmt = format != null ? SecretFormat.of(format) : SecretFormat.fromContentType(contentType);
        SecretImportService.Result result = importer.importGroup(group, text(body, SecretCodec.MAX_GROUP_BYTES), fmt,
                overwrite, "client:" + client);
        audit.record(tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType.CLIENT, client, "IMPORT", group,
                Outcome.SUCCESS, "format=" + fmt.id() + " created=" + result.created() + " updated=" + result.updated()
                        + " skipped=" + result.skippedExisting() + " warnings=" + result.warnings().size());
        return Map.of("group", group, "created", result.created(), "updated", result.updated(),
                "skippedExisting", result.skippedExisting(), "warnings", result.warnings());
    }

    /** Imports {group: {KEY: value}}. The client needs write access to every group in the file. */
    @PutMapping("/import")
    public Map<String, Object> importVault(@RequestParam(required = false) String format,
            @RequestParam(defaultValue = "false") boolean overwrite,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Content-Type", required = false)
            String contentType, @RequestBody(required = false) byte[] body, Authentication auth) {
        String client = auth.getName();
        SecretFormat fmt = format != null ? SecretFormat.of(format) : SecretFormat.fromContentType(contentType);
        SecretCodec.ParsedVault vault = codec.readVault(text(body, SecretCodec.MAX_VAULT_BYTES), fmt);
        java.util.List<String> denied = vault.groups().keySet().stream()
                .filter(g -> !policies.allows(client, g, Capability.WRITE)).toList();
        if (!denied.isEmpty()) {
            audit.client(client, "IMPORT_ALL", String.join(",", denied), Outcome.DENIED);
            throw new PolicyDeniedException("Client '" + client + "' has no write access to: " + String.join(", ", denied));
        }
        SecretImportService.VaultResult result = importer.importVault(vault, overwrite, "client:" + client);
        audit.record(tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType.CLIENT, client, "IMPORT_ALL", null,
                Outcome.SUCCESS, "format=" + fmt.id() + " groups=" + result.groups().size() + " created="
                        + result.created() + " updated=" + result.updated() + " skipped=" + result.skippedExisting());
        return Map.of("groups", result.groups().size(), "created", result.created(), "updated", result.updated(),
                "skippedExisting", result.skippedExisting(), "warnings", result.warnings());
    }

    @GetMapping("/export/{group}")
    public ResponseEntity<String> exportGroup(@PathVariable String group,
            @RequestParam(defaultValue = "json") String format, Authentication auth) {
        SecretFormat fmt = SecretFormat.of(format);
        Map<String, String> values = readGroup(auth.getName(), group, "EXPORT_" + fmt.name());
        return download(codec.writeGroup(values, fmt), group + fmt.extension(), fmt);
    }

    /** Every group the client can read, as {group: {KEY: value}}. */
    @GetMapping("/export")
    public ResponseEntity<String> exportVault(@RequestParam(defaultValue = "json") String format,
            Authentication auth) {
        String client = auth.getName();
        SecretFormat fmt = SecretFormat.of(format);
        Map<String, Map<String, String>> readable = new java.util.LinkedHashMap<>();
        secrets.exportAll().forEach((group, values) -> {
            if (policies.allows(client, group, Capability.READ)) {
                readable.put(group, values);
            }
        });
        String body = codec.writeVault(readable, fmt);
        audit.record(tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType.CLIENT, client, "EXPORT_ALL", null,
                Outcome.SUCCESS, "format=" + fmt.id() + " groups=" + readable.size());
        return download(body, "bastionvault-export" + fmt.extension(), fmt);
    }

    private static ResponseEntity<String> download(String body, String fileName, SecretFormat format) {
        return ResponseEntity.ok()
                .header("Content-Type", format.contentType() + ";charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .header("Cache-Control", "no-store")
                .body(body);
    }

    private static String text(byte[] body, int max) {
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("Request body must contain the file");
        }
        if (body.length > max) {
            throw new IllegalArgumentException("File is larger than " + (max / 1024) + " KiB");
        }
        return new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Map<String, String> readGroup(String client, String group, String action) {
        authorize(client, action, group, null, Capability.READ);
        Map<String, String> values = secrets.getGroup(group);
        if (values.isEmpty()) {
            audit.client(client, action, group, Outcome.NOT_FOUND);
            throw new NotFoundException("No secrets in group " + group);
        }
        audit.client(client, action, group, Outcome.SUCCESS);
        return values;
    }

    private void authorize(String client, String action, String group, String key, Capability capability) {
        // Reject malformed names (400) before they reach the policy matcher or the audit log.
        Names.group(group);
        if (key != null) {
            Names.key(key);
        }
        try {
            policies.require(client, group, capability);
        } catch (PolicyDeniedException e) {
            audit.client(client, action, key == null ? group : group + "/" + key, Outcome.DENIED);
            throw e;
        }
    }
}
