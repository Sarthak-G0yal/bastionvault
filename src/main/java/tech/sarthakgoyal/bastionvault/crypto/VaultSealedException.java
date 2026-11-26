package tech.sarthakgoyal.bastionvault.crypto;

/** Thrown when an operation needs the data key but the vault is sealed. Maps to HTTP 503. */
public class VaultSealedException extends RuntimeException {
    public VaultSealedException() {
        super("Vault is sealed");
    }
}
