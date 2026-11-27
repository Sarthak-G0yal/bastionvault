# What is BastionVault?

BastionVault is a small, self-hosted secrets engine for homelab automation.

- **People** manage secrets in a web UI. You sign in, unseal the vault, and create, reveal, import or export secrets.
- **Machines** such as External Secrets Operator, Ansible, Terraform, CI jobs and shell scripts read secrets over a REST API. Each request needs a token, and each machine sees only the groups its policy rules allow.
- **Every value is encrypted at rest** with AES-256-GCM. The encryption key stays in memory only. It is never written to the database in plain form.

It is built for the case where you want your own vault, free and on your own hardware, and you want the secrets to survive the loss of the server they run on.

## Who it is for

BastionVault fits a homelab or a small self-hosted setup with:

- a handful of apps that each need database passwords, API keys, or a whole `.env` file
- automation (Kubernetes, Ansible, Terraform) that should fetch those secrets instead of keeping them in git
- one or a few administrators

It is **not** a replacement for HashiCorp Vault or OpenBao in a multi-team organisation. It has no dynamic secrets, no PKI, no per-admin scoping, no high availability, and no second factor yet. See [Security model](/security/model) for the full list of limits.

## What it does

| Feature | Details |
| :--- | :--- |
| Encrypted storage | AES-256-GCM per value, bound to its group and key. Uses envelope encryption with a single data key. |
| Seal and unseal | The vault starts sealed. Unseal it with the master password, the recovery key, or an optional auto-unseal key file. |
| Admin UI | Form login with an HTTP session. Pages for secrets, machine identities, the audit log and settings. Light and dark theme. |
| Machine clients | Each client has an ID and a secret. Policy rules grant read or write per group glob. |
| Credentials | Short-lived session tokens (`bvt_…`, 60 min max) and long-lived, revocable API keys (`bvk_…`). |
| Import and export | `.env`, JSON and YAML, per group or for the whole vault, with a preview before writing. |
| Audit | Database table plus structured ECS JSON logs, kept for 90 days by default. |
| Brute-force protection | Lockout after repeated failed logins, unseal attempts, or client authentications. |
| Health endpoints | Probes for Kubernetes or Docker health checks. One of them reports sealed state. |

## Stack

| Layer | Technology |
| :--- | :--- |
| Language and runtime | Java 21 |
| Framework | Spring Boot 4.1, Spring Security 7 |
| UI | Vaadin 25 (server-side Java UI), Geist fonts, Lucide icons, all served by the app itself |
| Database | PostgreSQL, schema managed by Flyway |
| Packaging | Docker image `sarthakg0yal/bastionvault`, built by GitHub Actions |

The UI loads nothing from the internet. Fonts and icons are bundled, so it works on an isolated network.

## Next steps

- [Getting started](/guide/getting-started): run it with Docker Compose in five minutes
- [Concepts](/guide/concepts): groups, keys, clients, rules, tokens
- [How it works](/guide/how-it-works): sealing, envelope encryption, and access control
