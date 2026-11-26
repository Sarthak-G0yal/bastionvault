package tech.sarthakgoyal.bastionvault.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Singleton row holding the key-derivation parameters and the wrapped data encryption key (DEK).
 * The plaintext DEK only ever exists in memory while the vault is unsealed.
 */
@Entity
@Table(name = "vault_config")
public class VaultConfig {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "crypto_version", nullable = false)
    private int cryptoVersion;

    @Column(name = "kdf_algorithm", nullable = false)
    private String kdfAlgorithm;

    @Column(name = "kdf_iterations", nullable = false)
    private int kdfIterations;

    @Column(name = "kdf_salt", nullable = false)
    private byte[] kdfSalt;

    @Column(name = "dek_by_password", nullable = false)
    private byte[] dekByPassword;

    @Column(name = "dek_by_recovery", nullable = false)
    private byte[] dekByRecovery;

    @Column(name = "dek_by_unseal_key")
    private byte[] dekByUnsealKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Short getId() { return id; }
    public int getCryptoVersion() { return cryptoVersion; }
    public void setCryptoVersion(int cryptoVersion) { this.cryptoVersion = cryptoVersion; }
    public String getKdfAlgorithm() { return kdfAlgorithm; }
    public void setKdfAlgorithm(String kdfAlgorithm) { this.kdfAlgorithm = kdfAlgorithm; }
    public int getKdfIterations() { return kdfIterations; }
    public void setKdfIterations(int kdfIterations) { this.kdfIterations = kdfIterations; }
    public byte[] getKdfSalt() { return kdfSalt; }
    public void setKdfSalt(byte[] kdfSalt) { this.kdfSalt = kdfSalt; }
    public byte[] getDekByPassword() { return dekByPassword; }
    public void setDekByPassword(byte[] dekByPassword) { this.dekByPassword = dekByPassword; }
    public byte[] getDekByRecovery() { return dekByRecovery; }
    public void setDekByRecovery(byte[] dekByRecovery) { this.dekByRecovery = dekByRecovery; }
    public byte[] getDekByUnsealKey() { return dekByUnsealKey; }
    public void setDekByUnsealKey(byte[] dekByUnsealKey) { this.dekByUnsealKey = dekByUnsealKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
}
