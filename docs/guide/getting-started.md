# Getting started

This guide runs BastionVault and PostgreSQL on one host with Docker Compose, then walks through first-time setup, your first secret, and your first machine client.

For production on Kubernetes, see [Deploy on Kubernetes](/deploy/kubernetes) after you finish this page.

## Prerequisites

- Docker with the Compose plugin
- A clone of the repository:

```bash
git clone https://github.com/Sarthak-G0yal/bastionvault.git
cd bastionvault
```

## 1. Configure

```bash
cp .env.example .env
```

Edit `.env` and set at least `DB_PASSWORD` to a long random string:

```bash
# generates a 48-character password
openssl rand -base64 36
```

`.env` is gitignored. Never commit it. All other variables are optional. See [Configuration](/deploy/configuration).

## 2. Start

```bash
docker compose up -d
docker compose logs bastion-vault | grep "setup token"
```

The first start creates the database schema and prints a **one-time setup token** to the log. If you would rather choose it yourself, set `VAULT_SETUP_TOKEN` in `.env` before the first start.

::: tip
The compose file uses `sarthakg0yal/bastionvault:${BASTIONVAULT_VERSION:-latest}`. Pin a real version in `.env` (`BASTIONVAULT_VERSION=0.2.0`) instead of relying on `latest`.
:::

## 3. Run setup

Open `http://<host>:8080/setup` and enter:

1. **Setup token** from the log. It stops anyone else on the network from claiming a fresh instance.
2. **Admin username** (3 to 64 characters: letters, digits, `. _ @ -`) and a **login password** of at least 12 characters. You use these to sign in to the UI.
3. **Master password**, at least 12 characters and different from the login password. You use this to unseal the vault.

BastionVault then shows a **recovery key**. It appears exactly once.

::: danger Store the recovery key now
Put it somewhere outside the homelab, for example a password manager plus a paper copy. If you lose the master password **and** the recovery key (and have no auto-unseal key), your secrets cannot be decrypted by anyone. That is by design.
:::

`/setup` stops working once the vault is initialized.

## 4. Sign in and unseal

Sign in at `http://<host>:8080/login` with the admin username and login password.

Signing in does **not** unseal the vault. If the vault is sealed (for example after a restart), every page sends you to the **Unseal** page. Enter the master password or the recovery key there.

## 5. Add a secret

1. Open **Secrets** and click **New secret**.
2. Enter a **Group** (for example `affine`), a **Key** (`DATABASE_URL`) and a **Value**. Values can span several lines and be up to 64 KiB.
3. Click **Save secret**.

To load many at once, use **Import file** with an existing `.env`, `.json` or `.yaml` file. See [Import and export](/ui/import-export).

## 6. Let a machine read it

1. Open **Machine identities** → **New client**. Enter a client ID, for example `scripts`.
2. Copy the **client secret**. It is shown once.
3. Click **Access** on the client and add a rule: group pattern `affine`, **Read** checked.

Then, from any machine:

```bash
BV=http://<host>:8080

TOKEN=$(curl -s -X POST $BV/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"clientId":"scripts","clientSecret":"<the secret>"}' | jq -r .token)

curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/secrets/affine/DATABASE_URL | jq -r .value
curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/env/affine > .env
```

For long-running consumers such as External Secrets Operator, create an **API key** instead of using session tokens. See [Authentication](/api/authentication).

## What to do next

- [Enable auto-unseal](/deploy/auto-unseal) if the vault should come back by itself after a restart
- [Put it behind TLS](/deploy/reverse-proxy) before you use it beyond your own machine
- [Set up backups](/operations/backup-and-recovery)
- [Connect External Secrets Operator](/integrations/external-secrets)
