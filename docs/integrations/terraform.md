# Terraform and OpenTofu

Terraform and OpenTofu read secrets with the `http` data source from the `hashicorp/http` provider.

## Setup

1. Create a client `terraform` with read rules for the groups it needs.
2. Create an API key and pass it in as a variable, never committed:

```bash
export TF_VAR_bastionvault_api_key='bvk_…'
```

```hcl
variable "bastionvault_api_key" {
  type      = string
  sensitive = true
}
```

## Read one value

```hcl
data "http" "db_password" {
  url             = "https://bastionvault.homelab118.home/api/v1/secrets/postgres/ADMIN_PASSWORD"
  request_headers = { Authorization = "Bearer ${var.bastionvault_api_key}" }
}

locals {
  db_password = sensitive(jsondecode(data.http.db_password.response_body).value)
}
```

## Read a whole group

```hcl
data "http" "proxmox" {
  url             = "https://bastionvault.homelab118.home/api/v1/groups/proxmox"
  request_headers = { Authorization = "Bearer ${var.bastionvault_api_key}" }

  lifecycle {
    postcondition {
      condition     = self.status_code == 200
      error_message = "BastionVault returned ${self.status_code} (503 = sealed, 403 = no rule)"
    }
  }
}

locals {
  proxmox = sensitive(jsondecode(data.http.proxmox.response_body))
}

# local.proxmox.API_TOKEN, local.proxmox.SSH_KEY, …
```

## State contains the values

::: danger
Data-source results are written to the Terraform state. Even with `sensitive()`, the state file contains the secret in plain text. Protect the state: use OpenTofu's state encryption, or a backend with encryption at rest and tight access.
:::

## Chicken and egg

Terraform often builds the very machines BastionVault runs on. Credentials needed for that first run, such as the Proxmox API token, cannot come from BastionVault. Keep them in a `secrets.auto.tfvars` encrypted with SOPS, and use BastionVault only for what is created afterwards.
