
# Bastion Vault

Bastion Vault is a self-hosted, cryptographically secure secrets engine engineered specifically for homelab environments. It bridges the gap between secure human administration and stateless machine-to-machine automation.

Instead of leaving plain-text passwords in Terraform states, Ansible playbooks, or environment files, Bastion Vault stores your credentials using AES-256 encryption and serves them on the fly to authorized machines via short-lived, token-protected REST APIs.

---

## Key Features

* **Zero-Knowledge Architecture:** Master passwords are never saved to the database. The AES-256 encryption key is derived dynamically in memory using PBKDF2 only when the vault is explicitly unlocked by an admin.
* **Machine Identities:** Generate unique Client IDs and one-time Client Secrets for your deployment servers. Access can be monitored via the built-in Audit Log and revoked instantly.
* **Short-Lived Automation Tokens:** Machines trade their credentials for temporary 15-minute bearer tokens, ensuring compromised deployment scripts cannot grant permanent access to your infrastructure.
* **Bulk `.env` Streaming:** Group secrets by application (e.g., `vaultwarden`, `nextcloud`) and stream them directly into a `.env` format for instant Docker Compose deployments.
* **Granular JSON API:** Fetch single, specific passwords dynamically during Terraform or Ansible runs.
* **Premium Glassmorphic UI:** A purely Java-driven frontend built with Vaadin Flow, featuring a custom dark-mode Aura theme, interactive Accordions, and direct shadow-DOM protection against password manager interference.

---

## Tech Stack

* **Backend:** Java 21, Spring Boot 4.1.0, Spring Security
* **Frontend:** Vaadin Flow (Aura Theme, CSS Glassmorphism)
* **Database:** MongoDB
* **Cryptography:** `AES/CBC/PKCS5Padding`, `PBKDF2WithHmacSHA256`, `BCrypt`
* **Deployment:** Docker, Docker Compose

---

## Environment Variables

The application relies on a few specific environment variables for its cryptographic engine and database connection.

| Variable | Description | Example |
| :--- | :--- | :--- |
| `VAULT_SALT` | A static, highly secure string used to salt the PBKDF2 derivation function. Keep this safe and do not change it after initialization. | `homelab-super-secret-salt` |
| `MONGO_URI` | The connection string for the backend MongoDB database. | `mongodb://user:pass@mongo:27017/bastion` |

---

## Deployment (Docker Compose)

The easiest way to run Bastion Vault is via Docker Compose, which provisions both the application and a secure, isolated MongoDB instance.

**1. Create your `docker-compose.yml`:**

```yaml
networks:
  bastion-net:
    driver: bridge

services:
  mongodb:
    image: mongo:7
    container_name: bastion-mongo
    restart: unless-stopped
    networks:
      - bastion-net
    volumes:
      - bastion-db-data:/data/db
    environment:
      MONGO_INITDB_ROOT_USERNAME: vault_admin
      MONGO_INITDB_ROOT_PASSWORD: secure-mongo-password-123

  bastion-vault:
    image: sarthakg0yal/bastionvault:latest
    container_name: bastion-vault
    restart: unless-stopped
    depends_on:
      - mongodb
    networks:
      - bastion-net
    ports:
      - "8080:8080"
    environment:
      - VAULT_SALT=homelab-super-secret-salt
      - MONGO_URI=mongodb://vault_admin:secure-mongo-password-123@bastion-mongo:27017/bastion?authSource=admin

volumes:
  bastion-db-data:
```

**2. Spin up the environment:**

```bash
docker compose up -d
```

---

## Initialization & UI Usage

1. Open your browser and navigate to `http://<your-server-ip>:8080`.
2. You will be automatically redirected to the **Setup Wizard** on your first boot.
3. Provide an administrator email and a secure **Master Password**.
* *Note: Your Master Password mathematically derives your AES encryption key. Do not lose it. Data recovery is impossible without it.*


4. Unlock the vault to enter the dashboard.
5. Go to **Machine Identities** to create an API Client (e.g., `docker-runner-01`) and copy the one-time Client Secret.
6. Go to **Secrets Groups** to add encrypted secrets. Assign them to an **App Group** (e.g., `nexus`) to bundle them together.

---

## API & Automation Integration

Bastion Vault requires machines to authenticate to receive a temporary token before they can pull secrets.

### 1. Authenticate & Get Token

Trade your Machine Client credentials for a 15-minute bearer token.

```bash
curl -s -X POST http://<bastion-ip>:8080/api/v1/auth/token \
     -H "Content-Type: application/json" \
     -d '{"clientId": "docker-runner-01", "clientSecret": "YOUR_COPIED_SECRET"}'
```

**Response:**

```json
{
  "token": "eyJhb...long.random.string...",
  "expiresInMinutes": 15
}
```

### 2. Stream an Environment Bundle (Bulk)

Pass the token to download all secrets belonging to a specific Application Group (e.g., `nexus`), formatted perfectly for Docker Compose.

```bash
curl -s -H "Authorization: Bearer <YOUR_TOKEN>" \
     http://<bastion-ip>:8080/api/v1/env/nexus > .env
```

**Resulting `.env` file:**

```env
DB_PASS=super-secure-database-password-123
API_KEY=my-secret-key-999
```

### 3. Fetch a Single Secret (Granular)

If your Terraform or Ansible script just needs one specific password, request it by its group and key.

```bash
curl -s -H "Authorization: Bearer <YOUR_TOKEN>" \
     http://<bastion-ip>:8080/api/v1/secrets/nexus/DB_PASS
```

**Response:**

```json
{
  "key": "DB_PASS",
  "value": "super-secure-database-password-123"
}
```

---

## Example: Secure Docker Deployment Script

This is how you use Bastion Vault to securely deploy applications on your homelab without ever storing passwords on the disk permanently.

```bash
#!/bin/bash

# 1. Authenticate with BastionVault
echo "Authenticating..."
RESPONSE=$(curl -s -X POST http://<bastion-ip>:8080/api/v1/auth/token \
     -H "Content-Type: application/json" \
     -d '{"clientId": "docker-runner", "clientSecret": "my-client-secret"}')

TOKEN=$(echo $RESPONSE | jq -r .token)

# 2. Download the secure environment bundle
echo "Pulling secrets..."
curl -s -H "Authorization: Bearer $TOKEN" \
     http://<bastion-ip>:8080/api/v1/env/nexus > .env

# 3. Boot the application
echo "Starting Nexus..."
docker compose up -d

# 4. Destroy the local plaintext secrets
rm .env
echo "Deployment complete and secrets wiped."
```

---

## Security Architecture

1. **Authentication:** Human UI login is verified against a `BCrypt` hash of the Master Password. Machine API access is verified using BCrypt-hashed Client Secrets.
2. **Key Derivation:** Upon human login, the raw Master Password and the environment `VAULT_SALT` are fed into `PBKDF2WithHmacSHA256` (65,536 iterations) to derive a 256-bit AES key.
3. **Data Encryption:** Secrets are encrypted via `AES/CBC/PKCS5Padding`. A unique Initialization Vector (IV) is generated for every individual secret payload.
4. **Data Isolation:** Secrets are isolated via compound database indexes (`Group` + `Key`) to prevent cross-application collisions or accidental overwrites.
5. **Storage:** Only the Base64-encoded Ciphertext and IVs are stored in MongoDB. The AES decryption key is held strictly in volatile application RAM and is immediately destroyed when the vault is locked.

---

## License

This project is licensed under the MIT License - see the [LICENSE](https://www.google.com/search?q=LICENSE) file for details.
