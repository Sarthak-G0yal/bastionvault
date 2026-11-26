package tech.sarthakgoyal.bastionvault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Grants one client read and/or write access to the groups matching {@code groupPattern}.
 * Patterns are globs: {@code *} matches any run of characters, {@code ?} one character.
 */
@Entity
@Table(name = "policy_rules")
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "group_pattern", nullable = false)
    private String groupPattern;

    @Column(name = "can_read", nullable = false)
    private boolean canRead;

    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    protected PolicyRule() {
    }

    public PolicyRule(String clientId, String groupPattern, boolean canRead, boolean canWrite) {
        this.clientId = clientId;
        this.groupPattern = groupPattern;
        this.canRead = canRead;
        this.canWrite = canWrite;
    }

    public Long getId() { return id; }
    public String getClientId() { return clientId; }
    public String getGroupPattern() { return groupPattern; }
    public boolean isCanRead() { return canRead; }
    public boolean isCanWrite() { return canWrite; }
}
