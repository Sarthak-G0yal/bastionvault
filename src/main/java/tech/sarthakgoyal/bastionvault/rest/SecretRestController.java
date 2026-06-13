package tech.sarthakgoyal.bastionvault.rest;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.sarthakgoyal.bastionvault.service.SecretService;

@RestController
@RequestMapping("/api/v1/secrets")
public class SecretRestController {

    private final SecretService secretService;

    public SecretRestController(SecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> getSecret(@PathVariable String key) {
        try {
            Optional<String> decryptedValue = secretService.fetchAndDecrypt(key);

            if (decryptedValue.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Secret not found"));
            }

            return ResponseEntity.ok(Map.of(
                    "key", key,
                    "value", decryptedValue.get()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of("error", "Vault is currently locked. Admin must unlock it first."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Decryption failed"));
        }
    }
}
