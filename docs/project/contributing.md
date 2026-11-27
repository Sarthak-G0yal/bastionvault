# Contributing

Thanks for helping. BastionVault guards other people's secrets, so changes are held to a careful standard. The same guide is in the repository as [`CONTRIBUTING.md`](https://github.com/Sarthak-G0yal/bastionvault/blob/main/CONTRIBUTING.md).

## Ground rules

::: danger Security problems are not issues
Report vulnerabilities privately through [GitHub security advisories](https://github.com/Sarthak-G0yal/bastionvault/security/advisories/new), never in a public issue or pull request.
:::

- **Discuss big changes first.** Open an issue before starting a new feature, a new dependency, or anything that touches `crypto/`, authentication or the database schema.
- **One change per pull request.** Small, focused pull requests are reviewed faster.

## Set up

Java 21 and Docker are all you need. The Maven wrapper is included. The full setup is in [Development](/development/).

```bash
git clone https://github.com/Sarthak-G0yal/bastionvault.git
cd bastionvault
./mvnw test     # must pass before you start
```

## Workflow

1. Fork the repository and branch from `main`, for example `fix/env-export-quoting`.
2. Make the change, with tests.
3. Run `./mvnw verify`. CI runs exactly this on every pull request.
4. Update the documentation when behaviour changes.
5. Open a pull request that says **what** changed and **why**, and how you tested it.

Commit messages are short, lowercase and imperative, and describe the effect:

```
export and import secrets as JSON and YAML
fix lockout counting failures outside the window
```

## Tests

| Change | Expected tests |
| :--- | :--- |
| Crypto, parsing, validation | Unit tests (`CryptoTest`, `EnvParserTest`, `SecretCodecTest`, `ServiceUnitTest`) |
| A new or changed API endpoint | `VaultIntegrationTest`: success, `401`, `403`, `404` and sealed (`503`) cases |
| A new or changed view | `ViewsBrowserlessTest` drives it, and `ViewAccessTest` still passes |
| A bug fix | A test that fails without the fix |

## Security rules

- Secret values never appear in logs, audit rows, exception messages or error responses.
- Every new action that reads, writes or changes access is audited through `AuditService`.
- Every new API endpoint goes through `PolicyService`. No rule means no access.
- New UI views need `@RolesAllowed("ADMIN")`.
- Don't add a dependency for something the JDK or Spring already does. Every dependency is something that can leak secrets.
- Values shown once (keys, client secrets) use `UiSupport.showOnce`.

## Database and UI

- Schema changes are new Flyway migrations (`V2__short_description.sql`, …). Never edit a released migration. Keep to portable SQL so the H2 test database can run it.
- The UI uses the tokens in `styles.css` (`--bv-*`): the warm neutral ramp, one orange accent, Geist fonts and Lucide icons at 1.5 stroke. Check both the light and the dark theme. The UI must not load anything from the internet.

## Improving these docs

This site is built with [VitePress](https://vitepress.dev) from the `docs` branch. Pages are Markdown files in `docs/`.

```bash
git worktree add ../bastionvault-docs docs
cd ../bastionvault-docs
bun install
bun run docs:dev       # live preview at http://localhost:5173
bun run docs:build     # what CI publishes
```

Every page has an **Edit this page on GitHub** link at the bottom. Open documentation pull requests against `docs`.

Style: short sentences, plain words, sentence-case headings, and examples that run. When you mention a setting, an endpoint or a UI label, use its exact name.

## License of contributions

By contributing, you agree that your contributions are licensed under the [MIT License](/project/license), the same as the project.
