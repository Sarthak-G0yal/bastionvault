package tech.sarthakgoyal.bastionvault.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A long-lived, revocable credential for a client. Inherits the client's policy rules. */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    /** Public part of the token, used for lookup. */
    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    /** SHA-256 of the secret part. */
    @Column(name = "secret_hash", nullable = false)
    private byte[] secretHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean revoked = false;

    protected ApiKey() {
    }

    public ApiKey(String clientId, String keyId, byte[] secretHash, String name, Instant expiresAt) {
        this.clientId = clientId;
        this.keyId = keyId;
        this.secretHash = secretHash;
        this.name = name;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getClientId() { return clientId; }
    public String getKeyId() { return keyId; }
    public byte[] getSecretHash() { return secretHash; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public boolean isUsable(Instant now) {
        return !revoked && (expiresAt == null || now.isBefore(expiresAt));
    }
}
