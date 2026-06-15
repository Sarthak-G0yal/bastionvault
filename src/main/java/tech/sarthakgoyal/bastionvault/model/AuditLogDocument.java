package tech.sarthakgoyal.bastionvault.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "audit_logs")
public class AuditLogDocument {

    @Id
    private String id;
    private Instant timestamp = Instant.now();
    private String clientId;
    private String action; // e.g., "READ_ENV", "READ_SECRET"
    private String targetResource; // e.g., "vaultwarden"
    private String status; // "SUCCESS" or "UNAUTHORIZED"

    public AuditLogDocument() {
    }

    public AuditLogDocument(String clientId, String action, String targetResource, String status) {
        this.clientId = clientId;
        this.action = action;
        this.targetResource = targetResource;
        this.status = status;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetResource() {
        return targetResource;
    }

    public String getStatus() {
        return status;
    }
}
