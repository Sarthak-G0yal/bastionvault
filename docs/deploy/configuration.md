# Configuration

BastionVault is configured entirely with environment variables. There is no configuration file to edit.

## Database

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bastionvault` | JDBC URL of the PostgreSQL database |
| `DB_USERNAME` | `bastionvault` | Database user |
| `DB_PASSWORD` | *(empty)* | Database password |

Flyway creates and migrates the schema on start. Hibernate only validates it.

## Setup and unseal

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `VAULT_SETUP_TOKEN` | random, printed to the log | One-time token that `/setup` requires. Ignored once the vault is initialized. |
| `VAULT_UNSEAL_KEY_FILE` | *(none)* | Path to a file holding the auto-unseal key. Preferred: a mounted Secret. |
| `VAULT_UNSEAL_KEY` | *(none)* | The auto-unseal key itself, used if no file is set. Environment variables are easier to leak, so prefer the file. |
| `VAULT_KDF_ITERATIONS` | `600000` | PBKDF2-HMAC-SHA256 iterations for the master password |

`VAULT_KDF_ITERATIONS` applies when the master password is set, at setup or on change. The value used is stored with the vault, so changing the variable later does not lock you out.

## Tokens

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `VAULT_TOKEN_TTL_MINUTES` | `15` | Session token lifetime when the request does not ask for one |
| `VAULT_TOKEN_MAX_TTL_MINUTES` | `60` | Hard cap. Longer requests are cut to this. |

## UI session

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `VAULT_SESSION_TIMEOUT` | `30m` | Idle timeout of the admin's UI session |
| `VAULT_COOKIE_SECURE` | `false` | Set to `true` when served over HTTPS. The cookie is then sent only over TLS. |

## Network

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `VAULT_TRUSTED_PROXIES` | `127\.0\.0\.1\|0:0:0:0:0:0:0:1` | **Regex** of proxy addresses whose `X-Forwarded-For` header is trusted |

See [Trusted proxies](/deploy/reverse-proxy#trusted-proxies). The server always listens on port `8080`.

## Audit

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `VAULT_AUDIT_RETENTION_DAYS` | `90` | Audit rows older than this are deleted by a daily job |

## Fixed settings

These are not configurable in 0.2:

| Setting | Value |
| :--- | :--- |
| Lockout | 5 failures per user, client or unseal attempt, 20 per IP, within 15 minutes. Locked for 15 minutes. |
| Max value size | 64 KiB |
| Import limits | 256 KiB per group file, 1000 `.env` entries, 4 MiB per vault file |
| Error details | Spring's error messages are never included in responses |

## Logging profile

The Docker image starts with `-Dspring.profiles.active=prod`, which switches console logs to **one JSON object per line** in Elastic Common Schema (ECS). Without the profile, for example under `./mvnw spring-boot:run`, logs are plain text. See [Logging and audit](/operations/logging-and-audit).

## Removed in 0.2

`MONGO_URI`, `VAULT_SALT` and `VAULT_DEPLOYMENT_TOKEN` no longer exist. See [Upgrading from 0.1](/operations/upgrading).
