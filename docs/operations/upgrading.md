# Upgrading

## Between 0.2.x releases

1. Read the release notes.
2. [Back up the database](/operations/backup-and-recovery#back-up-the-database).
3. Change the image tag, for example `sarthakg0yal/bastionvault:0.2.1`, and redeploy.
4. Flyway applies schema migrations on start.
5. Unseal, unless auto-unseal is on.

Pin exact versions in deployments. The published tags are `X.Y.Z` and `X.Y`.

## From 0.1 (MongoDB) to 0.2

0.2 is a rewrite of the storage and security model:

| | 0.1 | 0.2 |
| :--- | :--- | :--- |
| Database | MongoDB | PostgreSQL |
| Cipher | AES-CBC, key from the master password and `VAULT_SALT` | AES-256-GCM with envelope encryption |
| UI login | The master password | A separate admin account |
| Client access | Every client could read everything | Deny by default, per-group rules |
| Tokens | Could be non-expiring | Capped at 60 minutes. Long-lived access uses API keys. |
| Unseal | n/a | Master password, recovery key, or auto-unseal key |

The data is not migrated automatically. You move it with the built-in importer.

### Steps

1. **Deploy 0.2** against an empty PostgreSQL database and complete [setup](/guide/getting-started#_3-run-setup). Keep 0.1 running for now.
2. **Export the old secrets** from MongoDB:

   ```bash
   mongoexport --uri "$MONGO_URI" --collection secrets --jsonArray > secrets.json
   ```

3. In 0.2, open **Settings → Import from BastionVault 0.1**:
   - paste the contents of `secrets.json`
   - enter the **old master password** and the **old `VAULT_SALT`**
   - click **Import**

   Each value is decrypted in memory with the old scheme and re-encrypted with AES-GCM. The result shows how many values were imported, skipped and failed.

4. **Recreate the machine clients.** 0.1 clients had no policies, so each one now needs explicit rules. Prefer [API keys](/api/authentication#api-keys) where 0.1 used non-expiring tokens.
5. **Point consumers** at the new instance and check the [audit log](/ui/audit) for `DENIED` rows.
6. **Clean up:** `shred -u secrets.json` and retire the MongoDB instance.

### Removed settings

| 0.1 setting | Replacement |
| :--- | :--- |
| `MONGO_URI` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| `VAULT_SALT` | A random salt per install, stored in the database |
| `VAULT_DEPLOYMENT_TOKEN` | `VAULT_SETUP_TOKEN`, used once |
| `"expiresInMinutes": 0` | API keys |
