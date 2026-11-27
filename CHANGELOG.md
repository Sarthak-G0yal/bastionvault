# Changelog

Notable changes to BastionVault. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.0]

A rewrite of storage and the security model. 0.1 data has to be migrated, see
[Migrating from 0.1](README.md#migrating-from-01).

### Added

- PostgreSQL storage with Flyway migrations, replacing MongoDB.
- Envelope encryption: one random data key, AES-256-GCM per value with the `(group, key)` as
  associated data, and the data key wrapped once per unseal method.
- Sealed and unsealed states. Unseal with the master password, the recovery key shown at setup, or
  an optional auto-unseal key read from a file.
- One-time setup at `/setup`, protected by a setup token.
- Machine clients with glob policy rules for read and write. Deny by default.
- Short-lived session tokens (`bvt_…`, capped by the server) and long-lived, revocable API keys
  (`bvk_…`) with an optional expiry and last-use time.
- Import and export of `.env`, JSON and YAML, per group and for the whole vault, in the UI and the
  API. The vault-wide export asks for the master password.
- Import from BastionVault 0.1 (MongoDB export plus the old master password and salt).
- Audit log in the database and as structured JSON log lines, with retention.
- Lockout after repeated failures per user, client, unseal and source IP.
- Redesigned UI with an overview page, group boxes, and light and dark themes. Fonts and icons are
  served by the app itself.
- `/api/v1/health` and `/api/v1/health/ready` (503 while sealed).
- CI that runs the tests on every push and publishes the Docker image for `v*` tags.

### Changed

- Master password key derivation is PBKDF2-HMAC-SHA256 with 600,000 iterations and a random salt
  per install.
- Admin login passwords and client secrets are hashed with BCrypt (cost 12).
- Session tokens must expire. `expiresInMinutes: 0` is rejected.

### Removed

- `MONGO_URI`, `VAULT_SALT` and `VAULT_DEPLOYMENT_TOKEN`. Use API keys instead of the deployment token.

### Security

- Spring Boot 4.1.3, Vaadin 25.1.13 and jsoup 1.23.2 for known CVEs in earlier versions.
- Generating or turning off the auto-unseal key needs the master password, not only a signed-in
  session.
- Parallel attempts can no longer get around the lockout.
- Policy glob matching no longer uses regular expressions, so long patterns cannot cause
  catastrophic backtracking.
- Invalid group and key names are rejected before the policy check.
- Client authentication takes the same time for unknown and known client IDs.
- Audit fields are length-limited and stripped of control characters.

## [0.1.1]

The first public version: MongoDB storage with AES-CBC, a deployment token for automation and a
Vaadin UI. No longer supported.

[Unreleased]: https://github.com/Sarthak-G0yal/bastionvault/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Sarthak-G0yal/bastionvault/releases/tag/v0.2.0
[0.1.1]: https://hub.docker.com/r/sarthakg0yal/bastionvault/tags?name=0.1.1
