# Scripts and CI

## Shell

A small helper that fetches a session token once and reads values:

```bash
#!/usr/bin/env bash
set -euo pipefail

BV=${BV:-https://bastionvault.homelab118.home}

bv_token() {
  curl -fsS -X POST "$BV/api/v1/auth/token" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg id "$BV_CLIENT_ID" --arg s "$BV_CLIENT_SECRET" \
          '{clientId: $id, clientSecret: $s, expiresInMinutes: 15}')" | jq -r .token
}

bv_get() {  # bv_get <group> <key>
  curl -fsS -H "Authorization: Bearer $TOKEN" "$BV/api/v1/secrets/$1/$2" | jq -r .value
}

TOKEN=$(bv_token)
PGPASSWORD=$(bv_get postgres ADMIN_PASSWORD) psql -h 192.168.1.140 -U postgres -c 'select 1'
```

`jq -n --arg` builds the JSON body, so quotes or backslashes in the secret cannot break it.

## Docker Compose apps

Write a group to an env file, start the stack, then remove the file:

```bash
curl -fsS -H "Authorization: Bearer $BV_API_KEY" "$BV/api/v1/env/linkding" -o .env
chmod 600 .env
docker compose up -d
shred -u .env
```

Compose reads `.env` only when containers are created, so deleting it afterwards is safe.

## Upload an existing `.env`

Move an app's current `.env` into the vault, keeping values already stored there:

```bash
curl -fsS -X PUT -H "Authorization: Bearer $TOKEN" --data-binary @.env \
  "$BV/api/v1/env/linkding?overwrite=false" | jq
```

## GitHub Actions or another CI

Store an API key in the CI's secret store and fetch only what the job needs:

```yaml
- name: Fetch deploy secrets
  env:
    BV_API_KEY: ${{ secrets.BV_API_KEY }}
  run: |
    value=$(curl -fsS -H "Authorization: Bearer $BV_API_KEY" \
      https://bastionvault.example.com/api/v1/secrets/ci/REGISTRY_TOKEN | jq -r .value)
    echo "::add-mask::$value"
    echo "REGISTRY_TOKEN=$value" >> "$GITHUB_ENV"
```

A hosted runner can only reach BastionVault if it is exposed beyond your LAN, which is **not** recommended. For a homelab, use a self-hosted runner inside the network.
