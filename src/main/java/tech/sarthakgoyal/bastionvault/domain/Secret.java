package tech.sarthakgoyal.bastionvault.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One encrypted value, addressed by (group, key). */
@Entity
@Table(name = "secrets", uniqueConstraints = @UniqueConstraint(columnNames = {"app_group", "secret_key"}))
public class Secret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_group", nullable = false)
    private String groupName;

    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    /** AES-256-GCM: nonce || ciphertext || tag. */
    @Column(name = "value_blob", nullable = false)
    private byte[] valueBlob;

    @Column(name = "enc_version", nullable = false)
    private int encVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected Secret() {
    }

    public Secret(String groupName, String secretKey) {
        this.groupName = groupName;
        this.secretKey = secretKey;
    }

    public Long getId() { return id; }
    public String getGroupName() { return groupName; }
    public String getSecretKey() { return secretKey; }
    public byte[] getValueBlob() { return valueBlob; }
    public int getEncVersion() { return encVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    public void updateValue(byte[] valueBlob, int encVersion, String updatedBy) {
        this.valueBlob = valueBlob;
        this.encVersion = encVersion;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
