package tech.sarthakgoyal.bastionvault.service;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tech.sarthakgoyal.bastionvault.config.VaultProperties;
import tech.sarthakgoyal.bastionvault.crypto.Aead;
import tech.sarthakgoyal.bastionvault.crypto.VaultSealedEvent;

/**
 * Short-lived session tokens ({@code bvt_...}) held in memory only (decision 3A/3B). The server
 * enforces the lifetime: requests above {@code maxTtlMinutes} are capped, non-positive values are
 * rejected. All tokens are dropped when the vault seals or the process restarts.
 */
@Service
public class TokenService {

    public static final String PREFIX = "bvt_";

    private record Session(String clientId, Instant expiresAt) {
    }

    public record Issued(String token, int ttlMinutes, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final VaultProperties.Token config;

    public TokenService(VaultProperties properties) {
        this.config = properties.token();
    }

    public Issued issue(String clientId, Integer requestedTtlMinutes) {
        int ttl = requestedTtlMinutes == null ? config.defaultTtlMinutes() : requestedTtlMinutes;
        if (ttl <= 0) {
            throw new IllegalArgumentException("expiresInMinutes must be positive; use an API key for long-lived access");
        }
        ttl = Math.min(ttl, config.maxTtlMinutes());
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(Aead.randomBytes(32));
        Instant expiresAt = Instant.now().plusSeconds(ttl * 60L);
        sessions.put(token, new Session(clientId, expiresAt));
        return new Issued(token, ttl, expiresAt);
    }

    public Optional<String> validate(String token) {
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.clientId());
    }

    public void revokeClient(String clientId) {
        sessions.values().removeIf(s -> s.clientId().equals(clientId));
    }

    @EventListener
    public void onSealed(VaultSealedEvent event) {
        sessions.clear();
    }

    @Scheduled(fixedDelay = 300_000)
    public void purgeExpired() {
        Instant now = Instant.now();
        sessions.values().removeIf(s -> now.isAfter(s.expiresAt()));
    }
}
