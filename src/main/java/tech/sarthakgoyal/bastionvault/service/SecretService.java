package tech.sarthakgoyal.bastionvault.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.crypto.KeyManager;
import tech.sarthakgoyal.bastionvault.crypto.SecretCipher;
import tech.sarthakgoyal.bastionvault.domain.Secret;
import tech.sarthakgoyal.bastionvault.repository.SecretRepository;

/** Encrypted secret storage. Every method that touches a value requires the vault to be unsealed. */
@Service
public class SecretService {

    private static final String GENERATE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretRepository repository;
    private final KeyManager keys;

    public SecretService(SecretRepository repository, KeyManager keys) {
        this.repository = repository;
        this.keys = keys;
    }

    /** @return true when the secret was created, false when an existing value was replaced */
    @Transactional
    public boolean put(String group, String key, String value, String actor) {
        Names.group(group);
        Names.key(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Value must not be empty");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > Names.MAX_VALUE_BYTES) {
            throw new IllegalArgumentException("Value is larger than 64 KiB");
        }
        byte[] blob = SecretCipher.encrypt(keys.dek(), group, key, value);
        Optional<Secret> existing = repository.findByGroupNameAndSecretKey(group, key);
        Secret secret = existing.orElseGet(() -> new Secret(group, key));
        secret.updateValue(blob, SecretCipher.ENC_VERSION_GCM, actor);
        repository.save(secret);
        return existing.isEmpty();
    }

    public Optional<String> get(String group, String key) {
        return repository.findByGroupNameAndSecretKey(group, key)
                .map(s -> SecretCipher.decrypt(keys.dek(), s.getGroupName(), s.getSecretKey(), s.getValueBlob()));
    }

    /** All values of one group, ordered by key. Empty map when the group does not exist. */
    public Map<String, String> getGroup(String group) {
        var dek = keys.dek();
        Map<String, String> values = new LinkedHashMap<>();
        for (Secret s : repository.findByGroupNameOrderBySecretKeyAsc(group)) {
            values.put(s.getSecretKey(), SecretCipher.decrypt(dek, s.getGroupName(), s.getSecretKey(), s.getValueBlob()));
        }
        return values;
    }

    /**
     * Creates a random value if the secret does not exist yet (idempotent).
     * @return the current value (new or existing)
     */
    @Transactional
    public Generated generateIfAbsent(String group, String key, int length, String actor) {
        if (length < 16 || length > 256) {
            throw new IllegalArgumentException("Length must be between 16 and 256");
        }
        Optional<String> existing = get(group, key);
        if (existing.isPresent()) {
            return new Generated(existing.get(), false);
        }
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(GENERATE_ALPHABET.charAt(RANDOM.nextInt(GENERATE_ALPHABET.length())));
        }
        put(group, key, value.toString(), actor);
        return new Generated(value.toString(), true);
    }

    public record Generated(String value, boolean created) {
    }

    @Transactional
    public boolean delete(String group, String key) {
        Optional<Secret> secret = repository.findByGroupNameAndSecretKey(group, key);
        secret.ifPresent(repository::delete);
        return secret.isPresent();
    }

    /** Metadata only; never decrypts. Safe to call while sealed. */
    public List<Secret> listAll() {
        return repository.findAllByOrderByGroupNameAscSecretKeyAsc();
    }
}
