# Deploy with Docker Compose

The repository's `docker-compose.yml` runs BastionVault with its own PostgreSQL 16. It suits development and single-host setups such as a Docker LXC.

## Files

```yaml
services:
  postgres:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: bastionvault
      POSTGRES_USER: bastionvault
      POSTGRES_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD in .env}
    volumes:
      - bastion-db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bastionvault -d bastionvault"]
      interval: 10s

  bastion-vault:
    image: sarthakg0yal/bastionvault:${BASTIONVAULT_VERSION:-latest}
    restart: unless-stopped
    depends_on:
      postgres: { condition: service_healthy }
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/bastionvault
      DB_USERNAME: bastionvault
      DB_PASSWORD: ${DB_PASSWORD}
      VAULT_COOKIE_SECURE: ${VAULT_COOKIE_SECURE:-false}
      VAULT_TRUSTED_PROXIES: ${VAULT_TRUSTED_PROXIES:-127\.0\.0\.1}
      VAULT_UNSEAL_KEY_FILE: ${VAULT_UNSEAL_KEY_FILE:-}

volumes:
  bastion-db-data:
```

`.env` next to it (copied from `.env.example`, never committed):

```ini
DB_PASSWORD=change-me-to-a-long-random-string
BASTIONVAULT_VERSION=0.2.0
VAULT_COOKIE_SECURE=false
VAULT_TRUSTED_PROXIES=127\.0\.0\.1
VAULT_UNSEAL_KEY_FILE=
```

## Run

```bash
docker compose up -d
docker compose logs bastion-vault | grep "setup token"   # first start only
```

Then complete [setup](/guide/getting-started#_3-run-setup) at `http://<host>:8080/setup`.

## Use an existing PostgreSQL server

Remove the `postgres` service and point `DB_URL` at your server. Create the database and user first:

```sql
CREATE USER bastionvault WITH PASSWORD '…';
CREATE DATABASE bastionvault OWNER bastionvault;
```

```yaml
    environment:
      DB_URL: jdbc:postgresql://192.168.1.140:5432/bastionvault
```

Flyway creates the tables on first start. The user only needs rights on its own database.

## Behind Traefik

Set `VAULT_COOKIE_SECURE=true`, set `VAULT_TRUSTED_PROXIES` to the proxy's address, and stop publishing `8080` beyond what the proxy needs. See [Reverse proxy and TLS](/deploy/reverse-proxy).

## Auto-unseal with Compose

Mount the key file read-only and point at it:

```yaml
  bastion-vault:
    volumes:
      - ./unseal.key:/run/secrets/unseal.key:ro
    environment:
      VAULT_UNSEAL_KEY_FILE: /run/secrets/unseal.key
```

The file must be readable by the container user `bastion`. See [Auto-unseal](/deploy/auto-unseal) before turning this on.

## Upgrade

```bash
# in .env: BASTIONVAULT_VERSION=<new version>
docker compose pull
docker compose up -d
```

The vault restarts **sealed** unless auto-unseal is on. Flyway applies any schema migrations on start. Take a [database backup](/operations/backup-and-recovery) first.

## The image

| Property | Value |
| :--- | :--- |
| Image | `sarthakg0yal/bastionvault:<version>` on Docker Hub |
| Base | `eclipse-temurin:21-jre-alpine` |
| User | `bastion` (non-root) |
| Port | `8080` |
| Profile | `prod` (structured JSON logs) |
| Heap | `-XX:MaxRAMPercentage=75`, sized from the container memory limit |
| Health check | `wget http://127.0.0.1:8080/api/v1/health` every 30 s, 60 s start period |

Give the container at least 512 MiB of memory.
