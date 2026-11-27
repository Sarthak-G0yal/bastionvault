# BastionVault

A small self-hosted secrets engine for homelab automation. You manage secrets in a web UI. Machines
(External Secrets Operator, Ansible, Terraform, scripts) read them over a token-protected REST API.
Every value is encrypted at rest with AES-256-GCM, and the key never touches the database.

**Stack:** Java 21 · Spring Boot 4.1 · Spring Security 7 · Vaadin 25 · PostgreSQL + Flyway

> Upgrading from 0.1 (MongoDB)? See [Migrating from 0.1](#migrating-from-01). The security model
> changed. [SECURITY.md](SECURITY.md) describes what is protected and what is not.

---

## How it works

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

- **Sealed vs unsealed.** On start the vault is *sealed*: the data key is not in memory, so nothing
  can be read or written. Unsealing loads it. Sealing (button, or restart) drops it.
- **Login is not unseal.** Signing in to the UI needs your admin username and password. Unsealing
  needs the **master password**, the **recovery key**, or the **auto-unseal key**. These are separate
  secrets.
- **Envelope encryption.** One random data key encrypts every secret. That key is stored only in
  wrapped (encrypted) form, once per unseal method. Changing the master password or rotating a key
  re-wraps the data key; secrets are never re-encrypted.
- **Deny by default.** A machine client can only touch groups that one of its policy rules grants.

---

## Quick start (Docker Compose)

```bash
cp .env.example .env              # set DB_PASSWORD
docker compose up -d
docker compose logs bastion-vault | grep "setup token"
```

Open `http://<host>:8080/setup` and enter:

1. the **setup token** from the log (stops anyone else from claiming a fresh instance; set your own
   with `VAULT_SETUP_TOKEN`),
2. an **admin username and login password**,
3. a **master password** (different from the login password).

You get a **recovery key** once. Store it offline (a password manager outside the homelab, plus
paper). If you lose the master password *and* the recovery key, the data cannot be recovered.

---

## Concepts

| Thing | What it is | Example |
| :--- | :--- | :--- |
| **Group** | Namespace for related secrets, usually one per app | `affine`, `firefly3` |
| **Key** | Name of one secret in a group | `DATABASE_URL`, `private.key` |
| **Client** | A machine identity, with a client ID and secret | `external-secrets`, `ansible` |
| **Policy rule** | Grants a client read and/or write on groups matching a glob (`*`, `?`) | `app-*` read |
| **Session token** | `bvt_…`, from `POST /api/v1/auth/token`. 15 min default, 60 min max, kept in memory only | scripts, CI jobs |
| **API key** | `bvk_<id>_<secret>`, long-lived, optional expiry, revocable, shows last use | External Secrets Operator |

Names: groups `[A-Za-z0-9][A-Za-z0-9._-]*`, keys `[A-Za-z0-9_][A-Za-z0-9_.-]*`, client IDs lowercase.
Values can be up to 64 KiB, multi-line included.

---

## API

All endpoints are under `/api/v1`. Send credentials as `Authorization: Bearer <token-or-api-key>`.
Errors are JSON: `{"error": "..."}`.

| Method | Path | Needs | Returns |
| :--- | :--- | :--- | :--- |
| `GET` | `/health` | nothing | `{"initialized","sealed","version"}`, always 200 |
| `GET` | `/health/ready` | nothing | same, but **503 while sealed** |
| `POST` | `/auth/token` | client ID + secret | `{"token","expiresInMinutes","expiresAt"}` |
| `GET` | `/secrets/{group}/{key}` | read | `{"group","key","value"}` |
| `PUT` | `/secrets/{group}/{key}` | write | `201` created / `204` updated. Body `{"value":"..."}` |
| `DELETE` | `/secrets/{group}/{key}` | write | `204` |
| `POST` | `/secrets/{group}/{key}/generate` | read + write | Creates a random value if missing (idempotent). Body `{"length":32}` |
| `GET` | `/groups/{group}` | read | `{"KEY":"value",…}` |
| `GET` | `/env/{group}` | read | `.env` text (see below) |
| `PUT` | `/env/{group}?overwrite=false` | write | Import a `.env` body. Returns `{"created","updated","skippedExisting","warnings"}` |
| `GET` | `/export/{group}?format=json` | read | One group as `json`, `yaml` or `env` (download) |
| `GET` | `/export?format=json` | read, per group | Every group the client can read: `{group: {KEY: value}}` |
| `PUT` | `/import/{group}?format=&overwrite=false` | write | Import a `json`, `yaml` or `env` body. Format comes from `?format=`, else the `Content-Type`, else `.env` |
| `PUT` | `/import?format=&overwrite=false` | write on **every** group in the file | Import `{group: {KEY: value}}`. 403 if any group is not writable |

| Status | Meaning |
| :---: | :--- |
| 400 | Invalid name or value, or `expiresInMinutes <= 0` |
| 401 | Missing, wrong, expired or revoked credential |
| 403 | Authenticated, but no policy rule allows it |
| 404 | No such secret or group |
| 429 | Locked out after too many failures (15 min) |
| 503 | Vault is sealed |

### Examples

```bash
BV=https://bastionvault.homelab118.home

# Short-lived token (capped at VAULT_TOKEN_MAX_TTL_MINUTES)
TOKEN=$(curl -s -X POST $BV/api/v1/auth/token -H 'Content-Type: application/json' \
  -d '{"clientId":"ansible","clientSecret":"'"$BV_CLIENT_SECRET"'","expiresInMinutes":30}' | jq -r .token)

curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/secrets/postgres/ADMIN_PASSWORD | jq -r .value
curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/env/affine > .env
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"value":"s3cret"}' $BV/api/v1/secrets/affine/API_KEY

# Upload a whole .env file into a group (existing keys are kept unless overwrite=true)
curl -s -X PUT -H "Authorization: Bearer $TOKEN" --data-binary @.env \
  "$BV/api/v1/env/affine?overwrite=false"
```

### `.env` format

- `KEY='value'` when the value has no `'` or newline. Single quotes are literal, with no `$`
  interpolation.
- Otherwise `KEY="value"` with `\\ \" \$ \n \r` escaped.
- Keys that are not valid variable names (like `private.key`) are left out and listed in a
  trailing comment. Use the JSON endpoints for those.

### JSON and YAML

| Shape | JSON | YAML | Used by |
| :--- | :--- | :--- | :--- |
| One group | `{"DB_PASS": "x", "PORT": "5432"}` | `DB_PASS: x` / `PORT: "5432"` | `/export/{group}`, `/import/{group}`, *Import file* |
| Whole vault | `{"affine": {"DATABASE_URL": "…"}, "memos": {…}}` | `affine:` / `  DATABASE_URL: …` | `/export`, `/import`, *Settings → Export and import everything* |

- Values are always strings. YAML values are kept **exactly as written**: `yes`, `0123` and `5432`
  stay those strings instead of turning into `true`, `83` or a number. JSON numbers and booleans are
  accepted and stored as their text, with a warning.
- Multi-line values are exported as YAML literal blocks (`|`).
- Nested objects and lists inside a group, `null` and empty values are skipped with a warning.
  YAML aliases are limited, so "billion laughs" files are refused.
- Limits: 256 KiB per group file, 4 MiB per vault file.
- Whatever you export, in any format, imports back to exactly the same values.

In the UI, each group on the Secrets page has **Export: .env / JSON / YAML** links.
**Settings → Export everything** asks for the master password before it offers the download, because
the file holds every secret in plain text. **Import vault file** shows a preview per group before
writing anything.

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$BV/api/v1/export/affine?format=yaml" > affine.yaml
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/yaml' \
  --data-binary @affine.yaml "$BV/api/v1/import/affine?overwrite=true"
```

### Importing a `.env` file

In the UI, use **Secrets → Import file** (`.env`, `.json`, `.yaml`): choose a group, drop the file, and
check the preview. The format is detected from the file name and can be changed.
The preview lists keys with masked values and whether each will be created, overwritten or skipped,
plus any parser warnings. Nothing is stored until you click **Import**. Scripts can do the same with
`PUT /api/v1/env/{group}`.

Parsing follows Docker Compose:
- comments, blank lines and `export KEY=…` are handled
- `'single'` quotes are literal; `"double"` quotes unescape `\n \r \t \\ \" \$`
- quoted values may span lines (PEM keys)
- unquoted values are trimmed, and `␠#` starts a comment

Duplicate keys keep the last value, and empty values and invalid keys are skipped with a warning.
The file is never stored, and must be at most 256 KiB / 1000 entries. Anything `GET /env/{group}`
returns can be uploaded again unchanged.

---

## Integrations

### Kubernetes: External Secrets Operator (webhook provider)

Create a client `external-secrets` with a read rule for the groups it may sync, then create an API
key for it. Store the key as a Kubernetes Secret (for example via SOPS), and point a
`ClusterSecretStore` at it:

```yaml
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata:
  name: bastionvault
spec:
  provider:
    webhook:
      url: "http://bastionvault.bastionvault.svc:8080/api/v1/groups/{{ .remoteRef.key }}"
      headers:
        Authorization: "Bearer {{ print .auth.token }}"
      result:
        jsonPath: "$"
      secrets:
        - name: auth
          secretRef:
            name: bastionvault-eso
            namespace: external-secrets
            key: token
---
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata:
  name: affine-secret
spec:
  refreshInterval: 1h
  secretStoreRef: { kind: ClusterSecretStore, name: bastionvault }
  target: { name: affine-secret }
  dataFrom:
    - extract: { key: affine }     # every key of group "affine" becomes a key of the Secret
```

While the vault is sealed, ESO keeps the Secrets it already synced, so running apps are unaffected.
These manifests follow the ESO webhook provider docs but are not exercised by this repo's tests.
Check them against your ESO version.

### Ansible

```yaml
- name: Read the Postgres admin password from BastionVault
  ansible.builtin.set_fact:
    postgres_admin_password: >-
      {{ (lookup('ansible.builtin.url', bv_url ~ '/api/v1/secrets/postgres/ADMIN_PASSWORD',
          headers={'Authorization': 'Bearer ' ~ lookup('ansible.builtin.env', 'BV_API_KEY')},
          split_lines=False) | from_json).value }}
  no_log: true
```

### Terraform

```hcl
data "http" "db_password" {
  url             = "https://bastionvault.homelab118.home/api/v1/secrets/postgres/ADMIN_PASSWORD"
  request_headers = { Authorization = "Bearer ${var.bastionvault_api_key}" }
}

locals {
  db_password = sensitive(jsondecode(data.http.db_password.response_body).value)
}
```

Data-source results are written to the Terraform state, so the state file still contains the
value. Encrypt or protect the state accordingly.

---

## Configuration

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/bastionvault` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `bastionvault` / *(empty)* | Database login. Flyway creates the schema. |
| `VAULT_SETUP_TOKEN` | random, logged | One-time token required by `/setup` |
| `VAULT_UNSEAL_KEY_FILE` | — | Path to the auto-unseal key (preferred: a mounted Secret) |
| `VAULT_UNSEAL_KEY` | — | Auto-unseal key as an env var (if no file) |
| `VAULT_TOKEN_TTL_MINUTES` | `15` | Default session-token lifetime |
| `VAULT_TOKEN_MAX_TTL_MINUTES` | `60` | Hard cap. Longer requests are cut to this. |
| `VAULT_KDF_ITERATIONS` | `600000` | PBKDF2-HMAC-SHA256 iterations for the master password |
| `VAULT_SESSION_TIMEOUT` | `30m` | UI session idle timeout |
| `VAULT_COOKIE_SECURE` | `false` | Set `true` when served over HTTPS |
| `VAULT_TRUSTED_PROXIES` | `127\.0\.0\.1\|…` | Regex of proxy IPs allowed to set `X-Forwarded-For` |
| `VAULT_AUDIT_RETENTION_DAYS` | `90` | Audit rows older than this are deleted daily |

Lockout: 5 failures per user, client or unseal (20 per IP) within 15 minutes locks that key
for 15 minutes.

The `prod` profile (on in the Docker image) logs **one JSON object per line** (ECS). Audit events use
the logger `bastionvault.audit`, with `audit.*` fields that Loki can index.

### Auto-unseal

*Settings → Auto-unseal → Generate new unseal key* shows a `bvu_…` key once. Save it as a
Kubernetes Secret, mount it, and set `VAULT_UNSEAL_KEY_FILE`. On start the vault unseals itself. If
the key is missing or was rotated, the vault stays sealed and you unseal it in the UI. Anyone who can
read that Secret can unseal the vault, which is the trade-off for unattended restarts.

---

## Backup and recovery

Back up **the PostgreSQL database** (`pg_dump`). The dump only holds ciphertext. To restore you need
the dump plus **one of**: the master password, the recovery key, or the auto-unseal key. Keep at least
two of them somewhere other than the machine that runs BastionVault.

---

## Migrating from 0.1

0.1 stored AES-CBC values in MongoDB, with a key derived from the master password and `VAULT_SALT`.
To move them:

1. Deploy 0.2 against PostgreSQL and complete `/setup`.
2. Export the old secrets:
   `mongoexport --uri "$MONGO_URI" --collection secrets --jsonArray > secrets.json`
3. In *Settings → Import from BastionVault 0.1*, paste the JSON and enter the old master password
   and old `VAULT_SALT`. Each value is decrypted in memory and re-encrypted with AES-GCM.
4. Recreate the machine clients. 0.1 clients had no policies, so each one now needs explicit rules.
5. Delete `secrets.json` and retire the MongoDB instance.

Removed in 0.2: `MONGO_URI`, `VAULT_SALT`, `VAULT_DEPLOYMENT_TOKEN`, and non-expiring tokens
(`expiresInMinutes: 0`). Use API keys instead.

---

## Development

```bash
./mvnw test                       # unit + integration (H2) + browserless UI tests
./mvnw spring-boot:run            # needs PostgreSQL (DB_URL/DB_USERNAME/DB_PASSWORD)
./mvnw -Pproduction package       # production jar with the compiled frontend
```

Releases: push a tag `vX.Y.Z`. CI runs the tests and publishes `sarthakg0yal/bastionvault:X.Y.Z` and
`:X.Y`. This needs the `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repository secrets. Pin a version in
deployments instead of `latest`.

## Contributing

Bug reports, fixes and documentation are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first.
Report security problems privately, as described in [SECURITY.md](SECURITY.md).

## License

MIT, see [LICENSE](LICENSE).

Bundled assets: the Geist and Geist Mono typefaces (SIL Open Font License, `fonts/GEIST-LICENSE.txt`) and
Lucide icons (ISC, `icons/LUCIDE-LICENSE.txt`), served from the app itself, so the UI loads nothing from
the internet. The interface was redesigned following *unslop* by Damir Sokolovsky
([@heydamir](https://x.com/heydamir)), CC BY 4.0.
