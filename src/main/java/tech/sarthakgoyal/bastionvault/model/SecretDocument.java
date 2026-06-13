package tech.sarthakgoyal.bastionvault.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "secrets")
public class SecretDocument {

    @Id
    private String key;     // The plain text lookup key
    private String ciphertext; // The encrypted value base64 encoded
    private String iv;       // The initialization vector used for encryption, base64 encoded
    private LocalDateTime lastUpdated;

    public SecretDocument() {
    }

    public SecretDocument(String key, String ciphertext, String iv, LocalDateTime lastUpdated) {
        this.key = key;
        this.ciphertext = ciphertext;
        this.iv = iv;
        this.lastUpdated = lastUpdated;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

}
