package tech.sarthakgoyal.bastionvault.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "admin_profile")
public class AdminProfile {

    @Id
    private String id = "SINGLETON"; // We only ever want ONE admin profile
    private String email;
    private String masterPasswordHash;

    public AdminProfile() {
    }

    public AdminProfile(String email, String masterPasswordHash) {
        this.email = email;
        this.masterPasswordHash = masterPasswordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMasterPasswordHash() {
        return masterPasswordHash;
    }

    public void setMasterPasswordHash(String masterPasswordHash) {
        this.masterPasswordHash = masterPasswordHash;
    }
}
