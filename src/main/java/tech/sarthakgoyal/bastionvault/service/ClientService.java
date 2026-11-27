package tech.sarthakgoyal.bastionvault.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.crypto.Aead;
import tech.sarthakgoyal.bastionvault.domain.ApiClient;
import tech.sarthakgoyal.bastionvault.domain.ApiKey;
import tech.sarthakgoyal.bastionvault.repository.ApiClientRepository;
import tech.sarthakgoyal.bastionvault.repository.ApiKeyRepository;

/** Machine identities, their secrets and their long-lived API keys (decision 3B). */
@Service
public class ClientService {

    public static final String API_KEY_PREFIX = "bvk_";
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(5);

    private final ApiClientRepository clients;
    private final ApiKeyRepository apiKeys;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public ClientService(ApiClientRepository clients, ApiKeyRepository apiKeys, PasswordEncoder passwordEncoder,
            TokenService tokens) {
        this.clients = clients;
        this.apiKeys = apiKeys;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    // ---------------------------------------------------------------- clients

    /** @return the raw client secret; it is not stored and cannot be shown again */
    @Transactional
    public String create(String clientId, String displayName) {
        Names.clientId(clientId);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (clients.existsById(clientId)) {
            throw new IllegalArgumentException("Client '" + clientId + "' already exists");
        }
        String secret = randomSecret();
        clients.save(new ApiClient(clientId, displayName.trim(), passwordEncoder.encode(secret)));
        return secret;
    }

    @Transactional
    public String rotateSecret(String clientId) {
        ApiClient client = require(clientId);
        String secret = randomSecret();
        client.setSecretHash(passwordEncoder.encode(secret));
        clients.save(client);
        tokens.revokeClient(clientId);
        return secret;
    }

    @Transactional
    public void setActive(String clientId, boolean active) {
        ApiClient client = require(clientId);
        client.setActive(active);
        clients.save(client);
        if (!active) {
            tokens.revokeClient(clientId);
        }
    }

    @Transactional
    public void delete(String clientId) {
        clients.deleteById(clientId); // policy rules and API keys cascade in the database
        tokens.revokeClient(clientId);
    }

    public List<ApiClient> list() {
        return clients.findAll();
    }

    /**
     * Verifies a client secret (BCrypt). Inactive clients never authenticate. Unknown and inactive
     * clients still cost one BCrypt check, so response time does not reveal which client IDs exist.
     */
    public boolean verifySecret(String clientId, String secret) {
        if (clientId == null || secret == null) {
            return false;
        }
        Optional<ApiClient> client = clients.findById(clientId).filter(ApiClient::isActive);
        String hash = client.map(ApiClient::getSecretHash).orElseGet(this::dummyHash);
        boolean matches;
        try {
            matches = passwordEncoder.matches(secret, hash);
        } catch (IllegalArgumentException e) { // BCrypt refuses passwords over 72 bytes
            return false;
        }
        return client.isPresent() && matches;
    }

    private volatile String dummyHash;

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode(randomSecret());
            dummyHash = hash;
        }
        return hash;
    }

    // ---------------------------------------------------------------- API keys

    /** @return the full key ({@code bvk_<id>_<secret>}); shown once */
    @Transactional
    public String createApiKey(String clientId, String name, Integer expiresInDays) {
        require(clientId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Key name is required");
        }
        Instant expiresAt = null;
        if (expiresInDays != null) {
            if (expiresInDays <= 0) {
                throw new IllegalArgumentException("Expiry must be a positive number of days");
            }
            expiresAt = Instant.now().plus(Duration.ofDays(expiresInDays));
        }
        String keyId = HexFormat.of().formatHex(Aead.randomBytes(6));
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(Aead.randomBytes(32));
        apiKeys.save(new ApiKey(clientId, keyId, sha256(secret), name.trim(), expiresAt));
        return API_KEY_PREFIX + keyId + "_" + secret;
    }

    @Transactional
    public void revokeApiKey(Long id) {
        apiKeys.findById(id).ifPresent(key -> {
            key.setRevoked(true);
            apiKeys.save(key);
        });
    }

    public List<ApiKey> apiKeys(String clientId) {
        return apiKeys.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    /** @return the owning client id when the key is valid, unrevoked, unexpired and the client is active */
    @Transactional
    public Optional<String> authenticateApiKey(String token) {
        if (token == null || !token.startsWith(API_KEY_PREFIX)) {
            return Optional.empty();
        }
        String body = token.substring(API_KEY_PREFIX.length());
        int separator = body.indexOf('_');
        if (separator <= 0) {
            return Optional.empty();
        }
        String keyId = body.substring(0, separator);
        String secret = body.substring(separator + 1);
        Instant now = Instant.now();
        Optional<ApiKey> match = apiKeys.findByKeyId(keyId)
                .filter(k -> k.isUsable(now))
                .filter(k -> MessageDigest.isEqual(k.getSecretHash(), sha256(secret)))
                .filter(k -> clients.findById(k.getClientId()).map(ApiClient::isActive).orElse(false));
        match.ifPresent(k -> {
            if (k.getLastUsedAt() == null || k.getLastUsedAt().isBefore(now.minus(LAST_USED_RESOLUTION))) {
                k.setLastUsedAt(now);
                apiKeys.save(k);
            }
        });
        return match.map(ApiKey::getClientId);
    }

    @Transactional
    public void touch(String clientId) {
        clients.findById(clientId).ifPresent(c -> {
            c.setLastUsedAt(Instant.now());
            clients.save(c);
        });
    }

    // ---------------------------------------------------------------- helpers

    private ApiClient require(String clientId) {
        return clients.findById(clientId).orElseThrow(() -> new NotFoundException("Unknown client " + clientId));
    }

    private static String randomSecret() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Aead.randomBytes(32));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
