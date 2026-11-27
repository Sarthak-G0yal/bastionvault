package tech.sarthakgoyal.bastionvault.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import tech.sarthakgoyal.bastionvault.domain.ApiClient;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.repository.AuditEventRepository;

/**
 * Read-only figures for the overview, computed from the audit log: API reads per day, recent
 * machine access, and when each group was last read. No secret values are involved.
 */
@Service
public class ActivityService {

    public static final int DAYS = 14;

    public record Day(LocalDate date, int reads) {
    }

    public record Snapshot(List<Day> days, int reads24h, int readsPrevious24h, List<AuditEvent> recent,
            Map<String, Instant> lastReadByGroup) {
        public int totalReads() {
            return days.stream().mapToInt(Day::reads).sum();
        }
    }

    private final AuditEventRepository audit;
    private final ClientService clients;
    private final PolicyService policies;

    public ActivityService(AuditEventRepository audit, ClientService clients, PolicyService policies) {
        this.audit = audit;
        this.clients = clients;
        this.policies = policies;
    }

    /** A successful machine read: single secret, group, .env or export. */
    public static boolean isRead(AuditEvent e) {
        return Outcome.SUCCESS.name().equals(e.getOutcome())
                && (e.getAction().startsWith("READ_") || e.getAction().startsWith("EXPORT"));
    }

    public static String groupOf(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        int slash = target.indexOf('/');
        return slash < 0 ? target : target.substring(0, slash);
    }

    public Snapshot snapshot(int recentLimit) {
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(DAYS - 1);
        List<AuditEvent> events = audit.findByActorTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                ActorType.CLIENT.name(), first.atStartOfDay(zone).toInstant());

        int[] perDay = new int[DAYS];
        int last24 = 0;
        int prev24 = 0;
        Map<String, Instant> lastRead = new HashMap<>();
        for (AuditEvent e : events) {
            if (!isRead(e)) {
                continue;
            }
            int index = (int) (e.getOccurredAt().atZone(zone).toLocalDate().toEpochDay() - first.toEpochDay());
            if (index >= 0 && index < DAYS) {
                perDay[index]++;
            }
            Duration age = Duration.between(e.getOccurredAt(), now);
            if (age.compareTo(Duration.ofHours(24)) <= 0) {
                last24++;
            } else if (age.compareTo(Duration.ofHours(48)) <= 0) {
                prev24++;
            }
            String group = groupOf(e.getTarget());
            if (group != null) {
                lastRead.merge(group, e.getOccurredAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            days.add(new Day(first.plusDays(i), perDay[i]));
        }
        List<AuditEvent> recent = events.stream().limit(recentLimit).toList();
        return new Snapshot(days, last24, prev24, recent, lastRead);
    }

    /** For each group, the active clients whose policy lets them read it. */
    public Map<String, List<String>> readersByGroup(List<String> groups) {
        Map<String, List<String>> readers = new HashMap<>();
        for (ApiClient client : clients.list()) {
            if (!client.isActive()) {
                continue;
            }
            var readRules = policies.rulesFor(client.getClientId()).stream()
                    .filter(tech.sarthakgoyal.bastionvault.domain.PolicyRule::isCanRead).toList();
            for (String group : groups) {
                if (readRules.stream().anyMatch(r -> GlobMatcher.matches(r.getGroupPattern(), group))) {
                    readers.computeIfAbsent(group, g -> new ArrayList<>()).add(client.getClientId());
                }
            }
        }
        return readers;
    }
}
