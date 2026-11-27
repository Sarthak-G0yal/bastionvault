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
 *
 * <p>Slow checks (BCrypt, PBKDF2) must use {@link #begin} / {@link #end}: an attempt counts as
 * soon as it starts, so a burst of parallel requests cannot run more guesses than the limit
 * before the first failure is recorded.
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
        int inFlight;
        Instant firstFailure;
        Instant lockedUntil;
    }

    public static String ip(String address) {
        return "ip:" + address;
    }

    private int limit(String key) {
        return key.startsWith("ip:") ? config.ipMaxFailures() : config.maxFailures();
    }

    private boolean locked(Entry entry, Instant now) {
        return entry != null && entry.lockedUntil != null && now.isBefore(entry.lockedUntil);
    }

    private int recentFailures(Entry entry, Instant now) {
        if (entry == null || entry.firstFailure == null || now.isAfter(entry.firstFailure.plus(config.window()))) {
            return 0;
        }
        return entry.failures;
    }

    /** @throws TooManyAttemptsException if any of the keys is currently locked */
    public synchronized void assertAllowed(String... keys) {
        Instant now = clock.instant();
        for (String key : keys) {
            if (locked(entries.get(key), now)) {
                throw new TooManyAttemptsException();
            }
        }
    }

    /**
     * Starts an attempt that is checked slowly. Fails when any key is locked, or when the failures so
     * far plus the attempts still running would reach the limit. Every successful call must be
     * followed by exactly one {@link #end}.
     *
     * @throws TooManyAttemptsException when the attempt is not allowed
     */
    public synchronized void begin(String... keys) {
        Instant now = clock.instant();
        for (String key : keys) {
            Entry entry = entries.get(key);
            if (locked(entry, now)) {
                throw new TooManyAttemptsException();
            }
            int inFlight = entry == null ? 0 : entry.inFlight;
            if (recentFailures(entry, now) + inFlight >= limit(key)) {
                throw new TooManyAttemptsException();
            }
        }
        for (String key : keys) {
            entries.computeIfAbsent(key, k -> new Entry()).inFlight++;
        }
    }

    /** Finishes an attempt started with {@link #begin}, counting a failure when it did not succeed. */
    public synchronized void end(boolean success, String... keys) {
        for (String key : keys) {
            Entry entry = entries.get(key);
            if (entry != null && entry.inFlight > 0) {
                entry.inFlight--;
            }
        }
        if (!success) {
            recordFailure(keys);
        }
    }

    public synchronized void recordFailure(String... keys) {
        Instant now = clock.instant();
        for (String key : keys) {
            Entry e = entries.computeIfAbsent(key, k -> new Entry());
            if (e.firstFailure == null || now.isAfter(e.firstFailure.plus(config.window()))) {
                e.failures = 0;
                e.firstFailure = now;
            }
            e.failures++;
            if (e.failures >= limit(key)) {
                e.lockedUntil = now.plus(config.lockDuration());
                e.failures = 0;
                e.firstFailure = null;
            }
        }
    }

    /** Clears the failure history of the keys. Attempts still running keep counting until they end. */
    public synchronized void reset(String... keys) {
        for (String key : keys) {
            Entry entry = entries.get(key);
            if (entry == null) {
                continue;
            }
            if (entry.inFlight > 0) {
                entry.failures = 0;
                entry.firstFailure = null;
                entry.lockedUntil = null;
            } else {
                entries.remove(key);
            }
        }
    }

    @Scheduled(fixedDelay = 600_000)
    public synchronized void cleanup() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(e -> {
            Entry v = e.getValue();
            boolean lockExpired = v.lockedUntil == null || now.isAfter(v.lockedUntil);
            boolean windowExpired = v.firstFailure == null || now.isAfter(v.firstFailure.plus(config.window()));
            return v.inFlight == 0 && lockExpired && windowExpired;
        });
    }
}
