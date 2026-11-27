# Authentication

Every API call except the [health endpoints](/api/health) needs a Bearer credential:

```http
Authorization: Bearer bvt_…        # session token
Authorization: Bearer bvk_<id>_…   # API key
```

Both belong to a **client** created under [Machine identities](/ui/clients). Both get exactly the client's policy rules.

## Session tokens

Exchange the client ID and secret for a short-lived token.

```http
POST /api/v1/auth/token
Content-Type: application/json

{"clientId": "ansible", "clientSecret": "…", "expiresInMinutes": 30}
```

```json
{"token": "bvt_…", "expiresInMinutes": 30, "expiresAt": "2026-11-27T12:30:00Z"}
```

| Field | Rule |
| :--- | :--- |
| `expiresInMinutes` | Optional. Defaults to `VAULT_TOKEN_TTL_MINUTES` (15). Values above `VAULT_TOKEN_MAX_TTL_MINUTES` (60) are cut to the maximum, and the response tells you the real lifetime. `0` or negative returns `400`. |

Responses:

| Status | When |
| :---: | :--- |
| `200` | Token issued |
| `401` | Wrong client ID or secret, or the client is disabled |
| `429` | Too many failures for this client ID or IP |
| `503` | The vault is sealed |

Session tokens are held **in memory only**. They disappear when the vault is sealed or the process restarts, and when the client's secret is rotated. A consumer that runs for a long time should use an API key, or fetch a new token when it gets `401`.

::: info Removed in 0.2
Non-expiring tokens (`"expiresInMinutes": 0`) no longer exist. Use an API key.
:::

## API keys

API keys are created in the UI: **Machine identities → Access → API keys → Create API key**. Optionally set an expiry in days.

Format: `bvk_<id>_<secret>`

- `<id>` identifies the key, so lookup does not need to scan every key.
- `<secret>` is 256 random bits. Only its SHA-256 hash is stored, and comparisons run in constant time.

An API key is refused (`401`) when it is revoked, expired, or its client is disabled. Rotating the client secret does **not** affect API keys.

Use API keys for:

- External Secrets Operator
- CI runners that keep a credential in their own secret store
- anything that needs to work across BastionVault restarts

## Choosing between them

| Situation | Use |
| :--- | :--- |
| A script that runs now and then and already has the client secret | Session token |
| A Kubernetes operator that polls every hour | API key |
| A CI pipeline | API key in the CI secret store, or a session token from a client secret kept there |
| Terraform runs | API key in a variable (`TF_VAR_bastionvault_api_key`) |

## Lockout

Failed client authentications and invalid Bearer credentials are counted per client ID (5) and per IP (20) within 15 minutes. Past that, requests are refused with `429` for 15 minutes. A successful authentication resets the client's counter. The counters live in memory and reset on restart.
