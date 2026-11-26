package tech.sarthakgoyal.bastionvault.crypto;

/** Published whenever the vault is sealed, so in-memory session state can be dropped. */
public record VaultSealedEvent() {
}
