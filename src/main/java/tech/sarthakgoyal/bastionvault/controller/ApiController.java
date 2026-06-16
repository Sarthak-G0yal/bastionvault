package tech.sarthakgoyal.bastionvault.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;
import tech.sarthakgoyal.bastionvault.model.ApiClientDocument;
import tech.sarthakgoyal.bastionvault.model.AuditLogDocument;
import tech.sarthakgoyal.bastionvault.model.SecretDocument;
import tech.sarthakgoyal.bastionvault.repository.ApiClientRepository;
import tech.sarthakgoyal.bastionvault.repository.AuditLogRepository;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;

import java.util.List;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final ApiClientRepository clientRepository;
    private final SecretRepository secretRepository;
    private final AuditLogRepository auditLogRepository;
    private final VaultSessionManager sessionManager;
    private final PasswordEncoder passwordEncoder;

    public ApiController(ApiClientRepository clientRepository, SecretRepository secretRepository,
            AuditLogRepository auditLogRepository, VaultSessionManager sessionManager,
            PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.secretRepository = secretRepository;
        this.auditLogRepository = auditLogRepository;
        this.sessionManager = sessionManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${vault.token-ttl-minutes:15}")
    private int defaultTokenTtl;

    // DTOs for the JSON request/response
    public record AuthRequest(String clientId, String clientSecret, Integer expiresInMinutes) {
    }

    public record AuthResponse(String token, int expiresInMinutes) {
    }

    // 1. THE TOKEN EXCHANGE ENDPOINT
    @PostMapping("/auth/token")
    public ResponseEntity<?> authenticateMachine(@RequestBody AuthRequest request) {
        // Prevent cold-boot automated attacks: Vault must be unlocked by a human first
        if (!sessionManager.isUnlocked()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Vault is locked. Human administration required."));
        }

        Optional<ApiClientDocument> clientOpt = clientRepository.findById(request.clientId());

        if (clientOpt.isEmpty() || !clientOpt.get().isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or inactive Client ID"));
        }

        if (!passwordEncoder.matches(request.clientSecret(), clientOpt.get().getClientSecretHash())) {
            auditLogRepository.save(new AuditLogDocument(request.clientId(), "AUTH", "SYSTEM", "FAILED_BAD_SECRET"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid Client Secret"));
        }

        int ttl = (request.expiresInMinutes() != null) ? request.expiresInMinutes() : defaultTokenTtl;
        String token = sessionManager.generateTemporaryToken(request.clientId(), ttl);
        auditLogRepository.save(new AuditLogDocument(request.clientId(), "AUTH", "SYSTEM", "SUCCESS"));

        return ResponseEntity.ok(new AuthResponse(token, ttl));
    }

    // 2. THE BULK .ENV RETRIEVAL ENDPOINT
    @GetMapping(value = "/env/{applicationGroup}", produces = "text/plain")
    public ResponseEntity<String> getEnvironmentBundle(@PathVariable String applicationGroup) {
        String clientId = SecurityContextHolder.getContext().getAuthentication().getName();
        List<SecretDocument> secrets = secretRepository.findByApplicationGroup(applicationGroup);

        if (secrets.isEmpty()) {
            auditLogRepository.save(new AuditLogDocument(clientId, "READ_ENV", applicationGroup, "NOT_FOUND"));
            return ResponseEntity.notFound().build();
        }

        StringBuilder envBuilder = new StringBuilder();
        SecretKey activeKey = sessionManager.getActiveKey();

        for (SecretDocument secret : secrets) {
            String decryptedValue = decryptValue(secret.getCiphertext(), secret.getIv(), activeKey);
            envBuilder.append(secret.getKey()).append("=").append(decryptedValue).append("\n");
        }

        auditLogRepository.save(new AuditLogDocument(clientId, "READ_ENV", applicationGroup, "SUCCESS"));
        return ResponseEntity.ok(envBuilder.toString());
    }

    // Decryption Helper
    private String decryptValue(String ciphertext, String iv, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivSpec = new IvParameterSpec(Base64.getDecoder().decode(iv));
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decrypted);
        } catch (Exception e) {
            return "ERROR_DECRYPTING_VALUE";
        }
    }

    // 3. THE GRANULAR SINGLE SECRET ENDPOINT
    @GetMapping(value = "/secrets/{applicationGroup}/{key}", produces = "application/json")
    public ResponseEntity<?> getSingleSecret(@PathVariable String applicationGroup, @PathVariable String key) {
        // Identify the machine client making the request
        String clientId = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<SecretDocument> secretOpt = secretRepository.findByApplicationGroupAndKey(applicationGroup, key);

        if (secretOpt.isEmpty()) {
            auditLogRepository
                    .save(new AuditLogDocument(clientId, "READ_SECRET", applicationGroup + ":" + key, "NOT_FOUND"));
            return ResponseEntity.notFound().build();
        }

        SecretDocument secret = secretOpt.get();
        SecretKey activeKey = sessionManager.getActiveKey();

        // Decrypt the payload in memory
        String decryptedValue = decryptValue(secret.getCiphertext(), secret.getIv(), activeKey);

        auditLogRepository.save(new AuditLogDocument(clientId, "READ_SECRET", applicationGroup + ":" + key, "SUCCESS"));

        // Return as a clean JSON object for easy parsing in automation scripts
        return ResponseEntity.ok(Map.of(
                "key", secret.getKey(),
                "value", decryptedValue));
    }
}