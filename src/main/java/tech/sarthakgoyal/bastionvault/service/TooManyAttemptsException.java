package tech.sarthakgoyal.bastionvault.service;

/** Raised while a key (user, client, IP) is locked out. Maps to HTTP 429. */
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException() {
        super("Too many failed attempts. Try again later.");
    }
}
