# Development

## Requirements

- Java 21 (Temurin or any OpenJDK build)
- PostgreSQL, only to run the app locally. Tests use an in-memory H2 database.
- The Maven wrapper (`./mvnw`) is included, so no Maven install is needed.

## Build and run

```bash
./mvnw test                       # unit, integration (H2) and browserless UI tests
./mvnw spring-boot:run            # needs PostgreSQL: DB_URL, DB_USERNAME, DB_PASSWORD
./mvnw -Pproduction package       # production jar with the compiled frontend
```

A quick local database:

```bash
docker run -d --name bv-pg -p 5432:5432 \
  -e POSTGRES_DB=bastionvault -e POSTGRES_USER=bastionvault -e POSTGRES_PASSWORD=dev \
  postgres:16

DB_PASSWORD=dev VAULT_SETUP_TOKEN=dev-setup-token ./mvnw spring-boot:run
```

Then open `http://localhost:8080/setup` with the token `dev-setup-token`.

::: tip Passing settings
Pass settings as environment variables, as above, rather than `-Dspring-boot.run.arguments`. Values with commas or regex characters (such as `VAULT_TRUSTED_PROXIES`) get split or mangled on the command line.
:::

## Project layout

```
src/main/java/tech/sarthakgoyal/bastionvault/
├── config/        VaultProperties (vault.* settings), SecurityConfig (UI + API filter chains)
├── crypto/        Aead (AES-GCM), Kdf (PBKDF2), KeyManager (DEK, seal/unseal), LegacyCbc (0.1 import)
├── domain/        JPA entities: VaultConfig, AdminUser, Secret, ApiClient, PolicyRule, ApiKey, AuditEvent
├── repository/    Spring Data repositories
├── service/       Secrets, policies, tokens, clients, setup, unseal, import/export, audit, lockout
├── api/           REST controllers, Bearer filter, error mapping, health
└── ui/            Vaadin: AppShell, MainLayout, views/ (Login, Setup, Unseal, Secrets, Clients, Audit, Settings)

src/main/resources/
├── application.yml                    every setting, driven by environment variables
├── db/migration/V1__initial_schema.sql
└── META-INF/resources/                styles.css, Geist fonts, Lucide icons
```

## Tests

| Suite | Covers |
| :--- | :--- |
| `CryptoTest` | AES-GCM round trips, fresh nonces, tamper and wrong-key rejection, AAD binding to `(group, key)`, recovery and unseal key formats, the legacy CBC decryptor |
| `EnvParserTest` | `.env` parsing: quoting, escapes, multi-line values, warnings |
| `SecretCodecTest` | JSON and YAML read/write, round trips, scalar preservation, alias limits |
| `ServiceUnitTest` | Glob matching, `.env` quoting, name validation, lockout window and expiry, token TTL caps, tokens dropped on revoke and seal |
| `VaultIntegrationTest` | The full API over MockMvc: auth, policies, every endpoint, status codes, sealed behaviour |
| `ViewAccessTest` | Every view needs the `ADMIN` role |
| `ViewsBrowserlessTest` | The real Vaadin views, driven server-side without a browser: unseal, save, reveal, import, export, clients, auto-unseal, theme toggle |

`./mvnw verify` runs everything. CI runs the same command.

## Database changes

Add a new Flyway migration, `V2__description.sql`, next to `V1`. Never edit a migration that has been released. Keep to portable SQL so the H2 test database (PostgreSQL mode) can run it too.

## Releases

1. Make sure `main` is green.
2. Tag and push:

   ```bash
   git tag v0.2.0
   git push origin v0.2.0
   ```

3. GitHub Actions runs the tests, then builds and pushes `sarthakg0yal/bastionvault:0.2.0` and `:0.2` to Docker Hub.

The workflow needs the repository secrets `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN`. Pushes to `main` and pull requests run the tests only.

## License

MIT. Bundled assets: the Geist and Geist Mono typefaces (SIL Open Font License) and Lucide icons (ISC), served from the app itself. The interface was redesigned following *unslop* by Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), CC BY 4.0.
