# Security model

What BastionVault protects, how, and where its limits are. Read this before relying on it.

## What is protected

| Asset | Protection |
| :--- | :--- |
| Secret values at rest | AES-256-GCM with a random 96-bit nonce per write. The AAD binds each ciphertext to its `(group, key)`, so rows cannot be swapped or moved without detection. |
| Data encryption key (DEK) | 256 random bits, held only in memory while unsealed. Stored only wrapped (AES-256-GCM) by each unseal method. |
| Master password | Never stored. Its key-encryption key is PBKDF2-HMAC-SHA256 (600,000 iterations by default) over a per-install random 128-bit salt. |
| Recovery key / auto-unseal key | 256 random bits each, shown once, never stored in plain form. |
| Admin login password | BCrypt (cost 12). |
| Client secrets | BCrypt (cost 12). Shown once. |
| API keys | `bvk_<id>_<secret>`. Only SHA-256 of the 256-bit secret is stored, compared in constant time. |
| Session tokens | 256 random bits, in memory only. Gone on seal or restart. Lifetime capped by the server. |

## Access control

- **UI:** Spring Security form login with an HTTP session (http-only, SameSite=Strict cookie,
  `Secure` when `VAULT_COOKIE_SECURE=true`) and an idle timeout. Every view requires the `ADMIN` role.
  Logging in does **not** unseal the vault.
- **API:** stateless. Bearer session token or API key. Every request is checked against the
  client's policy rules. No rule means no access.
- **Setup:** `/setup` only works while uninitialized and needs the setup token from the log or
  `VAULT_SETUP_TOKEN`.
- **Brute force:** failed admin logins, unseal attempts, client authentications and invalid Bearer
  credentials are counted per identity (5) and per IP (20) within 15 minutes, then locked for
  15 minutes. Client IPs come from `X-Forwarded-For` **only** when the TCP peer matches
  `VAULT_TRUSTED_PROXIES`.
- **Audit:** logins, unseal/seal, key management, every secret read, write, delete and reveal,
  policy denials and failed client/admin authentications are recorded in the database and in the structured log, with actor and source
  IP. Values are never logged. Requests with a missing or
  invalid Bearer credential are not audited individually; they count toward the IP lockout.

## What is not protected

- **A compromised host or cluster while unsealed.** The DEK is in the JVM's memory. Root on the host
  (or anyone who can exec into the pod) can read it.
- **Auto-unseal trades security for availability.** Whoever can read the Kubernetes Secret holding
  the unseal key can unseal the vault. Without auto-unseal, a restart leaves the vault sealed until a
  human unseals it.
- **Transport.** BastionVault speaks plain HTTP. Put it behind TLS (Traefik). Don't expose port 8080
  beyond the cluster or LAN.
- **Admin accounts have full access.** There is no per-admin scoping and no second factor yet (TOTP is
  planned).
- **Session tokens in memory** are lost on restart. Long-running consumers should use API keys.
- **Lockout state is in memory** and resets on restart.
- **Terraform state** keeps any value read through the `http` data source.

## Recovery

To recover after losing the server you need the **PostgreSQL dump** and **one** of: master password,
recovery key, auto-unseal key. Losing all three makes the data permanently unreadable. That is by
design.

## Supported versions

| Version | Supported |
| :--- | :--- |
| 0.2.x | Yes |
| 0.1.x | No. It stored values with AES-CBC in MongoDB and has known weaknesses. Upgrade with [Migrating from 0.1](README.md#migrating-from-01). |

## Reporting a vulnerability

Please do not open a public issue, discussion or pull request for a security problem.

Report it privately through
[GitHub security advisories](https://github.com/Sarthak-G0yal/bastionvault/security/advisories/new).
Include the version, what an attacker can do, and the steps to reproduce. Leave out real secret
values, keys and tokens.

What to expect:

- an acknowledgement within 7 days
- a fix or a mitigation plan, shared with you before anything is published
- credit in the advisory and the changelog, unless you prefer not to be named

This is a personal project maintained in spare time, so there is no bug bounty.
