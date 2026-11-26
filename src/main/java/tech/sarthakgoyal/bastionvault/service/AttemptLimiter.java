package tech.sarthakgoyal.bastionvault.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tech.sarthakgoyal.bastionvault.config.VaultProperties;

/**
 * In-memory failed-attempt counter with temporary lockout (decision 6A).
 *
 * <p>Keys are namespaced strings: {@code admin:<user>}, {@code client:<id>}, {@code unseal},
 * {@code ip:<address>}. Identity keys lock after {@code maxFailures}; IP keys after
 * {@code ipMaxFailures}. Counters reset after {@code window} without failures.
 */
@Component
public class AttemptLimiter {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final VaultProperties.Lockout config;
    private final Clock clock;

    @Autowired
    public AttemptLimiter(VaultProperties properties) {
        this(properties.lockout(), Clock.systemUTC());
    }

    AttemptLimiter(VaultProperties.Lockout config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    private static final class Entry {
        int failures;
        Instant firstFailure;
        Instant lockedUntil;
    }

    public static String ip(String address) {
        return "ip:" + address;
    }

    /** @throws TooManyAttemptsException if any of the keys is currently locked */
    public void assertAllowed(String... keys) {
        Instant now = clock.instant();
        for (String key : keys) {
            Entry entry = entries.get(key);
            if (entry != null && entry.lockedUntil != null && now.isBefore(entry.lockedUntil)) {
                throw new TooManyAttemptsException();
            }
        }
    }

    public void recordFailure(String... keys) {
        Instant now = clock.instant();
        for (String key : keys) {
            int limit = key.startsWith("ip:") ? config.ipMaxFailures() : config.maxFailures();
            entries.compute(key, (k, entry) -> {
                Entry e = entry == null ? new Entry() : entry;
                if (e.firstFailure == null || now.isAfter(e.firstFailure.plus(config.window()))) {
                    e.failures = 0;
                    e.firstFailure = now;
                }
                e.failures++;
                if (e.failures >= limit) {
                    e.lockedUntil = now.plus(config.lockDuration());
                    e.failures = 0;
                    e.firstFailure = null;
                }
                return e;
            });
        }
    }

    public void reset(String... keys) {
        for (String key : keys) {
            entries.remove(key);
        }
    }

    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(e -> {
            Entry v = e.getValue();
            boolean lockExpired = v.lockedUntil == null || now.isAfter(v.lockedUntil);
            boolean windowExpired = v.firstFailure == null || now.isAfter(v.firstFailure.plus(config.window()));
            return lockExpired && windowExpired;
        });
    }
}
