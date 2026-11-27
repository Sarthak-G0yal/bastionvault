# Integrations

BastionVault has a plain REST API, so anything that can send an HTTP request with a header can use it. These pages show the common consumers.

| Consumer | Credential | Typical rule | Page |
| :--- | :--- | :--- | :--- |
| External Secrets Operator (Kubernetes) | API key | `*` or app groups, read | [External Secrets Operator](/integrations/external-secrets) |
| Ansible | API key or session token | the groups the playbook needs, read (plus write to generate) | [Ansible](/integrations/ansible) |
| Terraform / OpenTofu | API key | infrastructure groups, read | [Terraform](/integrations/terraform) |
| Shell scripts, Docker Compose, CI | session token or API key | one group, read | [Scripts and CI](/integrations/scripts) |

## One client per consumer

Create a separate client for each consumer, even if they read the same groups. Then you can:

- give each one only the groups it needs
- see in the [audit log](/ui/audit) who read what
- rotate or disable one without breaking the others

## Bootstrap secrets stay outside

A few credentials have to exist **before** BastionVault can be reached: the API key External Secrets Operator uses, the auto-unseal key, BastionVault's own database password, and the Proxmox token that builds the machines. Keep those outside BastionVault, for example encrypted in git with SOPS and age. Everything else can live in BastionVault.
