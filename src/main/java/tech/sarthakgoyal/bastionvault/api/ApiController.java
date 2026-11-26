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
import org.springframework.web.bind.annotation.RestController;

import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.crypto.VaultSealedException;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.service.AttemptLimiter;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.ClientIp;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.EnvFormatter;
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

    public ApiController(TokenService tokens, ClientService clients, SecretService secrets, PolicyService policies,
            AuditService audit, AttemptLimiter limiter, KeyManager keys) {
        this.tokens = tokens;
        this.clients = clients;
        this.secrets = secrets;
        this.policies = policies;
        this.audit = audit;
        this.limiter = limiter;
        this.keys = keys;
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
        limiter.assertAllowed(clientKey, ipKey);
        if (!clients.verifySecret(request.clientId(), request.clientSecret())) {
            limiter.recordFailure(clientKey, ipKey);
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
        try {
            policies.require(client, group, capability);
        } catch (PolicyDeniedException e) {
            audit.client(client, action, key == null ? group : group + "/" + key, Outcome.DENIED);
            throw e;
        }
    }
}
