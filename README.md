# Bastion Vault

Bastion Vault is a self-hosted, cryptographically secure secrets engine engineered specifically for homelab environments. It bridges the gap between secure human administration and stateless machine-to-machine automation.

Instead of leaving plain-text passwords in Terraform states, Ansible playbooks, or environment files, Bastion Vault stores your credentials using AES-256 encryption and serves them on the fly via a token-protected REST API.

---

## Key Features

* **Zero-Knowledge Architecture:** Master passwords are never saved to the database. The AES-256 encryption key is derived dynamically in memory using PBKDF2 only when the vault is explicitly unlocked.
* **Stateless Automation API:** A dedicated endpoint designed for Terraform and Ansible `http` providers to securely pull secrets into memory during deployment pipelines.
* **Premium Glassmorphic UI:** A purely Java-driven frontend built with Vaadin Flow, featuring a custom dark-mode Aura theme, tactile feedback, and direct shadow-DOM protection against password manager interference.
* **First-Boot Initialization:** A built-in setup wizard to securely establish the initial administrator profile using BCrypt password hashing.
* **Docker-Native:** Packaged using a highly optimized multi-stage Alpine Linux container for minimal footprint and maximum security.

---

## Tech Stack

* **Backend:** Java 21, Spring Boot 4.1.0, Spring Security
* **Frontend:** Vaadin Flow (Aura Theme, CSS Glassmorphism)
* **Database:** MongoDB
* **Cryptography:** `AES/GCM/NoPadding`, `PBKDF2WithHmacSHA256`, `BCrypt`
* **Deployment:** Docker, Docker Compose

---

## Environment Variables

The application relies on specific environment variables for its cryptographic engine and database connection. 

| Variable | Description | Example |
| :--- | :--- | :--- |
| `VAULT_SALT` | A static, highly secure string used to salt the PBKDF2 derivation function. Keep this safe. | `homelab-super-secret-salt` |
| `VAULT_DEPLOYMENT_TOKEN` | The bearer token required by Terraform/Ansible to fetch secrets via the API. | `homelab-deploy-777` |
| `SPRING_DATA_MONGODB_URI` | The connection string for the backend MongoDB database. | `mongodb://user:pass@mongo:27017/bastion` |

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
      - VAULT_DEPLOYMENT_TOKEN=homelab-deploy-777
      # This matches the new Spring Boot 4 mapping we set in application.yml
      - MONGO_URI=mongodb://vault_admin:secure-mongo-password-123@bastion-mongo:27017/bastion?authSource=admin

volumes:
  bastion-db-data:
```

**2. Spin up the environment:**

```bash
docker compose up -d

```

---

## First-Boot Initialization & Usage

1. Open your browser and navigate to `http://<your-server-ip>:8080`.
2. You will be automatically redirected to the **Setup Wizard**.
3. Provide an administrator email and a secure **Master Password**.
* *Note: Your Master Password is used to mathematically derive your AES encryption key. Do not lose it, or your data will be permanently unrecoverable.*


4. Once initialized, the vault will lock. Log in with your new Master Password to drop into the Glassmorphic dashboard.
5. Add your target keys (e.g., `POSTGRES_PROD_DB`) and their plaintext values.

---

## API & Automation Integration

Bastion Vault is designed to be queried statelessly by your CI/CD pipelines.

**Standard `curl` request:**

```bash
curl -s -H "Authorization: Bearer <VAULT_DEPLOYMENT_TOKEN>" \
     http://<your-server-ip>:8080/api/v1/secrets/POSTGRES_PROD_DB

```

**Expected JSON Response:**

```json
{
  "key": "POSTGRES_PROD_DB",
  "value": "super-secure-database-password-123"
}

```

### Terraform Example

You can utilize the `http` data source in Terraform to pull credentials dynamically, ensuring they are never hardcoded into your `.tf` files.

```terraform
data "http" "db_password" {
  url = "http://<bastion-ip>:8080/api/v1/secrets/POSTGRES_PROD_DB"
  
  request_headers = {
    Authorization = "Bearer homelab-deploy-777"
  }
}

resource "docker_container" "postgres" {
  name  = "production-db"
  image = "postgres:15"
  
  env = [
    "POSTGRES_PASSWORD=${jsondecode(data.http.db_password.response_body)["value"]}"
  ]
}

```

---

## Security Architecture

1. **Authentication:** User login is verified against a `BCrypt` hash of the Master Password.
2. **Key Derivation:** Upon successful login, the raw Master Password and the environment `VAULT_SALT` are fed into `PBKDF2WithHmacSHA256` (65,536 iterations) to derive a 256-bit AES key.
3. **Data Encryption:** Secrets are encrypted via `AES/GCM/NoPadding`. A unique 12-byte Initialization Vector (IV) is generated for every secret.
4. **Storage:** Only the Base64-encoded Ciphertext and the unique IV are stored in MongoDB. The AES key is held strictly in volatile application memory and is destroyed when the vault is manually locked or the container restarts.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

