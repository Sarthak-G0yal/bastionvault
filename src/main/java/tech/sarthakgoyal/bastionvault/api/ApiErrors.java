package tech.sarthakgoyal.bastionvault.api;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletResponse;
import tech.sarthakgoyal.bastionvault.crypto.VaultSealedException;
import tech.sarthakgoyal.bastionvault.service.NotFoundException;
import tech.sarthakgoyal.bastionvault.service.PolicyDeniedException;
import tech.sarthakgoyal.bastionvault.service.TooManyAttemptsException;

/** Consistent JSON errors for the API: {"error": "..."}. */
@RestControllerAdvice(basePackageClasses = ApiErrors.class)
public class ApiErrors {

    @ExceptionHandler(VaultSealedException.class)
    public ResponseEntity<Map<String, String>> sealed(VaultSealedException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Vault is sealed");
    }

    @ExceptionHandler(PolicyDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(PolicyDeniedException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, String>> tooMany(TooManyAttemptsException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    /** For filters and entry points that run outside the MVC exception handling. */
    static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}
