package tech.sarthakgoyal.bastionvault.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "secrets")
public class SecretDocument {

    @Id
    private String key;
    private String ciphertext;
    private String iv;
    private LocalDateTime lastUpdated;

    // By ONLY having an empty constructor, Spring Data is forced to use the 
    // exact field names and setters, preventing mapping mix-ups.
    public SecretDocument() {
    }

    // Getters and Setters
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
