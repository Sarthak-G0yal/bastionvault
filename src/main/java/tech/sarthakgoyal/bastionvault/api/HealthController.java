package tech.sarthakgoyal.bastionvault.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.sarthakgoyal.bastionvault.BastionvaultApplication;
import tech.sarthakgoyal.bastionvault.crypto.KeyManager;

/**
 * Unauthenticated status for probes and monitoring. Always 200 while the process is healthy; use the
 * {@code sealed} field (or /api/v1/health/ready, which is 503 while sealed) to alert on a locked vault.
 */
@RestController
public class HealthController {

    private final KeyManager keys;

    public HealthController(KeyManager keys) {
        this.keys = keys;
    }

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "initialized", keys.isInitialized(),
                "sealed", keys.isSealed(),
                "version", BastionvaultApplication.version());
    }

    @GetMapping("/api/v1/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> body = health();
        return keys.isSealed() ? ResponseEntity.status(503).body(body) : ResponseEntity.ok(body);
    }
}
