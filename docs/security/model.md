# Security model

What BastionVault protects, how, and where its limits are. Read this before relying on it.

## What is protected

| Asset | Protection |
| :--- | :--- |
| Secret values at rest | AES-256-GCM with a random 96-bit nonce per write. The AAD binds each ciphertext to its `(group, key)`, so rows cannot be swapped or moved without detection. |
| Data encryption key (DEK) | 256 random bits, held only in memory while unsealed. Stored only wrapped (AES-256-GCM) by each unseal method. |
| Master password | Never stored. Its key-encryption key is PBKDF2-HMAC-SHA256 (600,000 iterations by default) over a per-install random 128-bit salt. |
| Recovery key and auto-unseal key | 256 random bits each, shown once, never stored in plain form |
| Admin login password | BCrypt, cost 12 |
| Client secrets | BCrypt, cost 12. Shown once. |
| API keys | `bvk_<id>_<secret>`. Only the SHA-256 of the 256-bit secret is stored, compared in constant time. |
| Session tokens | 256 random bits, in memory only. Gone on seal or restart. Lifetime capped by the server. |

How these fit together: [How it works](/guide/how-it-works).

## Access control

### UI

- Spring Security form login with an HTTP session. The cookie is `HttpOnly` and `SameSite=Strict`, and `Secure` when `VAULT_COOKIE_SECURE=true`. Sessions have an idle timeout.
- Every view requires the `ADMIN` role.
- Logging in does **not** unseal the vault.

### API

- Stateless. A Bearer session token or API key on every request.
- Every request is checked against the client's policy rules. No rule means no access.

### Setup

`/setup` only works while the vault is uninitialized, and it needs the setup token from the log or from `VAULT_SETUP_TOKEN`.

### Brute-force protection

These are counted per identity (**5**) and per IP (**20**) within 15 minutes, then locked for 15 minutes:

- failed admin logins
- failed unseal attempts
- failed client authentications
- invalid Bearer credentials

Client IPs come from `X-Forwarded-For` **only** when the TCP peer matches `VAULT_TRUSTED_PROXIES`.

### Audit

Logins, seal and unseal, key management, every secret read, write, delete and reveal, policy denials, and failed client or admin authentications are recorded in the database and in the structured log, with actor and source IP. Values are never logged.

Requests with a missing or invalid Bearer credential are not audited one by one. They count toward the IP lockout instead.

## What is not protected

- **A compromised host or cluster while unsealed.** The DEK is in the JVM's memory. Root on the host, or anyone who can exec into the pod, can read it.
- **Auto-unseal trades security for availability.** Whoever can read the Kubernetes Secret holding the unseal key can unseal the vault. Without auto-unseal, a restart leaves the vault sealed until a person unseals it.
- **Transport.** BastionVault speaks plain HTTP. Put it behind TLS (for example Traefik), and don't expose port 8080 beyond the cluster or LAN.
- **Admin accounts have full access.** There is no per-admin scoping and no second factor yet. TOTP is planned.
- **Session tokens are in memory** and are lost on restart. Long-running consumers should use API keys.
- **Lockout state is in memory** and resets on restart.
- **Terraform state** keeps any value read through the `http` data source.
- **Exports are plaintext.** Anything downloaded from an export is outside BastionVault's protection.

## Threats and answers

| If someone… | Then |
| :--- | :--- |
| copies the PostgreSQL database or a backup | They get ciphertext, wrapped keys and hashes. Without a key they can only brute-force the master password at 600,000 PBKDF2 rounds per guess. Use a long master password. |
| edits or swaps ciphertext rows in the database | GCM authentication and the `(group, key)` AAD make decryption fail. Nothing wrong is returned. |
| steals the admin login password | They can sign in, but cannot unseal without a separate key. If the vault is already unsealed, they have full UI access, so use a strong, unique password. |
| steals a client's API key | They get that client's rules only. Revoke the key and check the audit log for its reads. |
| guesses passwords, keys or tokens | They are locked out after 5 attempts per identity or 20 per IP. |
| spoofs `X-Forwarded-For` | The header is ignored unless the connection comes from a trusted proxy |
| reads the cluster's Secrets (with auto-unseal on) | They can unseal. Limit RBAC, or turn off auto-unseal. |
| gets root on the node while the vault is unsealed | Out of scope. They can read process memory. |

## Recovery

To recover after losing the server you need the **PostgreSQL dump** and **one** of: the master password, the recovery key, the auto-unseal key. Losing all three makes the data permanently unreadable. That is by design. See [Backup and recovery](/operations/backup-and-recovery).

## Hardening checklist

- [ ] Served only over HTTPS, with `VAULT_COOKIE_SECURE=true`
- [ ] `VAULT_TRUSTED_PROXIES` set to the proxy's exact address
- [ ] Port 8080 reachable only from the proxy and in-cluster consumers
- [ ] A long master password, different from the login password
- [ ] The recovery key stored offline, in at least two places
- [ ] One client per consumer, with the smallest rules possible
- [ ] API keys with an expiry where practical
- [ ] Database backups copied off the host and test-restored
- [ ] Alerts on a sealed vault and on `DENIED` or `FAILED` audit events
- [ ] RBAC on the namespace limited, if auto-unseal is on

## Reporting a problem

Open a private security advisory on the [GitHub repository](https://github.com/Sarthak-G0yal/bastionvault/security/advisories) rather than a public issue.
