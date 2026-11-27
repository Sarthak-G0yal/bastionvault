# Backup and recovery

## What you need to recover

To restore BastionVault on a new machine you need **two things**:

1. **A PostgreSQL dump** of the `bastionvault` database
2. **One** of:
   - the **master password**
   - the **recovery key**
   - the **auto-unseal key**

The dump on its own holds only ciphertext and wrapped keys, so it is safe to copy to backup storage. A key on its own decrypts nothing. Keep at least **two** of the three keys somewhere other than the machine that runs BastionVault, for example a password manager outside the homelab plus a paper copy.

::: danger No back door
If you lose the master password, the recovery key **and** the auto-unseal key, the data cannot be decrypted by anyone, including you. Regenerate the recovery key (Settings) as soon as you suspect it is lost.
:::

## Back up the database

```bash
pg_dump -h 192.168.1.140 -U bastionvault -Fc bastionvault \
  > bastionvault-$(date +%F).dump
```

- Run it on a schedule (cron, a Kubernetes CronJob, or the database host's own backup job) and copy the files off the host.
- Back up **after** changing the master password or regenerating keys too. An older dump has the older wrapped keys, so it opens only with the keys that were valid when it was taken.

## Restore

```bash
createdb -h <new-host> -U postgres -O bastionvault bastionvault
pg_restore -h <new-host> -U bastionvault -d bastionvault bastionvault-2026-11-27.dump
```

Point a fresh BastionVault at the restored database (`DB_URL`). It starts **already initialized** and sealed. Sign in with your existing admin account and unseal with any of the keys.

Clients, policy rules, API keys and the audit log are all in the database, so consumers keep working once their URL points at the new instance. Session tokens are not restored, because they were only ever in memory. Consumers fetch new ones.

## Exports are not backups

**Settings → Export everything** produces a **plaintext** file. It is useful for moving secrets between instances, but as a backup it is weaker: anyone who finds the file has everything. If you keep one anyway, encrypt it:

```bash
sops -e --age <your-age-recipient> bastionvault-export.yaml > bastionvault-export.enc.yaml
shred -u bastionvault-export.yaml
```

## Test the restore

A backup you have never restored is a guess. Once in a while:

1. Restore the latest dump into a throwaway database.
2. Start BastionVault against it, for example with `docker compose` on your laptop.
3. Unseal with the **recovery key** (not the master password), to prove the offline copy works.
4. Reveal one secret, then delete the test database.

## Disaster checklist

| Lost | Do this |
| :--- | :--- |
| The server or pod | Restore the dump, start BastionVault, unseal |
| The database, but the server is fine | Restore the dump into PostgreSQL and restart BastionVault |
| The master password | Unseal with the recovery key. Changing the master password needs the old one, so move the secrets to a fresh instance (see below) |
| The recovery key | Unseal with the master password, then **Regenerate recovery key** |
| The auto-unseal key | Unseal manually, then **Generate unseal key** and update the Secret |
| The admin login password | See [Troubleshooting](/operations/troubleshooting#forgot-the-admin-login-password) |

### Lost master password

Changing the master password needs the current one. If it is lost but you still have the recovery key:

1. Unseal with the recovery key.
2. **Settings → Export everything** needs the master password too, so instead let a client with read on `*` call `GET /api/v1/export?format=yaml`.
3. Set up a new instance (new database) with a new master password, and import the file through **Settings → Import vault file**.
4. Recreate clients and API keys, and update consumers.
