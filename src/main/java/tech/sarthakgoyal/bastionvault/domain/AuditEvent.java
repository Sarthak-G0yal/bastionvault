package tech.sarthakgoyal.bastionvault.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One audit trail entry. Never contains secret values. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    public enum ActorType { ADMIN, CLIENT, SYSTEM, ANONYMOUS }

    public enum Outcome { SUCCESS, DENIED, FAILED, NOT_FOUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    private String target;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "source_ip")
    private String sourceIp;

    private String detail;

    protected AuditEvent() {
    }

    public AuditEvent(ActorType actorType, String actor, String action, String target, Outcome outcome,
            String sourceIp, String detail) {
        this.actorType = actorType.name();
        this.actor = truncate(actor, 128);
        this.action = truncate(action, 48);
        this.target = truncate(target, 256);
        this.outcome = outcome.name();
        this.sourceIp = truncate(sourceIp, 64);
        this.detail = truncate(detail, 512);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActorType() { return actorType; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getOutcome() { return outcome; }
    public String getSourceIp() { return sourceIp; }
    public String getDetail() { return detail; }
}
