# Bastion Vault: Homelab Secrets Engine

Bastion Vault is a lightweight, self-hosted Secrets Management API and Administration UI designed specifically for homelab automation. It acts as the secure bridge between human administrators and stateless CI/CD pipelines, allowing deployment tools like Terraform and Ansible to fetch credentials dynamically without storing plain-text passwords on disk.

## Features

* **Pure Java Administration UI:** Built with Vaadin Flow, providing a responsive, dark-mode-ready dashboard to manage and rotate secrets without writing a single line of HTML/JS.
* **Stateless API Retrieval:** Returns flat, easily parsed JSON payloads designed specifically for the Terraform `http` data source and Ansible lookup plugins.
* **Application-Level Encryption:** All secrets are encrypted at rest inside MongoDB using **AES-256-GCM**.
* **PBKDF2 Key Derivation:** The encryption key is dynamically generated in memory at startup by combining a human-readable Master Password with a highly secure environment variable salt.

## Tech Stack

* **Backend:** Java 21, Spring Boot 3.x
* **Database:** Spring Data MongoDB
* **Frontend:** Vaadin Flow (Lumo Theme)
* **Cryptography:** Java `javax.crypto` (AES-256-GCM / PBKDF2WithHmacSHA256)

---

## Architecture & Security Model

Bastion Vault does not store the encryption key anywhere in the database. To decrypt payloads, the application requires two distinct components to generate the 256-bit AES key:

1. **The Salt (`VAULT_SALT`):** A long, randomized string passed to the application exclusively via an OS environment variable. 
2. **The Master Password:** A secure password input by the administrator.

*Warning: If the MongoDB database is backed up but the `VAULT_SALT` is lost, the ciphertext becomes permanently unrecoverable. Always backup the salt offline.*

---

## Configuration & Deployment

### 1. Environment Variables

Before starting the application, ensure the following environment variables are set on the host system (or injected via your deployment automation):

```bash
# Required: The cryptographic salt used for key derivation
export VAULT_SALT="your-highly-secure-random-string-here"

# Optional: MongoDB Connection String (Defaults to localhost:27017)
export SPRING_DATA_MONGODB_URI="mongodb://admin:password@192.168.1.300:27017/bastion"

# Required: The deployment token for API access
export VAULT_DEPLOYMENT_TOKEN="your-static-api-token"

```

### 2. Running the Application

Execute the packaged JAR. To prevent the JVM from triggering Proxmox LXC OOM kills, strict memory boundaries are recommended:

```bash
java -Xms256m -Xmx512m -jar bastion-vault.jar

```

---

## Infrastructure Integration

### Terraform Usage

Bastion Vault is designed to natively integrate with Terraform without requiring a custom provider. Use the `http` data source to pull secrets directly into memory during `terraform apply`.

```hcl
data "http" "postgres_password" {
  url = "[https://vault.homelab118.home/api/v1/secrets/POSTGRES_DB_PASSWORD](https://vault.homelab118.home/api/v1/secrets/POSTGRES_DB_PASSWORD)"
  
  request_headers = {
    Authorization = "Bearer ${var.deployment_api_token}"
    Accept        = "application/json"
  }
}

resource "proxmox_virtual_environment_container" "postgres" {
  # ... standard configuration ...
  initialization {
    user_account {
      password = jsondecode(data.http.postgres_password.response_body).value
    }
  }
}

```

### API Reference

**Retrieve a Secret** `GET /api/v1/secrets/{key}`

**Headers:**

* `Authorization: Bearer <VAULT_DEPLOYMENT_TOKEN>`

**Response (200 OK):**

```json
{
  "key": "POSTGRES_DB_PASSWORD",
  "value": "super-secure-homelab-password-123",
  "last_updated": "2026-06-10T14:30:00Z"
}

```

---

## Development Setup

1. Ensure **Java 21** and **Maven** are installed.
2. Start a local MongoDB instance.
3. Set the `VAULT_SALT` environment variable in your IDE run configuration.
4. Run the `BastionVaultApplication.java` main class.
5. Navigate to `http://localhost:8080/admin/secrets` to access the Vaadin UI.

```

