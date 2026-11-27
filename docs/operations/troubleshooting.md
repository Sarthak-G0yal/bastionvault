# Troubleshooting

## Every API call returns `503`

The vault is **sealed**. That is normal after every restart without auto-unseal.

```bash
curl -s https://bastionvault.homelab118.home/api/v1/health
# {"initialized":true,"sealed":true,"version":"…"}
```

Sign in to the UI and unseal. To make it unseal itself on start, see [Auto-unseal](/deploy/auto-unseal).

## Auto-unseal did not work

Check the start-up log:

```bash
kubectl -n bastionvault logs deploy/bastionvault | grep -i unseal
```

| Message | Fix |
| :--- | :--- |
| `the configured key does not match` | The key was rotated or auto-unseal was turned off. Unseal manually, **Generate unseal key**, and update the Secret. |
| `key file … is not readable` | Wrong mount path, Secret missing, or file permissions. The container runs as user `bastion`. |
| `no auto-unseal key configured` | `VAULT_UNSEAL_KEY_FILE` is not set in the container |

## I lost the setup token

While the vault is uninitialized, every start logs:

```
BastionVault is not initialized. Open /setup and enter this setup token: …
```

```bash
docker compose logs bastion-vault | grep "setup token"
```

Without `VAULT_SETUP_TOKEN`, the token is random and **new on each start**, so use the one from the latest start. To get a fixed one, set `VAULT_SETUP_TOKEN` and restart.

## Sign-in seems to work, then I am back on the login page

`VAULT_COOKIE_SECURE=true` is set, but you are using plain `http://`. The browser never sends the cookie back. Use the HTTPS URL through the proxy, or set it to `false` for local testing.

## `429 Too Many Requests` or the login is locked

There were too many failures for that user, client or IP: 5 per identity or 20 per IP within 15 minutes. Wait 15 minutes. The counters are in memory, so a restart also clears them, but it seals the vault too.

If **every** client is locked out at once, the IP limit is probably being hit for the proxy's address. Configure [trusted proxies](/deploy/reverse-proxy#trusted-proxies) so each client is counted by its own IP.

## A client gets `403`

It is authenticated but has no rule for that group or capability. Open **Machine identities → Access** for that client and add a rule. The [audit log](/ui/audit) shows the exact `DENIED` action and target.

`PUT /import` (many groups) needs write on **every** group in the file. The error message lists the groups that are missing.

## A client gets `401`

- The session token expired (60 minutes at most) or the vault was sealed or restarted since it was issued. Fetch a new one.
- The client secret was rotated. Update the consumer.
- The API key was revoked or expired, or the client is disabled.

## The audit log shows the proxy's IP for everyone

`VAULT_TRUSTED_PROXIES` does not match the proxy's address, or kube-proxy rewrites the source. See [Trusted proxies](/deploy/reverse-proxy#trusted-proxies).

## A key is missing from `/env/{group}`

Its name is not a valid shell variable name, for example `private.key`. It is listed in a comment at the end of the output. Read it with `/groups/{group}` or `/export/{group}?format=json`.

## A YAML value changed type

It didn't. BastionVault keeps YAML scalars exactly as written, so `yes` stays `"yes"`. The consumer reading the exported file may convert it. Quote values in your own YAML if that matters.

## Forgot the admin login password

0.2 has no reset flow in the UI. The login password is only a BCrypt hash in the `admin_users` table, and it is separate from the master password, so you can replace it directly:

```bash
# generate a BCrypt hash, cost 12
htpasswd -bnBC 12 "" 'new-long-password' | tr -d ':\n'
```

```sql
UPDATE admin_users SET password_hash = '<hash>' WHERE username = 'admin';
```

This gives access to the UI only. You still need the master password or recovery key to unseal.

## The app does not start: Flyway or schema validation errors

- `DB_URL`, `DB_USERNAME` or `DB_PASSWORD` is wrong, or the database does not exist.
- The database was changed by hand. Hibernate validates the schema on start and refuses to run against an unexpected one. Restore from a backup.
