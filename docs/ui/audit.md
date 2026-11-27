# Audit log

The **Audit log** page shows the most recent events, newest first. Click **Refresh** to load new ones.

| Column | Meaning |
| :--- | :--- |
| Time | When it happened |
| Actor | The admin username, the client ID, or the system |
| Action | What was attempted (see the list below) |
| Target | Usually `group/key`, a group, or a client ID |
| Outcome | `SUCCESS`, `DENIED` (no policy rule), `FAILED` (wrong credential), `NOT_FOUND` |
| Source IP | The client address. It is taken from `X-Forwarded-For` only when the request came through a [trusted proxy](/deploy/reverse-proxy#trusted-proxies) |
| Detail | Extra context, for example import counts |

Secret values are **never** written to the audit log.

## Actions

| Area | Actions |
| :--- | :--- |
| Vault | `SETUP`, `UNSEAL`, `SEAL`, `CHANGE_MASTER_PASSWORD`, `REGENERATE_RECOVERY_KEY`, `ENABLE_AUTO_UNSEAL`, `DISABLE_AUTO_UNSEAL`, `VERIFY_MASTER_PASSWORD` |
| Admin | `LOGIN`, `CHANGE_LOGIN_PASSWORD` |
| Secrets | `READ_SECRET`, `READ_GROUP`, `READ_ENV`, `WRITE_SECRET`, `DELETE_SECRET`, `GENERATE_SECRET`, `REVEAL_SECRET` |
| Import and export | `EXPORT_*`, `EXPORT_ALL_*`, `IMPORT`, `IMPORT_ALL`, `LEGACY_IMPORT` |
| Clients | `AUTH`, `CREATE_CLIENT`, `DELETE_CLIENT`, `ENABLE_CLIENT`, `DISABLE_CLIENT`, `ROTATE_CLIENT_SECRET`, `ADD_POLICY`, `REMOVE_POLICY`, `CREATE_API_KEY`, `REVOKE_API_KEY` |

## What is not recorded individually

Requests with a **missing or invalid** Bearer credential are not written as separate events, so a scanner can't flood the table. They still count toward the per-IP [lockout](/security/model#brute-force-protection).

## Retention

Rows older than `VAULT_AUDIT_RETENTION_DAYS` (90 by default) are deleted by a daily job. For longer retention or alerting, ship the structured logs to Loki. See [Logging and audit](/operations/logging-and-audit).
