# API reference

All endpoints are under `/api/v1` on the same port as the UI (`8080`).

- Send credentials as `Authorization: Bearer <session-token-or-api-key>`. See [Authentication](/api/authentication).
- Request and response bodies are JSON unless noted.
- Errors are JSON: `{"error": "..."}`.
- Every request is checked against the client's [policy rules](/guide/concepts#clients-and-policy-rules). No matching rule means `403`.

## Endpoints

| Method | Path | Needs | Returns |
| :--- | :--- | :--- | :--- |
| `GET` | [`/health`](/api/health) | nothing | `{"initialized","sealed","version"}`, always `200` |
| `GET` | [`/health/ready`](/api/health) | nothing | Same body, but `503` while sealed |
| `POST` | [`/auth/token`](/api/authentication#session-tokens) | client ID + secret | `{"token","expiresInMinutes","expiresAt"}` |
| `GET` | [`/secrets/{group}/{key}`](/api/secrets#read-a-secret) | read | `{"group","key","value"}` |
| `PUT` | [`/secrets/{group}/{key}`](/api/secrets#write-a-secret) | write | `201` created, `204` updated |
| `DELETE` | [`/secrets/{group}/{key}`](/api/secrets#delete-a-secret) | write | `204` |
| `POST` | [`/secrets/{group}/{key}/generate`](/api/secrets#generate-a-value-if-missing) | read + write | `{"group","key","value","created"}` |
| `GET` | [`/groups/{group}`](/api/groups#read-a-group-as-json) | read | `{"KEY":"value",…}` |
| `GET` | [`/env/{group}`](/api/groups#read-a-group-as-env) | read | `.env` text |
| `PUT` | [`/env/{group}?overwrite=false`](/api/import-export#import-env-into-a-group) | write | `{"created","updated","skippedExisting","warnings"}` |
| `GET` | [`/export/{group}?format=json`](/api/import-export#export-one-group) | read | One group as `json`, `yaml` or `env` |
| `GET` | [`/export?format=json`](/api/import-export#export-every-readable-group) | read, per group | `{group: {KEY: value}}` |
| `PUT` | [`/import/{group}?format=&overwrite=false`](/api/import-export#import-into-one-group) | write | Import counts |
| `PUT` | [`/import?format=&overwrite=false`](/api/import-export#import-many-groups) | write on every group in the file | Import counts |

## Status codes

| Status | Meaning |
| :---: | :--- |
| `200` / `201` / `204` | Success |
| `400` | Invalid name or value, bad body, or `expiresInMinutes <= 0` |
| `401` | Missing, wrong, expired or revoked credential, or a disabled client |
| `403` | Authenticated, but no policy rule allows it |
| `404` | No such secret or group |
| `429` | Locked out after too many failures (15 minutes) |
| `503` | The vault is sealed |

## A complete session

```bash
BV=https://bastionvault.homelab118.home

TOKEN=$(curl -s -X POST $BV/api/v1/auth/token -H 'Content-Type: application/json' \
  -d '{"clientId":"ansible","clientSecret":"'"$BV_CLIENT_SECRET"'","expiresInMinutes":30}' | jq -r .token)

# one value
curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/secrets/postgres/ADMIN_PASSWORD | jq -r .value

# a whole group as a .env file
curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/env/affine > .env

# write a value
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"value":"s3cret"}' $BV/api/v1/secrets/affine/API_KEY
```

::: tip Keep values out of your shell history
Read secrets into variables or files, as above, instead of pasting them on the command line. Use `--data-binary @file` to send file contents.
:::
