# Settings

**Settings** holds the keys, backups and your sign-in. Every action here is recorded in the [audit log](/ui/audit).

## Login password

Changes the password **you** use to sign in to the UI. Enter the current login password and the new one twice. The new password needs at least 12 characters.

This has nothing to do with unsealing. The master password is unchanged.

## Master password

Changes the password that unseals the vault. Enter the current master password and the new one twice (at least 12 characters).

The data key is re-wrapped with the new password. **Secrets are not re-encrypted**, so this is instant. The recovery key and the auto-unseal key keep working.

## Recovery key

The recovery key unseals the vault if the master password is lost. You got one at setup.

**Regenerate recovery key** needs the current master password. It shows a new key once, and **the old recovery key stops working immediately**. Store the new one offline.

Regenerate it when the old one may have been exposed, or when you are not sure where it is.

## Auto-unseal

Lets the vault unseal itself on start by reading a key file. See [Auto-unseal](/deploy/auto-unseal) for the deployment side.

| Control | Effect |
| :--- | :--- |
| **Status** | **On** when an auto-unseal key exists |
| **Generate unseal key** | Asks for confirmation, then shows a new `bvu_…` key once. Any previous unseal key stops working. Update the Kubernetes Secret before the next restart. |
| **Turn off** | Deletes the wrapped copy for the unseal key. After the next restart the vault stays sealed until someone unseals it in the UI. |

The master password and recovery key keep working either way.

::: warning Trade-off
Anyone who can read the unseal key (for example, anyone with access to that Kubernetes Secret) can unseal the vault. You get unattended restarts in exchange.
:::

## Export and import everything

A plaintext JSON or YAML file of **every** group, for moving secrets between instances.

- **Export everything** needs the master password. It then offers download links for JSON and YAML.
- **Import vault file** takes such a file and shows a per-group preview before writing.

Routine backups should copy PostgreSQL instead, which only holds ciphertext. Details: [Import and export](/ui/import-export#export-everything).

## Import from BastionVault 0.1

For upgrades from the old MongoDB version. Paste the output of `mongoexport --collection secrets --jsonArray`, and enter the **old master password** and the **old `VAULT_SALT`**. Each value is decrypted in memory with the old AES-CBC scheme and re-encrypted with AES-GCM.

Tick **Overwrite secrets that already exist** to replace keys that are already in the new vault. The result tells you how many values were imported, skipped and failed.

The full procedure is in [Upgrading from 0.1](/operations/upgrading).
