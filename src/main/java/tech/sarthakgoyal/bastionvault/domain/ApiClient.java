package tech.sarthakgoyal.bastionvault.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A machine identity (Ansible runner, External Secrets Operator, CI). */
@Entity
@Table(name = "api_clients")
public class ApiClient {

    @Id
    @Column(name = "client_id")
    private String clientId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiClient() {
    }

    public ApiClient(String clientId, String displayName, String secretHash) {
        this.clientId = clientId;
        this.displayName = displayName;
        this.secretHash = secretHash;
    }

    public String getClientId() { return clientId; }
    public String getDisplayName() { return displayName; }
    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
