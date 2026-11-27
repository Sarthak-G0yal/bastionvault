package tech.sarthakgoyal.bastionvault.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.config.VaultProperties;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.repository.AuditEventRepository;

/**
 * Records every security-relevant action (decision 8B): a row in audit_events for the UI, plus a
 * structured log line ("bastionvault.audit") that Alloy ships to Loki. Never pass secret values here.
 */
@Service
public class AuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("bastionvault.audit");
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final VaultProperties properties;

    public AuditService(AuditEventRepository repository, VaultProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ActorType actorType, String actor, String action, String target, Outcome outcome,
            String detail) {
        // Bound caller-supplied text to the column sizes, so an oversized client ID or path cannot make
        // the insert fail and drop the audit row. Control characters are removed for the text log.
        actor = clip(actor, 128);
        target = clip(target, 256);
        detail = clip(detail, 512);
        String ip = clip(ClientIp.current(), 64);
        AUDIT_LOG.atInfo()
                .addKeyValue("audit.actor_type", actorType.name())
                .addKeyValue("audit.actor", actor)
                .addKeyValue("audit.action", action)
                .addKeyValue("audit.target", target)
                .addKeyValue("audit.outcome", outcome.name())
                .addKeyValue("audit.source_ip", ip)
                .log("{} {} {} -> {}", actor, action, target == null ? "-" : target, outcome);
        repository.save(new AuditEvent(actorType, actor, action, target, outcome, ip, detail));
    }

    static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String clean = value.replaceAll("\\p{Cntrl}", "?");
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    public void admin(String admin, String action, String target, Outcome outcome) {
        record(ActorType.ADMIN, admin, action, target, outcome, null);
    }

    public void client(String clientId, String action, String target, Outcome outcome) {
        record(ActorType.CLIENT, clientId, action, target, outcome, null);
    }

    public void system(String action, String target, Outcome outcome, String detail) {
        record(ActorType.SYSTEM, "system", action, target, outcome, detail);
    }

    public List<AuditEvent> latest(int limit) {
        return repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit));
    }

    /** Daily retention job. */
    @Scheduled(cron = "0 15 3 * * *")
    public void prune() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.audit().retentionDays()));
        int removed = repository.deleteOlderThan(cutoff);
        if (removed > 0) {
            LOG.info("Pruned {} audit events older than {} days", removed, properties.audit().retentionDays());
        }
    }
}
