# Contributing to BastionVault

Thanks for helping. BastionVault guards other people's secrets, so changes are held to a careful
standard. This file explains how to get set up, what a good change looks like, and how it gets merged.

## Ground rules

- **Security problems are not issues.** Report them privately through
  [GitHub security advisories](https://github.com/Sarthak-G0yal/bastionvault/security/advisories/new),
  never in a public issue or pull request. See [SECURITY.md](SECURITY.md).
- **Discuss big changes first.** Open an issue before starting a new feature, a new dependency, or
  anything that touches `crypto/`, authentication or the database schema.
- **One change per pull request.** Small, focused pull requests are reviewed faster.

## Set up

Requirements: Java 21 and Docker (for a local PostgreSQL). The Maven wrapper is included.

```bash
git clone https://github.com/Sarthak-G0yal/bastionvault.git
cd bastionvault
./mvnw test                                   # must pass before you start

docker run -d --name bv-pg -p 5432:5432 \
  -e POSTGRES_DB=bastionvault -e POSTGRES_USER=bastionvault -e POSTGRES_PASSWORD=dev \
  postgres:16
DB_PASSWORD=dev VAULT_SETUP_TOKEN=dev-setup-token ./mvnw spring-boot:run
```

Open `http://localhost:8080/setup` and use the token `dev-setup-token`.

## Making a change

1. Fork the repository and create a branch from `main`, for example `fix/env-export-quoting`.
2. Make the change, with tests.
3. Run `./mvnw verify`. CI runs exactly this on every pull request.
4. Update the documentation when behaviour changes. See [Documentation](#documentation).
5. Open a pull request that says **what** changed and **why**, and how you tested it.

### Commit messages

Short, lowercase, imperative, describing the effect:

```
export and import secrets as JSON and YAML
fix lockout counting failures outside the window
```

Keep unrelated changes in separate commits.

## What a good change looks like

### Tests

| Change | Expected tests |
| :--- | :--- |
| Crypto, parsing, validation | Unit tests (`CryptoTest`, `EnvParserTest`, `SecretCodecTest`, `ServiceUnitTest`) |
| A new or changed API endpoint | `VaultIntegrationTest`: success, `401`, `403`, `404` and sealed (`503`) cases |
| A new or changed view | `ViewsBrowserlessTest` drives it, and `ViewAccessTest` still passes |
| A bug fix | A test that fails without the fix |

### Security rules

- Secret values never appear in logs, audit rows, exception messages or error responses.
- Every new action that reads, writes or changes access is audited through `AuditService`.
- Every new API endpoint goes through `PolicyService`. No rule means no access.
- New UI views need `@RolesAllowed("ADMIN")`.
- Don't add a dependency for something the JDK or Spring already does. Every dependency is
  something that can leak secrets.
- Values shown once (keys, client secrets) use `UiSupport.showOnce`.

### Database

Schema changes are new Flyway migrations (`V2__short_description.sql`, …) in
`src/main/resources/db/migration`. Never edit a released migration. Keep to portable SQL so the H2
test database can run it.

### UI

Use the tokens in `styles.css` (`--bv-*`): the warm neutral ramp, one accent, Geist fonts and Lucide
icons at 1.5 stroke. Check both the light and the dark theme. The UI must not load anything from
the internet.

## Documentation

- `README.md` and `SECURITY.md` live on `main`.
- The documentation site lives on the `docs` branch (VitePress, Markdown in `docs/`):

  ```bash
  git worktree add ../bastionvault-docs docs
  cd ../bastionvault-docs
  bun install
  bun run docs:dev
  ```

  Open a separate pull request against `docs` for documentation changes.

## Releases

Maintainers move the `Unreleased` entries in [CHANGELOG.md](CHANGELOG.md) under the new version,
then push a tag `vX.Y.Z`. CI runs the tests and publishes the Docker image
`sarthakg0yal/bastionvault:X.Y.Z` and `:X.Y`.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE),
the same as the project.
