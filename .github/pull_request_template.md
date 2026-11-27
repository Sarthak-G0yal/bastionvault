## What and why

<!-- What does this change, and why is it needed? Link the issue if there is one. -->

## How it was tested

<!-- Tests added or changed, and anything checked by hand (UI in light and dark theme, API calls). -->

## Checklist

- [ ] `./mvnw verify` passes
- [ ] No secret values in logs, audit rows, errors or responses
- [ ] New actions are audited, new endpoints go through `PolicyService`, new views need `ADMIN`
- [ ] Schema changes are a new Flyway migration
- [ ] Documentation updated (`README.md`, `SECURITY.md`, or a pull request against `docs`)
