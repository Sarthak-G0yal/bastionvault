package tech.sarthakgoyal.bastionvault.service;

/** The client is authenticated but its policy does not allow the operation. Maps to HTTP 403. */
public class PolicyDeniedException extends RuntimeException {
    public PolicyDeniedException(String message) {
        super(message);
    }
}
