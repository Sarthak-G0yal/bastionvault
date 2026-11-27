# How it works

## Architecture

```
                    ┌──────────── BastionVault ─────────────┐
 Admin ─ login ───▶ │ UI (form login, session)               │
       ─ unseal ──▶ │  master password │ recovery key        │──▶ data key (RAM only)
 K8s Secret ──────▶ │  auto-unseal key (optional)            │          │
                    │                                        │          ▼
 ESO / Ansible ───▶ │ API (Bearer token or API key)          │   AES-256-GCM
   / Terraform      │  └─ policy: which groups, read/write   │          │
                    └────────────────────────────────────────┘          ▼
                                                               PostgreSQL (ciphertext)
```

BastionVault is one Spring Boot application with two entry points:

- the **UI** at `/` for admins: Vaadin views behind Spring Security form login, with an HTTP session
- the **API** at `/api/v1` for machines: stateless, with Bearer credentials checked against policy rules on every request

Both use the same services and the same in-memory data key. PostgreSQL holds only ciphertext, wrapped keys, hashes and audit rows.

## Envelope encryption

BastionVault uses **one random data key** (the DEK, 256 bits) to encrypt every secret. The DEK itself is never stored in plain form. Instead it is stored **wrapped** (encrypted) once for each unseal method:

```
master password ──PBKDF2 (600k)──▶ KEK₁ ──wraps──┐
recovery key (256 bit) ──────────▶ KEK₂ ──wraps──┼──▶ DEK ──AES-256-GCM──▶ each secret
auto-unseal key (256 bit) ───────▶ KEK₃ ──wraps──┘
```

- **Master password**: turned into a key-encryption key with PBKDF2-HMAC-SHA256, 600,000 iterations by default, over a random 128-bit salt per install.
- **Recovery key**: 256 random bits, generated at setup and shown once.
- **Auto-unseal key**: 256 random bits, optional, generated in Settings and shown once.

Unsealing with any of them unwraps the same DEK. Because of this design:

- **Changing the master password** or **regenerating a key** only re-wraps the DEK. Secrets are never re-encrypted, so these operations are instant.
- **Revoking an unseal method** means deleting its wrapped copy. The old key then unwraps nothing.

## Encrypting a secret

Each value is encrypted with **AES-256-GCM** under the DEK:

- a fresh random **96-bit nonce** for every write
- **additional authenticated data (AAD)** set to the secret's `(group, key)`

GCM authenticates the ciphertext, so tampering is detected. The AAD binds each ciphertext to its location: copying the encrypted `postgres/ADMIN_PASSWORD` row over `affine/API_KEY` makes decryption fail instead of silently returning the wrong value.

## Seal and unseal

| Event | Effect |
| :--- | :--- |
| Process start | Sealed. The DEK is not in memory. If an auto-unseal key is configured and valid, the vault unseals immediately. |
| Unseal (UI) | The master password or recovery key unwraps the DEK into memory. |
| **Seal vault** button | Drops the DEK from memory and invalidates all session tokens. |
| Restart or crash | The same as seal: memory is gone. |

While sealed:

- the UI redirects every protected page to **Unseal**
- the API answers `503` for anything that touches secrets
- `GET /api/v1/health` still answers `200` with `"sealed": true`, and `GET /api/v1/health/ready` answers `503`

## Request flow for a machine

1. The client sends `Authorization: Bearer <bvt_… or bvk_…>`.
2. The API filter resolves it to a client. A session token is looked up in memory. For an API key, the SHA-256 hash of its secret part is compared in constant time.
3. Missing or invalid credentials return `401` and count toward the per-IP lockout.
4. The policy service checks the client's rules for the group and the capability (read or write). No match returns `403` and writes a `DENIED` audit event.
5. A sealed vault returns `503`. `POST /auth/token` also returns `503` while sealed, so no new session tokens can be issued.
6. The value is decrypted and returned, and an audit event is written: `SUCCESS`, or `NOT_FOUND` when the secret does not exist.

## Storage

PostgreSQL holds these tables, created by Flyway on first start:

| Table | Holds |
| :--- | :--- |
| `vault_config` | One row: KDF algorithm, iterations and salt, plus the DEK wrapped by password, by recovery key, and optionally by unseal key |
| `admin_users` | Admin usernames, BCrypt password hashes, last login |
| `secrets` | `(group, key)`, `nonce ‖ ciphertext ‖ tag`, who changed it and when |
| `api_clients` | Client IDs, display names, BCrypt secret hashes, active flag, last use |
| `policy_rules` | Group pattern plus read/write flags per client |
| `api_keys` | Key ID, SHA-256 hash, expiry, revoked flag, last use |
| `audit_events` | Time, actor, action, target, outcome, source IP |

A database dump on its own reveals no secret values.
